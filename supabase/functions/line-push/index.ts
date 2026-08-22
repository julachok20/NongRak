// Sends a NongRak notification to the caller's linked LINE account.
//
// Deployed normally (JWT verified) — unlike line-webhook:
//   supabase functions deploy line-push
//
// Scope is deliberately "push to yourself". The caller's own JWT decides the recipient, so
// there is no way to address someone else's LINE account by guessing a user id. Cross-user
// pushes (nanny logs a feed -> parent's LINE) need a shares-a-child authorisation check and
// are not built here.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const CHANNEL_TOKEN = Deno.env.get("LINE_CHANNEL_ACCESS_TOKEN") ?? "";
const MAX_LEN = 400;

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });

  const authHeader = req.headers.get("Authorization") ?? "";
  // The user's own JWT, not the service role: PostgREST then applies line_links RLS, so the
  // query can only ever return the caller's own row. That is the authorisation check —
  // there is no separate "is this my link?" branch to get wrong.
  const db = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );

  let text = "";
  try { text = String((await req.json())?.text ?? "").trim().slice(0, MAX_LEN); } catch { /* ignore */ }
  if (!text) return new Response(JSON.stringify({ error: "missing text" }), { status: 400 });

  const { data: link } = await db
    .from("line_links")
    .select("line_user_id, notify_enabled")
    .maybeSingle();

  // Not linked, or notifications switched off. Not an error: most users will never link, and
  // the caller should carry on writing the in-app notification either way.
  if (!link?.line_user_id || !link.notify_enabled) {
    return new Response(JSON.stringify({ sent: false, reason: "not_linked" }), {
      headers: { "Content-Type": "application/json" },
    });
  }

  const res = await fetch("https://api.line.me/v2/bot/message/push", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
    body: JSON.stringify({ to: link.line_user_id, messages: [{ type: "text", text }] }),
  });

  // 403 here means the user blocked the OA without LINE sending an unfollow we caught. Record
  // it so we stop paying for pushes into a void — the free tier is 200 messages a month.
  if (res.status === 403) {
    await db.from("line_links").update({ notify_enabled: false }).eq("line_user_id", link.line_user_id);
    return new Response(JSON.stringify({ sent: false, reason: "blocked" }), {
      headers: { "Content-Type": "application/json" },
    });
  }

  return new Response(JSON.stringify({ sent: res.ok }), {
    status: res.ok ? 200 : 502,
    headers: { "Content-Type": "application/json" },
  });
});
