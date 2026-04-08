# Auto-Dev Plan: Microphone Capability Unification

**Created:** 2026-04-07
**Spec:** `docs/superpowers/specs/2026-04-07-mic-unification-design.md`

## Goal

Every mic button offers both task voice (ADD/COMPLETE/DELETE/RESCHEDULE/QUERY/AMEND) and day planning. The AI classifies intent from what the user says, routing to the correct pipeline. Remove redundant VoiceFab from calendar. Consistent visual treatment everywhere.

## Assumptions

1. Intent classification via keyword heuristic first (zero latency), Gemini fallback only when ambiguous
2. VoiceFab on CalendarScreen is redundant — remove it, keep header button only
3. PhoneHomeScreen's ListeningPulse → WaveformVisualizer for visual consistency
4. Day organizer needs: Gemini key + calendar scope. When missing, task voice still works, day planning shows error

## Task Breakdown

### T1: Create VoiceIntentRouter [NEW FILE] (independent)
**File:** `capture/router/VoiceIntentRouter.kt`
- Sealed class `RoutedIntent { TaskAction(transcription: String), DayPlanning(transcription: String) }`
- `fun classifyIntent(rawTranscription: String): RoutedIntent` — keyword-based, synchronous
- Planning keywords: "plan my day", "plan my morning/afternoon/evening", "organize my day/schedule", "what should I do today/next", "schedule my day"
- Everything else → TaskAction
- No Gemini call needed — keywords are sufficient and avoid latency

### T2: Add unified voice flow in TaskWallViewModel (depends: T1)
**File:** `viewmodel/TaskWallViewModel.kt`
- New `fun startUnifiedVoiceCapture()` — starts VoiceCaptureManager, same as `startVoiceInput()`
- New internal routing: intercept `rawResultCallback` in a wrapper that:
  1. Gets transcription from VoiceCaptureManager
  2. Calls `VoiceIntentRouter.classifyIntent(transcription)`
  3. If TaskAction → forward to VoiceParsingCoordinator (existing flow)
  4. If DayPlanning → forward to DayOrganizerCoordinator.startWithTranscription() (new method)
- New `DayOrganizerCoordinator.startWithTranscription(transcription, scope, providers...)` method
  - Like `startListening()` but skips the voice capture phase, goes straight to Processing with the given transcription
- New `_unifiedVoiceRouting` StateFlow to track routing phase (needed for UI to know which overlay to show)
- Existing `startVoiceInput()` and `startDayOrganizer()` stay functional (not broken)

### T3: Add startWithTranscription to DayOrganizerCoordinator (depends: nothing, parallel with T1)
**File:** `capture/DayOrganizerCoordinator.kt`
- New `fun startWithTranscription(transcription: String, scope, listProvider, taskProvider, eventsProvider, selectedCalendarId, weatherProvider?, wakeHour, sleepHour, focusedListTitle?)` 
- Same as `startListening()` but:
  - Skips `voiceCaptureManager.startListening()`
  - Sets `lastTranscription = transcription`
  - Goes directly to `_state.value = Processing()`
  - Calls `handleTranscription(transcription)` (existing private method)

### T4: Update CalendarScreen — remove VoiceFab, add task voice overlay (depends: T2)
**File:** `ui/screens/CalendarScreen.kt`
- Remove `VoiceFab` composable definition
- Remove VoiceFab rendering from the Box overlay
- Rename `PlanDayButton` → `VoiceButton` (same visual, new name/semantics)
- Change click handler from `startDayOrganizerWithPermission` to `startUnifiedVoiceWithPermission`
- Add new params: `voiceState: VoiceInputState`, `onStartUnifiedVoice`, `onStopVoice`, `onCancelVoice`, `onConfirmVoice`, `onDismissVoiceError`
- Add voice overlay (AnimatedVisibility) for task preview states — copy pattern from TaskWallScreen
- Keep DayOrganizerOverlay for when dayOrganizerState is active

### T5: Update TaskWallScreen — add day organizer overlay (depends: T2)
**File:** `ui/screens/TaskWallScreen.kt`
- Add params: `dayOrganizerState: DayOrganizerState`, `onStopDayOrganizerListening`, `onAcceptDayPlan`, `onAdjustDayPlan`, `onCancelDayOrganizer`, `onRetryDayOrganizer`
- Wire mic button to `startUnifiedVoiceCapture` instead of `startVoiceInput`
- Add `DayOrganizerOverlay` rendering when dayOrganizerState is not Idle
- Add key handling for DayOrganizerOverlay states

### T6: Update PhoneVoiceBottomSheet — consistent listening visual (depends: nothing, parallel)
**File:** `ui/components/PhoneVoiceBottomSheet.kt`
- Replace `ListeningPulse()` with `WaveformVisualizer(amplitudeLevel = 0.5f, isActive = true, modifier = Modifier.size(120.dp))`
- Need to pass `amplitudeLevel` into the bottom sheet (from VoiceInputState.Listening)
- Add day planning preview section for when DayPlanning intent is routed (compact plan blocks)

### T7: Wire in MainActivity (depends: T4, T5, T6)
**File:** `MainActivity.kt`
- Pass unified voice callbacks to CalendarScreen
- Pass dayOrganizerState + callbacks to TaskWallScreen
- Pass amplitudeLevel to PhoneVoiceBottomSheet

## Execution Order

```
T1 (VoiceIntentRouter) ──┐
                          ├── T2 (ViewModel) ──┬── T4 (CalendarScreen)  ─┐
T3 (DayOrg startWith) ──┘                     ├── T5 (TaskWallScreen)   ├── T7 (MainActivity)
T6 (PhoneBottomSheet) ─────────────────────────┘                        ─┘
```

- **Parallel group 1:** T1, T3, T6 (no dependencies between them)
- **Sequential:** T2 (after T1+T3) → T4+T5 (parallel) → T7

## Build Verification

- `gradlew assembleDebug` after T2
- `gradlew assembleDebug` after T4+T5+T6
- `gradlew assembleDebug` after T7
- Code review subagent after T7

## Status

- T1: pending
- T2: pending
- T3: pending
- T4: pending
- T5: pending
- T6: pending
- T7: pending
