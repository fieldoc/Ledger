# Ship-Ready Fixes Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address all FIX, WIRE, REMOVE, and POLISH items from the ship-ready audit (docs/ship-ready-audit-2026-03-15.md)

**Architecture:** Parallel agents with worktree isolation, grouped by file ownership to minimize merge conflicts. Each agent works on its own git branch, merged sequentially afterward.

**Tech Stack:** Kotlin, Jetpack Compose, Google API Client, Coroutines, MutableStateFlow

**Deferred items (need user input or are separate projects):**
- F15: Voice parsing dedup (refactoring pass after fixes land)
- F17: Test coverage (separate project)
- W5: Phone calendar (needs design decisions)
- W8-W9: Low priority touch-only features
- P3: Offline queue, P8: Credential Manager, P9: Firebase encryption, P10: google-services.json
- P15: Sleep schedule util, P20: String resources, P21-P22: File splitting
- P27: Application ID, P28: Release signing, P30: ModifierParameter lint

---

## Task 1: ViewModel Stability

**Branch:** `fix/viewmodel-stability`
**Files:** `viewmodel/TaskWallViewModel.kt`
**Audit items:** F2, F6, F10, F11, F12, F18, P1, P5, P6, P23, P24

### F2: Atomic state updates
- [ ] Replace ALL `_uiState.value = _uiState.value.copy(...)` with `_uiState.update { it.copy(...) }` throughout the file (~49 occurrences)
- [ ] Pattern: `_uiState.value = _uiState.value.copy(foo = bar)` → `_uiState.update { it.copy(foo = bar) }`
- [ ] For multi-line copies where a local `val state = _uiState.value` is read first, convert to: `_uiState.update { state -> state.copy(...) }`

### F11 + F12: Thread-safe auth
- [ ] Change `private var isReauthenticating = false` (line 175) to `private val isReauthenticating = AtomicBoolean(false)`
- [ ] In `attemptReauthAndRetry`: use `if (!isReauthenticating.compareAndSet(false, true)) return` instead of the plain check
- [ ] In finally block: `isReauthenticating.set(false)`
- [ ] Guard `onSignedIn()`: add `private val signInMutex = Mutex()` and wrap the body with `signInMutex.withLock { ... }` to prevent concurrent calls

### F6 + F10: Optimistic update race protection
- [ ] Add `private val inFlightTaskIds = mutableSetOf<String>()` to track tasks being modified
- [ ] In `completeTask()`: add task.id to `inFlightTaskIds` before optimistic update, remove in onSuccess/onFailure
- [ ] In `uncompleteTask()`: same pattern
- [ ] In `updateTaskAcrossLists()` called from `performRefresh`→`loadTaskLists`: skip updating tasks whose IDs are in `inFlightTaskIds`
- [ ] For undo: store the server-confirmed task state (from onSuccess) in UndoState rather than the pre-operation snapshot

### F18: Pass parentTaskId in confirmVoiceTask
- [ ] In `confirmVoiceTask()` (line ~775): after resolving `dueDate`, also resolve parentTaskId from voice state
- [ ] Pass it to `tasksRepository.createTask(taskListId, title, dueDate, parentId = resolvedParentId)`

### P1: performRefresh mutex skip
- [ ] Change `if (refreshMutex.isLocked) return true` to `if (refreshMutex.isLocked) return false` (line ~398)

### P5: Task mutations re-auth on 401
- [ ] In `completeTask` onFailure: add `if (GoogleTasksRepository.isAuthError(error as? Exception ?: return@onFailure)) { attemptReauthAndRetry { performRefresh(false) } }`
- [ ] Same for `uncompleteTask` and `deleteTask` onFailure blocks

### P6: deleteTask local revert
- [ ] Before `removeTaskAcrossLists(task.id)`, save the current task list state
- [ ] In onFailure: re-insert the task instead of calling `refresh()`

### P23: Magic numbers → named constants
- [ ] Add companion object with: `SILENT_SIGN_IN_TIMEOUT_MS = 3_000L`, `UNDO_TIMEOUT_MS = 5_000L`, `SYNC_FEEDBACK_CLEAR_MS = 3_000L`, `PROMOTION_ANCHOR_EXPIRY_MS = 2 * 60 * 60 * 1000L`
- [ ] Replace all raw numeric literals with these constants

### P24: Thread-safe primitives
- [ ] Change `consecutiveSyncFailures` from `var` to `AtomicInteger(0)` and use `.get()`, `.set()`, `.incrementAndGet()`

---

## Task 2: Repository & Backend Fixes

**Branch:** `fix/repository-backend`
**Files:** `data/repository/GoogleTasksRepository.kt`, `data/repository/GoogleCalendarRepository.kt`, `capture/repository/GeminiCaptureRepository.kt`, `security/FirebaseKeySync.kt`, `voice/VoiceCaptureManager.kt`, NEW `data/repository/GoogleApiTransportFactory.kt`

