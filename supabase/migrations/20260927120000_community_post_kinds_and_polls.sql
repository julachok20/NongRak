-- Migration: 20260927120000_community_post_kinds_and_polls.sql
--
-- The community grows from one kind of post to three, as in the Stitch mockups:
--
--   post    an ordinary share. Title optional, body up to 2,000 characters.
--   thread  กระทู้ถาม-ตอบ, a titled question aimed at the volunteer doctors. Title 80, body 1,000,
--           at most 2 tags. May be posted anonymously, may be marked urgent, may ask for an AI
--           first pass. Replies rank by votes; there is deliberately NO accepted-answer mechanic.
--   poll    โพลสำรวจ, a titled question with 3-6 options and its own settings.
--
-- One table, one feed, a `kind` column - not three tables. Every existing row is a 'post', which
-- is why the default is what it is.
--
-- Limits are taken from the composer mockups and enforced here as well, because the composer is
-- not the only thing that can write to this table.

-- ---------------------------------------------------------------------------------------------
-- 1. Post kinds and the thread flags
-- ---------------------------------------------------------------------------------------------

alter table public.community_posts
  add column if not exists kind text not null default 'post',
  add column if not exists title text,
  -- The composer offers a grid of up to 10 pictures/clips. image_url stays as the cover, so every
  -- row written before today - and every older app build reading them - still works unchanged.
  add column if not exists media_urls jsonb not null default '[]'::jsonb,
  add column if not exists comments_enabled boolean not null default true,
  -- ตั้งกระทู้แบบไม่ระบุตัวตน. user_id is still recorded: anonymity is owed to other parents, not
  -- to moderation. It is stripped on the way out by community_posts_public below, never here.
  add column if not exists is_anonymous boolean not null default false,
  -- กระทู้ด่วน. Sorts the thread to the top of the doctor queue. It does NOT make this an
  -- emergency service, and the app must say so wherever this flag is set or shown.
  add column if not exists is_urgent boolean not null default false,
  -- The parent asked NongRak AI for a first pass under the thread.
  add column if not exists ai_assist boolean not null default false;

do $$ begin
  alter table public.community_posts add constraint community_posts_kind_check
    check (kind in ('post', 'thread', 'poll'));
exception when duplicate_object then null; end $$;

-- A question with no question in it is not a question. Ordinary posts may go untitled.
do $$ begin
  alter table public.community_posts add constraint community_posts_title_required_check
    check (kind = 'post' or btrim(coalesce(title, '')) <> '');
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.community_posts add constraint community_posts_length_check
    check (
      char_length(coalesce(title, '')) <= case when kind = 'thread' then 80 else 200 end
      and char_length(content) <= case when kind = 'thread' then 1000 else 2000 end
    );
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.community_posts add constraint community_posts_media_limit_check
    check (jsonb_array_length(media_urls) <= 10);
exception when duplicate_object then null; end $$;

-- "เลือกได้สูงสุด 2 แท็ก" applies to threads only; ordinary posts may carry more.
do $$ begin
  alter table public.community_posts add constraint community_posts_thread_tag_limit_check
    check (kind <> 'thread' or jsonb_array_length(tags) <= 2);
exception when duplicate_object then null; end $$;

-- Only a thread can be anonymous, urgent, or AI-assisted. The other kinds have no such controls
-- in the composer, so a row carrying those flags did not come from the app.
do $$ begin
  alter table public.community_posts add constraint community_posts_thread_flags_check
    check (kind = 'thread' or not (is_anonymous or is_urgent or ai_assist));
exception when duplicate_object then null; end $$;

create index if not exists community_posts_kind_created_idx
  on public.community_posts (kind, created_at desc);

-- Urgent threads first, oldest-waiting first, for the doctor queue.
create index if not exists community_posts_urgent_idx
  on public.community_posts (created_at) where is_urgent;

-- ---------------------------------------------------------------------------------------------
-- 2. Anonymity is enforced on the way out, not asked of the client
-- ---------------------------------------------------------------------------------------------

-- The display name and avatar are pinned at write time, so a client cannot post anonymously and
-- still hand over the author's real name in the same row.
create or replace function public.community_post_anonymity_guard()
returns trigger language plpgsql set search_path = public as $$
begin
  if NEW.is_anonymous then
    NEW.author_name := 'ไม่ระบุตัวตน';
    NEW.author_avatar_url := null;
  end if;
  return NEW;
end; $$;

drop trigger if exists community_posts_anonymity_guard on public.community_posts;
create trigger community_posts_anonymity_guard
  before insert or update on public.community_posts
  for each row execute function public.community_post_anonymity_guard();

