package com.example.todowallapp.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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

    // Always-continuous mode state
    private var userRequestedStop = false
    private val accumulatedText = StringBuilder()

    // Named listener field so restartRecognizer() can re-attach it
    private var activeListener: RecognitionListener? = null

    // Incremented by cancel() to invalidate any pending mainHandler.post from startListening().
    // The deferred post captures this value at call time and aborts if it has changed.
    private var sessionId = 0

    // Idle timeout: if no speech detected for 60s, auto-stop
    private var lastSpeechTimestamp = 0L

    private companion object {
        const val LISTENING_TIMEOUT_MS = 120_000L
        const val IDLE_TIMEOUT_MS = 60_000L
        const val IDLE_CHECK_INTERVAL_MS = 5_000L
        const val IDLE_RMS_THRESHOLD = 0.05f
    }

    private val timeoutRunnable = Runnable {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _state.value = VoiceInputState.Error("Listening timed out")
    }

    private val idleCheckRunnable = object : Runnable {
        override fun run() {
            val elapsed = System.currentTimeMillis() - lastSpeechTimestamp
            if (elapsed >= IDLE_TIMEOUT_MS) {
                // Idle timeout fired
                deliverAccumulatedText()
            } else {
                // Schedule next check
                mainHandler.postDelayed(this, IDLE_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Delivers accumulated text as a final result, or silently cancels if nothing
     * was captured. Used by both idle timeout and explicit stop paths.
     */
    private fun deliverAccumulatedText() {
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.removeCallbacks(idleCheckRunnable)
        unmuteBeep()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null

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
            // Nothing captured — silently return to idle
            _state.value = VoiceInputState.Idle
        }
    }

    private fun buildSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Set silence thresholds very high so Android never triggers end-of-speech
            // before our own idle timeout. The app controls when to stop via tap-to-stop
            // or the 60-second idle check.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 59_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 59_000L)
        }
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Mute the music stream to suppress Android's recognizer start/stop chimes. */
    private fun muteBeep() {
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                0
            )
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun unmuteBeep() {
        try {
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                0
            )
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun restartRecognizer() {
        mainHandler.removeCallbacks(timeoutRunnable)
        // Mute to suppress Android's end/start chime during seamless restart
        muteBeep()
        // Create the new recognizer before destroying the old one to minimize
        // the audio gap between segments — reduces mid-sentence cutoff risk.
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        newRecognizer.setRecognitionListener(activeListener ?: return)
        speechRecognizer?.destroy()
        speechRecognizer = newRecognizer
        newRecognizer.startListening(buildSpeechIntent())
        mainHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
        // Unmute after a short delay to let the start chime pass
        mainHandler.postDelayed({ unmuteBeep() }, 500)
    }

    fun startListening() {
        if (_state.value is VoiceInputState.Listening || _state.value is VoiceInputState.Processing) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = VoiceInputState.Error("Speech recognition not available")
            return
        }

        val capturedSessionId = ++sessionId
        mainHandler.post {
            // Bail out if cancel() was called between startListening() and this post executing.
            if (sessionId != capturedSessionId) return@post
            // Initialize state on the main thread so listener callbacks
            // reading these fields are always on the same thread.
            userRequestedStop = false
            accumulatedText.clear()
            lastSpeechTimestamp = System.currentTimeMillis()

            // Mute the music stream to suppress Android's recognizer start chime
            muteBeep()

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
                    // Track speech activity for idle timeout
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
                    // Always stay in Listening unless user tapped stop —
                    // the restart in onResults will seamlessly continue.
                    if (!userRequestedStop) {
                        return
                    }
                    _state.value = VoiceInputState.Processing
                }

                override fun onError(error: Int) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    mainHandler.removeCallbacks(idleCheckRunnable)
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

                    // If we already have accumulated text from earlier segments, deliver it
                    // rather than discarding on any recoverable error (silence, network,
                    // server, client, audio). Losing captured speech on a transient failure
                    // is the single worst UX outcome in this pipeline.
                    if (accumulatedText.isNotEmpty()) {
                        unmuteBeep()
                        val finalText = accumulatedText.toString().trim()
                        val callback = rawResultCallback
                        if (callback != null) {
                            _state.value = VoiceInputState.Processing
                            callback(finalText)
                        } else {
                            showPreviewFallback(finalText, clarification = message)
                        }
                        return
                    }

                    unmuteBeep()
                    _state.value = VoiceInputState.Error(message)
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

                    // Buffer this segment's text
                    if (text != null) {
                        if (accumulatedText.isNotEmpty()) accumulatedText.append(" ")
                        accumulatedText.append(text)
                    }
                    if (userRequestedStop) {
                        // Deliver final accumulated result
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
                            _state.value = VoiceInputState.Error("Didn't catch that")
                        }
                    } else {
                        // Not stopped yet. Restart recognizer to keep listening.
                        // If we got text OR already have accumulation, keep going.
                        // If both are empty, restart anyway (keep waiting for speech)
                        // rather than erroring — the idle timeout will handle true silence.
                        _state.value = VoiceInputState.Listening(
                            amplitudeLevel = 0f,
                            partialText = accumulatedText.toString().ifEmpty { null }
                        )
                        restartRecognizer()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                    latestPartial = partial
                    // Track speech activity for idle timeout
                    if (partial != null) {
                        lastSpeechTimestamp = System.currentTimeMillis()
                    }
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
            // Start idle check polling
            mainHandler.postDelayed(idleCheckRunnable, IDLE_CHECK_INTERVAL_MS)
        }
    }

    fun stopListening() {
        userRequestedStop = true
        unmuteBeep()
        mainHandler.removeCallbacks(idleCheckRunnable)
        speechRecognizer?.stopListening()
    }

    fun cancel() {
        sessionId++  // invalidate any pending mainHandler.post from startListening()
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.removeCallbacks(idleCheckRunnable)
        unmuteBeep()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
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
        mainHandler.removeCallbacks(idleCheckRunnable)
        speechRecognizer?.destroy()
        speechRecognizer = null
        activeListener = null
    }
}
