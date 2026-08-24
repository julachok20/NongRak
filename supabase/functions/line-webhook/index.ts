// LINE Messaging API webhook — inbound events from the NongRak OA.
//
// MUST be deployed public (verify_jwt false): LINE calls it with its own signature and no
// Supabase JWT. With JWT verification on, LINE gets 401 on every delivery and the console just
// reports the webhook as unreachable.
//
// Handles: follow / unfollow / text messages / postbacks.
// Text covers three things through one router — pairing codes, slash commands, and free
// questions — because rich-menu buttons send plain text, so a tap and a typed command are the
// same event and must not drift apart.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";
import { encodeBase64 } from "jsr:@std/encoding/base64";

const CHANNEL_SECRET = Deno.env.get("LINE_CHANNEL_SECRET") ?? "";
const CHANNEL_TOKEN = Deno.env.get("LINE_CHANNEL_ACCESS_TOKEN") ?? "";

const db = createClient(
  Deno.env.get("SUPABASE_URL")!,
  // Service role: the webhook is the only party that has actually heard from LINE, so it is the
  // only one allowed to assert "this line_user_id is real". RLS grants no client insert.
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
);

// ---------------------------------------------------------------------------
// Signature
// ---------------------------------------------------------------------------

/**
 * LINE signs the raw request body with the channel secret. This is the entire security boundary
 * of the endpoint: without it anyone who learns the URL can post a forged `message` event
 * carrying a stolen pairing code and bind their own LINE account to someone else's child data.
 */
async function verify(rawBody: string, signature: string | null): Promise<boolean> {
  if (!signature || !CHANNEL_SECRET) return false;
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(CHANNEL_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const mac = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(rawBody));
  const expected = btoa(String.fromCharCode(...new Uint8Array(mac)));
  // Length-independent compare so a wrong signature cannot be narrowed down by timing.
  if (expected.length !== signature.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ signature.charCodeAt(i);
  return diff === 0;
}

// ---------------------------------------------------------------------------
// LINE messaging
// ---------------------------------------------------------------------------

/** Replies are FREE — only pushes count against the 300-a-month quota. */
async function send(replyToken: string, messages: unknown[]) {
  await fetch("https://api.line.me/v2/bot/message/reply", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
    body: JSON.stringify({ replyToken, messages }),
  }).catch(() => {});
}

const reply = (replyToken: string, text: string) =>
  send(replyToken, [{ type: "text", text }]);

async function profile(lineUserId: string): Promise<{ displayName?: string; language?: string }> {
  try {
    const r = await fetch(`https://api.line.me/v2/bot/profile/${lineUserId}`, {
      headers: { Authorization: `Bearer ${CHANNEL_TOKEN}` },
    });
    return r.ok ? await r.json() : {};
  } catch { return {}; }
}

// ---------------------------------------------------------------------------
// Language
// ---------------------------------------------------------------------------

type Lang = "th" | "en";

/**
 * Resolution order: an explicit choice, then the phone's own LINE language, then Thai.
 *
 * The stored preference always wins — someone who typed /thai on an English phone meant it, and
 * silently overriding that on the next message would look like the command never worked.
 */
async function langFor(lineUserId: string): Promise<Lang> {
  const { data } = await db.from("line_prefs")
    .select("lang").eq("line_user_id", lineUserId).maybeSingle();
  if (data?.lang === "en" || data?.lang === "th") return data.lang;
  const p = await profile(lineUserId);
  return p.language?.toLowerCase().startsWith("en") ? "en" : "th";
}

/**
 * Swaps the user's rich menu to match their language.
 *
 * Per-user linking, not a new default: changing the default would flip the menu for every
 * follower, so one person typing /eng would put the whole OA into English.
 *
 * Best effort. If this fails the language still changed and replies are already in the new
 * language — a stale menu is a cosmetic problem, and failing the whole command over it would
 * turn a cosmetic problem into a broken one.
 */
async function linkMenu(lineUserId: string, lang: Lang) {
  try {
    const { data } = await db.from("line_rich_menus")
      .select("rich_menu_id").eq("lang", lang).maybeSingle();
    if (!data?.rich_menu_id) return;
    await fetch(
      `https://api.line.me/v2/bot/user/${lineUserId}/richmenu/${data.rich_menu_id}`,
      { method: "POST", headers: { Authorization: `Bearer ${CHANNEL_TOKEN}` } },
    );
  } catch { /* cosmetic only */ }
}

async function setLang(lineUserId: string, lang: Lang) {
  await db.from("line_prefs").upsert(
    { line_user_id: lineUserId, lang, updated_at: new Date().toISOString() },
    { onConflict: "line_user_id" },
  );
  await linkMenu(lineUserId, lang);
}

/** Every user-visible string, both languages side by side so neither can be forgotten. */
const S = {
  help: {
    th: [
      "น้องรัก AI ช่วยอะไรได้บ้าง",
      "",
      "พิมพ์คำถามเรื่องลูกได้เลย หรือเล่าสิ่งที่ลูกทำ เช่น",
      "",
      "ลูกกินนม 100 มล",
      "ลูกกำลังนอน",
      "ลูกตื่นแล้ว",
      "",
      "คำสั่ง",
      "/vaccine  วัคซีนเข็มต่อไป",
      "/children  รายชื่อลูก",
      "/link  เชื่อมบัญชีกับแอป",
      "/unlink  ยกเลิกการเชื่อมต่อ",
      "/eng  เปลี่ยนเป็นภาษาอังกฤษ",
      "/help  เมนูนี้",
    ].join("\n"),
    en: [
      "What NongRak AI can do",
      "",
      "Ask anything about your child, or just say what they are doing — e.g.",
      "",
      "baby drank 100 ml",
      "Giga is sleeping",
      "she woke up",
      "",
      "Commands",
      "/vaccine  next vaccine due",
      "/children  your children",
      "/link  connect your app account",
      "/unlink  disconnect",
      "/thai  switch to Thai",
      "/help  this menu",
    ].join("\n"),
  },
  link: {
    th: [
      "วิธีเชื่อมบัญชี",
      "",
      "1. เปิดแอปน้องรัก",
      "2. ไปที่ ตั้งค่า แล้วเลือก เชื่อมต่อ LINE Official Account",
      "3. กด ขอรหัสเชื่อมต่อ",
      "4. ส่งรหัสนั้นมาที่แชทนี้",
      "",
      "หรือเข้าสู่ระบบด้วย LINE ในแอป จะเชื่อมให้อัตโนมัติเลยค่ะ",
    ].join("\n"),
    en: [
      "How to connect your account",
      "",
      "1. Open the NongRak app",
      "2. Go to Settings and choose Connect LINE Official Account",
      "3. Tap Get a linking code",
      "4. Send that code to this chat",
      "",
      "Or sign in with LINE in the app and it connects automatically.",
    ].join("\n"),
  },
  langChanged: { th: "เปลี่ยนเป็นภาษาไทยแล้วค่ะ", en: "Switched to English." },
  noChildren: {
    th: "ยังไม่พบข้อมูลลูกในบัญชีนี้ค่ะ กรุณาเพิ่มข้อมูลลูกในแอปก่อน",
    en: "No children found on this account yet. Please add one in the app first.",
  },
  nextVaccine: { th: "วัคซีนเข็มต่อไป", en: "Next vaccine due" },
  noVaccine: { th: "ยังไม่มีนัดวัคซีนที่บันทึกไว้", en: "no scheduled dose recorded" },
  yourChildren: { th: "ลูกของคุณ", en: "Your children" },
  askPrompt: {
    th: "ถามได้เลยค่ะ พิมพ์คำถามเรื่องลูกมาได้เลย เช่น ลูก 8 เดือนนอนกี่ชั่วโมง",
    en: "Go ahead — ask anything about your child, e.g. how many hours should an 8-month-old sleep?",
  },
  aiDown: {
    th: "ตอนนี้ระบบผู้ช่วยไม่พร้อมค่ะ กรุณาลองใหม่อีกครั้ง",
    en: "The assistant is unavailable right now. Please try again.",
  },
  welcome: {
    th: "ยินดีต้อนรับสู่น้องรัก AI ค่ะ พิมพ์คำถามเรื่องลูกได้เลย หรือพิมพ์ /help เพื่อดูคำสั่งทั้งหมด",
    en: "Welcome to NongRak AI. Ask anything about your child, or type /help to see all commands.",
  },
  codeBad: {
    th: "รหัสนี้ใช้ไม่ได้หรือหมดอายุแล้วค่ะ กรุณาขอรหัสใหม่ในแอปน้องรัก",
    en: "That code is invalid or expired. Please request a new one in the NongRak app.",
  },
  codeTaken: {
    th: "บัญชี LINE นี้เชื่อมกับบัญชีน้องรักอื่นอยู่แล้วค่ะ กรุณายกเลิกการเชื่อมต่อในบัญชีเดิมก่อน",
    en: "This LINE account is already linked to another NongRak account. Please unlink it there first.",
  },
  paired: {
    th: "เชื่อมต่อสำเร็จแล้วค่ะ ตั้งแต่นี้น้องรักจะส่งการแจ้งเตือนมาที่ LINE นี้ ปิดได้ตลอดในหน้าตั้งค่าของแอปค่ะ",
    en: "Connected. NongRak will send notifications to this LINE from now on — you can turn that off any time in the app settings.",
  },
  unlinkAsk: {
    th: "ต้องการยกเลิกการเชื่อมต่อบัญชีนี้ใช่หรือไม่ หลังยกเลิกจะไม่ได้รับการแจ้งเตือนทาง LINE อีก",
    en: "Disconnect this account? You will stop receiving NongRak notifications on LINE.",
  },
  unlinkYes: { th: "ยกเลิกการเชื่อมต่อ", en: "Disconnect" },
  unlinkNo: { th: "ไม่ใช่", en: "Cancel" },
  unlinkDone: {
    th: "ยกเลิกการเชื่อมต่อแล้วค่ะ เชื่อมใหม่ได้ตลอดโดยพิมพ์ /link",
    en: "Disconnected. You can reconnect any time with /link.",
  },
  unlinkCancelled: { th: "ยกเลิกคำสั่งแล้วค่ะ", en: "Cancelled — nothing changed." },
  notLinked: {
    th: "บัญชีนี้ยังไม่ได้เชื่อมกับแอปค่ะ",
    en: "This LINE account is not connected to the app yet.",
  },
  recSaved: { th: "บันทึกเรียบร้อยแล้วค่ะ", en: "Saved." },
  recFailed: {
    th: "บันทึกไม่สำเร็จค่ะ กรุณาบันทึกในแอปแทน",
    en: "Could not save that. Please record it in the app instead.",
  },
  recExpired: {
    th: "รายการนี้หมดอายุแล้วค่ะ กรุณาพิมพ์ใหม่อีกครั้ง",
    en: "That request expired. Please send it again.",
  },
  recCancelled: { th: "ไม่ได้บันทึกค่ะ", en: "Not saved." },
  supportPrompt: {
    th: "พิมพ์ข้อความที่ต้องการส่งถึงทีมงานได้เลยค่ะ ข้อความถัดไปของคุณจะถูกส่งให้ทีมงาน",
    en: "Type your message for the team. Your next message will be sent to us.",
  },
  supportNeedsLink: {
    th: "กรุณาเชื่อมบัญชีกับแอปก่อนส่งข้อความถึงทีมงาน พิมพ์ /link เพื่อดูวิธี",
    en: "Please connect your app account first. Type /link to see how.",
  },
  supportSent: {
    th: "ส่งข้อความให้ทีมงานแล้วค่ะ ทีมงานจะตอบกลับทาง LINE นี้",
    en: "Sent to the team. They will reply here on LINE.",
  },
  replyUsage: { th: "ใช้: /reply <code> <ข้อความ>", en: "Usage: /reply <code> <message>" },
  replyNotFound: { th: "ไม่พบข้อความรหัสนี้", en: "No message with that code." },
  replySent: { th: "ส่งคำตอบแล้ว", en: "Reply sent." },
  replyFailed: { th: "ส่งคำตอบไม่สำเร็จ", en: "Could not deliver the reply." },
  clearDone: {
    th: "ล้างรายการที่ค้างอยู่แล้วค่ะ เริ่มใหม่ได้เลย",
    en: "Cleared any pending action. You can start again.",
  },
  clearNote: {
    th: "หมายเหตุ: น้องรักลบข้อความในหน้าแชทให้ไม่ได้ LINE ไม่เปิดให้บอททำ\n\nถ้าต้องการลบเอง แตะปุ่มสามขีด (☰) มุมขวาบน > ตั้งค่า > ลบข้อมูล > ลบข้อมูลแชทและข้อความทั้งหมด",
    en: "Note: I cannot delete the chat history — LINE gives bots no way to do that.\n\nTo clear it yourself: tap the three bars (☰) top right > Settings > Delete Data > Delete all chat data and messages.",
  },
  inboxEmpty: {
    th: "ไม่มีข้อความที่รอตอบค่ะ",
    en: "No messages waiting for a reply.",
  },
  whoami: {
    th: "รหัสผู้ใช้ LINE ของคุณคือ\n\n",
    en: "Your LINE user id is\n\n",
  },
  otpClaimed: {
    th: "รับรหัสแล้วค่ะ ขั้นตอนสุดท้าย เปิดแอปน้องรักบนมือถือของคุณแล้วกดยืนยันการเชื่อมต่อ",
    en: "Code accepted. Last step: open the NongRak app on your phone and approve the connection.",
  },
  unknownCmd: {
    th: "ไม่รู้จักคำสั่งนี้ค่ะ พิมพ์ /help เพื่อดูคำสั่งทั้งหมด",
    en: "Unknown command. Type /help to see everything I can do.",
  },
} as const;

