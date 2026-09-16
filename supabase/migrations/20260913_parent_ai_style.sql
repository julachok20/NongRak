-- How NongRak AI should talk to each parent, learnt by Typhoon from the parent's own chat messages
-- (e.g. first-time mom, worries easily, prefers short answers). ai-chat adds it to every Spark
-- reply's system prompt and refreshes it in the background.
--
-- PDPA: the parent sees the note in Settings, can switch learning off (`enabled`) and reset it.
-- The learning prompt forbids sensitive inferences (religion, politics, income, mental-health
-- diagnoses) because the parent reads what is written here.
create table if not exists public.parent_ai_style (
  user_id uuid primary key references auth.users(id) on delete cascade,
  style text not null default '',
  enabled boolean not null default true,
  answers_since_update integer not null default 0,
  updated_at timestamptz not null default now()
);

alter table public.parent_ai_style enable row level security;

drop policy if exists "Parents manage own AI style" on public.parent_ai_style;
create policy "Parents manage own AI style" on public.parent_ai_style
  for all using (user_id = auth.uid()) with check (user_id = auth.uid());
