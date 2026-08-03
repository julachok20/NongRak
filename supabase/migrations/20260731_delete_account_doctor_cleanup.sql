-- Strengthen delete_account for doctor-only accounts (2026-07-31).
-- profiles CASCADE covers most rows; explicit doctor cleanup avoids edge-case FK blocks.

CREATE OR REPLACE FUNCTION public.delete_account()
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'auth'
AS $function$
DECLARE
  uid uuid := auth.uid();
BEGIN
  IF uid IS NULL THEN
    RAISE EXCEPTION 'Not authenticated';
  END IF;

  -- Doctor operational data (safe if user is parent-only — zero rows match).
  DELETE FROM public.vaccine_verification_requests WHERE doctor_id = uid;
  DELETE FROM public.doctor_availability WHERE doctor_id = uid;
  DELETE FROM public.doctor_special_dates WHERE doctor_id = uid;
  DELETE FROM public.doctor_appointment_slots WHERE doctor_id = uid;
  DELETE FROM public.appointments WHERE doctor_id = uid;
  DELETE FROM public.doctor_profiles WHERE id = uid;

  -- App data (children/chat/notifications cascade from profiles).
  DELETE FROM public.profiles WHERE id = uid;

  -- Auth account (cascades identities/sessions).
  DELETE FROM auth.users WHERE id = uid;
END;
$function$;
