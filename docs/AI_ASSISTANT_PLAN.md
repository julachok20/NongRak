# Full Personal AI Assistant — plan, NOT built

**Status: nothing in this document is implemented.** Written 2026-10-04 so the thinking is not lost
while the focus moves to making the app easy to use. `docs/AI_SYSTEM.md` describes what is actually
running today; this file describes what was asked for next.

What was asked, verbatim:

> We will upgrade AI to FULL Personal AI Assistance
> That has own thing to do about child family

So: an assistant with **its own agenda** about the child and the family. Not a chat box that waits
to be asked.

---

## 0. The target, in the user own words (2026-10-06)

> I want to feel like REAL Personal AI Assistance like Grok Bot Dot but for parent

So the reference points are personal-companion AI - Dot, Grok - not a help desk. What actually makes
those feel real, and what each one costs here:

| What makes it feel real | What it needs | Have it? |
|---|---|---|
| It remembers without being asked to | `assistant_memory`, facts carried across sessions | no - section 3.1 |
| One continuous relationship, no cold opens | last-conversation state, no "how can I help you today" | no |
| A consistent voice with a personality | น้องรัก already exists as a character with a look and a tone | **yes, and most apps do not have this** |
| It knows the context already | ChatRepository already sees the whole family | **yes** |
| It starts a conversation sometimes | proactive layer - the FCM blocker in section 2 | no |

**The difference from Dot and Grok, and it is not a small one.** Those assistants can be wrong about
a weekend plan. This one is talking about a baby. So the personality lives in the TONE and never in
the confidence: warm, familiar, remembers the family - and still says "ไปหาหมอนะคะ" rather than a
guess dressed up in friendliness. A companion voice makes a wrong clinical claim MORE believable,
not less, which is exactly why section 3.4 gets stricter as the personality gets stronger, not
looser.

Practical consequence for the build order: personality and memory (3.1) can ship with no push
infrastructure at all and would already change how the assistant feels. Proactivity is the
expensive half.

---

## 1. What already exists to build on

| Piece | Where | What it gives the assistant |
|---|---|---|
| AI chain | `ClaudeClient` -> `AiProxyClient` -> `ai-generate` edge function -> Gemma 4 -> Gemini -> OpenRouter -> Claude | Text generation with fallbacks, already working |
| Family context | `ChatRepository` | Chat turns already see the whole family, not one child |
| Emergency routing | `NongRakMedicalRouter` | Red-flag detection, infant fever rules, 1669 |
| Prose cleanup | `ClaudeClient.unwrapProse` | Model JSON never reaches a parent as `{...}` |
| In-app notices | `SystemNotifier` | Shows a notification **only while the process is alive** |
| Records | child / health / medication / sleep / nutrition / routine / diary repositories | Everything the assistant would reason about |

The data is there. The reasoning is there. **What is missing is the ability to act when nobody is
looking at the app.**

---

## 2. CORRECTION (2026-10-06) - there is no FCM blocker

**Everything in the section below was wrong, and it was wrong in the direction that matters.** It
said a proactive assistant cannot reach a parent while the app is closed. It can, today, and the
infrastructure is already running in production:

| Piece | Where | State |
|---|---|---|
| Scheduler | `pg_cron` + `pg_net` | enabled, migration `20260821_enable_cron_and_net.sql` |
| Scheduled push, every 30 min | `line-care-reminder` | deployed, quota-aware, dedup log, `dryRun` mode |
| Date-based push | `line-vaccine-reminder` | deployed, fires 3 / 2 / 1 days before |
| Send a message | `line-push`, `line-notify-parents`, `LineRepository.push(text)` | deployed |
| Opt-in toggle | `LineRepository.setNotifyEnabled` | exists |

**LINE is the push channel, and for this market it beats FCM**: it is where Thai parents already
are, it survives an app uninstall, and it never meets the Android 13 notification-permission prompt.

So the staged plan below ("build the brain against server-only, add FCM later") is solved from the
other end: delivery is done, and what is missing is the part that decides WHAT to say.

