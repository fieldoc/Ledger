# Day Organizer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Voice-triggered Gemini day planning on calendar views — brain dump, smart scheduling around existing events, time-blocked preview, promote to Google Calendar on accept.

**Architecture:** New `DayOrganizerCoordinator` owns the planning state machine (Idle → Listening → Processing → PlanReady → Adjusting → Confirming). Reuses existing `VoiceCaptureManager` for speech-to-text and `GeminiCaptureRepository` for AI calls. New `DayOrganizerOverlay` composable renders the plan preview timeline. ViewModel exposes `dayOrganizerState: StateFlow` and delegates to the coordinator.

**Tech Stack:** Kotlin, Jetpack Compose, Google Calendar API, Gemini API (gemini-2.5-flash), Android SpeechRecognizer

**Spec:** `docs/superpowers/specs/2026-03-29-day-organizer-design.md`

---

## File Map

| File | Responsibility | Action |
|------|---------------|--------|
| `data/model/DayPlan.kt` | `DayPlan`, `PlanBlock`, `BlockCategory` data classes | **Create** |
| `capture/DayOrganizerCoordinator.kt` | State machine, Gemini orchestration, voice callback swap | **Create** |
| `capture/repository/GeminiCaptureRepository.kt` | `buildDayPlanPrompt()`, `buildPlanAdjustmentPrompt()`, `parseDayPlanResponse()` | **Modify** (append after line ~626) |
| `ui/components/DayOrganizerOverlay.kt` | Plan preview timeline, listening/processing/error overlays, action bar | **Create** |
| `ui/theme/Color.kt` | Add `PlanAccent` color token | **Modify** (after line 25) |
| `ui/theme/LedgerTheme.kt` | Wire `planAccent` into `WallColors` | **Modify** |
| `viewmodel/TaskWallViewModel.kt` | `dayOrganizerState` StateFlow, 6 public methods, coordinator wiring | **Modify** |
| `ui/screens/CalendarScreen.kt` | Voice FAB, overlay rendering, encoder nav for FAB + plan preview actions | **Modify** |

All paths are relative to `app/src/main/java/com/example/todowallapp/`.

---

## Task 1: Data Models

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/data/model/DayPlan.kt`

- [ ] **Step 1: Create `DayPlan.kt` with all data classes**

```kotlin
package com.example.todowallapp.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Output of the Day Organizer: a time-blocked plan for a single day.
 */
data class DayPlan(
    val targetDate: LocalDate,
    val blocks: List<PlanBlock>,
    val summary: String,
    val confidence: Float,
    val warning: String? = null
)

data class PlanBlock(
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val category: BlockCategory,
    val isExistingEvent: Boolean,
    val existingEventId: String? = null,
    val notes: String? = null,
    val sourceTaskId: String? = null,
    val sourceTaskListId: String? = null
)

