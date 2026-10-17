-- Migration: 20261004130000_community_group_stats.sql
--
-- The numbers on a group card (mockups 66, 70) cannot be counted by a client.
-- community_group_members_select returns only the rows of groups the caller is IN, which is correct
-- - nobody browses another group member list - but it means a directory card for a group you have
-- not joined would always read 0 members.
--
-- So the count comes from a function, which returns NUMBERS and never the member list. One call
-- covers every group on the page: a directory of 20 cards is one round trip, not 60.
--
-- What is deliberately absent: an online count. There is no presence system in this app, so the
-- "385 ออนไลน์" in mockup 66 would be a made-up number. The header shows members and posts-today,
-- both counted here from real rows.

create or replace function public.community_group_stats(group_ids uuid[])
returns table (
  group_id uuid,
  member_count integer,
  posts_today integer,
  has_doctor boolean
)
language sql stable security definer set search_path = public as $fn$
  select
    g.id as group_id,
    (select count(*)::integer from public.community_group_members m where m.group_id = g.id),
    (select count(*)::integer
       from public.community_posts p
      where p.group_id = g.id
        and p.removed_at is null
        and p.created_at >= now() - interval '24 hours'),
    exists (
      select 1
        from public.community_group_members m
        join public.profiles pr on pr.id = m.user_id
       where m.group_id = g.id and m.role = 'moderator' and 'doctor' = any(pr.roles)
    )
  from public.community_groups g
  -- SECURITY DEFINER bypasses RLS, so the visibility rule has to be restated here. A private
  -- group tells its numbers only to its own members.
  where g.id = any(group_ids)
    and (g.is_public or public.is_group_member(g.id));
$fn$;

revoke execute on function public.community_group_stats(uuid[]) from public, anon;
grant execute on function public.community_group_stats(uuid[]) to authenticated;