### What the user actually asked for (2026-10-06)

> like Grok Bot have daily where user can sent to do like summary email like that

Grok Tasks: the parent writes a recurring instruction, it runs on a schedule, and the result is
delivered. Concretely here:

1. `assistant_tasks` - family, child, instruction (preset id), schedule, channel, enabled, last_run
2. one cron edge function, copying `line-care-reminder` exactly: quota check, dedup, `dryRun`
3. a task screen with **presets, not free text** (see the risk below)

Presets worth shipping: สรุปการนอนเมื่อคืน (daily 07:00), อะไรยังไม่ได้ทำวันนี้ (daily 19:00),
สรุปสัปดาห์ (weekly), and สรุปอาการก่อนไปหาหมอ (1 day before an appointment) - the last one is the
one nobody asks for and everybody wants.

### The risk Tasks adds that chat does not have

Chat is PULL. A scheduled task is PUSH, and an unprompted message carries more authority than an
answer to a question somebody asked. A task worded "ทุกเช้าบอกหน่อยว่าลูกสบายดีไหม" is a parent
asking for a **daily diagnosis, delivered unprompted**. It must be reframed into facts or refused.
This is the main argument for presets over free-text instructions, alongside the push quota.

Composition rule is unchanged and non-negotiable: **counts from real rows, AI only phrases them**,
and no data means the message says so rather than inventing a pleasant night.

---

## 2b. The original (incorrect) blocker, kept for the record

**This app has no FCM.** `SystemNotifier` fires inside a live process, so an assistant that wants to
say "ถึงเวลายานอนของน้องแล้วค่ะ" at 20:00 while the app is closed cannot do it today. This was already
learned the hard way with friend requests, which is why those notify from a **database trigger**
rather than from app code.

Two ways, and they are a real fork:

**A. Server-side only (no app push).** Supabase scheduled functions (`pg_cron`) plus DB triggers
write rows into `notifications`. The parent sees them next time they open the app, and the in-app
banner shows them if the app happens to be open.

- Works today. No new dependency, no Play review risk.
- Cannot interrupt. A medicine reminder that arrives when the parent next opens the app is not a
  reminder, it is a record of a missed dose.
- Everything the assistant knows must be derivable **on the server**. Anything living only on the
  device is invisible to it.

**B. Add FCM.** Real push, so the assistant can reach the parent at the moment that matters.

- The only version that delivers what was actually asked for.
- Bigger job: Firebase project, token registration and refresh, a send path in the edge function,
  per-device token hygiene, and notification-permission UX on Android 13+.
- Play review: push about a child, plus the Families policy, means the consent screen has to be
  right the first time.

**Recommendation: B, but staged.** Build the assistant brain against A first - the scheduler, the
rules, the written output - because that part is testable without any push at all. Add FCM as the
delivery layer once the assistant is actually saying useful things. Delivering nonsense promptly is
worse than delivering it late.

---

## 3. What "its own thing to do" has to mean concretely

Four pieces, in dependency order.

### 3.1 A memory of the family, separate from chat

Chat context dies with the conversation. An assistant needs facts that persist:

- what this family normal looks like (usual nap window, usual feed volume, which parent does nights)
- what has already been said, so it does not repeat itself every day
- what the parent told it to stop doing - the single most important field, and the one most likely
  to be left out

A table, not a prompt:

```
assistant_memory(family_id, key, value, source, confidence, updated_at)
```

RLS-scoped to the family, with `source` recording whether a fact came from the parent saying it,
from a record, or from the model inferring it. **An inferred fact must never be presented back as
something the parent told it.**

### 3.2 A scheduler

`pg_cron` over the rows that already exist. The assistant wakes, looks, and usually does nothing -
that is the correct behaviour most of the time.

Candidate triggers, all computable from data that already exists:

- a medication dose window passing with no record of it being given
- a vaccine due date approaching
- a fever recorded on 3 consecutive days
- a feed or sleep gap far outside this child own established pattern
- a milestone window closing with nothing recorded

