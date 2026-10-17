-- Migration: 20260927150000_community_friends.sql
--
-- Round 2 of the community rebuild: profiles and friends, from mockups 54-61.
--
--   profiles                gains the bio and city the profile header shows, plus `discoverable`.
--   community_friendships   a mutual request/accept, as mockup 55 draws it (ยอมรับเพื่อน / ปฏิเสธ).
--   community_blocks        stopping someone contacting you. Needed NOW, not in the chat round:
--                           the moment strangers can send friend requests, a parent needs a way
--                           to make one stop.

-- ---------------------------------------------------------------------------------------------
-- 1. Profile fields
-- ---------------------------------------------------------------------------------------------

alter table public.profiles
  add column if not exists bio text,
  add column if not exists city text,
  -- Whether this parent may be SUGGESTED to strangers in เพื่อนที่คุณอาจรู้จัก.
  --
  -- The suggestion is matched on the child's age BAND (7-9 เดือน), never a birthdate, never the
  -- child's name. Even so it is information about a minor reaching people the family has never
  -- met, so it is one switch on the profile and it turns the whole feature off for that account.
  -- Being undiscoverable does not hide the profile - it only stops it being offered to strangers.
  add column if not exists discoverable boolean not null default true;

do $$ begin
  alter table public.profiles add constraint profiles_bio_length check (char_length(coalesce(bio, '')) <= 300);
exception when duplicate_object then null; end $$;

do $$ begin
  alter table public.profiles add constraint profiles_city_length check (char_length(coalesce(city, '')) <= 60);
exception when duplicate_object then null; end $$;

-- ---------------------------------------------------------------------------------------------
-- 2. Friendships
-- ---------------------------------------------------------------------------------------------

create table if not exists public.community_friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references auth.users(id) on delete cascade,
  addressee_id uuid not null references auth.users(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending', 'accepted', 'declined')),
  -- The note attached to a request, as in mockup 55.
  message text check (char_length(coalesce(message, '')) <= 200),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint community_friendships_not_self check (requester_id <> addressee_id)
);

-- One live relationship per pair, whichever way round it was asked. Without this, two parents who
-- request each other at the same time end up as two rows that disagree about who is pending.
create unique index if not exists community_friendships_pair_idx
  on public.community_friendships (least(requester_id, addressee_id), greatest(requester_id, addressee_id));

create index if not exists community_friendships_addressee_idx
  on public.community_friendships (addressee_id, status);

create index if not exists community_friendships_requester_idx
  on public.community_friendships (requester_id, status);

alter table public.community_friendships enable row level security;

-- Only the two people involved may see the row. A pending request is not public information.
drop policy if exists community_friendships_select on public.community_friendships;
create policy community_friendships_select on public.community_friendships
  for select to authenticated
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

-- You may only ask on your own behalf, and only for a pending row - a client cannot insert a row
-- that is already accepted and make itself somebody friend.
drop policy if exists community_friendships_insert on public.community_friendships;
create policy community_friendships_insert on public.community_friendships
  for insert to authenticated
  with check (auth.uid() = requester_id and status = 'pending');

-- Answering belongs to the ADDRESSEE. The requester cannot accept their own request.
drop policy if exists community_friendships_respond on public.community_friendships;
create policy community_friendships_respond on public.community_friendships
  for update to authenticated
  using (auth.uid() = addressee_id)
  with check (auth.uid() = addressee_id);

-- Either side may withdraw: the requester cancels, or a friend unfriends.
drop policy if exists community_friendships_delete on public.community_friendships;
create policy community_friendships_delete on public.community_friendships
  for delete to authenticated
  using (auth.uid() = requester_id or auth.uid() = addressee_id);

create or replace function public.community_friendship_guard()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  recent integer;
begin
  if TG_OP = 'INSERT' then
    -- Blocking has to beat asking, or blocking someone just means they ask again.
    if exists (
      select 1 from public.community_blocks b
       where (b.blocker_id = NEW.addressee_id and b.blocked_id = NEW.requester_id)
          or (b.blocker_id = NEW.requester_id and b.blocked_id = NEW.addressee_id)
    ) then
      raise exception 'friend_request_blocked';
    end if;

    -- A stranger working down a list of parents is what this stops.
    select count(*) into recent
      from public.community_friendships
     where requester_id = NEW.requester_id
       and created_at > now() - interval '1 hour';
    if recent >= 20 then
      raise exception 'community_rate_limit_friend_requests';
    end if;

    return NEW;
  end if;

  -- On answering: who asked and of whom is fixed, and the timestamp is the server to set.
  NEW.requester_id := OLD.requester_id;
  NEW.addressee_id := OLD.addressee_id;
  NEW.created_at := OLD.created_at;
  NEW.message := OLD.message;
  if NEW.status is distinct from OLD.status then
    NEW.responded_at := now();
  end if;
  return NEW;
