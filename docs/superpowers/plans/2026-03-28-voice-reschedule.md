# Voice Reschedule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the voice RESCHEDULE intent end-to-end: user says "Move groceries to Friday," Gemini parses the new date, high-confidence parses execute immediately with a 2-second confirmation banner, low-confidence shows a preview card with Accept / Retry / Cancel.

**Architecture:** Five files touched in dependency order — repository first, then coordinator, then Gemini client, then ViewModel, then UI. Each task is self-contained and compilable. The confidence split (>0.70 = auto-execute) happens in the ViewModel by observing `voiceCaptureManager.state` transitions.

**Tech Stack:** Kotlin, Jetpack Compose, Google Tasks API (Java client), Android SpeechRecognizer, Gemini REST API (via existing `GeminiCaptureRepository`).

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt` | Add `updateTaskDueDate()` — get + update pattern |
| `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt` | Add `parseRescheduleRetry()` with context-aware prompt |
| `app/src/main/java/com/example/todowallapp/capture/VoiceParsingCoordinator.kt` | Remove interception; add `RescheduleRetryContext` + `armRescheduleRetry()` |
| `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt` | `transientMessage` in UiState; auto-confirm; RESCHEDULE branch; `retryReschedule()` |
| `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt` | RESCHEDULE preview card (3-option nav); transient confirmation banner |
| `app/src/main/java/com/example/todowallapp/MainActivity.kt` | Wire `onRetryReschedule` callback |

---

## Task 1: Add `updateTaskDueDate()` to `GoogleTasksRepository`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt`

Uses the **get + update** pattern already proven in `completeTask()` and `uncompleteTask()`. Fetches the existing task (to avoid overwriting other fields), sets only the `due` field, then calls `tasks().update()`.

- [ ] **Step 1: Add the method** immediately after `uncompleteTask()` (around line 143):

```kotlin
/**
 * Update the due date of an existing task.
 * Pass null to clear the due date.
 */
suspend fun updateTaskDueDate(
    taskListId: String,
    taskId: String,
    dueDate: LocalDate?
): Result<Task> = withContext(Dispatchers.IO) {
    withTasksService { service ->
        val currentTask = service.tasks().get(taskListId, taskId).execute()
        if (dueDate != null) {
            val dueInstant = dueDate
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .toInstant()
            currentTask.due = dueInstant.toString()
        } else {
            currentTask.due = ""   // empty string clears the due date in Google Tasks API
        }
        val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()
        updatedTask.toAppTask()
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL` (or only pre-existing warnings, zero new errors).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt
git commit -m "feat: add updateTaskDueDate to GoogleTasksRepository"
```

---

## Task 2: Add `parseRescheduleRetry()` to `GeminiCaptureRepository`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`

Adds a new method that calls Gemini with a context-aware prompt embedding:
1. The original utterance
2. What Gemini first understood
3. The user's clarification

The response uses the same JSON schema as `parseVoiceInputV2()`, so it reuses `parseVoiceResponseJson()`.

- [ ] **Step 1: Add the public method** after `parseVoiceInputV2()`:

```kotlin
/**
 * Re-parse a RESCHEDULE utterance after the user indicated the first parse was wrong.
 * Bundles original transcript + first interpretation + clarification into one prompt.
 */
suspend fun parseRescheduleRetry(
    apiKey: String,
    originalTranscript: String,
    targetTaskTitle: String,
    firstParsedDate: LocalDate?,
    clarificationTranscript: String,
    existingLists: List<ExistingListRef>,
    existingTasks: List<ExistingTaskRef>,
    todayDate: LocalDate = LocalDate.now()
): Result<ParsedVoiceResponse> = withContext(Dispatchers.IO) {
    runCatching {
        val prompt = buildRescheduleRetryPrompt(
            originalTranscript = originalTranscript,
            targetTaskTitle = targetTaskTitle,
            firstParsedDate = firstParsedDate,
            clarificationTranscript = clarificationTranscript,
            existingLists = existingLists,
            todayDate = todayDate
        )
        val requestBody = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", prompt) })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.1)
                addProperty("responseMimeType", "application/json")
            })
        }
        val response = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody)
        val responseText = extractTextFromGeminiResponse(response)
        parseVoiceResponseJson(responseText, clarificationTranscript, existingLists, existingTasks)
    }
}
```

