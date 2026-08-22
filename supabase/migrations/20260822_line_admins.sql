-- Support admins, decoupled from NongRak accounts.
--
-- is_admin used to live on line_links, whose primary key is user_id with a foreign key to
-- auth.users. That meant an admin had to be a registered PARENT — a support person could not
-- exist without a NongRak account and, implicitly, a child. Keyed on the LINE id alone, an
-- admin only has to add the OA as a friend.
create table if not exists public.line_admins (
  line_user_id   text primary key,
  display_name   text,
  note           text,
  notify_enabled boolean not null default true,
  added_at       timestamptz not null default now()
);

alter table public.line_admins enable row level security;
-- No policies: only the service role (the webhook) reads this.

-- Carry the existing admin across so support never has a window with nobody watching it.
insert into public.line_admins (line_user_id, display_name, note)
select line_user_id, display_name, 'migrated from line_links.is_admin'
from public.line_links
where is_admin
on conflict (line_user_id) do nothing;
