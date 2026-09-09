-- Additive rollout: existing care records and permissions remain authoritative.
create table public.care_handoffs (
 id uuid primary key,
 child_id uuid not null references public.children(id) on delete cascade,
 sender_id uuid not null references auth.users(id) on delete cascade,
 recipient_id uuid not null references auth.users(id) on delete cascade,
 status text not null default 'pending' check(status in ('pending','active','declined','cancelled','completed','closed')),
 instructions text not null default '' check(length(instructions)<=4000),
 tasks jsonb not null default '[]' check(jsonb_typeof(tasks)='array'),
 return_note text not null default '' check(length(return_note)<=4000),
 expected_end timestamptz,
 created_at timestamptz not null default now(),
 accepted_at timestamptz,
 completed_at timestamptz,
 closed_at timestamptz,
 check(sender_id<>recipient_id)
);
create unique index care_handoffs_one_open on public.care_handoffs(child_id) where status in ('pending','active');
create index care_handoffs_recipient on public.care_handoffs(recipient_id,created_at desc);
create index care_handoffs_sender on public.care_handoffs(sender_id,created_at desc);
alter table public.care_handoffs enable row level security;
revoke all on public.care_handoffs from anon,authenticated;
grant select on public.care_handoffs to authenticated;
create policy handoff_participant_read on public.care_handoffs for select to authenticated
using (auth.uid() in (sender_id,recipient_id) and public.is_child_member(child_id));

create table public.care_handoff_events (
 id bigint generated always as identity primary key,
 handoff_id uuid not null references public.care_handoffs(id) on delete cascade,
 actor_id uuid not null references auth.users(id) on delete cascade,
 action text not null,
 created_at timestamptz not null default now()
);
create index care_handoff_events_handoff on public.care_handoff_events(handoff_id);
alter table public.care_handoff_events enable row level security;
revoke all on public.care_handoff_events from anon,authenticated;
grant select on public.care_handoff_events to authenticated;
create policy handoff_event_read on public.care_handoff_events for select to authenticated
using(exists(select 1 from public.care_handoffs h where h.id=handoff_id));

create function public.handoff_create(p_id uuid,p_child_id uuid,p_recipient_id uuid,p_instructions text,p_tasks jsonb,p_expected_end timestamptz default null)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
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
 if exists(select 1 from public.care_handoffs where child_id=p_child_id and status in ('pending','active')) then raise exception 'Handoff: this child already has a pending or active handoff'; end if;
 insert into public.care_handoffs(id,child_id,sender_id,recipient_id,instructions,tasks,expected_end)
 values(p_id,p_child_id,auth.uid(),p_recipient_id,p_instructions,
 coalesce((select jsonb_agg(jsonb_build_object('text',value#>>'{}','done',false)) from jsonb_array_elements(p_tasks)),'[]'),p_expected_end);
 insert into public.care_handoff_events(handoff_id,actor_id,action) values(p_id,auth.uid(),'send');
 insert into public.notifications(user_id,category,title,body) values(p_recipient_id,'normal','ส่งต่อการดูแล / Care handoff','เปิดกลุ่มผู้ดูแลเพื่อดูคำขอ / Open Care Circle to review your handoff.');
 return p_id;
end $$;
revoke all on function public.handoff_create(uuid,uuid,uuid,text,jsonb,timestamptz) from public,anon;
grant execute on function public.handoff_create(uuid,uuid,uuid,text,jsonb,timestamptz) to authenticated;

create function public.handoff_act(p_id uuid,p_action text,p_note text default '',p_task_index integer default null,p_done boolean default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
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
  update public.care_handoffs set tasks=jsonb_set(tasks,array[p_task_index::text,'done'],to_jsonb(p_done)) where id=p_id;
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
revoke all on function public.handoff_act(uuid,text,text,integer,boolean) from public,anon;
grant execute on function public.handoff_act(uuid,text,text,integer,boolean) to authenticated;

-- Read at viewing time, never duplicate health records into a handoff payload.
create function public.handoff_context(p_child_id uuid,p_handoff_id uuid default null)
returns jsonb language plpgsql security definer set search_path=public,pg_temp as $$
declare h public.care_handoffs; since_at timestamptz:=now()-interval '2 days'; until_at timestamptz:=now(); recent jsonb:='[]'; upcoming jsonb:='[]';
begin
 if auth.uid() is null or not public.is_child_member(p_child_id) then raise exception 'Handoff: child access required'; end if;
 if p_handoff_id is not null then
  select * into h from public.care_handoffs where id=p_handoff_id and child_id=p_child_id;
  if not found or auth.uid() not in(h.sender_id,h.recipient_id) then raise exception 'Handoff: participant access required'; end if;
  since_at:=coalesce(h.accepted_at,since_at); until_at:=coalesce(h.completed_at,now());
 end if;
 if public.child_can(p_child_id,'view_health') then
  select coalesce(jsonb_agg(to_jsonb(f)),'[]') into recent from
   (select * from public.list_child_activity_feed(p_child_id,500) where occurred_at between since_at and until_at order by occurred_at desc limit 80) f;
 end if;
 if public.child_can(p_child_id,'view_appointments') then
  select coalesce(jsonb_agg(to_jsonb(a)),'[]') into upcoming from
   (select id,title,starts_at from public.child_activities where child_id=p_child_id and starts_at between now() and now()+interval '1 day' order by starts_at limit 20) a;
 end if;
 return jsonb_build_object('health_allowed',public.child_can(p_child_id,'view_health'),'recent',recent,'upcoming',upcoming,'as_of',now());
end $$;
revoke all on function public.handoff_context(uuid,uuid) from public,anon;
grant execute on function public.handoff_context(uuid,uuid) to authenticated;
