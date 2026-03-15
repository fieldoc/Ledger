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
        val transcribedText: String,
        val dueDate: LocalDate? = null,
        val targetListId: String? = null,
        val clarification: String? = null
    ) : VoiceInputState()
    data class Error(val message: String) : VoiceInputState()
}

class VoiceCaptureManager(private val context: Context) {
    private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()
    var rawResultCallback: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private companion object {
        const val LISTENING_TIMEOUT_MS = 30_000L
    }

    private val timeoutRunnable = Runnable {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = VoiceInputState.Error("Listening timed out")
    }

    fun startListening() {
        if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.Processing) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceInputState.Error("Speech recognition not available")
            return
        }

        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Increase silence timeout to 2 seconds
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }

            var currentAmplitude = 0f
            var latestPartial: String? = null

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
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
                    _state.value = VoiceInputState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches
                        ?.firstOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    if (text != null) {
                        val callback = rawResultCallback
                        if (callback != null) {
                            _state.value = VoiceInputState.Processing
                            callback(text)
                        } else {
                            showPreview(text)
                        }
                    } else {
                        _state.value = VoiceInputState.Error("Didn't catch that")
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
            })

            speechRecognizer?.startListening(intent)
            mainHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun cancel() {
        mainHandler.removeCallbacks(timeoutRunnable)
        speechRecognizer?.cancel()
        _state.value = VoiceInputState.Idle
    }

    fun resetToIdle() {
        _state.value = VoiceInputState.Idle
    }

    fun showPreview(
        transcribedText: String,
        dueDate: LocalDate? = null,
        targetListId: String? = null,
        clarification: String? = null
    ) {
        _state.value = VoiceInputState.Preview(
            transcribedText = transcribedText,
            dueDate = dueDate,
            targetListId = targetListId,
            clarification = clarification
        )
    }

    fun setError(message: String) {
        _state.value = VoiceInputState.Error(message)
    }

    fun destroy() {
        mainHandler.removeCallbacks(timeoutRunnable)
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