end; $fn$;

drop trigger if exists community_friendships_guard on public.community_friendships;
create trigger community_friendships_guard
  before insert or update on public.community_friendships
  for each row execute function public.community_friendship_guard();

-- ---------------------------------------------------------------------------------------------
-- 3. Blocks
-- ---------------------------------------------------------------------------------------------

create table if not exists public.community_blocks (
  blocker_id uuid not null references auth.users(id) on delete cascade,
  blocked_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id),
  constraint community_blocks_not_self check (blocker_id <> blocked_id)
);

create index if not exists community_blocks_blocked_idx
  on public.community_blocks (blocked_id);

alter table public.community_blocks enable row level security;

-- Only the blocker reads their own list. The blocked person is never told, because telling them
-- is how blocking turns into an argument.
drop policy if exists community_blocks_select on public.community_blocks;
create policy community_blocks_select on public.community_blocks
  for select to authenticated using (auth.uid() = blocker_id);

drop policy if exists community_blocks_insert on public.community_blocks;
create policy community_blocks_insert on public.community_blocks
  for insert to authenticated with check (auth.uid() = blocker_id);

drop policy if exists community_blocks_delete on public.community_blocks;
create policy community_blocks_delete on public.community_blocks
  for delete to authenticated using (auth.uid() = blocker_id);

-- Blocking ends any friendship in either direction. Leaving the row would mean a blocked person
-- still counted as a friend everywhere friendship is checked.
create or replace function public.community_block_clears_friendship()
returns trigger language plpgsql security definer set search_path = public as $fn$
begin
  delete from public.community_friendships
   where (requester_id = NEW.blocker_id and addressee_id = NEW.blocked_id)
      or (requester_id = NEW.blocked_id and addressee_id = NEW.blocker_id);
  return NEW;
end; $fn$;

drop trigger if exists community_blocks_clear_friendship on public.community_blocks;
create trigger community_blocks_clear_friendship
  after insert on public.community_blocks
  for each row execute function public.community_block_clears_friendship();

revoke execute on function public.community_friendship_guard() from public, anon, authenticated;
revoke execute on function public.community_block_clears_friendship() from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- 4. Telling the other person
-- ---------------------------------------------------------------------------------------------
--
-- A friend request has to reach a phone that is not running the app, and the app has no FCM:
-- SystemNotifier only fires inside a live process. So the notification row is written by the
-- database, at the moment the friendship row changes, and the recipient sees it next time they
-- open the app.
--
-- SECURITY DEFINER because the requester has no INSERT right on the addressee notifications,
-- and must not be given one.

create or replace function public.community_friendship_notify()
returns trigger language plpgsql security definer set search_path = public as $fn$
declare
  actor_name text;
  target uuid;
  title text;
begin
  if TG_OP = 'INSERT' then
    select coalesce(nullif(btrim(p.username), ''), nullif(btrim(p.full_name), ''), 'คุณแม่น้องรัก')
      into actor_name from public.profiles p where p.id = NEW.requester_id;
    target := NEW.addressee_id;
    title := coalesce(actor_name, 'คุณแม่น้องรัก') || ' ส่งคำขอเป็นเพื่อน';

  elsif NEW.status = 'accepted' and OLD.status is distinct from 'accepted' then
    -- Only an ACCEPT is worth telling someone about. A decline is not news anyone needs.
    select coalesce(nullif(btrim(p.username), ''), nullif(btrim(p.full_name), ''), 'คุณแม่น้องรัก')
      into actor_name from public.profiles p where p.id = NEW.addressee_id;
    target := NEW.requester_id;
    title := coalesce(actor_name, 'คุณแม่น้องรัก') || ' ยอมรับคำขอเป็นเพื่อนแล้ว';

  else
    return NEW;
  end if;

  insert into public.notifications (user_id, category, title, body)
  values (
    target,
    'normal',
    title,
    case when TG_OP = 'INSERT' then nullif(btrim(coalesce(NEW.message, '')), '') else null end
  );

  return NEW;
end; $fn$;

drop trigger if exists community_friendships_notify on public.community_friendships;
create trigger community_friendships_notify
  after insert or update on public.community_friendships
  for each row execute function public.community_friendship_notify();

revoke execute on function public.community_friendship_notify() from public, anon, authenticated;
