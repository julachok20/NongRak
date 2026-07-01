# NongRak AI — Privacy & Security (maintainer guide)

Plain-language map of how children's data is protected and **where to change things**.
Last updated: 2026-06-13.

## Data protection layers

| Layer | What protects the data | Where to edit |
|---|---|---|
| **App key** | App ships the Supabase **anon** key (public by design). All real protection is server-side RLS. | `local.properties` → `SUPABASE_ANON_KEY` |
| **Row-Level Security** | Every table (`children`, `health_records`, `vaccine_records`, `medical_history`, `chat_messages`, …) is RLS-enabled and scoped so a parent sees **only their own** rows. Child tables filter via `child_id IN (SELECT id FROM children WHERE user_id = auth.uid())`. | Supabase → Auth Policies, or SQL |
| **Photos (parent + child)** | Stored in a **PRIVATE** `avatars` bucket. Each user locked to their own folder `{uid}/…`. Served only via **short-lived signed URLs**. | `StorageHelper.kt` (TTL + bucket name); storage policies in Supabase |
| **In transit** | HTTPS only — cleartext traffic disabled. | `AndroidManifest.xml` + `res/xml/network_security_config.xml` |
| **On device** | Backups disabled so health data can't be extracted via `adb backup` / cloud transfer. | `AndroidManifest.xml` (`allowBackup=false`) + `res/xml/data_extraction_rules.xml` |
| **Secrets in repo** | `.gitignore` keeps `local.properties` & keystores out of git. | `.gitignore` |

## Easy knobs
- **Photo link lifetime:** `StorageHelper.SIGNED_URL_TTL_SECONDS` (lower = more secure, default 3600s).
- **AI provider order:** `ClaudeClient.generateContent` (currently Gemini → Claude).

## ⚠️ Still TODO before public launch (need your decisions / content)
1. **Move AI API keys server-side.** Gemini/Claude keys are currently compiled into the APK (extractable). Proxy them through a Supabase Edge Function, or restrict each Gemini key to the app's package + signing cert in Google Cloud Console.
2. **Rotate all keys** — they were shared in plaintext during development.
3. **PDPA (Thailand) compliance — requires content/legal review:**
   - Parental **consent** checkbox at sign-up (data of minors).
   - **Privacy Policy** page content (what data, why, retention, contact of data controller).
   - **Data export** (user can download their data) — deletion already exists (account delete wipes `children` + `chat_sessions`).
   - Define a **data-retention** period and auto-purge.
4. **Encrypt session at rest** (currently SharedPreferences-backed). Optional but recommended.

> Note: the items above are technical scaffolding. Actual legal PDPA compliance should be confirmed with a qualified person — this app handles sensitive health data of minors.
