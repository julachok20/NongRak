-- Owner-only DELETE for child data (2026-07-31).
-- Non-owners (nanny, viewer, etc.) may still log daily care; only owners may delete rows.
-- Requires public.is_child_owner(uuid) (added in Batch W role enforcement).

DO $$
DECLARE
  tbl text;
  child_col text;
  pol record;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'children',
    'allergies', 'chronic_conditions', 'growth_records', 'health_records',
    'vaccine_records', 'medical_documents', 'medication_schedules', 'medication_doses',
    'sleep_logs', 'sleep_schedules', 'sleep_sound_events',
    'food_logs', 'milk_logs', 'exercise_logs', 'waste_records',
    'diary_memories', 'development_milestones', 'doctor_visits',
    'symptom_logs', 'ai_analysis', 'ai_tasks'
  ]
  LOOP
    IF to_regclass('public.' || tbl) IS NULL THEN
      CONTINUE;
    END IF;

    child_col := CASE WHEN tbl = 'children' THEN 'id' ELSE 'child_id' END;

    FOR pol IN
      SELECT policyname
      FROM pg_policies
      WHERE schemaname = 'public' AND tablename = tbl AND cmd = 'DELETE'
    LOOP
      EXECUTE format('DROP POLICY IF EXISTS %I ON public.%I', pol.policyname, tbl);
    END LOOP;

    EXECUTE format(
      'CREATE POLICY %I ON public.%I FOR DELETE TO authenticated USING (public.is_child_owner(%I))',
      tbl || '_owner_delete',
      tbl,
      child_col
    );
  END LOOP;
END $$;