const t = (key: keyof typeof S, lang: Lang) => S[key][lang];

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

/** The NongRak account this LINE user is linked to, or null. */
async function linkedUserId(lineUserId: string): Promise<string | null> {
  const { data } = await db.from("line_links")
    .select("user_id").eq("line_user_id", lineUserId).maybeSingle();
  return data?.user_id ?? null;
}

/**
 * Staff check, keyed on the LINE id alone.
 *
 * Deliberately NOT line_links.is_admin: that table is keyed on a NongRak user_id with a foreign
 * key to auth.users, so it could only ever mark a registered parent as staff. A support admin
 * needs to answer messages, not to have a child.
 */
async function isAdmin(lineUserId: string): Promise<boolean> {
  const { data } = await db.from("line_admins")
    .select("line_user_id").eq("line_user_id", lineUserId).maybeSingle();
  return !!data;
}

function ageText(birthdate: string | null | undefined, lang: Lang): string {
  if (!birthdate) return "";
  const b = new Date(birthdate);
  if (isNaN(b.getTime())) return "";
  const months = Math.max(0, Math.floor((Date.now() - b.getTime()) / (1000 * 60 * 60 * 24 * 30.44)));
  if (months < 24) return lang === "th" ? `${months} เดือน` : `${months} months`;
  const y = Math.floor(months / 12), m = months % 12;
  return lang === "th" ? `${y} ปี ${m} เดือน` : `${y}y ${m}m`;
}

async function childrenFor(userId: string) {
  const { data } = await db.from("children").select("id, name, birthdate").eq("user_id", userId);
  return data ?? [];
}

/**
 * The next dose actually due, straight from vaccine_records.
 *
 * Read, never generated. A wrong vaccine date is a medical error, and a model asked to guess one
 * will produce something plausible every single time. No record means we say so.
 */
async function nextVaccineText(userId: string, lang: Lang): Promise<string> {
  const kids = await childrenFor(userId);
  if (kids.length === 0) return t("noChildren", lang);
  const lines: string[] = [];
  for (const kid of kids) {
    const { data } = await db.from("vaccine_records")
      .select("vaccine_name, scheduled_date")
      .eq("child_id", kid.id)
      .not("scheduled_date", "is", null)
      .is("date_given", null)
      .order("scheduled_date", { ascending: true })
      .limit(1);
    const v = data?.[0];
    lines.push(v
      ? `${kid.name}: ${v.vaccine_name} — ${v.scheduled_date}`
      : `${kid.name}: ${t("noVaccine", lang)}`);
  }
  return [t("nextVaccine", lang), "", ...lines].join("\n");
}

async function childrenText(userId: string, lang: Lang): Promise<string> {
  const kids = await childrenFor(userId);
  if (kids.length === 0) return t("noChildren", lang);
  return [t("yourChildren", lang), "", ...kids.map((k) => {
    const age = ageText(k.birthdate, lang);
    return age ? `${k.name} (${age})` : `${k.name}`;
  })].join("\n");
}

// ---------------------------------------------------------------------------
// Guests (followers who have not linked a NongRak account)
//
// A guest can chat about the app and ask a little about child care. What they cannot have is the
// personalised half: tips matched to their child's age, records, schedules, reminders. Those all
// need a child, and a child needs an account.
//
// The cap exists because every AI call costs money and a follower who never links is an unbounded
// expense. It is per Bangkok day, so it resets overnight rather than locking anyone out for good.
// ---------------------------------------------------------------------------

const GUEST_DAILY_LIMIT = 5;

/** Bytes. Above this we decline rather than push a huge base64 payload through the AI chain. */
const MAX_IMAGE_BYTES = 4 * 1024 * 1024;

/**
 * Spends one of today's guest allowance and returns the NEW count.
 *
 * The increment happens inside Postgres, so two messages arriving together cannot both read the
 * old value and each be let through.
 */
async function guestTake(lineUserId: string): Promise<number> {
  const { data, error } = await db.rpc("line_guest_take", { p_line_user_id: lineUserId });
  // Fail OPEN on a database error: refusing to answer because a counter is unavailable is a
  // worse failure than letting one extra question through.
  if (error) return 0;
  return Number(data ?? 0);
}

/** The narrower brief a guest's questions are answered under. */
function guestContext(lang: Lang): string {
  return lang === "en"
    ? "Answer in English in at most 2 short sentences. You may explain what the NongRak app does, " +
      "and give brief general information about caring for a child. Do NOT give detailed or " +
      "personalised medical advice, dosages, or a diagnosis; for anything specific, say the " +
      "parent should connect their NongRak account in the app. "
    : "ตอบเป็นภาษาไทยไม่เกิน 2 ประโยคสั้นๆ ตอบได้เรื่องแอปน้องรักทำอะไรได้บ้าง " +
      "และข้อมูลทั่วไปเรื่องการเลี้ยงลูกแบบสั้นๆ ห้ามให้คำแนะนำทางการแพทย์แบบเจาะจง " +
      "ห้ามบอกขนาดยา ห้ามวินิจฉัยโรค ถ้าเป็นเรื่องเฉพาะของลูก ให้บอกว่าเชื่อมบัญชีในแอปน้องรักก่อน ";
}

/** Shared footer: the one button that lifts every guest limit. */
const linkButton = (lang: Lang) => ({
  type: "button", style: "primary", color: "#2743C4", height: "sm",
  action: {
    type: "message",
    label: lang === "en" ? "How to connect" : "วิธีเชื่อมบัญชี",
    text: "/link",
  },
});

/** Shown when a guest reaches for something that needs a child on file. */
function guestLockedCard(lang: Lang) {
  const th = lang !== "en";
  return {
    type: "flex",
    altText: th ? "ต้องเชื่อมบัญชีก่อน" : "Connect your account first",
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#FFF3E0", paddingAll: "14px",
        contents: [{
          type: "text", text: th ? "เฉพาะบัญชีที่เชื่อมแล้ว" : "For connected accounts",
          weight: "bold", size: "sm", color: "#B26A00", wrap: true,
        }],
      },
      body: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "16px",
        contents: [
          { type: "text",
            text: th
              ? "เคล็ดลับเลี้ยงลูกจะเลือกให้ตรงกับอายุของลูกคุณ จึงต้องเชื่อมบัญชีก่อนค่ะ"
              : "Parenting tips are matched to your child's age, so they need a connected account.",
            size: "sm", color: "#3C342C", wrap: true },
          { type: "separator", margin: "md" },
          { type: "text",
            text: th
              ? "เชื่อมแล้วจะได้ เคล็ดลับตามวัย บันทึกผ่านแชท เตือนวัคซีน ยา และเวลานอน"
              : "Once connected you get age-matched tips, recording by chat, and reminders for vaccines, medicine and sleep.",
            size: "xs", color: "#9C9389", wrap: true },
        ],
      },
      footer: {
        type: "box", layout: "vertical", paddingAll: "12px",
        contents: [linkButton(lang)],
      },
    },
  };
}

