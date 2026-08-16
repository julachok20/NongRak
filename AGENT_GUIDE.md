# NongRak AI — Guide for AI Agents

Standing rules and orientation. Read this before touching anything.
`AGENT_HANDOFF.md` is a snapshot of one session; **this file is permanent**.

---

## 1. Where memory lives, and the order to read it

| # | File | What it holds | When to read |
|---|------|---------------|--------------|
| 1 | `CLAUDE.md` | Auto-loaded rules | automatic |
| 2 | `AGENT_GUIDE.md` | This — conventions, workflow, traps | first session, then when unsure |
| 3 | `MEMORY_GRAPH.md` | Running log, **newest batch at the bottom** | start of every session |
| 4 | `AGENT_HANDOFF.md` | Latest session: what changed, what is open, what was rejected | picking up cold |
| 5 | `graphify-out/` | Code graph — 4,600+ nodes | **before** grepping |
| 6 | `docs/MODULE_INDEX.md` | Module map | navigating unfamiliar areas |
| 7 | `PRIVACY_SECURITY.md`, `SETUP_GUIDE.md` | Privacy posture, environment setup | security or setup work |

**Use graphify before grep.** From `LullabyAI/`:

```bash
graphify query "how does the chat agent write a care record"
graphify path "ChatViewModel" "ChildRepository"
graphify explain "AgentAction"
```

Grep is for a single known file. Graphify is for "where is X" and "how does X work".
After any non-trivial change: `graphify update .` (AST-only, free) and append a batch to
`MEMORY_GRAPH.md`.

---

## 2. Understanding the project in five minutes

**The domain.** One child's record, shared by several adults who are not equals: an owner,
co-owners, ญาติ (relatives), nannies. Almost every design question reduces to *who may see this,
who may write it, and who wrote it*.

**Four pillars:**

1. **The child record** — sleep, feeds, milk, nappies, growth, vaccines, medicine, symptoms.
2. **The care circle** — invites, roles, permissions, activity history, care chat.
3. **The AI companion** — chat, calls, and an agent that reads and *writes* records.
4. **Money and time** — Cost Record with budgets, Activity Schedule with AI outing suggestions.

**Layering** — one direction only:

```
ui/screens/*  →  viewmodel/*  →  data/repository/*  →  Supabase (Postgres, RLS, RPCs)
                                 data/remote/*      →  AI proxy, Google Places
core/common (utils, labels) · core/model (DTOs) · core/navigation (Screen routes)
```

**Where authority lives: Postgres.** Not the ViewModel, not the screen. RLS and
`security definer` RPCs decide what a caller may do. Repository wrappers add no checks of their
own — deliberately, so the UI can never be the thing that authorises.

> Practical consequence: **read the RPC body before assuming a rule.** A "missing feature" is often
> already permitted server-side with no button. That is exactly how the leave-a-child feature was
> found — `remove_child_member` had always allowed self-removal.

---

## 3. Hard rules — do not break these

1. **Money is integer satang** (THB × 100). Never a float. `ExpenseViewModel.parseSatang` is the
   only parser.
2. **Never widen permissions from the client.** If a feature needs new access, change the RPC or
   the policy, and say so out loud.
3. **The AI may choose, never invent facts about the world.** Venues come from Google Places and
   the model may only copy ids it was handed; invented ones are dropped before the UI. The same
   principle governs medical content.
4. **The agent obeys every rule the UI obeys.** `add_schedule` runs the same
   `validateActivityWindow` as the Add Activity screen. An agent path must never become a way
   around a validation.
5. **No silent failure.** This is the project's most expensive recurring bug class — a rejected
   Places request that looked like "no venues nearby", an asset that would have exported with no
   artwork, an action that failed with only "ดำเนินการไม่สำเร็จ". Errors must name **what was
   received**, not only what was wanted.
6. **Timestamps are ISO-8601 with offset**, compared as instants. Wall-clock comparison breaks the
   moment a phone changes timezone.
7. **Do not reintroduce a cross-child switcher in Cost Record.** It was deleted, not fixed — the
   bug class was "the screen silently records against a different child".
8. **Branch order in `dispatchAgentAction` is load-bearing.** The calendar branch must stay above
   the exercise branch, or "add an activity on Saturday" logs a workout. Do not sort it.

---

## 4. Workflow

**Before writing code**

- Search by **concept and by feature name**. A duplicate mascot engine was built here by grepping
  `drawBitmapMesh` and `Mascot(` while the existing system was called `PetMascot` / `CompanionCard`
  — 2,775 lines, same package. Grep the mechanism *and* the domain word.
- For a bug, read the log line and the schema before theorising. `sleep_logs.end_time` being
  nullable was the whole answer to something that looked like an AI problem.

**While writing**

- Match the surrounding style. This codebase comments **why**, not what — a comment restating the
  code is noise here; one explaining a non-obvious constraint is expected.
- Small files, focused functions. Nullable returns over exceptions for control flow.

**Before saying it is done**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest    # 77 tests
.\gradlew.bat assembleDebug
```

- Non-trivial logic leaves one runnable check behind. Pure functions
  (`validateActivityWindow`, `parseSatang`, `recommendedWindow`) are the testable seams — extract
  them rather than testing through a ViewModel.
- **Say what you did not verify.** "Compiles, never rendered" is useful. "Done" for something never
  run is not.

**After**

- Commit in logical chunks, with a body explaining *why* rather than listing files.
- `graphify update .`, then append a batch to `MEMORY_GRAPH.md`.
- Do not push unless asked.

---

## 5. Traps that have already cost hours

| Trap | What happens | Fix |
|------|--------------|-----|
| `JAVA_HOME` unset | Gradle fails instantly | export the Android Studio JBR path |
| PowerShell here-strings for commit messages | message mangled — commit `7527471` carries a stray `@` | use the Bash tool with a heredoc |
| Google Places disabled | every search 403s; the rail silently shows home-only ideas | enable **Places API (New)**, verify with one curl |
| Asset `includeInExport: false` | exports cleanly, loads cleanly, draws nothing | check export flags before shipping assets |
| Backward mesh warping | weights sampled where the limb has not arrived; barely moves | forward warp only |
| Emulator full | `INSTALL_FAILED_INSUFFICIENT_STORAGE` | do not wipe user data to make room |
| `adb logcat \| grep AgentAction` | — | fastest AI-action diagnosis; the line names params received |

---

## 6. How to communicate with this user

- **Disagree when something is wrong**, with the reason. Agreement followed by a bad build is worse
  than an argument. This is an explicit standing request.
- One task at a time. Ask when genuinely ambiguous; do not guess at scope.
- Plan screen connections before building a new design.
- Report failures plainly, including your own. Never claim verification you did not perform.
- Thai is the product language. UI strings are bilingual (`isThai`), and error messages must read as
  sentences a tired parent can act on — never raw codes.

---

## 7. Judgement calls worth inheriting

- **Delete the feature, not the symptom.** The cross-child switcher and the Rive dependency were
  both removed rather than patched, and both removals were the correct fix.
- **Derive, do not store.** Mascot level is computed from xp, so there is never a second answer to
  "what level am I".
- **Make abuse impossible in the database, not the UI.** The bond's 20-minute cooldown lives in the
  RPC, so no client can farm it.
- **A toy must not break a tool.** Bond failures are swallowed; the meal is already saved. Anything
  playful must never be able to fail a care record.
