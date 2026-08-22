-- LINE OA account linking + notification routing.
--
-- The app has no push notifications at all today: NotificationRepository writes rows that
-- are only seen when someone opens the app. LINE is therefore the first channel that can
-- reach a parent when the app is closed, which is the whole point of this table.

-- One LINE account per NongRak account, both ways.
--   * ON DELETE CASCADE is deliberate. Six existing FKs are ON DELETE NO ACTION and are the
--     reason in-app account deletion currently fails; this must not become the seventh.
create table if not exists public.line_links (
  user_id        uuid primary key references auth.users(id) on delete cascade,
  line_user_id   text not null unique,
  display_name   text,
  notify_enabled boolean not null default true,
  linked_at      timestamptz not null default now()
);

-- Short-lived pairing codes. The app asks for one, the parent sends it to the OA, and the
-- webhook turns "some LINE user said ABCD2345" into "that is this NongRak account".
create table if not exists public.line_pair_codes (
  code       text primary key,
  user_id    uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '10 minutes'),
  used_at    timestamptz
);
create index if not exists line_pair_codes_user_idx on public.line_pair_codes(user_id);

alter table public.line_links      enable row level security;
alter table public.line_pair_codes enable row level security;

-- A user may see and manage their own link, and nothing else. Inserts are deliberately NOT
-- granted: only the webhook (service role) may create a link, because only the webhook has
-- actually heard from the LINE side and can prove the LINE user id is real.
drop policy if exists line_links_select_own on public.line_links;
create policy line_links_select_own on public.line_links
  for select using (auth.uid() = user_id);

drop policy if exists line_links_update_own on public.line_links;
create policy line_links_update_own on public.line_links
  for update using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists line_links_delete_own on public.line_links;
create policy line_links_delete_own on public.line_links
  for delete using (auth.uid() = user_id);

-- No policies at all on line_pair_codes: unreachable from the client. Codes are issued by the
-- RPC below and consumed by the webhook, both of which bypass RLS. A readable code table
-- would let any logged-in user harvest a pending code and hijack someone else's link.

-- Issues a fresh code for the caller. SECURITY DEFINER so it can write a table the caller
-- cannot see.
create or replace function public.line_issue_pair_code()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  -- No 0/O and no 1/I/L: this is read off one screen and typed or sent by a tired parent.
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  new_code text := '';
  i int;
begin
  if auth.uid() is null then
    raise exception 'not authenticated';
  end if;

  -- Supersede any codes still outstanding for this user, so a screenshot of an older code
  -- taken from a shared phone cannot be redeemed later.
  update public.line_pair_codes
     set used_at = now()
   where user_id = auth.uid() and used_at is null;

  for i in 1..8 loop
    new_code := new_code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
  end loop;

  insert into public.line_pair_codes(code, user_id) values (new_code, auth.uid());
  return new_code;
end;
$$;

revoke all on function public.line_issue_pair_code() from public;
grant execute on function public.line_issue_pair_code() to authenticated;
