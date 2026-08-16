# NongRak AI — Agent Handoff (2026-08-16)

Self-contained brief for an AI agent picking this up cold. Read this, then
`graphify query "<question>"` from `LullabyAI/` for anything about existing code.

---

## 1. What this is

Thai childcare app. Parents and the people who help them — co-owner, ญาติ (relative),
nanny — share one child's record: feeds, sleep, nappies, growth, vaccines, medicine,
costs, and a calendar. An AI companion in chat can read and write those records.

| | |
|---|---|
| Package | `com.publish.NongRak` |
| Stack | Kotlin, Jetpack Compose, Material3 |
| Backend | Supabase (Postgres + RLS + edge functions + storage + realtime) |
| Project ref | `rdylsezwjdujvllyjhqb` |
| Branch | `Track-YSC` (duplicate `Track-YSC.02` created 2026-08-16) |
| Remote | `Jul-SBW/NongRak-LullabyAI` (private), default branch `track3` |
| Release line | `1.0.3-dev36`, versionCode 16, soft launch; a Play review is pending |

**Read `MEMORY_GRAPH.md` first** — it is the project's running log, newest batch at the
bottom. This file covers only 2026-08-16.

---

## 2. Non-negotiable conventions

- **Money is integer satang** (THB × 100). Never a float, anywhere. `ExpenseViewModel.parseSatang`
  is the single parser — "120", "120.50" and "฿120" must all land on the same integer.
- **Authorisation lives in Postgres**, not the client. RLS plus `security definer` RPCs decide what
  a caller may do. Repository wrappers deliberately add no checks of their own, so the UI can never
  be the thing that authorises. Read the function body before assuming a rule.
- **Timestamps are ISO-8601 with offset** end to end; `TimeUtils.toUtcIso` on the way in.
- **The AI may choose, never invent facts about the world.** Venues come from Google Places and the
  model may only copy ids it was given; anything invented is dropped before it reaches the UI.
- Run `graphify update .` after non-trivial changes. Update `MEMORY_GRAPH.md` too.

---

## 3. What changed on 2026-08-16

Seven commits on `Track-YSC`, all built, unit tests green (77):

```
0563b20  let a carer leave a child they are connected to
4ff5f8b  companion consistent across chat, bar and call
6d644bc  mascot switching in chat, per-companion voice, ความผูกพัน bond
282c4f3  agent reaches cost record and calendar; fix sleep logging
f8dc02f  skeletal animation for all four companions
68e18ce  activity schedule with AI outing suggestions
162f0c1  per-child cost record, readable budget errors, starter roles
```

### 3.1 Cost Record
Shows only the open child plus the family total. The cross-child switcher was **deleted**, not
fixed: `remember(childId, state.childId)` re-ran its initialiser when `load()` filled in the
account-wide child and silently overwrote the user's tap, recording against the wrong child.
Removing the switcher removed the class of bug. **Do not reintroduce a cross-child selector here.**

### 3.2 Activity Schedule (`ui/screens/schedule/`)
Day/week/month calendar from the child's ปฏิทิน tile. Start and end time on every entry.
Forward-only: `validateActivityWindow()` compares **instants, not wall clocks**, so a phone that
crosses a timezone cannot accept a moment that has passed. The agent path runs the same check.

### 3.3 Places + AI outing suggestions
`data/remote/PlacesClient.kt`, `ActivityRecommender.kt`.
- Venues searched **per category group** (nature 40 km, malls/food 15 km, learning 30 km) in
  parallel. One request for all types returns whatever Places ranks highest — cafés — and the beach
  never reaches the shortlist, so "vary them" was impossible to obey.
- Opening hours are fetched (`places.regularOpeningHours`) so the suggested hour lands while the
  venue is open. That field is Enterprise-tier billing.
- Batches cached 3 days, shared between carers, freshness computed by Postgres.
- **Google Places API (New) must be enabled** on the Cloud project or every search 403s and the rail
  silently shows home-only ideas. The key lives in `local.properties` as `MAPS_API_KEY`.
  **It was pasted into a chat transcript and should be rotated.**

### 3.4 Mascot skeletal animation (`ui/mascot/`)
Four flat PNGs (`res/drawable-nodpi/mascot_*.png`), animated by linear blend skinning over a vertex
grid fed to `Canvas.drawBitmapMesh` — the technique Rive uses, without Rive (its `.riv` export is
paid, and nothing in the design used a Rive-only feature).
- `MascotRig.kt` — bones (pivot, tip, falloff), per-mascot rigs, weights precomputed once.
- `MascotMood.kt` — 11 moods as maths, not keyframe tables.
- `Mascot.kt` — renderer, plus the painted mouth/blink overlay.

Three things that are load-bearing and easy to break:
- **Forward warping.** Vertices are pushed to new positions and triangles rasterised between them.
  Backward warping (asking each destination pixel where it came from) evaluates bone weights at the
  destination, where the limb has not arrived — the mascot barely moves and nobody can say why.
- **Squared falloff.** Linear falloff lets a bone tug faintly at the whole body, which reads as the
  picture wobbling rather than a limb moving.
- **Arms swing down, not up** (−30°…+8°). Every mascot is drawn with a limb already raised.

The face overlay stays aligned because all bones are on limbs, so weights across the face are ~0.