enum class BlockCategory {
    ACTIVE,
    PASSIVE,
    ERRAND,
    SOCIAL,
    LEISURE,
    EXISTING_EVENT
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/data/model/DayPlan.kt
git commit -m "feat(day-organizer): add DayPlan, PlanBlock, BlockCategory data models"
```

---

## Task 2: Plan Accent Color

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/Color.kt` (after line 25)
- Modify: `app/src/main/java/com/example/todowallapp/ui/theme/LedgerTheme.kt` (WallColors class)

- [ ] **Step 1: Add `PlanAccent` to Color.kt**

In `Color.kt`, after the `AccentWarm` line (~line 25), add:

```kotlin
val PlanAccent = Color(0xFFA8D5BA)            // Soft sage green for day organizer blocks
```

Also add in the light-mode section (after the equivalent light accents):

```kotlin
val LightPlanAccent = Color(0xFF66BB6A)       // Sage green for light mode
```

- [ ] **Step 2: Wire `planAccent` into WallColors**

Read `LedgerTheme.kt` to find the `WallColors` data class. Add a `planAccent: Color` field. Wire it in both dark and light palette constructors.

In the `WallColors` data class, add:
```kotlin
val planAccent: Color,
```

In the dark palette construction, add:
```kotlin
planAccent = PlanAccent,
```

In the light palette construction, add:
```kotlin
planAccent = LightPlanAccent,
```

- [ ] **Step 3: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/theme/Color.kt app/src/main/java/com/example/todowallapp/ui/theme/LedgerTheme.kt
git commit -m "feat(day-organizer): add planAccent color token to theme"
```

---

## Task 3: DayOrganizerState Sealed Class

This goes inside the coordinator file but we create it first so the ViewModel can reference it.

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt` (initial skeleton)

- [ ] **Step 1: Create coordinator file with state sealed class and empty coordinator class**

```kotlin
package com.example.todowallapp.capture

import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.BlockCategory
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.voice.VoiceCaptureManager
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.security.GeminiKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed class DayOrganizerState {
    object Idle : DayOrganizerState()

    data class Listening(
        val amplitudeLevel: Float = 0f,
        val isAdjustment: Boolean = false
    ) : DayOrganizerState()

    data class Processing(
        val isAdjustment: Boolean = false
    ) : DayOrganizerState()

    data class PlanReady(
        val plan: DayPlan,
        val focusedAction: Int = 0
    ) : DayOrganizerState()

    data class Adjusting(
        val previousPlan: DayPlan,
        val amplitudeLevel: Float = 0f
    ) : DayOrganizerState()

    object Confirming : DayOrganizerState()

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : DayOrganizerState()
}

class DayOrganizerCoordinator(
    private val voiceCaptureManager: VoiceCaptureManager,
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository
) {
    private val _state = MutableStateFlow<DayOrganizerState>(DayOrganizerState.Idle)
    val state: StateFlow<DayOrganizerState> = _state.asStateFlow()

    private var currentPlan: DayPlan? = null
    private var lastTranscription: String? = null
    private var parseJob: Job? = null

    // Placeholder — implementation in Task 5
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt
git commit -m "feat(day-organizer): add DayOrganizerState sealed class and coordinator skeleton"
```

---

## Task 4: Gemini Prompt Functions

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt` (append after ~line 626)

- [ ] **Step 1: Read the existing file ending to confirm insertion point**

Read `GeminiCaptureRepository.kt` lines 610-626 to find the exact end of the file. The new functions go inside the class body, before the closing brace.

- [ ] **Step 2: Add `buildDayPlanPrompt()` function**

Append inside the class (before the final `}`):

```kotlin
    // ── Day Organizer prompts ──────────────────────────────────────────

    fun buildDayPlanPrompt(
        rawTranscription: String,
        existingEvents: List<String>,   // pre-formatted: "9:00-9:30 Team standup"
        existingTasks: List<ExistingTaskRef>,
        targetDate: LocalDate,
        currentTime: LocalTime
    ): String {
        val eventsBlock = if (existingEvents.isEmpty()) "No existing events."
            else existingEvents.joinToString("\n") { "- $it" }

        val tasksBlock = if (existingTasks.isEmpty()) "No existing tasks."
            else existingTasks.take(40).joinToString("\n") { "- ${it.title} (list: ${it.listId})" }

        val timeContext = if (targetDate == LocalDate.now()) {
            "Start scheduling from ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))} (round to next half-hour)."
        } else {
            "Start scheduling from 07:00 (morning)."
        }

        return """
You are a day planning assistant. The user described what they want to accomplish today.
Produce a time-blocked schedule as a JSON object.

TARGET DATE: $targetDate
CURRENT TIME: ${currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))}
$timeContext

EXISTING CALENDAR EVENTS (DO NOT move or modify these — schedule around them):
$eventsBlock

EXISTING GOOGLE TASKS (for reference — match by name if the user mentions one):
$tasksBlock

USER'S REQUEST:
"$rawTranscription"

RULES:
1. NEVER move or modify existing calendar events. Include them in the output with isExistingEvent=true.
2. Categorize each NEW block: ACTIVE (cognitively demanding), PASSIVE (low-attention, may need proximity), ERRAND (location-based), SOCIAL (calls, meetings), LEISURE (reading, relaxation).
3. Energy curve: schedule ACTIVE tasks in the morning (before noon), PASSIVE/LEISURE in the evening. ERRAND blocks should cluster together to minimize travel.
4. For PASSIVE tasks with follow-up (e.g., laundry needs changeover), add a notes field and schedule a brief follow-up block at the appropriate time.
5. Estimate durations reasonably. If the user provided durations, use them. Common defaults: dog walk 30min, run 30-45min, bank 30min, grocery 45min, phone call 20-30min.
6. If the user mentioned an existing Google Task by name (fuzzy match), set sourceTaskId to the task ID and sourceTaskListId to the list ID. Otherwise leave null.
7. Leave at least 15 minutes buffer between blocks that require location changes.
8. Return confidence 0.0-1.0 for the overall plan. If confidence < 0.5, add a "warning" field explaining uncertainty.
9. The summary should be concise: "N blocks, M new — key insight" (e.g., "8 blocks, 6 new — errands clustered at 1pm").
10. Do NOT use emoji in titles. Plain text only.

RESPONSE FORMAT (strict JSON):
{
  "targetDate": "YYYY-MM-DD",
  "confidence": 0.0-1.0,
  "warning": null,
  "summary": "string",
  "blocks": [
    {
      "title": "string",
      "startTime": "HH:mm",
      "endTime": "HH:mm",
      "category": "ACTIVE|PASSIVE|ERRAND|SOCIAL|LEISURE|EXISTING_EVENT",
      "isExistingEvent": false,
      "existingEventId": null,
      "notes": null,
      "sourceTaskId": null,
      "sourceTaskListId": null
    }
  ]
}
""".trimIndent()
    }
```

- [ ] **Step 3: Add `buildPlanAdjustmentPrompt()` function**

```kotlin
    fun buildPlanAdjustmentPrompt(
        adjustmentRequest: String,
        previousPlanJson: String,
        existingEvents: List<String>,
        existingTasks: List<ExistingTaskRef>,
        targetDate: LocalDate,
        currentTime: LocalTime
    ): String {
        val basePrompt = buildDayPlanPrompt(
            rawTranscription = adjustmentRequest,
            existingEvents = existingEvents,
            existingTasks = existingTasks,
            targetDate = targetDate,
            currentTime = currentTime
        )

        return """
$basePrompt

IMPORTANT — ADJUSTMENT MODE:
The user already has a plan and wants to modify it. Here is the current plan:

$previousPlanJson

Apply the user's requested changes while keeping the rest of the plan intact.
Re-optimize timing if the change cascades (e.g., moving a block earlier frees a later slot).
Return the COMPLETE updated plan, not just the changed blocks.
""".trimIndent()
    }
