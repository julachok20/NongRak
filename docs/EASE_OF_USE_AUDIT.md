# Ease-of-use audit — NongRak, 2026-10-04

**No code was changed to produce this.** Every finding has a file and line behind it. Ordered by
impact for a tired parent divided by work to fix, highest first. Pick from it and I build.

The question asked was: *is this app hard to use?* Honest answer: **yes — and not because any single
screen is bad.** Measured size of what a parent faces:

| | Count | Where |
|---|---|---|
| Routes defined | **190** | `core/navigation/.../Screen.kt` |
| Navigation destinations wired | **195** | `app/.../ui/navigation/AppNavigation.kt` |
| Screen source files | **232** | `app/.../ui/screens/**` |
| Entries in the quick-action catalog | **48** | `data/QuickActionCatalog.kt` |
| Largest screens | 87 KB `ChildHubScreen`, 84 KB `DashboardScreen`, 84 KB `ChildSummaryScreen` | |

A parent holding a baby at 2am has one hand and about ten seconds. The app has 190 places to be.

---

## P0 — FIXED 2026-10-05 — three of the four home tiles did not go where their label said

Found while fixing P1, on the one row of this app a parent could actually learn.
`DashboardScreen.kt:477` wired the บันทึกด่วน tiles like this:

| Tile label | Where it went | Where it should go |
|---|---|---|
| บันทึกการให้นม | `Screen.Nutrition` (hub) | `Screen.MilkRecord` |
| บันทึกการขับถ่าย | `Screen.ChildHealth` (!) | `Screen.WasteRecord` |
| บันทึกการเจริญเติบโต | `Screen.GrowthHistory` (history) | `Screen.AddGrowthRecord` |
| บันทึกการนอน | `Screen.SleepTracker` | correct already |

Three of four. A tile that says **บันทึก** has to open the thing you write in — otherwise the only
learnable row on the home screen is a row of near-misses, and the parent who taps "record a nappy
change" lands on the health screen instead.

Fixed: each tile now opens its own log. All three destinations were already wired and `CapGate`-
wrapped, so a caregiver without the capability still gets the permission screen rather than a crash.

---

## P1 — FIXED 2026-10-05 — the most urgent task had no front door

`LogSicknessSymptoms` is the screen a parent needs when something is actually wrong. Every reference
to it in the codebase:

- `SicknessFollowUpScreen.kt:123` and `:146` — you must already be inside a follow-up
- `ActivityHistoryViewModel.kt:302` — tapping an existing symptom row in history

**Not on the home screen. Not in the quick-action card. Not in the recommended row.** To log that a
child is ill, a parent must already have logged that the child is ill.

**It was worse than unreachable.** `LogSicknessSymptomsScreen.kt:159` is `val rec = record ?: return`
and both save buttons are `enabled = record != null`. So a front door onto that screen would have
shown a parent a form whose save button is permanently grey, with nothing saying why. The screen is
the DAY-2 follow-up for an illness already on file; the first day belongs to `AddHealthRecord`,
which actually creates the record.

Fixed, in two parts:

1. A full-width **ลูกไม่สบาย / บันทึกไข้และอาการ** row under the four tiles on home, going to
   `AddHealthRecord`. A row rather than a fifth tile: it is not a routine log, and a frightened
   parent should not have to pick the right 66dp square out of five.
2. `LogSicknessSymptoms` now says what is wrong when no illness is open — "ยังไม่มีอาการที่กำลัง
   ติดตามอยู่ค่ะ", one line explaining that this page is for the days after, and a button to the
   health record. A greyed button with no reason is a broken screen as far as a parent can tell.

**Was: S (hours). Impact: the highest in this document.**

---

## P2 — The home screen is different every time it opens

`DashboardScreen.kt:283`

```kotlin
val sessionSeed = rememberSaveable { kotlin.random.Random.nextLong() }
```