/** Shown once a guest has spent today's allowance. */
function guestLimitCard(lang: Lang) {
  const th = lang !== "en";
  return {
    type: "flex",
    altText: th ? "ถามครบจำนวนของวันนี้แล้ว" : "Daily question limit reached",
    contents: {
      type: "bubble",
      body: {
        type: "box", layout: "vertical", spacing: "md", paddingAll: "20px",
        contents: [
          { type: "text",
            text: th ? "วันนี้ถามครบแล้วค่ะ" : "That is today's questions used up",
            weight: "bold", size: "lg", color: "#2743C4", wrap: true },
          { type: "text",
            text: th
              ? `บัญชีที่ยังไม่เชื่อมถามได้วันละ ${GUEST_DAILY_LIMIT} คำถาม พรุ่งนี้ถามใหม่ได้ค่ะ`
              : `Accounts that are not connected can ask ${GUEST_DAILY_LIMIT} questions a day. It resets tomorrow.`,
            size: "sm", color: "#7A7168", wrap: true },
          { type: "text",
            text: th
              ? "เชื่อมบัญชีกับแอปน้องรักแล้วถามได้ไม่จำกัดค่ะ"
              : "Connect your NongRak account and ask as much as you like.",
            size: "sm", color: "#3C342C", wrap: true },
        ],
      },
      footer: {
        type: "box", layout: "vertical", paddingAll: "12px",
        contents: [linkButton(lang)],
      },
    },
  };
}

/**
 * Charges a guest one question and reports whether they are over the line.
 * Linked accounts are never counted, so this is a no-op for them.
 */
async function guestOverLimit(lineUserId: string, userId: string | null): Promise<boolean> {
  if (userId) return false;
  return (await guestTake(lineUserId)) > GUEST_DAILY_LIMIT;
}

// ---------------------------------------------------------------------------
// Photos and stickers
// ---------------------------------------------------------------------------

/**
 * A photo the parent sent, answered by the same AI chain the app uses.
 *
 * The binary lives on api-data.line.me, NOT api.line.me — the messaging host returns 404 for
 * content, which reads like a missing message rather than a wrong hostname.
 */
async function onImage(lineUserId: string, messageId: string, replyToken: string) {
  const lang = await langFor(lineUserId);
  const userId = await linkedUserId(lineUserId);
  const th = lang !== "en";

  // Vision costs more than text, so a photo spends from the same daily allowance.
  if (await guestOverLimit(lineUserId, userId)) {
    return send(replyToken, [guestLimitCard(lang)]);
  }

  let b64 = "", mime = "image/jpeg";
  try {
    const res = await fetch(`https://api-data.line.me/v2/bot/message/${messageId}/content`, {
      headers: { Authorization: `Bearer ${CHANNEL_TOKEN}` },
      signal: AbortSignal.timeout(20_000),
    });
    if (!res.ok) return reply(replyToken, t("aiDown", lang));
    const bytes = new Uint8Array(await res.arrayBuffer());
    if (bytes.byteLength > MAX_IMAGE_BYTES) {
      return reply(replyToken, th
        ? "รูปนี้ใหญ่เกินไปค่ะ ลองส่งรูปที่เล็กลงหรือถ่ายใหม่อีกครั้งนะคะ"
        : "That photo is too large. Please send a smaller one.");
    }
    mime = res.headers.get("content-type")?.split(";")[0] || "image/jpeg";
    // Chunked by @std/encoding — btoa(String.fromCharCode(...bytes)) overflows the argument
    // limit and throws on anything above roughly 100 KB.
    b64 = encodeBase64(bytes);
  } catch {
    return reply(replyToken, t("aiDown", lang));
  }

  // Guests get the same narrow brief as their text questions: a photo must not become a way
  // around the limits on medical advice.
  const brief = userId
    ? (th
        ? "คุณคือน้องรัก ผู้ช่วยสำหรับพ่อแม่ ดูรูปนี้แล้วตอบสั้นๆ ไม่เกิน 3 ประโยค " +
          "บอกสิ่งที่เห็นและคำแนะนำที่ทำได้จริง ถ้าเป็นเรื่องที่ต้องพบแพทย์ให้บอกตรงๆ " +
          "ห้ามวินิจฉัยโรคจากรูป "
        : "You are NongRak, an assistant for parents. Look at this photo and answer in at most " +
          "3 short sentences: what you see and one practical suggestion. Say plainly if it needs " +
          "a doctor. Never diagnose from a photo. ")
    : guestContext(lang);

  try {
    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/ai-generate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
      },
      body: JSON.stringify({
        prompt: brief + (th ? "รูปนี้คืออะไร" : "What is in this photo?"),
        imageBase64: b64,
        imageMimeType: mime,
        maxTokens: 260,
        useSearch: false,
      }),
      // Vision is slower than text; the 20s used elsewhere times out on a large photo.
      signal: AbortSignal.timeout(40_000),
    });
    if (!res.ok) return reply(replyToken, t("aiDown", lang));
    const txt = (await res.json())?.text;
    if (typeof txt !== "string" || !txt.trim()) return reply(replyToken, t("aiDown", lang));

    const msgs: unknown[] = [{ type: "text", text: txt.trim() }];
    if (!userId) msgs.push(guestLockedCard(lang));
    return send(replyToken, msgs);
  } catch {
    return reply(replyToken, t("aiDown", lang));
  }
}

/**
 * A sticker. Answered with words, never with another sticker: an invalid packageId/stickerId
 * makes LINE reject the WHOLE reply, so a decorative flourish would cost the actual response.
 *
 * LINE sends `keywords` describing the sticker on most packages. Using them means the reply
 * matches the mood instead of being the same canned line every time.
 */
async function onSticker(lineUserId: string, message: any, replyToken: string) {
  const lang = await langFor(lineUserId);
  const th = lang !== "en";
  const words: string[] = Array.isArray(message?.keywords) ? message.keywords.slice(0, 5) : [];

  // No keywords means no signal worth spending an AI call — and no guest allowance — on.
  if (words.length === 0) {
    return reply(replyToken, th
      ? "ขอบคุณค่ะ มีอะไรให้น้องรักช่วยเรื่องลูกไหมคะ"
      : "Thank you. Anything I can help you with about your child?");
  }

  const userId = await linkedUserId(lineUserId);
  if (await guestOverLimit(lineUserId, userId)) {
    return send(replyToken, [guestLimitCard(lang)]);
  }

  const brief = th
    ? `ผู้ใช้ส่งสติกเกอร์ที่สื่อความรู้สึกว่า ${words.join(", ")} ` +
      "ตอบกลับอบอุ่นเป็นกันเองไม่เกิน 1 ประโยค แล้วชวนคุยเรื่องลูกต่อสั้นๆ "
    : `The user sent a sticker expressing: ${words.join(", ")}. ` +
      "Reply warmly in at most 1 sentence, then briefly invite them to talk about their child. ";

  try {
    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/ai-generate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
      },
      body: JSON.stringify({ prompt: brief, maxTokens: 120, useSearch: false }),
      signal: AbortSignal.timeout(15_000),
    });
    const txt = res.ok ? (await res.json())?.text : null;
    return reply(replyToken, typeof txt === "string" && txt.trim()
      ? txt.trim()
      : (th ? "ขอบคุณค่ะ" : "Thank you!"));
  } catch {
    return reply(replyToken, th ? "ขอบคุณค่ะ" : "Thank you!");
  }
}

/**
 * Answers a free-text question through the same ai-generate function the app uses, so LINE and
 * the app give the same advice from the same persona rather than drifting apart.
 */
async function askAi(question: string, userId: string | null, lang: Lang): Promise<string> {
  // Brevity goes in the PROMPT, not a system override: ai-generate turns jsonMode on by default
  // whenever a custom system is supplied, which would return JSON instead of prose.
  //
  // A LINE bubble is a few lines on a phone held one-handed over a cot. A long answer is not
  // more helpful there, it is unread.
  let context = lang === "en"
    ? "Answer in English, in at most 3 short sentences. Give the key point only, no preamble. "
    : "ตอบสั้นๆ ไม่เกิน 3 ประโยค เอาเฉพาะใจความสำคัญ ไม่ต้องเกริ่นนำ ";
  // A guest has no child on file, so there is nothing to personalise with — and personalised
  // medical advice is exactly what they must not get before linking.
  if (!userId) {
    context = guestContext(lang);
  } else {
    const kids = await childrenFor(userId);
    if (kids.length > 0) {
      context += (lang === "th" ? "ข้อมูลลูกของผู้ถาม: " : "The asker's children: ") +
        kids.map((k) => `${k.name} ${ageText(k.birthdate, lang) || "?"}`).join(", ") + ". ";
    }
  }
  try {
    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/ai-generate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
      },
      // LINE truncates long bubbles awkwardly, so keep answers phone-readable.
      body: JSON.stringify({ prompt: context + question, maxTokens: 220, useSearch: false }),
      signal: AbortSignal.timeout(20_000),
    });
    if (!res.ok) return t("aiDown", lang);
    const txt = (await res.json())?.text;
    return typeof txt === "string" && txt.trim() ? txt.trim() : t("aiDown", lang);
  } catch {
    return t("aiDown", lang);
  }
}

// ---------------------------------------------------------------------------
// Recording from chat
//
// A parent types "ลูกกินนม 100 ml" and it becomes a milk_log — but only after they confirm.
// Nothing here writes on a parse alone: a misread message would put wrong health data into a
// child's record, with no screen to notice it against.
// ---------------------------------------------------------------------------

/** Allowlists. A parse that invents a value must fall back, never write an unknown one. */
const ENUMS = {
  milk_type: ["breast", "formula", "mixed"],
  meal_type: ["breakfast", "lunch", "dinner", "snack", "other"],
  sleep_type: ["main", "nap"],
  waste_type: ["stool", "urine"],
  amount: ["small", "medium", "large"],
} as const;

const pick = (k: keyof typeof ENUMS, v: unknown, fallback: string): string =>
  (ENUMS[k] as readonly string[]).includes(String(v)) ? String(v) : fallback;

const PARSE_SYSTEM = [
  "You convert one short parenting message into JSON. Reply with JSON only, no prose.",
  "Fields: intent (record|question), type (milk|food|sleep|sleep_end|waste|null),",
  "child (name or null), milk_type (breast|formula|mixed), volume_ml (integer),",
  "meal_type (breakfast|lunch|dinner|snack|other), item_name (string),",
  "sleep_state (ongoing|finished), hours (number),",
  "waste_type (stool|urine), amount (small|medium|large).",
  "Use intent record whenever the message DESCRIBES something the child did or is doing now,",
  "even briefly or vaguely. Being unsure is fine: the parent confirms before anything is saved,",
  "so lean towards record.",
  "Use intent question only when the message ASKS for information or advice.",
  "type sleep with sleep_state ongoing means the child is asleep right now (started, not finished).",
  "type sleep with sleep_state finished means a sleep that already ended; set hours.",
  "type sleep_end means the child just woke up.",
].join(" ");

