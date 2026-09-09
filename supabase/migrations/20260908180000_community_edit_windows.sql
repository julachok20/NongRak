-- Migration: 20260908180000_community_edit_windows.sql
-- Description: Locks community posts and comments to their own authors, with time-limited edits.
--
--   * A post may be edited by its author only, and only within 48 hours of creation.
--   * A comment may be edited by its author only, and only within 24 hours of creation.
--   * Anyone signed in may comment on any post, and may like.
--   * Nobody may edit anyone else's post or comment, ever.
--
-- The policies this replaces were `USING (true) WITH CHECK (true)` for every authenticated user,
-- i.e. any signed-in parent could rewrite any other parent's post or comment. They were written
-- that way because likes_count / comments_count are maintained by whoever likes or comments -
-- somebody other than the author - so restricting UPDATE to the author would have broken the
-- counters. This migration removes that reason: the counters become the database's job.

-- ---------------------------------------------------------------------------------------------
-- 1. Counters become server-maintained
-- ---------------------------------------------------------------------------------------------

-- Backfill first, so the switch-over does not freeze a wrong number in place. Client-maintained
-- counts drift whenever a write failed midway, and from here on they are never written again.
UPDATE public.community_posts p SET
  likes_count = (SELECT count(*) FROM public.community_post_likes l WHERE l.post_id = p.id),
  comments_count = (SELECT count(*) FROM public.community_comments c WHERE c.post_id = p.id);

UPDATE public.community_comments c SET
  likes_count = (SELECT count(*) FROM public.community_comment_likes l WHERE l.comment_id = c.id);

CREATE OR REPLACE FUNCTION public.community_post_likes_recount()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  -- SECURITY DEFINER so the count is written by the table owner: the liker has no UPDATE right
  -- on somebody else's post, which is the entire point of the policies below.
  UPDATE public.community_posts SET likes_count = (
    SELECT count(*) FROM public.community_post_likes l
     WHERE l.post_id = coalesce(NEW.post_id, OLD.post_id)
  ) WHERE id = coalesce(NEW.post_id, OLD.post_id);
  RETURN NULL;
END; $$;

CREATE OR REPLACE FUNCTION public.community_comment_likes_recount()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE public.community_comments SET likes_count = (
    SELECT count(*) FROM public.community_comment_likes l
     WHERE l.comment_id = coalesce(NEW.comment_id, OLD.comment_id)
  ) WHERE id = coalesce(NEW.comment_id, OLD.comment_id);
  RETURN NULL;
END; $$;

CREATE OR REPLACE FUNCTION public.community_comments_recount()
RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE public.community_posts SET comments_count = (
    SELECT count(*) FROM public.community_comments c
     WHERE c.post_id = coalesce(NEW.post_id, OLD.post_id)
  ) WHERE id = coalesce(NEW.post_id, OLD.post_id);
  RETURN NULL;
END; $$;

DROP TRIGGER IF EXISTS community_post_likes_count ON public.community_post_likes;
CREATE TRIGGER community_post_likes_count
  AFTER INSERT OR DELETE ON public.community_post_likes
  FOR EACH ROW EXECUTE FUNCTION public.community_post_likes_recount();

DROP TRIGGER IF EXISTS community_comment_likes_count ON public.community_comment_likes;
CREATE TRIGGER community_comment_likes_count
  AFTER INSERT OR DELETE ON public.community_comment_likes
  FOR EACH ROW EXECUTE FUNCTION public.community_comment_likes_recount();

DROP TRIGGER IF EXISTS community_comments_count ON public.community_comments;
CREATE TRIGGER community_comments_count
  AFTER INSERT OR DELETE ON public.community_comments
  FOR EACH ROW EXECUTE FUNCTION public.community_comments_recount();

-- ---------------------------------------------------------------------------------------------
-- 2. Edit windows
-- ---------------------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.community_post_edit_guard()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  -- pg_trigger_depth() > 1 means this UPDATE came from a recount trigger above, not from a person
  -- editing. Those may touch the counters and nothing else.
  IF pg_trigger_depth() > 1 THEN
    RETURN NEW;
  END IF;

  -- Counters are not the client's to set. Pinned silently rather than rejected, so an older app
  -- build that still sends them can still save its own post instead of erroring on a column it
  -- had no business writing.
  NEW.likes_count := OLD.likes_count;
  NEW.comments_count := OLD.comments_count;

  -- Identity is immutable: rewriting author_name is how you impersonate someone.
  NEW.user_id := OLD.user_id;
  NEW.author_name := OLD.author_name;
  NEW.created_at := OLD.created_at;

  IF (NEW.content IS DISTINCT FROM OLD.content
      OR NEW.image_url IS DISTINCT FROM OLD.image_url
      OR NEW.tags IS DISTINCT FROM OLD.tags)
     AND now() > OLD.created_at + interval '48 hours' THEN
    -- Named, not silent: an RLS refusal matches zero rows, and the app cannot tell "not yours"
    -- apart from "too late".
    RAISE EXCEPTION 'post_edit_window_expired';
  END IF;

  NEW.updated_at := now();
  RETURN NEW;
END; $$;

CREATE OR REPLACE FUNCTION public.community_comment_edit_guard()
RETURNS trigger LANGUAGE plpgsql SET search_path = public AS $$
BEGIN
  IF pg_trigger_depth() > 1 THEN
    RETURN NEW;
  END IF;

  NEW.likes_count := OLD.likes_count;
  NEW.user_id := OLD.user_id;
  NEW.author_name := OLD.author_name;
  NEW.post_id := OLD.post_id;
  NEW.created_at := OLD.created_at;

  IF NEW.content IS DISTINCT FROM OLD.content
     AND now() > OLD.created_at + interval '24 hours' THEN
    RAISE EXCEPTION 'comment_edit_window_expired';
  END IF;

  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS community_posts_edit_guard ON public.community_posts;
CREATE TRIGGER community_posts_edit_guard
  BEFORE UPDATE ON public.community_posts
  FOR EACH ROW EXECUTE FUNCTION public.community_post_edit_guard();

DROP TRIGGER IF EXISTS community_comments_edit_guard ON public.community_comments;
CREATE TRIGGER community_comments_edit_guard
  BEFORE UPDATE ON public.community_comments
  FOR EACH ROW EXECUTE FUNCTION public.community_comment_edit_guard();

-- ---------------------------------------------------------------------------------------------
-- 3. Policies
-- ---------------------------------------------------------------------------------------------

-- The old blanket-edit policies: any authenticated user could rewrite any row.
DROP POLICY IF EXISTS community_posts_update ON public.community_posts;
DROP POLICY IF EXISTS community_comments_update ON public.community_comments;

-- Author only. The time limit lives in the trigger so it can name itself; RLS answers only
-- "is this yours". Both must pass.
CREATE POLICY community_posts_update ON public.community_posts
  FOR UPDATE TO authenticated
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY community_comments_update ON public.community_comments
  FOR UPDATE TO authenticated
  USING (auth.uid() = user_id)
  WITH CHECK (auth.uid() = user_id);

-- Close the impersonation hole: the previous INSERT policies allowed user_id IS NULL beside a
-- free-text author_name, so anyone could post under any name with no owner recorded.
DROP POLICY IF EXISTS community_posts_insert ON public.community_posts;
DROP POLICY IF EXISTS community_comments_insert ON public.community_comments;

CREATE POLICY community_posts_insert ON public.community_posts
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);

-- Everyone signed in may comment, on any post, not only their own.
CREATE POLICY community_comments_insert ON public.community_comments
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);
