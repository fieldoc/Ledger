# Voice & AI Capture Pipeline

Load when touching `voice/`, `capture/`, `GeminiCaptureRepository`, `VoiceParsingCoordinator`, `CaptureCommitOrchestrator`, or any voice overlay UI.

## Pipeline

`SpeechRecognizer` (continuous, with segment restart) → accumulated transcript text → text-only Gemini call (`parseVoiceInputV2` with V2 prompt) parses NL into structured intent + slots, fills `rawTranscript` → `ListRouting` picks target list → draft preview → encoder click (or screen tap during the touch-fallback period) commits via `CaptureCommitOrchestrator` → Google Tasks API write.

Trigger: header voice button (encoder navigates to it, click starts; click again stops). On the wall, tapping the dimmed Listening overlay also stops capture (added 2026-04-26 while the encoder was unwired). Phone uses a Stop pill in `PhoneVoiceBottomSheet`. Flow: full-screen dim + amplitude waveform → "Processing…" → draft card → confirm/cancel.

**Why SpeechRecognizer (not MediaRecorder):** stays text-first, so Gemini receives a small string instead of a base64 m4a blob — cheaper and faster for the common case. The trade-off is OEM "finished listening" chimes mid-sentence; mitigated by muting all chime-carrying streams across segment restarts (see Components below).

## Components

- **`VoiceCaptureManager`** (`voice/`) wraps Android `SpeechRecognizer` in always-continuous mode: each segment that ends with `onResults` is buffered into `accumulatedText` and the recognizer is restarted, so the user never hits a hard segment boundary. Exposes `rawResultCallback: ((String) -> Unit)?` that fires with the trimmed accumulated transcript on user-stop or idle timeout. 120 s hard cap (`LISTENING_TIMEOUT_MS`), 60 s no-speech idle timeout (`IDLE_TIMEOUT_MS`, polled every 5 s). Mutes `STREAM_MUSIC`, `STREAM_NOTIFICATION`, `STREAM_SYSTEM`, `STREAM_ALARM`, `STREAM_RING`, `STREAM_DTMF` for the entire session (across segment restarts) — single-stream muting isn't enough since OEMs route the chime through different streams.
- **`VoiceParsingCoordinator`** orchestrates transcript → `parseVoiceInputV2` → route → preview. Sets `voiceCaptureManager.rawResultCallback` in `configure()`. Call `configureDayPlanningContext()` after `loadSettings()` so calendar events / weather / wake-sleep hours flow into the prompt.
- **`GeminiCaptureRepository`** — `parseVoiceInputV2(apiKey, rawText, …)` sends a text-only V2 prompt. Default `GeminiRequestConfig`: 20 s connect, 35 s read. `VoiceIntent` enum: `ADD / COMPLETE / RESCHEDULE / DELETE / QUERY / AMEND / DAY_PLAN`.
- **`ListRouting`** assigns parsed tasks to the correct Google Tasks list.
- **`CaptureCommitOrchestrator`** writes to Tasks API after user confirmation.
- **`PendingCaptureStore`** holds the draft awaiting confirmation.

## Gotchas

- **`cancel()` vs `resetToIdle()`**: `cancel()` invalidates the pending session (via `sessionId++`), removes both timeout callbacks, unmutes streams, cancels + destroys the `SpeechRecognizer`, clears the listener, and flips state to Idle. `resetToIdle()` only flips the state flag — leaves the recognizer alive. Use `cancel()` on every teardown path; `resetToIdle()` is only for the day-organizer adjust hand-off where the recognizer's already done.
- **Stop vs cancel semantics**: `stopListening()` sets `userRequestedStop = true` and calls `SpeechRecognizer.stopListening()`; the next `onResults` (or recoverable `onError` with non-empty buffer) delivers the accumulated text via `rawResultCallback`. `cancel()` discards the buffer and never fires the callback.
- **Recoverable errors restart, don't end**: `ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_NETWORK`, `ERROR_NETWORK_TIMEOUT`, `ERROR_SERVER`, `ERROR_CLIENT`, `ERROR_AUDIO` all restart the recognizer (still muted) unless the user already requested stop. Only an explicit stop, the 60 s idle check, or the 120 s hard cap should ever terminate listening.
- **Mute persists across restarts**: `restartRecognizer()` deliberately keeps streams muted between segments. Unmuting in the gap lets the next segment-end chime play, which the user perceives as being cut off mid-thought. Streams unmute only on `stopListening` / `cancel` / `deliverAccumulatedText` / `destroy`.
- **Day organizer adjustment** swaps `rawResultCallback` temporarily (see `TaskWallViewModel.startDayPlannerVoiceAdjust`): captures the original callback, installs one that restores the original, calls `resetToIdle()`, and forwards `rawText` to `dayOrganizerCoordinator.adjustPlan(text)`. No second Gemini call for transcription — `SpeechRecognizer` already produced text.
- **Reschedule retry**: arms `rescheduleRetryContext`, then on next transcript `VoiceParsingCoordinator` calls `parseRescheduleRetry` (text-only Gemini call) instead of `parseVoiceInputV2`. Context is consumed on first use — one retry only.
- **`VoiceIntentRouter.kt` was deleted 2026-04-12** — Gemini classifies all intents including `DAY_PLAN`. Do not reintroduce the router.
- If Gemini parsing fails OR transcription is empty, `VoiceCaptureManager.showPreviewFallback` builds a single-task ADD draft from the raw transcript (with a clarification message) so the user can still confirm — don't silently drop.
- Double-click on a focused task opens the context menu (configurable: `DOUBLE_CLICK_WINDOW_MS`).
