package com.example.todowallapp.capture

import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.voice.VoiceCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Shared coordinator that handles voice transcription → Gemini AI parsing → preview.
 *
 * Both TaskWallViewModel and PhoneCaptureViewModel delegate their voice-parsing
 * pipeline to this class, eliminating ~120 lines of duplicated logic.
 */
class VoiceParsingCoordinator(
    private val voiceCaptureManager: VoiceCaptureManager,
    private val geminiCaptureRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore
) {
    var parsedDueDate: LocalDate? = null
        private set
    var parsedTargetListId: String? = null
        private set
    var parsedParentTaskId: String? = null
        private set

    private var parseJob: Job? = null

    /**
     * Wire up the [VoiceCaptureManager.rawResultCallback] to run AI parsing.
     *
     * @param scope          CoroutineScope (typically viewModelScope) for launching parse work.
     * @param listProvider   Returns the current list of [ExistingListRef] for Gemini context.
     * @param taskProvider   Returns the current list of [ExistingTaskRef] for hierarchy context.
     *                       Defaults to empty (TaskWallViewModel doesn't use this).
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
                voiceCaptureManager.showPreview(transcribedText = normalizedText)
                return@launch
            }

            val parseResult = geminiCaptureRepository.parseVoiceInput(
                apiKey = apiKey,
                rawText = normalizedText,
                existingLists = existingLists,
                existingTasks = existingTasks,
                todayDate = LocalDate.now()
            )

            parseResult.fold(
                onSuccess = { parsed ->
                    parsedDueDate = parsed.dueDate
                    parsedTargetListId = listIdValidator(parsed.targetListId)
                    parsedParentTaskId = parsed.parentTaskId

                    if (parsed.clarification != null) {
                        voiceCaptureManager.showPreview(
                            transcribedText = normalizedText,
                            clarification = parsed.clarification
                        )
                    } else {
                        voiceCaptureManager.showPreview(
                            transcribedText = parsed.title.ifBlank { normalizedText },
                            dueDate = parsedDueDate,
                            targetListId = parsedTargetListId
                        )
                    }
                },
                onFailure = {
                    clearMetadata()
                    voiceCaptureManager.showPreview(transcribedText = normalizedText)
                }
            )
        }
    }

    /** Cancel any in-flight parse and reset all parsed metadata fields. */
    fun clearMetadata() {
        parsedDueDate = null
        parsedTargetListId = null
        parsedParentTaskId = null
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
