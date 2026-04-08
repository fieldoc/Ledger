package com.example.todowallapp.capture

import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.GeminiPrompt
import com.google.gson.JsonObject
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.voice.VoiceCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

// ---------------------------------------------------------------------------
// State machine
// ---------------------------------------------------------------------------

sealed class DayOrganizerState {
    object Idle : DayOrganizerState()
    data class Listening(val amplitudeLevel: Float = 0f) : DayOrganizerState()
    data class Processing(val isAdjustment: Boolean = false) : DayOrganizerState()
    data class PlanReady(val plan: DayPlan, val focusedAction: Int = 0) : DayOrganizerState()
    data class Adjusting(val previousPlan: DayPlan, val amplitudeLevel: Float = 0f) : DayOrganizerState()
    object Confirming : DayOrganizerState()
    data class Error(val message: String, val canRetry: Boolean) : DayOrganizerState()
}

// ---------------------------------------------------------------------------
// Coordinator
// ---------------------------------------------------------------------------

class DayOrganizerCoordinator(
    private val voiceCaptureManager: VoiceCaptureManager,
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository
) {
    private val _state = MutableStateFlow<DayOrganizerState>(DayOrganizerState.Idle)
    val state: StateFlow<DayOrganizerState> = _state.asStateFlow()

    var currentPlan: DayPlan? = null
        private set
    var lastTranscription: String? = null
        private set

    /** Multi-turn conversation for adjustments: list of (role, text) pairs. */
    private var conversationTurns: MutableList<Pair<String, String>> = mutableListOf()

    /** Cached system instruction and generation config from initial plan request. */
    private var planSystemInstruction: String? = null
    private var planGenerationConfig: JsonObject? = null

    private var parseJob: Job? = null

    // Stored when startListening is called
    private var scope: CoroutineScope? = null
    private var listProvider: (() -> List<ExistingListRef>)? = null
    private var taskProvider: (() -> List<ExistingTaskRef>)? = null
    private var eventsProvider: (suspend () -> List<String>)? = null
    private var weatherProvider: (suspend () -> String?)? = null
    private var groundingContextProvider: (suspend () -> String?)? = null
    private var selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID
    private var wakeHour: Int = 7
    private var sleepHour: Int = 23
    private var focusedListTitle: String? = null

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    fun startListening(
        scope: CoroutineScope,
        listProvider: () -> List<ExistingListRef>,
        taskProvider: () -> List<ExistingTaskRef>,
        eventsProvider: suspend () -> List<String>,
        selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
        weatherProvider: (suspend () -> String?)? = null,
        wakeHour: Int = 7,
        sleepHour: Int = 23,
        focusedListTitle: String? = null,
        groundingContextProvider: (suspend () -> String?)? = null
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

        voiceCaptureManager.rawResultCallback = { rawText ->
            handleTranscription(rawText)
        }
        voiceCaptureManager.errorCallback = { message ->
            _state.value = DayOrganizerState.Error(message, canRetry = true)
        }

        _state.value = DayOrganizerState.Listening()
        voiceCaptureManager.startListening(continuous = true)
    }

    /**
     * Start the day planning flow with an already-captured transcription, skipping voice capture.
     * Used by the unified voice flow when VoiceCaptureManager has already captured speech and
     * classified it as day planning intent.
     */
    fun startWithTranscription(
        transcription: String,
        scope: CoroutineScope,
        listProvider: () -> List<ExistingListRef>,
        taskProvider: () -> List<ExistingTaskRef>,
        eventsProvider: suspend () -> List<String>,
        selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
        weatherProvider: (suspend () -> String?)? = null,
        wakeHour: Int = 7,
        sleepHour: Int = 23,
        focusedListTitle: String? = null
    ) {
        this.scope = scope
        this.listProvider = listProvider
        this.taskProvider = taskProvider
        this.eventsProvider = eventsProvider
        this.selectedCalendarId = selectedCalendarId
        this.weatherProvider = weatherProvider
        this.wakeHour = wakeHour
        this.sleepHour = sleepHour
        this.focusedListTitle = focusedListTitle

        lastTranscription = transcription
        _state.value = DayOrganizerState.Processing()

        scope.launch {
            handleTranscription(transcription)
        }
    }

    fun stopListening() {
        voiceCaptureManager.stopListening()
    }

    fun startAdjustment() {
        val currentState = _state.value
        if (currentState !is DayOrganizerState.PlanReady) return
        val plan = currentState.plan

        voiceCaptureManager.rawResultCallback = { rawText ->
            handleAdjustmentTranscription(rawText, plan)
        }
        voiceCaptureManager.errorCallback = { message ->
            _state.value = DayOrganizerState.Error(message, canRetry = true)
        }

        _state.value = DayOrganizerState.Adjusting(previousPlan = plan)
        // VoiceCaptureManager._state may be stuck at Processing from the previous recognition
        // (onResults sets it to Processing then fires the callback, but never resets it).
        // That stuck state causes startListening()'s guard to return early without activating
        // the mic. Reset to Idle first so the guard passes.
        voiceCaptureManager.resetToIdle()
        voiceCaptureManager.startListening(continuous = true)
    }

    fun stopAdjustmentListening() {
        voiceCaptureManager.stopListening()
    }

    suspend fun acceptPlan(): Result<Int> {
        val plan = currentPlan ?: return Result.failure(IllegalStateException("No plan available"))
        _state.value = DayOrganizerState.Confirming

        var createdCount = 0
        val errors = mutableListOf<Throwable>()

        for (block in plan.blocks) {
            if (block.isExistingEvent) continue

            val result = calendarRepository.createEvent(
                title = block.title,
                startDateTime = block.startTime,
                endDateTime = block.endTime,
                calendarId = selectedCalendarId,
                description = buildEventDescription(block)
            )

            result.fold(
                onSuccess = { createdCount++ },
                onFailure = { errors.add(it) }
            )
        }

        return if (errors.isEmpty()) {
            _state.value = DayOrganizerState.Idle
            Result.success(createdCount)
        } else {
            _state.value = DayOrganizerState.Error(
                message = "Some events failed to create (${errors.size} error(s)).",
                canRetry = false
            )
            Result.failure(errors.first())
        }
    }

    fun cancel() {
        parseJob?.cancel()
        parseJob = null
        voiceCaptureManager.rawResultCallback = null
        voiceCaptureManager.errorCallback = null
        voiceCaptureManager.cancel()
        currentPlan = null
        lastTranscription = null
        conversationTurns.clear()
        planSystemInstruction = null
        planGenerationConfig = null
        // Reset all provider/config state set by startListening()
        scope = null
        listProvider = null
        taskProvider = null
        eventsProvider = null
        weatherProvider = null
        groundingContextProvider = null
        focusedListTitle = null
        wakeHour = 7
        sleepHour = 23
        _state.value = DayOrganizerState.Idle
    }

    fun updateAmplitude(level: Float) {
        when (val s = _state.value) {
            is DayOrganizerState.Listening -> _state.value = s.copy(amplitudeLevel = level)
            is DayOrganizerState.Adjusting -> _state.value = s.copy(amplitudeLevel = level)
            else -> { /* no-op */ }
        }
    }

    fun setFocusedAction(index: Int) {
        val s = _state.value
        if (s is DayOrganizerState.PlanReady) {
            _state.value = s.copy(focusedAction = index)
        }
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

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
                val apiKey = geminiKeyStore.getApiKey()
                if (apiKey == null) {
                    _state.value = DayOrganizerState.Error("Gemini API key not configured.", canRetry = false)
                    return@launch
                }

                val events = eventsProvider?.invoke() ?: emptyList()
                val tasks = taskProvider?.invoke() ?: emptyList()
                val weather = weatherProvider?.invoke()
                val grounding = groundingContextProvider?.invoke()
                val targetDate = LocalDate.now()

                val prompt: GeminiPrompt = geminiCaptureRepository.buildDayPlanGeminiPrompt(
                    rawTranscription = trimmed,
                    existingEvents = events,
                    existingTasks = tasks,
                    targetDate = targetDate,
                    currentTime = LocalTime.now(),
                    weatherForecast = weather,
                    wakeHour = wakeHour,
                    sleepHour = sleepHour,
                    focusedListTitle = focusedListTitle,
                    groundingContext = grounding
                )

                // Cache system instruction & generation config for multi-turn adjustments
                planSystemInstruction = prompt.systemInstruction
                planGenerationConfig = prompt.generationConfig

                val responseJson = withContext(Dispatchers.IO) {
                    geminiCaptureRepository.callGeminiForDayPlan(apiKey, prompt)
                }

                // Initialize conversation history for future adjustments
                conversationTurns.clear()
                conversationTurns.add("user" to prompt.userContent)
                conversationTurns.add("model" to responseJson)

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
            _state.value = DayOrganizerState.PlanReady(plan = previousPlan)
            return
        }

        _state.value = DayOrganizerState.Processing(isAdjustment = true)

        // Append the user adjustment turn optimistically
        val userTurn = "user" to "Adjust the plan: $trimmed"
        conversationTurns.add(userTurn)

        parseJob = scope?.launch {
            try {
                val apiKey = geminiKeyStore.getApiKey()
                if (apiKey == null) {
                    conversationTurns.removeLastOrNull()
                    _state.value = DayOrganizerState.Error("Gemini API key not configured.", canRetry = false)
                    return@launch
                }

                val sysInstruction = planSystemInstruction
                    ?: error("Missing system instruction from initial plan request.")

                val responseJson = withContext(Dispatchers.IO) {
                    geminiCaptureRepository.callGeminiForDayPlanMultiTurn(
                        apiKey = apiKey,
                        systemInstruction = sysInstruction,
                        turns = conversationTurns.toList(),
                        generationConfig = planGenerationConfig
                    )
                }

                // Store model response for future turns
                conversationTurns.add("model" to responseJson)

                val updatedPlan = geminiCaptureRepository.parseDayPlanResponse(responseJson, previousPlan.targetDate)
                currentPlan = updatedPlan
                _state.value = DayOrganizerState.PlanReady(plan = updatedPlan)

            } catch (e: Exception) {
                // Remove the failed user turn so conversation stays consistent
                if (conversationTurns.lastOrNull() == userTurn) {
                    conversationTurns.removeLastOrNull()
                }
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

}
