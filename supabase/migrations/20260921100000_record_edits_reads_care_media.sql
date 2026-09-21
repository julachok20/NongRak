-- Migration: edit history + revert, read receipts, photo/voice on care logs, urgent-alert log
-- Date: 2026-09-21
-- Backs the rebuilt ประวัติกิจกรรม (parent) and บันทึกกิจกรรมด่วน (caregiver) screens.
--
-- Everything here is enforced in Postgres. The app only draws what these tables return; hiding a
-- button is not a permission.

-- ---------------------------------------------------------------------------------------------
-- 1. record_edits — who changed what, from what, to what.
--
-- One generic AFTER trigger on the nine tables list_child_activity_feed reads. Only UPDATE and
-- DELETE are kept: an insert is already the feed row itself, and logging it again would double
-- the table for nothing.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.record_edits (
  id bigint generated always as identity primary key,
  child_id uuid not null references public.children(id) on delete cascade,
  table_name text not null,
  record_id uuid not null,
  action text not null check (action in ('update', 'delete')),
  old_row jsonb not null,
  new_row jsonb,
  actor_id uuid default auth.uid(),
  created_at timestamptz not null default now()
);
create index if not exists record_edits_child_time on public.record_edits (child_id, created_at desc);
create index if not exists record_edits_record on public.record_edits (record_id, created_at desc);

alter table public.record_edits enable row level security;
-- Everyone who can see the child can see its history. Nobody writes it directly: rows arrive only
-- through the trigger, which is SECURITY DEFINER.
drop policy if exists record_edits_read on public.record_edits;
create policy record_edits_read on public.record_edits
  for select using (public.is_child_member(child_id));

create or replace function public.log_record_edit()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if tg_op = 'UPDATE' then
    if to_jsonb(old) = to_jsonb(new) then
      return new;                                  -- a save that changed nothing is not an edit
    end if;
    insert into public.record_edits (child_id, table_name, record_id, action, old_row, new_row, actor_id)
    values (new.child_id, tg_table_name, new.id, 'update', to_jsonb(old), to_jsonb(new), auth.uid());
    return new;
  end if;

  -- DELETE. When the child itself is being deleted, its records go by cascade after the child row
  -- is already gone; an audit row pointing at it would violate the foreign key and abort the whole
  -- child deletion. There is nothing to audit about a child that no longer exists.
  if not exists (select 1 from public.children c where c.id = old.child_id) then
    return old;
  end if;
  insert into public.record_edits (child_id, table_name, record_id, action, old_row, new_row, actor_id)
  values (old.child_id, tg_table_name, old.id, 'delete', to_jsonb(old), null, auth.uid());
  return old;
end;
$$;

