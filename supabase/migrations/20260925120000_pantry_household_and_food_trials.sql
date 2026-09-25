-- =====================================================================
-- The pantry becomes a HOUSEHOLD list, and food trials get their own table.
--
-- Before: fridge_ingredients was per child, with an RPC to copy rows to a
-- sibling. Two children of the same parent therefore kept two lists of the
-- same physical fridge, and a nanny saw only the child she was added to.
--
-- After: a row belongs to a household — the owner of the child it was added
-- under (children.user_id). Everyone who cares for ANY child of that household
-- sees the one list. Two households never merge, so a nanny who works for two
-- families sees each family's fridge only while looking at that family's child.
--
-- Food trials (the 3-4 day wait rule) stay PER CHILD on purpose: a reaction in
-- one child says nothing about a sibling, and marking a food safe for a child
-- who was never tested is the one mistake this feature must not make.
-- =====================================================================

-- ---------------------------------------------------------------- household

create or replace function public.is_household_member(owner uuid)
returns boolean
language sql stable security definer set search_path = public
as $$
  select owner = auth.uid()
      or exists (
        select 1
        from public.child_members m
        join public.children c on c.id = m.child_id
        where m.user_id = auth.uid() and c.user_id = owner
      );
$$;
-- Only signed-in callers, and never over /rest/v1/rpc for anon.
revoke all on function public.is_household_member(uuid) from public, anon;
grant execute on function public.is_household_member(uuid) to authenticated;

alter table public.fridge_ingredients add column if not exists owner_user_id uuid references auth.users(id);

update public.fridge_ingredients f
set owner_user_id = c.user_id
from public.children c
where c.id = f.child_id and f.owner_user_id is null;

-- A row whose child is already gone cannot name a household; nothing can read it.
delete from public.fridge_ingredients where owner_user_id is null;
alter table public.fridge_ingredients alter column owner_user_id set not null;
create index if not exists fridge_ingredients_owner on public.fridge_ingredients (owner_user_id);

-- The household is derived from the child, never taken from the client: a member
-- of one family must not be able to file a row into another family's fridge.
create or replace function public.fridge_ingredients_set_owner()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  select c.user_id into new.owner_user_id from public.children c where c.id = new.child_id;
  if new.owner_user_id is null then
    raise exception 'unknown_child %', new.child_id;
  end if;
  return new;
end;
$$;

-- A trigger function has no business being reachable as an RPC.
revoke all on function public.fridge_ingredients_set_owner() from public, anon, authenticated;

drop trigger if exists fridge_ingredients_owner_trg on public.fridge_ingredients;
create trigger fridge_ingredients_owner_trg
  before insert or update of child_id on public.fridge_ingredients
  for each row execute function public.fridge_ingredients_set_owner();

-- ---------------------------------------------------------------- the new fields
alter table public.fridge_ingredients
  add column if not exists bought_on date,
  add column if not exists expires_on date,
  add column if not exists storage text,       -- fridge | freezer | pantry, free text either way
  add column if not exists storage_note text,  -- "แช่เย็น 2-4°C", "ช่องฟรีซ -18°C"
  add column if not exists note text;

comment on column public.fridge_ingredients.owner_user_id is
  'The household: children.user_id of the child this was added under. Set by trigger, never by the client.';
comment on column public.fridge_ingredients.expires_on is
  'Drives the "ใกล้หมดอายุ" section; NULL means the parent did not say.';

-- ---------------------------------------------------------------- household RLS
drop policy if exists "fridge_ingredients_select" on public.fridge_ingredients;
drop policy if exists "fridge_ingredients_insert" on public.fridge_ingredients;
drop policy if exists "fridge_ingredients_update" on public.fridge_ingredients;
drop policy if exists "fridge_ingredients_delete" on public.fridge_ingredients;

create policy fridge_ingredients_select on public.fridge_ingredients
  for select to authenticated using (public.is_household_member(owner_user_id));

-- The insert check is on the CHILD, because owner_user_id is only filled in by the
-- trigger after this check has already run.
create policy fridge_ingredients_insert on public.fridge_ingredients
  for insert to authenticated
  with check (public.is_child_owner(child_id) or public.is_child_member(child_id));

create policy fridge_ingredients_update on public.fridge_ingredients
  for update to authenticated
  using (public.is_household_member(owner_user_id))
  with check (public.is_child_owner(child_id) or public.is_child_member(child_id));

create policy fridge_ingredients_delete on public.fridge_ingredients
  for delete to authenticated using (public.is_household_member(owner_user_id));

-- Copying between siblings was the old way to share one fridge; they now share it
-- by belonging to the same household.
drop function if exists public.sync_fridge_ingredients(uuid, uuid);

-- ---------------------------------------------------------------- food trials
create table if not exists public.food_trials (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references public.children(id) on delete cascade,
  food_name text not null,
  started_on date not null default current_date,
  -- testing: inside the 3-4 day watch window. safe / allergic: the parent decided.
  result text not null default 'testing' check (result in ('testing', 'safe', 'allergic')),
  reaction text,
  decided_on date,
  created_by uuid default auth.uid(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create unique index if not exists food_trials_child_food
  on public.food_trials (child_id, lower(btrim(food_name)));
create index if not exists food_trials_child on public.food_trials (child_id);

comment on table public.food_trials is
  'One new food at a time, watched for 3-4 days. Per child: a sibling result never speaks for this one.';

alter table public.food_trials enable row level security;

drop policy if exists food_trials_select on public.food_trials;
create policy food_trials_select on public.food_trials
  for select to authenticated
  using (public.is_child_owner(child_id) or public.is_child_member(child_id));

drop policy if exists food_trials_insert on public.food_trials;
create policy food_trials_insert on public.food_trials
  for insert to authenticated
  with check (public.is_child_owner(child_id) or public.is_child_member(child_id));

drop policy if exists food_trials_update on public.food_trials;
create policy food_trials_update on public.food_trials
  for update to authenticated
  using (public.is_child_owner(child_id) or public.is_child_member(child_id))
  with check (public.is_child_owner(child_id) or public.is_child_member(child_id));

drop policy if exists food_trials_delete on public.food_trials;
create policy food_trials_delete on public.food_trials
  for delete to authenticated using (public.is_child_owner(child_id));
