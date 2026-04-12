# Voice System Overhaul: Tap-to-Finish + Unified Routing

**Date:** 2026-04-12
**Status:** Design approved, pending implementation

## Problem Statement

The voice input system has three interrelated problems:

1. **Aggressive silence timeouts** — Android SpeechRecognizer's end-of-speech detection (4-6 seconds of silence) cuts users off mid-thought. When listing tasks or thinking about your day, a few seconds of trailing off is normal. The system should wait for an explicit "I'm done" signal.

2. **Fragmented voice paths** — Task capture and day planning are two separate voice flows with different entry points, different state machines, and different UX. The user must know to say "plan my day" to trigger day planning. Phone mode lacks day planning entirely. It feels like multiple apps stitched together.

3. **Inconsistent behavior across modes** — Wall mode uses continuous recognition; phone mode uses single-utterance. Wall supports reschedule auto-confirm; phone doesn't. Different hint text, different action buttons, different capabilities.

## Design Decisions

### D1: Always-Continuous + Tap-to-Stop + Idle Fallback

Every voice session uses continuous mode. The user explicitly taps to stop capture.

**Stopping:**
- **Wall:** Encoder click stops capture
- **Phone:** Tap the Stop button

**Idle timeout (60s):** An app-level timer monitors `amplitudeLevel` from `onRmsChanged`. When normalized RMS stays below 0.05 for 60 continuous seconds, the timeout fires:
- If accumulated text exists → gracefully stop and route to Gemini (as if user tapped stop)
- If no accumulated text → silently cancel, return to idle

Any speech-level RMS resets the timer. The existing 120s hard timeout remains as an absolute ceiling.

**SpeechRecognizer silence thresholds** are set to ~59,000ms (just under the idle timeout) so Android never triggers end-of-speech before the app's own idle logic. The `continuous` parameter on `startListening()` is removed — continuous is always on.

**Constants:**

| Constant | Current | New |
|----------|---------|-----|
| `COMPLETE_SILENCE_MS` | 5-6s | 59,000ms |
| `POSSIBLY_COMPLETE_SILENCE_MS` | 4s | 59,000ms |
| `LISTENING_TIMEOUT_MS` | 120s | 120s (unchanged) |
| New: `IDLE_TIMEOUT_MS` | — | 60,000ms |
| New: `IDLE_RMS_THRESHOLD` | — | 0.05 (normalized) |

### D2: Unified Post-Capture Gemini Classification

After the user stops capture, the full transcript goes to a single Gemini call that both classifies intent and parses content.

**Intent types returned by Gemini:**

| Intent | Example | Route |
|--------|---------|-------|
| `ADD` | "Buy groceries tomorrow and call the dentist by Friday" | Task creation pipeline |
| `COMPLETE` | "Mark the dentist appointment as done" | Task completion pipeline |
| `DELETE` | "Remove the old grocery list item" | Task deletion pipeline |
| `RESCHEDULE` | "Move the dentist to Friday" | Reschedule pipeline |
| `QUERY` | "What do I have due this week?" | Query/summary display |
| `DAY_PLAN` | "What should I do today, I have a meeting at 2" | Day Organizer pipeline |
| `AMEND` | "Change that last task to say milk not eggs" | Amendment pipeline |

**Key decisions:**
- One API call, not two — classification + parsing happen together
- `VoiceIntentRouter.kt` is **deleted** — no more local keyword matching
- Day planning context (tasks, calendar, weather) is always included in the Gemini call so it can make informed routing decisions
- **Fallback:** If Gemini is unavailable, treat transcript as a simple `ADD` with raw text as title

The `GeminiCaptureRepository.parseVoiceInputV2()` prompt is expanded to include an `intent` field in the response JSON. The existing parsed fields (title, dueDate, targetList, etc.) remain unchanged for `ADD`/`AMEND`/`RESCHEDULE` intents.

### D3: Day Organizer Voice Decoupling

The Day Organizer no longer owns voice capture. It becomes a pure plan-generation engine.