That seed feeds `QuickActionCatalog.recommendForHome(...)`, so the บริการแนะนำ row is **re-rolled on
every app launch**. Stable within a session — which was the intent, and the comment says so — but a
parent who opens the app six times a day sees six different home screens.

Muscle memory is the single biggest thing that makes an app feel easy, and this prevents it. Nobody
can learn "the thing I need is bottom-left" if it moves.

Fix: seed from `child.id` plus the date, so it is stable for a whole day; or drop the shuffle and
rank by what this parent has actually opened before. The refresh button keeps re-rolling on purpose.

**Effort: S (one line, plus a test). Impact: very high.**

---

## P3 — There are two navigation systems, and they overlap

`CommonComponents.kt:261` — the app footer has a **ชุมชน** tab.
`CommunityNav.kt` — the community then draws **its own** footer: หน้าหลัก | Feed | กลุ่ม | โปรไฟล์.

That community footer's first item navigates to `Screen.Dashboard` — already a tab in the app
footer. So a parent inside the community sees two bars doing overlapping jobs, and "หน้าหลัก" means
different things depending on which bar they look at.

I built the second footer, for a real reason: community pages were unreachable without it. That
treated the symptom.

Two ways out:

- **(a)** Keep the app footer visible inside the community and delete the community footer. One nav
  system, four tabs, everywhere.
- **(b)** Make the community a full-screen place with a single **close** button — like a separate app
  inside the app. Still two systems, but no ambiguity about which is in charge.

(a) is less code and fewer concepts for a parent. (b) is better if the community is meant to feel
like somewhere you *go*.

**Effort: M (about a day either way). Impact: high.**

---

## P4 — Home stacks eight things that all want to be first

From `DashboardScreen.kt`, in render order: header, greeting, monthly mental-health banner, question
notice banner, AI chat hero banner, quick actions, notification alerts, recent services, recommended
services, activity cards, caregiver-duty banner, guest banner.

Each was justified on its own. Together they are a wall, and a parent scrolls past all of it looking
for one number.

**This is where the home-screen design you are sending should land.** I am not inventing a layout —
recorded in `memory/nongrak-decisions.md`. When the mockups arrive I replace, not extend.

**Effort: M–L depending on the design. Impact: high.**

---

## P5 — "Did I already give the medicine?" is not on the home screen

The fixed quick-action card (`DashboardScreen.kt:1045`) has exactly four tiles: บันทึกการให้นม,
บันทึกการขับถ่าย, บันทึกการเจริญเติบโต, บันทึกการนอน.

Feeding, diaper, growth, sleep — all defensible. But `MedicationHistory` and
`FullMedicationHistory` exist as screens, and "did I already give the dose" is what a parent asks at
3am with a crying child and no memory of the last hour. A second dose given because the first was
forgotten is real harm, not inconvenience.

Fix: medicine becomes a tile. Something gives up its slot, or the card goes to five.

**Effort: S. Impact: high — and this one is a safety point, not a comfort one.**

---

## P6 — Onboarding asks for up to eight screens before the app does anything

`Screen.kt:9-17`: `MedicalDisclaimer` → `AddChildChoice` → (`UploadBirthCertificate` or `SyncChild`)
→ `OnboardingStep1` → `OnboardingStep2` → `OnboardingStep3` → `LastSetup` → `MascotSelect`.

A parent downloads this at a moment of worry. Eight screens of setup before a single answer is a lot
of faith to ask for.

Fix: everything after the child's name and birthdate becomes skippable with a visible "ทำภายหลัง",
and `MascotSelect` moves to the first moment the mascot actually appears. `MedicalDisclaimer` stays
exactly where it is — that one is not negotiable.

**Effort: M, and it touches first-run, so it needs care. Impact: high for new parents, zero for
existing ones.**

---

## P7 — The controls are at the top; the hand is at the bottom

`CommonComponents.kt:592-623` — `LullabyTopBar` carries back (top-left), notifications and profile
(top-right).

