-- Migration: 20261004120000_community_posts_public_groups.sql
--
-- community_posts_public was created with an EXPLICIT column list, before groups existed. Postgres
-- fixes a view column list at creation time, so group_id and pinned_at are invisible through it -
-- which means two things, both wrong:
--
--   * the open feed reads this view with no group filter, so a post written inside a public group
--     would surface in the main feed next to the open posts
--   * a group page cannot be read through the view at all, because it cannot filter on group_id
--
-- Appending the two columns fixes both. Appending is the only safe shape here: create or replace
-- view may add columns at the end and may not reorder or retype the existing ones.
--
-- Anonymity is unchanged - user_id is still nulled for an anonymous post, and group_id does not
-- reveal who wrote anything. RLS still decides which rows come back at all (security_invoker), so
-- a private group post reaches nobody outside that group whatever the app asks for.

create or replace view public.community_posts_public with (security_invoker = true) as
  select
    p.id,
    case when p.is_anonymous then null else p.user_id end as user_id,
    p.author_name, p.author_role, p.author_avatar_url,
    p.kind, p.title, p.content, p.tags,
    p.image_url, p.video_url, p.media_urls,
    p.comments_enabled, p.is_anonymous, p.is_urgent, p.ai_assist,
    p.likes_count, p.comments_count,
    p.created_at, p.updated_at, p.removed_at, p.removed_reason,
    p.group_id, p.pinned_at
  from public.community_posts p;

grant select on public.community_posts_public to anon, authenticated;
