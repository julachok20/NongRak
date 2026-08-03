-- Parent and doctor are separate roles; same auth user may hold both (same email, two app areas).
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS roles text[] NOT NULL DEFAULT ARRAY['parent']::text[];

UPDATE public.profiles
SET roles = ARRAY['doctor']::text[]
WHERE lower(coalesce(account_type, '')) = 'doctor'
  AND NOT ('doctor' = ANY (roles));

UPDATE public.profiles
SET roles = ARRAY['parent']::text[]
WHERE lower(coalesce(account_type, '')) <> 'doctor';

COMMENT ON COLUMN public.profiles.roles IS 'App areas this login may use: parent, doctor (both allowed).';
