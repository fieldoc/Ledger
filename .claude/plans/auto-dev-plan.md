# Auto-Dev Plan: Branch 2 — Context Enrichment

**Created:** 2026-04-02
**Spec:** `docs/superpowers/specs/2026-04-02-context-enrichment-design.md`

## Task Dependency Map

```
T1: Add preferredTime to Task + GoogleTasksRepository
T2: Enrich ExistingTaskRef data class (depends: T1)
T3: Enrich buildDayPlanGeminiPrompt signature + body (depends: T2)
T4: Update DayOrganizerCoordinator to pass new params (depends: T3)
T5: Update TaskWallViewModel to provide all new data (depends: T4)
T6: Add connectivity guard to voice/day-organizer entry points (independent)
```

## Parallel Groups

- **Group A (T1 → T2 → T3 → T4 → T5):** Sequential chain — each task modifies a different file but depends on the prior's type changes.
- **Group B (T6):** Independent — only touches ViewModel, no type dependencies.

Since Group A is a chain and T6 is small, execute everything sequentially.

## Execution Order

### T1: Add `preferredTime` to Task model [Task.kt, GoogleTasksRepository.kt]
- [ ] Add `val preferredTime: String? = null` to `Task` data class (after `cleanNotes`)
- [ ] In `GoogleTasksRepository.toAppTask()`: add `preferredTime = decoded.preferredTime`
- **Status:** pending

### T2: Enrich `ExistingTaskRef` [GeminiCaptureRepository.kt]
- [ ] Add fields: `dueDate: LocalDate? = null`, `priority: TaskPriority = TaskPriority.NORMAL`, `preferredTime: String? = null`, `recurrenceInfo: String? = null`
- [ ] All existing callers use named args or defaults, so backward-compatible
- **Status:** pending

### T3: Enrich `buildDayPlanGeminiPrompt()` [GeminiCaptureRepository.kt]
- [ ] Add params: `weatherForecast: String? = null`, `wakeHour: Int = 7`, `sleepHour: Int = 23`, `focusedListTitle: String? = null`
- [ ] In systemInstruction:
  - Replace rule 3 energy curve with dynamic wake/sleep hours
  - Add rule 11: weather-aware scheduling
  - Add rule 12: weekend/weekday awareness
- [ ] In userContent:
  - Add WEATHER section (if non-null)
  - Add day-of-week line: "TODAY IS: WEEKDAY (Tuesday)" or "TODAY IS: WEEKEND (Saturday)"
  - Add focused list line (if non-null)
  - Enrich task lines: include dueDate, priority, preferredTime, recurrenceInfo
- [ ] Replace hardcoded "07:00" with `wakeHour`
- **Status:** pending

### T4: Update `DayOrganizerCoordinator` [DayOrganizerCoordinator.kt]
- [ ] Add to `startListening()` params: `weatherProvider: (suspend () -> String?)? = null`, `wakeHour: Int = 7`, `sleepHour: Int = 23`, `focusedListTitle: String? = null`
- [ ] Store as private fields
- [ ] In `handleTranscription()`: call weatherProvider, pass new params to `buildDayPlanGeminiPrompt()`
- **Status:** pending

### T5: Update `TaskWallViewModel` [TaskWallViewModel.kt]
- [ ] In `startDayOrganizer()`:
  - Enrich taskProvider to build `ExistingTaskRef` with dueDate, priority, preferredTime, recurrenceInfo
  - Enrich eventsProvider to include duration and all-day flag in string
  - Add weatherProvider lambda: format today's weather from `_weatherForecast`
  - Pass `sleepEndHour.value` as wakeHour, `sleepStartHour.value` as sleepHour
  - Pass focused list title from `_uiState.value.selectedTaskListTitle`
- **Status:** pending

### T6: Connectivity guard [TaskWallViewModel.kt]
- [ ] In `startVoiceInput()`: early return with error if `!isOnline.value`
- [ ] In `startDayOrganizer()`: early return with transient message if `!isOnline.value`
- **Status:** pending

## Build Verification
- [ ] `gradlew assembleDebug` after T5 (all type changes done)
- [ ] `gradlew assembleDebug` after T6

## Review Checkpoint
- [ ] Code review after all tasks complete
- [ ] Verify prompt output reads correctly (mentally trace a sample scenario)

---

# Auto-Dev Plan: Branch happy-haibt — Day Planner Voice Cutoff Fixes

**Created:** 2026-04-07

## Problem Statement

