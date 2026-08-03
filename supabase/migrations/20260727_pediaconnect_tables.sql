-- Pedia Connect: hospital-facing patient + guardian records (child SSOT remains `children`).

CREATE TABLE IF NOT EXISTS public.pediaconnect_accounts (
  user_id uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  full_name text,
  phone text,
  email text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.pediaconnect_patients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  child_id uuid NOT NULL REFERENCES public.children (id) ON DELETE CASCADE,
  name text NOT NULL,
  birthdate date,
  gender text,
  blood_group text,
  allergies text,
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pediaconnect_patients_child_unique UNIQUE (child_id)
);

CREATE TABLE IF NOT EXISTS public.pediaconnect_patient_parents (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES public.pediaconnect_patients (id) ON DELETE CASCADE,
  user_id uuid REFERENCES auth.users (id) ON DELETE SET NULL,
  full_name text NOT NULL,
  relationship text,
  phone text,
  email text,
  is_main_parent boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS pediaconnect_one_main_parent_per_patient
  ON public.pediaconnect_patient_parents (patient_id)
  WHERE is_main_parent = true;

ALTER TABLE public.pediaconnect_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pediaconnect_patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.pediaconnect_patient_parents ENABLE ROW LEVEL SECURITY;

-- Accounts: self only
CREATE POLICY pediaconnect_accounts_self ON public.pediaconnect_accounts
  FOR ALL TO authenticated
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

-- Patients: owner or any child member may read; write requires edit_profile
CREATE POLICY pediaconnect_patients_select ON public.pediaconnect_patients
  FOR SELECT TO authenticated
  USING (
    owner_user_id = auth.uid()
    OR public.is_child_member(child_id)
  );

CREATE POLICY pediaconnect_patients_insert ON public.pediaconnect_patients
  FOR INSERT TO authenticated
  WITH CHECK (
    owner_user_id = auth.uid()
    AND public.is_child_member(child_id)
    AND public.child_can(child_id, 'edit_profile')
  );

CREATE POLICY pediaconnect_patients_update ON public.pediaconnect_patients
  FOR UPDATE TO authenticated
  USING (
    owner_user_id = auth.uid()
    AND public.is_child_member(child_id)
    AND public.child_can(child_id, 'edit_profile')
  )
  WITH CHECK (
    owner_user_id = auth.uid()
    AND public.is_child_member(child_id)
    AND public.child_can(child_id, 'edit_profile')
  );

-- Parents: visible when patient is visible
CREATE POLICY pediaconnect_parents_select ON public.pediaconnect_patient_parents
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.pediaconnect_patients p
      WHERE p.id = patient_id
        AND (p.owner_user_id = auth.uid() OR public.is_child_member(p.child_id))
    )
  );

CREATE POLICY pediaconnect_parents_write ON public.pediaconnect_patient_parents
  FOR ALL TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.pediaconnect_patients p
      WHERE p.id = patient_id
        AND p.owner_user_id = auth.uid()
        AND public.child_can(p.child_id, 'edit_profile')
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.pediaconnect_patients p
      WHERE p.id = patient_id
        AND p.owner_user_id = auth.uid()
        AND public.child_can(p.child_id, 'edit_profile')
    )
  );

CREATE OR REPLACE FUNCTION public.register_pediaconnect_intake(
  p_child_id uuid,
  p_name text,
  p_birthdate date,
  p_gender text,
  p_blood_group text,
  p_allergies text,
  p_notes text,
  p_main_user_id uuid,
  p_main_full_name text,
  p_main_relationship text,
  p_main_phone text,
  p_main_email text
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_patient_id uuid;
  v_profile record;
  v_email text;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'not authenticated';
  END IF;
  IF NOT public.is_child_member(p_child_id) OR NOT public.child_can(p_child_id, 'edit_profile') THEN
    RAISE EXCEPTION 'insufficient permissions for child';
  END IF;

  SELECT full_name, phone INTO v_profile FROM public.profiles WHERE id = v_uid;
  SELECT email INTO v_email FROM auth.users WHERE id = v_uid;
  INSERT INTO public.pediaconnect_accounts (user_id, full_name, phone, email, updated_at)
  VALUES (v_uid, v_profile.full_name, v_profile.phone, v_email, now())
  ON CONFLICT (user_id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    updated_at = now();

  INSERT INTO public.pediaconnect_patients (
    owner_user_id, child_id, name, birthdate, gender, blood_group, allergies, notes, updated_at
  ) VALUES (
    v_uid, p_child_id, p_name, p_birthdate, p_gender, p_blood_group, p_allergies, p_notes, now()
  )
  ON CONFLICT (child_id) DO UPDATE SET
    owner_user_id = EXCLUDED.owner_user_id,
    name = EXCLUDED.name,
    birthdate = EXCLUDED.birthdate,
    gender = EXCLUDED.gender,
    blood_group = EXCLUDED.blood_group,
    allergies = EXCLUDED.allergies,
    notes = EXCLUDED.notes,
    updated_at = now()
  RETURNING id INTO v_patient_id;

  DELETE FROM public.pediaconnect_patient_parents
  WHERE patient_id = v_patient_id AND is_main_parent = true;

  INSERT INTO public.pediaconnect_patient_parents (
    patient_id, user_id, full_name, relationship, phone, email, is_main_parent
  ) VALUES (
    v_patient_id,
    p_main_user_id,
    COALESCE(NULLIF(trim(p_main_full_name), ''), 'Guardian'),
    p_main_relationship,
    p_main_phone,
    p_main_email,
    true
  );

  RETURN v_patient_id;
END;
$$;

REVOKE ALL ON FUNCTION public.register_pediaconnect_intake FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_pediaconnect_intake TO authenticated;
