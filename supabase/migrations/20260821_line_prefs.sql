-- Per-LINE-user preferences (language today).
--
-- Keyed by line_user_id, NOT by auth user id, and deliberately separate from line_links: people
-- chat with the OA before they ever pair an account, and /eng has to work for them too. Keying
-- this on line_links.user_id would mean an unlinked user's language change silently did nothing.
create table if not exists public.line_prefs (
  line_user_id text primary key,
  lang         text not null default 'th' check (lang in ('th', 'en')),
  updated_at   timestamptz not null default now()
);

alter table public.line_prefs enable row level security;
-- No policies at all: written and read only by the webhook, which uses the service role.
