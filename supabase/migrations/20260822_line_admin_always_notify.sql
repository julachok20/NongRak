-- Per-admin override for the "do not notify the sender" rule.
--
-- supportCapture skips pushing to an admin who wrote the message, which is right by default:
-- nobody wants a notification about their own text. But the owner of the OA wants every enquiry
-- to land in their chat unconditionally, including their own test messages — otherwise the only
-- way to prove the alert path works is to borrow someone else's phone.
--
-- A column rather than deleting the skip, so adding a staff admin later does not start echoing
-- their own messages back at them.
alter table public.line_admins
  add column if not exists always_notify boolean not null default false;
