// NongRak AI — server-side AI proxy. Keys live as Supabase secrets, never on the device.
// Cost-ordered fallback: Gemini keys 1-5 (across flash models, all free-tier)
//   -> OpenRouter free models (also $0)
//   -> Claude Haiku (PAID — true last resort, only reached if every free option fails)
//   -> friendly Thai fallback message.
// Reordered 2026-07-03: OpenRouter used to be tried AFTER Claude, meaning paid Anthropic
// calls happened before a free option was exhausted. Also fixed a real bug found the same
// day: the old hardcoded OpenRouter model (`google/gemini-2.0-flash-exp:free`) no longer
// exists in OpenRouter's catalog at all (same failure class as the gemini-1.5-flash
// retirement below) — that fallback tier was silently dead. Now a real model LIST, verified
// live against OpenRouter's /api/v1/models before deploying (free-model availability shifts
// often — re-verify before trusting these IDs long-term).
// Supports an optional per-request `system` override (custom persona/instructions) and an
// independent `jsonMode` flag. When `jsonMode` is true we make Gemini return clean JSON reliably:
//   * responseMimeType=application/json  (forces valid JSON; skipped when googleSearch is on,
//     because Gemini rejects tools + JSON mime together)
//   * thinkingConfig.thinkingBudget=0    (gemini-2.5-flash otherwise spends the whole token
//     budget on hidden reasoning and returns EMPTY text -> empty lists in the app)
// Back-compat: if a caller sends `system` but omits `jsonMode` entirely, jsonMode defaults to
// true (the original behavior, still relied on by AiRepository/NutritionAiService callers).
// Callers that want a custom system prompt WITHOUT JSON mode (e.g. plain-prose analysis text)
// must explicitly send `jsonMode: false`.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const SYSTEM_PROMPT = `คุณคือ "น้องรัก" (NongRak) ผู้ช่วย AI สำหรับพ่อแม่ในการเลี้ยงดูเด็กแรกเกิดถึง 3 ปี ตอบเป็นภาษาไทยเสมอ ยกเว้นผู้ใช้พิมพ์ภาษาอังกฤษ ห้ามใช้สัญลักษณ์พิเศษหรือ markdown ห้ามใช้ emoji ใช้ภาษาที่เป็นธรรมชาติ อบอุ่น และเข้าใจง่าย คุณเป็นผู้ช่วยที่แนะนำและให้กำลังใจ ไม่ทำสิ่งต่างๆ แทนพ่อแม่`;

// gemini-1.5-flash* were retired (404 NOT_FOUND). Use current models; the -lite models have their
// own quota pool, giving extra fallback within a key when the flash models hit 429 RESOURCE_EXHAUSTED.
const GEMINI_MODELS = ["gemini-2.5-flash", "gemini-2.0-flash", "gemini-2.5-flash-lite", "gemini-2.0-flash-lite"];
const CLAUDE_MODEL = "claude-3-5-haiku-20241022";
const FALLBACK = "ขออภัยค่ะ ระบบผู้ช่วย AI ไม่พร้อมให้บริการในขณะนี้ กรุณาลองใหม่อีกครั้งในอีกสักครู่";

type ImageInput = { data: string; mimeType: string } | null;

