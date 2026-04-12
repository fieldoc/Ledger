# Voice System Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace aggressive silence timeouts with tap-to-stop, unify task/day-planning voice routing via Gemini, and make wall and phone voice UX consistent.

**Architecture:** VoiceCaptureManager becomes always-continuous with a 60s idle timeout. VoiceIntentRouter is deleted. All voice transcripts go to a single Gemini call that classifies intent (ADD/COMPLETE/DELETE/RESCHEDULE/QUERY/AMEND/DAY_PLAN) and parses content. DayOrganizerCoordinator loses its Listening/Adjusting states and receives text from the unified pipeline.

**Tech Stack:** Kotlin, Android SpeechRecognizer, Gemini API, Jetpack Compose, StateFlow

**Spec:** `docs/superpowers/specs/2026-04-12-voice-overhaul-design.md`

---

### Task 1: VoiceCaptureManager — Always-Continuous + Idle Timeout

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/voice/VoiceCaptureManager.kt`

This is the foundation — all other tasks depend on it.

- [ ] **Step 1: Remove `continuous` parameter and make always-continuous**

In `VoiceCaptureManager.kt`, change `startListening` signature and remove branching:

```kotlin
// Line 127: Remove continuous parameter
fun startListening() {
    if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.Processing) {
        return
    }
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        _state.value = VoiceInputState.Error("Speech recognition not available")
        return
    }

    mainHandler.post {
        // Always continuous — no mode flag needed
        userRequestedStop = false
        accumulatedText.clear()
        lastSpeechTimestamp = System.currentTimeMillis()

        muteBeep()

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        var currentAmplitude = 0f
        var latestPartial: String? = null
        activeListener = null

        activeListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                currentAmplitude = 0f
                latestPartial = null
                _state.value = VoiceInputState.Listening(amplitudeLevel = 0f, partialText = null)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                currentAmplitude = normalized
                // Reset idle timer on speech-level RMS
                if (normalized > IDLE_RMS_THRESHOLD) {
                    lastSpeechTimestamp = System.currentTimeMillis()
                }
                _state.value = VoiceInputState.Listening(
                    amplitudeLevel = currentAmplitude,
                    partialText = latestPartial
                )
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // Always stay in Listening — restart handles continuity
                if (!userRequestedStop) return
                _state.value = VoiceInputState.Processing
            }
            override fun onError(error: Int) {
                mainHandler.removeCallbacks(timeoutRunnable)
                mainHandler.removeCallbacks(idleCheckRunnable)
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied"
                    else -> "Voice input failed (code $error)"
                }
                speechRecognizer?.destroy()
                speechRecognizer = null

                val isSilenceError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (isSilenceError && accumulatedText.isNotEmpty()) {
                    unmuteBeep()
                    deliverAccumulatedText()
                    return
                }

                unmuteBeep()
                errorCallback?.invoke(message)
                _state.value = VoiceInputState.Error(message)
            }
            override fun onResults(results: Bundle?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

                if (text != null) {
                    if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                    accumulatedText.append(text)
                    lastSpeechTimestamp = System.currentTimeMillis()
                }
                if (userRequestedStop) {
                    deliverAccumulatedText()
                } else {
                    if (text != null || accumulatedText.isNotEmpty()) {
                        _state.value = VoiceInputState.Listening(
                            amplitudeLevel = 0f,
                            partialText = accumulatedText.toString().ifEmpty { null }
                        )
                        restartRecognizer()
                    } else {
                        // No speech at all and no accumulation — restart to wait for speech
                        restartRecognizer()
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                latestPartial = partial
                if (partial != null) lastSpeechTimestamp = System.currentTimeMillis()
                _state.value = VoiceInputState.Listening(
                    amplitudeLevel = currentAmplitude,
                    partialText = latestPartial
                )
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        speechRecognizer?.setRecognitionListener(activeListener)
        speechRecognizer?.startListening(buildSpeechIntent())
        mainHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
        startIdleCheck()
    }
}
```

- [ ] **Step 2: Add idle timeout constants and fields**

Add these to the companion object and class body:

```kotlin
private companion object {
    const val LISTENING_TIMEOUT_MS = 120_000L
    const val IDLE_TIMEOUT_MS = 60_000L
    const val IDLE_CHECK_INTERVAL_MS = 5_000L
    const val IDLE_RMS_THRESHOLD = 0.05f
}

// Add as class fields (after accumulatedText)
private var lastSpeechTimestamp = 0L
```

- [ ] **Step 3: Add idle check runnable and helper methods**

Add after the `timeoutRunnable` field:

```kotlin
private val idleCheckRunnable = object : Runnable {
    override fun run() {
        val elapsed = System.currentTimeMillis() - lastSpeechTimestamp
        if (elapsed >= IDLE_TIMEOUT_MS) {
            // Idle timeout fired
            if (accumulatedText.isNotEmpty()) {
                userRequestedStop = true
                speechRecognizer?.stopListening()
            } else {
                // No speech at all — silently cancel
                cancel()
            }
            return
        }
        mainHandler.postDelayed(this, IDLE_CHECK_INTERVAL_MS)
    }
}

private fun startIdleCheck() {
    mainHandler.removeCallbacks(idleCheckRunnable)
    mainHandler.postDelayed(idleCheckRunnable, IDLE_CHECK_INTERVAL_MS)
}

private fun deliverAccumulatedText() {
    unmuteBeep()
    mainHandler.removeCallbacks(idleCheckRunnable)
    val finalText = accumulatedText.toString().trim()
    if (finalText.isNotEmpty()) {
        val callback = rawResultCallback
        if (callback != null) {
            _state.value = VoiceInputState.Processing
            callback(finalText)
        } else {
            showPreviewFallback(finalText)
        }
    } else {
        _state.value = VoiceInputState.Idle
    }
}
```

- [ ] **Step 4: Update `buildSpeechIntent` — remove continuous parameter, set max silence**

```kotlin
private fun buildSpeechIntent(): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Set silence thresholds just under idle timeout so Android never
        // triggers end-of-speech before our app-level idle check.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 59_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 59_000L)
    }
}
```

- [ ] **Step 5: Remove `continuousMode` field and all references**

Delete the `continuousMode` field (line 50). Update `restartRecognizer()` to always pass no parameter to `buildSpeechIntent()`. Update `stopListening()`:

```kotlin
fun stopListening() {
    userRequestedStop = true
    unmuteBeep()
    mainHandler.removeCallbacks(idleCheckRunnable)
    speechRecognizer?.stopListening()
}
```

Update `cancel()` to also remove idle check:

```kotlin
fun cancel() {
    mainHandler.removeCallbacks(timeoutRunnable)
    mainHandler.removeCallbacks(idleCheckRunnable)
    unmuteBeep()
    speechRecognizer?.cancel()
    speechRecognizer?.destroy()
    speechRecognizer = null
    errorCallback = null
    activeListener = null
    _state.value = VoiceInputState.Idle
}
```

Update `destroy()`:

```kotlin
fun destroy() {
    mainHandler.removeCallbacks(timeoutRunnable)
    mainHandler.removeCallbacks(idleCheckRunnable)
    speechRecognizer?.destroy()
    speechRecognizer = null
    errorCallback = null
    activeListener = null
}
```

- [ ] **Step 6: Build and verify compilation**

Run: `cd /c/Users/glm_6/AndroidStudioProjects/ToDoWallApp/.claude/worktrees/suspicious-wilbur && ./gradlew assembleDebug 2>&1 | tail -20`

Expected: Compilation errors in files that call `startListening(continuous = ...)` — this is correct; Tasks 2-5 will fix those call sites.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/voice/VoiceCaptureManager.kt
git commit -m "refactor: VoiceCaptureManager always-continuous with 60s idle timeout

Remove continuous parameter, set silence thresholds to 59s,
add app-level idle timeout with RMS monitoring."
```

---

### Task 2: Add DAY_PLAN Intent to VoiceIntent Enum and Gemini Prompt

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt` (lines 31, 396-420, 1189-1204)

- [ ] **Step 1: Add DAY_PLAN to VoiceIntent enum**

At line 31:

```kotlin
enum class VoiceIntent { ADD, COMPLETE, RESCHEDULE, DELETE, QUERY, AMEND, DAY_PLAN }
```

- [ ] **Step 2: Update `parseVoiceInputV2` to accept optional day planning context**

```kotlin
suspend fun parseVoiceInputV2(
    apiKey: String,
    rawText: String,
    existingLists: List<ExistingListRef>,
    existingTasks: List<ExistingTaskRef> = emptyList(),
    todayDate: LocalDate = LocalDate.now(),
    currentTime: LocalTime = LocalTime.now(),
    // New: day planning context for unified routing
    calendarEvents: List<String> = emptyList(),
    weatherSummary: String? = null,
    wakeHour: Int = 7,
    sleepHour: Int = 23
): Result<ParsedVoiceResponse> = withContext(Dispatchers.IO) {
    runCatching {
        val prompt = buildVoiceGeminiPrompt(
            rawText = rawText,
            existingLists = existingLists,
            existingTasks = existingTasks,
            todayDate = todayDate,
            currentTime = currentTime,
            calendarEvents = calendarEvents,
            weatherSummary = weatherSummary,
            wakeHour = wakeHour,
            sleepHour = sleepHour
        )
        val requestBody = buildRequestBody(prompt)
        val response = apiClient.generateContent(apiKey = apiKey, requestBody = requestBody)
        val responseText = extractTextFromGeminiResponse(response)
        parseVoiceResponseJson(responseText, rawText, existingLists, existingTasks)
    }
}
```

- [ ] **Step 3: Update `buildVoiceGeminiPrompt` to include DAY_PLAN in intent instructions**

Find the prompt string in `buildVoiceGeminiPrompt` where it lists valid intents. Add `DAY_PLAN` to the list and add an instruction block:

```
- DAY_PLAN: The user wants to plan, organize, or schedule their day/morning/afternoon/evening. They are describing what they need to do and want you to help arrange it. Examples: "what should I do today", "I need to exercise, have a meeting at 2, and do groceries", "organize my afternoon". If calendar events and tasks are provided, mention them in context.
```

Also add the calendar/weather context to the prompt when provided:

```kotlin
// In buildVoiceGeminiPrompt, add after existing context blocks:
if (calendarEvents.isNotEmpty()) {
    append("\n\nToday's calendar events:\n")
    calendarEvents.forEach { append("- $it\n") }
}
if (weatherSummary != null) {
    append("\nWeather: $weatherSummary")
}
if (calendarEvents.isNotEmpty() || weatherSummary != null) {
    append("\nUser wake hour: $wakeHour, sleep hour: $sleepHour")
}
```

- [ ] **Step 4: Update `parseVoiceResponseJson` — handle DAY_PLAN intent**

The existing parser at line 1198-1202 already uses `VoiceIntent.valueOf(intentStr.uppercase())` with a catch fallback to ADD. Since we added `DAY_PLAN` to the enum, it will parse automatically. No code change needed — just verify.

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug 2>&1 | tail -20`