-- What every client reads. user_id is nulled for anonymous threads, so the link between a post
-- and an account never leaves the database - not to another parent, not to the answering doctor.
-- Row-level security is NOT bypassed: security_invoker makes the view run as the caller, so the
-- existing policies (including the removed-post rule) still decide which rows are visible.
--
-- Tracing an anonymous thread is deliberately a dashboard job:
--   select user_id from public.community_posts where id = '<post id>';
create or replace view public.community_posts_public with (security_invoker = true) as
  select
    p.id,
    case when p.is_anonymous then null else p.user_id end as user_id,
    p.author_name, p.author_role, p.author_avatar_url,
    p.kind, p.title, p.content, p.tags,
    p.image_url, p.video_url, p.media_urls,
    p.comments_enabled, p.is_anonymous, p.is_urgent, p.ai_assist,
    p.likes_count, p.comments_count,
    p.created_at, p.updated_at, p.removed_at, p.removed_reason
  from public.community_posts p;

grant select on public.community_posts_public to anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- 3. AI replies are marked as such, in the database
-- ---------------------------------------------------------------------------------------------

-- An AI first-pass is stored as an ordinary comment with this flag set, so it can never be
-- mistaken for a person's reply by any client, present or future. The app renders it with the
-- "AI ไม่ใช่แพทย์" notice; the flag is what makes that possible.
alter table public.community_comments
  add column if not exists is_ai boolean not null default false;

create index if not exists community_comments_ai_idx
  on public.community_comments (post_id) where is_ai;

-- ---------------------------------------------------------------------------------------------
-- 4. Polls
-- ---------------------------------------------------------------------------------------------
--
-- Settings come from mockup image 73 and nowhere else: 1/2/3/7 day duration, optional multiple
-- answers, results either right after voting or held until the poll closes, and optionally
-- voter-added options. A vote may be changed until the poll closes, and not after.

create table if not exists public.community_polls (
  post_id uuid primary key references public.community_posts(id) on delete cascade,
  description text,
  closes_at timestamptz not null,
  allow_multiple boolean not null default false,
  -- false = nobody sees the tally until the poll closes, so early votes cannot steer later ones.
  show_results_after_vote boolean not null default true,
  allow_user_options boolean not null default false,
  created_at timestamptz not null default now(),
  constraint community_polls_description_length check (char_length(coalesce(description, '')) <= 300)
);

create table if not exists public.community_poll_options (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.community_polls(post_id) on delete cascade,
  label text not null,
  position integer not null,
  -- Null for the author's own options. Set when a voter added one, so the row can be attributed
  -- and moderated like any other piece of user-written text.
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint community_poll_options_label_length check (char_length(btrim(label)) between 1 and 120)
);

create index if not exists community_poll_options_post_idx
  on public.community_poll_options (post_id, position);

