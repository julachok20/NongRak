-- Pedia Connect: one-time child ↔ doctor registration link (approve before queue booking).

CREATE TABLE IF NOT EXISTS public.pediaconnect_doctor_links (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  child_id uuid NOT NULL REFERENCES public.children (id) ON DELETE CASCADE,
  owner_user_id uuid NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'approved', 'rejected', 'revoked')),
  note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pediaconnect_doctor_links_pair_unique UNIQUE (doctor_id, child_id)
);

CREATE INDEX IF NOT EXISTS pediaconnect_doctor_links_doctor_status_idx
  ON public.pediaconnect_doctor_links (doctor_id, status);

ALTER TABLE public.pediaconnect_doctor_links ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION public.pediaconnect_links_set_owner()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.owner_user_id IS NULL THEN
    NEW.owner_user_id := auth.uid();
  END IF;
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS pediaconnect_links_owner_trg ON public.pediaconnect_doctor_links;
CREATE TRIGGER pediaconnect_links_owner_trg
  BEFORE INSERT OR UPDATE ON public.pediaconnect_doctor_links
  FOR EACH ROW EXECUTE FUNCTION public.pediaconnect_links_set_owner();

-- Parent: read own links; insert pending; revoke own
CREATE POLICY pediaconnect_links_parent_select ON public.pediaconnect_doctor_links
  FOR SELECT TO authenticated
  USING (owner_user_id = auth.uid());

CREATE POLICY pediaconnect_links_parent_insert ON public.pediaconnect_doctor_links
  FOR INSERT TO authenticated
  WITH CHECK (
    owner_user_id = auth.uid()
    AND status = 'pending'
    AND public.is_child_member(child_id)
    AND public.child_can(child_id, 'edit_profile')
  );

CREATE POLICY pediaconnect_links_parent_update ON public.pediaconnect_doctor_links
  FOR UPDATE TO authenticated
  USING (owner_user_id = auth.uid())
  WITH CHECK (
    owner_user_id = auth.uid()
    AND status IN ('pending', 'revoked')
  );

-- Doctor: read & resolve links addressed to them
CREATE POLICY pediaconnect_links_doctor_select ON public.pediaconnect_doctor_links
  FOR SELECT TO authenticated
  USING (doctor_id = auth.uid());

CREATE POLICY pediaconnect_links_doctor_update ON public.pediaconnect_doctor_links
  FOR UPDATE TO authenticated
  USING (doctor_id = auth.uid())
  WITH CHECK (
    doctor_id = auth.uid()
    AND status IN ('approved', 'rejected')
  );

-- Doctors with a pending/approved link may read Pedia intake for that child
CREATE POLICY pediaconnect_patients_doctor_select ON public.pediaconnect_patients
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.pediaconnect_doctor_links l
      WHERE l.child_id = pediaconnect_patients.child_id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );

CREATE POLICY pediaconnect_parents_doctor_select ON public.pediaconnect_patient_parents
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.pediaconnect_patients p
      JOIN public.pediaconnect_doctor_links l ON l.child_id = p.child_id
      WHERE p.id = pediaconnect_patient_parents.patient_id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );
