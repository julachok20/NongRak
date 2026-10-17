// NongRak AI — ephemeral token minter for Gemini Live voice calls.
//
// WHY THIS EXISTS
//
// The Live call used to authenticate with `BuildConfig.GEMINI_LIVE_API_KEY`, i.e. a real Google
// API key compiled into the APK. Anyone who downloads the app can extract it and spend against
// the project's Google account. That reversed the rule written into app/build.gradle.kts:
// AI keys live as Supabase secrets and never reach a device.
//
// So the key stays here, and the device gets a token instead: single-use, short-lived, and
// useless to anyone who pulls it out of the app an hour later.
//
// SETUP (once):
//   supabase secrets set GEMINI_LIVE_API_KEY=...        # the key, server side only
//   supabase functions deploy gemini-live-token
//
// The key in local.properties must be ROTATED before this helps — the old one is already inside
// every build made from that checkout, including the v1.1 bundle.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";

/** How long a minted token stays usable. Long enough to place a call, short enough to be dull. */
const TOKEN_TTL_MINUTES = 15;

/** How long after minting a NEW session may start with it. */
const START_WINDOW_MINUTES = 2;

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  // Supabase verifies the JWT before the function runs (verify_jwt is on by default). The header
  // is checked again here so a misconfigured deployment fails closed rather than minting tokens
  // for anyone who finds the URL.
  const auth = req.headers.get("Authorization") ?? "";
  if (!auth.toLowerCase().startsWith("bearer ") || auth.length < 40) {
    return json({ error: "unauthorized" }, 401);
  }

  const apiKey = Deno.env.get("GEMINI_LIVE_API_KEY");
  if (!apiKey) {
    // Named, not silent: the app falls back to the turn-based call path, and this tells whoever
    // reads the logs exactly which secret is missing.
    return json({ error: "gemini_live_not_configured" }, 503);
  }

  const now = Date.now();
  const expireTime = new Date(now + TOKEN_TTL_MINUTES * 60_000).toISOString();
  const newSessionExpireTime = new Date(now + START_WINDOW_MINUTES * 60_000).toISOString();

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1alpha/auth_tokens?key=${apiKey}`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        // One session per token. A leaked token cannot be replayed into a second call.
        uses: 1,
        expireTime,
        newSessionExpireTime,
      }),
    },
  );

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    console.error("auth_tokens failed", response.status, detail.slice(0, 300));
    // The status is passed through so the client can tell "not configured" from "Google is down"
    // and from "the key was rejected" — three different things to do about it.
    return json({ error: "token_mint_failed", status: response.status }, 502);
  }

  const body = await response.json().catch(() => null);
  const token = body?.name;
  if (typeof token !== "string" || token.length === 0) {
    console.error("auth_tokens returned no name field");
    return json({ error: "token_mint_failed" }, 502);
  }

  return json({ token, expiresAt: expireTime });
});

function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
