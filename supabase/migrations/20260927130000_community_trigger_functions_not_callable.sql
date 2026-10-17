-- Migration: 20260927130000_community_trigger_functions_not_callable.sql
--
-- Every function below is a TRIGGER function. A trigger runs as the table owner whatever the
-- caller may execute, so revoking EXECUTE does not disarm any trigger - it only stops a signed-in
-- client from invoking the function directly through PostgREST.
--
-- That matters because these are SECURITY DEFINER: called by hand they would run with the owner's
-- rights. The poll guards were already closed to anon; the recount functions, which predate this
-- work, were open to both anon and authenticated. Flagged by the Supabase security advisor as
-- anon_security_definer_function_executable / authenticated_security_definer_function_executable.

revoke execute on function public.community_poll_vote_guard() from public, anon, authenticated;
revoke execute on function public.community_poll_unvote_guard() from public, anon, authenticated;
revoke execute on function public.community_poll_option_guard() from public, anon, authenticated;
revoke execute on function public.community_post_anonymity_guard() from public, anon, authenticated;

revoke execute on function public.community_post_likes_recount() from public, anon, authenticated;
revoke execute on function public.community_comment_likes_recount() from public, anon, authenticated;
revoke execute on function public.community_comments_recount() from public, anon, authenticated;
