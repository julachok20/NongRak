-- The milk/water recorder keeps more of what the parent actually chose.
--
-- milk_type stays the coarse kind (breast | formula | uht | water). subtype carries the second
-- choice the screen asks for: direct or pumped for breast milk, and which water for water. Keeping
-- them in two columns means `milk_type = 'water'` still works everywhere it already does.
--
-- left_min / right_min are the per-side minutes of a feed at the breast. duration_min stays the
-- total, so every existing reader (the food page, the handover brief, the health book) is unchanged.
alter table public.milk_logs
  add column if not exists subtype text,       -- direct | pumped | boiled | mineral | warm
  add column if not exists left_min int,
  add column if not exists right_min int,
  add column if not exists temperature text,   -- warm | room | cold
  add column if not exists reaction text,      -- finished | left_some | spit_up | easy | little | refused
  add column if not exists method text;        -- cup | straw | spoon | bottle (water)

alter table public.milk_logs drop constraint if exists milk_logs_side_minutes_range;
alter table public.milk_logs add constraint milk_logs_side_minutes_range
  check (
    (left_min is null or left_min between 0 and 120) and
    (right_min is null or right_min between 0 and 120)
  );

comment on column public.milk_logs.subtype is
  'Second choice on the recorder: direct|pumped for breast milk, boiled|mineral|warm for water.';
comment on column public.milk_logs.left_min is 'Minutes on the left breast; duration_min holds the total.';
comment on column public.milk_logs.reaction is
  'How it went: finished|left_some|spit_up for milk, easy|little|refused for water.';
