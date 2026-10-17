-- Migration: 20260927140000_community_safety.sql
--
-- Safety floor for the community, asked for directly: "don't forget to do security in community
-- so it is safe here".
--
-- Two things, both of which have to live in the database rather than the app, because the app is
-- not the only thing that can reach these tables and a client-side limit is a suggestion:
--
--   1. Rate limits   how much one account may post, comment, or vote in an hour.
--   2. Reports       how a parent flags a post, a comment, or a poll option somebody added.
--
-- Blocking (stopping one account contacting another) is NOT here. It belongs with friends and
-- direct messages in round 2, where it first has something to block.

-- ---------------------------------------------------------------------------------------------
-- 1. Rate limits
-- ---------------------------------------------------------------------------------------------
--
-- Sized for a parent using the app normally, not for a parent using it heavily. A mother posting
-- her tenth thread within the hour is not the case these numbers are drawn around; a script
-- filling the feed is. If real users start hitting these, raise them - they are one number each.

create or replace function public.community_rate_limit_posts()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  recent integer;
begin
  select count(*) into recent
    from public.community_posts
   where user_id = NEW.user_id
     and created_at > now() - interval '1 hour';

  if recent >= 10 then
    -- Named so the app can say "you have posted a lot in the last hour, try again shortly"
    -- rather than showing a raw database error.
    raise exception 'community_rate_limit_posts';
  end if;
  return NEW;
end; $fn$;

create or replace function public.community_rate_limit_comments()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  recent integer;
begin
  -- An AI first pass is written on the parent behalf and must not consume their allowance.
  if NEW.is_ai then
    return NEW;
  end if;

  select count(*) into recent
    from public.community_comments
   where user_id = NEW.user_id
     and not is_ai
     and created_at > now() - interval '1 hour';

  if recent >= 60 then
    raise exception 'community_rate_limit_comments';
  end if;
  return NEW;
end; $fn$;

create or replace function public.community_rate_limit_votes()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  recent integer;
begin
  select count(*) into recent
    from public.community_poll_votes
   where user_id = NEW.user_id
     and created_at > now() - interval '1 hour';

  -- Deliberately loose: changing a vote deletes and re-inserts, so an indecisive voter on a
  -- lively poll runs up rows honestly. This is here to stop a script, not a person.
  if recent >= 100 then
    raise exception 'community_rate_limit_votes';
  end if;
  return NEW;
end; $fn$;

drop trigger if exists community_posts_rate_limit on public.community_posts;
create trigger community_posts_rate_limit
  before insert on public.community_posts
  for each row execute function public.community_rate_limit_posts();

drop trigger if exists community_comments_rate_limit on public.community_comments;
create trigger community_comments_rate_limit
  before insert on public.community_comments
  for each row execute function public.community_rate_limit_comments();

-- Triggers on the same event fire in alphabetical order, so community_poll_votes_guard runs
-- first and this one second. That is the order we want anyway: "this poll has closed" is a more
-- specific answer than "you have voted a lot", and the closed poll is what the voter should hear.
drop trigger if exists community_poll_votes_rate_limit on public.community_poll_votes;
create trigger community_poll_votes_rate_limit
  before insert on public.community_poll_votes
  for each row execute function public.community_rate_limit_votes();

-- Counting recent rows per author is the whole cost of this feature; give it an index.
create index if not exists community_posts_user_created_idx
  on public.community_posts (user_id, created_at desc);

create index if not exists community_comments_user_created_idx
  on public.community_comments (user_id, created_at desc);

create index if not exists community_poll_votes_user_created_idx
  on public.community_poll_votes (user_id, created_at desc);

-- ---------------------------------------------------------------------------------------------
-- 2. Reports
-- ---------------------------------------------------------------------------------------------
--
-- There is no admin role in this app; removal is done from the Supabase dashboard, the same way
-- community_posts.removed_at already works. This table is the queue that tells the owner where
-- to look.
--
--   select * from public.community_reports where status = 'open' order by created_at;

create table if not exists public.community_reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references auth.users(id) on delete cascade,
  target_type text not null check (target_type in ('post', 'comment', 'poll_option')),
  target_id uuid not null,
  reason text not null check (reason in (
    'spam',              -- โฆษณา / สแปม
    'wrong_info',        -- ข้อมูลผิด อาจเป็นอันตราย
    'harassment',        -- คุกคาม / ไม่สุภาพ
    'privacy',           -- เปิดเผยข้อมูลส่วนตัวของเด็กหรือผู้อื่น
    'unsafe_child',      -- เนื้อหาที่ไม่ปลอดภัยต่อเด็ก
    'other'
  )),
  note text check (char_length(coalesce(note, '')) <= 500),
  status text not null default 'open' check (status in ('open', 'reviewed', 'actioned', 'dismissed')),
  created_at timestamptz not null default now(),
  -- One report per person per thing. Reporting twice is not a stronger report; it is noise in
  -- the queue. The app should offer to withdraw instead.
  unique (reporter_id, target_type, target_id)
);

create index if not exists community_reports_open_idx
  on public.community_reports (created_at) where status = 'open';

create index if not exists community_reports_target_idx
  on public.community_reports (target_type, target_id);

alter table public.community_reports enable row level security;

-- A reporter may file, and may read and withdraw their own reports. Nobody reads anyone else:
-- who reported whom is exactly the thing that gets a parent harassed for reporting.
drop policy if exists community_reports_insert on public.community_reports;
create policy community_reports_insert on public.community_reports
  for insert to authenticated with check (auth.uid() = reporter_id);

drop policy if exists community_reports_select_own on public.community_reports;
create policy community_reports_select_own on public.community_reports
  for select to authenticated using (auth.uid() = reporter_id);

drop policy if exists community_reports_delete_own on public.community_reports;
create policy community_reports_delete_own on public.community_reports
  for delete to authenticated using (auth.uid() = reporter_id);

-- No update policy at all: status is the owner job, from the dashboard. A reporter who changes
-- their mind withdraws the report and files again.

-- Reporting is itself rate limited, or it becomes the harassment tool.
create or replace function public.community_rate_limit_reports()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  recent integer;
begin
  select count(*) into recent
    from public.community_reports
   where reporter_id = NEW.reporter_id
     and created_at > now() - interval '1 hour';

  if recent >= 20 then
    raise exception 'community_rate_limit_reports';
  end if;
  return NEW;
end; $fn$;

drop trigger if exists community_reports_rate_limit on public.community_reports;
create trigger community_reports_rate_limit
  before insert on public.community_reports
  for each row execute function public.community_rate_limit_reports();

-- Trigger functions, not RPCs. See 20260927130000 for why this matters for SECURITY DEFINER.
revoke execute on function public.community_rate_limit_posts() from public, anon, authenticated;
revoke execute on function public.community_rate_limit_comments() from public, anon, authenticated;
revoke execute on function public.community_rate_limit_votes() from public, anon, authenticated;
revoke execute on function public.community_rate_limit_reports() from public, anon, authenticated;
