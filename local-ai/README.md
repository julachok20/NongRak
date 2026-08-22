# NongRak local AI — GB10 + Cloudflare Tunnel

Routes NongRak's AI through a self-hosted NVIDIA GB10 instead of Gemini, without
changing a single line of the Android app.

## Why it needs no app change

`AiProxyClient` already sends every request to the `ai-generate` edge function, which
holds the provider chain. Adding the box means adding one provider at the top of that
chain — the app keeps calling the same URL and just gets a different `provider` string
back, which is already surfaced for debugging.

```
App -> Supabase edge function -> Cloudflare Tunnel -> GB10 (vLLM)
                              \-> Gemini -> OpenRouter -> Claude   (unchanged fallback)
```

The edge function stays in the path on purpose. Calling the box directly from the app
would mean shipping the tunnel hostname and a credential to every phone, and losing the
fallback chain the moment the box is offline.

## Order of work

**1. Box** — Ollama, tuned by `ollama/override.conf`. Serves an OpenAI-compatible API
on `127.0.0.1:11434`. Loopback-only bind is deliberate: cloudflared is the only thing
that can reach it, so a misconfigured tunnel fails closed rather than exposing an open
LLM to the network.

```bash
curl -fsSL https://ollama.com/install.sh | sh   # ARM64 native, no container needed
sudo mkdir -p /opt/models /etc/systemd/system/ollama.service.d
sudo cp ollama/override.conf /etc/systemd/system/ollama.service.d/
sudo systemctl daemon-reload && sudo systemctl restart ollama
ollama pull gemma3:12b
```

Ollama over vLLM for now: vLLM's advantage is continuous batching under heavy
concurrency, which NongRak does not have yet, and it costs an NGC container and a
CUDA-version debugging session on aarch64. Both serve the same OpenAI API, so moving to
vLLM later is one Supabase secret and a systemd file — no app release, no code change.
Switch when you can measure a queueing problem, not before.

**2. Tunnel** — `cloudflared/config.yml`. Outbound-only, no port forward, no static IP.

**3. Lock the door.** A public inference endpoint with no auth gets found and abused —
this is the single biggest risk in the whole setup. Two locks, both cheap:

- **Cloudflare Access service token** (Zero Trust → Access → Service Auth). Cloudflare
  rejects unauthenticated requests at its own edge, so they never touch the GB10.
- **`VLLM_API_KEY`** on the server itself, as a second lock if Access is ever misconfigured.

**4. Switch it on** — set the Supabase secrets. Until `LOCAL_AI_URL` exists, `tryLocal`
returns `null` immediately and nothing changes, so steps 1-3 can be done and verified
long before the chain is touched.

```bash
supabase secrets set LOCAL_AI_URL=https://ai.nongrak-ai.com
supabase secrets set LOCAL_AI_MODEL=nongrak-local
supabase secrets set LOCAL_AI_TOKEN=<the VLLM_API_KEY>
supabase secrets set LOCAL_AI_CF_CLIENT_ID=<service token id>
supabase secrets set LOCAL_AI_CF_CLIENT_SECRET=<service token secret>
supabase functions deploy ai-generate
```

| Secret | Effect when unset |
|---|---|
| `LOCAL_AI_URL` | Local provider disabled entirely — current behaviour |
| `LOCAL_AI_MODEL` | Sends `"local"` as the model id |
| `LOCAL_AI_TOKEN` | No `Authorization` header sent |
| `LOCAL_AI_CF_*` | No Cloudflare Access headers — only safe if Access is off |
| `LOCAL_AI_VISION` | Photo questions skip the box and go to Gemini |
| `LOCAL_AI_TIMEOUT_MS` | 90 000 |

## Verify

From anywhere, with the service token:

```bash
curl https://ai.nongrak-ai.com/v1/chat/completions \
  -H "CF-Access-Client-Id: $CF_ID" -H "CF-Access-Client-Secret: $CF_SECRET" \
  -H "Authorization: Bearer $LOCAL_AI_TOKEN" -H "Content-Type: application/json" \
  -d '{"model":"nongrak-local","messages":[{"role":"user","content":"สวัสดี"}]}'
```

Then in the app, check the `provider` field reads `Local (nongrak-local)`. If it still
says Gemini, the box was skipped — see the two deliberate skips below.

## Two cases that deliberately never use the box

- **`useSearch`** — Google Search grounding has no local equivalent. Nutrition and
  research answers depend on grounding being real, so they stay on Gemini.
- **Images** — unless `LOCAL_AI_VISION=true`. A text-only model handed a photo will
  answer confidently about nothing, which is worse than falling back.

## What is not covered yet

Voice. TTS still goes to Gemini (`mode: "tts"` in the edge function) and is a separate
contract with no fallback chain. Moving that local is a bigger job than this one and
should wait until text is proven on real hardware.

## Model choice on a GB10

128GB of unified memory, but only ~273 GB/s of bandwidth. Single-stream decode is
bandwidth bound, so **capacity is not the constraint — bandwidth is.** A 70B model fits
comfortably and answers at roughly 6 tok/s, which reads as broken in a chat bubble.

The memory is better spent on several specialist models resident at once:

| Role | Model | ~VRAM (q4) | ~tok/s |
|---|---|---|---|
| Chat | `gemma3:12b` — strong Thai, multimodal | 8 GB | ~25 |
| Chat (bigger) | `gemma3:27b` — better reasoning, notably slower | 16 GB | ~12 |
| Safety check | MedGemma 4B | 3 GB | fast |
| Embeddings | `bge-m3` — multilingual, handles Thai | 2 GB | fast |
| ASR (calls) | Whisper large-v3 | 3 GB | realtime |

Around 20 GB of 128 GB with the 12B. Enormous headroom — which is the point: run the
whole pipeline resident rather than one oversized model that swaps.

**Start at 12B, not 27B.** For warm Thai parenting advice a parent feels the latency far
more than the extra reasoning, and you can A/B it later by changing one secret.

**Also worth testing: Typhoon** (SCB 10X) — a Thai-specific fine-tune. For a Thai-first
app a Thai-tuned model can beat a larger general one on tone and idiom, which is most of
what NongRak's voice is. Verify the current release before pulling; the line moves fast.

## Power

DGX Spark draws roughly 240 W. A ~1000 VA UPS carries it plus the network gear through
the short cuts that are common in Thailand, which is all you need — the box is a
fallback provider, not the critical path. If mains drops for longer, `tryLocal` fails,
Cloudflare answers 502 in under a second, and the app is on Gemini before anyone notices.
