# Voice Reschedule — Design Spec
**Date:** 2026-03-28
**Status:** Approved

---

## Problem

When a user says "Move groceries to Friday," Gemini correctly classifies intent as `RESCHEDULE` — but `VoiceParsingCoordinator` intercepts it before preview and shows an error. `GoogleTasksRepository` has no `updateTaskDueDate()` method, so even unblocking the pipeline would produce a dead end.

---

## Goals

- User can say "Move [task] to [date]" and have the due date updated in Google Tasks
- High-confidence parses (>70%) execute immediately with a 2-second auto-dismissing confirmation
- Low-confidence parses show a preview card ("Move [Task] → [Date]") with Accept / Retry / Cancel
- Retry bundles the original transcript + Gemini's first parse + the user's clarification into a new prompt

---

## Non-Goals

- No UI for manually editing the rescheduled date (encoder-only device)
- No support for rescheduling multiple tasks in one utterance (single target only)
- No retry loop beyond one clarification round (one retry is sufficient)

---

## Architecture

### Data Flow

```
Voice input (hold encoder)
    ↓
VoiceCaptureManager  →  raw transcript
    ↓
VoiceParsingCoordinator.handleRawTranscription()
    ↓  (normal path, RESCHEDULE no longer intercepted)
GeminiCaptureRepository.parseVoiceInputV2()   [standard ADD/COMPLETE/etc.]
  — OR —
GeminiCaptureRepository.parseRescheduleRetry() [on retry, context-aware]
    ↓
ParsedVoiceResponse { intent=RESCHEDULE, tasks[0].parentTaskId=<target>, tasks[0].dueDate=<new> }
    ↓
    ├─ confidence > 0.70
    │       → GoogleTasksRepository.updateTaskDueDate(listId, taskId, dueDate)
    │       → TaskWallUiState.transientMessage = "Moved to [date]"   (auto-clears 2s)
    │       → voiceCaptureManager.resetToIdle()
    │
    └─ confidence ≤ 0.70
            → VoiceInputState.Preview (RESCHEDULE variant shown in UI)
                  Accept  → updateTaskDueDate() → same confirmation path
                  Cancel  → resetToIdle()
                  Retry   → VoiceParsingCoordinator stores RescheduleRetryContext
                            re-arms voiceCaptureManager for new transcript
                            new transcript → parseRescheduleRetry() → back to Preview
```

### Confidence Split Location

The confidence split happens in `TaskWallViewModel.confirmVoiceCapture()` — the same place COMPLETE and DELETE are handled. The coordinator simply passes `ParsedVoiceResponse` through to preview; the ViewModel decides whether to auto-execute or show the card.

For **high-confidence**, the ViewModel short-circuits before the Preview state is set — it calls `updateTaskDueDate()` directly from the coordinator's `onSuccess` handler path.

