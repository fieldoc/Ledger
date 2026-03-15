# Ship-Ready Audit — 2026-03-15

Comprehensive review of the Ledger (ToDoWallApp) codebase across 7 layers.
All findings are prioritized by severity and grouped by action type.

---

## Executive Summary

| Severity | Count | Description |
|----------|-------|-------------|
| **FIX** | 18 | Broken, will crash, or blocks a core user flow |
| **WIRE** | 10 | Code exists but isn't connected to anything |
| **REMOVE** | 9 | Dead code to delete |
| **POLISH** | 30 | Works but fragile, messy, or suboptimal |
| **NOTE** | 15 | Observations, no action needed now |

**Top 5 highest-impact fixes:**
1. Voice overlay completely missing in wall mode (spec'd but unbuilt)
2. `_uiState.value` race condition throughout ViewModel (every state update can lose data)
3. Completed tasks unreachable by encoder (can't undo/delete completed tasks)
4. PromotionSheet has zero encoder handling (can't schedule tasks from wall)
5. Gemini model `gemini-3.1-flash-lite-preview` will be retired (all AI parsing breaks)

---

## FIX — Must Fix Before Shipping

### F1. Voice overlay not rendered in wall mode
- **Layer**: 3 — UI/UX Gaps
- **Location**: `TaskWallScreen.kt` (entire file)
- **What**: `WaveformVisualizer` is imported but never rendered. Voice state transitions (Listening → Processing → Preview → Error) have zero visual representation. The callbacks `onConfirmVoice`, `onCancelVoice`, `onDismissVoiceError` are accepted as parameters but never invoked. The CLAUDE.md spec calls for full-screen dim + waveform + draft card.
- **Action**: Implement voice overlay: dim during Listening, waveform visualizer, draft card during Preview with encoder confirm/cancel, error display with dismiss.

### F2. `_uiState.value` read-modify-write is not atomic (race condition)
- **Layer**: 5 — State & Data Integrity
- **Location**: `TaskWallViewModel.kt` — throughout (~30 occurrences)
- **What**: `_uiState.value = _uiState.value.copy(...)` is non-atomic. Multiple concurrent coroutines (sync, completion, calendar load) can overwrite each other's changes silently.
- **Action**: Replace ALL occurrences with `_uiState.update { it.copy(...) }` which uses CAS for atomicity. This is the single highest-impact fix.

### F3. Completed tasks not in encoder focus order
- **Layer**: 3 — UI/UX + Layer 4 — Encoder
- **Location**: `TaskWallScreen.kt`, `buildFocusOrder()` (lines 1319-1335)
- **What**: `COMPLETED_TASK` and `COMPLETED_HEADER` enum values exist but are never added to the focus order. Completed tasks render but the encoder can't reach them — no undo, no delete, no restore.
- **Action**: Add `COMPLETED_HEADER` and `COMPLETED_TASK` nodes to `buildFocusOrder` for expanded folders.

### F4. PromotionSheet has no encoder key handling
- **Layer**: 4 — Encoder Reachability
- **Location**: `PromotionSheet.kt`, `MainActivity.kt` lines 575-606
- **What**: The task-to-calendar promotion overlay has visual focus state and touch handlers but zero `onKeyEvent` handling. Keys fall through to the underlying screen. Scheduling a task is impossible from the wall encoder.
- **Action**: Add `onKeyEvent` to PromotionSheet: CW/CCW cycles rows (or adjusts values), click toggles/confirms, dismiss gesture for cancel.

### F5. Gemini model is a preview that will be retired
- **Layer**: 2 — Broken Integrations
- **Location**: `GeminiCaptureRepository.kt` lines 16-17
- **What**: `gemini-3.1-flash-lite-preview` is a preview-tier model. Google removes these with weeks' notice. When retired, ALL voice parsing and image capture fails with HTTP 404.
- **Action**: Switch to a stable GA model (e.g., `gemini-2.0-flash`). Consider making the model name configurable via Firebase Remote Config.

### F6. Optimistic update race: sync overwrites in-flight completions
- **Layer**: 5 — State & Data Integrity
- **Location**: `TaskWallViewModel.kt` — `completeTask()` vs `performRefresh()`
- **What**: Auto-sync can fetch server state before the completion API call finishes, reverting the optimistic update. The user sees the task "un-complete itself."
- **Action**: Track in-flight optimistic task IDs and exclude from sync overwrite, or skip sync when operations are pending.

### F7. Calendar drill-down has no back-navigation via encoder
- **Layer**: 4 — Encoder Reachability
- **Location**: `CalendarScreen.kt` lines 188-257
- **What**: Enter drills MONTH→WEEK→DAY, but no gesture goes back. Once in DAY view, the user is stuck until switching to Tasks and back (which resets to MONTH).
- **Action**: Add a drill-up gesture (medium-hold, or navigate past top of list, or add mode pill to focus order).

### F8. `parseDateTime` returns `now()` on null — corrupts sort order
- **Layer**: 2 — Broken Integrations
- **Location**: `GoogleTasksRepository.kt` lines 316-325
- **What**: When a task has no `updated` timestamp, `parseDateTime` returns `LocalDateTime.now()`. This makes timestamp-less tasks sort as "most recent," corrupting list ordering.
- **Action**: Return `null` or a far-past sentinel date instead of `now()`.

### F9. Firebase `auth!!` force-unwrap can crash
- **Layer**: 2 — Broken Integrations
- **Location**: `FirebaseKeySync.kt` lines 49-50
- **What**: `auth` is a computed property calling `FirebaseAuth.getInstance()`. Between the `isAvailable` null check and `auth!!`, the instance can become null, causing NPE.
- **Action**: Capture into a local `val` at function start: `val firebaseAuth = auth ?: return`.

### F10. Undo races with sync and re-complete
- **Layer**: 5 — State & Data Integrity
- **Location**: `TaskWallViewModel.kt` — `completeTask()`, `undoCompletion()`
- **What**: If sync runs between completion and undo, the undo uses stale task data. If the user re-completes during undo, concurrent coroutines conflict.
- **Action**: Add per-task mutex/debounce. Store latest server-confirmed task state for undo.

### F11. `isReauthenticating` flag is not thread-safe
- **Layer**: 5 — State & Data Integrity
- **Location**: `TaskWallViewModel.kt` line 169
- **What**: Plain `Boolean` accessed from multiple coroutines. Two concurrent auth failures can both proceed to re-auth, triggering duplicate `onSignedIn()` calls.
- **Action**: Replace with `AtomicBoolean` or `Mutex`-guarded block.

### F12. `onSignedIn()` can be called concurrently from multiple sources
- **Layer**: 5 — State & Data Integrity
- **Location**: `TaskWallViewModel.kt` line 270
- **What**: Called from `checkAuthState`, `attemptReauthAndRetry`, and `MainActivity` sign-in launchers. Concurrent calls reinitialize repositories and load data twice, corrupting state.
- **Action**: Guard with mutex or check-and-set flag.

### F13. THREE_DAY calendar column switching impossible via encoder
- **Layer**: 4 — Encoder Reachability
- **Location**: `CalendarScreen.kt` lines 224-257
- **What**: `threeDaySelectedColumn` defaults to 1 (today) and is never modified by key events. Encoder user is stuck in center column.
- **Action**: Wire CW/CCW to horizontal column navigation or wrap at column boundaries.

### F14. Undo toast not operable via encoder
- **Layer**: 3 — UI/UX Gaps
- **Location**: `UndoToast.kt`, `TaskWallScreen.kt` lines 1022-1030
- **What**: "Undo" and "Dismiss" are touch-only (`Modifier.clickable`). No encoder focus or key handling. Wall users cannot undo task completions.
- **Action**: Intercept encoder click when undo toast is visible to trigger undo.

### F15. Voice parsing duplicated between both ViewModels
- **Layer**: 6 — Code Quality
- **Location**: `TaskWallViewModel.kt` lines 659-770, `PhoneCaptureViewModel.kt` lines 218-344
- **What**: ~120 lines of identical voice parsing logic duplicated: `configureVoiceInputParsing()`, `handleRawVoiceTranscription()`, `clearVoiceParsingMetadata()`, `resolveKnownTaskListId()`, and `confirmVoiceTask()`.
- **Action**: Extract shared `VoiceParsingCoordinator` class.

### F16. SSL/TLS transport setup duplicated between repositories
- **Layer**: 6 — Code Quality
- **Location**: `GoogleTasksRepository.kt` lines 44-63, `GoogleCalendarRepository.kt` lines 52-71
- **What**: Identical 10-line SSL setup block copy-pasted.
- **Action**: Extract shared `GoogleApiTransportFactory`.

### F17. Test coverage critically low
- **Layer**: 7 — Build & Test Health
- **Location**: All source files
- **What**: 62 production files, only 28 real unit tests. Zero coverage for: `TaskWallViewModel`, both repositories, `GoogleAuthManager`, `VoiceCaptureManager`, all UI components, `WeatherRepository`, `FirebaseKeySync`, `PhoneCaptureViewModel`.
- **Action**: Prioritize ViewModel unit tests (mock repositories), then repository tests.

### F18. `confirmVoiceTask` never passes `parentTaskId` — subtask placement lost
- **Layer**: 2 — Broken Integrations
- **Location**: `TaskWallViewModel.kt` lines 675-703
- **What**: Gemini parses `parentTaskId` from voice input, but `confirmVoiceTask` never passes it to `createTask()`. Voice-created subtasks always become top-level tasks.
- **Action**: Pass `parentTaskId` through to `createTask`.

---

## WIRE — Code Exists But Not Connected

### W1. Phone mode: no empty state for task lists
- **Layer**: 3 — Location: `PhoneHomeScreen.kt`
- **Action**: Add empty state view when `taskLists.isEmpty() && !isLoading`.

### W2. Phone mode: `isSyncing` state has no visual feedback
- **Layer**: 3 — Location: `PhoneHomeScreen.kt` line 105
- **Action**: Add `uiState.isSyncing` to spinner condition.

### W3. Phone mode: no task deletion or scheduling
- **Layer**: 3 — Location: `PhoneHomeScreen.kt`
- **Action**: Add long-press or swipe gesture for delete/schedule on `PhoneTaskItem`.

### W4. Phone mode: no undo for task completion
- **Layer**: 3 — Location: `PhoneCaptureViewModel.kt`
- **Action**: Add undo support matching wall mode pattern.

### W5. Phone mode: no calendar access
- **Layer**: 3 — Location: `MainActivity.kt` `PhoneModeContent`
- **Action**: Evaluate whether phone calendar is desired. Add lightweight day view if so.

### W6. Voice preview/error states have no encoder UI
- **Layer**: 4 — Location: `TaskWallScreen.kt` lines 751-793
- **Action**: Blocked on F1 (voice overlay). Wire encoder handling when overlay is built.

### W7. Calendar selector cycling is touch-only
- **Layer**: 4 — Location: `CalendarScreen.kt` `DateAndCalendarBar`
- **Action**: Add encoder gesture for cycling writable calendars.

### W8. Weather context strip expand/collapse is touch-only
- **Layer**: 4 — Location: `WeatherContextStrip.kt`
- **Action**: Low priority — informational, not actionable.

### W9. Multi-slot drag selection in DAY view is touch-only
- **Layer**: 4 — Location: `CalendarDayView.kt` lines 223-251
- **Action**: Low priority — single-slot scheduling works via encoder.

### W10. ModeSelectorScreen not operable via encoder
- **Layer**: 3 — Location: `ModeSelectorScreen.kt`
- **Action**: Add focus navigation between mode cards and sign-out button.

---

## REMOVE — Dead Code to Delete

### R1. `DisplayState` data class (never used)
- `Task.kt` lines 83-88

### R2. `GoogleAuthManager.revokeAccess()` (never called)
- `GoogleAuthManager.kt` lines 149-151

### R3. `GoogleAuthManager.hasTasksScope()` (never called)
- `GoogleAuthManager.kt` lines 92-94

### R4. `GoogleAuthManager.shouldRequestCalendarReconsent()` (never called)
- `GoogleAuthManager.kt` lines 106-108

### R5. `ModePreferenceRepository.getModePreference()` (never called)
- `ModePreferenceRepository.kt` lines 21-23

### R6. `GeminiCaptureRepository.extractErrorMessage()` (dead private method)
- `GeminiCaptureRepository.kt` lines 176-183

### R7. Unused color constants: `StateUrgent`, `AccentSuccess`, `AccentError`, `Primary`
- `Color.kt`

### R8. Unused WallColors properties: `stateCompleted`, `stateSubtle`, `connectivityOffline`, `accentDeep`
- `WallColors.kt`

### R9. `Key.Escape` handler in CalendarScreen event action menu
- `CalendarScreen.kt` line 325

---

## POLISH — Works But Should Improve

### P1. `performRefresh` mutex skip returns fake success
- `TaskWallViewModel.kt` line 470 — returns `true` when skipping, misleading callers.

### P2. VoiceCaptureManager has no timeout — can get stuck in Listening
- `VoiceCaptureManager.kt` — add 30-second timeout.

### P3. No offline queue for task operations
- `TaskWallViewModel.kt` — optimistic updates lost when offline, no retry queue.

### P4. Calendar view mode resets on every pager navigation
- `MainActivity.kt` lines 548-553 — drill-down state lost when switching pages.

### P5. Task mutations don't trigger re-auth on 401
- `TaskWallViewModel.kt` — `completeTask`/`uncompleteTask` show error but don't re-auth.

### P6. `deleteTask` reverts by full refresh instead of local revert
- `TaskWallViewModel.kt` lines 633-646 — causes visible loading flash.

### P7. Auth error detection via string matching is fragile
- `GoogleTasksRepository.kt` lines 302-310 — use type check instead of `contains("401")`.

### P8. Deprecated Google Sign-In API
- `GoogleAuthManager.kt` — plan migration to Credential Manager.

### P9. Firebase stores API keys in plaintext in RTDB
- `FirebaseKeySync.kt` — encrypt before pushing, ensure security rules.

### P10. No google-services.json — Firebase is non-functional
- Firebase key sync silently no-ops. Document or add the config file.

### P11. No retry logic for transient Gemini errors
- `GeminiCaptureRepository.kt` — single attempt, no retry on 429/5xx.

### P12. Gemini API key in URL query parameter (logged in errors)
- `GeminiCaptureRepository.kt` — sanitize error messages.

### P13. Application name "Ledger" hardcoded in repositories
- `GoogleTasksRepository.kt:61`, `GoogleCalendarRepository.kt:69` — use constant.

### P14. `TaskListWithTasks` duplicated as two identical data classes
- `TaskWallViewModel.kt` line 59, `PhoneCaptureViewModel.kt` line 39 — consolidate.

### P15. Sleep schedule logic duplicated in three places
- `TaskWallScreen.kt`, `MainActivity.kt` — extract `isInHourRange()` utility.

### P16. "Service not initialized" guard duplicated 12 times
- Both repositories — extract `withService` helper.

### P17. Task-to-model mapping duplicated 3 times
- `GoogleTasksRepository.kt` — extract `toAppTask()` extension.

### P18. Urgency color mapping duplicated between TaskItem and PhoneTaskItem
- Extract `WallColors.urgencyColor(urgency)` extension.

### P19. Hardcoded color literals outside theme system
- `Color(0x1AFFFFFF)` in 6+ places — add `rimGloss` token to WallColors.

### P20. All user-visible strings hardcoded in Kotlin
- Only 1 entry in `strings.xml`. 50+ strings inline in composables.

### P21. TaskWallScreen.kt is 1595 lines
- Extract FocusNavigation, FolderSection, status indicators into separate files.

### P22. TaskWallViewModel.kt is 1449 lines
- Extract CalendarManager, PromotionDraftManager, VoiceInputHandler, SyncScheduler.

### P23. Magic numbers scattered (timeouts, intervals, thresholds)
- Define named constants in companion objects.

### P24. `consecutiveSyncFailures` and other plain vars not thread-safe
- Use `AtomicInteger` or move inside mutex.

### P25. 17 outdated dependencies
- Compose BOM 2024.09.00, lifecycle 2.7.0, play-services-auth 21.0.0, etc.

### P26. 10 dependencies not in TOML catalog
- Hardcoded version strings in `build.gradle.kts`.

### P27. Application ID uses `com.example.todowallapp`
- Google Play rejects `com.example.*` prefix.

### P28. No release signing configuration
- No keystore, no `signingConfigs` block.

### P29. Accessibility: `contentDescription = null` on interactive icons
- Multiple files — add descriptions for screen readers.

### P30. ModifierParameter violations (17 lint warnings)
- Composable functions don't follow Modifier parameter guidelines.

---

## NOTE — Observations (No Action Needed Now)

- N1. `Key.Spacebar` handlers are harmless redundancy (11 locations)
- N2. `MockData` only used in `@Preview` composables — acceptable
- N3. `PageIndicator.showPageLabel` parameter never used from call sites
- N4. `PhoneCaptureViewModel` doesn't do optimistic updates (intentional)
- N5. Two ViewModels share repos but not state — phone completions don't update wall until next sync
- N6. `PendingCaptureStore` uses `apply()` not `commit()` — small orphan risk
- N7. Weather cache 3-hour TTL may be too long for wall display
- N8. `QuietModeContent` duplicates clock update logic from `ClockHeader`
- N9. Sleep mode PageIndicator uses stale `remember { LocalTime.now().hour }`
- N10. HOME category intent filter absent from manifest (CLAUDE.md says it should be there)
- N11. 9 unused resources detected by lint
- N12. 27 typo warnings from lint
- N13. Heap dumps in root (already addressed — in .gitignore)
- N14. `nul` file in root (Windows artifact — delete)
- N15. Duplicate `tween` import in CalendarScreen.kt

---

## Recommended Fix Order

### Phase 1: Critical Stability (do first)
1. **F2** — Atomic state updates (`_uiState.update {}`) — highest impact, touches everything
2. **F5** — Switch Gemini model to stable GA version
3. **F8** — Fix `parseDateTime` null handling
4. **F9** — Fix Firebase force-unwrap crash
5. **F11 + F12** — Thread-safe auth flags
6. **R1-R9** — Delete all dead code (quick wins)

### Phase 2: Encoder Completeness (core wall experience)
7. **F3** — Add completed tasks to focus order
8. **F4** — Wire encoder to PromotionSheet
9. **F7** — Calendar back-navigation
10. **F13** — THREE_DAY column switching
11. **F14** — Undo toast encoder support
12. **W10** — ModeSelectorScreen encoder support

### Phase 3: Voice Overlay (major feature gap)
13. **F1** — Build voice overlay (waveform + dim + draft card + error)
14. **W6** — Wire encoder to voice overlay states
15. **F18** — Pass parentTaskId from voice parsing

### Phase 4: Code Quality & Deduplication
16. **F15** — Extract shared VoiceParsingCoordinator
17. **F16** — Extract GoogleApiTransportFactory
18. **P14-P18** — Consolidate duplicated patterns
19. **P21-P22** — Split large files

### Phase 5: Phone Mode Gaps
20. **W1-W5** — Phone empty states, sync feedback, delete, undo, calendar

### Phase 6: Polish & Release Prep
21. **P25-P26** — Update dependencies
22. **P27-P28** — Fix application ID, add release signing
23. **P19-P20** — Theme tokens, string resources
24. **F17** — Add meaningful test coverage
