-- Migration: 20260927160000_community_profile_stats.sql
--
-- The profile header shows another parent's friend count and the friends you have in common.
-- A client cannot work either out: community_friendships RLS returns only rows you are part of,
-- which is the correct rule and stays. These two functions answer the narrow questions the header
-- actually asks, and nothing wider.

-- How many accepted friends a parent has. A count, never the list: who somebody is friends with
-- is theirs, and the header only ever displays a number.
create or replace function public.community_friend_count(target uuid)
returns integer language sql stable security definer set search_path = public as $fn$
  select count(*)::integer
    from public.community_friendships f
   where f.status = 'accepted'
     and (f.requester_id = target or f.addressee_id = target);
$fn$;

-- Friends you and [target] have in common.
--
-- Returns only people the CALLER is already friends with, so it reveals nothing about strangers -
-- you are told that someone you already know also knows this parent. It does not expose target's
-- other friends, and it is empty when you have none in common.
create or replace function public.community_mutual_friends(target uuid)
returns table (user_id uuid) language sql stable security definer set search_path = public as $fn$
  with mine as (
    select case when f.requester_id = auth.uid() then f.addressee_id else f.requester_id end as id
      from public.community_friendships f
     where f.status = 'accepted'
       and (f.requester_id = auth.uid() or f.addressee_id = auth.uid())
  ),
  theirs as (
    select case when f.requester_id = target then f.addressee_id else f.requester_id end as id
      from public.community_friendships f
     where f.status = 'accepted'
       and (f.requester_id = target or f.addressee_id = target)
  )
  select m.id from mine m
   where m.id in (select id from theirs)
     and m.id <> auth.uid()
     and m.id <> target;
$fn$;

-- Signed-in callers only. anon has no auth.uid(), so community_mutual_friends would return
-- nothing anyway - but community_friend_count would happily count for a stranger.
revoke execute on function public.community_friend_count(uuid) from public, anon;
revoke execute on function public.community_mutual_friends(uuid) from public, anon;
grant execute on function public.community_friend_count(uuid) to authenticated;
grant execute on function public.community_mutual_friends(uuid) to authenticated;
