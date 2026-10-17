# NongRak AI — น้องรัก

A Thai childcare app for parents of young children. Records a child's health, feeding, sleep and
vaccinations, answers questions about them in Thai, and gives the people who share the care — a
co-parent, a grandparent, a nanny — exactly as much access as the parent chooses.

Android, Kotlin and Jetpack Compose, on Supabase.

---

## What it does

**Keep the record.** Feeding, nappies, sleep, growth, medicines and doses, vaccinations against the
Thai schedule, illness episodes tracked day by day, doctor visits, and the documents that go with
them.

**Answer questions about *this* child.** น้องรัก reads the family's own records, so "can I give the
next dose yet" is answered from the medication log rather than in general terms. It also takes
dictation — *"ลูกกินนม 100 ml"* becomes a feeding log, but only after the parent confirms it.

**Share the care, precisely.** Per-capability access rather than all-or-nothing: who may view health
records, who may log symptoms, who may book appointments. A nanny can record the day without seeing
the medical history, and a handover brief tells the next shift what happened.

**A community for parents**, with posts, questions for volunteer paediatricians, polls and groups.
Anonymous threads are supported, with the author stripped server-side so the link never reaches a
client at all.

**LINE, because this is Thailand.** The app pairs with a LINE account for medicine and vaccine
reminders, and for asking น้องรัก a question without opening the app.

---

## Built on

| | |
|---|---|
| App | Kotlin, Jetpack Compose, Material 3 — `com.publish.NongRak`, minSdk 26, targetSdk 36 |
| Backend | Supabase — Postgres with row-level security, 69 migrations, 9 Deno edge functions |
| AI | Gemini → OpenRouter → Claude, with Gemma for extraction; `pg_cron` and `pg_net` for scheduled work |
| Messaging | LINE Messaging API — webhook, rich menu, scheduled push |

```
app/              the Android application
core/common/      shared utilities, time handling
core/model/       data models, shared across layers
core/navigation/  every route in one place
supabase/         migrations and edge functions
docs/             architecture notes and plans
```

---

## Two principles the code actually enforces

**Authorisation lives in Postgres.** Row-level security and `SECURITY DEFINER` functions decide who
may read what; the client is never the only thing standing between a stranger and a child's medical
record. Anonymous community posts have their author stripped by a database view before the row
reaches any app.

**The AI states facts, never conclusions.** Every figure it quotes comes from a row the parent
entered. It may say that a fever has been recorded three days running; it may not say what that
means. Anything resembling an emergency routes to **1669**, the Thai emergency number, ahead of any
model answer — and every AI surface says in Thai that it is not a doctor.

---

## Status

Working, and **not yet released**. The soft-launch build was v1.0.3; `versionName` here is V.1.1.

Recently rebuilt: the community (feed, profiles, friends, groups), the home screen, and the
assistant's long-term memory. 407 unit tests pass.

Known gaps are kept honestly in [`docs/EASE_OF_USE_AUDIT.md`](docs/EASE_OF_USE_AUDIT.md) — eleven
ranked findings, each with a file and a line — alongside the assistant roadmap in
[`docs/AI_ASSISTANT_PLAN.md`](docs/AI_ASSISTANT_PLAN.md).

---

## Running it

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Supabase and AI provider keys are supplied as environment variables and Supabase secrets. Nothing
secret belongs in this repository, and a Google API key must never become a `BuildConfig` field — it
ships inside the APK if it does.

---

Private repository. Not open source, and not accepting contributions.
