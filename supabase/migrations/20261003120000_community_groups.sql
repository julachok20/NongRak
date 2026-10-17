-- Migration: 20261003120000_community_groups.sql
--
-- Round 3 of the community rebuild: groups and the group directory, from mockups 66-71.
--
--   community_groups          a room (ห้องคุณแม่ลูกแรกเกิด, ชมรมอาหารเสริม...) with rules and a category
--   community_group_members   who is in it, and who moderates it
--   community_posts.group_id  a post belongs to a group, or to the open feed when null
--
-- TWO THINGS DELIBERATELY NOT BUILT, because the data behind them does not exist:
--
--   * "385 ออนไลน์" from mockup 66. There is no presence system in this app - no heartbeat, no
--     last-seen column, nothing. A number there would be invented. The header shows members and
--     posts-today instead, both counted from real rows.
--   * A free-text "has a doctor" flag. Whether a group is doctor-moderated is DERIVED from a
--     moderator actually holding the doctor role (community_group_has_doctor), so the badge
--     cannot be switched on for a group no doctor has ever looked at.

-- ---------------------------------------------------------------------------------------------
-- 1. Groups
-- ---------------------------------------------------------------------------------------------

create table if not exists public.community_groups (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text,
  -- The six headings in the directory (mockup 70). A check rather than a lookup table: these are
  -- product copy, not data somebody administers.
  category text not null check (category in (
    'health',      -- สุขภาพและการแพทย์
    'nutrition',   -- โภชนาการและ BLW
    'sleep',       -- การนอนและกิจวัตร
    'milestones',  -- ของเล่นและพัฒนาการ
    'wellness',    -- ดูแลตัวเองและจิตใจ
    'gear'         -- ป้ายยาของใช้ลูก
  )),
  cover_url text,
  -- กฎกลุ่ม, pinned at the top of the group page.
  rules text,
  -- A private group is invisible in the directory and its posts are members-only.
  is_public boolean not null default true,
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  constraint community_groups_name_length check (char_length(btrim(name)) between 2 and 80),
  constraint community_groups_description_length check (char_length(coalesce(description, '')) <= 500),
  constraint community_groups_rules_length check (char_length(coalesce(rules, '')) <= 1000)
);

create index if not exists community_groups_category_idx on public.community_groups (category);

create table if not exists public.community_group_members (
  group_id uuid not null references public.community_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null default 'member' check (role in ('member', 'moderator')),
  -- The bell on the group header.
  notifications_on boolean not null default true,
  joined_at timestamptz not null default now(),
  primary key (group_id, user_id)
);

create index if not exists community_group_members_user_idx
  on public.community_group_members (user_id);

create index if not exists community_group_members_moderator_idx
  on public.community_group_members (group_id) where role = 'moderator';

-- ---------------------------------------------------------------------------------------------
-- 2. Posts can belong to a group
-- ---------------------------------------------------------------------------------------------

alter table public.community_posts
  add column if not exists group_id uuid references public.community_groups(id) on delete cascade,
  -- ปักหมุดโดยผู้ดูแล (mockup 67). Only a moderator of that group may set it.
  add column if not exists pinned_at timestamptz,
  add column if not exists pinned_by uuid references auth.users(id) on delete set null;

create index if not exists community_posts_group_idx
  on public.community_posts (group_id, created_at desc) where group_id is not null;

create index if not exists community_posts_pinned_idx
  on public.community_posts (group_id, pinned_at desc) where pinned_at is not null;

-- ---------------------------------------------------------------------------------------------
-- 3. Membership helpers
-- ---------------------------------------------------------------------------------------------

-- SECURITY DEFINER so a policy on community_posts can ask "is the caller in this group" without
-- the caller needing read access to the whole membership table.
create or replace function public.is_group_member(group_uuid uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.community_group_members m
     where m.group_id = group_uuid and m.user_id = auth.uid()
  );
$fn$;

create or replace function public.is_group_moderator(group_uuid uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.community_group_members m
     where m.group_id = group_uuid and m.user_id = auth.uid() and m.role = 'moderator'
  );
$fn$;

-- Whether a group actually has a paediatrician moderating it. The badge in mockup 66 reads from
-- this and nothing else, so it cannot be set by hand on a group no doctor has ever seen.
create or replace function public.community_group_has_doctor(group_uuid uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1
      from public.community_group_members m
      join public.profiles p on p.id = m.user_id
     where m.group_id = group_uuid
       and m.role = 'moderator'
       and 'doctor' = any(p.roles)
  );
$fn$;

-- ---------------------------------------------------------------------------------------------
-- 4. RLS
-- ---------------------------------------------------------------------------------------------

alter table public.community_groups enable row level security;
alter table public.community_group_members enable row level security;