- [ ] **Step 2: Add the private prompt builder** after `buildVoicePromptV2()`:

```kotlin
private fun buildRescheduleRetryPrompt(
    originalTranscript: String,
    targetTaskTitle: String,
    firstParsedDate: LocalDate?,
    clarificationTranscript: String,
    existingLists: List<ExistingListRef>,
    todayDate: LocalDate
): String {
    val listContext = if (existingLists.isEmpty()) "No existing lists." else
        existingLists.joinToString("\n") { "- ${it.title} [id=${it.id}]" }
    val firstDateStr = firstParsedDate?.toString() ?: "no date"

    return """
        You are a voice-to-task assistant. The user spoke a rescheduling request, but your first interpretation was wrong.

        CURRENT CONTEXT:
        Today: $todayDate (${todayDate.dayOfWeek})

        Available lists:
        $listContext

        WHAT HAPPENED:
        Original request: "$originalTranscript"
        You interpreted this as: move task "$targetTaskTitle" to $firstDateStr
        The user said that was incorrect.

        USER'S CLARIFICATION:
        "$clarificationTranscript"

        TASK:
        Re-interpret the user's original request, using the clarification to correct your understanding.
        - intent must be "reschedule"
        - tasks[0].title should be the target task name: "$targetTaskTitle"
        - tasks[0].parentTaskId should remain the same as before (you cannot re-look this up — leave parentTaskId as null if unknown)
        - tasks[0].dueDate should reflect the corrected date from the clarification
        - Apply the same temporal reasoning rules (today=$todayDate)

        Return ONLY strict JSON in this schema:
        {
          "intent": "reschedule",
          "tasks": [
            {
              "title": "string",
              "dueDate": "YYYY-MM-DD|null",
              "preferredTime": "morning|afternoon|evening|null",
              "targetListId": "string|null",
              "newListName": "null",
              "parentTaskId": "null",
              "confidence": 0.0,
              "duplicateOf": "null"
            }
          ],
          "clarification": "string|null"
        }

        No markdown. No commentary. Just the JSON.
    """.trimIndent()
}
```

