-- A child has two names, and they are not the same thing.
--
-- `children.name` is the nickname (ชื่อเล่น) — what the app, the AI, and the parent call the child
-- everywhere, and what already fills every screen. It keeps its meaning and every existing read of
-- it stays correct.
--
-- `full_name` is the legal name as printed on the birth certificate, used only where a document is
-- being produced: the ID card, exports, the health book. Nullable, because the parent may not have
-- entered it and a blank legal name must read as "not given", never as the nickname.
alter table public.children
  add column if not exists full_name text;

comment on column public.children.name is 'Nickname (ชื่อเล่น) — the child''s display name across the app.';
comment on column public.children.full_name is 'Legal name for documents (ID card, exports). Null when not provided.';
