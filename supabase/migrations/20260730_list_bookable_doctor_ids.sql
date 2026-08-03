-- Server-side bookable doctor directory (bypasses RLS gaps for parent search).
CREATE OR REPLACE FUNCTION public.list_bookable_doctor_ids()
RETURNS TABLE(doctor_id uuid)
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
  WITH bangkok AS (
    SELECT (now() AT TIME ZONE 'Asia/Bangkok') AS ts
  ),
  today AS (
    SELECT ts::date AS d, ts::time AS t FROM bangkok
  )
  SELECT DISTINCT s.doctor_id
  FROM public.doctor_appointment_slots s
  CROSS JOIN today bt
  WHERE s.status = 'open'
    AND s.booked_count < s.capacity
    AND (
      s.slot_date::date > bt.d
      OR (s.slot_date::date = bt.d AND s.start_time::time >= bt.t)
    )
    AND EXISTS (
      SELECT 1
      FROM public.doctor_availability da
      WHERE da.doctor_id = s.doctor_id
        AND da.is_day_off = false
        AND da.start_time IS NOT NULL
        AND da.end_time IS NOT NULL
    );
$$;

REVOKE ALL ON FUNCTION public.list_bookable_doctor_ids() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.list_bookable_doctor_ids() TO authenticated;
