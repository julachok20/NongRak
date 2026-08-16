# NongRak AI — AI System Overview

Written for a project submission. Everything below describes what is implemented and running,
not a plan.

---

## 1. What the AI does in this app

Most childcare apps ask a parent to open a screen, choose a category, fill a form and press save.
NongRak lets them say what happened — *"ลูกเพิ่งหลับ"*, *"ซื้อนมผง 450 บาท"* — and the app writes
the record itself.

Three capabilities, one pipeline:

| | |
|---|---|
| **Answer** | Childcare questions, in Thai, with the child's own history as context |
| **Act** | Write to the child's record: sleep, feeds, milk, nappies, growth, vaccines, medicine, symptoms, expenses, calendar |
| **Navigate** | Open any screen on request |

---

## 2. AI Agent — the process

```
 ┌──────────────┐   text or voice
 │ Parent       │ ─────────────────────┐
 └──────────────┘                      ▼
                            ┌──────────────────────┐
                            │ ChatViewModel        │
                            └──────────┬───────────┘
                                       ▼
                            ┌──────────────────────┐   history + system prompt
                            │ ChatRepository       │   + companion persona
                            │                      │   + user's current time & zone
                            └──────────┬───────────┘
                                       ▼
                    ┌──────────────────────────────────────┐
                    │ Supabase edge function `ai-generate` │  JWT verified
                    │ cost-ordered provider fallback       │
                    └──────────┬───────────────────────────┘
                               ▼
                    reply text  +  optional <AGENT_ACTION> block
                               ▼
                    ┌──────────────────────┐
                    │ parseAgentAction     │  strips the block; bubble stays plain
                    └──────────┬───────────┘
                               ▼
              ┌────────────────┴─────────────────┐
              ▼                                  ▼
      confirmation card                  Auto Record mode
      (parent taps ยืนยัน)                (additive logging only)
              └────────────────┬─────────────────┘
                               ▼
                    ┌──────────────────────┐
                    │ dispatchAgentAction  │  → Repository → Supabase
                    └──────────┬───────────┘
                               ▼
                    RLS decides. Record written. Companion fed.
```

### Step by step

**1. Context assembly.** Prior conversation is retrieved as memory. The system prompt carries the
caller's *relationship to the child* (a nanny is addressed differently from a mother), the chosen
companion's persona, and — critically — **the user's current date, time and timezone**.

> Without the clock the model cannot resolve "เมื่อครู่". It answered with the literal placeholder
> `[current_time - 3 minutes]`, and the record failed. Supplying the clock fixed an entire class of
> relative-time instructions.

**2. Provider fallback, cost-ordered.** The edge function tries, in order:

1. **Gemini** — five API keys × several flash models, all free tier
2. **OpenRouter** — free model list, verified live
3. **Claude** — paid, only after both free tiers are exhausted
4. A friendly Thai message if everything is down — never a stack trace

**3. Structured action extraction.** The model may append one block:

```
<AGENT_ACTION>{"type":"log_health","confirmText":"บันทึกอาการ: ตัวร้อน 38.2°C",
               "params":{"title":"ตัวร้อน","temperature":"38.2"}}</AGENT_ACTION>
```

It is stripped from the visible bubble and turned into structured data. The parent sees a normal
sentence plus a confirmation card.

**4. Missing information.** If a detail is missing, the model emits `<NEED_INFO>` instead and asks
one short question. The answer returns as an ordinary message, so the conversation continues
naturally and the action is emitted on the next turn. The companion's face shows `CONFUSED` while
this is pending.

**5. Consent.** Nothing is written until the parent taps ยืนยัน — *except* in **Auto Record** mode,
which the parent turns on deliberately and which permits **additive logging only**. Deletions and
navigation always wait for a tap: an unattended delete loses data nobody knew was there, and
unattended navigation yanks the screen away mid-sentence.

**6. Execution and authority.** The action is dispatched to the repository layer, which calls
Supabase. **Row-level security decides what is allowed** — the agent can never exceed what that user
could do by hand. A nanny's AI has a nanny's permissions.

**7. Same rules as the UI.** `add_schedule` runs the identical `validateActivityWindow` the manual
Add Activity screen runs, so the agent cannot become a way around a validation.

**8. Diagnosable failure.** A failed action logs *what it received*, not only what it wanted:

```
W AgentAction: type='log_sleep' missing required param 'endTime' — had keys: [sleepType, startTime]
```

### Actions the agent can perform

| Domain | Actions |
|---|---|
| Sleep | `log_sleep` — start alone is valid; an ongoing nap needs no end time |
| Feeding | `log_food`, `log_milk` |
| Health | `log_health`, `update_growth`, `log_medicine`, `complete_vaccine`, `add_allergy`, `add_chronic_condition` |
| Daily care | `log_waste`, `log_exercise` |
| Money | `log_expense` — parsed to integer satang by the same parser as the manual form |
| Time | `add_schedule` — forward-only, same validator as the screen |
| App control | `navigate` — any screen by keyword, Thai or English |
| Correction | `delete_most_recent` — always requires a tap |

---

