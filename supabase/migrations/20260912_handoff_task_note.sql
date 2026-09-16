-- A caregiver ticking a task can say what actually happened ("ดื่มหมด 150 มล. ไม่แหวะนม").
-- The note lives on the task itself so the parent reads it next to the item, not as one blob at
-- the end of the shift. Only the recipient can write it (unchanged rule), max 300 characters, and
-- an empty note keeps whatever was there rather than wiping a note when a box is re-ticked.
create or replace function public.handoff_act(
  p_id uuid,
  p_action text,
  p_note text default '',
  p_task_index integer default null,
  p_done boolean default null
) returns void
language plpgsql
security definer
set search_path = public
as $$
declare h public.care_handoffs; target text; notify_user uuid;
begin
 select * into h from public.care_handoffs where id=p_id for update;
 if not found or auth.uid() is null or auth.uid() not in(h.sender_id,h.recipient_id) or not public.is_child_member(h.child_id) then raise exception 'Handoff: participant access required'; end if;
 if p_note is null or length(p_note)>4000 then raise exception 'Handoff: note exceeds 4000 characters'; end if;
 target := case p_action when 'accept' then 'active' when 'decline' then 'declined' when 'cancel' then 'cancelled' when 'complete' then 'completed' when 'acknowledge' then 'closed' end;
 if p_action in ('accept','decline','complete','task') and auth.uid()<>h.recipient_id then raise exception 'Handoff: only recipient may perform this action'; end if;
 if p_action in ('cancel','acknowledge') and auth.uid()<>h.sender_id then raise exception 'Handoff: only sender may perform this action'; end if;
 if target=h.status then return; end if;
 if (p_action in ('accept','decline','cancel') and h.status='pending')
 or (p_action='complete' and h.status='active')
 or (p_action='acknowledge' and h.status='completed') then
  if p_action='accept' and not exists(select 1 from public.child_members where child_id=h.child_id and user_id=h.sender_id) then raise exception 'Handoff: sender no longer has access'; end if;
  update public.care_handoffs set status=target,
   accepted_at=case when p_action='accept' then now() else accepted_at end,
   completed_at=case when p_action='complete' then now() else completed_at end,
   closed_at=case when p_action='acknowledge' then now() else closed_at end,
   return_note=case when p_action in ('complete','decline') then p_note else return_note end where id=p_id;
 elsif p_action='task' and h.status='active' then
  if p_task_index is null or p_task_index<0 or p_task_index>=jsonb_array_length(h.tasks) or p_done is null then raise exception 'Handoff: invalid task index or completion value'; end if;
  if length(p_note)>300 then raise exception 'Handoff: task note exceeds 300 characters'; end if;
  update public.care_handoffs set tasks=jsonb_set(
    jsonb_set(tasks,array[p_task_index::text,'done'],to_jsonb(p_done)),
    array[p_task_index::text,'note'],
    to_jsonb(coalesce(nullif(p_note,''), tasks->p_task_index->>'note', ''))
  ) where id=p_id;
 else raise exception 'Handoff: action % is invalid while status is %',p_action,h.status;
 end if;
 insert into public.care_handoff_events(handoff_id,actor_id,action) values(p_id,auth.uid(),p_action);
 if p_action<>'task' then
  notify_user:=case when auth.uid()=h.sender_id then h.recipient_id else h.sender_id end;
  if exists(select 1 from public.child_members where child_id=h.child_id and user_id=notify_user) then
   insert into public.notifications(user_id,category,title,body) values(notify_user,'normal','อัปเดตการส่งต่อ / Handoff updated','เปิดกลุ่มผู้ดูแลเพื่อดูสถานะ / Open Care Circle to view the current status.');
  end if;
 end if;
end $$;
