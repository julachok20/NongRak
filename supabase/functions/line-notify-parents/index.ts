// "แจ้งเตือนด่วนไปยังคุณแม่ทันที" — a caregiver pushes one LINE message to the child's parents.
//
// line-push is deliberately push-to-yourself; this is the cross-user case it left out, so the
// authorisation it deferred lives here:
//   1. the caller must be a member of the child (checked with the caller's own JWT, so RLS and
//      is_child_member() decide — not this code);
//   2. recipients are only that child's parents (owner / co_owner / member), never the caller,
//      and only those who linked LINE and left notifications on;
//   3. a per-child daily cap, because the OA's free tier is 200 pushes a month in total and an
//      "urgent" switch a caregiver can leave on would spend it in days.
//
// Deploy with JWT verification on (the default):  supabase functions deploy line-notify-parents
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const CHANNEL_TOKEN = Deno.env.get("LINE_CHANNEL_ACCESS_TOKEN") ?? "";
const MAX_LEN = 400;
const DAILY_CAP_PER_CHILD = 5;
const PARENT_ROLES = ["owner", "co_owner", "member"];

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });

  const authHeader = req.headers.get("Authorization") ?? "";
  const asCaller = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
  });
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  let childId = "";
  let text = "";
  try {
    const body = await req.json();
    childId = String(body?.child_id ?? "").trim();
    text = String(body?.text ?? "").trim().slice(0, MAX_LEN);
  } catch { /* fall through to the checks below */ }
  if (!childId || !text) return json({ sent: false, reason: "missing child_id or text" }, 400);

  const { data: me } = await asCaller.auth.getUser();
  const callerId = me?.user?.id;
  if (!callerId) return json({ sent: false, reason: "not signed in" }, 401);

  const { data: isMember, error: memberErr } = await asCaller.rpc("is_child_member", { cid: childId });
  if (memberErr) return json({ sent: false, reason: `membership check failed: ${memberErr.message}` }, 500);
  if (isMember !== true) return json({ sent: false, reason: "not a member of this child" }, 403);

  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await admin
    .from("care_alert_log")
    .select("id", { count: "exact", head: true })
    .eq("child_id", childId)
    .gte("created_at", since);
  if ((count ?? 0) >= DAILY_CAP_PER_CHILD) {
    return json({ sent: false, reason: "daily_limit", limit: DAILY_CAP_PER_CHILD });
  }

  const { data: parents, error: parentsErr } = await admin
    .from("child_members")
    .select("user_id")
    .eq("child_id", childId)
    .in("role", PARENT_ROLES)
    .neq("user_id", callerId);
  if (parentsErr) return json({ sent: false, reason: `recipient lookup failed: ${parentsErr.message}` }, 500);

  const ids = (parents ?? []).map((p) => p.user_id);
  if (ids.length === 0) return json({ sent: false, reason: "no_parents" });

  const { data: links } = await admin
    .from("line_links")
    .select("user_id, line_user_id, notify_enabled")
    .in("user_id", ids);
  const targets = (links ?? []).filter((l) => l.line_user_id && l.notify_enabled);
  if (targets.length === 0) return json({ sent: false, reason: "no_linked_parent" });

  const { data: child } = await admin.from("children").select("name").eq("id", childId).maybeSingle();
  const message = `🔔 แจ้งเตือนด่วนเรื่องน้อง${child?.name ?? ""}\n${text}`.slice(0, 1000);

  let delivered = 0;
  for (const t of targets) {
    const res = await fetch("https://api.line.me/v2/bot/message/push", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
      body: JSON.stringify({ to: t.line_user_id, messages: [{ type: "text", text: message }] }),
    });
    if (res.ok) delivered++;
    // Blocked the OA: stop paying for pushes into a void, as line-push does.
    if (res.status === 403) {
      await admin.from("line_links").update({ notify_enabled: false }).eq("user_id", t.user_id);
    }
  }

  // Counted even when every push failed: an attempt still spent the caregiver's intent, and not
  // counting failures would let a broken link retry past the cap.
  await admin.from("care_alert_log").insert({ child_id: childId, sender_id: callerId, recipients: delivered });
  return json({ sent: delivered > 0, delivered, attempted: targets.length });
});
