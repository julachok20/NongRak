-- Keyword memory for long chats.
--
-- The Spark pipeline reads only the last few messages word for word. ai-chat folds everything
-- older into `memory` (short keyword lines) once 30 answers have built up since `compacted_until`,
-- and sends that memory along with every question. Existing RLS ("Users can manage own chat
-- sessions", user_id = auth.uid()) already covers reading and updating both columns.
alter table public.chat_sessions
  add column if not exists memory text not null default '',
  add column if not exists compacted_until timestamptz;