```

- [ ] **Step 4: Add `parseDayPlanResponse()` function**

This parses the Gemini JSON response into a `DayPlan`. Place it after the prompt builders:

```kotlin
    fun parseDayPlanResponse(responseJson: String, targetDate: LocalDate): DayPlan {
        val root = org.json.JSONObject(responseJson)

        val parsedDate = try {
            LocalDate.parse(root.getString("targetDate"))
        } catch (_: Exception) { targetDate }

        val confidence = root.optDouble("confidence", 0.7).toFloat()
        val warning = root.optString("warning", "").ifBlank { null }
        val summary = root.optString("summary", "Plan ready")

        val blocksArray = root.getJSONArray("blocks")
        val blocks = (0 until blocksArray.length()).map { i ->
            val obj = blocksArray.getJSONObject(i)
            val startTime = LocalTime.parse(obj.getString("startTime"), DateTimeFormatter.ofPattern("HH:mm"))
            val endTime = LocalTime.parse(obj.getString("endTime"), DateTimeFormatter.ofPattern("HH:mm"))

            PlanBlock(
                title = obj.getString("title"),
                startTime = parsedDate.atTime(startTime),
                endTime = parsedDate.atTime(endTime),
                category = try {
                    BlockCategory.valueOf(obj.getString("category"))
                } catch (_: Exception) { BlockCategory.ACTIVE },
                isExistingEvent = obj.optBoolean("isExistingEvent", false),
                existingEventId = obj.optString("existingEventId", "").ifBlank { null },
                notes = obj.optString("notes", "").ifBlank { null },
                sourceTaskId = obj.optString("sourceTaskId", "").ifBlank { null },
                sourceTaskListId = obj.optString("sourceTaskListId", "").ifBlank { null }
            )
        }

        return DayPlan(
            targetDate = parsedDate,
            blocks = blocks.sortedBy { it.startTime },
            summary = summary,
            confidence = confidence,
            warning = warning
        )
    }
```

- [ ] **Step 5: Add necessary imports at the top of the file**

Add these imports if not already present:
```kotlin
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.BlockCategory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
```

- [ ] **Step 6: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat(day-organizer): add Gemini prompt builders and plan response parser"
```

---