/**
 * Classifies a message and extracts fields. Returns null for anything not clearly a record —
 * ambiguous cases belong in the question path, where being wrong is cheap.
 */
async function classify(text: string): Promise<any | null> {
  try {
    const res = await fetch(`${Deno.env.get("SUPABASE_URL")}/functions/v1/ai-generate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")}`,
      },
      body: JSON.stringify({
        prompt: text, system: PARSE_SYSTEM, jsonMode: true, maxTokens: 300, useSearch: false,
      }),
      signal: AbortSignal.timeout(15_000),
    });
    if (!res.ok) return null;
    const j = JSON.parse(String((await res.json())?.text ?? "{}"));
    if (j?.intent !== "record") return null;
    if (!["milk", "food", "sleep", "sleep_end", "waste"].includes(j?.type)) return null;
    return j;
  } catch { return null; }
}

/** One human-readable line describing what we understood, for the confirm bubble. */
function describe(j: any, childName: string, lang: Lang): string {
  const th = lang !== "en";
  switch (j.type) {
    case "milk":
      return th ? `${childName} กินนม ${j.volume_ml} มล.` : `${childName} drank ${j.volume_ml} ml of milk`;
    case "food":
      return th ? `${childName} กิน ${j.item_name}` : `${childName} ate ${j.item_name}`;
    case "sleep":
      return j.sleep_state === "ongoing"
        ? (th ? `${childName} กำลังนอน (เริ่มตอนนี้)` : `${childName} is sleeping (starting now)`)
        : (th ? `${childName} นอน ${j.hours} ชั่วโมง` : `${childName} slept ${j.hours} hours`);
    case "sleep_end":
      return th ? `${childName} ตื่นแล้ว` : `${childName} woke up`;
    default:
      return th ? `${childName} ขับถ่าย (${j.waste_type})` : `${childName} — nappy (${j.waste_type})`;
  }
}

function confirmTemplate(text: string, id: string, lang: Lang) {
  const th = lang !== "en";
  return {
    type: "template",
    altText: text,
    template: {
      type: "confirm",
      text: (th ? "บันทึกข้อมูลนี้ใช่ไหม" : "Record this?") + "\n\n" + text,
      actions: [
        { type: "postback", label: th ? "บันทึก" : "Save", data: `action=rec_yes&id=${id}` },
        { type: "postback", label: th ? "ไม่ใช่" : "Cancel", data: "action=rec_no" },
      ],
    },
  };
}

/**
 * Writes the confirmed record.
 *
 * created_by is set explicitly: the column defaults to auth.uid(), which is NULL under the
 * service role, so without this every LINE-recorded row would have no author.
 */
async function writeRecord(payload: any, userId: string): Promise<boolean> {
  const j = payload.j;
  const base = { child_id: payload.child_id, created_by: userId };
  try {
    if (j.type === "milk") {
      const ml = Math.round(Number(j.volume_ml));
      // Bounds, not just a type check: "1000000 ml" parses fine as a number and is nonsense.
      if (!Number.isFinite(ml) || ml <= 0 || ml > 2000) return false;
      const { error } = await db.from("milk_logs").insert({
        ...base, milk_type: pick("milk_type", j.milk_type, "breast"), volume_ml: ml,
        logged_at: new Date().toISOString(), notes: "via LINE",
      });
      return !error;
    }
    if (j.type === "food") {
      const item = String(j.item_name ?? "").trim();
      if (!item) return false;
      const { error } = await db.from("food_logs").insert({
        ...base, meal_type: pick("meal_type", j.meal_type, "other"), item_name: item.slice(0, 200),
      });
      return !error;
    }
    // An open sleep: started now, no end yet. This is the case a parent cannot tap a button for,
    // because they are holding the child.
    if (j.type === "sleep" && j.sleep_state === "ongoing") {
      const { error } = await db.from("sleep_logs").insert({
        ...base, sleep_type: pick("sleep_type", j.sleep_type, "nap"),
        start_time: new Date().toISOString(), end_time: null, notes: "via LINE",
      });
      return !error;
    }
    // Waking closes the most recent open sleep rather than inserting anything. With no open
    // sleep there is nothing to close, and inventing one would fabricate a start time.
    if (j.type === "sleep_end") {
      const { data: open } = await db.from("sleep_logs")
        .select("id").eq("child_id", payload.child_id).is("end_time", null)
        .order("start_time", { ascending: false }).limit(1);
      if (!open?.[0]?.id) return false;
      const { error } = await db.from("sleep_logs")
        .update({ end_time: new Date().toISOString() }).eq("id", open[0].id);
      return !error;
    }
    if (j.type === "sleep") {
      const hours = Number(j.hours);
      if (!Number.isFinite(hours) || hours <= 0 || hours > 24) return false;
      const end = new Date();
      const start = new Date(end.getTime() - hours * 3600 * 1000);
      const { error } = await db.from("sleep_logs").insert({
        ...base, sleep_type: pick("sleep_type", j.sleep_type, "nap"),
        start_time: start.toISOString(), end_time: end.toISOString(), notes: "via LINE",
      });
      return !error;
    }
    const { error } = await db.from("waste_records").insert({
      ...base, waste_type: pick("waste_type", j.waste_type, "stool"),
      amount: pick("amount", j.amount, "medium"),
      recorded_at: new Date().toISOString(), notes: "via LINE",
    });
    return !error;
  } catch { return false; }
}

/**
 * Turns a parsed record into either a confirm bubble or a "which child?" question.
 *
 * Asking which child is not politeness. Writing a feed against the wrong sibling is a silent
 * corruption nobody notices until the growth chart looks wrong.
 */
async function offerRecord(lineUserId: string, userId: string, j: any, replyToken: string, lang: Lang) {
  const kids = await childrenFor(userId);
  if (kids.length === 0) return reply(replyToken, t("noChildren", lang));

  const named = j.child
    ? kids.find((k: any) => String(k.name).toLowerCase() === String(j.child).toLowerCase())
    : null;
  const chosen = named ?? (kids.length === 1 ? kids[0] : null);

  const { data: pend } = await db.from("line_pending_actions").insert({
    line_user_id: lineUserId, user_id: userId,
    payload: { j, child_id: chosen?.id ?? null },
  }).select("id").single();
  if (!pend?.id) return reply(replyToken, t("aiDown", lang));

  if (chosen) {
    return send(replyToken, [confirmTemplate(describe(j, chosen.name, lang), pend.id, lang)]);
  }

  // Quick replies rather than free text: a typed name must be matched again, and a near-miss
  // would silently fall through to the wrong child.
  return send(replyToken, [{
    type: "text",
    text: lang === "th" ? "บันทึกให้ลูกคนไหนคะ" : "Which child is this for?",
    quickReply: {
      items: kids.slice(0, 10).map((k: any) => ({
        type: "action",
        action: {
          type: "postback", label: String(k.name).slice(0, 20),
          data: `action=rec_child&id=${pend.id}&child=${k.id}`,
          displayText: String(k.name),
        },
      })),
    },
  }]);
}

/** The pending row, if still valid. Expiry and single-use are enforced here, once. */
async function takePending(id: string, lineUserId: string) {
  const { data } = await db.from("line_pending_actions")
    .select("id, user_id, payload, expires_at, confirmed_at")
    .eq("id", id).eq("line_user_id", lineUserId).maybeSingle();
  if (!data || data.confirmed_at || new Date(data.expires_at) < new Date()) return null;
  return data;
}

/**
 * A card for the topic this question is about, or null.
 *
 * Matched on the QUESTION, not the answer: the question is what the parent chose to ask, so the
 * card stays predictable instead of shifting with whatever the model happened to mention.
 *
 * Thai has no word boundaries, so substring matching is the correct tool here, not a word regex.
 */
async function topicCard(question: string, lang: Lang) {
  const { data } = await db.from("line_topic_cards")
    .select("keywords, title_th, title_en, body_th, body_en, route, image_url");
  const q = question.toLowerCase();
  const hit = (data ?? []).find((c: any) =>
    (c.keywords ?? []).some((k: string) => q.includes(String(k).toLowerCase())));
  if (!hit) return null;

  const th = lang !== "en";
  const title = th ? hit.title_th : hit.title_en;
  // LINE only permits http/https/line/tel in a URI action, so a raw nongrak:// button would be
  // rejected. line-open is an https page that bounces into the app, with a Play Store fallback.
  const url = `${Deno.env.get("SUPABASE_URL")}/functions/v1/line-open?r=${encodeURIComponent(hit.route)}`;

  const bubble: any = {
    type: "bubble",
    body: {
      type: "box", layout: "vertical",
      contents: [
        { type: "text", text: title, weight: "bold", size: "lg", wrap: true },
        { type: "text", text: th ? hit.body_th : hit.body_en, size: "sm",
          color: "#7A7168", wrap: true, margin: "md" },
      ],
    },
    footer: {
      type: "box", layout: "vertical",
      contents: [{
        type: "button", style: "primary", color: "#06C755", height: "sm",
        action: { type: "uri", label: th ? "เปิดในแอป" : "Open in app", uri: url },
      }],
    },
  };
  // Only attach a hero when one exists: LINE fails to render the whole bubble if the image
  // URL 404s, so a missing picture would cost the button too.
  if (hit.image_url) {
    bubble.hero = { type: "image", url: hit.image_url, size: "full",
                    aspectRatio: "20:13", aspectMode: "cover" };
  }
  return { type: "flex", altText: title, contents: bubble };
}

// ---------------------------------------------------------------------------
// Rich-menu cards
// ---------------------------------------------------------------------------

const SUPPORT_EMAIL = "pgaslgofficial@gmail.com";

const APP_LINK = (route: string) =>
  `${Deno.env.get("SUPABASE_URL")}/functions/v1/line-open?r=${encodeURIComponent(route)}`;

const openButton = (route: string, lang: Lang, label?: string) => ({
  type: "button", style: "primary", color: "#2743C4", height: "sm",
  action: {
    type: "uri",
    label: label ?? (lang === "th" ? "เปิดในแอป" : "Open in app"),
    uri: APP_LINK(route),
  },
});

