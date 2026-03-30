package com.example.todowallapp.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TaskSortTest {

    private val today = LocalDate.of(2026, 3, 29)
    private val fixedUpdatedAt = LocalDateTime.of(2026, 3, 29, 12, 0)

    private fun makeTask(
        id: String,
        isCompleted: Boolean = false,
        priority: TaskPriority = TaskPriority.NORMAL,
        dueDate: LocalDate? = null,
        position: String = ""
    ) = Task(
        id = id,
        title = "Task $id",
        priority = priority,
        isCompleted = isCompleted,
        dueDate = dueDate,
        position = position,
        updatedAt = fixedUpdatedAt
    )

    // ── New Task fields ──────────────────────────────────────────────────────

    @Test
    fun `Task default priority is NORMAL`() {
        val task = Task(id = "1", title = "t")
        assertEquals(TaskPriority.NORMAL, task.priority)
    }

    @Test
    fun `Task default recurrenceRule is null`() {
        val task = Task(id = "1", title = "t")
        assertNull(task.recurrenceRule)
    }

    @Test
    fun `Task default cleanNotes is null`() {
        val task = Task(id = "1", title = "t")
        assertNull(task.cleanNotes)
    }

    @Test
    fun `Task accepts HIGH priority`() {
        val task = Task(id = "1", title = "t", priority = TaskPriority.HIGH)
        assertEquals(TaskPriority.HIGH, task.priority)
    }

    @Test
    fun `Task accepts recurrenceRule`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "SUNDAY")
        val task = Task(id = "1", title = "t", recurrenceRule = rule)
        assertEquals(rule, task.recurrenceRule)
    }

    @Test
    fun `Task accepts cleanNotes`() {
        val task = Task(id = "1", title = "t", cleanNotes = "cleaned notes")
        assertEquals("cleaned notes", task.cleanNotes)
    }

    // ── Priority sort ────────────────────────────────────────────────────────

    @Test
    fun `HIGH priority task sorts before NORMAL priority at same urgency`() {
        val normal = makeTask("normal", priority = TaskPriority.NORMAL)
        val high = makeTask("high", priority = TaskPriority.HIGH)
        val sorted = sortTasksForDisplay(listOf(normal, high), today)
        assertEquals("high", sorted[0].id)
        assertEquals("normal", sorted[1].id)
    }

    @Test
    fun `completed tasks sort after all incomplete tasks regardless of priority`() {
        val completedHigh = makeTask("completedHigh", isCompleted = true, priority = TaskPriority.HIGH)
        val normalIncomplete = makeTask("normalIncomplete", priority = TaskPriority.NORMAL)
        val sorted = sortTasksForDisplay(listOf(completedHigh, normalIncomplete), today)
        assertEquals("normalIncomplete", sorted[0].id)
        assertEquals("completedHigh", sorted[1].id)
    }

    @Test
    fun `overdue NORMAL task sorts before HIGH task with no due date`() {
        // Urgency (OVERDUE) beats priority among incomplete tasks
        val overdue = makeTask("overdue", priority = TaskPriority.NORMAL, dueDate = today.minusDays(1))
        val highNoDue = makeTask("highNoDue", priority = TaskPriority.HIGH, dueDate = null)
        val sorted = sortTasksForDisplay(listOf(highNoDue, overdue), today)
        assertEquals("overdue", sorted[0].id)
        assertEquals("highNoDue", sorted[1].id)
    }

    @Test
    fun `two HIGH priority tasks with different urgency sort by urgency`() {
        val highOverdue = makeTask("highOverdue", priority = TaskPriority.HIGH, dueDate = today.minusDays(1))
        val highNormal = makeTask("highNormal", priority = TaskPriority.HIGH, dueDate = null)
        val sorted = sortTasksForDisplay(listOf(highNormal, highOverdue), today)
        assertEquals("highOverdue", sorted[0].id)
        assertEquals("highNormal", sorted[1].id)
    }

    @Test
    fun `HIGH priority sorts before NORMAL when both have same urgency and due date`() {
        val normalDue = makeTask("normalDue", priority = TaskPriority.NORMAL, dueDate = today.plusDays(5))
        val highDue = makeTask("highDue", priority = TaskPriority.HIGH, dueDate = today.plusDays(5))
        val sorted = sortTasksForDisplay(listOf(normalDue, highDue), today)
        assertEquals("highDue", sorted[0].id)
        assertEquals("normalDue", sorted[1].id)
    }
}