> **Note:** `parentTaskId` will be `null` in the retry response (the prompt can't re-look it up). The ViewModel will supply the known target ID from `RescheduleRetryContext` rather than reading it from the parsed response.

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: add parseRescheduleRetry to GeminiCaptureRepository"
```

---

## Task 3: Update `VoiceParsingCoordinator`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/VoiceParsingCoordinator.kt`

Three changes:
1. Remove the RESCHEDULE interception block so it flows to `showPreview()`.
2. Add `RescheduleRetryContext` data class + storage.
3. Add `armRescheduleRetry()` so the ViewModel can prime the next transcription to use the retry path.

- [ ] **Step 1: Add `RescheduleRetryContext` data class and the retry flag** — add these after the imports, before the class declaration:

```kotlin
/**
 * Holds context from a failed RESCHEDULE parse so the next transcription can
 * be sent to Gemini with conversational history.
 */
data class RescheduleRetryContext(
    val originalTranscript: String,
    val targetTaskTitle: String,
    val targetTaskId: String,
    val firstParsedDate: LocalDate?
)
```

- [ ] **Step 2: Add instance field and public methods** to the `VoiceParsingCoordinator` class body, after `private var parseJob: Job? = null`:

```kotlin
// Non-null when the user tapped Retry on a low-confidence RESCHEDULE preview
private var rescheduleRetryContext: RescheduleRetryContext? = null

/** Called by ViewModel when user taps Retry on the reschedule preview card. */
fun armRescheduleRetry(context: RescheduleRetryContext) {
    rescheduleRetryContext = context
}
```

- [ ] **Step 3: Modify `clearMetadata()`** to also clear the retry context:

Replace:
```kotlin
fun clearMetadata() {
    lastResponse = null
}
```
With:
```kotlin
fun clearMetadata() {
    lastResponse = null
    rescheduleRetryContext = null
}
```

- [ ] **Step 4: Modify `cancelParse()`** to also clear retry context (so a mid-retry cancel doesn't leave stale state).

Find the function body (do not include the KDoc comment in the match — just replace the body):
```kotlin
    fun cancelParse() {
        parseJob?.cancel()
    }
```
Replace with:
```kotlin
    fun cancelParse() {
        parseJob?.cancel()
        rescheduleRetryContext = null
    }
```

- [ ] **Step 5: Remove the RESCHEDULE interception block** in `handleRawTranscription()`.

Find and replace this block (inside the `onSuccess` lambda):
```kotlin
                    lastResponse = validatedResponse
                    if (validatedResponse.intent == VoiceIntent.RESCHEDULE) {
                        clearMetadata()
                        voiceCaptureManager.setError(
                            "Rescheduling is not yet supported. Try adding a new task instead."
                        )
                    } else {
                        voiceCaptureManager.showPreview(validatedResponse)
                    }
```
With:
```kotlin
                    lastResponse = validatedResponse
                    voiceCaptureManager.showPreview(validatedResponse)
```

- [ ] **Step 6: Route retry transcriptions through `parseRescheduleRetry()`** — modify `handleRawTranscription()` to branch on whether a retry context is set.

Replace the line:
```kotlin
            val parseResult = geminiCaptureRepository.parseVoiceInputV2(
                apiKey = apiKey,
                rawText = normalizedText,
                existingLists = existingLists,
                existingTasks = existingTasks,
                todayDate = LocalDate.now(),
                currentTime = LocalTime.now()
            )
```
With:
```kotlin
            val retryCtx = rescheduleRetryContext
            val parseResult = if (retryCtx != null) {
                rescheduleRetryContext = null  // consume immediately — one retry only
                geminiCaptureRepository.parseRescheduleRetry(
                    apiKey = apiKey,
                    originalTranscript = retryCtx.originalTranscript,
                    targetTaskTitle = retryCtx.targetTaskTitle,
                    firstParsedDate = retryCtx.firstParsedDate,
                    clarificationTranscript = normalizedText,
                    existingLists = existingLists,
                    existingTasks = existingTasks,
                    todayDate = LocalDate.now()
                ).map { response ->
                    // Re-inject the known targetTaskId into parentTaskId,
                    // since the retry prompt can't re-look it up.
                    response.copy(
                        tasks = response.tasks.map { task ->
                            task.copy(parentTaskId = retryCtx.targetTaskId)
                        }
                    )
                }
            } else {
                geminiCaptureRepository.parseVoiceInputV2(
                    apiKey = apiKey,
                    rawText = normalizedText,
                    existingLists = existingLists,
                    existingTasks = existingTasks,
                    todayDate = LocalDate.now(),
                    currentTime = LocalTime.now()
                )
            }
```

- [ ] **Step 7: Verify it compiles**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/VoiceParsingCoordinator.kt
git commit -m "feat: unblock RESCHEDULE in VoiceParsingCoordinator, add retry context"
```

---

## Task 4: Update `TaskWallViewModel`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

Four sub-changes:
1. Add `transientMessage: String?` to `TaskWallUiState`.
2. Add `setTransientMessage()` + `clearTransientMessage()` private helpers.
3. Add `observeVoiceState()` to auto-confirm high-confidence RESCHEDULE.
4. Implement the RESCHEDULE branch in `confirmVoiceTasks()`.
5. Add public `retryReschedule()`.

- [ ] **Step 1: Add `transientMessage` to `TaskWallUiState`**

Find the data class (around line 62):
```kotlin
data class TaskWallUiState(
```
Add one new field at the end (before the closing `)`):
```kotlin
    val transientMessage: String? = null
```

- [ ] **Step 2: Add private helpers** — add these after `dismissVoiceError()` in the ViewModel class:

```kotlin
private var transientMessageJob: Job? = null

private fun setTransientMessage(message: String) {
    _uiState.update { it.copy(transientMessage = message) }
    transientMessageJob?.cancel()
    transientMessageJob = viewModelScope.launch {
        delay(2000)
        _uiState.update { it.copy(transientMessage = null) }
    }
}

fun clearTransientMessage() {
    transientMessageJob?.cancel()
    _uiState.update { it.copy(transientMessage = null) }
}
```

- [ ] **Step 3: Add `autoConfirmReschedule()` private helper** — add after `setTransientMessage`:

```kotlin
private suspend fun autoConfirmReschedule(response: ParsedVoiceResponse) {
    val task = response.tasks.firstOrNull() ?: run {
        voiceCaptureManager.setError("Couldn't find that task")
        return
    }
    val targetId = task.parentTaskId ?: run {
        voiceCaptureManager.setError("Couldn't identify which task to reschedule")
        return
    }
    val targetListId = findTaskListId(targetId) ?: run {
        voiceCaptureManager.setError("Couldn't find that task in your lists")
        return
    }
    voiceCaptureManager.resetToIdle()
    voiceParsingCoordinator.clearMetadata()
    tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
        onSuccess = {
            val dateLabel = if (task.dueDate == null) "cleared"
                           else task.dueDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d"))
            setTransientMessage("Moved to $dateLabel")
            refresh()
        },
        onFailure = {
            voiceCaptureManager.setError("Failed to reschedule task")
        }
    )
}
```

- [ ] **Step 4: Add `observeVoiceState()`** — add the method to the ViewModel class:

```kotlin
private fun observeVoiceState() {
    viewModelScope.launch {
        voiceCaptureManager.state.collect { state ->
            if (state is VoiceInputState.Preview &&
                state.response.intent == VoiceIntent.RESCHEDULE &&
                (state.response.tasks.firstOrNull()?.confidence ?: 0f) > 0.70f) {
                autoConfirmReschedule(state.response)
            }
        }
    }
}
```

Then call it from `init {}`, after the existing `observeConnectivity()` call:
```kotlin
observeVoiceState()
```

- [ ] **Step 5: Implement the RESCHEDULE branch in `confirmVoiceTasks()`**

Find:
```kotlin
                VoiceIntent.RESCHEDULE -> {
                    voiceCaptureManager.setError("Rescheduling is not yet supported. Try adding a new task instead.")
                }
```

Replace with:
```kotlin
                VoiceIntent.RESCHEDULE -> {
                    val task = response.tasks.firstOrNull() ?: run {
                        voiceCaptureManager.setError("Couldn't find that task")
                        return@launch
                    }
                    val targetId = task.parentTaskId ?: run {
                        voiceCaptureManager.setError("Couldn't identify which task to reschedule")
                        return@launch
                    }
                    val targetListId = findTaskListId(targetId) ?: run {
                        voiceCaptureManager.setError("Couldn't find that task in your lists")
                        return@launch
                    }
                    voiceParsingCoordinator.clearMetadata()
                    voiceCaptureManager.resetToIdle()
                    tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
                        onSuccess = {
                            val dateLabel = if (task.dueDate == null) "cleared"
                                           else task.dueDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d"))
                            setTransientMessage("Moved to $dateLabel")
                            refresh()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to reschedule task")
                        }
                    )
                }