### 3.5 Chat agent
Already existed (`ChatViewModel.dispatchAgentAction` + `util/AgentActionParser`). Extended with
`log_expense` and `add_schedule`, and navigation widened to the new screens.

Two real bugs fixed:
- `"activity" in t -> addExercise` meant *"add an activity on Saturday"* logged a **workout**. The
  calendar branch now sits above it. **That ordering is load-bearing** — sorting these branches
  alphabetically reintroduces the bug.
- Sleep could not be logged at all. The model was never told the current time, so it emitted
  `startTime=[current_time - 3 minutes]` verbatim; and `endTime` was mandatory, which made "the baby
  just went down" unrecordable although `sleep_logs.end_time` is nullable. The prompt now carries
  the user's clock, missing start means now, missing end means still sleeping, and anything that is
  not `HH:mm` is treated as absent rather than written into a child's record.

### 3.6 Companion system
- Chosen and switched **from chat** (tap the name in the header); removed from onboarding.
- Per-mascot persona injected into the system prompt. It steers **voice only** — what a companion
  may *do* stays fixed by the action list and RLS.
- **ความผูกพัน bond**: table `mascot_bonds`, PK `(user_id, mascot)` — each carer raises their own
  companion, and switching switches relationship. Level is derived by `mascot_level(xp)`, never
  stored. `feed_mascot(p_mascot, p_xp)` has a **20-minute cooldown**: without it, logging a week of
  backdated meals pours a week of affection in at once. Meals announce a banner; talking adds 4 xp
  quietly. Bond failures are swallowed — the meal is already saved and a toy must not break a tool.

### 3.7 Access
A non-owner can now **leave a child** (`ออกจากการดูแลเด็กคนนี้`). `remove_child_member` always
permitted self-removal; only the button was missing. Owners are excluded — a child with no owner has
nobody who could grant access again.

---

## 4. Migrations applied 2026-08-16

`activity_schedule`, `activity_recommendation_cache`, `mascot_bonds`, plus the cost-record and role
set from earlier the same day (`expense_name_used_lock`, `total_goal_separate_from_child_goals`,
`child_goal_capped_by_total_goal`, `budget_errors_machine_readable`,
`total_budget_force_clears_child_goals`, `any_member_can_record_expenses`,
`starter_roles_co_owner_relative_nanny`, `roles_for_access_request`).

---

## 5. Open work, roughly by value

1. **Gemini-style calls.** Wanted: Gemini Live API (native audio dialog) — streams audio both ways,
   interruptible, no record→transcribe→LLM→synthesize gap. Biggest realism win, and it also answers
   "voice that sounds human". Billed per second of audio.
2. **Per-mascot TTS voice.** `AiProxyClient.synthesize(text, voice, style)` already accepts a voice
   and **nobody passes one** — all four speak as `Leda`. The backend passes it straight to
   `prebuiltVoiceConfig.voiceName`, no allowlist. Cheapest large improvement available.
3. **In-call action banner** with confirm plus an auto-mode toggle.
4. **Bond XP from the nutrition screen** — currently only the chat agent path grants it.
5. **Owner exit.** No ownership transfer exists; an owner can only delete the child.
6. **Cancel a pending access request** — not verified whether a requester can withdraw one.
7. **Rotate `MAPS_API_KEY`**; add the Play **Data Safety** declaration for `ACCESS_COARSE_LOCATION`.
8. **Nothing is pushed.** All seven commits are local only.

### Rejected, with reasons — do not re-propose without new information
- **Rive** for mascot animation: `.riv` export requires a paid plan, and nothing in the design used
  a Rive-only feature. `drawBitmapMesh` gives the same result for free.
- **Fish Speech + Picovoice + Common Voice** for Thai voice: the 9 GB download in
  `D:\Claude-Access\New folder` is Mozilla **Common Voice** — multi-speaker ASR *training* data, not
  a voice. Picovoice has no Thai. Fish Speech needs a GPU server and weeks of fine-tuning. Thai
  speech input already works via Android `SpeechRecognizer`.
- **Accessibility Service** to drive the app's own UI: Play restricts those APIs to genuine
  accessibility use and rejects automation. Calling the app's own repositories is safer and exact.

---

## 6. Environment gotchas

- `JAVA_HOME` is not set in agent shells. Use
  `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"` before `.\gradlew.bat`.
- The **Bash** tool takes heredocs; **PowerShell** here-strings (`@'…'@`) mangle commit messages
  there. One earlier commit (`7527471`) carries a stray `@` from that mistake.
- A **GateGuard** hook demands stated facts before the first Bash call and the first edit of each
  file: callers, existing equivalents, schemas, and the user's verbatim instruction.
- Unit tests: `.\gradlew.bat testDebugUnitTest` — 77 tests, all green.
- An emulator (`emulator-5554`) is available. `adb logcat | grep AgentAction` is the fastest way to
  diagnose a failed AI action: the log line names which params were present, not just what was
  wanted.

---

## 7. Working style the user expects

- Real disagreement when an idea is wrong, with the reason — not agreement followed by a bad build.
- One task at a time; ask when genuinely ambiguous rather than guessing.
- Plan screen connections before building a new design.
- **Read existing code before adding anything.** A duplicate mascot engine was built on this date by
  grepping for `drawBitmapMesh` and `Mascot(` and missing the existing `PetMascot` / `CompanionCard`
  system — 2,775 lines of it, in the same package. That merge is still only partly done.
