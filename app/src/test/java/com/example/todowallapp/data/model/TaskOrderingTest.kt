package com.example.todowallapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TaskOrderingTest {

    private val today = LocalDate.of(2026, 2, 18)

    @Test
    fun `sortTasksForDisplay prioritizes urgency before non urgent tasks`() {
        val tasks = listOf(
            Task(id = "normal", title = "Normal", dueDate = today.plusDays(10), position = "0003"),
            Task(id = "overdue", title = "Overdue", dueDate = today.minusDays(2), position = "0001"),
            Task(id = "today", title = "Today", dueDate = today, position = "0002"),
            Task(id = "soon", title = "Soon", dueDate = today.plusDays(2), position = "0004"),
            Task(id = "unscheduled", title = "Unscheduled", dueDate = null, position = "0005")
        )

        val sorted = sortTasksForDisplay(tasks, today = today).map { it.id }

        assertEquals(listOf("overdue", "today", "soon", "normal", "unscheduled"), sorted)
    }

    @Test
    fun `sortTasksForDisplay keeps completed tasks after pending tasks`() {
        val tasks = listOf(
            Task(id = "done", title = "Done", isCompleted = true, position = "0001"),
            Task(id = "pending", title = "Pending", dueDate = today.plusDays(3), position = "0002")
        )

        val sorted = sortTasksForDisplay(tasks, today = today).map { it.id }

        assertEquals(listOf("pending", "done"), sorted)
    }

    @Test
    fun `sortTasksForDisplay uses position and updatedAt for stable tiebreaks`() {
        val tasks = listOf(
            Task(
                id = "laterPosition",
                title = "Later position",
                dueDate = today.plusDays(4),
                position = "0002",
                updatedAt = LocalDateTime.of(2026, 2, 10, 8, 0)
            ),
            Task(
                id = "earlierPosition",
                title = "Earlier position",
                dueDate = today.plusDays(4),
                position = "0001",
                updatedAt = LocalDateTime.of(2026, 2, 10, 9, 0)
            ),
            Task(
                id = "samePositionNewerUpdate",
                title = "Same position newer",
                dueDate = today.plusDays(4),
                position = "0001",
                updatedAt = LocalDateTime.of(2026, 2, 10, 10, 0)
            )
        )

        val sorted = sortTasksForDisplay(tasks, today = today).map { it.id }

        assertEquals(
            listOf("samePositionNewerUpdate", "earlierPosition", "laterPosition"),
            sorted
        )
    }
}
