# Caregiver Handoff — implementation and rollout

Entry points: Child Home status card and Care Circle -> Caregiver handoff.
The route carries the child UUID; existing logging/chat shortcuts restore that selection.

Implemented: recipient selection, reviewed send, persisted account/child-scoped local
drafts, instructions, checklist (20 items maximum), optional expected return presets,
accept/decline/cancel, active-care status, per-task completion, return notes and sender
acknowledgment, participant history. Completing a checklist never creates a care record.

The `care_handoffs` table has one pending/active handoff per child. RPCs lock rows,
validate actor and status, and write audit events. Client UUIDs make send retries
idempotent. Repeated transitions are idempotent. A new send can cancel an orphaned
handoff whose participant membership was revoked. Expected return never ends care.

Health context is read on demand behind current `view_health` permission; upcoming
activities additionally require `view_appointments`. No health snapshot is copied into
handoff storage. The context is bounded (latest 80 of the latest 500 feed entries,
two days before acceptance or the accepted-to-completed period); it is not a complete
medical report. History shows current source records, which may have been corrected.
Free-form instructions are intentionally shared by their author with the recipient.

The Home card refreshes every 30 seconds while composed; the handoff screen every 15
seconds with manual refresh available. Source timestamps and missing-data messages are
shown explicitly. Failed reads clear fetched handoff/health data rather than retain it
after an access failure. Drafts are not auto-submitted when connectivity returns.

## Rollout state

Migration: `supabase/migrations/20260907074602_caregiver_handoff.sql`.
Migration dry-run and `supabase/tests/caregiver_handoff.sql` passed against the existing
database inside a transaction that was rolled back. No production schema changes or
synthetic records were retained. Apply the migration before using the new app build.

Notifications are inserted into the existing `normal` inbox category. There is no FCM
configuration in the app. These inbox entries are NOT closed-app push delivery. Existing
LINE pushes are quota-limited and self-directed; they were not repurposed to send to
another account. Push delivery requires its own backend integration before promising it.

## Tests and device checklist

SQL runner: BEGIN, migration contents, test fixture contents, ROLLBACK. The SQL test file
is a fixture fragment: do not execute it outside that enclosing rollback transaction.
It tests full lifecycle, duplicate sends/accepts, sender/stranger rejection, direct-write
denial, health permission filtering, and loss of access to history/audit after revocation.
Unit tests: `CareHandoffTest` covers actor-specific actions and acknowledgment boundaries.
Final `testDebugUnitTest assembleDebug` passed: 124 tests, zero failures/errors. Debug
APK: `app/build/outputs/apk/debug/app-debug.apk`. Existing unrelated whitespace warnings
in EditProfileScreen.kt and HealthBook.kt were preserved; changed integration files pass
`git diff --check`.

Required two-account device walkthrough before rollout:
1. Parent sends to an existing caregiver; recipient sees pending, accepts, sender refreshes.
2. Record feeding/sleep for the correct child; check a task without creating a care record.
3. Finish with notes; parent reviews recorded care and acknowledges return.
4. Repeat decline/cancel; retry after connection loss; verify draft survives app restart.
5. Remove permissions/membership and verify content/actions are inaccessible after refresh.
6. Test text scaling, long Thai notes, keyboard, and accessibility on physical devices.

No Android device was attached during implementation, so visual and two-device QA remain.
Play Store publishing and production migration are not part of the verified local result.

Graphify AST update completed: 5,895 nodes, 12,204 edges, 398 communities. The previous
curated graph was backed up under `graphify-out/2026-09-07/`. Graph HTML was not regenerated
because the graph exceeds the installed visualizer's 5,000-node limit; JSON/report updated.