Actually, on reflection: the coordinator always calls `showPreview()` for RESCHEDULE (it doesn't know confidence). The ViewModel's `confirmVoiceCapture()` is only called when the user is *already* in Preview state. So the confidence split needs to happen one step earlier — in the ViewModel's voice state observation, immediately after the coordinator publishes the preview.

**Revised split point:** In `TaskWallViewModel`, observe `VoiceInputState.Preview` transitions. When a new Preview arrives with `intent == RESCHEDULE` and `confidence > 0.70`, auto-confirm it without waiting for user input (skip showing the card to the user; execute immediately and show the transient banner).

---

## Components

### 1. `GoogleTasksRepository.updateTaskDueDate()`

```kotlin
suspend fun updateTaskDueDate(
    taskListId: String,
    taskId: String,
    dueDate: LocalDate?           // null clears the due date
): Result<Task>
```

Uses the `get()` + `update()` pattern already proven in `completeTask()` and `uncompleteTask()` — fetch the current task, set only the `due` field, then call `tasks().update()`. (`tasks().patch()` exists in the library but is not yet used anywhere in the codebase; using the established pattern avoids an unverified code path.)

Due date format: RFC 3339 `"YYYY-MM-DDT00:00:00.000Z"` (matching how `createTask()` sets it). To clear a due date, set `due = ""` (empty string).

### 2. `VoiceParsingCoordinator` changes

- **Remove** the `if (validatedResponse.intent == VoiceIntent.RESCHEDULE)` interception block. RESCHEDULE now flows to `showPreview()` like ADD/COMPLETE/DELETE.
- **Add** `RescheduleRetryContext` data class (private to coordinator):
  ```kotlin
  data class RescheduleRetryContext(
      val originalTranscript: String,
      val targetTaskTitle: String,
      val targetTaskId: String,
      val firstParsedDate: LocalDate?
  )
  ```
- **Add** `fun armRescheduleRetry(context: RescheduleRetryContext)` — stores the context and sets a flag so the next raw transcription uses `parseRescheduleRetry()` instead of the standard prompt.
- The retry context is cleared after use (success or failure), on `clearMetadata()`, and in `cancelParse()` / `destroy()` — all paths that can interrupt the voice flow must reset this flag.

### 3. `GeminiCaptureRepository.parseRescheduleRetry()`

```kotlin
suspend fun parseRescheduleRetry(
    apiKey: String,
    originalTranscript: String,
    targetTaskTitle: String,
    firstParsedDate: LocalDate?,
    clarificationTranscript: String,
    existingLists: List<ExistingListRef>,   // needed by parseVoiceResponseJson() for list validation
    existingTasks: List<ExistingTaskRef>,
    todayDate: LocalDate
): Result<ParsedVoiceResponse>
```

Builds a single context-aware prompt embedding:
- Original utterance
- Gemini's first interpretation ("I understood: move [title] to [date]")
- User's clarification transcript
- Instruction to re-parse with this context and return the same JSON schema

Returns a `ParsedVoiceResponse` with `intent = RESCHEDULE`.

### 4. `TaskWallViewModel` changes

**Auto-execute on high confidence (in Preview observation):**
```kotlin
// In the coroutine that observes voiceInputState
if (state is VoiceInputState.Preview &&
    state.response.intent == VoiceIntent.RESCHEDULE &&
    (state.response.tasks.firstOrNull()?.confidence ?: 0f) > 0.70f) {
    autoConfirmReschedule(state.response)
    return@collect
}
```

**`autoConfirmReschedule()`** — private fun that calls `updateTaskDueDate()`, sets `transientMessage`, resets voice to idle.

**RESCHEDULE branch in `confirmVoiceTasks()`** (actual method name; for Accept from low-confidence preview):
```kotlin
VoiceIntent.RESCHEDULE -> {
    val task = response.tasks.firstOrNull() ?: return@launch
    val targetId = task.parentTaskId ?: return@launch
    val targetListId = findTaskListId(targetId) ?: return@launch
    tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
        onSuccess = { setTransientMessage("Moved to ${task.dueDate.formatRelative()}") },
        onFailure = { voiceCaptureManager.setError("Failed to reschedule task") }
    )
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.resetToIdle()
}
```

**Retry handling** — new `fun retryReschedule()` called from UI on Retry tap:
```kotlin
fun retryReschedule() {
    val response = voiceParsingCoordinator.lastResponse ?: return
    val task = response.tasks.firstOrNull() ?: return
    val ctx = RescheduleRetryContext(
        originalTranscript = response.rawTranscript,
        targetTaskTitle = task.title,
        targetTaskId = task.parentTaskId ?: return,
        firstParsedDate = task.dueDate
    )
    voiceParsingCoordinator.armRescheduleRetry(ctx)
    voiceCaptureManager.startListening()
}
```

**`transientMessage`** — new field on the `TaskWallUiState` data class (defined at the top of `TaskWallViewModel.kt`):
```kotlin
val transientMessage: String? = null
```
Auto-cleared after 2 seconds via a `viewModelScope.launch { delay(2000); clearTransientMessage() }`.
Note: the data class itself must be updated, not just the ViewModel logic.

### 5. UI — `TaskWallScreen` / preview card

When `VoiceInputState.Preview` has `intent == RESCHEDULE`, render a distinct card:

```
┌──────────────────────────────────────┐
│  Move task                           │
│                                      │
│  "Buy groceries"  →  Friday Mar 28   │
│                                      │
│  [Accept]  [Retry]  [Cancel]         │
└──────────────────────────────────────┘
```

- "Accept" = encoder click (focused by default)
- "Retry" / "Cancel" = encoder rotate to navigate, click to confirm
- Encoder focus cycles: Accept → Retry → Cancel → Accept

**Transient confirmation banner** — rendered in `TaskWallScreen` overlay when `uiState.transientMessage != null`:
- Small pill at top of screen, same surface as the sync status pill
- Text: "Moved to Friday Mar 28" (or whatever the date resolves to)
- Fades out after 2 seconds (no user interaction needed)

---

## Error Cases

| Scenario | Behavior |
|----------|----------|
| Target task ID not found in local state | `voiceCaptureManager.setError("Couldn't find that task")` — must be explicit at the null-check site, not a silent `return@launch` |
| `parentTaskId` silently nulled by `parseVoiceResponseJson` (task not in `existingTasks`) | `GeminiCaptureRepository.parseVoiceResponseJson()` filters `parentTaskId` to `null` if the ID isn't in `existingTasks`. For RESCHEDULE, implementors must pass the full task list to `existingTasks` and emit an error if `parentTaskId` is still null after parsing — not silently return |
| `updateTaskDueDate()` API failure | Show error in voice state, stay in Preview |
| Retry: Gemini still low confidence after clarification | Show preview again (one more chance to Accept or Cancel) |
| No due date parsed (e.g., "move it to whenever") | `dueDate = null` — clears the due date, confirmation says "Due date cleared" |

---

## Files Changed

| File | Nature of change |
|------|-----------------|
| `data/repository/GoogleTasksRepository.kt` | Add `updateTaskDueDate()` |
| `capture/VoiceParsingCoordinator.kt` | Remove interception; add retry context + re-arm logic |
| `capture/repository/GeminiCaptureRepository.kt` | Add `parseRescheduleRetry()` |
| `viewmodel/TaskWallViewModel.kt` | Auto-confirm high-confidence; implement RESCHEDULE confirm + retry; add transientMessage |
| `ui/screens/TaskWallScreen.kt` | RESCHEDULE preview card variant; transient confirmation banner |
