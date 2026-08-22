-- Auto-pushed LINE reminders for medicine doses and sleep times.
--
-- Vaccines already have their own 3/2/1-day job (line_due_vaccine_reminders). This covers the
-- two things that are useless unless they arrive AT the moment they are due: a dose and a bedtime.

-- Dedup log. Without this, a cron whose window overlaps the previous run (deliberately, so a
-- reminder is never lost between runs) would push the same dose twice.
create table if not exists public.line_care_reminder_log (
  id uuid primary key default gen_random_uuid(),
  kind text not null check (kind in ('medicine', 'sleep')),
  ref_id uuid not null,
  -- A sleep schedule is one row that repeats every day, so ref_id alone is not unique per send.
  -- The Bangkok date it fired on is what makes it one-per-day.
  slot date not null,
  sent_at timestamptz not null default now(),
  unique (kind, ref_id, slot)
);

create index if not exists line_care_reminder_log_sent_idx
  on public.line_care_reminder_log (sent_at desc);

alter table public.line_care_reminder_log enable row level security;
-- No policies: only the service role (the cron function) touches this table.

/**
 * Everything due to be pushed in the next p_window_minutes, already joined to the LINE account
 * that should receive it.
 *
 * security definer so the cron function reads across every family; it is never exposed to a
 * client (execute is revoked from anon/authenticated below).
 */
create or replace function public.line_due_care_reminders(p_window_minutes int default 35)
returns table (
  line_user_id text,
  lang text,
  kind text,
  ref_id uuid,
  slot date,
  child_name text,
  title text,
  at_local text
)
language sql
security definer
set search_path = public
as $$
  -- Medicine: one row per dose still open.
  select
    ll.line_user_id,
    coalesce(lp.lang, 'th')                                             as lang,
    'medicine'::text                                                    as kind,
    md.id                                                               as ref_id,
    ((md.scheduled_at at time zone 'Asia/Bangkok')::date)               as slot,
    c.name                                                              as child_name,
    ms.medicine_name
      || coalesce(' ' || trim(trailing '.0' from ms.dosage::text)
                       || ' ' || coalesce(ms.dosage_unit, ''), '')      as title,
    to_char(md.scheduled_at at time zone 'Asia/Bangkok', 'HH24:MI')     as at_local
  from medication_doses md
  join children   c  on c.id = md.child_id
  join line_links ll on ll.user_id = c.user_id and ll.notify_enabled
  left join line_prefs lp           on lp.line_user_id = ll.line_user_id
  left join medication_schedules ms on ms.id = md.schedule_id
  where
    -- status is NULL for a dose nobody has touched, so coalesce before the NOT IN: a bare
    -- `not in (...)` against NULL yields NULL and drops exactly the doses still due.
    coalesce(md.status, 'pending') not in ('taken', 'skipped')
    and md.scheduled_at >= now()
    and md.scheduled_at <  now() + make_interval(mins => p_window_minutes)
    and not exists (
      select 1 from line_care_reminder_log l
      where l.kind = 'medicine' and l.ref_id = md.id
    )

  union all

  -- Sleep: one row per schedule whose start time falls in the window today.
  select
    ll.line_user_id,
    coalesce(lp.lang, 'th')                                             as lang,
    'sleep'::text                                                       as kind,
    ss.id                                                               as ref_id,
    (now() at time zone 'Asia/Bangkok')::date                           as slot,
    c.name                                                              as child_name,
    coalesce(nullif(btrim(ss.label), ''),
             case when ss.sleep_type = 'nap' then 'Nap' else 'Sleep' end) as title,
    substring(ss.start_time from 1 for 5)                               as at_local
  from sleep_schedules ss
  join children   c  on c.id = ss.child_id
  join line_links ll on ll.user_id = c.user_id and ll.notify_enabled
  left join line_prefs lp on lp.line_user_id = ll.line_user_id
  where
    -- start_time is TEXT ('21:30'), not a time column — cast before any arithmetic.
    ss.start_time ~ '^[0-2][0-9]:[0-5][0-9]'
    and (
      ss.days_of_week is null
      or upper(btrim(ss.days_of_week)) = 'ALL'
      or upper(trim(to_char(now() at time zone 'Asia/Bangkok', 'DY')))
         = any (string_to_array(upper(replace(ss.days_of_week, ' ', '')), ','))
    )
    -- Today's Bangkok date carrying the schedule's local clock time, converted back to an
    -- absolute instant so it can be compared against now().
    and (((now() at time zone 'Asia/Bangkok')::date
          + substring(ss.start_time from 1 for 5)::time) at time zone 'Asia/Bangkok') >= now()
    and (((now() at time zone 'Asia/Bangkok')::date
          + substring(ss.start_time from 1 for 5)::time) at time zone 'Asia/Bangkok')
        <  now() + make_interval(mins => p_window_minutes)
    and not exists (
      select 1 from line_care_reminder_log l
      where l.kind = 'sleep'
        and l.ref_id = ss.id
        and l.slot = (now() at time zone 'Asia/Bangkok')::date
    );
$$;

revoke all on function public.line_due_care_reminders(int) from public, anon, authenticated;
