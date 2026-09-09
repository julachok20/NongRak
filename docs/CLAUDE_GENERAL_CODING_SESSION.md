# Claude Desktop — General Coding Session

Source: Claude Desktop session `2f9d8e50-bfe9-4bbd-8907-0e26386a579e` (NongRak, branch `Track-YSC.02`).
Last user turns: 2026-08-28 → 2026-08-29. Compact recap written 2026-08-29 for Graphify.

## Shipped in this session (git, HEAD `79d1df4`)

- **Closet on chat, not Settings.** `Screen.MascotCloset` from `AIChatScreen` / companion switcher (`9bf333e`).
- **Call turn-taking.** End of user speech is silence against a rolling room-energy estimate, not a fixed pause (`7e274fd`, `a3d6210`).
- **TTS speaks immediately** once the model reply is ready (`9aafd63`).
- **Greet once, by name.** `chatWelcome` uses `profiles.full_name` (then username, then relationship). Chat prompt forbids re-greeting (`3f1227d`).
- **Spoken brevity must not kill recording.** Prefix restates the assistant role and keeps the `<AGENT_ACTION>` block (`4bcfa0f`).
- **Gemini audio in, not Android STT.** `VoiceTurnRecorder` captures 16 kHz mono PCM WAV; `ai-generate` sends `inlineData` audio to Gemini. Typhoon 2 Audio is **deferred to next week** (`79d1df4`). Installed on the A54; tests green.

## Decisions

- Audio rides the existing `ImageInput` `{data, mimeType}` path. No new wire format.
- Turn end is adaptive to a baby-noisy room. A fixed threshold is useless here.
- Multi-child: do not silently default to one child when the parent names another (e.g. "giga").
- Transcript open must not squash/fatten the mascot (requested; verify on device).
- Natural Thai TTS via **Typhoon 2 Audio** is next week, not this week.

## Still open after this chat

- Typhoon 2 Audio (user: talk next week).
- Transcript layout: mascot should keep its shape when the transcript is open.
- Press-to-interrupt / “waiting for answer” call chrome (requested; confirm on device).
- Wardrobe catalogue still empty by design. Closet exists; garments need art.
- Parent Community screens are wired in `AppNavigation` but the Kotlin files were untracked as of 2026-08-29.
- Welcome is parent-only (doctor portal card removed, 2026-08-25) — compile not verified in Cursor.
- 19 local commits not pushed.

## Do not re-propose

Rive, Fish Speech + Picovoice, Accessibility Service automation.