async function tryGemini(prompt: string, useSearch: boolean, maxTokens: number, system: string, jsonMode: boolean, image: ImageInput) {
  const raw = [
    Deno.env.get("GEMINI_API_KEY_1"),
    Deno.env.get("GEMINI_API_KEY_2"),
    Deno.env.get("GEMINI_API_KEY_3"),
    Deno.env.get("GEMINI_API_KEY_4"),
    Deno.env.get("GEMINI_API_KEY_5"),
  ];
  // Track each key's real slot so `provider` reflects which key actually answered.
  const present = raw.map((k, idx) => (k && k !== "null") ? (idx + 1) : 0).filter((x) => x !== 0);
  const keys = raw.filter((k) => k && k !== "null") as string[];

  for (let i = 0; i < keys.length; i++) {
    const slot = present[i];
    for (const model of GEMINI_MODELS) {
      try {
        const genCfg: Record<string, unknown> = { maxOutputTokens: maxTokens };
        // Disable thinking on 2.5 for EVERY call (only 2.5 supports thinkingConfig) so short
        // advisory text isn't starved by hidden reasoning tokens.
        if (model.startsWith("gemini-2.5")) genCfg.thinkingConfig = { thinkingBudget: 0 };
        // Force JSON output, but not together with googleSearch (Gemini rejects that combo).
        if (jsonMode && !useSearch) genCfg.responseMimeType = "application/json";
        const parts: Record<string, unknown>[] = [{ text: `${system}\n\n${prompt}` }];
        if (image) parts.push({ inlineData: { mimeType: image.mimeType, data: image.data } });
        const body: Record<string, unknown> = {
          contents: [{ role: "user", parts }],
          generationConfig: genCfg,
        };
        if (useSearch) body.tools = [{ googleSearch: {} }];
        const res = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${keys[i]}`,
          { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) },
        );
        const data = await res.json();
        if (data.error) continue;
        const text = (data.candidates?.[0]?.content?.parts ?? [])
          .map((p: { text?: string }) => p.text ?? "").join("").trim();
        if (text) return { text, provider: `Gemini ${slot}` };
      } catch (_) { /* try next */ }
    }
  }
  return null;
}

async function tryClaude(prompt: string, maxTokens: number, system: string, image: ImageInput) {
  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key || key === "null") return null;
  try {
    const content: Record<string, unknown>[] = [];
    // PDFs use Claude's separate "document" block type (not "image") -- sending a PDF under
    // type:"image" is rejected outright since that block only accepts image media types.
    if (image) {
      const blockType = image.mimeType === "application/pdf" ? "document" : "image";
      content.push({ type: blockType, source: { type: "base64", media_type: image.mimeType, data: image.data } });
    }
    content.push({ type: "text", text: prompt });
    const res = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "Content-Type": "application/json" },
      body: JSON.stringify({
        model: CLAUDE_MODEL, max_tokens: maxTokens, system: system,
        messages: [{ role: "user", content }],
      }),
    });
    const data = await res.json();
    if (data.error) return null;
    const text = (data.content ?? [])
      .filter((c: { type?: string }) => c.type === "text")
      .map((c: { text?: string }) => c.text ?? "").join("").trim();
    if (text) return { text, provider: "Claude" };
  } catch (_) { /* fall through */ }
  return null;
}

// Free tier (verified live against openrouter.ai/api/v1/models on 2026-07-03 — re-check
// before relying on these long-term, free-model availability shifts often; e.g. Qwen3
// Coder's :free endpoint was pulled shortly before this list was verified). Ordered by
// general/multilingual instruction-following quality over coding-specific models, since
// this is a Thai-language childcare assistant, not a code assistant.
const OPENROUTER_FREE_MODELS = [
  "meta-llama/llama-3.3-70b-instruct:free",
  "google/gemma-4-31b-it:free",
  "qwen/qwen3-next-80b-a3b-instruct:free",
  "openai/gpt-oss-120b:free",
];

// Free-tier fallback: OpenRouter (OpenAI-compatible gateway), tried BEFORE paid Claude.
// Rotates across up to 4 keys (OPENROUTER_API_KEY_1..4, mirroring the Gemini key-rotation
// pattern) and, per key, across several free models — so one exhausted key or one
// pulled/rate-limited model doesn't take out the whole tier. Falls back to the original
// single OPENROUTER_API_KEY secret if no numbered keys are set, so this deploys safely
// before any new keys have actually been added.
async function tryOpenRouter(prompt: string, maxTokens: number, system: string, image: ImageInput) {
  // OpenRouter's free-tier models here don't accept PDFs via image_url -- skip straight to
  // Claude (which has a real "document" block) rather than sending a request known to fail.
  if (image?.mimeType === "application/pdf") return null;
  const raw = [
    Deno.env.get("OPENROUTER_API_KEY_1"),
    Deno.env.get("OPENROUTER_API_KEY_2"),
    Deno.env.get("OPENROUTER_API_KEY_3"),
    Deno.env.get("OPENROUTER_API_KEY_4"),
  ];
  const keys = raw.filter((k) => k && k !== "null") as string[];
  if (keys.length === 0) {
    const legacy = Deno.env.get("OPENROUTER_API_KEY");
    if (legacy && legacy !== "null") keys.push(legacy);
  }
  if (keys.length === 0) return null;

  const userContent: unknown = image
    ? [{ type: "text", text: prompt }, { type: "image_url", image_url: { url: `data:${image.mimeType};base64,${image.data}` } }]
    : prompt;

  for (let i = 0; i < keys.length; i++) {
    for (const model of OPENROUTER_FREE_MODELS) {
      try {
        const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
          method: "POST",
          headers: { "Authorization": `Bearer ${keys[i]}`, "Content-Type": "application/json", "X-Title": "NongRak AI" },
          body: JSON.stringify({
            model,
            max_tokens: maxTokens,
            messages: [{ role: "system", content: system }, { role: "user", content: userContent }],
          }),
        });
        const data = await res.json();
        if (data.error) continue;
        const text = String(data.choices?.[0]?.message?.content ?? "").trim();
        if (text) return { text, provider: `OpenRouter ${i + 1} (${model.split("/")[1]})` };
      } catch (_) { /* try next model/key */ }
    }
  }
  return null;
}

// --- Text to speech -------------------------------------------------------------------------
// Gemini's TTS models return raw 24kHz mono PCM as base64, not a container format. The client
// feeds it straight to AudioTrack, so no decoding step is needed on the device.
const TTS_MODELS = ["gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts"];
const TTS_DEFAULT_VOICE = "Leda";          // warm and youthful; suits a childcare companion
const TTS_MAX_CHARS = 700;                 // caps latency and cost on a long reply

function geminiKeys(): { key: string; slot: number }[] {
  return [
    Deno.env.get("GEMINI_API_KEY_1"),
    Deno.env.get("GEMINI_API_KEY_2"),
    Deno.env.get("GEMINI_API_KEY_3"),
    Deno.env.get("GEMINI_API_KEY_4"),
    Deno.env.get("GEMINI_API_KEY_5"),
  ]
    .map((key, idx) => ({ key: key ?? "", slot: idx + 1 }))
    .filter((k) => k.key && k.key !== "null");
}

// Gemini TTS takes direction as plain language in the prompt itself — there is no rate/pitch
// parameter. Without a direction it reads like a screen reader; this is what makes it sound
// like a person talking to a parent.
const TTS_DEFAULT_STYLE =
  "พูดด้วยน้ำเสียงอบอุ่น นุ่มนวล เป็นธรรมชาติ เหมือนเพื่อนที่ห่วงใยกำลังคุยด้วย ไม่ต้องอ่านเหมือนหุ่นยนต์";

async function tryGeminiTts(text: string, voice: string, style: string) {
  const directed = `${style}: ${text}`;
  for (const { key, slot } of geminiKeys()) {
    for (const model of TTS_MODELS) {
      try {
        const res = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              contents: [{ parts: [{ text: directed }] }],
              generationConfig: {
                responseModalities: ["AUDIO"],
                speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } },
              },
            }),
          },
        );
        const data = await res.json();
        const inline = data.candidates?.[0]?.content?.parts?.[0]?.inlineData;
        if (inline?.data) {
          return {
            audioBase64: inline.data as string,
            mimeType: (inline.mimeType as string) ?? "audio/L16;codec=pcm;rate=24000",
            provider: `Gemini TTS ${slot} (${model})`,
          };
        }
      } catch (_) { /* try next model/key */ }
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Self-hosted NVIDIA box (GB10 / Grace Blackwell), reached over a Cloudflare Tunnel.
//
// Speaks the OpenAI Chat Completions API — what vLLM, llama.cpp and Ollama all serve,
// and the same shape MedGemmaClient already talks on the device. No new protocol.
//
// First in the chain: it is the only provider with no per-token cost and the only one
// where a family's questions about their child never leave hardware we own. Everything
// below it is untouched, so a box that is rebooting, loading a model, or simply off
// costs one failed request and the cloud answers exactly as it does today.
//
// LOCAL_AI_URL unset -> returns null instantly, so this deploys safely BEFORE the
// hardware exists and switches on by setting one secret.
//
// Two cases deliberately skip local and go straight to Gemini:
//   * useSearch  — Google Search grounding has no local equivalent, and the nutrition
//                  and research features depend on it being real.
//   * image      — only when LOCAL_AI_VISION is not "true". A text-only local model
//                  handed an image answers confidently about nothing.
// ---------------------------------------------------------------------------
async function tryLocal(
  prompt: string,
  useSearch: boolean,
  maxTokens: number,
  system: string,
  jsonMode: boolean,
  image: ImageInput,
) {
  const base = (Deno.env.get("LOCAL_AI_URL") ?? "").trim().replace(/\/+$/, "");
  if (!base) return null;
  if (useSearch) return null;
  if (image && Deno.env.get("LOCAL_AI_VISION") !== "true") return null;

  const url = base.endsWith("/chat/completions") ? base : `${base}/v1/chat/completions`;
  const model = Deno.env.get("LOCAL_AI_MODEL") ?? "local";

  // Decode on a GB10 is memory-bandwidth bound (~273 GB/s), not compute bound: a 12B at
  // 4-bit runs ~25 tok/s and a 27B nearer 12, so a full 1024-token answer can genuinely
  // take over a minute. The tunnel is what makes so generous a budget safe — when the box
  // is down Cloudflare answers 502/530 in well under a second, so this ceiling is only
  // ever spent on a machine that is actually working.
  // maxTokens is the better lever if answers feel slow; this is just the ceiling.
  const timeoutMs = Number(Deno.env.get("LOCAL_AI_TIMEOUT_MS") ?? 90_000);

  const userContent = image
    ? [
        { type: "text", text: prompt },
        { type: "image_url", image_url: { url: `data:${image.mimeType};base64,${image.data}` } },
      ]
    : prompt;

  const body: Record<string, unknown> = {
    model,
    max_tokens: maxTokens,
    temperature: 0.7,
    messages: [
      { role: "system", content: system },
      { role: "user", content: userContent },
    ],
  };
  // vLLM enforces this with guided decoding, so JSON callers get valid JSON or nothing —
  // the same guarantee responseMimeType gives us on Gemini.
  if (jsonMode) body.response_format = { type: "json_object" };

  const headers: Record<string, string> = { "Content-Type": "application/json" };
  // Cloudflare Access service token: Cloudflare rejects anything without it at its own
  // edge, so unauthenticated traffic never reaches the box at all. The bearer token is a
  // second lock on the inference server itself.
  const cfId = Deno.env.get("LOCAL_AI_CF_CLIENT_ID");
  const cfSecret = Deno.env.get("LOCAL_AI_CF_CLIENT_SECRET");
  if (cfId && cfSecret) {
    headers["CF-Access-Client-Id"] = cfId;
    headers["CF-Access-Client-Secret"] = cfSecret;
  }
  const bearer = Deno.env.get("LOCAL_AI_TOKEN");
  if (bearer) headers["Authorization"] = `Bearer ${bearer}`;

  try {
    const res = await fetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (!res.ok) return null;
    const data = await res.json();
    const text = data?.choices?.[0]?.message?.content;
    if (typeof text !== "string" || !text.trim()) return null;
    return { text, provider: `Local (${model})` };
  } catch (_) {
    // Box off, tunnel down, model still loading, or over the time budget — all the same
    // answer here: say nothing and let the cloud chain take the turn.
    return null;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "POST only" }), { status: 405, headers: { "Content-Type": "application/json" } });
  }
  let prompt = "", useSearch = false, maxTokens = 1024, system = SYSTEM_PROMPT, jsonMode = false;
  let image: ImageInput = null;
  let mode = "text", voice = TTS_DEFAULT_VOICE, style = TTS_DEFAULT_STYLE;
  try {
    const b = await req.json();
    prompt = b.prompt ?? "";
    if (typeof b.mode === "string") mode = b.mode;
    if (typeof b.voice === "string" && b.voice.trim()) voice = b.voice;
    if (typeof b.style === "string" && b.style.trim()) style = b.style;
    useSearch = !!b.useSearch;
    maxTokens = typeof b.maxTokens === "number" ? b.maxTokens : 1024;
    // A per-request system override replaces the persona; jsonMode is independent (see header note).
    if (typeof b.system === "string" && b.system.trim()) system = b.system;
    if (typeof b.jsonMode === "boolean") jsonMode = b.jsonMode;
    else if (system !== SYSTEM_PROMPT) jsonMode = true;
    // Optional image (base64, no data: prefix) for vision questions.
    if (typeof b.imageBase64 === "string" && b.imageBase64.length > 0) {
      image = { data: b.imageBase64, mimeType: typeof b.imageMimeType === "string" ? b.imageMimeType : "image/jpeg" };
    }
  } catch (_) { /* ignore */ }

  // Speech synthesis is a separate contract: audio out, no provider fallback chain. If Gemini
  // cannot speak, the caller falls back to the device's own TTS rather than to another model.
  if (mode === "tts") {
    const line = String(prompt).trim().slice(0, TTS_MAX_CHARS);
    if (!line) {
      return new Response(JSON.stringify({ error: "missing prompt" }), { status: 400, headers: { "Content-Type": "application/json" } });
    }
    const audio = await tryGeminiTts(line, voice, style);
    if (!audio) {
      return new Response(JSON.stringify({ error: "tts unavailable" }), { status: 503, headers: { "Content-Type": "application/json" } });
    }
    return new Response(JSON.stringify(audio), { headers: { "Content-Type": "application/json" } });
  }

  // Text is required unless an image is attached (user may send just a photo).
  if (!prompt && !image) {
    return new Response(JSON.stringify({ error: "missing prompt" }), { status: 400, headers: { "Content-Type": "application/json" } });
  }
  if (!prompt) prompt = "ช่วยดูรูปภาพนี้และให้คำแนะนำ";

  // Cost order: self-hosted GB10 (free, private) -> free Gemini -> free OpenRouter
  // -> PAID Claude (true last resort). tryLocal is a no-op until LOCAL_AI_URL is set.
  const result = (await tryLocal(prompt, useSearch, maxTokens, system, jsonMode, image))
    ?? (await tryGemini(prompt, useSearch, maxTokens, system, jsonMode, image))
    ?? (await tryOpenRouter(prompt, maxTokens, system, image))
    ?? (await tryClaude(prompt, maxTokens, system, image));
  const payload = result ?? { text: FALLBACK, provider: "" };
  return new Response(JSON.stringify(payload), { headers: { "Content-Type": "application/json" } });
});