/** A labelled row inside a card body. */
const row = (label: string, value: string) => ({
  type: "box", layout: "baseline", spacing: "sm",
  contents: [
    { type: "text", text: label, size: "sm", color: "#9C9389", flex: 2 },
    { type: "text", text: value, size: "sm", color: "#3C342C", flex: 4, wrap: true },
  ],
});

/**
 * One bubble per child: name, age, and the next dose actually due.
 *
 * A carousel rather than a text list because the menu tile promises a view OF the child, and a
 * wall of text is what the parent already gets everywhere else. Each bubble carries its own
 * buttons so the card is a way in, not just a readout.
 */
async function childrenCards(userId: string, lang: Lang) {
  const kids = await childrenFor(userId);
  if (kids.length === 0) return null;
  const th = lang !== "en";

  const bubbles = [];
  // LINE caps a carousel at 12 bubbles.
  for (const kid of kids.slice(0, 12)) {
    const { data: v } = await db.from("vaccine_records")
      .select("vaccine_name, scheduled_date")
      .eq("child_id", kid.id)
      .not("scheduled_date", "is", null)
      .is("date_given", null)
      .order("scheduled_date", { ascending: true })
      .limit(1);
    const next = v?.[0];

    bubbles.push({
      type: "bubble", size: "kilo",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#EEF2FF", paddingAll: "16px",
        contents: [
          { type: "text", text: String(kid.name), weight: "bold", size: "xl",
            color: "#2743C4", wrap: true },
          { type: "text", text: ageText(kid.birthdate, lang) || (th ? "ไม่ระบุอายุ" : "age unknown"),
            size: "sm", color: "#6B7BC4", margin: "xs" },
        ],
      },
      body: {
        type: "box", layout: "vertical", spacing: "md", paddingAll: "16px",
        contents: [
          row(th ? "วันเกิด" : "Born", kid.birthdate ?? "-"),
          row(th ? "วัคซีนต่อไป" : "Next vaccine",
              next ? `${next.vaccine_name} (${next.scheduled_date})`
                   : (th ? "ยังไม่มีนัด" : "none scheduled")),
        ],
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [
          openButton("vaccine_book", lang, th ? "สมุดวัคซีน" : "Vaccine book"),
          { type: "button", style: "secondary", height: "sm",
            action: { type: "uri", label: th ? "การเจริญเติบโต" : "Growth",
                      uri: APP_LINK("growth_history") } },
        ],
      },
    });
  }
  return { type: "flex", altText: th ? "ลูกของคุณ" : "Your children",
           contents: { type: "carousel", contents: bubbles } };
}

/**
 * What the OA can do, and a way into the app.
 *
 * Reachable from the Quick Action tile. It lists things a parent can simply SAY, because nothing
 * else in LINE advertises that recording by chat is possible at all.
 */
function quickActionCard(lang: Lang) {
  const th = lang !== "en";
  const examples = th
    ? ["ลูกกินนม 120 มล.", "ลูกกำลังนอน", "ลูกตื่นแล้ว", "ลูกกินข้าวไข่เจียว", "ลูกอึแล้ว"]
    : ["baby drank 120 ml", "Giga is sleeping", "she woke up", "he ate rice and egg", "nappy change"];
  return {
    type: "flex",
    altText: th ? "บันทึกอะไรได้บ้างใน LINE" : "What you can record on LINE",
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#2743C4", paddingAll: "16px",
        contents: [
          { type: "text", text: th ? "บันทึกได้เลยจากแชทนี้" : "Record straight from this chat",
            color: "#FFFFFF", weight: "bold", size: "lg", wrap: true },
          { type: "text",
            text: th ? "พิมพ์บอกน้องรักได้เลย แล้วกดยืนยัน" : "Just tell NongRak, then tap to confirm",
            color: "#C3CDF5", size: "xs", margin: "sm", wrap: true },
        ],
      },
      body: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "16px",
        contents: [
          ...examples.map((e) => ({
            type: "box", layout: "baseline", spacing: "sm",
            contents: [
              { type: "text", text: "•", size: "sm", color: "#2743C4", flex: 0 },
              { type: "text", text: e, size: "sm", color: "#3C342C", wrap: true, flex: 1 },
            ],
          })),
          { type: "separator", margin: "lg" },
          { type: "text",
            text: th
              ? "ดูกราฟ ประวัติ และรายละเอียดทั้งหมดได้ในแอป"
              : "Charts, history and the full picture live in the app.",
            size: "xs", color: "#9C9389", margin: "lg", wrap: true },
        ],
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [openButton("dashboard~2", lang, th ? "ไปที่แอป NongRak" : "Go to the NongRak app")],
      },
    },
  };
}

/** Where to reach a human. One place, so the wording cannot drift between languages. */
function adminCard(lang: Lang) {
  const th = lang !== "en";
  return {
    type: "flex",
    altText: th ? "ติดต่อแอดมิน" : "Contact admin",
    contents: {
      type: "bubble",
      body: {
        type: "box", layout: "vertical", spacing: "md", paddingAll: "20px",
        contents: [
          { type: "text", text: th ? "ติดต่อทีมงาน NongRak" : "Contact the NongRak team",
            weight: "bold", size: "lg", color: "#2743C4", wrap: true },
          { type: "text",
            text: th
              ? "พิมพ์คำถามทิ้งไว้ในแชทนี้ได้เลย ทีมงานจะอ่านและติดต่อกลับ"
              : "Leave your question in this chat and the team will read it and come back to you.",
            size: "sm", color: "#7A7168", wrap: true },
          { type: "separator", margin: "md" },
          row(th ? "อีเมล" : "Email", SUPPORT_EMAIL),
          { type: "text",
            text: th
              ? "กรณีฉุกเฉินทางการแพทย์ กรุณาติดต่อโรงพยาบาลหรือโทร 1669 ทันที"
              : "For a medical emergency contact a hospital or call 1669 immediately.",
            size: "xs", color: "#C0392B", margin: "md", wrap: true },
        ],
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [
          { type: "button", style: "primary", color: "#2743C4", height: "sm",
            action: { type: "postback",
              label: th ? "เขียนข้อความในแชท" : "Write here in chat",
              data: "action=support_start" } },
          // Gmail compose over https, NOT mailto: LINE rejects any scheme other than
          // http/https/line/tel in a URI action, so a mailto button would simply not render.
          { type: "button", style: "secondary", height: "sm",
            action: { type: "uri", label: th ? "ส่งอีเมล" : "Send an email",
              uri: `https://mail.google.com/mail/?view=cm&fs=1&to=${encodeURIComponent(SUPPORT_EMAIL)}` +
                   `&su=${encodeURIComponent("NongRak support")}` } },
          // For anyone not on Gmail: copy the address and use whatever mail app they have.
          { type: "button", style: "link", height: "sm",
            action: { type: "clipboard", label: th ? "คัดลอกอีเมล" : "Copy email address",
                      clipboardText: SUPPORT_EMAIL } },
        ],
      },
    },
  };
}

/**
 * The "new enquiry" card an admin receives.
 *
 * The reply command is a CLIPBOARD button, not a message button: a message action would send
 * "/reply <code> " immediately, with no body, and the webhook would answer with the usage hint.
 * Copying it lets them paste, type the answer, and send once.
 */
function supportNotifyCard(sender: string, body: string, code: string, lang: Lang) {
  const th = lang !== "en";
  return {
    type: "flex",
    altText: th ? `ข้อความใหม่จาก ${sender}` : `New message from ${sender}`,
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#FFF3E0", paddingAll: "14px",
        contents: [
          { type: "text", text: th ? "ข้อความใหม่ถึงทีมงาน" : "New message for the team",
            weight: "bold", size: "sm", color: "#B26A00", wrap: true },
          { type: "text", text: sender, size: "xs", color: "#B7A98F", margin: "xs", wrap: true },
        ],
      },
      body: {
        type: "box", layout: "vertical", spacing: "md", paddingAll: "16px",
        contents: [
          { type: "text", text: body.slice(0, 300), size: "sm", color: "#3C342C", wrap: true },
          { type: "separator", margin: "md" },
          row(th ? "รหัส" : "Code", code),
        ],
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [
          { type: "button", style: "primary", color: "#2743C4", height: "sm",
            action: { type: "clipboard",
              label: th ? "คัดลอกคำสั่งตอบ" : "Copy reply command",
              clipboardText: `/reply ${code} ` } },
          { type: "button", style: "secondary", height: "sm",
            action: { type: "message",
              label: th ? "ดูข้อความที่รอตอบ" : "See all waiting",
              text: "/inbox" } },
        ],
      },
    },
  };
}

/** The team's answer, as it lands in the parent's chat. */
function supportReplyCard(body: string, lang: Lang) {
  const th = lang !== "en";
  return {
    type: "flex",
    altText: th ? "ทีมงานน้องรักตอบกลับ" : "Reply from the NongRak team",
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#2743C4", paddingAll: "14px",
        contents: [{
          type: "text", text: th ? "ทีมงานน้องรักตอบกลับ" : "Reply from the NongRak team",
          color: "#FFFFFF", weight: "bold", size: "sm", wrap: true,
        }],
      },
      body: {
        type: "box", layout: "vertical", paddingAll: "18px",
        contents: [{ type: "text", text: body.slice(0, 1000),
                     size: "sm", color: "#3C342C", wrap: true }],
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [
          // Keeps the conversation open. Without it the parent has to hunt for the menu tile
          // again to say one more thing, which is where a support thread usually dies.
          { type: "button", style: "secondary", height: "sm",
            action: { type: "postback",
              label: th ? "ส่งข้อความอีกครั้ง" : "Write again",
              data: "action=support_start" } },
        ],
      },
    },
  };
}

/**
 * Captures the next message as a support enquiry and notifies staff.
 *
 * Reuses line_pending_actions for the "waiting for their message" state: it already has the
 * expiry and single-use semantics this needs, and a second table for one boolean would be two
 * places to get the timeout wrong.
 */
async function supportStart(lineUserId: string, replyToken: string, lang: Lang) {
  const userId = await linkedUserId(lineUserId);
  // Checked BEFORE the insert: user_id is NOT NULL, so an unlinked sender would throw here
  // rather than get the friendly message. They still reach us — the card shows the email.
  if (!userId) return reply(replyToken, t("supportNeedsLink", lang));

  await db.from("line_pending_actions").insert({
    line_user_id: lineUserId, user_id: userId, payload: { kind: "support" },
  });
  return reply(replyToken, t("supportPrompt", lang));
}

/** Short, typeable handle for a message. A full uuid is not something anyone types correctly. */
const shortCode = () => Math.random().toString(36).slice(2, 10);

