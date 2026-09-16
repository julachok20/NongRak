-- Migration: 20260916100000_community_video.sql
--
-- A community post may carry a video. The clip itself lives in the community-media bucket and its
-- first frame is stored as image_url, so the feed card has something to show without a player.
--
-- Every picture, and every frame the app cuts out of a video, is screened by the ai-chat "moderate"
-- mode BEFORE the file is uploaded (nothing reaches the public bucket unchecked).

alter table public.community_posts
  add column if not exists video_url text;