### 3.3 A quiet filter - the part that decides what NOT to say

The hard problem is not generating messages. It is sending few enough that they still get read.
Rules worth writing down now:

- **One unprompted message per day, maximum**, unless it is a medication dose or an emergency.
- **Nothing between 22:00 and 06:00** except a medication dose the parent explicitly scheduled. A
  parent of a newborn sleeps whenever they can, and waking them to say something non-urgent is an
  unforgivable thing for software to do.
- **No repeats.** If the same observation was sent and not acted on, the second one is noise.
- **Silence is a valid output**, and must be the default rather than the exception.

### 3.4 Boundaries - non-negotiable

- **Observations and reminders, never conclusions.** "ลูกมีไข้ 3 วันติดกันแล้วค่ะ" is a fact the app
  owns and may state. "ลูกน่าจะเป็น..." is a diagnosis. Reactive answers already carry
  "AI ไม่ใช่แพทย์"; an unprompted message about a child health is a **bigger** claim than an answer to
  a question, because nobody asked for it - so the line has to be harder, not softer.
- **Every proactive message routes through `NongRakMedicalRouter` first.** If it trips a red flag,
  the message is the 1669 banner and nothing else - not an assistant opinion with a banner under it.
- **Explicit opt-in**, per family, with a visible off switch that is not buried in settings. PDPA,
  and Play Families policy will ask.
- **No message is based on data the parent did not knowingly enter.** No inferring from phone
  sensors, location or usage patterns.
- **The parent can tell it to stop a topic**, and that has to be a stored fact (3.1), not a sentence
  in a prompt that the next model call forgets.

---

## 4. Build order, when it starts

1. `assistant_memory` table + RLS + the stop-list field.
2. The scheduler with **one** trigger only - medication dose missed - writing to `notifications`,
   read on next app open. Nothing else. Prove the loop end to end.
3. The quiet filter, with tests asserting the night window and the one-per-day cap. These are the
   tests that matter; the message wording is the easy part.
4. The router gate on every proactive message, with a test that an emergency phrase can only ever
   produce the 1669 message.
5. The opt-in screen and the off switch.
6. FCM, once 1-5 are real.
7. More triggers, one at a time, each earning its place.

---

## 5. Open decisions - not yet answered, do not guess

- Server-only (A) or FCM (B), and if B, when.
- Does the assistant speak as น้องรัก (the existing mascot) or as something separate? A mascot that
  suddenly has opinions about a child health is a different character from a mascot that plays.
- Per-family or per-parent? Two parents on one child will have different tolerances for being
  messaged, and the handoff feature means both are real users.
- Does it ever write records on its own, or only ever suggest? Writing a record unasked means the
  parent own log is no longer only their own words.

---

## 6. Why this is parked right now

The focus moved to making the app easy for every parent, which is the right call and partly
overlaps: an assistant that knows the family can replace navigation entirely - a parent asks
instead of hunting through thirty screens. But an assistant bolted onto an app that is already hard
to use gives a parent two confusing things instead of one. Ease of use first.

---

# The buddy upgrade (planned 2026-10-11)

> I would like to plan on upgrade AI System now longer memory and connected ecosystem like real buddy

Two asks, and they are different problems. Longer memory is a data architecture. A connected
ecosystem is an identity question. Taken in the wrong order you get an assistant that remembers a
great deal and still feels like three separate bots.

## What already exists (checked, not assumed)

| Piece | State |
|---|---|
| `vector` 0.8.0, `pg_cron` 1.6.4, `pg_net` 0.20.3, `pg_trgm` 1.6 | **all enabled** on the project |
| Agentic actions: `AgentAction(type, confirmText, params)` | built; used by chat and the call screen |
| `AiCommand` with a server-signed, single-use, short-lived ticket | built - the app hands it back on Confirm and cannot alter it |
| Family context in every prompt (`ChatRepository`) | built |
| Scheduled LINE delivery (`line-care-reminder`, quota + dedup) | built, running |
| Grounded-prompt pattern (`generateQuickTip`, Smart Trends) | built |