Expected: May still have errors from Task 1 call sites. The enum addition itself should compile cleanly.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: add DAY_PLAN intent to VoiceIntent enum and Gemini prompt

Unified voice classification now includes day planning as an intent.
Calendar events and weather context included in the prompt."
```

---

### Task 3: Delete VoiceIntentRouter + Update VoiceParsingCoordinator

**Files:**
- Delete: `app/src/main/java/com/example/todowallapp/capture/router/VoiceIntentRouter.kt`
- Modify: `app/src/main/java/com/example/todowallapp/capture/VoiceParsingCoordinator.kt`

- [ ] **Step 1: Delete VoiceIntentRouter.kt**

```bash
rm app/src/main/java/com/example/todowallapp/capture/router/VoiceIntentRouter.kt
```

- [ ] **Step 2: Update VoiceParsingCoordinator to accept day planning context providers**

Add new parameters to `configure()` and pass them through:

```kotlin
class VoiceParsingCoordinator(
    private val voiceCaptureManager: VoiceCaptureManager,
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore
) {
    var lastResponse: ParsedVoiceResponse? = null
        private set

    private var parseJob: Job? = null
    private var rescheduleRetryContext: RescheduleRetryContext? = null

    // Day planning context providers (set by ViewModel)
    var calendarEventsProvider: (suspend () -> List<String>)? = null
    var weatherSummaryProvider: (suspend () -> String?)? = null
    var wakeHour: Int = 7
    var sleepHour: Int = 23

    /** Called by ViewModel to set day planning context for unified routing. */
    fun configureDayPlanningContext(
        calendarEventsProvider: suspend () -> List<String>,
        weatherSummaryProvider: (suspend () -> String?)? = null,
        wakeHour: Int = 7,
        sleepHour: Int = 23
    ) {
        this.calendarEventsProvider = calendarEventsProvider
        this.weatherSummaryProvider = weatherSummaryProvider
        this.wakeHour = wakeHour
        this.sleepHour = sleepHour
    }

    fun armRescheduleRetry(context: RescheduleRetryContext) {
        rescheduleRetryContext = context
    }

    fun configure(
        scope: CoroutineScope,
        listProvider: () -> List<ExistingListRef>,
        taskProvider: () -> List<ExistingTaskRef> = { emptyList() },
        listIdValidator: (String?) -> String?
    ) {
        voiceCaptureManager.rawResultCallback = { rawText ->
            handleRawTranscription(
                rawText = rawText,
                scope = scope,
                listProvider = listProvider,
                taskProvider = taskProvider,
                listIdValidator = listIdValidator
            )
        }
    }

    private fun handleRawTranscription(
        rawText: String,
        scope: CoroutineScope,
        listProvider: () -> List<ExistingListRef>,
        taskProvider: () -> List<ExistingTaskRef>,
        listIdValidator: (String?) -> String?
    ) {
        val normalizedText = rawText.trim()
        if (normalizedText.isEmpty()) {
            voiceCaptureManager.setError("Didn't catch that")
            return
        }

        parseJob?.cancel()
        parseJob = scope.launch {
            val existingLists = listProvider()
            val existingTasks = taskProvider()

            val apiKey = geminiKeyStore.getApiKey()
            if (apiKey == null) {
                clearMetadata()
                voiceCaptureManager.showPreviewFallback(
                    normalizedText,
                    clarification = "No Gemini API key — add one in Settings for smart parsing"
                )
                return@launch
            }

            val retryCtx = rescheduleRetryContext
            val parseResult = if (retryCtx != null) {
                rescheduleRetryContext = null
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
                    response.copy(
                        tasks = response.tasks.map { task ->
                            task.copy(parentTaskId = retryCtx.targetTaskId)
                        }
                    )
                }
            } else {
                // Unified call with day planning context
                val calEvents = calendarEventsProvider?.invoke() ?: emptyList()
                val weather = weatherSummaryProvider?.invoke()
                geminiCaptureRepository.parseVoiceInputV2(
                    apiKey = apiKey,
                    rawText = normalizedText,
                    existingLists = existingLists,
                    existingTasks = existingTasks,
                    todayDate = LocalDate.now(),
                    currentTime = LocalTime.now(),
                    calendarEvents = calEvents,
                    weatherSummary = weather,
                    wakeHour = wakeHour,
                    sleepHour = sleepHour
                )
            }

            parseResult.fold(
                onSuccess = { response ->
                    val validatedResponse = response.copy(
                        tasks = response.tasks.map { task ->
                            task.copy(targetListId = listIdValidator(task.targetListId))
                        }
                    )
                    lastResponse = validatedResponse
                    voiceCaptureManager.showPreview(validatedResponse)
                },
                onFailure = { _ ->
                    clearMetadata()
                    voiceCaptureManager.showPreviewFallback(
                        normalizedText,
                        clarification = "AI parsing failed — task will be added as-is"
                    )
                }
            )
        }
    }

    fun clearMetadata() {
        lastResponse = null
        rescheduleRetryContext = null
    }

    fun cancelParse() {
        parseJob?.cancel()
        rescheduleRetryContext = null
    }

    fun destroy() {
        cancelParse()
        clearMetadata()
    }
}
```

- [ ] **Step 3: Commit**

```bash
git rm app/src/main/java/com/example/todowallapp/capture/router/VoiceIntentRouter.kt
git add app/src/main/java/com/example/todowallapp/capture/VoiceParsingCoordinator.kt
git commit -m "refactor: delete VoiceIntentRouter, add day planning context to VoiceParsingCoordinator

