// Auto-pushed LINE reminders for medicine doses and sleep times, at the moment they are due.
//
// Invoked every 30 minutes by pg_cron. JWT verification is ON: this spends a scarce push quota
// and reads every family's schedule, so it must not be callable by anyone who learns the URL.
//
// POST { "dryRun": true } shows exactly what WOULD be sent without spending quota or writing the
// dedup log. Use it after any change to the SQL.
//
// Vaccines are NOT handled here — line-vaccine-reminder already pushes those at 3, 2 and 1 days
// before. Duplicating them would send a parent two messages about the same appointment.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const CHANNEL_TOKEN = Deno.env.get("LINE_CHANNEL_ACCESS_TOKEN") ?? "";

// The window the SQL looks ahead by. Deliberately LONGER than the 30-minute cron period: with an
// exact 30, a run that starts a few seconds late leaves a gap, and the dose that falls in it is
// never reminded at all. The overlap is safe because line_care_reminder_log makes a second send
// impossible.
const WINDOW_MINUTES = 35;

// Pushes left below which routine reminders stop. The same quota carries urgent health alerts and
// support replies; a month of bedtime nudges must never be why one of those cannot go out.
const RESERVE = 30;

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { "Content-Type": "application/json" } });

type Row = {
  line_user_id: string;
  lang: string;
  kind: "medicine" | "sleep";
  ref_id: string;
  slot: string;
  child_name: string;
  title: string;
  at_local: string;
};

function message(r: Row): string {
  const th = r.lang !== "en";
  if (r.kind === "medicine") {
    return th
      ? [
          `ถึงเวลายาของ ${r.child_name} แล้วค่ะ`,
          "",
          `ยา: ${r.title}`,
          `เวลา: ${r.at_local} น.`,
          "",
          "กดบันทึกว่ากินแล้วได้ในแอปน้องรัก",
        ].join("\n")
      : [
          `Time for ${r.child_name}'s medicine`,
          "",
          `Medicine: ${r.title}`,
          `Time: ${r.at_local}`,
          "",
          "Mark it as taken in the NongRak app.",
        ].join("\n");
  }
  return th
    ? [
        `ใกล้ถึงเวลานอนของ ${r.child_name} แล้วค่ะ`,
        "",
        `${r.title} เวลา ${r.at_local} น.`,
        "",
        "เริ่มเตรียมตัวเข้านอนได้เลยค่ะ",
      ].join("\n")
    : [
        `${r.child_name}'s sleep time is coming up`,
        "",
        `${r.title} at ${r.at_local}`,
        "",
        "Time to start winding down.",
      ].join("\n");
}

/** Pushes remaining this month, or null when LINE will not say. */
async function remainingQuota(): Promise<number | null> {
  try {
    const h = { Authorization: `Bearer ${CHANNEL_TOKEN}` };
    const [q, c] = await Promise.all([
      fetch("https://api.line.me/v2/bot/message/quota", { headers: h }),
      fetch("https://api.line.me/v2/bot/message/quota/consumption", { headers: h }),
    ]);
    if (!q.ok || !c.ok) return null;
    const quota = await q.json(), used = await c.json();
    // type "none" means an unlimited plan; nothing to ration.
    if (quota?.type !== "limited") return Number.MAX_SAFE_INTEGER;
    return Math.max(0, (quota.value ?? 0) - (used.totalUsage ?? 0));
  } catch { return null; }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "POST only" }, 405);

  let dryRun = false;
  try { dryRun = !!(await req.json())?.dryRun; } catch { /* default false */ }

  const { data, error } = await db.rpc("line_due_care_reminders", {
    p_window_minutes: WINDOW_MINUTES,
  });
  if (error) return json({ error: `rpc_failed: ${error.message}` }, 500);
  const rows = (data ?? []) as Row[];
  if (rows.length === 0) return json({ due: 0, sent: 0, dryRun });

  const remaining = await remainingQuota();
  if (dryRun) {
    return json({
      dryRun: true,
      due: rows.length,
      remaining,
      preview: rows.slice(0, 10).map((r) => ({ to: r.line_user_id, kind: r.kind, text: message(r) })),
    });
  }

  // Refuse the whole run rather than send a partial batch into an exhausted quota: half a
  // family's reminders is worse than none, because the missing ones look like they were never due.
  if (remaining !== null && remaining - rows.length < RESERVE) {
    return json({ due: rows.length, sent: 0, skipped: "quota_reserve", remaining, reserve: RESERVE });
  }

  let sent = 0;
  const failures: string[] = [];
  for (const r of rows) {
    try {
      const res = await fetch("https://api.line.me/v2/bot/message/push", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
        body: JSON.stringify({ to: r.line_user_id, messages: [{ type: "text", text: message(r) }] }),
      });

      if (res.status === 403) {
        // Blocked the OA without an unfollow event reaching us. Stop pushing into a void.
        await db.from("line_links")
          .update({ notify_enabled: false }).eq("line_user_id", r.line_user_id);
        failures.push(`${r.kind}:${r.ref_id}:blocked`);
        continue;
      }
      if (!res.ok) { failures.push(`${r.kind}:${r.ref_id}:${res.status}`); continue; }

      // Logged ONLY after a confirmed send. Logging first would mean a transient LINE outage
      // permanently swallows that reminder — the dedup key makes it unrepeatable.
      await db.from("line_care_reminder_log")
        .insert({ kind: r.kind, ref_id: r.ref_id, slot: r.slot });
      sent++;
    } catch {
      failures.push(`${r.kind}:${r.ref_id}:net`);
    }
  }

  return json({ due: rows.length, sent, failures, remaining });
});