async function supportCapture(lineUserId: string, body: string, replyToken: string, lang: Lang) {
  const p = await profile(lineUserId);
  const code = shortCode();
  const { error } = await db.from("line_support_messages").insert({
    code, line_user_id: lineUserId,
    display_name: p.displayName ?? null,
    body: body.slice(0, 2000),
  });
  if (error) return reply(replyToken, t("aiDown", lang));

  // Notify staff by push. One message per admin, and there is normally one admin, so this is a
  // negligible slice of the monthly quota. Failure here is not surfaced to the sender: their
  // message is already stored, and the team will see it whether or not the push landed.
  const { data: admins } = await db.from("line_admins")
    .select("line_user_id, always_notify").eq("notify_enabled", true);
  for (const a of admins ?? []) {
    // An admin does not get pinged about their own message — unless they asked to be. The OA
    // owner needs every enquiry to land in their chat, including their own test messages,
    // because otherwise the alert path can only be proved from a second phone.
    if (a.line_user_id === lineUserId && !a.always_notify) continue;
    // The ADMIN's language, not the sender's: this card is read by staff, and an English-reading
    // admin should not have to parse a Thai header to find out that someone needs help.
    const adminLang = await langFor(a.line_user_id);
    await fetch("https://api.line.me/v2/bot/message/push", {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
      body: JSON.stringify({
        to: a.line_user_id,
        messages: [supportNotifyCard(p.displayName ?? lineUserId, body, code, adminLang)],
      }),
    }).catch(() => {});
  }
  return reply(replyToken, t("supportSent", lang));
}