create table if not exists public.community_poll_votes (
  post_id uuid not null references public.community_polls(post_id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  option_id uuid not null references public.community_poll_options(id) on delete cascade,
  created_at timestamptz not null default now(),
  -- One row per option per person. Single-answer polls are held to one row by the trigger below,
  -- because the key alone cannot express "at most one" without knowing the poll's settings.
  primary key (post_id, user_id, option_id)
);

create index if not exists community_poll_votes_option_idx
  on public.community_poll_votes (option_id);

create or replace function public.community_poll_vote_guard()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  poll public.community_polls%rowtype;
  existing integer;
begin
  select * into poll from public.community_polls where post_id = NEW.post_id;
  if not found then
    raise exception 'poll_not_found';
  end if;

  -- "Changeable until the poll closes, and not after." Once results are final, neither a new vote
  -- nor a changed one may move them.
  if now() >= poll.closes_at then
    raise exception 'poll_closed';
  end if;

  -- The option must belong to THIS poll. Without this, a vote row could point at another poll's
  -- option and be counted in both.
  if not exists (
    select 1 from public.community_poll_options o
     where o.id = NEW.option_id and o.post_id = NEW.post_id
  ) then
    raise exception 'poll_option_mismatch';
  end if;

  if not poll.allow_multiple then
    select count(*) into existing
      from public.community_poll_votes v
     where v.post_id = NEW.post_id and v.user_id = NEW.user_id;
    if existing > 0 then
      -- Changing a single-answer vote means deleting the old row first. Saying so by name beats
      -- a unique-violation the app has to guess at.
      raise exception 'poll_single_choice_already_voted';
    end if;
  end if;

  return NEW;
end; $fn$;

drop trigger if exists community_poll_votes_guard on public.community_poll_votes;
create trigger community_poll_votes_guard
  before insert on public.community_poll_votes
  for each row execute function public.community_poll_vote_guard();

-- A vote may be withdrawn only while the poll is open, for the same reason.
create or replace function public.community_poll_unvote_guard()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if exists (
    select 1 from public.community_polls p
     where p.post_id = OLD.post_id and now() >= p.closes_at
  ) then
    raise exception 'poll_closed';
  end if;
  return OLD;
end; $fn$;

drop trigger if exists community_poll_votes_unvote_guard on public.community_poll_votes;
create trigger community_poll_votes_unvote_guard
  before delete on public.community_poll_votes
  for each row execute function public.community_poll_unvote_guard();

-- A voter-added option is allowed only when that poll invited them, and only while it is open.
create or replace function public.community_poll_option_guard()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  poll public.community_polls%rowtype;
  author uuid;
  option_count integer;
begin
  select * into poll from public.community_polls where post_id = NEW.post_id;
  if not found then
    raise exception 'poll_not_found';
  end if;

  select user_id into author from public.community_posts where id = NEW.post_id;

  -- The author builds the poll; anyone else is a voter adding an option.
  if auth.uid() is distinct from author then
    if not poll.allow_user_options then
      raise exception 'poll_user_options_not_allowed';
    end if;
    if now() >= poll.closes_at then
      raise exception 'poll_closed';
    end if;
    NEW.created_by := auth.uid();
  end if;

  -- 6 is the ceiling in the composer. Voter-added options must not sail past it.
  select count(*) into option_count from public.community_poll_options where post_id = NEW.post_id;
  if option_count >= 6 then
    raise exception 'poll_option_limit_reached';
  end if;

  return NEW;
end; $fn$;

drop trigger if exists community_poll_options_guard on public.community_poll_options;
create trigger community_poll_options_guard
  before insert on public.community_poll_options
  for each row execute function public.community_poll_option_guard();

-- ---------------------------------------------------------------------------------------------
-- 5. Poll RLS
-- ---------------------------------------------------------------------------------------------

alter table public.community_polls enable row level security;
alter table public.community_poll_options enable row level security;
alter table public.community_poll_votes enable row level security;

drop policy if exists community_polls_select on public.community_polls;
create policy community_polls_select on public.community_polls for select using (true);

drop policy if exists community_poll_options_select on public.community_poll_options;
create policy community_poll_options_select on public.community_poll_options for select using (true);

-- Votes are readable so a voter can see their own choice and the app can tally.
-- show_results_after_vote is a presentation rule, NOT a secrecy guarantee: anyone who can query
-- can count these rows. The UI must not imply the tally is hidden from everyone.
drop policy if exists community_poll_votes_select on public.community_poll_votes;
create policy community_poll_votes_select on public.community_poll_votes for select using (true);

-- Only the post's author may attach a poll to it.
drop policy if exists community_polls_insert on public.community_polls;
create policy community_polls_insert on public.community_polls
  for insert to authenticated
  with check (exists (
    select 1 from public.community_posts p
     where p.id = post_id and p.user_id = auth.uid() and p.kind = 'poll'
  ));

drop policy if exists community_polls_update on public.community_polls;
create policy community_polls_update on public.community_polls
  for update to authenticated
  using (exists (select 1 from public.community_posts p where p.id = post_id and p.user_id = auth.uid()))
  with check (exists (select 1 from public.community_posts p where p.id = post_id and p.user_id = auth.uid()));

-- Anyone signed in may insert an option; the trigger decides whether they were invited to.
drop policy if exists community_poll_options_insert on public.community_poll_options;
create policy community_poll_options_insert on public.community_poll_options
  for insert to authenticated with check (true);

-- An option may be withdrawn by whoever added it, or by the poll's author moderating it away.
drop policy if exists community_poll_options_delete on public.community_poll_options;
create policy community_poll_options_delete on public.community_poll_options
  for delete to authenticated
  using (
    created_by = auth.uid()
    or exists (select 1 from public.community_posts p where p.id = post_id and p.user_id = auth.uid())
  );

drop policy if exists community_poll_votes_insert on public.community_poll_votes;
create policy community_poll_votes_insert on public.community_poll_votes
  for insert to authenticated with check (auth.uid() = user_id);

drop policy if exists community_poll_votes_delete on public.community_poll_votes;
create policy community_poll_votes_delete on public.community_poll_votes
  for delete to authenticated using (auth.uid() = user_id);

-- The guards run as the table owner so they can read community_posts whoever the caller is;
-- nothing else should be able to call them.
revoke execute on function public.community_poll_vote_guard() from public, anon;
revoke execute on function public.community_poll_unvote_guard() from public, anon;
revoke execute on function public.community_poll_option_guard() from public, anon;
revoke execute on function public.community_post_anonymity_guard() from public, anon;