All voice routing now happens via Gemini classification.
VoiceParsingCoordinator passes calendar/weather context to the unified prompt."
```

---

### Task 4: Simplify DayOrganizerCoordinator — Remove Voice Ownership

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt`

- [ ] **Step 1: Remove Listening and Adjusting states**

Replace the state machine (lines 39-52):

```kotlin
sealed class DayOrganizerState {
    object Idle : DayOrganizerState()
    data class Processing(val isAdjustment: Boolean = false) : DayOrganizerState()
    data class PlanReady(
        val plan: DayPlan,
        val focusedIndex: Int = 0,
        val pendingRemoveIndex: Int? = null
    ) : DayOrganizerState()
    data class Confirming(val current: Int = 0, val total: Int = 0) : DayOrganizerState()
    data class PartialSuccess(val createdCount: Int, val failedBlocks: List<PlanBlock>) : DayOrganizerState()
    data class Error(val message: String, val canRetry: Boolean) : DayOrganizerState()
}
```

- [ ] **Step 2: Remove VoiceCaptureManager dependency from constructor**

```kotlin
class DayOrganizerCoordinator(
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository
) {
```

- [ ] **Step 3: Replace `startListening()` with `generatePlan()`**

Remove `startListening()` (lines 114-147). Replace with:

```kotlin
fun generatePlan(
    transcription: String,
    scope: CoroutineScope,
    listProvider: () -> List<ExistingListRef>,
    taskProvider: () -> List<ExistingTaskRef>,
    eventsProvider: suspend () -> List<String>,
    selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
    weatherProvider: (suspend () -> String?)? = null,
    wakeHour: Int = 7,
    sleepHour: Int = 23,
    focusedListTitle: String? = null,
    groundingContextProvider: (suspend () -> String?)? = null,
    energyProfile: EnergyProfile = EnergyProfile.BALANCED
) {
    this.scope = scope
    this.listProvider = listProvider
    this.taskProvider = taskProvider
    this.eventsProvider = eventsProvider
    this.selectedCalendarId = selectedCalendarId
    this.weatherProvider = weatherProvider
    this.groundingContextProvider = groundingContextProvider
    this.wakeHour = wakeHour
    this.sleepHour = sleepHour
    this.focusedListTitle = focusedListTitle
    this.energyProfile = energyProfile

    handleTranscription(transcription)
}
```

