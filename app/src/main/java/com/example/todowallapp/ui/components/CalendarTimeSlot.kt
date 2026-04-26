package com.example.todowallapp.ui.components

import com.example.todowallapp.data.model.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime

data class CalendarTimeSlot(
    val start: LocalDateTime,
    val events: List<CalendarEvent>
)

fun buildHalfHourSlots(
    date: LocalDate,
    events: List<CalendarEvent>,
    startHour: Int = 7,
    endHourExclusive: Int = 23
): List<CalendarTimeSlot> {
    val slots = mutableListOf<CalendarTimeSlot>()
    var cursor = date.atTime(startHour, 0)
    val end = date.atTime(endHourExclusive, 0)
    while (cursor < end) {
        val next = cursor.plusMinutes(30)
        val slotEvents = events.filter { event ->
            val eventStart = event.startDateTime ?: return@filter false
            val eventEnd = event.endDateTime ?: eventStart.plusMinutes(30)
            eventStart < next && eventEnd > cursor
        }
        slots += CalendarTimeSlot(start = cursor, events = slotEvents)
        cursor = next
    }
    return slots
}

/**
 * Represents a drag-selected range of time slots.
 * [startTime] is always <= [endTime] regardless of drag direction.
 */
data class SlotDragRange(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
) {
    val durationMinutes: Int
        get() = java.time.Duration.between(startTime, endTime).toMinutes().toInt()
}
