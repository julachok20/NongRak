-- Migration: a direct breastfeed is measured in minutes, not millilitres
-- Date: 2026-09-21
--
-- The milk form asked every "นมแม่" feed for a volume, with a slider starting at 30 ml. A mother
-- feeding at the breast cannot know that, so she guessed (wrong data) or gave up. A direct feed now
-- records how long it took, and volume_ml stays 0 — which 7 of the 13 existing rows already hold,
-- so 0 has meant "not measured" in practice all along. Every total that sums volume_ml therefore
-- keeps counting only milk somebody actually measured.

alter table public.milk_logs
  add column if not exists duration_min integer;

alter table public.milk_logs drop constraint if exists milk_logs_duration_range;
alter table public.milk_logs add constraint milk_logs_duration_range
  check (duration_min is null or duration_min between 1 and 180);

-- No "volume > 0 or duration set" check: the info-ask answer (ChildViewModel) records the feeding
-- type as a zero-volume row, and that writer must keep working.

comment on column public.milk_logs.duration_min is 'Minutes at the breast for a direct feed (volume_ml is 0 then). Null for bottle and formula feeds.';

-- The activity feed printed "breast 0 ml" for such a feed; show the minutes instead.
CREATE OR REPLACE FUNCTION public.list_child_activity_feed(p_child_id uuid, p_limit integer DEFAULT 80)
 RETURNS TABLE(source_id uuid, kind text, category text, occurred_at timestamp with time zone, detail text, note text, author_id uuid, author_name text, author_avatar text)
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
BEGIN
  IF NOT public.is_child_member(p_child_id) THEN RAISE EXCEPTION 'not authorized'; END IF;
  RETURN QUERY
  WITH feed AS (
    SELECT g.id, 'growth'::text k, 'growth'::text c, g.recorded_at ts,
           trim(concat_ws(' · ', nullif(g.weight_kg::text,'')||' kg',
                                 nullif(g.height_cm::text,'')||' cm')) d,
           g.notes n, g.created_by cb
      FROM public.growth_records g WHERE g.child_id = p_child_id
    UNION ALL
    SELECT v.id, 'vaccine', 'medical', COALESCE(v.date_given::timestamptz, v.created_at),
           v.vaccine_name, v.place, v.created_by
      FROM public.vaccine_records v
     WHERE v.child_id = p_child_id AND v.date_given IS NOT NULL
    UNION ALL
    SELECT m.id, 'milk', 'nutrition', m.logged_at,
           CASE WHEN m.duration_min IS NOT NULL
                THEN concat_ws(' · ', m.milk_type, m.duration_min::text||' min', m.side)
                ELSE concat_ws(' ', m.milk_type, m.volume_ml::text||' ml') END,
           m.notes, m.created_by
      FROM public.milk_logs m WHERE m.child_id = p_child_id
    UNION ALL
    SELECT f.id, 'food', 'nutrition', f.log_date::timestamptz,
           concat_ws(' · ', f.meal_type, f.item_name), NULL, f.created_by
      FROM public.food_logs f WHERE f.child_id = p_child_id
    UNION ALL
    SELECT w.id, 'waste', 'daily', w.recorded_at,
           concat_ws(' · ', w.waste_type, w.consistency), w.notes, w.created_by
      FROM public.waste_records w WHERE w.child_id = p_child_id
    UNION ALL
    SELECT s.id, 'sleep', 'daily', s.start_time,
           concat_ws(' · ', s.sleep_type,
             CASE WHEN s.end_time IS NOT NULL
                  THEN round(extract(epoch FROM s.end_time - s.start_time)/60)::text||' min' END),
           s.notes, s.created_by
      FROM public.sleep_logs s WHERE s.child_id = p_child_id
    UNION ALL
    SELECT h.id, 'symptom', 'medical', h.created_at,
           h.symptoms::text, h.notes, h.created_by
      FROM public.health_symptom_logs h WHERE h.child_id = p_child_id
    UNION ALL
    SELECT d.id, 'medicine', 'medical', d.taken_at, d.status, NULL, d.created_by
      FROM public.medication_doses d
     WHERE d.child_id = p_child_id AND d.taken_at IS NOT NULL
    UNION ALL
    -- An abnormality the caregiver flagged outranks the note; a parent scanning the day
    -- should see it without opening anything.
    SELECT cl.id, 'care_'||cl.kind, 'care', cl.logged_at,
           NULLIF(cl.abnormal, ''), COALESCE(NULLIF(cl.note,''), NULLIF(cl.mood,'')), cl.created_by
      FROM public.care_logs cl WHERE cl.child_id = p_child_id
  )
  SELECT fd.id, fd.k, fd.c, fd.ts, fd.d, fd.n, fd.cb,
         COALESCE(cm.display_name, pr.username, pr.full_name, 'ผู้ดูแล'), pr.avatar_url
    FROM feed fd
    LEFT JOIN public.profiles pr ON pr.id = fd.cb
    LEFT JOIN public.child_members cm ON cm.user_id = fd.cb AND cm.child_id = p_child_id
   WHERE fd.ts IS NOT NULL
   ORDER BY fd.ts DESC
   LIMIT p_limit;
END; $function$;
