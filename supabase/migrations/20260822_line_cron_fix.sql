-- Rewires the LINE cron jobs.
--
-- All three had been created through the dashboard Cron UI with its default template left in
-- place: they POSTed to /functions/v1/ai-generate with `headers := '{}'` and a 1000 ms timeout.
-- Wrong function, no Authorization header, and a timeout shorter than a cold start. So the
-- vaccine reminders had never once fired, despite the job showing as active since 2026-08-21.
--
-- The key is read from Vault at run time rather than pasted into the command, so `cron.job` —
-- which is readable by anyone who can query it — never contains a service-role key.
-- Requires, once, run by a human with the key in front of them:
--   select vault.create_secret('<SERVICE_ROLE_KEY>', 'service_role_key');

select cron.unschedule('line-vaccine-reminder-daily');
select cron.unschedule('line-tip-broadcast');

select cron.schedule('line-vaccine-reminder-daily', '0 2 * * *', $job$
select net.http_post(
  url := 'https://rdylsezwjdujvllyjhqb.supabase.co/functions/v1/line-vaccine-reminder',
  headers := jsonb_build_object(
    'Content-Type', 'application/json',
    'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
  ),
  body := '{}'::jsonb,
  timeout_milliseconds := 30000
);
$job$);

select cron.schedule('line-tip-broadcast', '0 3 * * 1,4', $job$
select net.http_post(
  url := 'https://rdylsezwjdujvllyjhqb.supabase.co/functions/v1/line-tip-broadcast',
  headers := jsonb_build_object(
    'Content-Type', 'application/json',
    'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
  ),
  body := '{}'::jsonb,
  timeout_milliseconds := 30000
);
$job$);

-- Every 30 minutes, because a dose reminder that arrives an hour late is not a reminder.
select cron.schedule('line-care-reminder-30min', '*/30 * * * *', $job$
select net.http_post(
  url := 'https://rdylsezwjdujvllyjhqb.supabase.co/functions/v1/line-care-reminder',
  headers := jsonb_build_object(
    'Content-Type', 'application/json',
    'Authorization', 'Bearer ' || (select decrypted_secret from vault.decrypted_secrets where name = 'service_role_key')
  ),
  body := '{}'::jsonb,
  timeout_milliseconds := 30000
);
$job$);
