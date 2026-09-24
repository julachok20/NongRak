-- Food log: the photo the parent took, and how much of the serving the child actually ate.
--
-- photo_path reuses the existing private `care-media` bucket instead of adding a food-only one:
-- its layout is already {child_id}/{uploader_id}/{file} and its policies already say "any member
-- of this child may read, only the uploader may write" — exactly the rule a food photo needs.
--
-- eaten_pct: the record screen asks "น้องทำหกหรือไม่?" and scales the kcal by the answer. Before
-- this column the answer was folded into `calories` and thrown away, so the food list could not
-- tell "ate it all" from "ate half of twice as much". NULL means the parent was not asked.
alter table public.food_logs add column if not exists photo_path text;
alter table public.food_logs add column if not exists eaten_pct int;

alter table public.food_logs drop constraint if exists food_logs_eaten_pct_range;
alter table public.food_logs add constraint food_logs_eaten_pct_range
  check (eaten_pct is null or eaten_pct between 0 and 100);

comment on column public.food_logs.photo_path is
  'care-media object path: {child_id}/{uploader_id}/{file}. Private; signed URLs only.';
comment on column public.food_logs.eaten_pct is
  'Share of the served portion the child actually ate, 0-100. NULL = not recorded.';