### F16: Extract shared SSL transport
- [ ] Create `data/repository/GoogleApiTransportFactory.kt`:
```kotlin
object GoogleApiTransportFactory {
    const val APP_NAME = "Ledger"
    fun createTransport(): NetHttpTransport {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, tmf.trustManagers, null)
        return NetHttpTransport.Builder().setSslSocketFactory(sslContext.socketFactory).build()
    }
}
```
- [ ] Replace SSL blocks in both `GoogleTasksRepository.initialize()` and `GoogleCalendarRepository.initialize()` with `GoogleApiTransportFactory.createTransport()`

### P13: Application name constant
- [ ] Replace `.setApplicationName("Ledger")` in both repos with `.setApplicationName(GoogleApiTransportFactory.APP_NAME)`

### P16: withService helper
- [ ] In `GoogleTasksRepository`, add:
```kotlin
private inline fun <T> withTasksService(block: (Tasks) -> T): Result<T> {
    val service = tasksService ?: return Result.failure(IllegalStateException("Tasks service not initialized"))
    return runCatching { block(service) }
}
```
- [ ] Replace all 7 `val service = tasksService ?: return@withContext Result.failure(...)` guards
- [ ] Same pattern for `GoogleCalendarRepository` with `withCalendarService`

### P17: Task mapping extension
- [ ] In `GoogleTasksRepository`, add private extension:
```kotlin
private fun com.google.api.services.tasks.model.Task.toAppTask(): Task {
    return Task(
        id = this.id ?: "", title = this.title ?: "",
        notes = this.notes ?: "", isCompleted = this.status == "completed",
        dueDate = parseDueDate(this.due), position = this.position ?: "",
        parentId = this.parent ?: "", updatedAt = parseDateTime(this.updated),
        completedAt = if (this.status == "completed") parseDateTime(this.completed) else null
    )
}
```
- [ ] Replace all 4+ inline Task construction blocks with `.toAppTask()`

### F8: parseDateTime null handling
- [ ] Change `parseDateTime` return type to `LocalDateTime?`
- [ ] Change `if (rfc3339 == null) return LocalDateTime.now()` to `if (rfc3339 == null) return null`
- [ ] Change catch block to return `null` instead of `LocalDateTime.now()`
- [ ] Update call sites: `updatedAt = parseDateTime(...)` → `updatedAt = parseDateTime(...) ?: LocalDateTime.MIN`

### P7: Auth error type check
- [ ] In `isAuthError()`: remove `e.message?.contains("401") == true` line
- [ ] Change `e.javaClass.simpleName == "UserRecoverableAuthIOException"` to proper type check: `e is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException`

