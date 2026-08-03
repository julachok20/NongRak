-- Parent "ค้นหาคุณหมอ" + booking must read OTHER doctors' published hours/slots.
-- Without these, RLS (doctor_id = auth.uid()) hides every row from parent accounts.

-- profiles: directory search needs other doctors' display names
DROP POLICY IF EXISTS "Authenticated users can view doctor directory profiles" ON public.profiles;
CREATE POLICY "Authenticated users can view doctor directory profiles"
  ON public.profiles
  FOR SELECT
  TO authenticated
  USING (
    id = auth.uid()
    OR 'doctor' = ANY (roles)
    OR lower(coalesce(account_type, '')) = 'doctor'
  );

-- doctor_availability: weekly hours (read-only for any signed-in user)
DROP POLICY IF EXISTS "Parents can view doctor availability" ON public.doctor_availability;
CREATE POLICY "Parents can view doctor availability"
  ON public.doctor_availability
  FOR SELECT
  TO authenticated
  USING (true);

-- doctor_appointment_slots: parents see open slots; doctors still see all of their own
DROP POLICY IF EXISTS "Parents can view open appointment slots" ON public.doctor_appointment_slots;
CREATE POLICY "Parents can view open appointment slots"
  ON public.doctor_appointment_slots
  FOR SELECT
  TO authenticated
  USING (status = 'open' OR doctor_id = auth.uid());

-- doctor_profiles: directory join (clinic name, specialty)
DROP POLICY IF EXISTS "Anyone authenticated can view doctor profiles" ON public.doctor_profiles;
CREATE POLICY "Anyone authenticated can view doctor profiles"
  ON public.doctor_profiles
  FOR SELECT
  TO authenticated
  USING (true);
