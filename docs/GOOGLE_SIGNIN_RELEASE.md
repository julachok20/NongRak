# Google Sign-In — Release checklist (NongRak / LullabyAI)

Use this before shipping a **release** (Play Store) build with Google Sign-In enabled.

## 1. OAuth client IDs (Google Cloud Console)

Project must have **two** Android OAuth clients plus the **Web client** used for ID tokens:

| Client | Purpose |
|--------|---------|
| **Web application** | Supabase Google provider + `BuildConfig.GOOGLE_WEB_CLIENT_ID` |
| **Android (debug)** | Local dev — SHA-1 from debug keystore |
| **Android (release)** | Play Store — SHA-1 from **Play App Signing** certificate |

**Debug SHA-1 (reference):** `AE:4E:B5:2C:97:8B:B2:CA:AE:36:92:AC:D9:AF:8C:14:DB:0F:B1:E7`

**Release SHA-1:** Play Console → Your app → Setup → App integrity → **App signing key certificate** → copy SHA-1 and add it to a new Android OAuth client (same package name `com.publish.NongRak`). Cloud Console key name in use: **NongRak-Release**.

**Web client ID (Supabase):** `423936452077-21155uohucdv6ng1ege7pb1r4phiroj4.apps.googleusercontent.com`

Local property (repo root `local.properties`, not committed):

```properties
GOOGLE_WEB_CLIENT_ID=423936452077-21155uohucdv6ng1ege7pb1r4phiroj4.apps.googleusercontent.com
```

## 2. Supabase Auth

1. Dashboard → **Authentication** → **Providers** → **Google** → Enabled.
2. Client ID = Web client ID above.
3. Client Secret = from Google Cloud (Web client).
4. **Automatic linking** (same email): Authentication → Settings → enable linking so email/password and Google share one user.

## 3. App build

- `ENABLE_GOOGLE_SIGN_IN = true` in `LoginScreen.kt` (and doctor screens).
- `GoogleAuthHelper` uses `BuildConfig.GOOGLE_WEB_CLIENT_ID` — must be non-empty in release builds.
- ProGuard keeps model classes (`proguard-rules.pro`).

## 4. Verify on device

1. Install **release** or **internal testing** build from Play (not sideload debug APK for release SHA-1 test).
2. Parent: Login → Continue with Google.
3. Doctor: Doctor Sign In → Continue with Google → complete profile if new.
4. Existing email account: sign in with email once, then Google with same email → same profile (linked identity).

## 5. Common errors

| Symptom | Fix |
|---------|-----|
| `developer_error` / status 10 | Wrong SHA-1 or package name on Android OAuth client |
| Token rejected / invalid | Web client ID mismatch between app and Supabase |
| User already registered | Enable automatic linking; doctor portal uses `accountType=doctor` on Google sign-in |
