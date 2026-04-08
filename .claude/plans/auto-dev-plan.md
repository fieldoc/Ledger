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
