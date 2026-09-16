-- "เตรียม: สบู่สูตรอ่อนโยน & ผ้าเช็ดตัวอุ่น" / "เป้าหมาย: หลับต่อเนื่อง 6-8 ชม."
--
-- The routine design puts one short line under an item that has not come round yet: what to have
-- ready, or what the item is aiming at. It is deliberately not part of `detail`: detail says what
-- to do once the item starts, this says what the person on duty needs before it does, and the two
-- are read at different moments. Which of the two words prefixes it is decided by the category on
-- the client, so the stored text stays the caregiver's own words.
alter table public.care_routines
  add column if not exists prep_note text not null default '';

alter table public.care_routines
  drop constraint if exists care_routines_prep_note_len;
alter table public.care_routines
  add constraint care_routines_prep_note_len check (char_length(prep_note) <= 300);
