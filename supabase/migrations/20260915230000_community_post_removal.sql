-- Migration: 20260915230000_community_post_removal.sql
--
-- Lets the app owner remove a community post without hard-deleting it, so its author can be told.
-- There is no admin role in the app; removal is done from the Supabase dashboard.
--
--   removed_at / removed_reason  Everyone else stops seeing the post. Its author still reads it,
--                                and the app shows them a warning banner at the top of Community.
--
-- Remove a post:
--   update public.community_posts
--      set removed_at = now(), removed_reason = 'ข้อความไม่เป็นไปตามแนวทางชุมชน'
--    where id = '<post id>';
-- Restore it:
--   update public.community_posts set removed_at = null, removed_reason = null where id = '<post id>';

alter table public.community_posts
  add column if not exists removed_at timestamptz,
  add column if not exists removed_reason text;

-- Removed posts: visible to their author only.
drop policy if exists "community_posts_select" on public.community_posts;
create policy "community_posts_select" on public.community_posts
  for select using (removed_at is null or auth.uid() = user_id);

-- Same guard as before, plus: a signed-in app user (the author) cannot set or clear a removal.
-- The dashboard SQL editor runs with no app login (auth.uid() is null), so it can.
create or replace function public.community_post_edit_guard()
returns trigger
language plpgsql
set search_path to 'public'
as $function$
BEGIN
  IF pg_trigger_depth() > 1 THEN
    RETURN NEW;
  END IF;

  NEW.likes_count := OLD.likes_count;
  NEW.comments_count := OLD.comments_count;
  NEW.user_id := OLD.user_id;
  NEW.author_name := OLD.author_name;
  NEW.created_at := OLD.created_at;

  IF auth.uid() IS NOT NULL THEN
    NEW.removed_at := OLD.removed_at;
    NEW.removed_reason := OLD.removed_reason;
  END IF;

  IF (NEW.content IS DISTINCT FROM OLD.content
      OR NEW.image_url IS DISTINCT FROM OLD.image_url
      OR NEW.tags IS DISTINCT FROM OLD.tags)
     AND now() > OLD.created_at + interval '48 hours' THEN
    RAISE EXCEPTION 'post_edit_window_expired';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END; $function$;
