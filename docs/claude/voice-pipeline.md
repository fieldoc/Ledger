# Voice & AI Capture Pipeline

Load when touching `voice/`, `capture/`, `GeminiCaptureRepository`, `VoiceParsingCoordinator`, `CaptureCommitOrchestrator`, or any voice overlay UI.

## Pipeline

`SpeechRecognizer` (on-device STT) → Gemini parses NL into structured intent + slots → `ListRouting` picks target list → draft preview → encoder click commits via `CaptureCommitOrchestrator` → Google Tasks API write.

Trigger: header voice button (encoder navigates to it, click starts). Flow: full-screen dim + waveform → draft card → confirm/cancel.

## Components

- **`VoiceCaptureManager`** (`voice/`) wraps Android `SpeechRecognizer`. Always-continuous; do NOT pass a `continuous` parameter to `startListening()`.
- **`VoiceParsingCoordinator`** orchestrates voice → Gemini parse → route → commit. Call `configureDayPlanningContext()` after `loadSettings()`. Use the `refreshDayPlanningContext()` helper to avoid stale sleep hours.
- **`GeminiCaptureRepository`** sends transcriptions to Gemini. `VoiceIntent` enum: `ADD / COMPLETE / RESCHEDULE / DELETE / QUERY / AMEND / DAY_PLAN`.
- **`ListRouting`** assigns parsed tasks to the correct Google Tasks list.
- **`CaptureCommitOrchestrator`** writes to Tasks API after user confirmation.
- **`PendingCaptureStore`** holds the draft awaiting confirmation.

## Gotchas

- **`cancel()` vs `resetToIdle()`**: use `cancel()` for full teardown. `resetToIdle()` only flips the state flag — it does NOT stop the recognizer or remove handler callbacks. Wrong choice leaks active recognition.
- **`VoiceIntentRouter.kt` was deleted 2026-04-12** — Gemini classifies all intents including `DAY_PLAN`. Do not reintroduce the router.
- If Gemini parsing fails, fall back to raw transcription (don't drop the capture).
- Double-click on a focused task opens the context menu (configurable: `DOUBLE_CLICK_WINDOW_MS`).