-- Public groups are discoverable by anyone; a private group is visible only to its members.
drop policy if exists community_groups_select on public.community_groups;
create policy community_groups_select on public.community_groups
  for select using (is_public or public.is_group_member(id));

drop policy if exists community_groups_insert on public.community_groups;
create policy community_groups_insert on public.community_groups
  for insert to authenticated with check (auth.uid() = created_by);

-- Only a moderator edits the group. Its creator becomes one by the trigger below.
drop policy if exists community_groups_update on public.community_groups;
create policy community_groups_update on public.community_groups
  for update to authenticated
  using (public.is_group_moderator(id))
  with check (public.is_group_moderator(id));

-- Membership of a group you are in is visible to you; nobody browses another group's member list.
drop policy if exists community_group_members_select on public.community_group_members;
create policy community_group_members_select on public.community_group_members
  for select to authenticated
  using (user_id = auth.uid() or public.is_group_member(group_id));

-- Joining is something you do for yourself, and always as a plain member: a client cannot insert
-- itself as a moderator.
drop policy if exists community_group_members_insert on public.community_group_members;
create policy community_group_members_insert on public.community_group_members
  for insert to authenticated
  with check (auth.uid() = user_id and role = 'member');

-- Your own row (the bell), or a moderator managing someone else's.
drop policy if exists community_group_members_update on public.community_group_members;
create policy community_group_members_update on public.community_group_members
  for update to authenticated
  using (auth.uid() = user_id or public.is_group_moderator(group_id))
  with check (auth.uid() = user_id or public.is_group_moderator(group_id));

-- Leaving, or being removed by a moderator.
drop policy if exists community_group_members_delete on public.community_group_members;
create policy community_group_members_delete on public.community_group_members
  for delete to authenticated
  using (auth.uid() = user_id or public.is_group_moderator(group_id));

-- A group needs at least one moderator from the moment it exists, or nobody can ever edit it.
create or replace function public.community_group_seed_moderator()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  if NEW.created_by is not null then
    insert into public.community_group_members (group_id, user_id, role)
    values (NEW.id, NEW.created_by, 'moderator')
    on conflict (group_id, user_id) do update set role = 'moderator';
  end if;
  return NEW;
end; $fn$;

drop trigger if exists community_groups_seed_moderator on public.community_groups;
create trigger community_groups_seed_moderator
  after insert on public.community_groups
  for each row execute function public.community_group_seed_moderator();

-- ---------------------------------------------------------------------------------------------
-- 5. Group posts are not public by default
-- ---------------------------------------------------------------------------------------------
--
-- community_posts_select was `using (true)` - correct while every post was in the open feed, and
-- WRONG the moment a post can belong to a private group. Rewritten so a post is readable when it
-- is in the open feed, in a public group, or in a private group the caller belongs to.

drop policy if exists community_posts_select on public.community_posts;
create policy community_posts_select on public.community_posts
  for select using (
    group_id is null
    or exists (
      select 1 from public.community_groups g
       where g.id = group_id and (g.is_public or public.is_group_member(g.id))
    )
  );

-- Posting into a group requires being in it. The open feed (group_id null) is unchanged.
drop policy if exists community_posts_insert on public.community_posts;
create policy community_posts_insert on public.community_posts
  for insert to authenticated
  with check (
    auth.uid() = user_id
    and (group_id is null or public.is_group_member(group_id))
  );

-- Pinning belongs to moderators. The edit guard already pins identity and counts; this adds the
-- one field a non-author may change, and only in a group they moderate.
create or replace function public.community_post_pin_guard()
returns trigger language plpgsql set search_path = public as $fn$
begin
  if NEW.pinned_at is distinct from OLD.pinned_at
     or NEW.pinned_by is distinct from OLD.pinned_by then
    if NEW.group_id is null or not public.is_group_moderator(NEW.group_id) then
      raise exception 'pin_requires_moderator';
    end if;
  end if;
  return NEW;
end; $fn$;

drop trigger if exists community_posts_pin_guard on public.community_posts;
create trigger community_posts_pin_guard
  before update on public.community_posts
  for each row execute function public.community_post_pin_guard();

revoke execute on function public.community_group_seed_moderator() from public, anon, authenticated;
revoke execute on function public.community_post_pin_guard() from public, anon, authenticated;
revoke execute on function public.is_group_member(uuid) from public, anon;
revoke execute on function public.is_group_moderator(uuid) from public, anon;
revoke execute on function public.community_group_has_doctor(uuid) from public, anon;
grant execute on function public.is_group_member(uuid) to authenticated;
grant execute on function public.is_group_moderator(uuid) to authenticated;
grant execute on function public.community_group_has_doctor(uuid) to authenticated;
