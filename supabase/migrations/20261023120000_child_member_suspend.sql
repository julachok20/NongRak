-- Migration: 20261023120000_child_member_suspend.sql
--
-- NOT YET APPLIED. The session that wrote this could not reach project rdylsezwjdujvllyjhqb, so the
-- file is ready to run but unverified. Apply and verify it BEFORE any app code reads suspended_at,
-- and before the pause control is added to จัดการครอบครัว.
--
-- Pausing is not removing. A nanny between shifts, a grandparent away for a month, a helper whose
-- access is under review - today the only options are "keeps full access" or "delete the row", and
-- deleting means re-inviting from scratch afterwards. Removal already works and is unchanged; this
-- adds the middle state, so access can go off on Friday and back on for Monday without the person
-- losing their role or their place in the history they wrote.

alter table public.child_members
  add column if not exists suspended_at timestamptz;

comment on column public.child_members.suspended_at is
  'When access was paused. Null means active. The row, its role, its capabilities and the records this person wrote all survive a pause; only access stops. Removal is a separate, destructive action.';

-- Partial: the overwhelmingly common read is "who is active here", and a suspended member is rare.
create index if not exists child_members_suspended_idx
  on public.child_members (child_id) where suspended_at is not null;

-- is_child_member is what every RLS policy on this child's data funnels through, so a pause has to
-- take effect HERE rather than in each policy. Doing it per-policy would mean a pause that holds
-- for health records but not for, say, documents - and nobody would notice which.
--
-- The owner is deliberately exempt: an owner must not be able to end up paused out of their own
-- child's record, which would leave that record unreachable by anyone.
create or replace function public.is_child_member(cid uuid)
returns boolean language sql stable security definer set search_path = public as $fn$
  select exists (
    select 1 from public.child_members m
     where m.child_id = cid
       and m.user_id = auth.uid()
       and (m.suspended_at is null or m.role = 'owner')
  );
$fn$;

-- Pause and resume, mirroring set_child_member_caregiver: only an owner or co_owner may do it, and
-- the check lives in the function rather than in the client.
create or replace function public.set_child_member_suspended(
  p_child_id uuid,
  p_user_id uuid,
  p_suspended boolean
) returns void language plpgsql security definer set search_path = public as $fn$
declare
  v_role text;
  v_target_role text;
begin
  select role into v_role from public.child_members
   where child_id = p_child_id and user_id = auth.uid();

  if v_role is null or v_role not in ('owner', 'co_owner') then
    raise exception 'not_authorised_to_manage_members';
  end if;

  select role into v_target_role from public.child_members
   where child_id = p_child_id and user_id = p_user_id;

  if v_target_role is null then
    raise exception 'member_not_found';
  end if;

  -- An owner is the last line of access to a child record; pausing one could orphan it.
  if v_target_role = 'owner' then
    raise exception 'cannot_suspend_owner';
  end if;

  update public.child_members
     set suspended_at = case when p_suspended then now() else null end
   where child_id = p_child_id and user_id = p_user_id;
end; $fn$;

revoke execute on function public.set_child_member_suspended(uuid, uuid, boolean) from public, anon;
grant execute on function public.set_child_member_suspended(uuid, uuid, boolean) to authenticated;

-- The members list has to say who is paused, or a parent cannot see the state they just set.
-- The return type changes, so the function is dropped and recreated rather than replaced.
drop function if exists public.list_child_access_members(uuid);

create function public.list_child_access_members(p_child_id uuid)
 returns table(user_id uuid, role text, display_name text, email text, avatar_url text,
   can_view_health boolean, can_log_symptoms boolean, can_edit_growth boolean,
   can_view_appointments boolean, can_book_doctor boolean, can_cancel_appointments boolean,
   can_view_documents boolean, can_upload_documents boolean, can_edit_profile boolean,
   created_at timestamp with time zone, capabilities text[], role_id uuid,
   role_name_th text, role_name_en text, is_caregiver boolean, is_family boolean,
   suspended_at timestamp with time zone)
 language plpgsql
 security definer
 set search_path to 'public'
as $function$
begin
  -- A paused member must still be able to SEE that they are paused, so this checks membership
  -- directly rather than through is_child_member, which now excludes them.
  if not exists (
    select 1 from public.child_members m
     where m.child_id = p_child_id and m.user_id = auth.uid()
  ) then
    raise exception 'not authorized';
  end if;

  return query
  select m.user_id, m.role,
    coalesce(m.display_name, p.username, p.full_name, u.email::text, 'User'),
    u.email::text, p.avatar_url,
    m.can_view_health, m.can_log_symptoms, m.can_edit_growth,
    m.can_view_appointments, m.can_book_doctor, m.can_cancel_appointments,
    m.can_view_documents, m.can_upload_documents, m.can_edit_profile, m.created_at,
    m.capabilities, m.role_id, r.name_th, r.name_en, m.is_caregiver, m.is_family,
    m.suspended_at
  from public.child_members m
  left join public.profiles p on p.id = m.user_id
  left join auth.users u on u.id = m.user_id
  left join public.child_roles r on r.id = m.role_id
  where m.child_id = p_child_id
  order by case m.role when 'owner' then 0 when 'co_owner' then 1 when 'member' then 1
                       when 'doctor' then 2 when 'nanny' then 3 else 4 end, m.created_at;
end; $function$;
