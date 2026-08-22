// Daily vaccine reminders over LINE, at 3, 2 and 1 days before the scheduled date.
//
// Invoked once a day by a scheduler (Supabase Cron / pg_cron). JWT verification is ON: this
// spends a scarce push quota and reads every family's vaccine schedule, so it must not be
// callable by anyone who learns the URL.
//
// POST body: { "dryRun": true } to see exactly what WOULD be sent without spending quota or
// writing the dedup log. Worth using the first time, and after any change to the SQL.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const CHANNEL_TOKEN = Deno.env.get("LINE_CHANNEL_ACCESS_TOKEN") ?? "";

// Pushes left below which we stop sending reminders. The same quota carries urgent health
// alerts, and a month of routine vaccine reminders must never be the reason one cannot go out.
const RESERVE = 30;

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

const json = (b: unknown, status = 200) =>
  new Response(JSON.stringify(b), { status, headers: { "Content-Type": "application/json" } });

type Row = {
  vaccine_record_id: string;
  line_user_id: string;
  lang: string;
  child_name: string;
  vaccine_name: string;
  scheduled_date: string;
  days_before: number;
};

function message(r: Row): string {
  const th = r.lang !== "en";
  const when = r.days_before === 1
    ? (th ? "พรุ่งนี้" : "tomorrow")
    : (th ? `อีก ${r.days_before} วัน` : `in ${r.days_before} days`);
  return th
    ? [
        `เตือนความจำ: ${r.child_name} มีนัดวัคซีน${when}`,
        "",
        `วัคซีน: ${r.vaccine_name}`,
        `วันที่: ${r.scheduled_date}`,
        "",
        "ดูรายละเอียดทั้งหมดได้ในแอปน้องรัก",
      ].join("\n")
    : [
        `Reminder: ${r.child_name} has a vaccine appointment ${when}`,
        "",
        `Vaccine: ${r.vaccine_name}`,
        `Date: ${r.scheduled_date}`,
        "",
        "Full details are in the NongRak app.",
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

  const { data, error } = await db.rpc("line_due_vaccine_reminders");
  if (error) return json({ error: `rpc_failed: ${error.message}` }, 500);
  const rows = (data ?? []) as Row[];
  if (rows.length === 0) return json({ due: 0, sent: 0, dryRun });

  const remaining = await remainingQuota();
  if (dryRun) {
    return json({
      dryRun: true,
      due: rows.length,
      remaining,
      preview: rows.slice(0, 10).map((r) => ({ to: r.line_user_id, text: message(r) })),
    });
  }

  // Refuse the whole run rather than sending a partial batch into an exhausted quota: half a
  // family's reminders is worse than none, because the missing ones look like they were never due.
  if (remaining !== null && remaining - rows.length < RESERVE) {
    return json({
      due: rows.length, sent: 0, skipped: "quota_reserve",
      remaining, reserve: RESERVE,
    }, 200);
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
        failures.push(`${r.vaccine_record_id}:blocked`);
        continue;
      }
      if (!res.ok) { failures.push(`${r.vaccine_record_id}:${res.status}`); continue; }

      // Log ONLY after a confirmed send. Logging first would mean a transient LINE outage
      // permanently swallows that reminder — the dedup key makes it unrepeatable.
      await db.from("line_reminder_log").insert({
        vaccine_record_id: r.vaccine_record_id,
        days_before: r.days_before,
      });
      sent++;
    } catch (e) {
      failures.push(`${r.vaccine_record_id}:net`);
    }
  }

  return json({ due: rows.length, sent, failures, remaining });
});