- [ ] **Step 4: Replace `startAdjustment()` with `adjustPlan()`**

Remove `startAdjustment()` (lines 188-207) and `stopAdjustmentListening()`. Replace with:

```kotlin
fun adjustPlan(adjustmentText: String) {
    val currentState = _state.value
    if (currentState !is DayOrganizerState.PlanReady) return
    handleAdjustmentTranscription(adjustmentText, currentState.plan)
}
```

- [ ] **Step 5: Remove `stopListening()`, `updateAmplitude()`, and voice-related cleanup**

Delete `stopListening()` (line 184-186). Update `updateAmplitude()`:

```kotlin
fun updateAmplitude(level: Float) {
    // No-op — voice capture is no longer owned by this coordinator
}
```

Actually, just delete `updateAmplitude()` entirely — callers will be updated.

Update `cancel()` — remove voiceCaptureManager references:

```kotlin
fun cancel() {
    parseJob?.cancel()
    parseJob = null
    currentPlan = null
    lastTranscription = null
    conversationTurns.clear()
    planSystemInstruction = null
    planGenerationConfig = null
    adjustmentAttempts = 0
    lastCreatedEventIds.clear()
    scope = null
    listProvider = null
    taskProvider = null
    eventsProvider = null
    weatherProvider = null
    groundingContextProvider = null
    focusedListTitle = null
    energyProfile = EnergyProfile.BALANCED
    wakeHour = 7
    sleepHour = 23
    _state.value = DayOrganizerState.Idle
}
```

- [ ] **Step 6: Remove `startWithTranscription()` — replaced by `generatePlan()`**

Delete lines 154-182. The new `generatePlan()` serves the same purpose.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/DayOrganizerCoordinator.kt
git commit -m "refactor: DayOrganizerCoordinator loses voice ownership

Remove Listening/Adjusting states. New entry points:
generatePlan(transcription) and adjustPlan(adjustmentText).
No more direct VoiceCaptureManager dependency."
```

---

### Task 5: TaskWallViewModel — Unified Voice Pipeline

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

- [ ] **Step 1: Remove VoiceIntentRouter import and unifiedVoiceRouting flag**

Delete import at line ~17:
```kotlin
// DELETE: import com.example.todowallapp.capture.router.VoiceIntentRouter
```

Delete `unifiedVoiceRouting` field (around line 234-237).

- [ ] **Step 2: Update DayOrganizerCoordinator construction — remove voiceCaptureManager**

```kotlin
private val dayOrganizerCoordinator by lazy {
    DayOrganizerCoordinator(
        geminiCaptureRepository = geminiCaptureRepository,
        geminiKeyStore = geminiKeyStore,
        calendarRepository = calendarRepository,
        tasksRepository = tasksRepository
    )
}
```

- [ ] **Step 3: Remove routing wrapper from rawResultCallback**

In the init block (around lines 294-311), remove the VoiceIntentRouter wrapping. The `voiceParsingCoordinator.configure()` call stays, but remove the callback wrapper:

```kotlin
// Just configure normally — no routing wrapper needed.
// Gemini will classify intent including DAY_PLAN.
voiceParsingCoordinator.configure(
    scope = viewModelScope,
    listProvider = listProvider,
    taskProvider = taskProvider,
    listIdValidator = ::resolveKnownTaskListId
)

