-- กิจวัตรการดูแล: the child's repeating day (07:15 wake + temp, 08:30 bottle, 13:00 nap 2 ...),
-- plus one row per day per item recording that it actually happened.
--
-- Separate from child_activities on purpose: an activity is a dated one-off ("zoo on Saturday"),
-- a routine is the same item every day whose interesting fact is today's status and who ticked it.
create table if not exists public.care_routines (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references public.children(id) on delete cascade,
  time_of_day time not null,
  category text not null default 'other',
  title text not null,
  detail text not null default '',
  active boolean not null default true,
  created_by uuid not null default auth.uid() references auth.users(id),
  created_at timestamptz not null default now(),
  constraint care_routines_title_len check (char_length(title) between 1 and 200),
  constraint care_routines_detail_len check (char_length(detail) <= 1000)
);
create index if not exists care_routines_child on public.care_routines (child_id, time_of_day);

-- One tick per item per day; re-ticking updates the same row rather than stacking duplicates.
create table if not exists public.care_routine_logs (
  id uuid primary key default gen_random_uuid(),
  routine_id uuid not null references public.care_routines(id) on delete cascade,
  child_id uuid not null references public.children(id) on delete cascade,
  on_date date not null default (now() at time zone 'Asia/Bangkok')::date,
  status text not null default 'done' check (status in ('done','skipped')),
  note text not null default '',
  actor_id uuid not null default auth.uid() references auth.users(id),
  recorded_at timestamptz not null default now(),
  constraint care_routine_logs_note_len check (char_length(note) <= 500),
  unique (routine_id, on_date)
);
create index if not exists care_routine_logs_child_day on public.care_routine_logs (child_id, on_date);

alter table public.care_routines enable row level security;
alter table public.care_routine_logs enable row level security;

-- Everyone with access to the child sees the routine; only parents (or whoever created the item)
-- may change what the routine IS. A nanny follows the plan, they do not rewrite it.
create policy care_routines_read on public.care_routines
  for select using (public.is_child_member(child_id));
create policy care_routines_write on public.care_routines
  for insert with check (
    public.is_child_member(child_id)
    and exists (select 1 from public.child_members m
                where m.child_id = care_routines.child_id and m.user_id = auth.uid()
                  and m.role = any (array['owner','co_owner','member']))
  );
create policy care_routines_update on public.care_routines
  for update using (
    (created_by = auth.uid() and public.is_child_member(child_id))
    or exists (select 1 from public.child_members m
               where m.child_id = care_routines.child_id and m.user_id = auth.uid()
                 and m.role = any (array['owner','co_owner','member']))
  );
create policy care_routines_delete on public.care_routines
  for delete using (
    (created_by = auth.uid() and public.is_child_member(child_id))
    or exists (select 1 from public.child_members m
               where m.child_id = care_routines.child_id and m.user_id = auth.uid()
                 and m.role = any (array['owner','co_owner','member']))
  );

-- Ticking is the caregiver's whole job, so any member may write a log; correcting one is limited to
-- whoever recorded it or a parent, so a caregiver cannot quietly rewrite another shift's record.
create policy care_routine_logs_read on public.care_routine_logs
  for select using (public.is_child_member(child_id));
create policy care_routine_logs_write on public.care_routine_logs
  for insert with check (public.is_child_member(child_id) and actor_id = auth.uid());
create policy care_routine_logs_update on public.care_routine_logs
  for update using (
    (actor_id = auth.uid() and public.is_child_member(child_id))
    or exists (select 1 from public.child_members m
               where m.child_id = care_routine_logs.child_id and m.user_id = auth.uid()
                 and m.role = any (array['owner','co_owner','member']))
  );
create policy care_routine_logs_delete on public.care_routine_logs
  for delete using (
    (actor_id = auth.uid() and public.is_child_member(child_id))
    or exists (select 1 from public.child_members m
               where m.child_id = care_routine_logs.child_id and m.user_id = auth.uid()
                 and m.role = any (array['owner','co_owner','member']))
  );