**New flow:**
1. User speaks → tap stop → Gemini classifies as `DAY_PLAN`
2. `VoiceParsingCoordinator` calls `DayOrganizerCoordinator.generatePlan(transcript)`
3. Day Organizer goes straight to `Processing` state (skips `Listening`)
4. Plan returns → `PlanReady` → user reviews with encoder

**For adjustments:**
1. User navigates to "Adjust" action on plan preview
2. A new unified voice capture session starts (same tap-to-stop flow)
3. Transcript goes to Gemini with existing plan as context
4. Day Organizer receives adjustment text and regenerates

**Removed from DayOrganizerCoordinator:**
- `Listening` state
- `Adjusting` state (with `amplitudeLevel`)
- `startListening()` and `startAdjustment()` methods
- Direct `VoiceCaptureManager` dependency

**Simplified state machine:**
```
Idle → Processing → PlanReady → Confirming → Done
                  ↗               ↓
        (adjustment text)    PartialSuccess / Error
```

Down from 8 states to 6.

### D4: Consistent UI/UX Across Wall and Phone

Both form factors share the same voice interaction model, adapted for input method.

**Wall (TaskWallScreen):**
- Full-screen dim + waveform visualizer (unchanged visual design)
- Hint text: "Speak naturally — click to finish"
- Hint stays visible throughout capture (no auto-fade)
- Encoder click = stop. Rotate is consumed/ignored during capture.
- Preview cards show results for all intent types

**Phone (PhoneVoiceBottomSheet):**
- Continuous mode (was single-utterance)
- Full intent support (day planning, reschedule — was missing)
- Stop = tap Stop button (only way to end capture)
- Hint text: "Speak naturally — tap Stop when done"
- Same preview card structure as wall for all intent types

**Shared:**
- No "plan my day" instruction anywhere in the UI
- Preview card intent label ("Draft Task", "Day Plan", "Reschedule") is the only routing indicator
- Error states identical across modes
- No partial transcript displayed during listening — waveform shows amplitude only

### D5: Edge Cases

| Scenario | Behavior |
|----------|----------|
| Speak, pause 30s to think, speak again | Idle timer resets on second speech. No interruption. |
| Speak, walk away | 60s after last speech → route accumulated text to Gemini |
| Activate voice, never speak, walk away | 60s → silent cancel, return to idle |
| Speak for 2+ minutes straight | 120s hard timeout → route to Gemini |
| Network dies mid-capture | Capture continues locally. Gemini failure → fallback to raw ADD |
| Click stop with empty transcript | Return to idle silently. No Gemini call. |

## Files Changed

### Modified (9 files)

| File | Change |
|------|--------|
| `voice/VoiceCaptureManager.kt` | Remove `continuous` param, always-continuous, idle timeout timer, bump silence thresholds |
| `capture/VoiceParsingCoordinator.kt` | Single orchestrator for all intents, day planning routing |
| `capture/repository/GeminiCaptureRepository.kt` | Prompt returns `intent` field, day planning context always included |
| `capture/DayOrganizerCoordinator.kt` | Remove Listening/Adjusting states, add `generatePlan(transcript)` entry |
| `viewmodel/TaskWallViewModel.kt` | Remove `unifiedVoiceRouting`, remove VoiceIntentRouter usage, simplify voice start/stop |
| `ui/screens/TaskWallScreen.kt` | Update hint text, click-to-stop, consume rotate during capture |
| `ui/components/PhoneVoiceBottomSheet.kt` | Continuous mode, all intent types, match wall preview structure |
| `viewmodel/PhoneCaptureViewModel.kt` | Use VoiceParsingCoordinator, add reschedule + day planning |
| `ui/components/DayOrganizerOverlay.kt` | Wire adjustment to unified voice session |

### Deleted (1 file)

| File | Reason |
|------|--------|
| `capture/router/VoiceIntentRouter.kt` | Replaced by Gemini classification |

### Not Changed

- `WaveformVisualizer.kt`, `GeminiJsonParser.kt`, `ClockHeader.kt`, theme files — no changes needed
