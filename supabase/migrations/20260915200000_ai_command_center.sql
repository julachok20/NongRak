-- Migration: 20260915200000_ai_command_center.sql
--
-- The AI command center in the ai-chat edge function proposes record commands as signed tickets;
-- a command runs only after the person taps Confirm. These two tables back that:
--
--   ai_command_runs  - one row per ticket, keyed by the ticket id, so a ticket can run once. The
--                      row is written with the person's own login before the command runs.
--   ai_command_trash - the previous version of any row the AI edits or deletes (phase 2), kept 30
--                      days so it can be restored. Only its owner can read it.
--
-- Nothing here widens what a person may write: the commands themselves run under the existing
-- RLS on each record table (child_can / is_child_member).

create table if not exists public.ai_command_runs (
  ticket_id  uuid primary key,
  user_id    uuid not null default auth.uid() references auth.users(id) on delete cascade,
  command    text not null,
  child_id   uuid not null,
  status     text not null default 'running' check (status in ('running', 'done', 'failed')),
  error      text,
  created_at timestamptz not null default now()
);

alter table public.ai_command_runs enable row level security;

drop policy if exists "ai_command_runs_select_own" on public.ai_command_runs;
create policy "ai_command_runs_select_own" on public.ai_command_runs
  for select using (user_id = auth.uid());

drop policy if exists "ai_command_runs_insert_own" on public.ai_command_runs;
create policy "ai_command_runs_insert_own" on public.ai_command_runs
  for insert with check (user_id = auth.uid() and public.is_child_member(child_id));

drop policy if exists "ai_command_runs_update_own" on public.ai_command_runs;
create policy "ai_command_runs_update_own" on public.ai_command_runs
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

create index if not exists ai_command_runs_user_created_idx
  on public.ai_command_runs (user_id, created_at desc);

create table if not exists public.ai_command_trash (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
  child_id    uuid not null,
  table_name  text not null,
  row_id      text not null,
  action      text not null check (action in ('update', 'delete')),
  old_row     jsonb not null,
  ticket_id   uuid,
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default (now() + interval '30 days'),
  restored_at timestamptz
);

alter table public.ai_command_trash enable row level security;

drop policy if exists "ai_command_trash_select_own" on public.ai_command_trash;
create policy "ai_command_trash_select_own" on public.ai_command_trash
  for select using (user_id = auth.uid());

drop policy if exists "ai_command_trash_insert_own" on public.ai_command_trash;
create policy "ai_command_trash_insert_own" on public.ai_command_trash
  for insert with check (user_id = auth.uid() and public.is_child_member(child_id));

drop policy if exists "ai_command_trash_update_own" on public.ai_command_trash;
create policy "ai_command_trash_update_own" on public.ai_command_trash
  for update using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Lets ai-chat drop the copy it just made when the edit or delete it guarded did not go through.
drop policy if exists "ai_command_trash_delete_own" on public.ai_command_trash;
create policy "ai_command_trash_delete_own" on public.ai_command_trash
  for delete using (user_id = auth.uid());

create index if not exists ai_command_trash_user_created_idx
  on public.ai_command_trash (user_id, created_at desc);

-- Daily clean-up: trash past its 30 days, run records older than 60 days.
do $$
begin
  if not exists (select 1 from cron.job where jobname = 'ai-command-center-purge') then
    perform cron.schedule(
      'ai-command-center-purge',
      '17 3 * * *',
      $job$
        delete from public.ai_command_trash where expires_at < now();
        delete from public.ai_command_runs where created_at < now() - interval '60 days';
      $job$
    );
  end if;
end
$$;
