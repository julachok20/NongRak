-- Migration: 20260916140000_active_sessions.sql  (applied 2026-09-16)
--
-- Settings › ประวัติการเข้าสู่ระบบและอุปกรณ์ (Active Sessions).
-- auth.sessions is not readable from the app, so these security-definer functions expose only the
-- caller's own sessions. The current session comes from the JWT's session_id claim.
--
-- Signing a session out deletes its auth.sessions row (its refresh tokens go with it). That device's
-- current access token still works until it expires (up to 1 hour); after that it is signed out.

create or replace function public.list_my_sessions()
returns table (
  id uuid,
  created_at timestamptz,
  last_active_at timestamptz,
  user_agent text,
  ip text,
  is_current boolean
)
language sql
stable
security definer
set search_path = public, auth
as $$
  select s.id,
         s.created_at,
         coalesce(s.refreshed_at at time zone 'UTC', s.updated_at, s.created_at) as last_active_at,
         s.user_agent,
         host(s.ip) as ip,
         s.id = nullif(auth.jwt() ->> 'session_id', '')::uuid as is_current
    from auth.sessions s
   where s.user_id = auth.uid()
     and (s.not_after is null or s.not_after > now())
   order by 3 desc;
$$;

create or replace function public.revoke_my_session(p_session_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  removed int;
begin
  if auth.uid() is null then
    raise exception 'not_signed_in';
  end if;
  -- The phone in hand signs out with ออกจากระบบ, which also handles biometric sign-in correctly.
  if p_session_id = nullif(auth.jwt() ->> 'session_id', '')::uuid then
    raise exception 'cannot_revoke_current_session';
  end if;
  delete from auth.sessions where id = p_session_id and user_id = auth.uid();
  get diagnostics removed = row_count;
  return removed > 0;
end;
$$;

create or replace function public.revoke_my_other_sessions()
returns integer
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  current_session uuid := nullif(auth.jwt() ->> 'session_id', '')::uuid;
  removed int;
begin
  if auth.uid() is null or current_session is null then
    raise exception 'not_signed_in';
  end if;
  delete from auth.sessions where user_id = auth.uid() and id <> current_session;
  get diagnostics removed = row_count;
  return removed;
end;
$$;

revoke all on function public.list_my_sessions() from public, anon;
revoke all on function public.revoke_my_session(uuid) from public, anon;
revoke all on function public.revoke_my_other_sessions() from public, anon;
grant execute on function public.list_my_sessions() to authenticated;
grant execute on function public.revoke_my_session(uuid) to authenticated;
grant execute on function public.revoke_my_other_sessions() to authenticated;
