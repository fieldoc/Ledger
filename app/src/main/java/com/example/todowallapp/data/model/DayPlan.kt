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
    val sourceTaskListId: String? = null,
    val confidence: Float = 1.0f,
    val flexibility: Flexibility = Flexibility.FLEXIBLE
)

enum class BlockCategory {
    ACTIVE,
    PASSIVE,
    ERRAND,
    SOCIAL,
    LEISURE,
    EXISTING_EVENT,
    MEETING,
    HEALTH,
    MEAL,
    BREAK,
    COMMUTE,
    DEEP_WORK
}

enum class Flexibility { RIGID, FLEXIBLE }

enum class EnergyProfile {
    MORNING_PERSON,
    NIGHT_OWL,
    BALANCED;

    fun displayName(): String = when (this) {
        MORNING_PERSON -> "Morning person"
        NIGHT_OWL -> "Night owl"
        BALANCED -> "Balanced"
    }

    fun next(): EnergyProfile = when (this) {
        MORNING_PERSON -> NIGHT_OWL
        NIGHT_OWL -> BALANCED
        BALANCED -> MORNING_PERSON
    }

    fun previous(): EnergyProfile = when (this) {
        MORNING_PERSON -> BALANCED
        NIGHT_OWL -> MORNING_PERSON
        BALANCED -> NIGHT_OWL
    }
}

class DayPlanValidationException(val errors: List<String>) : Exception(
    "Day plan validation failed: ${errors.joinToString("; ")}"
)

/**
 * Validate a parsed day plan for logical consistency.
 * Returns the plan with a warning appended if issues found (soft validation).
 */
fun DayPlan.validated(): DayPlan {
    val errors = mutableListOf<String>()

    for (block in blocks) {
        if (block.startTime >= block.endTime) {
            errors += "Block '${block.title}': start ${block.startTime.toLocalTime()} >= end ${block.endTime.toLocalTime()}"
        }
    }

    val sorted = blocks.sortedBy { it.startTime }
    for (i in 0 until sorted.size - 1) {
        if (sorted[i].endTime > sorted[i + 1].startTime) {
            errors += "Overlap: '${sorted[i].title}' ends at ${sorted[i].endTime.toLocalTime()} but '${sorted[i + 1].title}' starts at ${sorted[i + 1].startTime.toLocalTime()}"
        }
    }

    if (confidence < 0f || confidence > 1f) {
        errors += "Confidence $confidence outside [0,1]"
    }

    return if (errors.isEmpty()) {
        this
    } else {
        val validationWarning = "Validation: ${errors.joinToString("; ")}"
        val combinedWarning = if (warning != null) "$warning | $validationWarning" else validationWarning
        copy(warning = combinedWarning)
    }
}