// Configure day planning context for unified routing
voiceParsingCoordinator.configureDayPlanningContext(
    calendarEventsProvider = {
        val today = java.time.LocalDate.now()
        val events = calendarRepository.getEventsForDateRange(
            today, today, _uiState.value.selectedCalendarId
        ).getOrElse { emptyMap() }
        events[today]?.map { event ->
            if (event.isAllDay) "All day ${event.title} (all-day)"
            else {
                val start = event.startDateTime?.toLocalTime()
                val end = event.endDateTime?.toLocalTime()
                val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val timeRange = "${start?.format(fmt) ?: "?"}-${end?.format(fmt) ?: "?"}"
                "$timeRange ${event.title}"
            }
        } ?: emptyList()
    },
    weatherSummaryProvider = {
        _weatherForecast.value[java.time.LocalDate.now()]?.name
    },
    wakeHour = _sleepEndHour.value,
    sleepHour = _sleepStartHour.value
)
```

- [ ] **Step 4: Simplify startUnifiedVoiceCapture**

```kotlin
fun startUnifiedVoiceCapture() {
    if (!isOnline.value) {
        setTransientMessage("No internet connection — voice input requires network access.")
        return
    }
    voiceParsingCoordinator.cancelParse()
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.startListening()
}
```

No more `unifiedVoiceRouting = true` — it's always unified.

- [ ] **Step 5: Add DAY_PLAN intent handler in confirmVoiceInput**

In the `when (response.intent)` block in `confirmVoiceInput()`, add:

```kotlin
VoiceIntent.DAY_PLAN -> {
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.resetToIdle()
    // Route to Day Organizer with the raw transcript
    if (!_uiState.value.hasCalendarScope) {
        setTransientMessage("Day planning requires calendar access. Grant permission in Settings.")
        return@launch
    }
    dayOrganizerCoordinator.generatePlan(
        transcription = response.rawTranscript,
        scope = viewModelScope,
        listProvider = {
            _uiState.value.taskLists.map { list ->
                ExistingListRef(id = list.id, title = list.title)
            }
        },
        taskProvider = {
            _uiState.value.allTaskLists.flatMap { list ->
                list.tasks.filter { it.parentId == null && !it.isCompleted }.map { task ->
                    ExistingTaskRef(
                        id = task.id, title = task.title, listId = list.taskList.id,
                        listTitle = list.taskList.title, dueDate = task.dueDate,
                        priority = task.priority, preferredTime = task.preferredTime,
                        recurrenceInfo = task.recurrenceRule?.toHumanReadable()
                    )
                }
            }.take(40)
        },
        eventsProvider = {
            val today = java.time.LocalDate.now()
            val events = calendarRepository.getEventsForDateRange(
                today, today, _uiState.value.selectedCalendarId
            ).getOrElse { emptyMap() }
            events[today]?.map { event ->
                if (event.isAllDay) "All day ${event.title} (all-day)"
                else {
                    val start = event.startDateTime?.toLocalTime()
                    val end = event.endDateTime?.toLocalTime()
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    "${ start?.format(fmt) ?: "?"}-${end?.format(fmt) ?: "?"} ${event.title}"
                }
            } ?: emptyList()
        },
        selectedCalendarId = _uiState.value.selectedCalendarId,
        weatherProvider = { _weatherForecast.value[java.time.LocalDate.now()]?.name },
        wakeHour = _sleepEndHour.value,
        sleepHour = _sleepStartHour.value,
        focusedListTitle = _uiState.value.selectedTaskListTitle.takeIf {
            _uiState.value.selectedTaskListId != null
        },
        energyProfile = _energyProfile.value
    )
}
```

- [ ] **Step 6: Update routeToDayOrganizer to use generatePlan (for direct invocations)**

Keep `routeToDayOrganizer()` but have it show a message that day planning now works via voice:

Actually, we should keep this method but change it to show that the user should use voice. OR we can remove it since the unified flow handles everything. Let's remove `routeToDayOrganizer()` entirely — it's no longer called since VoiceIntentRouter is gone.

- [ ] **Step 7: Wire Day Organizer adjustment through unified voice**

Add a method for triggering adjustment voice capture:

```kotlin
fun startDayOrganizerAdjustment() {
    // Start a new voice capture session — when the transcript comes back,
    // it will be routed through Gemini. But for adjustments we need to
    // intercept and send directly to the day organizer.
    voiceParsingCoordinator.cancelParse()
    voiceParsingCoordinator.clearMetadata()

    // Temporarily replace the callback to route to adjustment
    val originalCallback = voiceCaptureManager.rawResultCallback
    voiceCaptureManager.rawResultCallback = { rawText ->
        voiceCaptureManager.rawResultCallback = originalCallback
        voiceCaptureManager.resetToIdle()
        dayOrganizerCoordinator.adjustPlan(rawText)
    }
    voiceCaptureManager.startListening()
}
```

- [ ] **Step 8: Build and verify**

Run: `./gradlew assembleDebug 2>&1 | tail -30`

Expected: May still have errors in UI files referencing removed states. Fix will come in Tasks 6-7.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat: unified voice pipeline in TaskWallViewModel

Remove VoiceIntentRouter usage. All voice goes through Gemini classification.
DAY_PLAN intent routes to DayOrganizerCoordinator.generatePlan().
Adjustment flow uses temporary callback swap."
```

---

### Task 6: TaskWallScreen — UI Updates

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 1: Update listening overlay — new hint text, remove auto-fade**

