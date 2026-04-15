package com.example.todowallapp.capture

import android.util.Log
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.GeminiPrompt
import com.google.gson.JsonObject
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.EnergyProfile
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.security.GeminiKeyStore

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalTime

private const val TAG = "DayOrganizerCoordinator"

// ---------------------------------------------------------------------------
// State machine
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Coordinator
// ---------------------------------------------------------------------------

class DayOrganizerCoordinator(
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val calendarRepository: GoogleCalendarRepository,
    private val tasksRepository: GoogleTasksRepository
) {
    private val _state = MutableStateFlow<DayOrganizerState>(DayOrganizerState.Idle)
    val state: StateFlow<DayOrganizerState> = _state.asStateFlow()

    /** One-shot error events for transient adjustment failures (UI shows toast, plan stays visible). */
    private val _adjustmentErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val adjustmentErrors: SharedFlow<String> = _adjustmentErrors.asSharedFlow()

    var currentPlan: DayPlan? = null
        private set
    var lastTranscription: String? = null
        private set

    /** IDs of calendar events created during the last acceptPlan() — for potential rollback. */
    private var lastCreatedEventIds: MutableList<String> = mutableListOf()

    /** Multi-turn conversation for adjustments: list of (role, text) pairs. */
    private var conversationTurns: MutableList<Pair<String, String>> = mutableListOf()

    /** Cached system instruction and generation config from initial plan request. */
    private var planSystemInstruction: String? = null
    private var planGenerationConfig: JsonObject? = null

    /** Tracks the number of adjustment attempts for the current plan to prevent infinite loops. */
    private var adjustmentAttempts = 0

    private var parseJob: Job? = null

    // Stored when generatePlan is called
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
    private var energyProfile: EnergyProfile = EnergyProfile.BALANCED

    companion object {
        private const val GEMINI_TIMEOUT_MS = 60_000L
        private const val MAX_ADJUSTMENT_ATTEMPTS = 10
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

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
        if (_state.value !is DayOrganizerState.Idle) return
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

    fun adjustPlan(adjustmentText: String) {
        val currentState = _state.value
        if (currentState !is DayOrganizerState.PlanReady) return
        handleAdjustmentTranscription(adjustmentText, currentState.plan)
    }

    suspend fun acceptPlan(): Result<Int> {
        val plan = currentPlan ?: return Result.failure(IllegalStateException("No plan available"))
        val newBlocks = plan.blocks.filter { !it.isExistingEvent }
        _state.value = DayOrganizerState.Confirming(current = 0, total = newBlocks.size)

        lastCreatedEventIds.clear()

        val succeeded = mutableListOf<PlanBlock>()
        val failed = mutableListOf<PlanBlock>()

        for (block in newBlocks) {
            val result = calendarRepository.createEvent(
                title = block.title,
                startDateTime = block.startTime,
                endDateTime = block.endTime,
                calendarId = selectedCalendarId,
                description = buildEventDescription(block)
            )

            result.fold(
                onSuccess = { event ->
                    succeeded.add(block)
                    lastCreatedEventIds.add(event.id)
                },
                onFailure = { failed.add(block) }
            )
            // Emit progress after each attempt
            _state.value = DayOrganizerState.Confirming(
                current = succeeded.size + failed.size,
                total = newBlocks.size
            )
        }

        return when {
            failed.isEmpty() -> {
                _state.value = DayOrganizerState.Idle
                Result.success(succeeded.size)
            }
            succeeded.isEmpty() -> {
                _state.value = DayOrganizerState.Error(
                    message = "Failed to create any events (${failed.size} error(s)).",
                    canRetry = true
                )
                Result.failure(IllegalStateException("All event creations failed"))
            }
            else -> {
                _state.value = DayOrganizerState.PartialSuccess(
                    createdCount = succeeded.size,
                    failedBlocks = failed
                )
                Result.success(succeeded.size)
            }
        }
    }

    /** Retry creating only the blocks that failed in a previous acceptPlan() call. */
    suspend fun retryFailedBlocks(blocks: List<PlanBlock>): Result<Int> {
        if (blocks.isEmpty()) return Result.success(0)
        _state.value = DayOrganizerState.Confirming(current = 0, total = blocks.size)

        val succeeded = mutableListOf<PlanBlock>()
        val stillFailed = mutableListOf<PlanBlock>()

        for (block in blocks) {
            val result = calendarRepository.createEvent(
                title = block.title,
                startDateTime = block.startTime,
                endDateTime = block.endTime,
                calendarId = selectedCalendarId,
                description = buildEventDescription(block)
            )
            result.fold(
                onSuccess = { event ->
                    succeeded.add(block)
                    lastCreatedEventIds.add(event.id)
                },
                onFailure = { stillFailed.add(block) }
            )
            _state.value = DayOrganizerState.Confirming(
                current = succeeded.size + stillFailed.size,
                total = blocks.size
            )
        }

        return when {
            stillFailed.isEmpty() -> {
                _state.value = DayOrganizerState.Idle
                Result.success(succeeded.size)
            }
            succeeded.isEmpty() -> {
                _state.value = DayOrganizerState.Error(
                    message = "Retry failed — ${stillFailed.size} event(s) still couldn't be created.",
                    canRetry = true
                )
                Result.failure(IllegalStateException("All retried event creations failed"))
            }
            else -> {
                _state.value = DayOrganizerState.PartialSuccess(
                    createdCount = succeeded.size,
                    failedBlocks = stillFailed
                )
                Result.success(succeeded.size)
            }
        }
    }

    /** Returns the IDs of calendar events created during the last acceptPlan() call. */
    fun getLastCreatedEventIds(): List<String> = lastCreatedEventIds.toList()

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

    fun setFocusedIndex(index: Int) {
        val s = _state.value
        if (s is DayOrganizerState.PlanReady) {
            _state.value = s.copy(focusedIndex = index)
        }
    }

    fun setPendingRemove(index: Int?) {
        val s = _state.value
        if (s is DayOrganizerState.PlanReady) {
            _state.value = s.copy(pendingRemoveIndex = index)
        }
    }

    fun confirmRemoveBlock(index: Int) {
        val s = _state.value
        if (s !is DayOrganizerState.PlanReady) return
        if (index !in s.plan.blocks.indices) return
        val newBlocks = s.plan.blocks.toMutableList().also { it.removeAt(index) }
        val newPlan = s.plan.copy(blocks = newBlocks)
        currentPlan = newPlan
        // If the removed block was at or past the end, clamp focus to last block
        val newFocusIndex = if (s.focusedIndex >= newBlocks.size) newBlocks.size.coerceAtLeast(0) else s.focusedIndex
        _state.value = s.copy(
            plan = newPlan,
            focusedIndex = newFocusIndex,
            pendingRemoveIndex = null
        )
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private fun handleTranscription(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank() || trimmed.length < 3) {
            _state.value = DayOrganizerState.Error(
                message = "Couldn't hear anything — try speaking more clearly.",
                canRetry = true
            )
            return
        }

        lastTranscription = trimmed
        adjustmentAttempts = 0  // new plan resets the adjustment counter
        _state.value = DayOrganizerState.Processing()

        parseJob = scope?.launch {
            try {
                val apiKey = geminiKeyStore.getApiKey()
                if (apiKey == null) {
                    if (_state.value is DayOrganizerState.Idle) return@launch
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
                    groundingContext = grounding,
                    energyProfile = energyProfile
                )

                // Cache system instruction & generation config for multi-turn adjustments
                planSystemInstruction = prompt.systemInstruction
                planGenerationConfig = prompt.generationConfig

                val responseJson = withContext(Dispatchers.IO) {
                    withTimeout(GEMINI_TIMEOUT_MS) {
                        geminiCaptureRepository.callGeminiForDayPlan(apiKey, prompt)
                    }
                }

                if (_state.value is DayOrganizerState.Idle) return@launch

                // Initialize conversation history for future adjustments
                conversationTurns.clear()
                conversationTurns.add("user" to prompt.userContent)
                conversationTurns.add("model" to responseJson)

                val plan = geminiCaptureRepository.parseDayPlanResponse(responseJson, targetDate)
                currentPlan = plan
                _state.value = DayOrganizerState.PlanReady(plan = plan)

            } catch (e: TimeoutCancellationException) {
                if (_state.value is DayOrganizerState.Idle) return@launch
                Log.w(TAG, "Day plan Gemini call timed out after ${GEMINI_TIMEOUT_MS}ms")
                _state.value = DayOrganizerState.Error(
                    message = "Request timed out. Try again.",
                    canRetry = true
                )
            } catch (e: CancellationException) {
                // Job was cancelled (e.g., user pressed cancel) — don't overwrite state
                throw e
            } catch (e: Exception) {
                if (_state.value is DayOrganizerState.Idle) return@launch
                _state.value = DayOrganizerState.Error(
                    message = e.message ?: "Planning failed. Try again.",
                    canRetry = true
                )
            }
        }
    }

    private fun handleAdjustmentTranscription(rawText: String, previousPlan: DayPlan) {
        val trimmed = rawText.trim()
        if (trimmed.isBlank() || trimmed.length < 3) {
            _adjustmentErrors.tryEmit("Couldn't hear the adjustment — speak clearly and try again.")
            _state.value = DayOrganizerState.PlanReady(plan = previousPlan)
            return
        }

        adjustmentAttempts++
        if (adjustmentAttempts >= MAX_ADJUSTMENT_ATTEMPTS) {
            _state.value = DayOrganizerState.Error(
                message = "Too many adjustments. Please accept or cancel the plan.",
                canRetry = false
            )
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
                    if (_state.value is DayOrganizerState.Idle) return@launch
                    _state.value = DayOrganizerState.Error("Gemini API key not configured.", canRetry = false)
                    return@launch
                }

                val sysInstruction = planSystemInstruction
                    ?: error("Missing system instruction from initial plan request.")

                val responseJson = withContext(Dispatchers.IO) {
                    withTimeout(GEMINI_TIMEOUT_MS) {
                        geminiCaptureRepository.callGeminiForDayPlanMultiTurn(
                            apiKey = apiKey,
                            systemInstruction = sysInstruction,
                            turns = conversationTurns.toList(),
                            generationConfig = planGenerationConfig
                        )
                    }
                }

                if (_state.value is DayOrganizerState.Idle) return@launch

                // Store model response for future turns
                conversationTurns.add("model" to responseJson)

                val updatedPlan = geminiCaptureRepository.parseDayPlanResponse(responseJson, previousPlan.targetDate)
                currentPlan = updatedPlan
                _state.value = DayOrganizerState.PlanReady(plan = updatedPlan)

            } catch (e: TimeoutCancellationException) {
                if (conversationTurns.lastOrNull() == userTurn) conversationTurns.removeLastOrNull()
                if (_state.value is DayOrganizerState.Idle) return@launch
                Log.w(TAG, "Adjustment Gemini call timed out after ${GEMINI_TIMEOUT_MS}ms")
                _adjustmentErrors.tryEmit("Adjustment timed out. Try again.")
                _state.value = DayOrganizerState.PlanReady(plan = previousPlan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Remove the failed user turn so conversation stays consistent
                if (conversationTurns.lastOrNull() == userTurn) {
                    conversationTurns.removeLastOrNull()
                }
                if (_state.value is DayOrganizerState.Idle) return@launch
                val message = e.message ?: "Adjustment failed. Try again."
                Log.w(TAG, "Adjustment failed: $message")
                _adjustmentErrors.tryEmit(message)
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
