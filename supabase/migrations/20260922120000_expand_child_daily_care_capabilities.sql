-- Stitch Permission Settings Detail: daily-care + nutrition/sleep toggles.
-- child_can() already matches any stored string; valid_child_capabilities() is the allowlist
-- used by update_child_member_permissions / upsert_child_role.

CREATE OR REPLACE FUNCTION public.valid_child_capabilities()
RETURNS text[]
LANGUAGE sql
IMMUTABLE
SET search_path TO 'public'
AS $function$
  SELECT ARRAY[
    'view_health','log_symptoms','edit_growth',
    'view_appointments','book_doctor','cancel_appointments',
    'view_documents','upload_documents','edit_profile',
    'view_activities','log_activities',
    'log_medications','log_feeding','view_nutrition','log_sleep'
  ]::text[];
$function$;

CREATE OR REPLACE FUNCTION public.default_capabilities_for_role(p_role text)
RETURNS text[]
LANGUAGE sql
IMMUTABLE
SET search_path TO 'public'
AS $function$
  SELECT CASE p_role
    WHEN 'owner'    THEN public.valid_child_capabilities()
    WHEN 'co_owner' THEN public.valid_child_capabilities()
    WHEN 'member'   THEN public.valid_child_capabilities()
    WHEN 'grandparent' THEN ARRAY[
      'view_health','log_symptoms','view_appointments','view_documents',
      'view_activities','log_activities','log_feeding','view_nutrition','log_sleep'
    ]::text[]
    WHEN 'relative' THEN ARRAY[
      'view_health','view_appointments','view_documents',
      'view_activities','view_nutrition'
    ]::text[]
    WHEN 'nanny' THEN ARRAY[
      'view_health','log_symptoms','view_appointments','view_documents',
      'view_activities','log_activities','log_medications','log_feeding','view_nutrition','log_sleep'
    ]::text[]
    WHEN 'teacher' THEN ARRAY[
      'view_health','log_symptoms',
      'view_activities','log_activities','log_feeding','view_nutrition','log_sleep'
    ]::text[]
    WHEN 'doctor' THEN ARRAY[
      'view_health','view_appointments','view_documents','upload_documents'
    ]::text[]
    WHEN 'viewer' THEN ARRAY[
      'view_health','view_appointments','view_documents',
      'view_activities','view_nutrition'
    ]::text[]
    WHEN 'other' THEN ARRAY[
      'view_health','view_appointments','view_documents',
      'view_activities','view_nutrition'
    ]::text[]
    ELSE '{}'::text[]
  END;
$function$;

-- Existing members keep daily-care access until an owner turns a toggle off.
UPDATE public.child_members
SET capabilities = (
  SELECT ARRAY(
    SELECT DISTINCT c
    FROM unnest(
      COALESCE(capabilities, '{}'::text[]) ||
      ARRAY['view_activities','log_activities','log_medications','log_feeding','view_nutrition','log_sleep']::text[]
    ) AS c
    WHERE c = ANY(public.valid_child_capabilities())
    ORDER BY c
  )
);

CREATE OR REPLACE FUNCTION public.request_child_capability(
  p_child_id uuid,
  p_capability text,
  p_note text DEFAULT NULL
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
DECLARE
  v_role text;
  v_owner uuid;
  v_name text;
  v_child text;
  v_label text;
BEGIN
  SELECT role INTO v_role
    FROM public.child_members
   WHERE child_id = p_child_id AND user_id = auth.uid();
  IF v_role IS NULL THEN
    RAISE EXCEPTION 'not a member';
  END IF;

  SELECT user_id INTO v_owner
    FROM public.child_members
   WHERE child_id = p_child_id AND role = 'owner'
   LIMIT 1;
  IF v_owner IS NULL THEN
    SELECT user_id INTO v_owner FROM public.children WHERE id = p_child_id;
  END IF;

  v_label := COALESCE(NULLIF(trim(p_note), ''), p_capability);

  INSERT INTO public.child_access_activity
    (child_id, actor_user_id, target_user_id, event_type, role, detail)
  VALUES (
    p_child_id,
    auth.uid(),
    v_owner,
    'access_requested',
    v_role,
    v_label
  );

  IF v_owner IS NULL OR v_owner = auth.uid() THEN
    RETURN;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_owner) THEN
    RETURN;
  END IF;

  SELECT COALESCE(NULLIF(trim(p.full_name), ''), NULLIF(trim(p.username), ''), 'สมาชิก')
    INTO v_name
    FROM public.profiles p
   WHERE p.id = auth.uid();
  v_name := COALESCE(v_name, 'สมาชิก');

  SELECT COALESCE(NULLIF(trim(c.name), ''), 'ลูก')
    INTO v_child
    FROM public.children c
   WHERE c.id = p_child_id;
  v_child := COALESCE(v_child, 'ลูก');

  INSERT INTO public.notifications (user_id, category, title, body)
  VALUES (
    v_owner,
    'normal',
    'คำขอเพิ่มสิทธิ์',
    v_name || ' ขอสิทธิ์ "' || v_label || '" สำหรับ ' || v_child
  );
END;
$function$;

REVOKE ALL ON FUNCTION public.request_child_capability(uuid, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_child_capability(uuid, text, text) TO authenticated;
