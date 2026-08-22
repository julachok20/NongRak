-- Required by scheduled jobs: pg_cron runs them, pg_net makes the outbound HTTP call.
-- Supabase installs pg_net into its own "extensions" schema and pg_cron into pg_catalog.
--
-- Needed by the daily LINE vaccine reminder. Without pg_net the dashboard Cron UI refuses to
-- create an Edge Function job at all, reporting only that the extension is missing.
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron;
