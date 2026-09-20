-- Migration: สิ่งที่น้องชอบ — the child's favourites, plus the fields the edit page needs
-- Date: 2026-09-20
-- Description:
--   A favourite is "ตุ๊กตากระต่าย 'มีมี่'", "กล้วยน้ำว้าบด 2 ช้อนชา", "ลูบหลังเบาๆ จังหวะสม่ำเสมอ".
--   The parent records them, the AI adds what it notices, and the nanny reads them on the
--   handover screen so she can soothe the child the way he is used to being soothed.
--
--   Deliberately a table, not a column on children: items arrive one at a time from three
--   different places, each needs to say where it came from, and each is removed on its own.

-- Parents vs caretakers. Both see the child; only a parent decides what the child IS.
-- A nanny follows the notes, she does not rewrite them. Repeated in four policies below,
-- so it lives here once. SECURITY DEFINER keeps the membership lookup out of RLS recursion.
create or replace function public.is_child_parent(cid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.child_members m
    where m.child_id = cid
      and m.user_id = auth.uid()
      and m.role = any (array['owner', 'co_owner', 'member'])
  );
$$;

comment on function public.is_child_parent(uuid) is
  'True when the caller is an owner/co_owner/member of the child — the roles allowed to change what the child is. Excludes nanny, doctor and other, who may read but not write.';

create table if not exists public.child_favorites (
  id uuid primary key default gen_random_uuid(),
  child_id uuid not null references public.children(id) on delete cascade,
  -- The six groups the edit page and the handover page both draw from.
  category text not null check (category in ('toy', 'food', 'sound', 'activity', 'soothing', 'interaction')),
  label text not null,
  -- Where it came from, so the parent can tell their own note from the AI's guess.
  source text not null default 'parent' check (source in ('parent', 'ai', 'info_ask')),
  created_by uuid references auth.users(id) default auth.uid(),
  created_at timestamptz not null default now(),
  constraint child_favorites_label_len check (char_length(label) between 1 and 120),
  -- The AI re-reading the same activity must not stack a second copy of the same favourite.
  unique (child_id, category, label)
);

create index if not exists child_favorites_child on public.child_favorites (child_id, category);

alter table public.child_favorites enable row level security;

-- Fields the แก้ไขข้อมูลลูก page shows that had nowhere to live.
alter table public.children
  add column if not exists soothing_note text,
  add column if not exists favorites_shared boolean not null default true,
  add column if not exists birth_time time,
  add column if not exists national_id text;

comment on column public.children.soothing_note is 'ข้อสังเกตและวิธีปลอบใจเมื่อน้องงอแง — free text shown to caretakers alongside favourites, and hidden with them when favorites_shared is off.';
comment on column public.children.favorites_shared is 'แชร์ให้ผู้ดูแลเห็น. Off hides favourites and soothing_note from nanny/doctor/other; parents always see them.';
comment on column public.children.birth_time is 'เวลาเกิด, printed on the Health Book and the digital ID card.';
comment on column public.children.national_id is 'เลขประจำตัวประชาชน / รหัสเด็ก. Sensitive: RLS on children already limits this to the child''s own members.';

-- Caretakers see favourites only while the parent leaves sharing on; a parent always sees them.
drop policy if exists child_favorites_read on public.child_favorites;
create policy child_favorites_read on public.child_favorites
  for select using (
    public.is_child_member(child_id)
    and (
      public.is_child_parent(child_id)
      or coalesce((select c.favorites_shared from public.children c where c.id = child_favorites.child_id), false)
    )
  );

drop policy if exists child_favorites_insert on public.child_favorites;
create policy child_favorites_insert on public.child_favorites
  for insert with check (public.is_child_parent(child_id));

drop policy if exists child_favorites_update on public.child_favorites;
create policy child_favorites_update on public.child_favorites
  for update using (public.is_child_parent(child_id)) with check (public.is_child_parent(child_id));

drop policy if exists child_favorites_delete on public.child_favorites;
create policy child_favorites_delete on public.child_favorites
  for delete using (public.is_child_parent(child_id));
