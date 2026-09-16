-- Migration: 20260914140000_fix_confirm_child_sync_request_role_arg.sql
--
-- Bug: pressing Deny (or Approve) on a sync request did nothing. The app calls
-- confirm_child_sync_request(p_request_id, p_approve, p_role), but production only had the 2-arg
-- (uuid, boolean) version, so PostgREST answered 404 on every call and the request stayed
-- 'pending' - which is why the same request kept popping up on every launch.
--
-- The 3-arg versions in 20260816_owner_transfer_and_reclaim.sql and
-- 20260908160000_approver_order_and_skip.sql were never applied, and both write columns that do
-- not exist in production (child_access_activity.metadata, child_members.approval_rank). Do NOT
-- apply them as-is. This migration keeps the live body and only adds p_role.
--
-- Also closes a hole in the live function: it was executable by anon, and with auth.uid() NULL
-- the `v_owner <> auth.uid()` check evaluates to NULL and does not raise.

DROP FUNCTION IF EXISTS public.confirm_child_sync_request(uuid, boolean);

CREATE OR REPLACE FUNCTION public.confirm_child_sync_request(
  p_request_id uuid,
  p_approve boolean,
  p_role text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_child uuid; v_owner uuid; v_requester uuid; v_status text; v_role text; v_label text;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'not authorized'; END IF;

  SELECT child_id, owner_user_id, requester_user_id, status, requested_role, requester_label
    INTO v_child, v_owner, v_requester, v_status, v_role, v_label
    FROM public.child_sync_requests WHERE id = p_request_id;
  IF v_owner IS NULL THEN RAISE EXCEPTION 'request not found'; END IF;
  IF v_owner <> auth.uid() THEN RAISE EXCEPTION 'not authorized'; END IF;
  IF v_status <> 'pending' THEN RAISE EXCEPTION 'already resolved'; END IF;

  -- The owner's pick in the approve dialog wins over what the requester asked for.
  v_role := COALESCE(NULLIF(trim(p_role), ''), NULLIF(v_role, ''), 'co_owner');
  IF v_role = 'member' THEN v_role := 'co_owner'; END IF;
  -- A sync request can never mint another owner.
  IF v_role = 'owner' OR NOT (v_role = ANY(public.valid_child_roles())) THEN v_role := 'co_owner'; END IF;

  UPDATE public.child_sync_requests
    SET status = CASE WHEN p_approve THEN 'approved' ELSE 'rejected' END, resolved_at = now()
    WHERE id = p_request_id;

  IF p_approve THEN
    INSERT INTO public.child_members (child_id, user_id, role, display_name, capabilities)
    VALUES (v_child, v_requester, v_role, v_label, public.default_capabilities_for_role(v_role))
    ON CONFLICT (child_id, user_id) DO UPDATE SET
      role = EXCLUDED.role,
      display_name = COALESCE(EXCLUDED.display_name, public.child_members.display_name),
      capabilities = EXCLUDED.capabilities;
    INSERT INTO public.notifications (user_id, category, title, body)
      VALUES (v_requester, 'normal', 'คำขอเชื่อมข้อมูลลูกได้รับการยืนยันแล้ว', 'คุณสามารถเข้าถึงข้อมูลลูกได้แล้ว');
    INSERT INTO public.child_access_activity (child_id, actor_user_id, target_user_id, event_type, role, detail)
      VALUES (v_child, auth.uid(), v_requester, 'access_approved', v_role, COALESCE(v_label, 'approved'));
  ELSE
    INSERT INTO public.notifications (user_id, category, title, body)
      VALUES (v_requester, 'normal', 'คำขอเชื่อมข้อมูลลูกถูกปฏิเสธ', NULL);
    INSERT INTO public.child_access_activity (child_id, actor_user_id, target_user_id, event_type, role, detail)
      VALUES (v_child, auth.uid(), v_requester, 'access_rejected', v_role, COALESCE(v_label, 'rejected'));
  END IF;
END; $function$;

REVOKE ALL ON FUNCTION public.confirm_child_sync_request(uuid, boolean, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.confirm_child_sync_request(uuid, boolean, text) TO authenticated;

NOTIFY pgrst, 'reload schema';