Replace lines 1438-1462:

```kotlin
is VoiceInputState.Listening -> {
    androidx.compose.animation.AnimatedVisibility(
        visible = voiceState is VoiceInputState.Listening,
        enter = fadeIn(tween(WallAnimations.SHORT)),
        exit = fadeOut(tween(WallAnimations.SHORT))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WaveformVisualizer(
                amplitudeLevel = state.amplitudeLevel,
                isActive = true,
                modifier = Modifier.size(200.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Speak naturally \u2014 click to finish",
                color = colors.textMuted.copy(alpha = 0.45f),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
```

Key changes: Hint text changed from "add a task, or say 'plan my day' to schedule" to "Speak naturally — click to finish". Removed the 5-second auto-fade `LaunchedEffect`.

- [ ] **Step 2: Add DAY_PLAN to preview intent label**

In the preview section (around line 1481-1488), add DAY_PLAN:

```kotlin
val intentLabel = when (response.intent) {
    VoiceIntent.ADD -> if (response.tasks.size > 1) "Draft Tasks" else "Draft Task"
    VoiceIntent.COMPLETE -> "Complete Task"
    VoiceIntent.DELETE -> "Delete Task"
    VoiceIntent.RESCHEDULE -> "Reschedule Task"
    VoiceIntent.QUERY -> "Tasks Found"
    VoiceIntent.AMEND -> "Amended Task"
    VoiceIntent.DAY_PLAN -> "Plan Your Day"
}
```

Also update the confirm label:

```kotlin
val confirmLabel = when (response.intent) {
    VoiceIntent.ADD -> if (response.tasks.size > 1) "Add All" else "Confirm"
    VoiceIntent.COMPLETE -> "Complete"
    VoiceIntent.DELETE -> "Delete"
    VoiceIntent.RESCHEDULE -> "Reschedule"
    VoiceIntent.QUERY -> "Dismiss"
    VoiceIntent.AMEND -> "Confirm"
    VoiceIntent.DAY_PLAN -> "Start Planning"
}
```

- [ ] **Step 3: Update DayOrganizerOverlay references — remove Listening/Adjusting handling**

Find all references to `DayOrganizerState.Listening` and `DayOrganizerState.Adjusting` in TaskWallScreen.kt and remove/update them. In key handling (around line 870), remove the listening/adjusting branches. Replace any amplitude forwarding.

- [ ] **Step 4: Wire `onStartDayOrganizerAdjustment` parameter**

Add to the composable parameters and wire to the ViewModel's `startDayOrganizerAdjustment()`.

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug 2>&1 | tail -30`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "feat: TaskWallScreen voice UI updates

New hint text, no auto-fade, DAY_PLAN intent label,
remove Listening/Adjusting state handling for Day Organizer."
```

---

### Task 7: DayOrganizerOverlay — Remove Listening/Adjusting UI

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/DayOrganizerOverlay.kt`

- [ ] **Step 1: Remove ListeningContent rendering**

Delete `ListeningContent` composable (lines 157-226) and its invocation in the main composable.

- [ ] **Step 2: Remove Adjusting state rendering**

Remove any `is DayOrganizerState.Adjusting ->` branches in the main composable.

- [ ] **Step 3: Update PlanPreviewContent — "Adjust" action triggers unified voice**

Replace the "Adjust" button's `onAdjust` callback to call the new unified voice adjustment:

```kotlin
// In PlanPreviewContent, the "Adjust" action pill should call:
onStartAdjustment: () -> Unit  // This triggers startDayOrganizerAdjustment() in ViewModel
```

- [ ] **Step 4: Clean up — remove amplitude parameters and voice-related imports**

Remove any references to `amplitudeLevel` in the overlay since the coordinator no longer provides it.

- [ ] **Step 5: Build and verify full compilation**

Run: `./gradlew assembleDebug 2>&1 | tail -30`

Expected: PASS — all references resolved.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/DayOrganizerOverlay.kt
git commit -m "refactor: DayOrganizerOverlay removes Listening/Adjusting UI

Adjustment now triggers a unified voice capture session.
Overlay only renders Processing, PlanReady, Confirming, PartialSuccess, Error."
```

---

### Task 8: PhoneCaptureViewModel + PhoneVoiceBottomSheet — Full Parity

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/PhoneVoiceBottomSheet.kt`

- [ ] **Step 1: PhoneCaptureViewModel — use unified parsing with day planning context**

Update the VoiceParsingCoordinator configuration in the init block to include day planning context:

```kotlin
voiceParsingCoordinator.configureDayPlanningContext(
    calendarEventsProvider = { emptyList() }, // Phone doesn't have calendar yet — future work
    weatherSummaryProvider = null,
    wakeHour = 7,
    sleepHour = 23
)
```

- [ ] **Step 2: PhoneCaptureViewModel — remove `continuous = false` from startVoiceInput**

`startListening()` no longer takes a parameter, so just call it:

```kotlin
fun startVoiceInput() {
    voiceCaptureManager.startListening()
}

