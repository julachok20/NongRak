-- =====================================================================
-- Migration: 20260817_oral_health_system.sql
-- Oral Health Pro System (ระบบสุขภาพช่องปากครบวงจร)
-- 1. Teeth Map Records (child_teeth_records)
-- 2. Oral Health Event & Milestone Logs (oral_health_events)
-- 3. Daily Oral Hygiene & Brushing Tracker (daily_oral_routines)
-- =====================================================================

-- 1. Teeth Map Records
CREATE TABLE IF NOT EXISTS public.child_teeth_records (
  child_id uuid NOT NULL REFERENCES public.children(id) ON DELETE CASCADE,
  tooth_id text NOT NULL,
  status text NOT NULL DEFAULT 'healthy', -- 'healthy', 'emerging', 'needs_care', 'missing'
  notes text,
  erupted_at date,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (child_id, tooth_id)
);

ALTER TABLE public.child_teeth_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY "child_teeth_records_select"
ON public.child_teeth_records FOR SELECT
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "child_teeth_records_insert"
ON public.child_teeth_records FOR INSERT
TO authenticated
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "child_teeth_records_update"
ON public.child_teeth_records FOR UPDATE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
)
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "child_teeth_records_delete"
ON public.child_teeth_records FOR DELETE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);


-- 2. Oral Health Events & Milestones
CREATE TABLE IF NOT EXISTS public.oral_health_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  child_id uuid NOT NULL REFERENCES public.children(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id),
  category text NOT NULL DEFAULT 'first_tooth', -- 'first_tooth', 'dentist_visit', 'treatment', 'other'
  event_name text NOT NULL,
  event_date date NOT NULL DEFAULT current_date,
  notes text,
  photo_urls text[] NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE public.oral_health_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY "oral_health_events_select"
ON public.oral_health_events FOR SELECT
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "oral_health_events_insert"
ON public.oral_health_events FOR INSERT
TO authenticated
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "oral_health_events_update"
ON public.oral_health_events FOR UPDATE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
)
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "oral_health_events_delete"
ON public.oral_health_events FOR DELETE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);


-- 3. Daily Oral Hygiene & Brushing Tracker
CREATE TABLE IF NOT EXISTS public.daily_oral_routines (
  child_id uuid NOT NULL REFERENCES public.children(id) ON DELETE CASCADE,
  routine_date date NOT NULL DEFAULT current_date,
  morning boolean NOT NULL DEFAULT false,
  lunch boolean NOT NULL DEFAULT false,
  bedtime boolean NOT NULL DEFAULT false,
  flossing boolean NOT NULL DEFAULT false,
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (child_id, routine_date)
);

ALTER TABLE public.daily_oral_routines ENABLE ROW LEVEL SECURITY;

CREATE POLICY "daily_oral_routines_select"
ON public.daily_oral_routines FOR SELECT
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "daily_oral_routines_insert"
ON public.daily_oral_routines FOR INSERT
TO authenticated
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "daily_oral_routines_update"
ON public.daily_oral_routines FOR UPDATE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
)
WITH CHECK (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);

CREATE POLICY "daily_oral_routines_delete"
ON public.daily_oral_routines FOR DELETE
TO authenticated
USING (
  child_id IN (
    SELECT id FROM public.children WHERE user_id = auth.uid()
    UNION
    SELECT child_id FROM public.child_members WHERE user_id = auth.uid()
  )
);
