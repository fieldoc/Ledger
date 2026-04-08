package com.example.todowallapp.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.todowallapp.capture.repository.ParsedVoiceResponse
import com.example.todowallapp.capture.repository.ParsedVoiceTaskItem
import com.example.todowallapp.capture.repository.VoiceIntent
import java.time.LocalDate
import java.util.Locale

sealed class VoiceInputState {
    object Idle : VoiceInputState()
    data class Listening(
        val amplitudeLevel: Float = 0f,
        val partialText: String? = null
    ) : VoiceInputState()
    object Processing : VoiceInputState()
    data class Preview(
        val response: ParsedVoiceResponse
    ) : VoiceInputState() {
        // Convenience accessors for backwards compatibility
        val transcribedText: String get() = response.tasks.firstOrNull()?.title ?: response.rawTranscript
        val dueDate: LocalDate? get() = response.tasks.firstOrNull()?.dueDate
        val targetListId: String? get() = response.tasks.firstOrNull()?.targetListId
        val clarification: String? get() = response.clarification
    }
    data class Error(val message: String) : VoiceInputState()
}

class VoiceCaptureManager(private val context: Context) {
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()
    var rawResultCallback: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Continuous mode state
    private var continuousMode = false
    private var userRequestedStop = false
    private val accumulatedText = StringBuilder()
    var errorCallback: ((String) -> Unit)? = null

    // Named listener field so restartRecognizer() can re-attach it
    private var activeListener: RecognitionListener? = null

    private companion object {
        const val LISTENING_TIMEOUT_MS = 120_000L
    }

    private val timeoutRunnable = Runnable {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = VoiceInputState.Error("Listening timed out")
    }

    private fun buildSpeechIntent(continuous: Boolean): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            if (continuous) {
                // Longer silence thresholds for continuous mode — let the user pause to think
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10000L)
            } else {
                // Original generous thresholds for single-utterance mode
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            }
        }
    }

    private fun restartRecognizer() {
        mainHandler.removeCallbacks(timeoutRunnable)
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(activeListener ?: return)
        speechRecognizer?.startListening(buildSpeechIntent(continuous = true))
        mainHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
    }

    fun startListening(continuous: Boolean = false) {
        if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.Processing) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceInputState.Error("Speech recognition not available")
            return
        }

        mainHandler.post {
            // Initialize continuous mode state on the main thread so listener callbacks
            // reading these fields are always on the same thread.
            continuousMode = continuous
            userRequestedStop = false
            accumulatedText.clear()

            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            var currentAmplitude = 0f
            var latestPartial: String? = null

            // Clear any previous listener before creating a new one
            activeListener = null

            activeListener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    currentAmplitude = 0f
                    latestPartial = null
                    _state.value = VoiceInputState.Listening(amplitudeLevel = 0f, partialText = null)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    // Normalize RMS to 0-1 range (typical RMS range is -2 to 10).
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    currentAmplitude = normalized
                    _state.value = VoiceInputState.Listening(
                        amplitudeLevel = currentAmplitude,
                        partialText = latestPartial
                    )
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.value = VoiceInputState.Processing
                }

                override fun onError(error: Int) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error (code 2)"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout (code 1)"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error (code 3)"
                        SpeechRecognizer.ERROR_SERVER -> "Server error (code 4)"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error (code 5)"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (code 6)"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission denied (code 9)"
                        else -> "Voice input failed (code $error)"
                    }
                    speechRecognizer?.destroy()
                    speechRecognizer = null

                    // In continuous mode, a silence/no-match error mid-session means the user
                    // paused long enough to trigger a timeout. If we already have accumulated text,
                    // deliver it rather than discarding it and showing an error.
                    val isSilenceError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (continuousMode && isSilenceError && accumulatedText.isNotEmpty()) {
                        val finalText = accumulatedText.toString().trim()
                        val callback = rawResultCallback
                        if (callback != null) {
                            _state.value = VoiceInputState.Processing
                            callback(finalText)
                        } else {
                            showPreviewFallback(finalText)
                        }
                        return
                    }

                    errorCallback?.invoke(message)
                    _state.value = VoiceInputState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

                    if (continuousMode) {
                        // Buffer this segment's text
                        if (text != null) {
                            if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                            accumulatedText.append(text)
                        }
                        if (userRequestedStop) {
                            // Deliver final accumulated result
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
                                _state.value = VoiceInputState.Error("Didn't catch that")
                            }
                        } else {
                            // Not stopped yet. Only restart if we got something this segment or
                            // have already accumulated text (user is pausing mid-utterance).
                            // If both are empty the recognizer delivered silence with no speech —
                            // restart would loop forever, so treat it as a no-speech error instead.
                            if (text != null || accumulatedText.isNotEmpty()) {
                                _state.value = VoiceInputState.Listening(
                                    amplitudeLevel = 0f,
                                    partialText = accumulatedText.toString().ifEmpty { null }
                                )
                                restartRecognizer()
                            } else {
                                errorCallback?.invoke("No speech detected")
                                _state.value = VoiceInputState.Error("No speech detected")
                            }
                        }
                    } else {
                        // Original non-continuous behavior
                        if (text != null) {
                            val callback = rawResultCallback
                            if (callback != null) {
                                _state.value = VoiceInputState.Processing
                                callback(text)
                            } else {
                                showPreviewFallback(text)
                            }
                        } else {
                            _state.value = VoiceInputState.Error("Didn't catch that")
                        }
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    latestPartial = partial
                    _state.value = VoiceInputState.Listening(
                        amplitudeLevel = currentAmplitude,
                        partialText = latestPartial
                    )
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            speechRecognizer?.setRecognitionListener(activeListener)
            speechRecognizer?.startListening(buildSpeechIntent(continuous))
            mainHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
        }
    }

    fun stopListening() {
        if (continuousMode) {
            userRequestedStop = true
        }
        speechRecognizer?.stopListening()
    }

    fun cancel() {
        mainHandler.removeCallbacks(timeoutRunnable)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        errorCallback = null
        activeListener = null
        _state.value = VoiceInputState.Idle
    }

    fun resetToIdle() {
        _state.value = VoiceInputState.Idle
    }

    fun showPreview(response: ParsedVoiceResponse) {
        _state.value = VoiceInputState.Preview(response = response)
    }

    fun showPreviewFallback(rawText: String, clarification: String? = null) {
        showPreview(
            ParsedVoiceResponse(
                intent = VoiceIntent.ADD,
                tasks = listOf(
                    ParsedVoiceTaskItem(
                        title = rawText,
                        dueDate = null,
                        preferredTime = null,
                        targetListId = null,
                        newListName = null,
                        parentTaskId = null,
                        confidence = 0f,
                        duplicateOf = null
                    )
                ),
                clarification = clarification,
                rawTranscript = rawText
            )
        )
    }

    fun setError(message: String) {
        _state.value = VoiceInputState.Error(message)
    }

    fun destroy() {
        mainHandler.removeCallbacks(timeoutRunnable)
        speechRecognizer?.destroy()
        speechRecognizer = null
        errorCallback = null
        activeListener = null
    }
}
