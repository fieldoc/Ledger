package com.example.todowallapp.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TaskUrgencyTest {

    private val today = LocalDate.of(2026, 2, 18)

    @Test
    fun `completed task always uses completed urgency`() {
        val task = Task(
            id = "done",
            title = "Done",
            dueDate = today.minusDays(10),
            isCompleted = true
        )

        assertEquals(TaskUrgency.COMPLETED, task.getUrgencyLevel(today))
    }

    @Test
    fun `task with no due date is normal urgency`() {
        val task = Task(id = "a", title = "No due date")

        assertEquals(TaskUrgency.NORMAL, task.getUrgencyLevel(today))
    }

    @Test
    fun `overdue task is flagged overdue`() {
        val task = Task(id = "a", title = "Overdue", dueDate = today.minusDays(1))

        assertEquals(TaskUrgency.OVERDUE, task.getUrgencyLevel(today))
    }

    @Test
    fun `task due today is flagged due today`() {
        val task = Task(id = "a", title = "Today", dueDate = today)

        assertEquals(TaskUrgency.DUE_TODAY, task.getUrgencyLevel(today))
    }

    @Test
    fun `task due within two days is flagged due soon`() {
        val task = Task(id = "a", title = "Soon", dueDate = today.plusDays(2))

        assertEquals(TaskUrgency.DUE_SOON, task.getUrgencyLevel(today))
    }

    @Test
    fun `task due later stays normal`() {
        val task = Task(id = "a", title = "Later", dueDate = today.plusDays(5))

        assertEquals(TaskUrgency.NORMAL, task.getUrgencyLevel(today))
    }
}
