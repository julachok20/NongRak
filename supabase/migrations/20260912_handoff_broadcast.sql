-- Care handover: one parent submission reaches every caregiver ticked in Permission settings.
--
-- Until now a handoff was one sender -> one recipient, and a unique index allowed a single open
-- handoff per child, so a family with a nanny AND a grandmother could only brief one of them.
-- The unit of "open" becomes (child, recipient): each caregiver accepts, works and completes their
-- own row, which keeps handoff_act, the events table and the participant-only RLS untouched.

begin;

-- 1. One open handoff per caregiver, not per child.
drop index if exists public.care_handoffs_one_open;
create unique index care_handoffs_one_open
  on public.care_handoffs (child_id, recipient_id)
  where (status in ('pending', 'active'));

-- 2. The 1:1 send keeps working; only its duplicate guard narrows to the same pair.
create or replace function public.handoff_create(
  p_id uuid,
  p_child_id uuid,
  p_recipient_id uuid,
  p_instructions text,
  p_tasks jsonb,
  p_expected_end timestamptz default null
) returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare h public.care_handoffs; task jsonb;
begin
  if auth.uid() is null or not public.is_child_member(p_child_id) then raise exception 'Handoff: child access required'; end if;
  if p_recipient_id=auth.uid() or not exists(select 1 from public.child_members where child_id=p_child_id and user_id=p_recipient_id) then raise exception 'Handoff: recipient must be another current member'; end if;
  -- Lock the child to serialize competing senders; the partial index also guards direct SQL.
  perform 1 from public.children where id=p_child_id for update;
  select * into h from public.care_handoffs where id=p_id;
  if found then
    if h.sender_id=auth.uid() and h.child_id=p_child_id and h.recipient_id=p_recipient_id then return h.id; end if;
    raise exception 'Handoff: request identifier conflict';
  end if;
  if p_instructions is null or length(p_instructions)>4000 or jsonb_typeof(p_tasks) is distinct from 'array' then raise exception 'Handoff: invalid instructions or checklist'; end if;
  if jsonb_array_length(p_tasks)>20 then raise exception 'Handoff: maximum 20 tasks'; end if;
  for task in select value from jsonb_array_elements(p_tasks) loop
    if jsonb_typeof(task) is distinct from 'string' or length(task#>>'{}') not between 1 and 300 then raise exception 'Handoff: each task must contain 1-300 characters'; end if;
  end loop;
  if p_expected_end<=now() then raise exception 'Handoff: expected return must be in the future'; end if;
  -- A revoked participant must not leave the child permanently blocked from future handoffs.
  with cancelled as (
    update public.care_handoffs old_h set status='cancelled'
    where old_h.child_id=p_child_id and old_h.status in ('pending','active')
      and (not exists(select 1 from public.child_members m where m.child_id=p_child_id and m.user_id=old_h.sender_id)
        or not exists(select 1 from public.child_members m where m.child_id=p_child_id and m.user_id=old_h.recipient_id))
    returning id
  ) insert into public.care_handoff_events(handoff_id,actor_id,action)
  select id,auth.uid(),'cancel_revoked_participant' from cancelled;
  -- Narrowed from "this child" to "this caregiver": other caregivers may hold their own handoff.
  if exists(select 1 from public.care_handoffs where child_id=p_child_id and recipient_id=p_recipient_id and status in ('pending','active')) then raise exception 'Handoff: this caregiver already has a pending or active handoff'; end if;
  insert into public.care_handoffs(id,child_id,sender_id,recipient_id,instructions,tasks,expected_end)
  values(p_id,p_child_id,auth.uid(),p_recipient_id,p_instructions,
    coalesce((select jsonb_agg(jsonb_build_object('text',value#>>'{}','done',false)) from jsonb_array_elements(p_tasks)),'[]'),p_expected_end);
  insert into public.care_handoff_events(handoff_id,actor_id,action) values(p_id,auth.uid(),'send');
  insert into public.notifications(user_id,category,title,body) values(p_recipient_id,'normal','ส่งต่อการดูแล / Care handoff','เปิดกลุ่มผู้ดูแลเพื่อดูคำขอ / Open Care Circle to review your handoff.');
  return p_id;
end $$;

-- 3. Broadcast: the parent writes the briefing once, every ticked caregiver gets their own handoff.
--
-- Returns a summary instead of a bare id so the app can say exactly what happened: a caregiver who
-- already holds an open handoff is skipped rather than failing the whole send, and "sent to nobody"
-- is a named error, not a silent success.
create or replace function public.handoff_broadcast(
  p_child_id uuid,
  p_instructions text,
  p_tasks jsonb,
  p_expected_end timestamptz
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  task jsonb;
  normalised jsonb;
  caregiver record;
  new_id uuid;
  sent int := 0;
  skipped int := 0;
  names text[] := '{}';
  skipped_names text[] := '{}';
begin
  if auth.uid() is null or not public.is_child_member(p_child_id) then raise exception 'Handoff: child access required'; end if;
  if p_instructions is null or length(p_instructions)>4000 or jsonb_typeof(p_tasks) is distinct from 'array' then raise exception 'Handoff: invalid instructions or checklist'; end if;
  if jsonb_array_length(p_tasks)>20 then raise exception 'Handoff: maximum 20 tasks'; end if;
  for task in select value from jsonb_array_elements(p_tasks) loop
    if jsonb_typeof(task) is distinct from 'string' or length(task#>>'{}') not between 1 and 300 then raise exception 'Handoff: each task must contain 1-300 characters'; end if;
  end loop;
  if p_expected_end is null or p_expected_end<=now() then raise exception 'Handoff: expected return must be in the future'; end if;

  -- Serialize competing senders on this child, exactly as the 1:1 path does.
  perform 1 from public.children where id=p_child_id for update;

  -- Clear out handoffs whose sender or recipient no longer has access to this child.
  with cancelled as (
    update public.care_handoffs old_h set status='cancelled'
    where old_h.child_id=p_child_id and old_h.status in ('pending','active')
      and (not exists(select 1 from public.child_members m where m.child_id=p_child_id and m.user_id=old_h.sender_id)
        or not exists(select 1 from public.child_members m where m.child_id=p_child_id and m.user_id=old_h.recipient_id))
    returning id
  ) insert into public.care_handoff_events(handoff_id,actor_id,action)
  select id,auth.uid(),'cancel_revoked_participant' from cancelled;

  normalised := coalesce(
    (select jsonb_agg(jsonb_build_object('text',value#>>'{}','done',false)) from jsonb_array_elements(p_tasks)),
    '[]'
  );

  for caregiver in
    select m.user_id, coalesce(nullif(m.display_name,''), 'ผู้ดูแล') as display_name
    from public.child_members m
    where m.child_id = p_child_id
      and m.is_caregiver
      and m.user_id <> auth.uid()
    order by m.created_at
  loop
    if exists(
      select 1 from public.care_handoffs
      where child_id=p_child_id and recipient_id=caregiver.user_id and status in ('pending','active')
    ) then
      skipped := skipped + 1;
      skipped_names := skipped_names || caregiver.display_name;
      continue;
    end if;

    new_id := gen_random_uuid();
    insert into public.care_handoffs(id,child_id,sender_id,recipient_id,instructions,tasks,expected_end)
    values(new_id,p_child_id,auth.uid(),caregiver.user_id,p_instructions,normalised,p_expected_end);
    insert into public.care_handoff_events(handoff_id,actor_id,action) values(new_id,auth.uid(),'send');
    insert into public.notifications(user_id,category,title,body)
    values(caregiver.user_id,'normal','ส่งต่อการดูแล / Care handover','คุณมีรายการที่ต้องดูแลวันนี้ / You have care tasks waiting today.');
    sent := sent + 1;
    names := names || caregiver.display_name;
  end loop;

  -- Nobody ticked as a caregiver means the parent's briefing went nowhere: say so.
  if sent = 0 and skipped = 0 then
    raise exception 'Handoff: no caregiver is ticked in permission settings';
  end if;

  return jsonb_build_object(
    'sent', sent,
    'skipped', skipped,
    'recipients', to_jsonb(names),
    'skipped_recipients', to_jsonb(skipped_names)
  );
end $$;

revoke all on function public.handoff_broadcast(uuid, text, jsonb, timestamptz) from public;
grant execute on function public.handoff_broadcast(uuid, text, jsonb, timestamptz) to authenticated;

commit;
