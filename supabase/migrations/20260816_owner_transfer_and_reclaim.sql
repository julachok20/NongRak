-- ===========================================================================
-- Migration: Owner Transfer, Owner Reclaim on Re-registration, and Member Deletion Rules
-- Date: 2026-08-16
-- ===========================================================================

-- 1. Extend children table with transfer audit fields
ALTER TABLE public.children ADD COLUMN IF NOT EXISTS previous_owner_email text;
ALTER TABLE public.children ADD COLUMN IF NOT EXISTS previous_owner_id uuid;
ALTER TABLE public.children ADD COLUMN IF NOT EXISTS transferred_from_role text;
ALTER TABLE public.children ADD COLUMN IF NOT EXISTS transferred_at timestamptz;

-- 2. Owner Transfer RPC
CREATE OR REPLACE FUNCTION public.transfer_child_ownership(
  p_child_id uuid,
  p_new_owner_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_child_owner uuid;
  v_old_owner_email text;
  v_target_role text;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  IF v_caller = p_new_owner_id THEN
    RAISE EXCEPTION 'Cannot transfer ownership to yourself';
  END IF;

  -- Check current owner of the child
  SELECT user_id INTO v_child_owner FROM public.children WHERE id = p_child_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Child not found';
  END IF;

  IF v_child_owner <> v_caller THEN
    -- Check if caller is marked as owner in child_members
    IF NOT EXISTS (
      SELECT 1 FROM public.child_members
      WHERE child_id = p_child_id AND user_id = v_caller AND role = 'owner'
    ) THEN
      RAISE EXCEPTION 'Only the child owner can transfer ownership';
    END IF;
  END IF;

  -- Verify target member exists in child_members
  SELECT role INTO v_target_role
  FROM public.child_members
  WHERE child_id = p_child_id AND user_id = p_new_owner_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Target user is not a member of this child record';
  END IF;

  -- Fetch caller email from auth
  SELECT email INTO v_old_owner_email FROM auth.users WHERE id = v_caller;
  IF v_old_owner_email IS NULL THEN
    v_old_owner_email := auth.jwt() ->> 'email';
  END IF;

  -- Update children record
  UPDATE public.children
  SET
    user_id = p_new_owner_id,
    previous_owner_email = v_old_owner_email,
    previous_owner_id = v_caller,
    transferred_from_role = coalesce(v_target_role, 'co_owner'),
    transferred_at = now()
  WHERE id = p_child_id;

  -- Promote new owner
  UPDATE public.child_members
  SET role = 'owner'
  WHERE child_id = p_child_id AND user_id = p_new_owner_id;

  -- Demote previous owner to co_owner
  UPDATE public.child_members
  SET role = 'co_owner'
  WHERE child_id = p_child_id AND user_id = v_caller;

  -- Audit log if child_access_activity exists
  IF to_regclass('public.child_access_activity') IS NOT NULL THEN
    INSERT INTO public.child_access_activity (
      child_id,
      actor_user_id,
      event_type,
      target_user_id,
      metadata
    ) VALUES (
      p_child_id,
      v_caller,
      'ownership_transferred',
      p_new_owner_id,
      jsonb_build_object('transferred_from_role', v_target_role, 'old_owner_email', v_old_owner_email)
    );
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.transfer_child_ownership(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.transfer_child_ownership(uuid, uuid) TO authenticated;


-- 3. Check Reclaimable Children RPC
CREATE OR REPLACE FUNCTION public.check_reclaimable_children()
RETURNS TABLE (
  child_id uuid,
  child_name text,
  birth_date text,
  current_owner_name text,
  transferred_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_caller_email text;
BEGIN
  IF v_caller IS NULL THEN
    RETURN;
  END IF;

  SELECT email INTO v_caller_email FROM auth.users WHERE id = v_caller;
  IF v_caller_email IS NULL THEN
    v_caller_email := auth.jwt() ->> 'email';
  END IF;

  IF v_caller_email IS NULL OR trim(v_caller_email) = '' THEN
    RETURN;
  END IF;

  RETURN QUERY
  SELECT
    c.id AS child_id,
    c.name AS child_name,
    coalesce(c.birth_date::text, '') AS birth_date,
    coalesce(p.display_name, 'ผู้ดูแลปัจจุบัน') AS current_owner_name,
    c.transferred_at
  FROM public.children c
  LEFT JOIN public.profiles p ON p.id = c.user_id
  WHERE lower(trim(c.previous_owner_email)) = lower(trim(v_caller_email))
    AND c.user_id <> v_caller;
END;
$$;

REVOKE ALL ON FUNCTION public.check_reclaimable_children() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.check_reclaimable_children() TO authenticated;


-- 4. Reclaim Child Ownership RPC
CREATE OR REPLACE FUNCTION public.reclaim_child_ownership(p_child_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_caller_email text;
  v_current_owner uuid;
  v_prev_email text;
  v_fallback_role text;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  SELECT email INTO v_caller_email FROM auth.users WHERE id = v_caller;
  IF v_caller_email IS NULL THEN
    v_caller_email := auth.jwt() ->> 'email';
  END IF;

  SELECT
    user_id,
    previous_owner_email,
    coalesce(transferred_from_role, 'co_owner')
  INTO
    v_current_owner,
    v_prev_email,
    v_fallback_role
  FROM public.children
  WHERE id = p_child_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Child not found';
  END IF;

  IF v_prev_email IS NULL OR lower(trim(v_prev_email)) <> lower(trim(v_caller_email)) THEN
    RAISE EXCEPTION 'Not authorized to reclaim ownership of this child';
  END IF;

  -- Demote current owner back to their previous role
  UPDATE public.child_members
  SET role = v_fallback_role
  WHERE child_id = p_child_id AND user_id = v_current_owner;

  -- Update child record back to reclaimed caller
  UPDATE public.children
  SET
    user_id = v_caller,
    previous_owner_email = NULL,
    previous_owner_id = NULL,
    transferred_from_role = NULL,
    transferred_at = NULL
  WHERE id = p_child_id;

  -- Upsert caller as owner in child_members
  INSERT INTO public.child_members (child_id, user_id, role, is_caregiver)
  VALUES (p_child_id, v_caller, 'owner', true)
  ON CONFLICT (child_id, user_id)
  DO UPDATE SET role = 'owner';

  -- Audit log
  IF to_regclass('public.child_access_activity') IS NOT NULL THEN
    INSERT INTO public.child_access_activity (
      child_id,
      actor_user_id,
      event_type,
      target_user_id,
      metadata
    ) VALUES (
      p_child_id,
      v_caller,
      'ownership_reclaimed',
      v_current_owner,
      jsonb_build_object('restored_role', v_fallback_role)
    );
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.reclaim_child_ownership(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.reclaim_child_ownership(uuid) TO authenticated;


-- 5. Updated remove_child_member with owner-can-delete-anyone & co-owner restrictions
CREATE OR REPLACE FUNCTION public.remove_child_member(
  p_child_id uuid,
  p_user_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_child_owner uuid;
  v_caller_role text;
  v_target_role text;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  SELECT user_id INTO v_child_owner FROM public.children WHERE id = p_child_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Child not found';
  END IF;

  -- Cannot delete the child owner through remove_child_member (must transfer or delete child)
  IF p_user_id = v_child_owner THEN
    RAISE EXCEPTION 'Cannot remove the owner. Transfer ownership first.';
  END IF;

  SELECT role INTO v_target_role
  FROM public.child_members
  WHERE child_id = p_child_id AND user_id = p_user_id;

  -- If target is owner in child_members, also block
  IF v_target_role = 'owner' THEN
    RAISE EXCEPTION 'Cannot remove an owner';
  END IF;

  -- Determine caller role
  IF v_caller = v_child_owner THEN
    v_caller_role := 'owner';
  ELSE
    SELECT role INTO v_caller_role
    FROM public.child_members
    WHERE child_id = p_child_id AND user_id = v_caller;
  END IF;

  -- Authorization check:
  -- 1. Self-removal: allowed for any non-owner member
  -- 2. Owner: can remove anyone (including co-owners, relatives, nannies, etc.)
  -- 3. Co-owner: can remove non-co-owners (relatives, nannies, viewers) but CANNOT remove other co-owners or the owner.
  IF v_caller <> p_user_id THEN
    IF v_caller_role = 'owner' THEN
      -- Owner can remove anyone
      NULL;
    ELSIF v_caller_role IN ('co_owner', 'member') THEN
      IF v_target_role IN ('co_owner', 'member', 'owner') THEN
        RAISE EXCEPTION 'Co-owners cannot remove other co-owners';
      END IF;
    ELSE
      RAISE EXCEPTION 'Not authorized to remove members';
    END IF;
  END IF;

  DELETE FROM public.child_members
  WHERE child_id = p_child_id AND user_id = p_user_id;

  IF to_regclass('public.child_access_activity') IS NOT NULL THEN
    INSERT INTO public.child_access_activity (
      child_id,
      actor_user_id,
      event_type,
      target_user_id
    ) VALUES (
      p_child_id,
      v_caller,
      'member_removed',
      p_user_id
    );
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.remove_child_member(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.remove_child_member(uuid, uuid) TO authenticated;


-- 6. Confirm Child Sync Request with optional role override
CREATE OR REPLACE FUNCTION public.confirm_child_sync_request(
  p_request_id uuid,
  p_approve boolean,
  p_role text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_req record;
  v_granted_role text;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  SELECT * INTO v_req
  FROM public.child_sync_requests
  WHERE id = p_request_id AND status = 'pending';

  IF NOT FOUND THEN
    RAISE EXCEPTION 'already resolved';
  END IF;

  -- Check caller is the owner of the child or the owner_user_id on request
  IF v_req.owner_user_id <> v_caller THEN
    IF NOT EXISTS (
      SELECT 1 FROM public.children WHERE id = v_req.child_id AND user_id = v_caller
    ) AND NOT EXISTS (
      SELECT 1 FROM public.child_members WHERE child_id = v_req.child_id AND user_id = v_caller AND role = 'owner'
    ) THEN
      RAISE EXCEPTION 'not authorized';
    END IF;
  END IF;

  IF p_approve THEN
    v_granted_role := coalesce(nullif(trim(p_role), ''), v_req.requested_role, 'co_owner');
    IF v_granted_role = 'owner' THEN
      v_granted_role := 'co_owner';
    END IF;

    INSERT INTO public.child_members (
      child_id,
      user_id,
      role,
      is_caregiver
    ) VALUES (
      v_req.child_id,
      v_req.requester_user_id,
      v_granted_role,
      false
    )
    ON CONFLICT (child_id, user_id)
    DO UPDATE SET role = EXCLUDED.role;

    UPDATE public.child_sync_requests
    SET status = 'approved',
        resolved_at = now()
    WHERE id = p_request_id;

    IF to_regclass('public.child_access_activity') IS NOT NULL THEN
      INSERT INTO public.child_access_activity (
        child_id,
        actor_user_id,
        event_type,
        target_user_id,
        metadata
      ) VALUES (
        v_req.child_id,
        v_caller,
        'access_approved',
        v_req.requester_user_id,
        jsonb_build_object('role', v_granted_role)
      );
    END IF;
  ELSE
    UPDATE public.child_sync_requests
    SET status = 'rejected',
        resolved_at = now()
    WHERE id = p_request_id;

    IF to_regclass('public.child_access_activity') IS NOT NULL THEN
      INSERT INTO public.child_access_activity (
        child_id,
        actor_user_id,
        event_type,
        target_user_id
      ) VALUES (
        v_req.child_id,
        v_caller,
        'access_rejected',
        v_req.requester_user_id
      );
    END IF;
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.confirm_child_sync_request(uuid, boolean, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.confirm_child_sync_request(uuid, boolean, text) TO authenticated;

