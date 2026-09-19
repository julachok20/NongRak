-- Migration: 20260917100000_email_verified.sql
--
-- Optional "this email really belongs to me" badge (owner, 2026-09-17: confirming is the parent's
-- choice, not required; it tells us the account is a real person). Supabase's own
-- auth.users.email_confirmed_at cannot be used: "Confirm email" is off, so every account was
-- auto-confirmed without anyone opening a mailbox.
--
-- profiles.email_verified_at is set only by confirm_email_ownership(), and only when the caller's
-- session was created by a code/link sent to that email (JWT amr method otp/magiclink/...). A LINE
-- sign-in also records "otp", which is acceptable: LINE only shares an email it verified itself, and
-- LINE's synthetic @line.invalid addresses are refused. Clients cannot write the column directly,
-- and changing the account email clears it.

alter table public.profiles add column if not exists email_verified_at timestamptz;

create or replace function public.confirm_email_ownership()
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_uid uuid := auth.uid();
  v_email text;
begin
  if v_uid is null then
    raise exception 'not_signed_in';
  end if;
  if not exists (
    select 1 from jsonb_array_elements(coalesce(auth.jwt() -> 'amr', '[]'::jsonb)) m
     where m ->> 'method' in ('otp', 'magiclink', 'email/signup', 'email_change', 'recovery', 'invite')
  ) then
    return false;  -- signed in by password / Google: nothing was proven about the mailbox
  end if;
  select email into v_email from auth.users where id = v_uid;
  if v_email is null or v_email like '%@line.invalid' then
    return false;
  end if;
  update public.profiles set email_verified_at = coalesce(email_verified_at, now()) where id = v_uid;
  return true;
end;
$$;

revoke all on function public.confirm_email_ownership() from public, anon;
grant execute on function public.confirm_email_ownership() to authenticated;

-- Only confirm_email_ownership / the reset trigger (running as their owner) may change the column.
create or replace function public.guard_email_verified_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if current_user in ('authenticated', 'anon') then
    if tg_op = 'INSERT' then
      new.email_verified_at := null;
    elsif new.email_verified_at is distinct from old.email_verified_at then
      new.email_verified_at := old.email_verified_at;
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists guard_email_verified_at on public.profiles;
create trigger guard_email_verified_at
  before insert or update on public.profiles
  for each row execute function public.guard_email_verified_at();

-- A new address has not been proven yet.
create or replace function public.reset_email_verified_on_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.email is distinct from old.email then
    update public.profiles set email_verified_at = null where id = new.id;
  end if;
  return new;
end;
$$;

drop trigger if exists reset_email_verified_on_change on auth.users;
create trigger reset_email_verified_on_change
  after update of email on auth.users
  for each row execute function public.reset_email_verified_on_change();
