-- The caregiver's "บันทึกแล้ววันนี้" list needs the photo, voice note and author of each row.
-- RETURNS TABLE cannot grow in place, so drop and recreate; the app decodes with
-- ignoreUnknownKeys, so installed versions simply ignore the new columns. Applied 2026-09-21.
drop function if exists public.list_care_logs(uuid, timestamptz, timestamptz);
create function public.list_care_logs(p_child_id uuid, p_from timestamptz, p_to timestamptz)
returns table(id uuid, kind text, note text, mood text, abnormal text, logged_at timestamptz,
              author_name text, created_by uuid, photo_path text, voice_path text, voice_seconds integer)
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if not public.is_child_member(p_child_id) then raise exception 'not authorized'; end if;
  return query
  select cl.id, cl.kind, cl.note, cl.mood, cl.abnormal, cl.logged_at,
         coalesce(cm.display_name, pr.username, pr.full_name, 'ผู้ดูแล'),
         cl.created_by, cl.photo_path, cl.voice_path, cl.voice_seconds
    from public.care_logs cl
    left join public.profiles pr on pr.id = cl.created_by
    left join public.child_members cm on cm.user_id = cl.created_by and cm.child_id = p_child_id
   where cl.child_id = p_child_id and cl.logged_at >= p_from and cl.logged_at < p_to
   order by cl.logged_at desc;
end; $function$;
