-- 1) SECURITY DEFINER RPC: doctor registers vaccine PDF in parent คลังเอกสาร (bypasses INSERT RLS).
-- 2) doctor_visits.appointment_id — one clinical record per appointment (load + update, not duplicate).

CREATE OR REPLACE FUNCTION public.doctor_can_access_child(p_child_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.appointments a
    WHERE a.child_id = p_child_id
      AND a.doctor_id = auth.uid()
  )
  OR EXISTS (
    SELECT 1 FROM public.pediaconnect_doctor_links l
    WHERE l.child_id = p_child_id
      AND l.doctor_id = auth.uid()
      AND l.status IN ('pending', 'approved')
  );
$$;

REVOKE ALL ON FUNCTION public.doctor_can_access_child(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.doctor_can_access_child(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION public.register_vaccine_certificate_document(
  p_child_id uuid,
  p_file_path text,
  p_file_name text,
  p_file_size_bytes bigint
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  IF NOT public.doctor_can_access_child(p_child_id) THEN
    RAISE EXCEPTION 'access_denied';
  END IF;
  IF EXISTS (
    SELECT 1 FROM public.medical_documents md WHERE md.file_path = p_file_path
  ) THEN
    RETURN;
  END IF;
  INSERT INTO public.medical_documents (
    child_id, file_name, file_path, mime_type, category, file_size_bytes, verified
  ) VALUES (
    p_child_id,
    p_file_name,
    p_file_path,
    'application/pdf',
    'certs',
    p_file_size_bytes,
    true
  );
END;
$$;

REVOKE ALL ON FUNCTION public.register_vaccine_certificate_document(uuid, text, text, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.register_vaccine_certificate_document(uuid, text, text, bigint) TO authenticated;

ALTER TABLE public.doctor_visits
  ADD COLUMN IF NOT EXISTS appointment_id uuid REFERENCES public.appointments (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS doctor_visits_one_per_appointment
  ON public.doctor_visits (appointment_id)
  WHERE appointment_id IS NOT NULL;

-- Doctors may read visits they authored (for reload on บันทึกการตรวจ)
DROP POLICY IF EXISTS doctor_visits_doctor_select ON public.doctor_visits;
CREATE POLICY doctor_visits_doctor_select ON public.doctor_visits
  FOR SELECT TO authenticated
  USING (doctor_id = auth.uid());
