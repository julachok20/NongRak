-- Migration: 20260908160000_approver_order_and_skip.sql
-- Description: Ordered approvers for child sync requests.
--
--   * The owner decides first while they are actually around.
--   * Co-owners are ranked (1, 2, ...) by the owner and take their turn in order.
--   * "ข้าม" (skip) hands the decision to the next rank immediately - it is NOT a denial,
--     the request stays pending.
--   * A rank that never answers must not freeze the request, so each rank also unlocks on a
--     timer, and a rank whose user has not had the app open recently is passed over at once.
--
-- Deliberately server-side. The client may show or hide a button however it likes; whether a
-- given user is ALLOWED to resolve a request is decided here, in one place, per the project's
-- "authorisation lives in Postgres" rule.

-- ---------------------------------------------------------------------------------------------
-- 1. Schema
-- ---------------------------------------------------------------------------------------------

-- Rank among approvers. NULL = not an approver at all (the current behaviour for every co-owner,
-- so this migration grants nobody anything until an owner explicitly assigns a rank).
-- The owner is always rank 0 implicitly and is never stored here.
ALTER TABLE public.child_members
  ADD COLUMN IF NOT EXISTS approval_rank integer;

ALTER TABLE public.child_members
  DROP CONSTRAINT IF EXISTS child_members_approval_rank_positive;
ALTER TABLE public.child_members
  ADD CONSTRAINT child_members_approval_rank_positive
  CHECK (approval_rank IS NULL OR approval_rank >= 1);

-- One rank per child: no two co-owners may both be "Co-Owner 1".
CREATE UNIQUE INDEX IF NOT EXISTS child_members_unique_rank_per_child
  ON public.child_members(child_id, approval_rank)
  WHERE approval_rank IS NOT NULL;

-- Liveness. Stamped by the app on foreground; a stale value means "not around right now", which
-- lets the next rank act immediately instead of waiting out the timer.
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS last_active_at timestamptz;

-- Who has passed on which request. A skip is per (request, user) and permanent for that request.
CREATE TABLE IF NOT EXISTS public.child_sync_request_skips (
  request_id uuid NOT NULL REFERENCES public.child_sync_requests(id) ON DELETE CASCADE,
  user_id    uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (request_id, user_id)
);

ALTER TABLE public.child_sync_request_skips ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "read own skips" ON public.child_sync_request_skips;
CREATE POLICY "read own skips" ON public.child_sync_request_skips
  FOR SELECT USING (user_id = auth.uid());

-- No INSERT/UPDATE/DELETE policy on purpose: skips are written only by the security definer
-- RPC below, which checks the caller is genuinely an approver for that request first.

-- ---------------------------------------------------------------------------------------------
-- 2. Tunables
-- ---------------------------------------------------------------------------------------------

-- How long each rank holds the exclusive right to decide before the next rank unlocks.
CREATE OR REPLACE FUNCTION public.sync_approval_grace()
RETURNS interval LANGUAGE sql IMMUTABLE AS $$ SELECT interval '60 seconds' $$;

-- How recently a user must have had the app open to count as "around".
CREATE OR REPLACE FUNCTION public.sync_presence_window()
RETURNS interval LANGUAGE sql IMMUTABLE AS $$ SELECT interval '2 minutes' $$;

-- ---------------------------------------------------------------------------------------------
-- 3. Turn resolution
-- ---------------------------------------------------------------------------------------------

/*
 * The rank of [p_user] among approvers for [p_child_id]:
 *   0    - the owner
 *   >= 1 - a co-owner the owner has explicitly ranked
 *   NULL - not an approver
 */
CREATE OR REPLACE FUNCTION public.child_approver_rank(p_child_id uuid, p_user uuid)
RETURNS integer
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT CASE
    WHEN EXISTS (SELECT 1 FROM public.children c WHERE c.id = p_child_id AND c.user_id = p_user)
      THEN 0
    WHEN EXISTS (SELECT 1 FROM public.child_members m
                 WHERE m.child_id = p_child_id AND m.user_id = p_user AND m.role = 'owner')
      THEN 0
    ELSE (SELECT m.approval_rank FROM public.child_members m
          WHERE m.child_id = p_child_id AND m.user_id = p_user)
  END
$$;

/*
 * May [p_user] resolve [p_request_id] right now?
 *
 * True when the caller is an approver AND every strictly-lower rank has yielded, where "yielded"
 * means any of:
 *   - they skipped this request,
 *   - they have not had the app open inside the presence window (so they are not there to decide),
 *   - their grace period has elapsed (so they had their turn and did not take it).
 *
 * The owner is rank 0, so with the owner present and recent, nobody else passes this check for the
 * first 60 seconds - the "owner and co-owner enter at the same time, owner decides" case. With the
 * owner away, every lower rank is stale and the co-owner passes immediately - the "co-owner comes
 * first" case.
 */
