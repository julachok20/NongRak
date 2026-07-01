# Lullaby AI — Setup Guide

## Step 1: Download Fonts

The app uses two Google Fonts. Download and place them in:
`app/src/main/res/font/`

### Files needed:
| File name | Font | Style |
|---|---|---|
| `be_vietnam_pro_regular.ttf` | Be Vietnam Pro | Regular |
| `be_vietnam_pro_semibold.ttf` | Be Vietnam Pro | SemiBold |
| `be_vietnam_pro_bold.ttf` | Be Vietnam Pro | Bold |
| `be_vietnam_pro_extrabold.ttf` | Be Vietnam Pro | ExtraBold |
| `plus_jakarta_sans_regular.ttf` | Plus Jakarta Sans | Regular |
| `plus_jakarta_sans_semibold.ttf` | Plus Jakarta Sans | SemiBold |
| `plus_jakarta_sans_bold.ttf` | Plus Jakarta Sans | Bold |

Download from: https://fonts.google.com/specimen/Be+Vietnam+Pro
Download from: https://fonts.google.com/specimen/Plus+Jakarta+Sans

## Step 2: Open in Android Studio

1. Open Android Studio
2. File → Open → Select `D:\Claude-Access\LullabyAI`
3. Wait for Gradle sync (may take 3-5 minutes on first run)
4. Android Studio will download all dependencies automatically

## Step 3: Set up Android Emulator

1. Tools → Device Manager → Create Virtual Device
2. Choose: Pixel 6 (or any phone)
3. System Image: API 34 (Android 14) — download if needed
4. Click Finish

## Step 4: Run the App

1. Select your emulator from the device dropdown
2. Click the green ▶ Run button (or Shift+F10)
3. Wait for build and install (~2 minutes first time)

## Step 5: Google Sign-In Setup (Optional)

To enable "เข้าสู่ระบบด้วย Google":
1. Go to https://supabase.com → Your Project → Authentication → Providers → Google
2. Enable Google and get the Client ID/Secret from Google Cloud Console
3. Follow Supabase docs: https://supabase.com/docs/guides/auth/social-login/auth-google

## Step 6: Verify Gemini API Key

Your Gemini API key: `AQ.Ab8RN6IFpQdUF6yX2f0G-TQ2gPPISDAhIZl8HqwuaXvJ6VTOQQ`

⚠️ Note: Standard Gemini API keys from Google AI Studio start with `AIza...`
If AI chat doesn't work, get a fresh key from: https://aistudio.google.com/apikey

---

## Credentials (already configured in local.properties)
- Supabase URL: https://rdylsezwjdujvllyjhqb.supabase.co
- All credentials are in `local.properties` (never commit this file to git)