Day planning mode gets cut off too early. Three root causes identified:
1. Android `SpeechRecognizer` ends sessions after ~10–15s of speech (hard cap, not configurable) AND 4s "possibly-complete" silence threshold triggers on thinking pauses
2. Gemini `readTimeoutMs=45s` is too short for `gemini-2.5-flash` with large day-plan prompts
3. `VoiceCaptureManager` errors never reach `DayOrganizerCoordinator` → stuck in Listening state

## Design Decisions

### Fix 1: Continuous Recognition Mode (VoiceCaptureManager.kt)

Add `continuous: Boolean = false` param to `startListening()`. When `true`:
- Extract the recognition listener to a private inner class (`InnerRecognitionListener`) so it can be re-attached on restart
- Extract intent construction to `buildSpeechIntent(continuous: Boolean)` so silence thresholds can vary by mode
- `onResults` buffers the new text into `accumulatedText: StringBuilder`, then restarts the recognizer instead of delivering
- `stopListening()` sets `userRequestedStop = true` first — the next `onResults` after `stopListening()` delivers the final concatenated text via `rawResultCallback`
- Silence thresholds when continuous: `POSSIBLY_COMPLETE = 8000L`, `COMPLETE = 10000L`

**Error callback addition:** Add `errorCallback: ((String) -> Unit)?` field (similar to `rawResultCallback`). Called from `onError` after the error message is computed. This lets callers observe errors without polling state.

### Fix 2: Gemini Timeout (GeminiCaptureRepository.kt)

Change both `callGeminiForDayPlan` and `callGeminiForDayPlanMultiTurn`:
- `readTimeoutMs = 45_000` → `readTimeoutMs = 90_000`

### Fix 3: Error Propagation (DayOrganizerCoordinator.kt)

In `startListening()` and `startAdjustment()`, set `voiceCaptureManager.errorCallback` to a lambda that transitions `_state` to `DayOrganizerState.Error(message, canRetry = true)`.

In `cancel()`, clear `voiceCaptureManager.errorCallback = null`.

## Task Dependency Map

```
F1: VoiceCaptureManager — continuous mode + errorCallback  [voice/VoiceCaptureManager.kt]
F2: DayOrganizerCoordinator — use continuous=true + wire errorCallback  [capture/DayOrganizerCoordinator.kt]  (depends: F1)
F3: GeminiCaptureRepository — increase readTimeoutMs  [capture/repository/GeminiCaptureRepository.kt]  (independent)
```

F1 and F3 can run in parallel. F2 depends on F1 completing first.

## Execution Order

### F1 + F3 (parallel): Core changes
- **F1** `VoiceCaptureManager.kt`:
  - [ ] Extract `buildSpeechIntent(continuous: Boolean)` private fun
  - [ ] Extract recognition listener to named inner class `InnerListener`
  - [ ] Add `continuous: Boolean = false` param to `startListening()`
  - [ ] Add `private var continuousMode = false`, `userRequestedStop = false`, `accumulatedText = StringBuilder()`
  - [ ] In `startListening()`: set `continuousMode`, reset flags/buffer, use `buildSpeechIntent(continuous)`
  - [ ] In `onResults`: branch on `continuousMode && !userRequestedStop` → buffer + restart; else deliver
  - [ ] In `stopListening()`: if continuousMode, set `userRequestedStop = true` before calling `stopListening()`
  - [ ] Add `var errorCallback: ((String) -> Unit)? = null` field
  - [ ] In `onError`: call `errorCallback?.invoke(message)` after computing message
  - [ ] In `cancel()` / `destroy()`: clear `errorCallback = null`

- **F3** `GeminiCaptureRepository.kt`:
  - [ ] `callGeminiForDayPlan`: `readTimeoutMs = 90_000`
  - [ ] `callGeminiForDayPlanMultiTurn`: `readTimeoutMs = 90_000`

### F2: Wire coordinator to use continuous mode + error propagation
- **F2** `DayOrganizerCoordinator.kt`:
  - [ ] `startListening()`: call `voiceCaptureManager.startListening(continuous = true)`
  - [ ] `startListening()`: set `voiceCaptureManager.errorCallback = { msg -> _state.value = DayOrganizerState.Error(msg, canRetry = true) }`
  - [ ] `startAdjustment()`: also call `startListening(continuous = true)` and set errorCallback
  - [ ] `cancel()`: clear `voiceCaptureManager.errorCallback = null`

## Build Verification
- [ ] `gradlew assembleDebug` after F1+F3 complete
- [ ] `gradlew assembleDebug` after F2 complete

## Review Checkpoint
- [ ] Code review subagent after all fixes complete
- [ ] Check: does DayOrganizerOverlay's "Click to finish speaking" still work correctly?
- [ ] Check: non-day-planner voice capture unaffected (continuous=false by default)

