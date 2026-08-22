-- Which LINE rich menu belongs to which language.
--
-- A table rather than constants in the edge function: regenerating a menu produces a new
-- richMenuId, and that should be a one-row UPDATE, not a redeploy of the webhook.
create table if not exists public.line_rich_menus (
  lang         text primary key check (lang in ('th', 'en')),
  rich_menu_id text not null,
  updated_at   timestamptz not null default now()
);

alter table public.line_rich_menus enable row level security;
-- No policies: webhook-only (service role).

-- Values are environment-specific; re-run after regenerating menus.
insert into public.line_rich_menus (lang, rich_menu_id) values
  ('th', 'richmenu-f54b17f4d1c7ded8e2e485ab4a732bab'),
  ('en', 'richmenu-24344abe33884756c1aa76fcd37d1ca4')
on conflict (lang) do update
  set rich_menu_id = excluded.rich_menu_id, updated_at = now();