CREATE OR REPLACE FUNCTION public.may_resolve_sync_request(p_request_id uuid, p_user uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_req      record;
  v_rank     integer;
  v_lower    record;
  v_grace    interval := public.sync_approval_grace();
  v_presence interval := public.sync_presence_window();
BEGIN
  SELECT * INTO v_req FROM public.child_sync_requests WHERE id = p_request_id;
  IF NOT FOUND OR v_req.status <> 'pending' THEN
    RETURN false;
  END IF;

  v_rank := public.child_approver_rank(v_req.child_id, p_user);
  IF v_rank IS NULL THEN
    RETURN false;                      -- not an approver at all
  END IF;

  -- Having skipped, you have given up your own turn.
  IF EXISTS (SELECT 1 FROM public.child_sync_request_skips s
             WHERE s.request_id = p_request_id AND s.user_id = p_user) THEN
    RETURN false;
  END IF;

  IF v_rank = 0 THEN
    RETURN true;                       -- the owner never waits for anyone
  END IF;

  -- Walk every approver ranked ahead of the caller.
  FOR v_lower IN
    SELECT u.user_id, u.rank
    FROM (
      SELECT c.user_id, 0 AS rank
        FROM public.children c
       WHERE c.id = v_req.child_id
      UNION
      SELECT m.user_id, m.approval_rank AS rank
        FROM public.child_members m
       WHERE m.child_id = v_req.child_id
         AND m.approval_rank IS NOT NULL
    ) u
    WHERE u.rank < v_rank
  LOOP
    -- Skipped: yielded.
    CONTINUE WHEN EXISTS (
      SELECT 1 FROM public.child_sync_request_skips s
       WHERE s.request_id = p_request_id AND s.user_id = v_lower.user_id
    );

    -- Not around: yielded. A missing profile row or a NULL stamp counts as away, so a user who
    -- has never run a build that reports liveness can never block a co-parent.
    CONTINUE WHEN NOT EXISTS (
      SELECT 1 FROM public.profiles p
       WHERE p.id = v_lower.user_id
         AND p.last_active_at IS NOT NULL
         AND p.last_active_at > now() - v_presence
    );

    -- Present and has not skipped: they hold the decision until their grace window closes.
    IF now() < v_req.created_at + (v_lower.rank + 1) * v_grace THEN
      RETURN false;
    END IF;
  END LOOP;

  RETURN true;
END;
$$;

-- ---------------------------------------------------------------------------------------------
-- 4. RPCs
-- ---------------------------------------------------------------------------------------------

/* Owner assigns (or clears, with NULL) a co-owner's place in the order. */
CREATE OR REPLACE FUNCTION public.set_child_approver_rank(
  p_child_id uuid,
  p_user_id  uuid,
  p_rank     integer
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller uuid := auth.uid();
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  IF public.child_approver_rank(p_child_id, v_caller) IS DISTINCT FROM 0 THEN
    RAISE EXCEPTION 'only the owner may set the approval order';
  END IF;

  IF p_rank IS NOT NULL AND p_rank < 1 THEN
    RAISE EXCEPTION 'rank must be 1 or higher (the owner is always first)';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM public.child_members
                  WHERE child_id = p_child_id AND user_id = p_user_id) THEN
    RAISE EXCEPTION 'not a member of this child';
  END IF;

  UPDATE public.child_members
     SET approval_rank = p_rank
   WHERE child_id = p_child_id AND user_id = p_user_id;
END;
$$;

/* ข้าม - pass the decision to the next rank. Never changes the request's status. */
CREATE OR REPLACE FUNCTION public.skip_child_sync_request(p_request_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_req    record;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;

  SELECT * INTO v_req FROM public.child_sync_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'request not found';
  END IF;

  IF v_req.status <> 'pending' THEN
    RAISE EXCEPTION 'already resolved';
  END IF;

  IF public.child_approver_rank(v_req.child_id, v_caller) IS NULL THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  INSERT INTO public.child_sync_request_skips (request_id, user_id)
  VALUES (p_request_id, v_caller)
  ON CONFLICT (request_id, user_id) DO NOTHING;
END;
$$;

/* What the current user may do with this request, for rendering the buttons. */
CREATE OR REPLACE FUNCTION public.child_sync_request_turn(p_request_id uuid)
RETURNS TABLE (my_rank integer, may_act boolean, i_skipped boolean, waiting_for uuid)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_caller uuid := auth.uid();
  v_req    record;
BEGIN
  SELECT * INTO v_req FROM public.child_sync_requests WHERE id = p_request_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'request not found';
  END IF;

  my_rank   := public.child_approver_rank(v_req.child_id, v_caller);
  may_act   := public.may_resolve_sync_request(p_request_id, v_caller);
  i_skipped := EXISTS (SELECT 1 FROM public.child_sync_request_skips s
                        WHERE s.request_id = p_request_id AND s.user_id = v_caller);

  -- Lowest rank ahead of the caller that has not skipped: who they are waiting on.
  SELECT u.user_id INTO waiting_for
  FROM (
    SELECT c.user_id, 0 AS rank FROM public.children c WHERE c.id = v_req.child_id
    UNION
    SELECT m.user_id, m.approval_rank FROM public.child_members m
     WHERE m.child_id = v_req.child_id AND m.approval_rank IS NOT NULL
  ) u
  WHERE my_rank IS NOT NULL
    AND u.rank < my_rank
    AND NOT EXISTS (SELECT 1 FROM public.child_sync_request_skips s
                     WHERE s.request_id = p_request_id AND s.user_id = u.user_id)
  ORDER BY u.rank
  LIMIT 1;

  RETURN NEXT;
END;
$$;

/* Heartbeat. Cheap enough to call on every app foreground. */
CREATE OR REPLACE FUNCTION public.touch_last_active()
RETURNS void
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
AS $$
  UPDATE public.profiles SET last_active_at = now() WHERE id = auth.uid();
$$;

-- ---------------------------------------------------------------------------------------------
-- 5. confirm_child_sync_request - now turn-aware
-- ---------------------------------------------------------------------------------------------
--
-- Replaces the 20260816 version. Two changes:
--   * ranked co-owners may resolve (previously only role = 'owner' could, which is why a co-owner
--     pressing Deny got 'not authorized'),
--   * whoever calls must actually hold the turn.

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
  v_rank integer;
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

  v_rank := public.child_approver_rank(v_req.child_id, v_caller);
  IF v_rank IS NULL THEN
    RAISE EXCEPTION 'not authorized';
  END IF;

  -- Distinct from 'not authorized' on purpose: this caller IS an approver, just not yet. The
  -- client shows "waiting for the owner", not "you have no rights here".
  IF NOT public.may_resolve_sync_request(p_request_id, v_caller) THEN
    RAISE EXCEPTION 'not_your_turn';
  END IF;

  IF p_approve THEN
    v_granted_role := coalesce(nullif(trim(p_role), ''), v_req.requested_role, 'co_owner');
    IF v_granted_role = 'owner' THEN
      v_granted_role := 'co_owner';
    END IF;

    INSERT INTO public.child_members (child_id, user_id, role, is_caregiver)
    VALUES (v_req.child_id, v_req.requester_user_id, v_granted_role, false)
    ON CONFLICT (child_id, user_id) DO UPDATE SET role = EXCLUDED.role;

    UPDATE public.child_sync_requests
       SET status = 'approved', resolved_at = now()
     WHERE id = p_request_id;

    IF to_regclass('public.child_access_activity') IS NOT NULL THEN
      INSERT INTO public.child_access_activity
        (child_id, actor_user_id, event_type, target_user_id, metadata)
      VALUES
        (v_req.child_id, v_caller, 'access_approved', v_req.requester_user_id,
         jsonb_build_object('role', v_granted_role, 'approver_rank', v_rank));
    END IF;
  ELSE
    UPDATE public.child_sync_requests
       SET status = 'rejected', resolved_at = now()
     WHERE id = p_request_id;

    IF to_regclass('public.child_access_activity') IS NOT NULL THEN
      INSERT INTO public.child_access_activity
        (child_id, actor_user_id, event_type, target_user_id, metadata)
      VALUES
        (v_req.child_id, v_caller, 'access_rejected', v_req.requester_user_id,
         jsonb_build_object('approver_rank', v_rank));
    END IF;
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------------------------
-- 6. Grants and indexes
-- ---------------------------------------------------------------------------------------------

REVOKE ALL ON FUNCTION public.set_child_approver_rank(uuid, uuid, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.set_child_approver_rank(uuid, uuid, integer) TO authenticated;

REVOKE ALL ON FUNCTION public.skip_child_sync_request(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.skip_child_sync_request(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.child_sync_request_turn(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.child_sync_request_turn(uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.touch_last_active() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.touch_last_active() TO authenticated;

REVOKE ALL ON FUNCTION public.may_resolve_sync_request(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.may_resolve_sync_request(uuid, uuid) TO authenticated;

REVOKE ALL ON FUNCTION public.child_approver_rank(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.child_approver_rank(uuid, uuid) TO authenticated;

CREATE INDEX IF NOT EXISTS idx_child_members_rank
  ON public.child_members(child_id, approval_rank) WHERE approval_rank IS NOT NULL;
