# Local AI Stack — NVIDIA GB10 Grace Blackwell (128 GB, 4 TB)

Every model worth running on this box for NongRak, what each is for, and what it costs in memory.

---

## The number that governs everything

| | Bandwidth |
|---|---|
| RTX 4090 | ~1,000 GB/s |
| H100 | ~3,350 GB/s |
| **GB10** | **~273 GB/s** |

Generation speed is bandwidth-bound: `tokens/sec ≈ bandwidth ÷ model size`.

| Model size (4-bit) | Realistic single-stream |
|---|---|
| 8B (~5 GB) | 40–50 tok/s |
| 31B (~18 GB) | 12–14 tok/s |
| 70B (~40 GB) | 4–6 tok/s — batch only |

**128 GB is capacity, not speed.** The win is running *many* models at once, not one enormous one.

Thai costs 2–3× more tokens than English on most tokenisers, so effective Thai throughput is worse
than the figures above. Measure token counts per model before choosing.

---

## 1. Language — the core

| Model | Size | Role | Notes |
|---|---|---|---|
| **Gemma ~31B** | 18 GB | Chat companion | Same family as Gemini, so **one system prompt serves both** cloud and local |
| **Typhoon** (SCB 10X) | ~18 GB | Chat alternative | Thai-specialised, Thai-adapted tokeniser — test against Gemma |
| **Qwen 3 8B** | 5 GB | Agent action extraction | Constrained JSON — a big model is wasted here |
| **Any 70B** | 40 GB | Offline judge | Useless for chat at 5 tok/s; ideal overnight for scoring evals |

**Grammar-constrained decoding** (GBNF in llama.cpp, XGrammar in vLLM) makes invalid JSON
structurally impossible. Locally this replaces most of the parser's tolerance layer.

---

## 2. Voice — the call system

The full local pipeline, in signal order:

| Stage | Model | Size | Why |
|---|---|---|---|
| **Denoise** | **DeepFilterNet** | ~10 MB | A nursery has a crying baby, a fan, a TV. Cleaning audio before ASR is the cheapest accuracy gain available |
| **Voice activity** | **Silero VAD** | ~2 MB | Detects speech vs silence; stops streaming dead air |
| **Speech to text** | **Whisper large-v3** | ~3 GB | Best open Thai ASR. **Fine-tune it on your Common Voice Thai** |
| | *faster-whisper / distil-whisper* | ~1 GB | Same model, lower latency, small accuracy cost |
| **Reasoning** | Gemma / Qwen | above | — |
| **Text to speech** | **Fish Speech** | ~2 GB | Fine-tune for Thai; voice cloning gives each mascot its own voice |
| | *F5-TTS, XTTS-v2* | ~2 GB | Alternatives worth comparing on Thai |
| **Echo cancellation** | Android `AcousticEchoCanceler` | — | On the phone, not here. Without it the mic hears the mascot and the AI interrupts itself |

**Honest limit:** open speech-to-speech models for Thai are not mature. A local pipeline is
cascaded, so it will feel slower than Gemini Live. Keep Live for the real-time call, and use the
local stack for **batch generation** — mascot lines, story audio, cached greetings — and for
fine-tuning.

---

## 3. Vision

| Model | Size | Role |
|---|---|---|
| **Qwen VL 7B** | 6 GB | Thai OCR — สมุดวัคซีน, medicine labels, paper baby books |

Most vision models read Thai script badly. This one handles it. Local means a child's full medical
history never leaves the house.

---

## 4. Retrieval

| Model | Size | Role |
|---|---|---|
| **BGE-M3** | 1.2 GB | Embeddings — multilingual, 8k context, **dense + sparse in one model** |
| **BGE-reranker-v2-m3** | 600 MB | Reranks top-20 to top-3. Bigger quality gain than upgrading the embedder |

Sparse retrieval is what catches exact vaccine and medicine names, where pure semantic search drifts.

---

## 5. Safety

| Model | Size | Role |
|---|---|---|
| **ShieldGemma** | 2 GB | Content classifier — the actual safety layer |

**Not MedGemma.** That is an English clinical domain model, not a safety filter, and domain
fine-tuning degrades Thai. For anything that could hurt a child — medicine doses, red-flag symptoms
— **retrieve fixed verified content, never generate**.

---

## 6. Audio events

| Model | Size | Role |
|---|---|---|
| **YAMNet** | 4 MB | Baby cry detection — 521 AudioSet classes including infant cry |

Runs on the *phone*, on-device, fully private. GB10 is where you fine-tune it on real nursery audio
so it can tell a crying baby from a television.

---

## 7. Creative

| Model | Size | Role |
|---|---|---|
| **Flux / SDXL + LoRA on your 4 mascots** | 12–24 GB | New poses, side views, separated limbs, story art |

This removes the ceiling hit during animation work: each mascot exists in exactly one pose, which is
why walking and layered rigging were impossible. A LoRA turns "commission more art" into an afternoon.

---

## 8. Translation

| Model | Size | Role |
|---|---|---|
| **NLLB-200** | 3 GB | Burmese / Khmer / Lao to Thai and back |

Many nannies in Thailand are migrant workers. The nanny logs in her language, the parent reads Thai,
one shared record. The most differentiating feature on this list.

---

## 9. For development, not the app

| Model | Size | Role |
|---|---|---|
| **Qwen Coder 14B** | 9 GB | Offline coding, no quota |
| **BGE-M3 over graphify** | shared | RAG across the 4,600-node code graph |
| **Whisper** | shared | Your own voice notes to structured tasks |

---

## Memory plan

**Always resident (~36 GB of 128):**

```
Gemma 31B chat        18 GB
Qwen 8B action         5 GB
Whisper large-v3       3 GB
Fish Speech            2 GB
Qwen VL 7B             6 GB
BGE-M3 + reranker      2 GB
ShieldGemma            2 GB
Silero VAD + DFN      ~0 GB
```

**On demand:** 70B judge (40 GB), Flux + LoRA training (24 GB), Qwen Coder (9 GB).

Capacity is never the constraint. **Bandwidth is** — models compete when run at the same time, so
keep the pipeline sequential.

---

## What not to run

- **70B for interactive chat** — 4-6 tok/s, and it crowds out everything else
- **MedGemma** — wrong tool, weak Thai
- **Serving your app's users from this box** — home internet, no redundancy, one machine. Gemini's
  free tier is faster and never goes down because something got unplugged

---

## Build order

1. **An eval set** — 200 real Thai prompts with expected behaviour. Without it every model comparison
   is guesswork, and Thai quality is exactly what cannot be eyeballed reliably.
2. Ollama + Gemma and Typhoon — tokeniser counts, then the Thai bake-off
3. Whisper fine-tune on Common Voice Thai
4. Mascot LoRA
5. Everything else

Integrate as **tier 0** in the existing `ai-generate` fallback chain:

```
local GB10 (if reachable) -> Gemini free -> OpenRouter free -> Claude paid
```
