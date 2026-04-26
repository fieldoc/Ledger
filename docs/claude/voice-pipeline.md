# Voice & AI Capture Pipeline

Load when touching `voice/`, `capture/`, `GeminiCaptureRepository`, `VoiceParsingCoordinator`, `CaptureCommitOrchestrator`, or any voice overlay UI.

## Pipeline (2026-04-23: raw audio)

`MediaRecorder` records AAC/m4a blob → single Gemini multimodal call (audio `inlineData` + V2 prompt with "transcribe first" preamble) parses NL into structured intent + slots AND fills `rawTranscript` → `ListRouting` picks target list → draft preview → encoder click commits via `CaptureCommitOrchestrator` → Google Tasks API write.

Trigger: header voice button (encoder navigates to it, click starts, click again stops). Flow: full-screen dim + amplitude waveform → "Processing…" → draft card → confirm/cancel.

**Why not SpeechRecognizer anymore:** its segment restart loop triggered OEM "finished listening" chimes mid-sentence. Stream-mute workarounds were unreliable (no `MODIFY_AUDIO_SETTINGS`, OEM chime routing bypasses stream muting). MediaRecorder = no chimes, ever.

## Components

- **`VoiceCaptureManager`** (`voice/`) wraps `MediaRecorder` (MPEG_4/AAC, 44.1 kHz mono, 64 kbps). Exposes `audioResultCallback: ((ByteArray, String) -> Unit)?` that fires on stop with the m4a bytes + mime type. 2-minute hard cap. Amplitude polled every 100 ms via `maxAmplitude`.
- **`VoiceParsingCoordinator`** orchestrates audio → `parseVoiceInputFromAudio` → route → preview. Call `configureDayPlanningContext()` after `loadSettings()`.
- **`GeminiCaptureRepository`** — `parseVoiceInputFromAudio(apiKey, audioBytes, audioMimeType, …)` sends base64 audio `inlineData` + voice V2 prompt. 60 s read timeout (vs 35 s for text). `VoiceIntent` enum: `ADD / COMPLETE / RESCHEDULE / DELETE / QUERY / AMEND / DAY_PLAN`.
- **`ListRouting`** assigns parsed tasks to the correct Google Tasks list.
- **`CaptureCommitOrchestrator`** writes to Tasks API after user confirmation.
- **`PendingCaptureStore`** holds the draft awaiting confirmation.

## Gotchas

- **`cancel()` vs `resetToIdle()`**: `cancel()` stops the `MediaRecorder`, releases it, deletes the temp m4a from `cacheDir`, and flips state. `resetToIdle()` only flips the state flag — leaks the recorder + file. Use `cancel()` on every teardown path.
- **Temp m4a cleanup**: every teardown path (stop-success, cancel, error, destroy) must delete the temp file. Regression here = disk bloat over time.
- **Day organizer adjustment** reuses `parseVoiceInputFromAudio` and pulls only `rawTranscript` (discards the parse), then calls `dayOrganizerCoordinator.adjustPlan(text)`. Extra prompt work vs. a transcribe-only endpoint, but keeps the surface small.
- **Reschedule retry**: audio → `parseVoiceInputFromAudio` for transcript → feed transcript into `parseRescheduleRetry`. Two Gemini calls in that path (one multimodal, one text) is acceptable because retry is rare.
- **`VoiceIntentRouter.kt` was deleted 2026-04-12** — Gemini classifies all intents including `DAY_PLAN`. Do not reintroduce the router.
- If Gemini parsing fails OR transcription is empty, fall back to preview with `"(couldn't transcribe audio)"` title + clarification — don't silently drop.
- Double-click on a focused task opens the context menu (configurable: `DOUBLE_CLICK_WINDOW_MS`).