```

- [ ] **Step 6a: Add the import** at the top of `TaskWallViewModel.kt` with the other capture imports:

```kotlin
import com.example.todowallapp.capture.RescheduleRetryContext
```

- [ ] **Step 6b: Add `retryReschedule()` public method** — add after `dismissVoiceError()`:

```kotlin
/**
 * Called when the user taps Retry on a low-confidence reschedule preview card.
 * Arms the coordinator with retry context, then starts a new listening session.
 */
fun retryReschedule() {
    val response = voiceParsingCoordinator.lastResponse ?: return
    val task = response.tasks.firstOrNull() ?: return
    val targetId = task.parentTaskId ?: return   // can't retry without knowing target
    val ctx = RescheduleRetryContext(
        originalTranscript = response.rawTranscript,
        targetTaskTitle = task.title,
        targetTaskId = targetId,
        firstParsedDate = task.dueDate
    )
    voiceParsingCoordinator.armRescheduleRetry(ctx)
    voiceCaptureManager.resetToIdle()   // leave Preview state before starting listen
    voiceCaptureManager.startListening()
}
```

Add the import at the top of the file (if not already present):
```kotlin
import com.example.todowallapp.capture.RescheduleRetryContext
```

- [ ] **Step 7: Verify it compiles**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat: implement RESCHEDULE handling in TaskWallViewModel"
```

