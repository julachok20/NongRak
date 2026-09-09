-- Migration: Health Book birth details on children + guardian address on profiles
-- Date: 2026-08-31
-- Description:
--   Home Info Ask writes structured answers into the same tables the Health Book already
--   reads. Birth lines on form page 1 were hardcoded empty because these columns did not
--   exist; guardian address was missing on profiles (name / relation / phone already live).
--
-- RLS: children and profiles already scope UPDATE to the owner. New columns inherit that.

ALTER TABLE public.children
    ADD COLUMN IF NOT EXISTS delivery_type text,
    ADD COLUMN IF NOT EXISTS place_of_birth text,
    ADD COLUMN IF NOT EXISTS gestational_weeks integer,
    ADD COLUMN IF NOT EXISTS birth_weight_g double precision,
    ADD COLUMN IF NOT EXISTS birth_length_cm double precision,
    ADD COLUMN IF NOT EXISTS birth_head_cm double precision;

COMMENT ON COLUMN public.children.delivery_type IS 'Canonical: natural | cesarean | assisted | other. Printed on Health Book page 1.';
COMMENT ON COLUMN public.children.place_of_birth IS 'Hospital / clinic / home of birth. Health Book สถานที่คลอด.';
COMMENT ON COLUMN public.children.gestational_weeks IS 'Gestational age at birth, whole weeks.';
COMMENT ON COLUMN public.children.birth_weight_g IS 'Birth weight in grams.';
COMMENT ON COLUMN public.children.birth_length_cm IS 'Birth length in centimetres.';
COMMENT ON COLUMN public.children.birth_head_cm IS 'Birth head circumference in centimetres.';

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS address text;

COMMENT ON COLUMN public.profiles.address IS 'Guardian home address for Health Book page 1.';