## Task 5: DayOrganizerCoordinator Implementation

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt` (replace placeholder)

- [ ] **Step 1: Read the existing GeminiCaptureRepository HTTP call pattern**

Read `GeminiCaptureRepository.kt` lines 585-626 to understand the `HttpGeminiApiClient` pattern — the coordinator needs to call Gemini the same way. Also read lines 104-160 (`parseVoiceInputV2`) to understand the API call flow: build prompt → build request JSON → HTTP POST → extract text → parse JSON.

- [ ] **Step 2: Implement the coordinator's core methods**

Replace the placeholder comment in `DayOrganizerCoordinator.kt` with the full implementation:

```kotlin
    /**
     * Start a new planning session. Takes ownership of VoiceCaptureManager's rawResultCallback.
     * Call from ViewModel on FAB click.
     */
    fun startListening(
        scope: CoroutineScope,
        listProvider: () -> List<ExistingListRef>,
        taskProvider: () -> List<ExistingTaskRef>,
        eventsProvider: suspend () -> List<String>,
        selectedCalendarId: String
    ) {
        this.scope = scope
        this.listProvider = listProvider
        this.taskProvider = taskProvider
        this.eventsProvider = eventsProvider
        this.selectedCalendarId = selectedCalendarId

        _state.value = DayOrganizerState.Listening()

        voiceCaptureManager.rawResultCallback = { rawText ->
            handleTranscription(rawText)
        }
        voiceCaptureManager.startListening()
    }

    /**
     * Stop listening and send to Gemini.
     */
    fun stopListening() {
        voiceCaptureManager.stopListening()
        // rawResultCallback will fire with the final transcription
    }

    /**
     * Start adjustment listening (user wants to change the plan).
     */
    fun startAdjustment() {
        val plan = currentPlan ?: return
        _state.value = DayOrganizerState.Adjusting(previousPlan = plan)

        voiceCaptureManager.rawResultCallback = { rawText ->
            handleAdjustmentTranscription(rawText, plan)
        }
        voiceCaptureManager.startListening()
    }

    /**
     * Stop adjustment listening.
     */
    fun stopAdjustmentListening() {
        voiceCaptureManager.stopListening()
    }

    /**
     * Accept the current plan — create calendar events for all new blocks.
     */
    suspend fun acceptPlan(): Result<Int> {
        val plan = currentPlan ?: return Result.failure(Exception("No plan to accept"))
        _state.value = DayOrganizerState.Confirming

        val newBlocks = plan.blocks.filter { !it.isExistingEvent }
        var successCount = 0

        for (block in newBlocks) {
            val description = buildEventDescription(block)
            val result = withContext(Dispatchers.IO) {
                calendarRepository.createEvent(
                    title = block.title,
                    startDateTime = block.startTime,
                    endDateTime = block.endTime,
                    calendarId = selectedCalendarId,
                    description = description
                )
            }
            if (result.isSuccess) successCount++
        }

        currentPlan = null
        lastTranscription = null
        _state.value = DayOrganizerState.Idle

        return Result.success(successCount)
    }

    /**
     * Cancel and return to idle. Releases VoiceCaptureManager callback.
     */
    fun cancel() {
        parseJob?.cancel()
        parseJob = null
        currentPlan = null
        lastTranscription = null
        voiceCaptureManager.resetToIdle()
        _state.value = DayOrganizerState.Idle
    }

    /**
     * Update amplitude level for waveform visualization.
     */
    fun updateAmplitude(level: Float) {
        val current = _state.value
        when (current) {
            is DayOrganizerState.Listening -> _state.value = current.copy(amplitudeLevel = level)
            is DayOrganizerState.Adjusting -> _state.value = current.copy(amplitudeLevel = level)
            else -> {}
        }
    }

    /**
     * Update focused action index in PlanReady state.
     */
    fun setFocusedAction(index: Int) {
        val current = _state.value
        if (current is DayOrganizerState.PlanReady) {
            _state.value = current.copy(focusedAction = index)
        }
    }

    // ── Private ──

    private var scope: CoroutineScope? = null
    private var listProvider: (() -> List<ExistingListRef>)? = null
    private var taskProvider: (() -> List<ExistingTaskRef>)? = null
    private var eventsProvider: (suspend () -> List<String>)? = null
    private var selectedCalendarId: String = "primary"

    private fun handleTranscription(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.length < 3) {
            _state.value = DayOrganizerState.Error(
                message = "I didn't catch that. Click to try again.",
                canRetry = true
            )
            return
        }

        lastTranscription = trimmed
        _state.value = DayOrganizerState.Processing()

        parseJob = scope?.launch {
            try {
                val apiKey = geminiKeyStore.getKey()
                if (apiKey == null) {
                    _state.value = DayOrganizerState.Error("Gemini API key not configured.", canRetry = false)
                    return@launch
                }

                val events = eventsProvider?.invoke() ?: emptyList()
                val tasks = taskProvider?.invoke() ?: emptyList()
                // Gemini handles target day extraction in its response (e.g., "plan my Saturday" → targetDate in JSON).
                // We pass today as the default; parseDayPlanResponse uses Gemini's returned targetDate.
                val targetDate = LocalDate.now()

                val prompt = geminiCaptureRepository.buildDayPlanPrompt(
                    rawTranscription = trimmed,
                    existingEvents = events,
                    existingTasks = tasks,
                    targetDate = targetDate,
                    currentTime = LocalTime.now()
                )

                val responseJson = withContext(Dispatchers.IO) {
                    geminiCaptureRepository.callGeminiForDayPlan(apiKey, prompt)
                }

                val plan = geminiCaptureRepository.parseDayPlanResponse(responseJson, targetDate)
                currentPlan = plan
                _state.value = DayOrganizerState.PlanReady(plan = plan)

            } catch (e: Exception) {
                _state.value = DayOrganizerState.Error(
                    message = e.message ?: "Planning failed. Try again.",
                    canRetry = true
                )
            }
        }
    }

    private fun handleAdjustmentTranscription(rawText: String, previousPlan: DayPlan) {
        val trimmed = rawText.trim()
        if (trimmed.length < 3) {
            // Short input — just go back to the existing plan
            _state.value = DayOrganizerState.PlanReady(plan = previousPlan)
            return
        }

        _state.value = DayOrganizerState.Processing(isAdjustment = true)

        parseJob = scope?.launch {
            try {
                val apiKey = geminiKeyStore.getKey()
                if (apiKey == null) {
                    _state.value = DayOrganizerState.Error("Gemini API key not configured.", canRetry = false)
                    return@launch
                }

                val events = eventsProvider?.invoke() ?: emptyList()
                val tasks = taskProvider?.invoke() ?: emptyList()

                val previousPlanJson = serializePlanToJson(previousPlan)

                val prompt = geminiCaptureRepository.buildPlanAdjustmentPrompt(
                    adjustmentRequest = trimmed,
                    previousPlanJson = previousPlanJson,
                    existingEvents = events,
                    existingTasks = tasks,
                    targetDate = previousPlan.targetDate,
                    currentTime = LocalTime.now()
                )

                val responseJson = withContext(Dispatchers.IO) {
                    geminiCaptureRepository.callGeminiForDayPlan(apiKey, prompt)
                }

                val updatedPlan = geminiCaptureRepository.parseDayPlanResponse(responseJson, previousPlan.targetDate)
                currentPlan = updatedPlan
                _state.value = DayOrganizerState.PlanReady(plan = updatedPlan)

            } catch (e: Exception) {
                // On adjustment failure, return to previous plan
                _state.value = DayOrganizerState.PlanReady(plan = previousPlan)
            }
        }
    }

    private fun buildEventDescription(block: PlanBlock): String {
        val parts = mutableListOf("[Day Organizer] ${block.category.name}")
        block.notes?.let { parts.add("Notes: $it") }
        block.sourceTaskId?.let { id ->
            parts.add("[todowallapp:task:$id]")
        }
        return parts.joinToString("\n")
    }

    private fun serializePlanToJson(plan: DayPlan): String {
        val blocksJson = plan.blocks.joinToString(",\n") { block ->
            val fmt = DateTimeFormatter.ofPattern("HH:mm")
            """    {
      "title": "${block.title.replace("\"", "\\\"")}",
      "startTime": "${block.startTime.format(fmt)}",
      "endTime": "${block.endTime.format(fmt)}",
      "category": "${block.category.name}",
      "isExistingEvent": ${block.isExistingEvent},
      "existingEventId": ${block.existingEventId?.let { "\"$it\"" } ?: "null"},
      "notes": ${block.notes?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},
      "sourceTaskId": ${block.sourceTaskId?.let { "\"$it\"" } ?: "null"},
      "sourceTaskListId": ${block.sourceTaskListId?.let { "\"$it\"" } ?: "null"}
    }"""
        }
        return """{
  "targetDate": "${plan.targetDate}",
  "confidence": ${plan.confidence},
  "warning": ${plan.warning?.let { "\"${it.replace("\"", "\\\"")}\"" } ?: "null"},
  "summary": "${plan.summary.replace("\"", "\\\"")}",
  "blocks": [
$blocksJson
  ]
}"""
    }