---

## Task 5: Update `TaskWallScreen` and `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`
- Modify: `app/src/main/java/com/example/todowallapp/MainActivity.kt`

Two UI changes:
1. The voice preview card for RESCHEDULE shows "Move [task] → [date]" with 3-option nav (Accept / Retry / Cancel).
2. A transient confirmation banner appears at the top when `transientMessage != null`.

### 5a — Update `TaskWallScreen` function signature

- [ ] **Step 1: Add new parameters to `TaskWallScreen`** — find the parameter list (around line 244) and add after `onDismissVoiceError`:

```kotlin
onRetryReschedule: () -> Unit = {},
transientMessage: String? = null,
```

### 5b — Expand encoder nav for RESCHEDULE preview

The current `voicePreviewFocus` has 2 states (0=Confirm, 1=Cancel). For RESCHEDULE we need 3 (0=Accept, 1=Retry, 2=Cancel). The simplest approach: keep one focus variable, branch logic on `isReschedulePreview`.

- [ ] **Step 2: Add a local val for reschedule detection** — in the `Box.onKeyEvent` lambda, find the `if (voiceState is VoiceInputState.Preview)` block and add this val at the very top of that block (before the `when`):

```kotlin
                if (voiceState is VoiceInputState.Preview) {
                    val isReschedulePreview = (voiceState as VoiceInputState.Preview).response.intent == VoiceIntent.RESCHEDULE
                    val maxFocusIndex = if (isReschedulePreview) 2 else 1
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionRight, Key.DirectionDown -> {
                                voicePreviewFocus = (voicePreviewFocus + 1) % (maxFocusIndex + 1)
                                performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                            }
                            Key.DirectionLeft, Key.DirectionUp -> {
                                voicePreviewFocus = (voicePreviewFocus - 1 + maxFocusIndex + 1) % (maxFocusIndex + 1)
                                performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
                            }
                            Key.Enter, Key.NumPadEnter -> {
                                when {
                                    isReschedulePreview && voicePreviewFocus == 0 ->
                                        onConfirmVoice((voiceState as VoiceInputState.Preview).targetListId)
                                    isReschedulePreview && voicePreviewFocus == 1 ->
                                        onRetryReschedule()
                                    else ->  // focus==1 non-reschedule OR focus==2 reschedule → Cancel
                                        onCancelVoice()
                                }
                                performAppHaptic(view, context, AppHapticPattern.CONFIRM)
                            }
                        }
                    }
                    return@onKeyEvent true
                }
```

> This fully replaces the existing `if (voiceState is VoiceInputState.Preview)` block (the original only handled Right/Left/Up/Down as one action and had a simpler Enter handler).

### 5c — Render the RESCHEDULE preview card variant

- [ ] **Step 3: Replace the `is VoiceInputState.Preview` card** in the voice overlay `when` block.

Find:
```kotlin
                    is VoiceInputState.Preview -> {
                        val response = state.response
                        val intentLabel = when (response.intent) {
```

Replace the entire `is VoiceInputState.Preview -> { ... }` arm with:

```kotlin
                    is VoiceInputState.Preview -> {
                        val response = state.response
                        if (response.intent == VoiceIntent.RESCHEDULE) {
                            // --- Reschedule-specific card ---
                            val task = response.tasks.firstOrNull()
                            val taskTitle = task?.title?.ifBlank { response.rawTranscript } ?: response.rawTranscript
                            val newDateLabel = task?.dueDate?.let { date ->
                                val today = java.time.LocalDate.now()
                                when {
                                    date == today -> "Today"
                                    date == today.plusDays(1) -> "Tomorrow"
                                    else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d"))
                                }
                            } ?: "no date"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .padding(32.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        "Move task",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            "\u201C$taskTitle\u201D",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            "\u2192",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = colors.textSecondary
                                        )
                                        Text(
                                            newDateLabel,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.accentWarm
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    // Three-button row: Accept / Retry / Cancel
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(
                                            Triple(0, "Accept", colors.accentPrimary),
                                            Triple(1, "Retry",  colors.surfaceExpanded),
                                            Triple(2, "Cancel", colors.surfaceExpanded)
                                        ).forEach { (index, label, activeColor) ->
                                            val isFocused = voicePreviewFocus == index
                                            val isCancelOption = index == 2
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        when {
                                                            isFocused && isCancelOption -> colors.urgencyOverdue.copy(alpha = 0.3f)
                                                            isFocused -> activeColor
                                                            else -> colors.surfaceExpanded
                                                        }
                                                    )
                                                    .clickable {
                                                        when (index) {
                                                            0 -> onConfirmVoice(state.targetListId)
                                                            1 -> onRetryReschedule()
                                                            else -> onCancelVoice()
                                                        }
                                                    }
                                                    .padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    label,
                                                    color = when {
                                                        isFocused && !isCancelOption && index == 0 -> Color.White
                                                        isFocused && isCancelOption -> colors.urgencyOverdue
                                                        else -> colors.textSecondary
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // --- Standard ADD/COMPLETE/DELETE/AMEND card (unchanged) ---
                            val intentLabel = when (response.intent) {
                                VoiceIntent.ADD -> if (response.tasks.size > 1) "Draft Tasks" else "Draft Task"
                                VoiceIntent.COMPLETE -> "Complete Task"
                                VoiceIntent.DELETE -> "Delete Task"
                                VoiceIntent.RESCHEDULE -> "Reschedule Task"
                                VoiceIntent.QUERY -> "Tasks Found"
                                VoiceIntent.AMEND -> "Amended Task"
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(32.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Text(
                                        intentLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    response.tasks.forEachIndexed { index, task ->
                                        if (index > 0) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(0.5.dp)
                                                    .background(colors.textSecondary.copy(alpha = 0.2f))
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                        }
                                        Text(
                                            task.title.ifBlank { response.rawTranscript },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.textPrimary
                                        )
                                        if (task.dueDate != null || task.preferredTime != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val dueParts = buildList {
                                                task.dueDate?.let { add("Due: $it") }
                                                task.preferredTime?.let { add(it.name.lowercase()) }
                                            }
                                            Text(
                                                dueParts.joinToString(" \u00B7 "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.accentWarm
                                            )
                                        }
                                        if (task.duplicateOf != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Similar task already exists",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                    val clarificationText = state.clarification
                                    if (clarificationText != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            clarificationText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        val confirmLabel = when (response.intent) {
                                            VoiceIntent.ADD -> if (response.tasks.size > 1) "Add All" else "Confirm"
                                            VoiceIntent.COMPLETE -> "Complete"
                                            VoiceIntent.DELETE -> "Delete"
                                            VoiceIntent.RESCHEDULE -> "Reschedule"
                                            VoiceIntent.QUERY -> "Dismiss"
                                            VoiceIntent.AMEND -> "Confirm"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (voicePreviewFocus == 0) colors.accentPrimary
                                                    else colors.surfaceExpanded
                                                )
                                                .clickable { onConfirmVoice(state.targetListId) }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                confirmLabel,
                                                color = if (voicePreviewFocus == 0) Color.White
                                                        else colors.textPrimary
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (voicePreviewFocus == 1) colors.urgencyOverdue.copy(alpha = 0.3f)
                                                    else colors.surfaceExpanded
                                                )
                                                .clickable { onCancelVoice() }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "Cancel",
                                                color = if (voicePreviewFocus == 1) colors.urgencyOverdue
                                                        else colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
```

