-- QR sync: accept codes from child_access_invites (Share Access QR) not only legacy child_invite_codes.
-- Realtime: ensure co-parent sync tables broadcast postgres_changes to authenticated clients.

-- ---------------------------------------------------------------------------
-- Realtime publication (idempotent — skip tables already in supabase_realtime)
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'child_sync_requests',
    'child_members',
    'children',
    'health_records',
    'vaccine_records',
    'growth_records',
    'allergies'
  ]
  LOOP
    IF to_regclass('public.' || tbl) IS NOT NULL AND NOT EXISTS (
      SELECT 1
      FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime'
        AND schemaname = 'public'
        AND tablename = tbl
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE public.%I', tbl);
    END IF;
  END LOOP;
END $$;

-- ---------------------------------------------------------------------------
-- Resolve a scanned/typed access code → child_id (new QR table first, legacy fallback)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.resolve_child_id_from_access_code(p_code text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_input text := trim(p_code);
  v_code text := upper(v_input);
  v_short text;
  v_child_id uuid;
BEGIN
  IF v_input IS NULL OR v_input = '' THEN
    RETURN NULL;
  END IF;

  -- Extract 6-char code from nongrak://access?code=XXXXXX when present.
  SELECT upper(m[1]) INTO v_short
  FROM regexp_match(v_input, '[?&]code=([A-Za-z0-9]+)', 'i') AS m;

  IF v_short IS NULL THEN
    v_short := regexp_replace(v_code, '[^A-Z0-9]', '', 'g');
    IF length(v_short) > 8 THEN
      v_short := left(v_short, 6);
    END IF;
  END IF;

  IF to_regclass('public.child_access_invites') IS NOT NULL THEN
    SELECT i.child_id INTO v_child_id
    FROM public.child_access_invites i
    WHERE (
      (v_short IS NOT NULL AND v_short <> '' AND upper(i.code) = v_short)
      OR upper(trim(i.payload)) = v_code
      OR (v_short IS NOT NULL AND upper(i.payload) LIKE '%' || v_short || '%')
    )
      AND i.used_at IS NULL
      AND i.expires_at > now()
    FOR UPDATE;

    IF v_child_id IS NOT NULL THEN
      UPDATE public.child_access_invites
      SET used_at = now()
      WHERE child_id = v_child_id
        AND used_at IS NULL
        AND expires_at > now()
        AND (
          (v_short IS NOT NULL AND upper(code) = v_short)
          OR upper(trim(payload)) = v_code
          OR (v_short IS NOT NULL AND upper(payload) LIKE '%' || v_short || '%')
        );
      RETURN v_child_id;
    END IF;
  END IF;

  IF to_regclass('public.child_invite_codes') IS NOT NULL AND v_short IS NOT NULL AND v_short <> '' THEN
    SELECT c.child_id INTO v_child_id
    FROM public.child_invite_codes c
    WHERE upper(c.code) = v_short
      AND c.used_at IS NULL
      AND c.expires_at > now()
    FOR UPDATE;

    IF v_child_id IS NOT NULL THEN
      UPDATE public.child_invite_codes
      SET used_at = now()
      WHERE upper(code) = v_short AND used_at IS NULL;
      RETURN v_child_id;
    END IF;
  END IF;

  RETURN NULL;
END;
$$;

REVOKE ALL ON FUNCTION public.resolve_child_id_from_access_code(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.resolve_child_id_from_access_code(text) TO authenticated;

-- Patch the 4-arg overload used by the Android client (p_child_id, p_method, p_code, p_role).
-- Live DB already has RETURNS uuid — must drop before changing body (CREATE OR REPLACE cannot change return type).
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

  SELECT r.id INTO v_request_id
  FROM public.child_sync_requests r
  WHERE r.child_id = v_child_id
    AND r.requester_user_id = v_requester
    AND r.status = 'pending'
  LIMIT 1;

  IF v_request_id IS NOT NULL THEN
    RETURN v_request_id;
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