```

- [ ] **Step 3: Add `callGeminiForDayPlan()` to GeminiCaptureRepository**

This is the HTTP call that sends the prompt and returns raw JSON text. Add it to `GeminiCaptureRepository.kt` alongside the other prompt functions:

```kotlin
    /**
     * Call Gemini for day planning. Uses gemini-2.5-flash (stronger reasoning than lite).
     * Returns the raw JSON text from the response.
     */
    suspend fun callGeminiForDayPlan(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
        val model = "gemini-2.5-flash"
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestBody = org.json.JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                org.json.JSONObject().put("parts", org.json.JSONArray().put(
                    org.json.JSONObject().put("text", prompt)
                ))
            ))
            put("generationConfig", org.json.JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })
        }

        var lastException: Exception? = null
        val retryDelays = listOf(2000L, 4000L)

        for (attempt in 0..2) {
            try {
                val connection = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 45_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                if (responseCode in listOf(429, 500, 503) && attempt < 2) {
                    lastException = Exception("HTTP $responseCode")
                    Thread.sleep(retryDelays[attempt])
                    continue
                }

                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                    throw Exception("Gemini API error $responseCode: ${errorBody.take(200)}")
                }

                return@withContext extractTextFromGeminiResponse(responseBody)

            } catch (e: Exception) {
                lastException = e
                if (attempt < 2 && (e.message?.contains("429") == true || e.message?.contains("50") == true)) {
                    Thread.sleep(retryDelays[attempt])
                    continue
                }
                throw e
            }
        }

        throw lastException ?: Exception("Day plan request failed after retries")
    }
```

- [ ] **Step 4: Also add `createEvent` overload to `GoogleCalendarRepository`**

Read `GoogleCalendarRepository.kt` to check if the existing `createEvent()` method accepts `title` + `startDateTime` + `endDateTime` + `calendarId` + `description` directly (without a Task object). If not, add an overload:

```kotlin
    suspend fun createEvent(
        title: String,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        calendarId: String,
        description: String? = null
    ): Result<CalendarEvent>
```

Implementation body (mirrors the existing `createEvent(task, ...)` method but without Task dependency):

```kotlin
    suspend fun createEvent(
        title: String,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        calendarId: String = PRIMARY_CALENDAR_ID,
        description: String? = null
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!endDateTime.isAfter(startDateTime)) {
            return@withContext Result.failure(
                IllegalArgumentException("Event end time must be after start time")
            )
        }

        withCalendarService { service ->
            val zoneId = ZoneId.systemDefault()
            val startInstant = startDateTime.atZone(zoneId).toInstant()
            val endInstant = endDateTime.atZone(zoneId).toInstant()

            val event = Event()
                .setSummary(title)
                .apply { if (description != null) setDescription(description) }
                .setStart(
                    EventDateTime()
                        .setDateTime(DateTime(startInstant.toEpochMilli()))
                        .setTimeZone(zoneId.id)
                )
                .setEnd(
                    EventDateTime()
                        .setDateTime(DateTime(endInstant.toEpochMilli()))
                        .setTimeZone(zoneId.id)
                )

            // If description contains a task tag, also set extended properties
            val taskIdMatch = Regex("\\[todowallapp:task:(.+?)]").find(description ?: "")
            if (taskIdMatch != null) {
                event.extendedProperties = com.google.api.services.calendar.model.Event.ExtendedProperties()
                    .setPrivate(mapOf("todowall_task_id" to taskIdMatch.groupValues[1]))
            }

            val created = service.events().insert(calendarId, event).execute()
            created.toCalendarEvent(calendarId = calendarId, zoneId = zoneId)
                ?: throw IllegalStateException("Calendar API returned an invalid event payload")
        }
    }
```

- [ ] **Step 5: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt app/src/main/java/com/example/todowallapp/data/repository/GoogleCalendarRepository.kt
git commit -m "feat(day-organizer): implement DayOrganizerCoordinator and Gemini API call"
```

---

## Task 6: ViewModel Integration

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

- [ ] **Step 1: Read the ViewModel constructor and state setup**

Read `TaskWallViewModel.kt` lines 111-200 to see the constructor, field declarations, and how `voiceParsingCoordinator` is set up. This is the pattern to follow.

- [ ] **Step 2: Add `DayOrganizerCoordinator` as a lazy field**

After the `voiceParsingCoordinator` declaration (~line 185), add:

```kotlin
    private val dayOrganizerCoordinator by lazy {
        DayOrganizerCoordinator(
            voiceCaptureManager = voiceCaptureManager,
            geminiCaptureRepository = geminiCaptureRepository,
            geminiKeyStore = geminiKeyStore,
            calendarRepository = calendarRepository,
            tasksRepository = tasksRepository
        )
    }

    val dayOrganizerState: StateFlow<DayOrganizerState>
        get() = dayOrganizerCoordinator.state
```

- [ ] **Step 3: Add 6 public methods**

Add these methods in the ViewModel, grouped together. Place them after the existing voice methods (after `confirmVoiceTasks()` which ends around line 900):