### 5d — Transient confirmation banner

- [ ] **Step 4: Add the transient message banner** — find the undo toast anchor comment (around line 1324):
```kotlin
        // Undo toast — bottom-center, above tap-to-wake overlay
        UndoToast(
```
Insert the banner block **immediately before** the `// Undo toast` comment:

```kotlin
// Transient confirmation banner (e.g., "Moved to Friday Mar 28")
AnimatedVisibility(
    visible = transientMessage != null,
    enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -it }),
    exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(targetOffsetY = { -it }),
    modifier = Modifier.align(Alignment.TopCenter)
) {
    if (transientMessage != null) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.accentPrimary.copy(alpha = 0.92f),
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = transientMessage,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}
```

> The `AnimatedVisibility` must be inside the root `Box` that already holds the undo toast, with `modifier = Modifier.align(Alignment.TopCenter)`. Check that the root `Box` uses `contentAlignment = Alignment.BottomCenter` for the undo toast — if so, the new banner's modifier just needs `.align(Alignment.TopCenter)` explicitly.

Import needed (add if missing):
```kotlin
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
```

### 5e — Wire in MainActivity

- [ ] **Step 5: Add `onRetryReschedule` and `transientMessage` to the `TaskWallScreen(...)` call in `MainActivity.kt`**

Find the existing `onConfirmVoice` line in the call site (~line 480) and add after it:
```kotlin
onRetryReschedule = viewModel::retryReschedule,
```

And find where `uiState` fields are read (near `error = uiState.error`) and add:
```kotlin
transientMessage = uiState.transientMessage,
```

- [ ] **Step 6: Verify the full project compiles**

```bash
./gradlew.bat :app:compileDebugKotlin 2>&1 | tail -30
```
Expected: `BUILD SUCCESSFUL` with zero errors.

- [ ] **Step 7: Build the debug APK**

```bash
./gradlew.bat assembleDebug 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git add app/src/main/java/com/example/todowallapp/MainActivity.kt
git commit -m "feat: RESCHEDULE preview card, retry flow, and transient confirmation banner"
```

---

## Task 6: Manual Smoke Test

Deploy to the wall device and exercise all paths.

- [ ] **Test A — High confidence reschedule:** Say "Move [real task title] to [clear date, e.g. next Monday]". Expect: no preview card shown; wall returns to normal with a banner reading "Moved to Mon Mar 30" (or similar) for ~2 seconds, then disappears.

- [ ] **Test B — Low confidence reschedule:** Say something ambiguous like "push that thing". Expect: reschedule preview card appears with "Move [task] → [date]", Accept highlighted by default. Rotate to Retry; rotate again to Cancel. Verify encoder cycling wraps correctly.

- [ ] **Test C — Accept from preview:** Show low-confidence preview, press Accept. Expect: task due date updated, confirmation banner appears, voice resets to idle.

- [ ] **Test D — Retry flow:** Show low-confidence preview, navigate to Retry, press click. Expect: screen returns to Listening/waveform state. Speak a clear correction (e.g., "No, I meant this Friday"). Expect: a new preview card appears with the corrected date. Accept it.

- [ ] **Test E — Cancel:** Show low-confidence preview, navigate to Cancel, press click. Expect: returns to idle with no change to any task.

- [ ] **Test F — Task not found:** Say "Move a task that doesn't exist to Monday". Expect: error card appears with a descriptive message, not a silent no-op.

- [ ] **Final commit** (if any fixups were needed from smoke test):

```bash
git add -p
git commit -m "fix: smoke test fixups for voice reschedule"
```
