-- Doctors with an appointment or Pedia Connect link to a child may read parent health
-- tables needed on ข้อมูลคนไข้ (vaccine book sync, allergies, chronic conditions).
-- Matches the patient gate in 20260730_doctor_read_patients_via_appointments.sql.

-- vaccine_records (parent vaccine book SSOT)
DROP POLICY IF EXISTS vaccine_records_doctor_patient_select ON public.vaccine_records;
CREATE POLICY vaccine_records_doctor_patient_select ON public.vaccine_records
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.appointments a
      WHERE a.child_id = vaccine_records.child_id
        AND a.doctor_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.pediaconnect_doctor_links l
      WHERE l.child_id = vaccine_records.child_id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );

-- allergies
DROP POLICY IF EXISTS allergies_doctor_patient_select ON public.allergies;
CREATE POLICY allergies_doctor_patient_select ON public.allergies
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.appointments a
      WHERE a.child_id = allergies.child_id
        AND a.doctor_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.pediaconnect_doctor_links l
      WHERE l.child_id = allergies.child_id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );

-- chronic_conditions
DROP POLICY IF EXISTS chronic_conditions_doctor_patient_select ON public.chronic_conditions;
CREATE POLICY chronic_conditions_doctor_patient_select ON public.chronic_conditions
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.appointments a
      WHERE a.child_id = chronic_conditions.child_id
        AND a.doctor_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.pediaconnect_doctor_links l
      WHERE l.child_id = chronic_conditions.child_id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );
