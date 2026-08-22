-- Vaccine reminders over LINE, at 3, 2 and 1 days before the scheduled date.

-- Exactly-once bookkeeping. The daily job would otherwise re-send the same reminder every day
-- until the dose is given, which on a 300-a-month push quota empties the whole budget in days
-- and takes every health alert down with it.
create table if not exists public.line_reminder_log (
  vaccine_record_id uuid not null references public.vaccine_records(id) on delete cascade,
  days_before       int  not null check (days_before between 1 and 3),
  sent_at           timestamptz not null default now(),
  primary key (vaccine_record_id, days_before)
);

alter table public.line_reminder_log enable row level security;
-- No policies: written only by the reminder job (service role).

-- Everything due a reminder right now, already filtered for "not sent yet".
--
-- The day arithmetic runs in Asia/Bangkok, not UTC. On a UTC server the date rolls over at 07:00
-- Thai time, so a "1 day before" reminder would fire on the wrong calendar day for every family
-- -- which for a vaccine appointment is the difference between useful and actively misleading.
--
-- Joins children.user_id: the OWNER only. A second parent or nanny with child_members access
-- does not receive reminders. Deliberate for now -- widening it multiplies push cost against a
-- 300-a-month quota -- but revisit if shared-care families start missing appointments.
create or replace function public.line_due_vaccine_reminders()
returns table (
  vaccine_record_id uuid,
  line_user_id      text,
  lang              text,
  child_name        text,
  vaccine_name      text,
  scheduled_date    date,
  days_before       int
)
language sql
security definer
set search_path = public
as $$
  with today as (select (now() at time zone 'Asia/Bangkok')::date as d)
  select v.id, l.line_user_id, coalesce(p.lang, 'th'), c.name,
         v.vaccine_name, v.scheduled_date, (v.scheduled_date - t.d)::int
  from public.vaccine_records v
  cross join today t
  join public.children c   on c.id = v.child_id
  join public.line_links l on l.user_id = c.user_id and l.notify_enabled
  left join public.line_prefs p on p.line_user_id = l.line_user_id
  where v.date_given is null
    and v.scheduled_date is not null
    and (v.scheduled_date - t.d) in (1, 2, 3)
    and not exists (
      select 1 from public.line_reminder_log r
      where r.vaccine_record_id = v.id
        and r.days_before = (v.scheduled_date - t.d)
    );
$$;

revoke all on function public.line_due_vaccine_reminders() from public, anon, authenticated;
