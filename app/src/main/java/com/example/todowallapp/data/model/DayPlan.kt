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
    val sourceTaskListId: String? = null
)

enum class BlockCategory {
    ACTIVE,
    PASSIVE,
    ERRAND,
    SOCIAL,
    LEISURE,
    EXISTING_EVENT
}