do $$
declare t text;
begin
  foreach t in array array['growth_records','milk_logs','food_logs','waste_records','sleep_logs',
                           'health_symptom_logs','medication_doses','care_logs','vaccine_records']
  loop
    execute format('drop trigger if exists record_edit_audit on public.%I', t);
    execute format('create trigger record_edit_audit after update or delete on public.%I
                    for each row execute function public.log_record_edit()', t);
  end loop;
end $$;

-- Puts a record back the way it was before one edit. Parents only — the same rule that decides who
-- may change what a child is. The revert is itself an UPDATE (or, for a delete, an INSERT of the old
-- row), so it lands in record_edits like any other change and can be reverted in turn.
create or replace function public.revert_record_edit(p_edit_id bigint)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  e public.record_edits%rowtype;
  cols text;
  n int;
begin
  select * into e from public.record_edits where id = p_edit_id;
  if not found then
    raise exception 'edit % not found', p_edit_id;
  end if;
  if not public.is_child_parent(e.child_id) then
    raise exception 'only a parent of this child may revert its records (edit %)', p_edit_id;
  end if;
  if e.table_name <> all (array['growth_records','milk_logs','food_logs','waste_records','sleep_logs',
                                'health_symptom_logs','medication_doses','care_logs','vaccine_records']) then
    raise exception 'records in % cannot be reverted', e.table_name;
  end if;

  if e.action = 'update' then
    select string_agg(quote_ident(column_name), ', ' order by ordinal_position) into cols
      from information_schema.columns
     where table_schema = 'public' and table_name = e.table_name and column_name <> 'id';
    execute format(
      'update public.%I t set (%s) = (select %s from jsonb_populate_record(null::public.%I, $1)) '
      'where t.id = $2 and t.child_id = $3',
      e.table_name, cols, cols, e.table_name)
      using e.old_row, e.record_id, e.child_id;
    get diagnostics n = row_count;
    if n = 0 then
      raise exception 'record % in % no longer exists, so edit % cannot be reverted',
        e.record_id, e.table_name, p_edit_id;
    end if;
  else
    execute format(
      'insert into public.%I select * from jsonb_populate_record(null::public.%I, $1)',
      e.table_name, e.table_name)
      using e.old_row;
  end if;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- 2. record_reads — "คุณแม่อ่านแล้ว".
--
-- Keyed by record id alone: ids are uuids, unique across the nine tables, and the feed row a parent
-- is looking at carries only the id.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.record_reads (
  record_id uuid not null,
  child_id uuid not null references public.children(id) on delete cascade,
  reader_id uuid not null default auth.uid(),
  read_at timestamptz not null default now(),
  primary key (record_id, reader_id)
);
create index if not exists record_reads_child on public.record_reads (child_id, record_id);

alter table public.record_reads enable row level security;
drop policy if exists record_reads_read on public.record_reads;
create policy record_reads_read on public.record_reads
  for select using (public.is_child_member(child_id));

-- Marks what the caller has now seen. Only ever writes the caller's own receipts, for a child they
-- belong to — the worst a caller can do is claim to have read something themselves.
create or replace function public.mark_records_read(p_child_id uuid, p_record_ids uuid[])
returns int
language plpgsql
security definer
set search_path = public
as $$
declare n int;
begin
  if not public.is_child_member(p_child_id) then
    raise exception 'not a member of child %', p_child_id;
  end if;
  insert into public.record_reads (record_id, child_id, reader_id)
  select distinct unnest(p_record_ids), p_child_id, auth.uid()
  on conflict (record_id, reader_id) do nothing;
  get diagnostics n = row_count;
  return n;
end;
$$;

-- ---------------------------------------------------------------------------------------------
-- 3. Photo and voice on a care log.
-- ---------------------------------------------------------------------------------------------
alter table public.care_logs
  add column if not exists photo_path text,
  add column if not exists voice_path text,
  add column if not exists voice_seconds integer;

alter table public.care_logs drop constraint if exists care_logs_voice_seconds_range;
alter table public.care_logs add constraint care_logs_voice_seconds_range
  check (voice_seconds is null or voice_seconds between 1 and 120);

comment on column public.care_logs.photo_path is 'care-media object path: {child_id}/{uploader_id}/{file}. Private; signed URLs only.';
comment on column public.care_logs.voice_path is 'care-media object path of a voice note, same layout as photo_path.';

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('care-media', 'care-media', false, 10485760,
        array['image/jpeg','image/png','image/webp','audio/mp4','audio/aac','audio/m4a','audio/x-m4a','audio/mpeg'])
on conflict (id) do nothing;

-- Same shape as chat-images: the first folder is the child, the second the uploader. Anyone on the
-- child may read; only the uploader may write into or delete from their own folder.
drop policy if exists care_media_read on storage.objects;
create policy care_media_read on storage.objects
  for select using (bucket_id = 'care-media'
                    and public.is_child_member(((storage.foldername(name))[1])::uuid));
drop policy if exists care_media_insert on storage.objects;
create policy care_media_insert on storage.objects
  for insert with check (bucket_id = 'care-media'
                         and public.is_child_member(((storage.foldername(name))[1])::uuid)
                         and (storage.foldername(name))[2] = auth.uid()::text);
drop policy if exists care_media_delete on storage.objects;
create policy care_media_delete on storage.objects
  for delete using (bucket_id = 'care-media'
                    and (storage.foldername(name))[2] = auth.uid()::text);

-- ---------------------------------------------------------------------------------------------
-- 4. care_alert_log — the line-notify-parents quota ledger.
--
-- The LINE free tier is 200 pushes a month for the whole OA. An "urgent" toggle a caregiver can tap
-- on every save would spend that in a week, so the edge function counts here before it sends.
-- No policies: only the service role (the edge function) touches it.
-- ---------------------------------------------------------------------------------------------
create table if not exists public.care_alert_log (
  id bigint generated always as identity primary key,
  child_id uuid not null references public.children(id) on delete cascade,
  sender_id uuid not null,
  recipients integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists care_alert_log_child_time on public.care_alert_log (child_id, created_at desc);
alter table public.care_alert_log enable row level security;