## 3. Call system — listening and speaking

A call is the same agent with speech at both ends. Everything above still applies: the AI can log a
feed while the parent is holding the baby and cannot type.

```
   speaks  ┌───────────────────────┐  th-TH / en-US
 ─────────▶│ Android               │  free-form model
           │ SpeechRecognizer      │  partial results ON
           └───────────┬───────────┘
                       │ live transcript while speaking
                       ▼
           ┌───────────────────────┐
           │ Same agent pipeline   │  (section 2)
           └───────────┬───────────┘
                       ▼ reply text
           ┌───────────────────────┐
           │ TtsSpeaker            │
           │  → AiProxyClient      │  mode="tts"
           │  → Gemini TTS         │  voice + natural-language style
           └───────────┬───────────┘
                       ▼ base64 PCM, 24 kHz mono
           ┌───────────────────────┐
           │ AudioTrack (streamed) │  no file, no decode step
           └───────────┬───────────┘
                       ▼
             mascot mouth animates while isSpeaking
```

### Listening

- `SpeechRecognizer` with `ACTION_RECOGNIZE_SPEECH`, `LANGUAGE_MODEL_FREE_FORM`
- Language follows the app: `th-TH` or `en-US`
- **Tuned for a tired parent, not a dictation app:**

| Setting | Value | Why |
|---|---|---|
| Complete silence | 1800 ms | Thai speakers pause mid-sentence; 1 s cuts them off |
| Possibly-complete silence | 1800 ms | same reason |
| Minimum input | 400 ms | rejects a cough or a door |
| Partial results | on | the transcript appears while speaking, so the parent sees they are heard |

- `ERROR_NO_MATCH` and `ERROR_SPEECH_TIMEOUT` restart listening rather than ending the call — silence
  is normal when someone is settling a baby.

### Speaking

- **Gemini TTS** (`gemini-2.5-flash-preview-tts`, falling back to the pro model) through the same
  edge function, so no API key ever reaches the device.
- Returns **raw 24 kHz mono PCM**, played straight through `AudioTrack` — no temporary file, no
  decoder, minimum delay before the voice starts.
- **Delivery is steered by language, not parameters.** Gemini TTS exposes no rate or pitch control,
  so the style is written as an instruction, and it changes with the mode:
  - *Story* — "เล่านิทานด้วยน้ำเสียงนุ่มนวล ช้าๆ อบอุ่น เหมือนกำลังกล่อมเด็กก่อนนอน"
  - *Talking to the child* — bright, friendly, slow and clear
  - *Normal* — a caring friend, explicitly "not a robot reading"
- **Offline fallback:** if the cloud voice fails, Android's own TextToSpeech takes over. The
  companion is never mute.

### The companion during a call

The mascot on the call screen is the one chosen in chat. While speaking, its mouth is driven by two
sine waves at unrelated frequencies so the flap never looks metronomic, and its body plays the
`SPEAKING` cadence. Eyes blink on their own timer, every 2.2–5.2 s, with an occasional double blink.

---

## 4. Feature and tool map

| Feature | Technology | Where |
|---|---|---|
| Conversational AI | Gemini → OpenRouter → Claude, cost-ordered | `ai-generate` edge function |
| Structured actions | Tagged JSON block parsed out of the reply | `util/AgentActionParser.kt` |
| Action execution | Repository layer, authorised by RLS | `ChatViewModel.dispatchAgentAction` |
| Slot filling | `<NEED_INFO>` one-question loop | `ChatViewModel.answerNeedInfo` |
| Speech to text | Android `SpeechRecognizer`, th-TH | `CallScreen.kt` |
| Text to speech | Gemini TTS → PCM → `AudioTrack` | `util/TtsSpeaker.kt`, `AiProxyClient.kt` |
| Companion animation | Bone skinning → `Canvas.drawBitmapMesh` | `ui/mascot/` |
| Outing suggestions | Google Places (New) + the model as chooser | `data/remote/ActivityRecommender.kt` |
| Vision | Image sent alongside the message | a rash, a label, a vaccine book |
| Memory | Conversation history per session | `ChatRepository` |
| Safety | Postgres RLS + `security definer` RPCs | database |

---

## 5. Design decisions worth defending

**The AI never invents facts about the world.** For outing suggestions the model receives real
venues from Google Places and may only copy their ids. Any venue it invents is dropped before
reaching the screen. It chooses and explains; it does not author reality.

**Authority lives in the database, not the app.** The agent runs as the signed-in user, and RLS
enforces what that user may do — so a manipulated prompt cannot widen access. The worst case is an
action that gets refused.

**Confirmation is the default; automation is opt-in and narrow.** Auto Record covers additive
logging only. Anything destructive keeps a human in the loop.

**Failures are legible** — the model's and our own. One error that read only "ดำเนินการไม่สำเร็จ"
cost hours of debugging; every action failure now names the parameters it actually received.

**Cost is a design constraint.** Free tiers are tried before paid ones, AI outing suggestions are
cached for three days and shared between everyone caring for the same child, and the child's data
never leaves Supabase for a third party that is not answering the request.