### F5: Gemini model swap
- [ ] Change `GEMINI_BASE_URL` from `gemini-3.1-flash-lite-preview` to `gemini-2.0-flash`
- [ ] URL becomes: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent`

### R6: Remove dead extractErrorMessage
- [ ] Delete the `extractErrorMessage()` private method (lines ~243-252)

### P11: Gemini retry logic
- [ ] In `HttpGeminiApiClient.generateContent()`: wrap the HTTP call in a retry loop (max 2 retries) for response codes 429, 500, 503
- [ ] Add exponential backoff: delay 1000ms, then 2000ms

### P12: Gemini error sanitization
- [ ] In the error message construction: strip the API key from any URL in the error body
- [ ] `errorMessage.replace(Regex("key=[^&\\s]+"), "key=***")`

### F9: Firebase force-unwrap
- [ ] In `FirebaseKeySync` methods that use `auth!!`: capture to local val first
- [ ] `val firebaseAuth = auth ?: return` at the start of each method that uses it
- [ ] Replace `auth!!` with `firebaseAuth`

### P2: VoiceCaptureManager timeout
- [ ] In `startListening()`: after starting the recognizer, post a delayed timeout:
```kotlin
mainHandler.postDelayed({
    if (_state.value is VoiceInputState.Listening) {
        speechRecognizer?.cancel()
        _state.value = VoiceInputState.Error("Voice input timed out")
    }
}, 30_000)
```
- [ ] Cancel the timeout in `onResults`, `onError`, and `cancel()`

---

## Task 3: Dead Code & Theme Cleanup

**Branch:** `fix/dead-code-theme`
**Files:** `data/model/Task.kt`, `auth/GoogleAuthManager.kt`, `data/repository/ModePreferenceRepository.kt`, `ui/theme/Color.kt`, `ui/theme/WallColors.kt`, various UI files

### R1: Remove DisplayState
- [ ] Delete `DisplayState` data class from `Task.kt` (lines 83-88)

### R2-R4: Remove unused GoogleAuthManager methods
- [ ] Delete `revokeAccess()` (lines 149-151)
- [ ] Delete `hasTasksScope()` (lines 92-94)
- [ ] Delete `shouldRequestCalendarReconsent()` (lines 106-108)

### R5: Remove unused getModePreference
- [ ] Delete `getModePreference()` from `ModePreferenceRepository.kt` (lines 21-23)

### R7: Remove unused Color constants
- [ ] Delete `StateUrgent`, `AccentSuccess`, `AccentError`, `Primary` from `Color.kt`

### R8: Remove unused WallColors properties
- [ ] Remove `stateCompleted`, `stateSubtle`, `connectivityOffline`, `accentDeep` from `WallColors` data class
- [ ] Remove their initializations in `darkWallColors()` and `lightWallColors()`
- [ ] Remove source constants: `StateCompleted`, `LightStateCompleted`, `StateSubtle`, `LightStateSubtle`, `ConnectivityOffline`, `LightConnectivityOffline`

### P18: Urgency color extension
- [ ] Add to `WallColors.kt`:
```kotlin
fun WallColors.urgencyColor(urgency: TaskUrgency): Color = when (urgency) {
    TaskUrgency.OVERDUE -> urgencyOverdue
    TaskUrgency.DUE_TODAY -> urgencyDueToday
    TaskUrgency.DUE_SOON -> urgencyDueSoon
    TaskUrgency.NORMAL -> textSecondary
    TaskUrgency.COMPLETED -> textSecondary.copy(alpha = 0.5f)
}
```
- [ ] Replace urgency color `when` blocks in `TaskItem.kt` and `PhoneTaskItem.kt` with `colors.urgencyColor(urgency)`

### P19: Hardcoded color literals → theme tokens
- [ ] Add to `WallColors` data class: `rimGloss: Color`, `rimGlossStrong: Color`
- [ ] Dark values: `rimGloss = Color(0x1AFFFFFF)`, `rimGlossStrong = Color(0x26FFFFFF)`
- [ ] Light values: `rimGloss = Color(0x1A000000)`, `rimGlossStrong = Color(0x26000000)`
- [ ] Replace all `Color(0x1AFFFFFF)` occurrences with `colors.rimGloss`
- [ ] Replace `Color(0x26FFFFFF)` with `colors.rimGlossStrong`

### P29: Accessibility contentDescription
- [ ] Add content descriptions to interactive icons:
  - Chevrons in accordion headers: "Expand" / "Collapse"
  - Checkmark icons: "Completed"
  - Settings icon: "Settings"
  - Close icons: "Close"
  - Navigation arrows: "Previous" / "Next"

---

## Task 4: Wall Encoder Fixes

**Branch:** `fix/wall-encoder`
**Files:** `ui/screens/TaskWallScreen.kt`, `ui/screens/CalendarScreen.kt`, `ui/components/PromotionSheet.kt`, `ui/screens/ModeSelectorScreen.kt`, `MainActivity.kt`

### F3: Completed tasks in focus order
- [ ] In `buildFocusOrder()`: after iterating `model.pendingGroups` for the expanded folder, add:
```kotlin
if (model.shownCompletedTasks.isNotEmpty()) {
    focusOrder += FocusNode(
        key = "completed_header_$folderId",
        folderId = folderId,
        type = FocusNodeType.COMPLETED_HEADER
    )
    model.shownCompletedTasks.forEach { task ->
        focusOrder += FocusNode(
            key = taskFocusKey(folderId, task.id),
            folderId = folderId,
            type = FocusNodeType.COMPLETED_TASK,
            task = task
        )
    }
}
```
- [ ] In `selectCurrent()`: handle `COMPLETED_TASK` type — trigger uncomplete (restore) on click
- [ ] In the context menu logic: allow context menu on completed tasks (restore + delete options)

### F14: Undo toast encoder support
- [ ] In the main `onKeyEvent` handler, add a check BEFORE all other handling:
```kotlin
if (undoVisible && keyEvent.type == KeyEventType.KeyUp) {
    when (keyEvent.key) {
        Key.Enter, Key.NumPadEnter -> { onUndo(); return@onKeyEvent true }
    }
}
```
- [ ] This intercepts the encoder click to trigger undo when the toast is visible

### F4: PromotionSheet encoder handling
- [ ] Add `onKeyEvent` modifier to PromotionSheet's root Box or the sheet card
- [ ] When `visible && !isAdjusting`: CW/CCW cycles `focusedRow` (0-3), click calls `onToggleAdjusting` (or `onConfirm` when row==3)
- [ ] When `visible && isAdjusting`: CW/CCW adjusts the value (duration ±15min, time ±15min, calendar cycles), click calls `onToggleAdjusting` to exit
- [ ] Key handling must be added in `WallModeContent` in `MainActivity.kt` to intercept keys when `promotionDraft != null`, OR inside PromotionSheet with focusRequester

### F7: Calendar back-navigation
- [ ] In CalendarScreen key handlers: when in WEEK mode, add a "back" gesture — medium-hold (350-800ms) goes back to MONTH
- [ ] When in DAY mode: medium-hold goes back to WEEK
- [ ] Implementation: track KeyDown time for Enter key, on KeyUp check hold duration. If 350-800ms and in WEEK/DAY, call `onViewModeChange` to go up one level instead of drilling down.

### F13: THREE_DAY column switching
- [ ] When at top of a column (slotIndex == 0) and navigating UP: move to previous column's last slot
- [ ] When at bottom of a column and navigating DOWN: move to next column's first slot
- [ ] Update `threeDaySelectedColumn` accordingly

### R9: Remove Key.Escape from CalendarScreen
- [ ] Delete the `Key.Escape ->` branch in the event action menu handler

### W7: Calendar selector cycling
- [ ] In DAY mode when `isDateBarFocused && isEditing`: add a secondary gesture (e.g., long-press) to cycle calendars
- [ ] Or: add a third focus state `isCalendarSelectorFocused` accessible from the date bar

### W10: ModeSelectorScreen encoder support
- [ ] Add `var focusedIndex by remember { mutableIntStateOf(0) }` (0=Wall, 1=Phone, 2=Sign out)
- [ ] Add `onKeyEvent`: CW/CCW cycles focusedIndex, Enter selects
- [ ] Add visual focus indicator (border/glow) on focused card
- [ ] Request focus on composition

### P4: Calendar view mode reset fix
- [ ] In `MainActivity.kt`: change the LaunchedEffect that resets to MONTH on pager page change
- [ ] Only reset to MONTH on FIRST entry to calendar page, not on every return
- [ ] Use a `var hasEnteredCalendar by remember { mutableStateOf(false) }` guard

---

## Task 5: Phone Mode Gaps

**Branch:** `fix/phone-mode`
**Files:** `ui/screens/PhoneHomeScreen.kt`, `viewmodel/PhoneCaptureViewModel.kt`, `ui/components/PhoneTaskItem.kt`, NEW `data/model/TaskListWithTasks.kt`

### P14: Consolidate TaskListWithTasks
- [ ] Create `data/model/TaskListWithTasks.kt`:
```kotlin
data class TaskListWithTasks(val taskList: TaskList, val tasks: List<Task> = emptyList())
```
- [ ] Remove `TaskListWithTasks` from `TaskWallViewModel.kt` and `PhoneTaskListWithTasks` from `PhoneCaptureViewModel.kt`
- [ ] Update all imports to use the shared version

### W1: Phone empty state
- [ ] In `PhoneHomeScreen`, after the LazyColumn items: if `uiState.taskLists.isEmpty() && !uiState.isLoading`, show an empty state card: "No tasks yet. Tap the mic to capture something."

### W2: Phone isSyncing feedback
- [ ] Change the spinner condition from `uiState.isLoading || uiState.isParsingCapture` to include `|| uiState.isSyncing`

### W3: Phone task deletion
- [ ] Add `onLongClick` to `PhoneTaskItem` that shows a simple dropdown menu with "Delete" option
- [ ] Wire to `PhoneCaptureViewModel.deleteTask(task)` (add this method if missing)

### W4: Phone undo
- [ ] Add `UndoState` to `PhoneCaptureUiState` and `PhoneCaptureViewModel`
- [ ] After task completion: set undo state with 5-second timeout (same as wall mode)
- [ ] Show a Snackbar in `PhoneHomeScreen` when undo is available

---

## Task 6: Build Config Updates

**Branch:** `fix/build-config`
**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`

### P25: Update dependencies
- [ ] Update versions in `libs.versions.toml`:
  - `kotlin` → latest stable 2.0.x (stay on 2.0 series for Compose compatibility)
  - `composeBom` → `2025.01.01` or latest
  - `generativeai` → latest

### P26: Move hardcoded deps to TOML
- [ ] Add version entries and library aliases for all 10 hardcoded dependencies
- [ ] Replace hardcoded strings in `build.gradle.kts` with `libs.xxx` references
- [ ] Verify build succeeds after changes

---

## Merge Order

After all agents complete:
1. Task 3 (dead code — removes code, unlikely to conflict)
2. Task 6 (build config — independent files)
3. Task 2 (repositories — independent from ViewModel)
4. Task 1 (ViewModel — largest change, apply to clean base)
5. Task 5 (phone mode — touches PhoneCaptureViewModel after Task 1's patterns are set)
6. Task 4 (encoder — touches UI screens, may need minor conflict resolution with Task 3's theme changes)