fun showVoiceSheet() {
    _uiState.update { it.copy(showVoiceSheet = true) }
    voiceCaptureManager.startListening()
}
```

- [ ] **Step 3: PhoneCaptureViewModel — add DAY_PLAN and RESCHEDULE intent handlers**

In the `confirmVoiceTask()` method, add handlers for the new intents:

```kotlin
VoiceIntent.RESCHEDULE -> {
    val task = response.tasks.firstOrNull() ?: return@launch
    val targetId = task.parentTaskId ?: return@launch
    val targetListId = findTaskListId(targetId) ?: return@launch
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.resetToIdle()
    tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
        onSuccess = {
            _uiState.update { it.copy(showVoiceSheet = false) }
            refreshTaskLists()
        },
        onFailure = {
            voiceCaptureManager.setError("Failed to reschedule task")
        }
    )
}

VoiceIntent.QUERY -> {
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.resetToIdle()
    _uiState.update { it.copy(showVoiceSheet = false) }
}

VoiceIntent.DAY_PLAN -> {
    voiceParsingCoordinator.clearMetadata()
    voiceCaptureManager.resetToIdle()
    _uiState.update { it.copy(
        showVoiceSheet = false,
        infoMessage = "Day planning coming soon to phone mode"
    ) }
}
```

- [ ] **Step 4: PhoneVoiceBottomSheet — update hint text**

Change the idle instruction text:

```kotlin
// Replace "Speak a task title naturally" with:
Text(
    "Speak naturally \u2014 tap Stop when done",
    style = MaterialTheme.typography.bodyMedium,
    color = colors.textSecondary
)
```

- [ ] **Step 5: PhoneVoiceBottomSheet — add DAY_PLAN to preview rendering**

Update any `when` blocks that switch on `response.intent` to handle `VoiceIntent.DAY_PLAN`.

- [ ] **Step 6: Build and verify**

Run: `./gradlew assembleDebug 2>&1 | tail -30`

Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/PhoneCaptureViewModel.kt \
      app/src/main/java/com/example/todowallapp/ui/components/PhoneVoiceBottomSheet.kt
git commit -m "feat: phone voice parity — continuous mode, all intents, new hint text

Phone mode now uses continuous capture with tap-to-stop.
Adds RESCHEDULE, QUERY, DAY_PLAN intent handling.
Consistent hint text across wall and phone."
```

---

### Task 9: Full Build Verification + Integration Smoke Test

**Files:** None (verification only)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean assembleDebug 2>&1 | tail -30`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no remaining references to deleted code**

```bash
grep -r "VoiceIntentRouter" app/src/main/java/ --include="*.kt"
grep -r "continuousMode" app/src/main/java/ --include="*.kt"
grep -r "startListening(continuous" app/src/main/java/ --include="*.kt"
grep -r "DayOrganizerState.Listening" app/src/main/java/ --include="*.kt"
grep -r "DayOrganizerState.Adjusting" app/src/main/java/ --include="*.kt"
```

Expected: No results for any of these.

- [ ] **Step 3: Verify VoiceIntentRouter.kt is deleted**

```bash
ls app/src/main/java/com/example/todowallapp/capture/router/
```

Expected: Directory empty or doesn't exist.

- [ ] **Step 4: Commit final verification**

No code changes — just verify everything compiles.

---

### Task Dependency Graph

```
Task 1 (VoiceCaptureManager) ─────────────────────────┐
Task 2 (VoiceIntent enum + Gemini prompt) ─────────────┤
Task 3 (Delete Router + Update Coordinator) ───────────┤
Task 4 (DayOrganizerCoordinator simplify) ─────────────┤
                                                       ▼
Task 5 (TaskWallViewModel unified pipeline) ───────────┤
                                                       ▼
Task 6 (TaskWallScreen UI) ────────────────────────────┤
Task 7 (DayOrganizerOverlay UI) ───────────────────────┤
Task 8 (Phone parity) ────────────────────────────────┤
                                                       ▼
Task 9 (Full build verification) ──────────────────────┘
```

**Parallelizable:** Tasks 1, 2, 3, 4 can be done in parallel (they touch independent files).
Tasks 6, 7, 8 can be done in parallel (independent UI files, all depend on Task 5).
Task 5 depends on Tasks 1-4. Task 9 depends on all.
