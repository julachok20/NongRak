-- Migration: 20260908120000_one_time_sync_request_and_reject_lock.sql
-- Description: Ensures sync requests are strictly one-time only and prevents re-requesting if parent rejected ("No means No").

-- Drop any legacy 3-arg or 4-arg overloads
DROP FUNCTION IF EXISTS public.request_child_sync(uuid, text, text);
DROP FUNCTION IF EXISTS public.request_child_sync(uuid, text, text, text);

CREATE OR REPLACE FUNCTION public.request_child_sync(
  p_child_id uuid,
  p_method text,
  p_code text DEFAULT NULL,
  p_role text DEFAULT 'co_owner'
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_requester uuid := auth.uid();
  v_child_id uuid := p_child_id;
  v_owner_id uuid;
  v_role text := coalesce(nullif(trim(p_role), ''), 'co_owner');
  v_existing uuid;
  v_request_id uuid;
BEGIN
  IF v_requester IS NULL THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  IF v_role NOT IN ('co_owner', 'nanny', 'doctor', 'other', 'viewer') THEN
    v_role := 'co_owner';
  END IF;

  IF p_code IS NOT NULL AND trim(p_code) <> '' THEN
    v_child_id := public.resolve_child_id_from_access_code(p_code);
    IF v_child_id IS NULL THEN
      RAISE EXCEPTION 'invalid or expired code';
    END IF;
  END IF;

  IF v_child_id IS NULL THEN
    RAISE EXCEPTION 'child not found';
  END IF;

  SELECT user_id INTO v_owner_id FROM public.children WHERE id = v_child_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'child not found';
  END IF;

  IF v_owner_id = v_requester THEN
    RAISE EXCEPTION 'already a member';
  END IF;

  SELECT cm.child_id INTO v_existing
  FROM public.child_members cm
  WHERE cm.child_id = v_child_id AND cm.user_id = v_requester
  LIMIT 1;

  IF v_existing IS NOT NULL THEN
    RAISE EXCEPTION 'already a member';
  END IF;

  -- 1. If parent previously rejected the request: "No means No, not again"
  IF EXISTS (
    SELECT 1 FROM public.child_sync_requests r
    WHERE r.child_id = v_child_id
      AND r.requester_user_id = v_requester
      AND r.status = 'rejected'
  ) THEN
    RAISE EXCEPTION 'request_rejected';
  END IF;

  -- 2. If a sync request is already pending: one-time only (cannot duplicate or spam)
  IF EXISTS (
    SELECT 1 FROM public.child_sync_requests r
    WHERE r.child_id = v_child_id
      AND r.requester_user_id = v_requester
      AND r.status = 'pending'
  ) THEN
    RAISE EXCEPTION 'already_pending';
  END IF;

  INSERT INTO public.child_sync_requests (
    child_id,
    requester_user_id,
    owner_user_id,
    method,
    requested_role,
    status
  ) VALUES (
    v_child_id,
    v_requester,
    v_owner_id,
    coalesce(nullif(trim(p_method), ''), 'code'),
    v_role,
    'pending'
  )
  RETURNING id INTO v_request_id;

  RETURN v_request_id;
END;
$$;

REVOKE ALL ON FUNCTION public.request_child_sync(uuid, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_child_sync(uuid, text, text, text) TO authenticated;

-- Index for fast lookup of requests by child, requester and status
CREATE INDEX IF NOT EXISTS idx_child_sync_requests_lookup
  ON public.child_sync_requests(child_id, requester_user_id, status);
