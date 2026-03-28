package com.example.todowallapp.capture

import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.ParsedVoiceResponse
import com.example.todowallapp.capture.repository.VoiceIntent
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.voice.VoiceCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shared coordinator that handles voice transcription → Gemini AI parsing → preview.
 *
 * Both TaskWallViewModel and PhoneCaptureViewModel delegate their voice-parsing
 * pipeline to this class. Uses the V2 prompt with multi-task extraction,
 * intent classification, duplicate detection, and temporal reasoning.
 */
class VoiceParsingCoordinator(
    private val voiceCaptureManager: VoiceCaptureManager,
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore
) {
    var lastResponse: ParsedVoiceResponse? = null
        private set

    private var parseJob: Job? = null

    /**
     * Wire up the [VoiceCaptureManager.rawResultCallback] to run AI parsing.
     *
     * @param scope          CoroutineScope (typically viewModelScope) for launching parse work.
     * @param listProvider   Returns the current list of [ExistingListRef] for Gemini context.
     * @param taskProvider   Returns the current list of [ExistingTaskRef] for hierarchy/duplicate context.
     * @param listIdValidator Validates that a candidate list ID actually exists. Returns the ID
     *                        if valid, null otherwise.
     */
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
                    clarification = "No Gemini API key \u2014 add one in Settings for smart parsing"
                )
                return@launch
            }

            val parseResult = geminiCaptureRepository.parseVoiceInputV2(
                apiKey = apiKey,
                rawText = normalizedText,
                existingLists = existingLists,
                existingTasks = existingTasks,
                todayDate = LocalDate.now(),
                currentTime = LocalTime.now()
            )

            parseResult.fold(
                onSuccess = { response ->
                    // Validate list IDs through the validator
                    val validatedResponse = response.copy(
                        tasks = response.tasks.map { task ->
                            task.copy(
                                targetListId = listIdValidator(task.targetListId)
                            )
                        }
                    )
                    lastResponse = validatedResponse
                    if (validatedResponse.intent == VoiceIntent.RESCHEDULE) {
                        clearMetadata()
                        voiceCaptureManager.setError(
                            "Rescheduling is not yet supported. Try adding a new task instead."
                        )
                    } else {
                        voiceCaptureManager.showPreview(validatedResponse)
                    }
                },
                onFailure = { error ->
                    clearMetadata()
                    voiceCaptureManager.showPreviewFallback(
                        normalizedText,
                        clarification = "AI parsing failed \u2014 task will be added as-is"
                    )
                }
            )
        }
    }

    /** Cancel any in-flight parse and reset stored response. */
    fun clearMetadata() {
        lastResponse = null
    }

    /** Cancel in-flight work. Call from ViewModel.onCleared(). */
    fun cancelParse() {
        parseJob?.cancel()
    }

    /** Full teardown: cancel work + clear metadata. */
    fun destroy() {
        cancelParse()
        clearMetadata()
    }
}