---

# Auto-Dev Plan: Branch 3 — Coordinator Robustness

**Created:** 2026-04-08

## Feature List

| # | Feature | Files |
|---|---------|-------|
| F1 | Partial event creation recovery (track success/failure, retry failed blocks) | DayOrganizerCoordinator.kt, DayOrganizerOverlay.kt |
| F2 | Store created event IDs during acceptPlan() for rollback | DayOrganizerCoordinator.kt |
| F3 | Surface adjustment failures with error toast instead of silent revert | DayOrganizerCoordinator.kt, DayOrganizerOverlay.kt |
| F4 | 60s application-level timeout around Gemini call → Error(canRetry=true) | DayOrganizerCoordinator.kt |
| F5 | Adjustment attempt counter to prevent infinite loops | DayOrganizerCoordinator.kt |
| F6 | Handle cancel-during-processing race condition | DayOrganizerCoordinator.kt |
| F7 | Log Gemini retry attempts and timing (already partially done; add attempt timestamps) | GeminiCaptureRepository.kt |
| F8 | Distinguish client errors (400/403) from server errors (500) in messages | GeminiCaptureRepository.kt |
| F9 | Handle empty/whitespace transcription before sending to Gemini | DayOrganizerCoordinator.kt |
| F10 | Haptic feedback on state transitions (listening start, plan ready, error, accept complete) | DayOrganizerOverlay.kt, Haptics.kt |

## Assumptions

1. **F1 retry**: "Retry failed blocks" re-attempts only the failed blocks from the last `acceptPlan()` call — not a full restart. Adds `PartialSuccess(createdCount, failedBlocks)` state.
2. **F2 rollback**: Storing IDs is the requirement. No rollback UI is built yet (future work).
3. **F3 transient error**: Use a `SharedFlow<String>` in the coordinator for adjustment errors. UI collects and shows a brief overlay message. State reverts to `PlanReady` as before.
4. **F4 timeout**: 60s is applied around the entire Gemini block (not just HTTP). `TimeoutCancellationException` caught explicitly with a user-friendly message.
5. **F5 counter**: Max 10 adjustment attempts. Reset on cancel and on new initial plan.
6. **F6 race**: Guard each state-setting in the parseJob with a check: if already `Idle`, don't overwrite.
7. **F7 logging**: Add per-attempt timestamp logging to `HttpGeminiApiClient`. Latency callback already fires.
8. **F8 errors**: 400 → "Invalid request", 401/403 → "API key error", 500/503 → stay retryable + "Server error".
9. **F9 transcription**: Improve empty-transcription message. Make adjustment path also emit an error event (not just silent revert) for whitespace input.
10. **F10 haptics**: Use `LaunchedEffect(state)` in `DayOrganizerOverlay` with `LocalView.current` + `LocalContext.current`. Add `ERROR` pattern to `AppHapticPattern`.

## Dependency Graph

```
F2 (IDs) ──┐
F1 (partial) depends on F2 (to store IDs on success)
F3 (adj error) ─── independent of F1/F2
F4 (timeout) ────── independent
F5 (counter) ────── independent
F6 (race) ───────── independent
F7 (logging) ────── independent (GeminiCaptureRepository.kt)
F8 (errors) ─────── independent (GeminiCaptureRepository.kt, same file as F7)
F9 (transcription) ─ independent
F10 (haptics) ────── independent (UI files)
```

## Parallel Groups

- **Group A** (sequential, all in DayOrganizerCoordinator.kt): F2 → F1, then F3, F4, F5, F6, F9
- **Group B** (parallel with A): F7 + F8 in GeminiCaptureRepository.kt
- **Group C** (after A): F10 in DayOrganizerOverlay.kt + Haptics.kt

## Execution Order

### Step 1: DayOrganizerCoordinator.kt changes (F1–F6, F9) — all in one file

#### F2: Store created event IDs
- [ ] Add `private var lastCreatedEventIds: MutableList<String> = mutableListOf()`
- [ ] Clear in `cancel()`
- [ ] In `acceptPlan()`, collect event IDs from `Result<CalendarEvent>.getOrNull()?.id`

#### F1: Partial event creation recovery
- [ ] Add `PartialSuccess(createdCount: Int, failedBlocks: List<PlanBlock>)` to `DayOrganizerState`
- [ ] In `acceptPlan()`: collect succeeded/failed separately; if mixed → `PartialSuccess`; if all fail → `Error(canRetry=false)` (or true?); if all succeed → `Idle`
- [ ] Add `suspend fun retryFailedBlocks(blocks: List<PlanBlock>): Result<Int>` that re-runs createEvent only for the given blocks