```kotlin
    // ── Day Organizer ──────────────────────────────────────────────────

    fun startDayOrganizer() {
        if (dayOrganizerCoordinator.state.value !is DayOrganizerState.Idle) return

        // Swap VoiceCaptureManager ownership from voice pipeline to day organizer
        dayOrganizerCoordinator.startListening(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { list ->
                    ExistingListRef(id = list.id, title = list.title)
                }
            },
            taskProvider = {
                _uiState.value.allTaskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map {
                        ExistingTaskRef(id = it.id, title = it.title, listId = list.taskList.id)
                    }
                }.take(40)
            },
            eventsProvider = {
                val today = LocalDate.now()
                val events = calendarRepository.getEventsForDateRange(
                    today, today, _uiState.value.selectedCalendarId
                ).getOrElse { emptyMap() }

                events[today]?.map { event ->
                    val timeRange = if (event.isAllDay) "All day"
                    else "${event.startDateTime?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}-${event.endDateTime?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}"
                    "$timeRange ${event.title}"
                } ?: emptyList()
            },
            selectedCalendarId = _uiState.value.selectedCalendarId
        )
    }

    fun stopDayOrganizerListening() {
        val state = dayOrganizerCoordinator.state.value
        when (state) {
            is DayOrganizerState.Listening -> dayOrganizerCoordinator.stopListening()
            is DayOrganizerState.Adjusting -> dayOrganizerCoordinator.stopAdjustmentListening()
            else -> {}
        }
    }

    fun acceptDayPlan() {
        viewModelScope.launch {
            val result = dayOrganizerCoordinator.acceptPlan()
            result.fold(
                onSuccess = { count ->
                    _uiState.update { it.copy(transientMessage = "$count events added to your calendar") }
                    // Refresh calendar view
                    loadCalendarRangeInternal()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(transientMessage = "Failed to create events: ${error.message}") }
                }
            )
            // Restore voice pipeline ownership
            restoreVoicePipelineCallback()
        }
    }

    fun adjustDayPlan() {
        dayOrganizerCoordinator.startAdjustment()
    }

    fun cancelDayOrganizer() {
        dayOrganizerCoordinator.cancel()
        restoreVoicePipelineCallback()
    }

    fun retryDayOrganizer() {
        cancelDayOrganizer()
        startDayOrganizer()
    }

    private fun restoreVoicePipelineCallback() {
        // Re-configure the voice parsing coordinator so it owns rawResultCallback again
        voiceParsingCoordinator.configure(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { list ->
                    ExistingListRef(id = list.id, title = list.title)
                }
            },
            taskProvider = {
                _uiState.value.allTaskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map {
                        ExistingTaskRef(id = it.id, title = it.title, listId = list.taskList.id)
                    }
                }.take(30)
            },
            listIdValidator = ::resolveKnownTaskListId
        )
    }
```

- [ ] **Step 4: Add necessary imports**

Add at the top of the file:
```kotlin
import com.example.todowallapp.capture.DayOrganizerCoordinator
import com.example.todowallapp.capture.DayOrganizerState
```

- [ ] **Step 5: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat(day-organizer): wire coordinator into ViewModel with 6 public methods"
```

---

## Task 7: DayOrganizerOverlay Composable

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/DayOrganizerOverlay.kt`

- [ ] **Step 1: Read existing voice overlay pattern**

Read `TaskWallScreen.kt` lines 1152-1440 to see how the voice overlay is structured (AnimatedVisibility, state-based content switching, waveform rendering, preview card layout).

- [ ] **Step 2: Create `DayOrganizerOverlay.kt`**

This composable renders the full overlay for all Day Organizer states. It mirrors the voice overlay pattern but with a timeline preview instead of a task card:

```kotlin
package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.capture.DayOrganizerState
import com.example.todowallapp.data.model.BlockCategory
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.ui.theme.LocalWallColors
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DayOrganizerOverlay(
    state: DayOrganizerState,
    amplitudeLevel: Float,
    onStopListening: () -> Unit,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current

    AnimatedVisibility(
        visible = state !is DayOrganizerState.Idle,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is DayOrganizerState.Listening -> {
                    ListeningContent(
                        label = "PLANNING YOUR DAY",
                        amplitudeLevel = state.amplitudeLevel,
                        onStop = onStopListening
                    )
                }
                is DayOrganizerState.Adjusting -> {
                    ListeningContent(
                        label = "ADJUSTING PLAN",
                        amplitudeLevel = state.amplitudeLevel,
                        onStop = onStopListening
                    )
                }
                is DayOrganizerState.Processing -> {
                    ProcessingContent(isAdjustment = state.isAdjustment)
                }
                is DayOrganizerState.PlanReady -> {
                    PlanPreviewContent(
                        plan = state.plan,
                        focusedAction = state.focusedAction,
                        onAccept = onAccept,
                        onAdjust = onAdjust,
                        onCancel = onCancel
                    )
                }
                is DayOrganizerState.Confirming -> {
                    ProcessingContent(label = "Creating events...")
                }
                is DayOrganizerState.Error -> {
                    ErrorContent(
                        message = state.message,
                        canRetry = state.canRetry,
                        onRetry = onRetry,
                        onDismiss = onCancel
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ListeningContent(
    label: String,
    amplitudeLevel: Float,
    onStop: () -> Unit
) {
    val colors = LocalWallColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onStop)
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))
        WaveformVisualizer(
            amplitudeLevel = amplitudeLevel,
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Click to finish speaking",
            color = colors.textMuted.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ProcessingContent(
    isAdjustment: Boolean = false,
    label: String? = null
) {
    val colors = LocalWallColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colors.accentPrimary,
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = label ?: if (isAdjustment) "Adjusting plan..." else "Planning your day...",
            color = colors.textMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PlanPreviewContent(
    plan: DayPlan,
    focusedAction: Int,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalWallColors.current
    val newBlockCount = plan.blocks.count { !it.isExistingEvent }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceBlack)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (plan.targetDate == java.time.LocalDate.now()) "Today's Plan"
                       else plan.targetDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE's Plan")),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = plan.summary,
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }

        // Low-confidence warning
        plan.warning?.let { warning ->
            Text(
                text = warning,
                color = colors.accentWarm.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Timeline
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(plan.blocks) { block ->
                PlanBlockRow(block = block)
            }
        }

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actions = listOf("Accept" to onAccept, "Adjust" to onAdjust, "Cancel" to onCancel)
            actions.forEachIndexed { index, (label, action) ->
                val isFocused = index == focusedAction
                val isAccept = index == 0
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isAccept && isFocused) colors.accentPrimary
                            else if (isFocused) colors.surfaceCard
                            else colors.surfaceBlack
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) colors.accentPrimary else colors.divider,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = action)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isAccept && isFocused) colors.surfaceBlack else colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanBlockRow(block: PlanBlock) {
    val colors = LocalWallColors.current
    val isExisting = block.isExistingEvent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time label
        Text(
            text = block.startTime.format(timeFmt),
            color = colors.textMuted.copy(alpha = if (isExisting) 0.4f else 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.width(44.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Block card
        val borderColor = if (isExisting) colors.divider else colors.planAccent.copy(alpha = 0.4f)
        val bgColor = if (isExisting) colors.surfaceCard.copy(alpha = 0.5f) else colors.surfaceCard

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .then(
                    if (!isExisting) Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .padding(start = 0.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            if (isExisting) colors.accentPrimary.copy(alpha = 0.4f)
                            else colors.planAccent
                        )
                )
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = block.title,
                        color = colors.textPrimary.copy(alpha = if (isExisting) 0.6f else 1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val timeRange = "${block.startTime.format(timeFmt)} - ${block.endTime.format(timeFmt)}"
                    val categoryLabel = if (isExisting) "Existing" else block.category.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    Text(
                        text = "$timeRange  \u00B7  $categoryLabel",
                        color = colors.textMuted.copy(alpha = if (isExisting) 0.4f else 0.6f),
                        fontSize = 10.sp
                    )
                    block.notes?.let { notes ->
                        Text(
                            text = notes,
                            color = colors.textMuted.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWallColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = if (canRetry) onRetry else onDismiss)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Planning Error",
            color = colors.accentWarm,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = colors.textMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (canRetry) "Click to try again" else "Click to dismiss",
            color = colors.textMuted.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (may need import adjustments for `WaveformVisualizer`)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/DayOrganizerOverlay.kt
git commit -m "feat(day-organizer): add DayOrganizerOverlay composable with timeline preview"
```

---

## Task 8: CalendarScreen Integration

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt`
- Modify: `app/src/main/java/com/example/todowallapp/MainActivity.kt` (wire new params)

- [ ] **Step 1: Read CalendarScreen signature and Box layout**

Read `CalendarScreen.kt` lines 90-165 (signature) and lines 220-230 (Box layout start) and lines 1160-1168 (end of Box).

- [ ] **Step 2: Add new parameters to CalendarScreen**

Add these parameters to the `CalendarScreen` composable signature:

```kotlin
    // Day Organizer
    dayOrganizerState: DayOrganizerState = DayOrganizerState.Idle,
    onStartDayOrganizer: () -> Unit = {},
    onStopDayOrganizerListening: () -> Unit = {},
    onAcceptDayPlan: () -> Unit = {},
    onAdjustDayPlan: () -> Unit = {},
    onCancelDayOrganizer: () -> Unit = {},
    onRetryDayOrganizer: () -> Unit = {},
    onDayOrganizerFocusChange: (Int) -> Unit = {},
    voiceStateIdle: Boolean = true,
    geminiKeyPresent: Boolean = false,
```

- [ ] **Step 3: Add Voice FAB at the bottom of the Box**

Inside the root `Box(modifier = modifier.fillMaxSize())`, after the Column closes (before the Box's closing brace ~line 1167), add:

```kotlin
        // Day Organizer Voice FAB
        if (!isLoading && voiceStateIdle && geminiKeyPresent &&
            dayOrganizerState is DayOrganizerState.Idle && hasCalendarScope) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 32.dp)
            ) {
                VoiceFab(onClick = onStartDayOrganizer)
            }
        }

        // Day Organizer Overlay
        DayOrganizerOverlay(
            state = dayOrganizerState,
            amplitudeLevel = when (dayOrganizerState) {
                is DayOrganizerState.Listening -> dayOrganizerState.amplitudeLevel
                is DayOrganizerState.Adjusting -> dayOrganizerState.amplitudeLevel
                else -> 0f
            },
            onStopListening = onStopDayOrganizerListening,
            onAccept = onAcceptDayPlan,
            onAdjust = onAdjustDayPlan,
            onCancel = onCancelDayOrganizer,
            onRetry = onRetryDayOrganizer
        )
