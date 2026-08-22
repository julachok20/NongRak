// Signs a parent in with LINE Login and hands back a real Supabase session.
//
// Deployed public — the caller has no Supabase session yet, that is the entire point:
//   supabase functions deploy line-auth --no-verify-jwt
//
// Supabase Auth has no LINE provider and signInWithIdToken only accepts
// Google/Apple/Azure/Facebook/Kakao, so the session is minted here instead:
//
//   1. LINE's own /oauth2/v2.1/verify validates the id_token (signature, iss, aud, exp).
//   2. We resolve or create the matching Supabase user.
//   3. admin.generateLink issues a single-use magic-link token.
//   4. /auth/v1/verify exchanges that token for a real access + refresh token pair.
//
// Step 1 is the whole security boundary. An id_token that is not verified against LINE is just
// a base64 blob any caller can write by hand, naming any LINE user they like — including one
// already linked to a family's account. Never decode it locally and trust the claims.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const LOGIN_CHANNEL_ID = Deno.env.get("LINE_LOGIN_CHANNEL_ID") ?? "";

const admin = createClient(SUPABASE_URL, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { autoRefreshToken: false, persistSession: false },
});

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type LineClaims = { sub: string; name?: string; picture?: string; email?: string };

/**
 * Asks LINE whether this id_token is genuine and was issued for OUR Login channel.
 *
 * Passing client_id makes LINE enforce the `aud` check server-side, which is what stops a token
 * minted for somebody else's LINE channel being replayed against this app.
 */
async function verifyIdToken(idToken: string, nonce?: string): Promise<LineClaims | null> {
  if (!LOGIN_CHANNEL_ID) return null;
  const form = new URLSearchParams({ id_token: idToken, client_id: LOGIN_CHANNEL_ID });
  // Binds the token to the login attempt that requested it, so an id_token captured from an
  // earlier session cannot be replayed. Only enforced when the app sent a nonce.
  if (nonce) form.set("nonce", nonce);

  const res = await fetch("https://api.line.me/oauth2/v2.1/verify", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: form,
  });
  if (!res.ok) return null;
  const c = await res.json();
  if (!c?.sub || c?.iss !== "https://access.line.me") return null;
  return c as LineClaims;
}

/**
 * LINE only returns an email when the channel has been granted the email permission, which is a
 * one-off manual review in the LINE Developers console. Without it we still need a unique,
 * stable address for the Supabase user, so we synthesise one.
 *
 * `.invalid` is reserved by RFC 2606 and can never resolve, which is the point: a synthetic
 * address must not turn into a real mailbox if someone buys the domain later.
 */
const syntheticEmail = (sub: string) => `line.${sub.toLowerCase()}@line.invalid`;

/** A single-use magic-link token for [email], or null when no such user exists. */
async function magicToken(email: string): Promise<string | null> {
  const { data, error } = await admin.auth.admin.generateLink({ type: "magiclink", email });
  if (error || !data?.properties?.hashed_token) return null;
  return data.properties.hashed_token;
}

/**
 * Exchanges a magic-link token for a real session.
 *
 * `token_hash`, NOT `token`. generateLink returns a value that is ALREADY hashed; GoTrue hashes
 * whatever arrives in `token` before comparing, so sending it there compares a double hash
 * against a single one and never matches. The two fields look interchangeable and are not.
 *
 * Returns the reason alongside the session, because a bare null here produced
 * "could_not_exchange_token" with no way to tell a wrong field from a rejected email domain.
 */
async function exchange(token: string): Promise<{ session: any | null; reason: string }> {
  const res = await fetch(`${SUPABASE_URL}/auth/v1/verify`, {
    method: "POST",
    headers: { "Content-Type": "application/json", apikey: ANON_KEY },
    body: JSON.stringify({ type: "magiclink", token_hash: token }),
  });
  const body = await res.text();
  if (!res.ok) return { session: null, reason: `verify_${res.status}: ${body.slice(0, 160)}` };
  try {
    const s = JSON.parse(body);
    if (s?.access_token && s?.refresh_token) return { session: s, reason: "" };
    return { session: null, reason: `verify_no_tokens: ${body.slice(0, 160)}` };
  } catch {
    return { session: null, reason: `verify_unparseable: ${body.slice(0, 160)}` };
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);
  if (!LOGIN_CHANNEL_ID) return json({ error: "line_login_not_configured" }, 503);

  let idToken = "", nonce: string | undefined, accountType = "parent";
  try {
    const b = await req.json();
    idToken = String(b?.idToken ?? "");
    if (typeof b?.nonce === "string" && b.nonce) nonce = b.nonce;
    if (typeof b?.accountType === "string" && b.accountType) accountType = b.accountType;
  } catch { /* handled by the blank check below */ }
  if (!idToken) return json({ error: "missing_id_token" }, 400);

  const claims = await verifyIdToken(idToken, nonce);
  if (!claims) return json({ error: "line_token_rejected" }, 401);

  const lineUserId = claims.sub;

  // An existing link is the strongest signal we have: this LINE account has already proved it
  // belongs to a specific NongRak account, so honour that over anything in the token.
  const { data: existing } = await admin
    .from("line_links")
    .select("user_id")
    .eq("line_user_id", lineUserId)
    .maybeSingle();

  let email: string | null = null;

  if (existing?.user_id) {
    const { data: u } = await admin.auth.admin.getUserById(existing.user_id);
    email = u?.user?.email ?? null;
    if (!email) return json({ error: "linked_user_has_no_email" }, 500);
  } else {
    // A verified LINE email matching an existing account means the same person signed up with
    // email before. Reusing that account is what keeps their children attached to them; creating
    // a second account here is the classic duplicate-identity bug.
    email = claims.email?.trim().toLowerCase() || syntheticEmail(lineUserId);
    if (!(await magicToken(email))) {
      const { error: createErr } = await admin.auth.admin.createUser({
        email,
        email_confirm: true,          // no mailbox to confirm from, synthetic or not
        user_metadata: {
          full_name: claims.name ?? null,
          avatar_url: claims.picture ?? null,
          provider: "line",
        },
      });
      // Names what came back rather than a generic failure — this is the step that breaks when a
      // project rejects the .invalid domain, and a bare "sign-in failed" hides that completely.
      if (createErr) return json({ error: `create_user_failed: ${createErr.message}` }, 500);
    }
  }

  const token = await magicToken(email);
  if (!token) return json({ error: "could_not_issue_token" }, 500);

  const { session, reason } = await exchange(token);
  // Carries GoTrue's own words forward. The previous generic message is what made this bug take
  // a round trip to diagnose instead of being readable off the screen.
  if (!session) return json({ error: `could_not_exchange_token: ${reason}` }, 500);

  // Linking here means signing in with LINE also switches notifications on, with no second
  // pairing step. That is the main reason to prefer LINE Login over the code flow.
  const { error: linkErr } = await admin.from("line_links").upsert({
    user_id: session.user?.id,
    line_user_id: lineUserId,
    display_name: claims.name ?? null,
    notify_enabled: true,
    linked_at: new Date().toISOString(),
  }, { onConflict: "user_id" });
  // A failed link must not fail the sign-in — they are in; notifications can be paired later.
  const linked = !linkErr;

  return json({
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    email,
    linked,
    accountType,
  });
});
