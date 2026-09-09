-- Migration: 20260908_community_posts_and_comments.sql
-- Description: Sets up community posts, comments, and likes tables with RLS and community-media storage bucket.

-- 1. Community Posts Table
CREATE TABLE IF NOT EXISTS public.community_posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  author_name text NOT NULL,
  author_role text NOT NULL DEFAULT 'คุณแม่',
  author_avatar_url text,
  content text NOT NULL,
  tags jsonb NOT NULL DEFAULT '[]'::jsonb,
  image_url text,
  likes_count integer NOT NULL DEFAULT 0,
  comments_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_posts_created_at_idx
  ON public.community_posts (created_at DESC);

CREATE INDEX IF NOT EXISTS community_posts_user_id_idx
  ON public.community_posts (user_id);

-- 2. Community Comments Table
CREATE TABLE IF NOT EXISTS public.community_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES public.community_posts(id) ON DELETE CASCADE,
  user_id uuid REFERENCES auth.users(id) ON DELETE SET NULL,
  author_name text NOT NULL,
  author_role text NOT NULL DEFAULT 'คุณแม่',
  author_avatar_url text,
  content text NOT NULL,
  likes_count integer NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS community_comments_post_created_idx
  ON public.community_comments (post_id, created_at ASC);

CREATE INDEX IF NOT EXISTS community_comments_user_id_idx
  ON public.community_comments (user_id);

-- 3. Community Post Likes Table
CREATE TABLE IF NOT EXISTS public.community_post_likes (
  post_id uuid NOT NULL REFERENCES public.community_posts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS community_post_likes_user_idx
  ON public.community_post_likes (user_id);

-- 4. Community Comment Likes Table
CREATE TABLE IF NOT EXISTS public.community_comment_likes (
  comment_id uuid NOT NULL REFERENCES public.community_comments(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS community_comment_likes_user_idx
  ON public.community_comment_likes (user_id);

-- 5. Row Level Security (RLS)
ALTER TABLE public.community_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.community_comment_likes ENABLE ROW LEVEL SECURITY;

-- Select policies: Community is public readable by all authenticated users and anon
CREATE POLICY community_posts_select ON public.community_posts
  FOR SELECT USING (true);

CREATE POLICY community_comments_select ON public.community_comments
  FOR SELECT USING (true);

CREATE POLICY community_post_likes_select ON public.community_post_likes
  FOR SELECT USING (true);

CREATE POLICY community_comment_likes_select ON public.community_comment_likes
  FOR SELECT USING (true);

-- Insert policies
CREATE POLICY community_posts_insert ON public.community_posts
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id OR user_id IS NULL);

CREATE POLICY community_comments_insert ON public.community_comments
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id OR user_id IS NULL);

CREATE POLICY community_post_likes_insert ON public.community_post_likes
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);

CREATE POLICY community_comment_likes_insert ON public.community_comment_likes
  FOR INSERT TO authenticated
  WITH CHECK (auth.uid() = user_id);

-- Update policies: Allow updating like/comment counts & author updates
CREATE POLICY community_posts_update ON public.community_posts
  FOR UPDATE TO authenticated
  USING (true)
  WITH CHECK (true);

CREATE POLICY community_comments_update ON public.community_comments
  FOR UPDATE TO authenticated
  USING (true)
  WITH CHECK (true);

-- Delete policies: Only author can delete their own post/comment/like
CREATE POLICY community_posts_delete ON public.community_posts
  FOR DELETE TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY community_comments_delete ON public.community_comments
  FOR DELETE TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY community_post_likes_delete ON public.community_post_likes
  FOR DELETE TO authenticated
  USING (auth.uid() = user_id);

CREATE POLICY community_comment_likes_delete ON public.community_comment_likes
  FOR DELETE TO authenticated
  USING (auth.uid() = user_id);

-- 6. Storage Bucket for Community Images & Media
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'community-media', 'community-media', true, 10485760,
  ARRAY[
    'image/jpeg', 'image/png', 'image/webp',
    'video/mp4', 'video/quicktime'
  ]
)
ON CONFLICT (id) DO NOTHING;

-- Storage policies for community-media
CREATE POLICY "community_media_public_select" ON storage.objects
  FOR SELECT TO public
  USING (bucket_id = 'community-media');

CREATE POLICY "community_media_auth_insert" ON storage.objects
  FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'community-media');