```

- [ ] **Step 4: Add VoiceFab composable to CalendarScreen**

Copy the `VoiceFab` private composable from `TaskWallScreen.kt` (lines 2010-2033) into `CalendarScreen.kt` as a private function. Alternatively, extract it to a shared location — but for now, duplication is simpler:

```kotlin
@Composable
private fun VoiceFab(onClick: () -> Unit) {
    val colors = LocalWallColors.current
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(colors.accentPrimary.copy(alpha = 0.85f))
            .border(1.dp, colors.accentPrimary, CircleShape)
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Plan your day with voice"
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = "Day organizer voice input",
            tint = colors.surfaceBlack,
            modifier = Modifier.size(24.dp)
        )
    }
}
```

- [ ] **Step 5: Add encoder key handling for Day Organizer states**

In the `onKeyEvent` block (~line 227), add handling BEFORE the existing calendar key handlers (the Day Organizer overlay consumes all keys when active):

```kotlin
    // Day Organizer active — consume all encoder input
    val orgState = dayOrganizerState
    if (orgState !is DayOrganizerState.Idle) {
        if (keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
            when (orgState) {
                is DayOrganizerState.Listening -> onStopDayOrganizerListening()
                is DayOrganizerState.Adjusting -> onStopDayOrganizerListening()
                is DayOrganizerState.PlanReady -> {
                    when (orgState.focusedAction) {
                        0 -> onAcceptDayPlan()
                        1 -> onAdjustDayPlan()
                        2 -> onCancelDayOrganizer()
                    }
                }
                is DayOrganizerState.Error -> {
                    if (orgState.canRetry) onRetryDayOrganizer() else onCancelDayOrganizer()
                }
                else -> {}
            }
        } else if (keyEvent.key in listOf(Key.DirectionRight, Key.DirectionDown)) {
            if (orgState is DayOrganizerState.PlanReady) {
                val next = (orgState.focusedAction + 1).coerceAtMost(2)
                // Need ViewModel to update focus — pass via callback
                onDayOrganizerFocusChange(next)
            }
        } else if (keyEvent.key in listOf(Key.DirectionLeft, Key.DirectionUp)) {
            if (orgState is DayOrganizerState.PlanReady) {
                val prev = (orgState.focusedAction - 1).coerceAtLeast(0)
                onDayOrganizerFocusChange(prev)
            }
        }
        return@onKeyEvent true  // Consume all input when overlay is active
    }
```

This requires one more callback parameter:
```kotlin
    onDayOrganizerFocusChange: (Int) -> Unit = {},
```

And a corresponding ViewModel method that calls `dayOrganizerCoordinator.setFocusedAction(index)`.

- [ ] **Step 6: Wire CalendarScreen params in MainActivity**

Read `MainActivity.kt` to find where `CalendarScreen(...)` is called (~line 463). Add the new parameters:

```kotlin
    dayOrganizerState = dayOrganizerState,
    onStartDayOrganizer = { viewModel.startDayOrganizer() },
    onStopDayOrganizerListening = { viewModel.stopDayOrganizerListening() },
    onAcceptDayPlan = { viewModel.acceptDayPlan() },
    onAdjustDayPlan = { viewModel.adjustDayPlan() },
    onCancelDayOrganizer = { viewModel.cancelDayOrganizer() },
    onRetryDayOrganizer = { viewModel.retryDayOrganizer() },
    onDayOrganizerFocusChange = { index -> viewModel.setDayOrganizerFocus(index) },
    voiceStateIdle = voiceState is VoiceInputState.Idle,
    geminiKeyPresent = geminiKeyPresent,
```

Also collect the state at the top of the composable:
```kotlin
    val dayOrganizerState by viewModel.dayOrganizerState.collectAsState()
```

- [ ] **Step 7: Add `setDayOrganizerFocus()` to ViewModel**

In `TaskWallViewModel.kt`, add:
```kotlin
    fun setDayOrganizerFocus(index: Int) {
        dayOrganizerCoordinator.setFocusedAction(index)
    }
```

- [ ] **Step 8: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt app/src/main/java/com/example/todowallapp/MainActivity.kt app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat(day-organizer): wire FAB and overlay into CalendarScreen with encoder navigation"
```

---

## Task 9: Audio Permission Handling

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt`

- [ ] **Step 1: Add audio permission launcher**

Read how `TaskWallScreen.kt` handles audio permission (lines 356-365). Add the same pattern to `CalendarScreen`:

```kotlin
val audioPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
) { granted ->
    if (granted) onStartDayOrganizer()
}

val startDayOrganizerWithPermission = remember(onStartDayOrganizer) {
    {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                onStartDayOrganizer()
            }
            else -> {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
```

Then update the FAB click to use `startDayOrganizerWithPermission` instead of `onStartDayOrganizer` directly.

- [ ] **Step 2: Verify it compiles**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt
git commit -m "feat(day-organizer): add audio permission check before voice activation"
```

---

## Task 10: Build Verification and Smoke Test

- [ ] **Step 1: Full clean build**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat clean assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint**

Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat lint 2>&1 | tail -10`
Expected: No new errors introduced

- [ ] **Step 3: Verify all imports resolve**

Check for any unresolved references by grepping build output for "Unresolved reference":
Run: `cd "C:/Users/glm_6/AndroidStudioProjects/ToDoWallApp" && ./gradlew.bat compileDebugKotlin 2>&1 | grep -i "unresolved"`
Expected: No output

- [ ] **Step 4: Final commit with build verification**

```bash
git log --oneline -8  # Review all commits from this feature
```

Verify the commit chain makes sense and each commit is self-contained.
