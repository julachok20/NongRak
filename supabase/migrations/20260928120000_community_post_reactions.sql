-- Migration: 20260928120000_community_post_reactions.sql
--
-- A like becomes a reaction: ถูกใจ / รักเลย / ฮาา / เสียใจด้วย.
--
-- On the existing community_post_likes row rather than a new table, because a person still has
-- exactly ONE reaction per post - picking รักเลย replaces ถูกใจ, it does not add to it. The
-- primary key (post_id, user_id) already says that, and a second table would let it be violated.
--
-- Every row written before today was a plain like, which is why 'like' is the default: the
-- backfill is the default, and likes_count keeps meaning "how many people reacted".

alter table public.community_post_likes
  add column if not exists reaction text not null default 'like';

do $$ begin
  alter table public.community_post_likes
    add constraint community_post_likes_reaction_check
    check (reaction in ('like', 'love', 'laugh', 'sad'));
exception when duplicate_object then null; end $$;

-- Changing a reaction is an UPDATE of your own row. There was no update policy at all before,
-- because a like could only be added or removed.
drop policy if exists community_post_likes_update on public.community_post_likes;
create policy community_post_likes_update on public.community_post_likes
  for update to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Counting reactions per post for the summary row on the card.
create index if not exists community_post_likes_reaction_idx
  on public.community_post_likes (post_id, reaction);
