-- Run in a transaction AFTER the migration; all synthetic fixtures are rolled back.
-- The runner supplies BEGIN + migration + this file + ROLLBACK for a migration dry run.
insert into auth.users(id,email,raw_user_meta_data) values
 ('a7fa0000-0000-4000-8000-000000000001','handoff-parent@example.invalid','{}'),
 ('a7fa0000-0000-4000-8000-000000000002','handoff-carer@example.invalid','{}'),
 ('a7fa0000-0000-4000-8000-000000000003','handoff-stranger@example.invalid','{}');
insert into public.children(id,user_id,name,birthdate) values
 ('a7fa0000-0000-4000-8000-000000000004','a7fa0000-0000-4000-8000-000000000001','Handoff test fixture','2026-01-01');
insert into public.child_members(child_id,user_id,role,capabilities) values
 ('a7fa0000-0000-4000-8000-000000000004','a7fa0000-0000-4000-8000-000000000002','nanny','{}');
set local role authenticated;
select set_config('request.jwt.claim.sub','a7fa0000-0000-4000-8000-000000000001',true);
select public.handoff_create('a7fa0000-0000-4000-8000-000000000005','a7fa0000-0000-4000-8000-000000000004','a7fa0000-0000-4000-8000-000000000002','Review instructions','["Prepare milk"]',null);
-- Retrying the exact request must not create duplicate rows/notifications.
select public.handoff_create('a7fa0000-0000-4000-8000-000000000005','a7fa0000-0000-4000-8000-000000000004','a7fa0000-0000-4000-8000-000000000002','Review instructions','["Prepare milk"]',null);
do $$ begin
 assert (select count(*)=1 from public.care_handoffs where child_id='a7fa0000-0000-4000-8000-000000000004');
 begin
  perform public.handoff_act('a7fa0000-0000-4000-8000-000000000005','accept');
  raise exception 'TEST FAILED: sender accepted';
 exception when raise_exception then
  if sqlerrm not like 'Handoff: only recipient%' then raise; end if;
 end;
 begin
  perform public.handoff_create('a7fa0000-0000-4000-8000-000000000006','a7fa0000-0000-4000-8000-000000000004','a7fa0000-0000-4000-8000-000000000002','','[]',null);
  raise exception 'TEST FAILED: duplicate active request';
 exception when raise_exception then
  if sqlerrm not like 'Handoff: this child already%' then raise; end if;
 end;
end $$;
select set_config('request.jwt.claim.sub','a7fa0000-0000-4000-8000-000000000003',true);
do $$ begin
 assert not exists(select 1 from public.care_handoffs where id='a7fa0000-0000-4000-8000-000000000005');
 begin
  perform public.handoff_act('a7fa0000-0000-4000-8000-000000000005','accept');
  raise exception 'TEST FAILED: stranger accepted';
 exception when raise_exception then
  if sqlerrm not like 'Handoff: participant access%' then raise; end if;
 end;
end $$;
select set_config('request.jwt.claim.sub','a7fa0000-0000-4000-8000-000000000002',true);
do $$ begin
 assert (public.handoff_context('a7fa0000-0000-4000-8000-000000000004')->>'health_allowed')::boolean=false;
 assert public.handoff_context('a7fa0000-0000-4000-8000-000000000004')->'recent'='[]'::jsonb;
 begin
  update public.care_handoffs set sender_id=auth.uid() where id='a7fa0000-0000-4000-8000-000000000005';
  raise exception 'TEST FAILED: direct write allowed';
 exception when insufficient_privilege then null;
 end;
end $$;
select public.handoff_act('a7fa0000-0000-4000-8000-000000000005','accept');
select public.handoff_act('a7fa0000-0000-4000-8000-000000000005','accept');
select public.handoff_act('a7fa0000-0000-4000-8000-000000000005','task','',0,true);
do $$ begin
 assert (select status='active' and accepted_at is not null and tasks->0->>'done'='true' from public.care_handoffs where id='a7fa0000-0000-4000-8000-000000000005');
end $$;
select public.handoff_act('a7fa0000-0000-4000-8000-000000000005','complete','All done');
select set_config('request.jwt.claim.sub','a7fa0000-0000-4000-8000-000000000001',true);
select public.handoff_act('a7fa0000-0000-4000-8000-000000000005','acknowledge');
do $$ begin
 assert (select status='closed' and completed_at is not null and closed_at is not null from public.care_handoffs where id='a7fa0000-0000-4000-8000-000000000005');
 assert (select count(*)=5 from public.care_handoff_events where handoff_id='a7fa0000-0000-4000-8000-000000000005');
end $$;
reset role;
delete from public.child_members where child_id='a7fa0000-0000-4000-8000-000000000004' and user_id='a7fa0000-0000-4000-8000-000000000002';
set local role authenticated;
select set_config('request.jwt.claim.sub','a7fa0000-0000-4000-8000-000000000002',true);
do $$ begin
 assert not exists(select 1 from public.care_handoffs where id='a7fa0000-0000-4000-8000-000000000005');
 assert not exists(select 1 from public.care_handoff_events where handoff_id='a7fa0000-0000-4000-8000-000000000005');
end $$;
reset role;