**The expensive parts are done.** Nothing below needs new infrastructure, and pgvector being already
enabled is the single biggest saving: semantic memory is otherwise a migration, an extension request
and a redeploy before a line of it works.

## 1. Memory, in three tiers

Treating "longer memory" as one thing is how these projects fail. It is three, with different costs
and different failure modes.

**Tier 1 - working memory.** The current conversation. Exists today. Nothing to do.

**Tier 2 - facts.** `assistant_memory(family_id, child_id, key, value, source, confidence,
updated_at, muted)`. Small, always loaded into the prompt, cheap to read. This is what makes it feel
like it knows the family: the usual nap window, which parent does nights, that มีนา refuses the
afternoon bottle.

`source` is not optional. Every row records whether a fact came from the parent SAYING it, from a
logged row, or from the model INFERRING it. An inferred fact must never be replayed as something she
told it - that is the fastest way to destroy trust in a memory system.

`muted` is the stop list: the topic she asked it to drop. It has to be a stored fact, not a sentence
in a prompt that the next model call forgets.

**Tier 3 - episodic.** Past conversations, summarised, embedded, stored in pgvector, retrieved by
similarity when a new question arrives. This is the "longer" in longer memory.

Embed **summaries, not raw turns**: cheaper, and a summary is what you actually want back anyway.
BGE-M3 is already the embedder named in `LOCAL_AI_STACK.md` - multilingual, dense and sparse in one
model, and the sparse half is what catches exact vaccine and medicine names where pure semantic
search drifts.

**The failure mode to design against:** a memory that remembers the wrong thing is worse than no
memory, because the parent cannot tell that it is wrong. So every fact carries provenance, the parent
can see and delete what it remembers, and delete means delete rather than a hidden flag.

## 2. A connected ecosystem is an identity problem

The assistant appears today in chat and on the call screen. The plan adds the home card, LINE digests
and the tips page. That is four surfaces - and four surfaces with four contexts is not a buddy, it is
four bots wearing the same mascot.

**One rule governs the whole thing: one assistant, one memory, every surface.** If the 07:00 LINE
digest does not know what she told the chat at 02:00, the illusion breaks and does not come back.

Concretely: memory is keyed to the FAMILY, never to a screen or a session. Every surface reads the
same `assistant_memory` and writes back to it. A LINE reply is a conversation turn like any other.

## 3. What a buddy does that a chat box does not

1. **Opens where you left off** instead of asking how it can help.
2. **Remembers across weeks**, not within a session.
3. **Notices** - the proactive tasks, already deliverable over LINE.
4. **Acts** - already possible through `AgentAction` and the signed ticket.
5. **Knows when to say nothing.** The hardest one, and the one most assistants get wrong.

## 4. Build order

1. `assistant_memory` plus the extractor that writes facts to it after a conversation. Nothing
   visible to the user yet.
2. Continuity: chat and the home card open from the last exchange. **The first moment it feels
   different.**
3. The memory screen - what it remembers about your family, with delete. Ship this WITH tier 2,
   never after it.
4. Tier 3: summarise, embed, retrieve. Only once tiers 1 and 2 are proven.
5. Tasks over LINE, reusing the `line-care-reminder` shape.
6. Voice and LINE read the same memory, closing the loop.

Steps 1-3 are the buddy feeling. Steps 4-6 are its range.

## 5. The boundary, restated because it gets harder here

Everything above increases how much the assistant appears to know, which increases how much a parent
will believe it. The medical line does not move: observations and reminders, never conclusions, and
every proactive message still routes through `NongRakMedicalRouter` first.

A buddy that remembers your child and then gets a symptom wrong is more dangerous than a chat box
that does, precisely because it earned the trust first.

---

# LINE, chat, and what the assistant actually does (2026-10-11)

