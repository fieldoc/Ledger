package com.example.todowallapp.capture.router

/**
 * Routes raw voice transcriptions to the correct pipeline:
 * task management or day planning.
 */
object VoiceIntentRouter {

    sealed class RoutedIntent {
        data class TaskAction(val transcription: String) : RoutedIntent()
        data class DayPlanning(val transcription: String) : RoutedIntent()
    }

    // Only explicitly intentional phrases trigger day planning.
    // Ambiguous input like "I need to do X" must fall through to task creation.
    private val DAY_PLANNING_PATTERNS = listOf(
        "plan my day",
        "plan my morning",
        "plan my afternoon",
        "plan my evening",
        "plan my schedule",
        "plan today",
        "plan tomorrow",
        "organize my day",
        "organize my schedule",
        "organize my morning",
        "organize my afternoon",
        "organize my evening",
        "schedule my day",
        "lay out my day",
        "map out my day"
    )

    /**
     * Classify a voice transcription as either a task action or day planning request.
     * Uses keyword matching — no network call, zero latency.
     * Falls back to TaskAction when ambiguous.
     */
    fun classifyIntent(rawTranscription: String): RoutedIntent {
        val normalized = rawTranscription.trim().lowercase()

        for (pattern in DAY_PLANNING_PATTERNS) {
            if (normalized.contains(pattern)) {
                return RoutedIntent.DayPlanning(rawTranscription)
            }
        }

        return RoutedIntent.TaskAction(rawTranscription)
    }
}
