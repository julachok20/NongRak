-- Medical answers show a "not a diagnosis" banner in the chat instead of carrying the warning in
-- their text. The flag is stored with the message so the banner is still there when the chat is
-- reopened. Existing RLS ("Users can manage own chat", user_id = auth.uid()) covers the column.
alter table public.chat_messages
  add column if not exists medical boolean not null default false;