#### F3: Adjustment error events
- [ ] Add `private val _adjustmentErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)`
- [ ] Expose `val adjustmentErrors: SharedFlow<String> = _adjustmentErrors.asSharedFlow()`
- [ ] In `handleAdjustmentTranscription` catch block: emit error to `_adjustmentErrors` before reverting to PlanReady

#### F4: 60s timeout
- [ ] Import `kotlinx.coroutines.withTimeout`, `kotlinx.coroutines.TimeoutCancellationException`
- [ ] In `handleTranscription`: wrap Gemini call with `withTimeout(60_000L)`, catch `TimeoutCancellationException`
- [ ] In `handleAdjustmentTranscription`: same

#### F5: Adjustment counter
- [ ] Add `private var adjustmentAttempts = 0`, `companion object { const val MAX_ADJUSTMENTS = 10 }`
- [ ] Increment at top of `handleAdjustmentTranscription`
- [ ] If > MAX: emit `Error("Too many adjustments. Accept or cancel the plan.", canRetry = false)`
- [ ] Reset in `cancel()` and in `handleTranscription` (new initial plan resets counter)

#### F6: Cancel race condition
- [ ] In `parseJob` before each `_state.value = ...` assignment: `if (_state.value is DayOrganizerState.Idle) return@launch`

#### F9: Transcription validation
- [ ] Improve error message in `handleTranscription` empty check
- [ ] In `handleAdjustmentTranscription` empty check: emit to `_adjustmentErrors` + revert to PlanReady (don't silently drop)

### Step 2: GeminiCaptureRepository.kt changes (F7, F8)

#### F7: Logging
- [ ] In `HttpGeminiApiClient.generateContent()`: record `attemptStart = System.currentTimeMillis()` per iteration, log elapsed after each attempt

#### F8: Error message distinction
- [ ] In `doRequest()`: replace generic error message with per-code variants:
  - 400 → "Invalid request: $sanitized"
  - 401, 403 → "API key or permission error: $sanitized"
  - 500, 503 → already `RetryableGeminiException` (add "Server error: " prefix)
  - other → existing "Gemini request failed ($responseCode): $sanitized"

### Step 3: UI changes (F10, F1 overlay)

#### F10: Haptics in DayOrganizerOverlay.kt + Haptics.kt
- [ ] Add `ERROR` to `AppHapticPattern` enum in `Haptics.kt`
- [ ] Add ERROR haptic handling (short double-pulse using `vibrateFallback`)
- [ ] In `DayOrganizerOverlay`: `val view = LocalView.current`, `val context = LocalContext.current`
- [ ] `var previousState by remember { mutableStateOf<DayOrganizerState>(DayOrganizerState.Idle) }`
- [ ] `LaunchedEffect(state)` → fire haptic based on transition: Listening→NAVIGATE, PlanReady→CONFIRM, Error→ERROR, Confirming→Idle (accept complete) → CONFIRM
- [ ] Update previousState after haptic

#### F1 overlay: PartialSuccess UI
- [ ] Add `PartialSuccessContent(createdCount, failedCount, onRetryFailed, onCancel)` composable in `DayOrganizerOverlay.kt`
- [ ] Wire up in `when(state)` block
- [ ] Add `onRetryFailed: (List<PlanBlock>) -> Unit` param to `DayOrganizerOverlay`
- [ ] In callers (TaskWallScreen), wire `onRetryFailed` to call `coordinator.retryFailedBlocks(blocks)`

#### F3 overlay: Collect adjustment errors
- [ ] In the composable calling `DayOrganizerOverlay`, collect `coordinator.adjustmentErrors` in a `LaunchedEffect` and show a snackbar/toast overlay

## Build Verification
- [ ] `gradlew assembleDebug` after Step 1 (coordinator changes)
- [ ] `gradlew assembleDebug` after Step 2 (repository changes)
- [ ] `gradlew assembleDebug` after Step 3 (UI changes) — final build

## Sanity Checks
- Encoder navigability: `PartialSuccessContent` must be navigable (click = accept, encoder navigation = dismiss). Since this is a wall-mounted device with only 3 encoder inputs, keep actions minimal.
- Error state `canRetry` semantics are consistent: true = retry same action, false = requires user decision.
- Adjustment counter resets when initial plan re-generated (new voice session).
- No UI animations added that violate the "calm, premium, no gamification" philosophy.

## Status
- [ ] Step 1: DayOrganizerCoordinator.kt — PENDING
- [ ] Step 2: GeminiCaptureRepository.kt — PENDING
- [ ] Step 3: UI — PENDING
