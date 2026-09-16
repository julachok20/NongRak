-- Migration: 20260916120000_app_feedback.sql  (applied 2026-09-16)
--
-- App reviews from Settings > รีวิวแอปพลิเคชัน.
--
-- The app used to insert into a `feedbacks` table that no migration ever created, and wrapped the
-- insert in a runCatching that discarded the failure — so every review was lost while the parent
-- saw "thank you". This creates the table the screen writes to. Read reviews from the Supabase
-- dashboard (service role); a parent can only insert and read back their own.

create table if not exists public.feedbacks (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid default auth.uid() references auth.users(id) on delete set null,
  rating        smallint not null check (rating between 1 and 5),
  highlights    text[] not null default '{}',
  message       text not null default '' check (char_length(message) <= 500),
  contact_email text check (contact_email is null or char_length(contact_email) <= 254),
  image_path    text,
  device_model  text,
  os_version    text,
  app_version   text,
  created_at    timestamptz not null default now()
);

alter table public.feedbacks enable row level security;

drop policy if exists "feedbacks_insert_own" on public.feedbacks;
create policy "feedbacks_insert_own" on public.feedbacks
  for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "feedbacks_select_own" on public.feedbacks;
create policy "feedbacks_select_own" on public.feedbacks
  for select to authenticated using (user_id = auth.uid());

create index if not exists feedbacks_created_idx on public.feedbacks (created_at desc);

-- Optional screenshot with a review. Private: stored under <user id>/..., readable by its owner
-- (and by you through the dashboard).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('feedback-media', 'feedback-media', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

drop policy if exists "feedback_media_insert_own" on storage.objects;
create policy "feedback_media_insert_own" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'feedback-media' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "feedback_media_select_own" on storage.objects;
create policy "feedback_media_select_own" on storage.objects
  for select to authenticated
  using (bucket_id = 'feedback-media' and (storage.foldername(name))[1] = auth.uid()::text);
