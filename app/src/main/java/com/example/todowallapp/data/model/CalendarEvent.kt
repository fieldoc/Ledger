package com.example.todowallapp.data.model

import java.time.LocalDate
import java.time.LocalDateTime

const val ALL_DAY_SCHEDULE_HOUR = -1

data class GoogleCalendar(
    val id: String,
    val title: String,
    val isPrimary: Boolean,
    val isWritable: Boolean
)

data class CalendarEvent(
    val id: String,
    val calendarId: String = "primary",
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startDateTime: LocalDateTime? = null,
    val endDateTime: LocalDateTime? = null,
    val isAllDay: Boolean = false,
    val allDayStartDate: LocalDate? = null,
    val allDayEndDateExclusive: LocalDate? = null,
    val isPromotedTask: Boolean = false,
    val sourceTaskId: String? = null,
    val htmlLink: String? = null
)

data class PromotionDraft(
    val task: Task,
    val startDateTime: LocalDateTime,
    val durationMinutes: Int = 30,
    val calendarId: String = "primary"
) {
    val endDateTime: LocalDateTime
        get() = startDateTime.plusMinutes(durationMinutes.toLong())
}

data class PromotionAnchor(
    val previousEventTitle: String,
    val endsAt: LocalDateTime
)

fun CalendarEvent.occursOn(date: LocalDate): Boolean {
    if (isAllDay) {
        val start = allDayStartDate ?: startDateTime?.toLocalDate() ?: return false
        val endExclusive = allDayEndDateExclusive ?: start.plusDays(1)
        return date >= start && date < endExclusive
    }

    val start = startDateTime ?: return false
    val end = endDateTime ?: start.plusMinutes(30)
    val dayStart = date.atStartOfDay()
    val dayEnd = dayStart.plusDays(1)
    return start < dayEnd && end > dayStart
}

fun buildScheduleMapForDate(
    events: List<CalendarEvent>,
    selectedDate: LocalDate
): Map<Int, List<CalendarEvent>> {
    return events
        .asSequence()
        .filter { it.occursOn(selectedDate) }
        .groupBy { event -> event.scheduleHour(selectedDate) }
        .mapValues { (_, groupedEvents) ->
            groupedEvents.sortedWith(
                compareBy<CalendarEvent> { it.isAllDay.not() }
                    .thenBy { it.startDateTime ?: LocalDateTime.MIN }
                    .thenBy { it.title.lowercase() }
            )
        }
        .toSortedMap()
}

private fun CalendarEvent.scheduleHour(selectedDate: LocalDate): Int {
    if (isAllDay) return ALL_DAY_SCHEDULE_HOUR
    val start = startDateTime ?: return ALL_DAY_SCHEDULE_HOUR
    return if (start.toLocalDate().isBefore(selectedDate)) 0 else start.hour
}
