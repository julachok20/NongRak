-- Daily allowance for followers who have not linked a NongRak account.
--
-- Every AI answer costs money, and a follower who never links is an unbounded expense. The cap
-- is per Bangkok day so it resets overnight rather than locking anyone out for good.
create table if not exists public.line_guest_usage (
  line_user_id text not null,
  day          date not null,
  used         int  not null default 0,
  primary key (line_user_id, day)
);

alter table public.line_guest_usage enable row level security;
-- No policies: only the service role (the webhook) touches this.

-- Atomic take: increments and returns the new count in ONE statement, so two messages arriving
-- together cannot both read the old value and each be allowed through.
create or replace function public.line_guest_take(p_line_user_id text)
returns int
language sql
security definer
set search_path = public
as $$
  insert into line_guest_usage (line_user_id, day, used)
  values (p_line_user_id, (now() at time zone 'Asia/Bangkok')::date, 1)
  on conflict (line_user_id, day)
    do update set used = line_guest_usage.used + 1
  returning used;
$$;

revoke all on function public.line_guest_take(text) from public, anon, authenticated;