> add like more activity for AI to do ... should be like 100x better in Line when talking
> or talking in chat UI/UX should be better easy to use look professional
> with concept real AI helping you

## What LINE already is

`line-webhook/index.ts` is 2,096 lines and already far more than a reminder bot: pairing, a rich
menu, slash commands, an inbox, recording from chat behind a confirm step, and **two-way AI
conversation** through `askAi()`, which calls the same `ai-generate` the app uses so the two cannot
drift into different personas.

## Why it still feels like a bot, precisely

Three things, all inside `askAi(question, userId, lang)`:

1. **It is stateless.** One question in, one answer out. A follow-up starts from nothing, so the
   parent restates her situation every message. This is the big one.
2. **Its context is a name and an age.** `kids.map(name + age)` - it does not know about the fever
   being tracked, the dose missed this morning, or last night's sleep, though the app chat does. It
   answers about a generic 7-month-old rather than about มีนา.
3. **Every reply is plain text.** `reply()` sends `{type: "text"}`. LINE supports quick-reply chips,
   Flex bubbles and buttons; none are used, so every interaction demands typing, one handed, over a
   cot.

The 3-sentence cap is right and stays. Short is correct for a phone bubble. The problem is not
length - it is that the short answer is generic and forgotten the moment it is sent.

## The fix, in order

1. **Conversation memory.** The same `assistant_memory`, keyed to the family. A LINE turn is a
   conversation turn, stored and read like any other. One memory, every surface.
2. **Real context.** Feed `askAi` what the home card already computes - overdue dose, active illness
   and its day number, next vaccine. The data exists; `TodayPlan` is the shape of it.
3. **Quick replies.** Two or three likely next questions as LINE quick-reply chips after every
   answer. A tap beats typing at 2am, and it steers the conversation somewhere answerable.
4. **Flex bubbles for structured answers.** A dose answer carries a "บันทึกแล้ว" button; a vaccine
   answer carries the date. Recording from chat already exists behind a confirm step - this gives it
   a button instead of a typed sentence.

## What the assistant should DO, beyond answering

Ordered by value to a tired parent, not by how impressive each sounds. Each names what it needs.

| Activity | Needs |
|---|---|
| **Doctor-visit brief** - everything since the last visit, in her hand at the clinic | Tier 2 memory + existing records. The sleeper hit: nobody asks for it, everybody wants it |
| **Night handover** - what happened today, for whoever takes the next shift | `ParentHandoffBrief` already exists; wire it to the assistant |
| **Record by talking** - "ลูกกินนม 100 ml" becomes a log | Built in LINE. Bring the same thing into the app chat |
| **Read the วัคซีน book or a medicine label from a photo** | Qwen VL 7B, already named in `LOCAL_AI_STACK.md` for Thai OCR |
| **Answer from THIS child's history** rather than general advice | Tier 2 memory, plus the context fix above |
| **Notice a pattern across weeks** - sleep shifting later since the fever | Tier 3 episodic memory. Only after 1 and 2 |
| **Draft the community post** when she cannot word it | Existing composer plus moderation |

Deliberately NOT on this list: anything that generates an image, writes a record without confirming,
or states a conclusion about a child's health.

## Chat UI - what "professional" means here

Not a glossier bubble. Three things, in order of how much trust each buys:

1. **It shows its working.** A caption above the answer naming what it read - "อ่านจากบันทึกยา 3 วัน
   ล่าสุด". The highest-value element on the screen: it lets a parent judge an answer instead of
   trusting it, and it makes a wrong answer visibly wrong rather than quietly wrong.
2. **Actions are cards inside the conversation**, with Confirm and "ไม่ใช่ตอนนี้", and a line saying
   nothing is saved until she confirms. `AgentAction` and the signed `AiCommand` ticket already do
   this; the chat UI only has to render it.
3. **Escalation is part of the answer, not a footer.** When the router trips, 1669 appears in the
   conversation, where the answer would have been.

Quick-reply chips under each answer; voice and camera in the composer. Everything else is restraint.
