-- Migration: 20260919100000_public_child_card.sql
--
-- The QR on the digital ID card is meant to be scanned by someone who is NOT the parent — a nurse,
-- a teacher, whoever is holding the child — so the page behind it cannot require a sign-in.
--
-- Two things keep that from becoming "every child's allergies are public":
--   * the link carries a random per-child token, not the child id, and the parent can reset it if a
--     card is lost (one child's link never exposes another's, and guessing it is not feasible);
--   * the function returns ONLY what helps in an emergency. No address, no photo, no records, no
--     ids — owner's choice, 2026-09-19: "Emergency basics only".
--
-- The table itself stays unreadable to anon: this security-definer function is the only way in.

alter table public.children add column if not exists card_token uuid not null default gen_random_uuid();
create unique index if not exists children_card_token_key on public.children (card_token);

create or replace function public.public_child_card(p_token uuid)
returns table (
  name text,
  birthdate date,
  gender text,
  blood_group text,
  conditions text[],
  drug_allergies text[],
  food_allergies text[],
  emergency_contact text,
  emergency_relation text,
  hospital text
)
language sql
stable
security definer
set search_path = public
as $$
  select c.name,
         c.birthdate,
         c.gender,
         c.blood_group,
         coalesce((select array_agg(cc.name order by cc.name) from chronic_conditions cc where cc.child_id = c.id), '{}'),
         coalesce((select array_agg(a.name order by a.name) from allergies a
                    where a.child_id = c.id and lower(coalesce(a.type, '')) in ('drug', 'medicine', 'medication', 'ยา')), '{}'),
         coalesce((select array_agg(a.name order by a.name) from allergies a
                    where a.child_id = c.id and lower(coalesce(a.type, '')) not in ('drug', 'medicine', 'medication', 'ยา')), '{}'),
         p.phone,
         p.relationship,
         (select dv.facility_name from doctor_visits dv
           where dv.child_id = c.id and dv.facility_name is not null
           order by dv.visit_datetime desc limit 1)
    from children c
    left join profiles p on p.id = c.user_id
   where c.card_token = p_token;
$$;

revoke all on function public.public_child_card(uuid) from public;
grant execute on function public.public_child_card(uuid) to anon, authenticated;

-- A lost card is handled by changing the lock, not by deleting the child's card.
create or replace function public.reset_child_card_token(p_child_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  fresh uuid := gen_random_uuid();
  changed int;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  -- RLS is bypassed inside a security-definer function, so ownership is checked here instead.
  update children set card_token = fresh
   where id = p_child_id and user_id = auth.uid();
  get diagnostics changed = row_count;
  if changed = 0 then
    raise exception 'not_your_child';
  end if;
  return fresh;
end;
$$;

revoke all on function public.reset_child_card_token(uuid) from public, anon;
grant execute on function public.reset_child_card_token(uuid) to authenticated;
