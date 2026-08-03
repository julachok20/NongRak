-- Doctors must read parent booking/intake data for children with appointments or links at their clinic.

-- pediaconnect_patients: also readable when doctor has any appointment with that child
DROP POLICY IF EXISTS pediaconnect_patients_doctor_appt_select ON public.pediaconnect_patients;
CREATE POLICY pediaconnect_patients_doctor_appt_select ON public.pediaconnect_patients
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.appointments a
      WHERE a.child_id = pediaconnect_patients.child_id
        AND a.doctor_id = auth.uid()
    )
  );

-- pediaconnect_patient_parents: same via patient join
DROP POLICY IF EXISTS pediaconnect_parents_doctor_appt_select ON public.pediaconnect_patient_parents;
CREATE POLICY pediaconnect_parents_doctor_appt_select ON public.pediaconnect_patient_parents
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.pediaconnect_patients p
      JOIN public.appointments a ON a.child_id = p.child_id
      WHERE p.id = pediaconnect_patient_parents.patient_id
        AND a.doctor_id = auth.uid()
    )
  );

-- children: basic profile for doctor's patients (directory + schedule)
DROP POLICY IF EXISTS children_doctor_patient_select ON public.children;
CREATE POLICY children_doctor_patient_select ON public.children
  FOR SELECT TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.appointments a
      WHERE a.child_id = children.id AND a.doctor_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM public.pediaconnect_doctor_links l
      WHERE l.child_id = children.id
        AND l.doctor_id = auth.uid()
        AND l.status IN ('pending', 'approved')
    )
  );