/** Staff reply, addressed by the short code. Silence is a valid outcome: no reply, no push. */
async function supportReply(lineUserId: string, rest: string, replyToken: string, lang: Lang) {
  // Same answer as an unknown command for non-staff: confirming that /reply exists would tell a
  // stranger there is an admin channel worth probing.
  if (!(await isAdmin(lineUserId))) return reply(replyToken, t("unknownCmd", lang));

  const sp = rest.trim().indexOf(" ");
  if (sp < 1) return reply(replyToken, t("replyUsage", lang));
  const code = rest.trim().slice(0, sp);
  const body = rest.trim().slice(sp + 1).trim();
  if (!body) return reply(replyToken, t("replyUsage", lang));

  const { data: msg } = await db.from("line_support_messages")
    .select("id, line_user_id, replied_at").eq("code", code).maybeSingle();
  if (!msg) return reply(replyToken, t("replyNotFound", lang));

  // The RECIPIENT's language, not the admin's — `lang` here belongs to whoever typed /reply.
  const toLang = await langFor(msg.line_user_id);
  const res = await fetch("https://api.line.me/v2/bot/message/push", {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${CHANNEL_TOKEN}` },
    body: JSON.stringify({ to: msg.line_user_id, messages: [supportReplyCard(body, toLang)] }),
  });
  if (!res.ok) return reply(replyToken, t("replyFailed", lang));

  await db.from("line_support_messages").update({
    replied_at: new Date().toISOString(), reply_body: body, replied_by: lineUserId,
  }).eq("id", msg.id);
  return reply(replyToken, t("replySent", lang));
}

/**
 * Support messages still waiting for an answer.
 *
 * Exists because an admin who is also the sender gets no push — supportCapture skips notifying
 * whoever wrote the message — so without this the only way to see the queue was SQL.
 *
 * Each bubble copies the whole "/reply <code> " prefix to the clipboard: typing an eight
 * character code correctly on a phone is exactly the friction that stops people answering.
 */
async function inboxCard(lang: Lang) {
  const { data } = await db.from("line_support_messages")
    .select("code, display_name, body, created_at")
    .is("replied_at", null)
    .order("created_at", { ascending: false }).limit(10);
  const list = data ?? [];
  const th = lang !== "en";
  if (list.length === 0) return { type: "text", text: t("inboxEmpty", lang) };

  return {
    type: "flex",
    altText: th ? "ข้อความรอตอบ" : "Waiting for reply",
    contents: {
      type: "carousel",
      contents: list.map((m: any) => ({
        type: "bubble", size: "kilo",
        header: {
          type: "box", layout: "vertical", backgroundColor: "#FFF3E0", paddingAll: "12px",
          contents: [
            { type: "text", text: String(m.display_name ?? "?"), weight: "bold",
              size: "sm", color: "#B26A00", wrap: true },
            { type: "text", text: String(m.created_at).slice(0, 16).replace("T", " "),
              size: "xxs", color: "#B7A98F" },
          ],
        },
        body: {
          type: "box", layout: "vertical", paddingAll: "14px",
          contents: [{ type: "text", text: String(m.body).slice(0, 300),
                       size: "sm", color: "#3C342C", wrap: true }],
        },
        footer: {
          type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
          contents: [
            { type: "text", text: m.code, size: "xs", color: "#9C9389", align: "center" },
            { type: "button", style: "primary", color: "#2743C4", height: "sm",
              action: { type: "clipboard",
                label: th ? "คัดลอกคำสั่งตอบ" : "Copy reply command",
                clipboardText: `/reply ${m.code} ` } },
          ],
        },
      })),
    },
  };
}

/**
 * One curated parenting tip, matched to the youngest linked child's age.
 *
 * Curated rows, never model output: a parent reads this as advice about their real child, and a
 * generated tip can be confidently wrong with nobody in the loop to catch it.
 *
 * Unlinked or childless users still get a tip from the general band, so the menu button always
 * does something rather than turning into a login prompt.
 */
async function tipCard(userId: string | null, lang: Lang) {
  const th = lang !== "en";
  let months = 12;
  if (userId) {
    const kids = await childrenFor(userId);
    const ages = kids
      .map((k: any) => k.birthdate
        ? Math.floor((Date.now() - new Date(k.birthdate).getTime()) / (1000 * 60 * 60 * 24 * 30.44))
        : null)
      .filter((m: any) => typeof m === "number" && m >= 0) as number[];
    if (ages.length) months = Math.min(...ages);
  }

  const { data } = await db.from("line_tips")
    .select("title_th, title_en, body_th, body_en")
    .eq("active", true).lte("min_months", months).gte("max_months", months);

  const list = data ?? [];
  if (list.length === 0) return { type: "text", text: t("aiDown", lang) };
  const tip = list[Math.floor(Math.random() * list.length)];

  return {
    type: "flex",
    altText: th ? tip.title_th : tip.title_en,
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#FFF3E0", paddingAll: "14px",
        contents: [{
          type: "text", text: th ? "เคล็ดลับเลี้ยงลูก" : "Parenting tip",
          size: "xs", color: "#B26A00", weight: "bold",
        }],
      },
      body: {
        type: "box", layout: "vertical", spacing: "md", paddingAll: "18px",
        contents: [
          { type: "text", text: th ? tip.title_th : tip.title_en,
            weight: "bold", size: "lg", color: "#3C342C", wrap: true },
          { type: "text", text: th ? tip.body_th : tip.body_en,
            size: "sm", color: "#7A7168", wrap: true },
        ],
      },
      footer: {
        type: "box", layout: "vertical", paddingAll: "12px",
        contents: [openButton("dashboard~2", lang, th ? "เปิดแอป NongRak" : "Open NongRak")],
      },
    },
  };
}

/**
 * The help card: what you can type, plus a way into the FAQ.
 *
 * A card rather than a wall of text because the menu tile promises help, and a list of slash
 * commands with no way to ask a follow-up is a dead end.
 */
/**
 * Everything due today, on demand.
 *
 * This is a REPLY, not a push. LINE's free tier allows 300 push messages a month across every
 * follower combined, so a bot that pushes each vaccine, dose and bedtime would burn the whole
 * month's allowance on a handful of families and then go silent for everyone — including the
 * warnings that actually matter. A tile the parent taps costs nothing and can be tapped all day.
 *
 * Pushes stay reserved for the genuinely time-critical: the vaccine reminder cron, and the
 * WARNING/SCHEDULE notifications the app mirrors.
 */

/** Bangkok "today". UTC rolls over at 07:00 Thai time, so a naive toISOString is a day out all
 *  evening — long enough to hide the bedtime the parent opened this to check. */
const bkkNow = () => new Date(Date.now() + 7 * 3600 * 1000);
const bkkToday = () => bkkNow().toISOString().slice(0, 10);

/** HH:MM in Bangkok for a UTC timestamp. */
function bkkTime(iso: string): string {
  return new Date(new Date(iso).getTime() + 7 * 3600 * 1000).toISOString().slice(11, 16);
}

const DAY_CODES = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];

/** One line inside a reminder section. */
const remRow = (time: string, label: string, who: string) => ({
  type: "box", layout: "baseline", spacing: "sm",
  contents: [
    { type: "text", text: time, size: "sm", color: "#2743C4", weight: "bold", flex: 2 },
    { type: "text", text: label, size: "sm", color: "#3C342C", flex: 5, wrap: true },
    { type: "text", text: who, size: "xxs", color: "#9C9389", flex: 3, wrap: true, align: "end" },
  ],
});

const remHeading = (text: string) => ({
  type: "text", text, size: "xs", color: "#B26A00", weight: "bold", margin: "lg",
});

/**
 * The reminders tab: medicine doses, sleep times and upcoming vaccines for every linked child.
 */
async function remindersCard(userId: string, lang: Lang) {
  const th = lang !== "en";
  const kids = await childrenFor(userId);
  if (kids.length === 0) return { type: "text", text: t("noChildren", lang) };

  const ids = kids.map((k: any) => k.id);
  const nameOf = new Map<string, string>(kids.map((k: any) => [String(k.id), String(k.name)]));
  const today = bkkToday();
  const sections: unknown[] = [];

  // ---- Medicine doses still open today -------------------------------------------------
  // The window is built from the Bangkok day, then converted back to UTC for the column, which
  // stores UTC. Comparing a UTC column against a Bangkok date string silently shifts the window
  // by seven hours and drops the evening doses.
  const dayStart = new Date(`${today}T00:00:00+07:00`).toISOString();
  const dayEnd = new Date(`${today}T23:59:59+07:00`).toISOString();
  const { data: doses } = await db.from("medication_doses")
    .select("scheduled_at, status, child_id, schedule_id")
    .in("child_id", ids)
    .gte("scheduled_at", dayStart)
    .lte("scheduled_at", dayEnd)
    .order("scheduled_at", { ascending: true });

  // Filtered here rather than in the query: status is NULL for a dose nobody has touched, and
  // `not in (taken, skipped)` is NULL-unsafe in Postgres — it would drop exactly the doses that
  // are still due.
  const openDoses = (doses ?? []).filter((d: any) => d.status !== "taken" && d.status !== "skipped");
  if (openDoses.length) {
    const schedIds = [...new Set(openDoses.map((d: any) => d.schedule_id).filter(Boolean))];
    const { data: scheds } = await db.from("medication_schedules")
      .select("id, medicine_name, dosage, dosage_unit").in("id", schedIds);
    const medOf = new Map<string, any>((scheds ?? []).map((m: any) => [String(m.id), m]));

    sections.push(remHeading(th ? "ยา" : "MEDICINE"));
    for (const d of openDoses.slice(0, 8)) {
      const m: any = medOf.get(d.schedule_id);
      const dose = m?.dosage ? ` ${m.dosage} ${m.dosage_unit ?? ""}`.trimEnd() : "";
      sections.push(remRow(
        bkkTime(d.scheduled_at),
        `${m?.medicine_name ?? (th ? "ยา" : "medicine")}${dose}`,
        nameOf.get(d.child_id) ?? "",
      ));
    }
  }

  // ---- Sleep schedule for today's weekday ----------------------------------------------
  // getUTCDay on the shifted clock: 0 = Sunday, and the stored codes are Monday-first.
  const todayCode = DAY_CODES[(bkkNow().getUTCDay() + 6) % 7];
  const { data: sleeps } = await db.from("sleep_schedules")
    .select("label, sleep_type, start_time, end_time, days_of_week, child_id")
    .in("child_id", ids)
    .order("start_time", { ascending: true });

  const todaySleeps = (sleeps ?? []).filter((s: any) => {
    const d = String(s.days_of_week ?? "").trim();
    if (!d || d.toUpperCase() === "ALL") return true;
    return d.split(",").map((x: string) => x.trim().toUpperCase()).includes(todayCode);
  });
  if (todaySleeps.length) {
    sections.push(remHeading(th ? "การนอน" : "SLEEP"));
    for (const s of todaySleeps.slice(0, 8)) {
      const label = String(s.label ?? "").trim() ||
        (s.sleep_type === "nap" ? (th ? "งีบ" : "Nap") : (th ? "นอนกลางคืน" : "Night sleep"));
      sections.push(remRow(
        String(s.start_time).slice(0, 5),
        `${label} - ${String(s.end_time).slice(0, 5)}`,
        nameOf.get(s.child_id) ?? "",
      ));
    }
  }

  // ---- Vaccines due or overdue ----------------------------------------------------------
  // No lower bound on purpose: a dose whose date has already passed is the one most worth
  // showing, and an "upcoming only" window is exactly how a missed dose stays missed.
  const horizon = new Date(bkkNow().getTime() + 14 * 86400 * 1000).toISOString().slice(0, 10);
  const { data: vax } = await db.from("vaccine_records")
    .select("vaccine_name, scheduled_date, child_id")
    .in("child_id", ids)
    .is("date_given", null)
    .not("scheduled_date", "is", null)
    .lte("scheduled_date", horizon)
    .order("scheduled_date", { ascending: true });

  if (vax?.length) {
    sections.push(remHeading(th ? "วัคซีน" : "VACCINE"));
    for (const v of vax.slice(0, 8)) {
      const overdue = v.scheduled_date < today;
      sections.push(remRow(
        String(v.scheduled_date).slice(5),
        `${v.vaccine_name}${overdue ? (th ? " (เลยกำหนด)" : " (overdue)") : ""}`,
        nameOf.get(v.child_id) ?? "",
      ));
    }
  }

  if (sections.length === 0) {
    sections.push({
      type: "text",
      text: th
        ? "วันนี้ไม่มีรายการเตือนค่ะ พักผ่อนได้เลย"
        : "Nothing due today. Enjoy the quiet.",
      size: "sm", color: "#7A7168", wrap: true,
    });
  }

  return {
    type: "flex",
    altText: th ? "เตือนความจำวันนี้" : "Today's reminders",
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#2743C4", paddingAll: "16px",
        contents: [
          { type: "text", text: th ? "เตือนความจำวันนี้" : "Today's reminders",
            color: "#FFFFFF", weight: "bold", size: "lg", wrap: true },
          { type: "text", text: today, color: "#C3CDF5", size: "xs", margin: "sm" },
        ],
      },
      body: { type: "box", layout: "vertical", spacing: "sm", paddingAll: "16px", contents: sections },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [openButton("dashboard~2", lang, th ? "เปิดแอป NongRak" : "Open NongRak")],
      },
    },
  };
}

function helpCard(lang: Lang) {
  const th = lang !== "en";
  const cmds: [string, string][] = th
    ? [
        ["/reminders", "เตือนความจำวันนี้"],
        ["/quick", "บันทึกอะไรได้บ้าง"],
        ["/tips", "เคล็ดลับเลี้ยงลูก"],
        ["/children", "ข้อมูลลูก"],
        ["/vaccine", "วัคซีนเข็มต่อไป"],
        ["/admin", "ติดต่อทีมงาน"],
        ["/link", "เชื่อมบัญชีกับแอป"],
        ["/unlink", "ยกเลิกการเชื่อมต่อ"],
        ["/clear", "ล้างรายการที่ค้างอยู่"],
        ["/eng", "ภาษาอังกฤษ"],
      ]
    : [
        ["/reminders", "everything due today"],
        ["/quick", "what you can record here"],
        ["/tips", "parenting tips"],
        ["/children", "your children"],
        ["/vaccine", "next vaccine due"],
        ["/admin", "contact the team"],
        ["/link", "connect your app account"],
        ["/unlink", "disconnect"],
        ["/clear", "clear a stuck action"],
        ["/thai", "switch to Thai"],
      ];

  return {
    type: "flex",
    altText: th ? "ช่วยเหลือ" : "Help",
    contents: {
      type: "bubble",
      header: {
        type: "box", layout: "vertical", backgroundColor: "#2743C4", paddingAll: "16px",
        contents: [
          { type: "text", text: th ? "น้องรักช่วยอะไรได้บ้าง" : "What NongRak can do",
            color: "#FFFFFF", weight: "bold", size: "lg", wrap: true },
          { type: "text",
            text: th
              ? "พิมพ์คำถามเรื่องลูกได้เลย หรือใช้คำสั่งด้านล่าง"
              : "Ask anything about your child, or use a command below",
            color: "#C3CDF5", size: "xs", margin: "sm", wrap: true },
        ],
      },
      body: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "16px",
        contents: cmds.map(([c, d]) => ({
          type: "box", layout: "baseline", spacing: "sm",
          contents: [
            { type: "text", text: c, size: "sm", color: "#2743C4", weight: "bold", flex: 3 },
            { type: "text", text: d, size: "sm", color: "#7A7168", flex: 5, wrap: true },
          ],
        })),
      },
      footer: {
        type: "box", layout: "vertical", spacing: "sm", paddingAll: "12px",
        contents: [
          { type: "button", style: "primary", color: "#2743C4", height: "sm",
            action: { type: "message",
              label: th ? "คำถามที่พบบ่อย" : "FAQ",
              text: "/faq" } },
          { type: "button", style: "secondary", height: "sm",
            action: { type: "message",
              label: th ? "เคล็ดลับเลี้ยงลูก" : "Parenting tips",
              text: "/tips" } },
        ],
      },
    },
  };
}

/**
 * The FAQ, one bubble per question.
 *
 * A carousel keeps each answer readable on a phone; a single bubble holding six Q&A pairs is a
 * scroll nobody finishes.
 */
async function faqCard(lang: Lang) {
  const { data } = await db.from("line_faqs")
    .select("q_th, q_en, a_th, a_en").eq("active", true).order("sort").limit(12);
  const list = data ?? [];
  const th = lang !== "en";
  if (list.length === 0) return { type: "text", text: t("aiDown", lang) };

  return {
    type: "flex",
    altText: th ? "คำถามที่พบบ่อย" : "FAQ",
    contents: {
      type: "carousel",
      contents: list.map((f: any) => ({
        type: "bubble", size: "kilo",
        header: {
          type: "box", layout: "vertical", backgroundColor: "#EEF2FF", paddingAll: "14px",
          contents: [{ type: "text", text: th ? f.q_th : f.q_en,
                       weight: "bold", size: "sm", color: "#2743C4", wrap: true }],
        },
        body: {
          type: "box", layout: "vertical", paddingAll: "14px",
          contents: [{ type: "text", text: th ? f.a_th : f.a_en,
                       size: "sm", color: "#7A7168", wrap: true }],
        },
      })),
    },
  };
}

// ---------------------------------------------------------------------------
// Routing
// ---------------------------------------------------------------------------

/**
 * Destructive, so it asks first.
 *
 * A mistyped /unlink would otherwise silently cut a parent off from every health alert, and they
 * would only discover it by not receiving one.
 */
function unlinkConfirm(lang: Lang) {
  return {
    type: "template",
    altText: t("unlinkAsk", lang),
    template: {
      type: "confirm",
      text: t("unlinkAsk", lang),
      actions: [
        { type: "postback", label: t("unlinkYes", lang), data: "action=unlink_yes" },
        { type: "postback", label: t("unlinkNo", lang), data: "action=unlink_no" },
      ],
    },
  };
}

/** Rich-menu buttons send plain text, so menu taps and typed commands share this one path. */
async function route(lineUserId: string, raw: string, replyToken: string) {
  const text = raw.trim();
  const lower = text.toLowerCase();
  const lang = await langFor(lineUserId);

  // Language first: these must work before anything that depends on being linked.
  if (lower === "/eng" || lower === "/en" || lower === "/english") {
    await setLang(lineUserId, "en");
    return reply(replyToken, t("langChanged", "en"));
  }
  if (lower === "/thai" || lower === "/th" || lower === "/ไทย") {
    await setLang(lineUserId, "th");
    return reply(replyToken, t("langChanged", "th"));
  }
  // Undocumented on purpose — it is a setup tool, not a feature, and it is how a prospective
  // admin hands over their LINE id without anyone having to read it out of the database. It
  // reveals only the caller's OWN id, so leaving it open to everyone costs nothing.
  if (lower === "/whoami" || lower === "/id") {
    return reply(replyToken, t("whoami", lang) + lineUserId);
  }
  if (lower === "/help" || lower === "/menu" || /^(ช่วยเหลือ|เมนู|help|menu)$/i.test(text)) {
    return send(replyToken, [helpCard(lang)]);
  }
  if (lower === "/faq" || /^(คำถามที่พบบ่อย|faq)$/i.test(text)) {
    return send(replyToken, [await faqCard(lang)]);
  }
  if (lower === "/link" || /^(เชื่อมบัญชี|link)$/i.test(text)) {
    return reply(replyToken, t("link", lang));
  }

  const userId = await linkedUserId(lineUserId);

  if (lower === "/unlink") {
    if (!userId) return reply(replyToken, t("notLinked", lang));
    return send(replyToken, [unlinkConfirm(lang)]);
  }
  if (lower === "/reminders" || lower === "/today" ||
      /^(เตือนความจำ|วันนี้|reminders|today)$/i.test(text)) {
    if (!userId) return reply(replyToken, t("link", lang));
    return send(replyToken, [await remindersCard(userId, lang)]);
  }
  if (lower === "/vaccine" || /^(วัคซีน|วัคซีนต่อไป|vaccine)$/i.test(text)) {
    return reply(replyToken, userId ? await nextVaccineText(userId, lang) : t("link", lang));
  }
  if (lower === "/children" || lower === "/kids" || /^(ลูกของฉัน|ลูก|children)$/i.test(text)) {
    if (!userId) return reply(replyToken, t("link", lang));
    const cards = await childrenCards(userId, lang);
    return cards ? send(replyToken, [cards]) : reply(replyToken, t("noChildren", lang));
  }
  if (lower === "/admin") return send(replyToken, [adminCard(lang)]);
  if (lower === "/clear") {
    // Clears STATE, not history. LINE gives a bot no way to delete messages from a chat, so the
    // honest thing is to unstick the conversation and say plainly what cannot be done.
    await db.from("line_pending_actions")
      .update({ confirmed_at: new Date().toISOString() })
      .eq("line_user_id", lineUserId).is("confirmed_at", null);
    return send(replyToken, [
      { type: "text", text: t("clearDone", lang) },
      { type: "text", text: t("clearNote", lang) },
    ]);
  }
  if (lower.startsWith("/reply ")) return supportReply(lineUserId, text.slice(7), replyToken, lang);
  if (lower === "/inbox") {
    // Non-staff get the same answer as any unknown command, so /inbox never advertises that a
    // support queue exists at all.
    if (!(await isAdmin(lineUserId))) return reply(replyToken, t("unknownCmd", lang));
    return send(replyToken, [await inboxCard(lang)]);
  }
  if (lower === "/quick") return send(replyToken, [quickActionCard(lang)]);
  if (lower === "/tips") {
    // Tips are matched to a child's age. Without a linked account there is no age to match,
    // and a tip from the general band pretending otherwise is worse than an honest prompt.
    if (!userId) return send(replyToken, [guestLockedCard(lang)]);
    return send(replyToken, [await tipCard(userId, lang)]);
  }
  if (/^(ถามน้องรัก|ask)$/i.test(text)) {
    return reply(replyToken, t("askPrompt", lang));
  }

  // An unrecognised slash command is a typo, not a question. Sending it to the AI would produce
  // a confident answer about a command that does not exist.
  if (text.startsWith("/")) return reply(replyToken, t("unknownCmd", lang));

  // A record only makes sense for a linked account: there is no child to write against
  // otherwise, and pointing them at /link is more use than an answer.
  if (userId) {
    const parsed = await classify(text);
    if (parsed) return offerRecord(lineUserId, userId, parsed, replyToken, lang);
  }

  // Answer first, then a card into the matching screen. Two bubbles in ONE reply: a reply token
  // is single-use, so sending them separately would need a push and cost quota.
  if (await guestOverLimit(lineUserId, userId)) {
    return send(replyToken, [guestLimitCard(lang)]);
  }

  const answer = await askAi(text, userId, lang);
  const card = await topicCard(text, lang);
  const msgs: unknown[] = [{ type: "text", text: answer }];
  if (card) msgs.push(card);
  return send(replyToken, msgs);
}

async function onPostback(lineUserId: string, data: string, replyToken: string) {
  const lang = await langFor(lineUserId);
  const p = new URLSearchParams(data);
  const action = p.get("action");

  if (action === "unlink_yes") {
    await db.from("line_links").delete().eq("line_user_id", lineUserId);
    return reply(replyToken, t("unlinkDone", lang));
  }
  if (action === "unlink_no") return reply(replyToken, t("unlinkCancelled", lang));
  if (action === "rec_no") return reply(replyToken, t("recCancelled", lang));
  if (action === "support_start") return supportStart(lineUserId, replyToken, lang);

  if (action === "rec_child") {
    const pend = await takePending(p.get("id") ?? "", lineUserId);
    if (!pend) return reply(replyToken, t("recExpired", lang));
    const kids = await childrenFor(pend.user_id);
    const kid = kids.find((k: any) => k.id === p.get("child"));
    if (!kid) return reply(replyToken, t("recFailed", lang));
    const payload = { ...(pend.payload as any), child_id: kid.id };
    await db.from("line_pending_actions").update({ payload }).eq("id", pend.id);
    return send(replyToken, [confirmTemplate(describe(payload.j, kid.name, lang), pend.id, lang)]);
  }

  if (action === "rec_yes") {
    const pend = await takePending(p.get("id") ?? "", lineUserId);
    if (!pend) return reply(replyToken, t("recExpired", lang));
    // Claim it BEFORE writing. A double-tap on the confirm button would otherwise insert the
    // same feed twice, and a duplicated health record is harder to notice than a missing one.
    await db.from("line_pending_actions")
      .update({ confirmed_at: new Date().toISOString() }).eq("id", pend.id);
    const ok = await writeRecord(pend.payload, pend.user_id);
    return reply(replyToken, ok ? t("recSaved", lang) : t("recFailed", lang));
  }
}

/** Codes are 8 chars from an unambiguous alphabet; anything else is ordinary conversation. */
const CODE_RE = /^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$/;

/** The two-factor code shown in the app. Digits only, so it cannot collide with CODE_RE. */
const OTP_RE = /^[0-9]{8}$/;

async function onText(lineUserId: string, text: string, replyToken: string) {
  // A pending support enquiry takes the next message whole — checked BEFORE the router so a
  // question typed to the team is not answered by the AI instead of reaching a human.
  const { data: pendingSupport } = await db.from("line_pending_actions")
    .select("id, expires_at, confirmed_at, payload")
    .eq("line_user_id", lineUserId).is("confirmed_at", null)
    .order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (pendingSupport && (pendingSupport.payload as any)?.kind === "support"
      && new Date(pendingSupport.expires_at) > new Date()) {
    await db.from("line_pending_actions")
      .update({ confirmed_at: new Date().toISOString() }).eq("id", pendingSupport.id);
    return supportCapture(lineUserId, text, replyToken, await langFor(lineUserId));
  }

  const code = text.trim().toUpperCase();

  // Eight digits is the 2FA code from the app. Claiming it raises a request; it does NOT link.
  // The parent still has to approve on the phone, which is the whole point of the second factor.
  if (OTP_RE.test(code)) {
    const lang = await langFor(lineUserId);
    const p = await profile(lineUserId);
    const { data: ok } = await db.rpc("claim_link_code", {
      p_code: code,
      p_line_user_id: lineUserId,
      p_display_name: p.displayName ?? null,
    });
    // Same message for wrong, expired, already-used and rate-limited: telling them which would
    // let someone guessing codes learn which guesses exist.
    return reply(replyToken, ok === true ? t("otpClaimed", lang) : t("codeBad", lang));
  }

  if (!CODE_RE.test(code)) return route(lineUserId, text, replyToken);

  const lang = await langFor(lineUserId);
  const { data: row } = await db.from("line_pair_codes")
    .select("code, user_id, expires_at, used_at").eq("code", code).maybeSingle();

  // One message for "wrong", "already used" and "expired" on purpose: distinguishing them tells
  // someone guessing codes which of their guesses exist.
  if (!row || row.used_at || new Date(row.expires_at) < new Date()) {
    return reply(replyToken, t("codeBad", lang));
  }

  const p = await profile(lineUserId);
  const { error } = await db.from("line_links").upsert({
    user_id: row.user_id,
    line_user_id: lineUserId,
    display_name: p.displayName ?? null,
    notify_enabled: true,
    linked_at: new Date().toISOString(),
  }, { onConflict: "user_id" });

  // The unique constraint on line_user_id fires here: this LINE account already belongs to a
  // different NongRak account. Silently re-pointing it would move a family's alerts to the
  // wrong phone.
  if (error) return reply(replyToken, t("codeTaken", lang));

  await db.from("line_pair_codes").update({ used_at: new Date().toISOString() }).eq("code", code);
  return reply(replyToken, t("paired", lang));
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("POST only", { status: 405 });

  // Raw text, not req.json(): the signature covers the exact bytes LINE sent, so re-serialising
  // a parsed object would change key order or spacing and never match.
  const raw = await req.text();
  if (!(await verify(raw, req.headers.get("x-line-signature")))) {
    return new Response("bad signature", { status: 401 });
  }

  let events: any[] = [];
  try { events = JSON.parse(raw)?.events ?? []; } catch { /* LINE also sends an empty verify ping */ }

  for (const e of events) {
    const lineUserId = e?.source?.userId;
    if (!lineUserId) continue;
    try {
      if (e.type === "follow") {
        await db.from("line_links").update({ notify_enabled: true }).eq("line_user_id", lineUserId);
        const lang = await langFor(lineUserId);
        // Give them the menu in their phone's language before they have typed anything.
        await linkMenu(lineUserId, lang);
        await reply(e.replyToken, t("welcome", lang));
      } else if (e.type === "unfollow") {
        // Keep the row so re-following restores the link without pairing again; just stop pushing.
        await db.from("line_links").update({ notify_enabled: false }).eq("line_user_id", lineUserId);
      } else if (e.type === "message" && e.message?.type === "text") {
        await onText(lineUserId, e.message.text ?? "", e.replyToken);
      } else if (e.type === "message" && e.message?.type === "image") {
        await onImage(lineUserId, e.message.id, e.replyToken);
      } else if (e.type === "message" && e.message?.type === "sticker") {
        await onSticker(lineUserId, e.message, e.replyToken);
      } else if (e.type === "postback") {
        await onPostback(lineUserId, e.postback?.data ?? "", e.replyToken);
      }
    } catch (_) { /* one bad event must not fail the batch — LINE would redeliver all of them */ }
  }

  // LINE retries anything that is not a 200, so always acknowledge once handled.
  return new Response("ok", { status: 200 });
});
