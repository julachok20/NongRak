-- Doctor vaccine certificate upload + vaccine book write for linked patients.
-- Fixes: storage RLS on medical-docs when doctor uploads vaccine PDFs, and
-- vaccine_records INSERT/UPDATE for doctors with an appointment or Pedia link.

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

-- vaccine_records: doctor may mark doses completed / attach certificate path
DROP POLICY IF EXISTS vaccine_records_doctor_patient_insert ON public.vaccine_records;
CREATE POLICY vaccine_records_doctor_patient_insert ON public.vaccine_records
  FOR INSERT TO authenticated
  WITH CHECK (public.doctor_can_access_child(child_id));

DROP POLICY IF EXISTS vaccine_records_doctor_patient_update ON public.vaccine_records;
CREATE POLICY vaccine_records_doctor_patient_update ON public.vaccine_records
  FOR UPDATE TO authenticated
  USING (public.doctor_can_access_child(child_id))
  WITH CHECK (public.doctor_can_access_child(child_id));

-- medical-docs layout: {parent_user_id}/{child_id}/certs/vaccine_{record_id}.pdf
DROP POLICY IF EXISTS "medical-docs insert doctor patient" ON storage.objects;
CREATE POLICY "medical-docs insert doctor patient" ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (
    bucket_id = 'medical-docs'
    AND (storage.foldername(name))[2] IS NOT NULL
    AND public.doctor_can_access_child(((storage.foldername(name))[2])::uuid)
  );

DROP POLICY IF EXISTS "medical-docs update doctor patient" ON storage.objects;
CREATE POLICY "medical-docs update doctor patient" ON storage.objects
  FOR UPDATE TO authenticated
  USING (
    bucket_id = 'medical-docs'
    AND (storage.foldername(name))[2] IS NOT NULL
    AND public.doctor_can_access_child(((storage.foldername(name))[2])::uuid)
  )
  WITH CHECK (
    bucket_id = 'medical-docs'
    AND (storage.foldername(name))[2] IS NOT NULL
    AND public.doctor_can_access_child(((storage.foldername(name))[2])::uuid)
  );

DROP POLICY IF EXISTS "medical-docs read doctor patient" ON storage.objects;
CREATE POLICY "medical-docs read doctor patient" ON storage.objects
  FOR SELECT TO authenticated
  USING (
    bucket_id = 'medical-docs'
    AND (storage.foldername(name))[2] IS NOT NULL
    AND public.doctor_can_access_child(((storage.foldername(name))[2])::uuid)
  );

-- medical_documents: doctor may register vaccine certs in the parent's vault
DROP POLICY IF EXISTS medical_documents_doctor_patient_insert ON public.medical_documents;
CREATE POLICY medical_documents_doctor_patient_insert ON public.medical_documents
  FOR INSERT TO authenticated
  WITH CHECK (public.doctor_can_access_child(child_id));