On a 6.7" phone held one-handed, with a baby on the other arm, the top quarter of the screen does
not exist. A parent has to shuffle their grip — while holding an infant — to go back.

The floating community footer was the right instinct; the rest of the app has not followed it.

Fix: primary actions move to the bottom, the top bar keeps title and back only. Wide change across
many screens, so better after the home screen is settled.

**Effort: L (many files). Impact: high, but spread thin — no single screen looks broken.**

---

## P8 — Four warnings protect less than one

`HandoffScreen.kt` carries four separate disclaimer strings; `MedicalDisclaimerScreen.kt` three;
`CommunityPostCards.kt` two.

I argued for most of these, and each is individually correct. But a parent who meets four warnings
on one screen reads none of them — and then the one that mattered, 1669 or AI ไม่ใช่แพทย์, is
wallpaper too. Warning stacking is a safety regression wearing the costume of caution.

Fix: a budget of **one** prominent warning per screen, the strongest. The rest move into an info
sheet reachable from it. **Do not remove** the 1669 banner on urgent threads or the
AI-is-not-a-doctor notice — those are precisely the ones everything else is drowning out.

**Effort: S per screen. Impact: medium, and it is a safety improvement rather than a cosmetic one.**

---

## P9 — Three ways to write a post, chosen before writing anything

The community offers โพสต์ / ตั้งกระทู้ถามหมอ / โพลสำรวจ as three separate entry points. A parent who
uses Facebook wants to write; which shape it is should be the app's problem, not theirs.

Fix: one **เขียน** button. Ask the kind afterwards, or infer it — a title with a question mark is a
กระทู้, adding options makes it a poll.

**Effort: S. Impact: medium.**

---

## P10 — Numbers no mother asked for

Contributor levels, posts-in-24h, member counts, friend counts. I added most of them, and they are
honest numbers now rather than invented ones — but honest and *wanted* are different things. They
compete for attention with the child's actual data.

Fix: keep them (they are real, and they do encourage answering other parents' questions), demote
them visually, and never place one above a health number.

**Effort: S. Impact: medium-low, but very cheap.**

---

## P11 — The big screens are too big to scan

`ChildHubScreen` 87 KB, `DashboardScreen` 84 KB, `ChildSummaryScreen` 84 KB, `AIChatScreen` 83 KB.
File size is only a proxy, but a fair one here: these pages hold a great deal and show it all at
once.

Fix: sections collapsed by default, and the app remembers which one this parent last opened. The
parent who only ever wants vaccines gets vaccines first.

**Effort: L. Impact: medium — real, but the cheaper items above buy more per hour spent.**

---

## What already works, and should not be touched

- **The fixed quick-action card.** Four tiles, same place every time, one tap to log. The best thing
  on the home screen, and the model the rest of the app should copy.
- **Quick-action search** (`onNavigateToQuickActionSearch`) — the honest escape hatch for 190 routes.
- **Thai first, not translated.** It reads like a person wrote it.
- **It does not lie.** No invented "385 ออนไลน์", no fake doctor review, no made-up member counts.
  Rarer than it should be, and worth more than it looks.
- **1669 routing**, including on the voice path.
- **Facebook-style reactions** — nobody needs teaching.

---

## The honest limit of this document

I audited the code, not parents. I can prove the home screen reshuffles and that the sickness screen
has no entry point. I **cannot** tell you from here which of these actually makes a mother give up.

Cheapest way to find out, and it needs no code at all: sit with three parents, give them five tasks,
time them, and watch where they stop.

1. "Your baby has a fever. Record it."
2. "When is the next vaccine due?"
3. "Did you give the evening dose yet?"
4. "Record that she slept from 1pm to 3pm."
5. "Ask the community whether this rash is normal."

Any task that takes over 30 seconds, or needs two hands, is a finding — and it outranks everything I
ranked above by reasoning alone.
