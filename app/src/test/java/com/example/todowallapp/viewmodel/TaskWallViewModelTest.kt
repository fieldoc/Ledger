package com.example.todowallapp.viewmodel

import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.ALL_DAY_SCHEDULE_HOUR
import com.example.todowallapp.data.model.PromotionDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskListWithTasks
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.buildScheduleMapForDate
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.data.model.sortTasksForDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Tests for pure logic functions used by TaskWallViewModel:
 * - sortTasksForDisplay ordering
 * - TaskUrgency edge cases
 * - CalendarEvent.occursOn logic
 * - buildScheduleMapForDate grouping
 * - PromotionDraft computed property
 * - TaskWallUiState / UndoState data class defaults
 */
class TaskWallViewModelTest {

    private val today = LocalDate.of(2026, 3, 15)

    // ── sortTasksForDisplay ──────────────────────────────────────────

    @Test
    fun `sortTasksForDisplay with empty list returns empty`() {
        assertEquals(emptyList<Task>(), sortTasksForDisplay(emptyList(), today))
    }

    @Test
    fun `sortTasksForDisplay with single task returns it unchanged`() {
        val task = Task(id = "a", title = "Solo")
        val result = sortTasksForDisplay(listOf(task), today)
        assertEquals(1, result.size)
        assertEquals("a", result[0].id)
    }

    @Test
    fun `sortTasksForDisplay places overdue before due-today before due-soon before normal`() {
        val tasks = listOf(
            Task(id = "normal", title = "Normal", dueDate = today.plusDays(10)),
            Task(id = "dueSoon", title = "Due Soon", dueDate = today.plusDays(1)),
            Task(id = "dueToday", title = "Due Today", dueDate = today),
            Task(id = "overdue", title = "Overdue", dueDate = today.minusDays(3))
        )

        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        assertEquals(listOf("overdue", "dueToday", "dueSoon", "normal"), sorted)
    }

    @Test
    fun `sortTasksForDisplay keeps all completed tasks after all incomplete tasks`() {
        val tasks = listOf(
            Task(id = "done1", title = "Done 1", isCompleted = true),
            Task(id = "pending1", title = "Pending 1"),
            Task(id = "done2", title = "Done 2", isCompleted = true, dueDate = today.minusDays(5)),
            Task(id = "pending2", title = "Pending 2")
        )

        val sorted = sortTasksForDisplay(tasks, today)
        val completedStartIndex = sorted.indexOfFirst { it.isCompleted }
        val lastIncompleteIndex = sorted.indexOfLast { !it.isCompleted }
        assertTrue(
            "All completed tasks should come after all incomplete tasks",
            completedStartIndex > lastIncompleteIndex
        )
    }

    @Test
    fun `sortTasksForDisplay sorts tasks with no due date after tasks with due dates within same urgency`() {
        val tasks = listOf(
            Task(id = "noDue", title = "No due date", dueDate = null),
            Task(id = "farAway", title = "Far away", dueDate = today.plusDays(30))
        )

        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        // Both are NORMAL urgency, but farAway has a real date (sorts before MAX)
        assertEquals(listOf("farAway", "noDue"), sorted)
    }

    @Test
    fun `sortTasksForDisplay breaks due date ties by position`() {
        val tasks = listOf(
            Task(id = "b", title = "B", dueDate = today.plusDays(5), position = "0002"),
            Task(id = "a", title = "A", dueDate = today.plusDays(5), position = "0001")
        )

        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        assertEquals(listOf("a", "b"), sorted)
    }

    @Test
    fun `sortTasksForDisplay breaks position ties by most recent updatedAt first`() {
        val older = LocalDateTime.of(2026, 1, 1, 8, 0)
        val newer = LocalDateTime.of(2026, 3, 1, 8, 0)
        val tasks = listOf(
            Task(id = "old", title = "Old", dueDate = today.plusDays(5), position = "0001", updatedAt = older),
            Task(id = "new", title = "New", dueDate = today.plusDays(5), position = "0001", updatedAt = newer)
        )

        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        assertEquals(listOf("new", "old"), sorted)
    }

    @Test
    fun `sortTasksForDisplay with blank position uses tilde for sorting`() {
        val tasks = listOf(
            Task(id = "blank", title = "Blank", dueDate = today.plusDays(5), position = ""),
            Task(id = "pos", title = "Has Position", dueDate = today.plusDays(5), position = "0001")
        )

        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        // "0001" sorts before "~"
        assertEquals(listOf("pos", "blank"), sorted)
    }

    // ── TaskUrgency edge cases ───────────────────────────────────────

    @Test
    fun `getUrgencyLevel completed task with overdue date still returns COMPLETED`() {
        val task = Task(
            id = "x",
            title = "Completed overdue",
            dueDate = today.minusDays(100),
            isCompleted = true
        )
        assertEquals(TaskUrgency.COMPLETED, task.getUrgencyLevel(today))
    }

    @Test
    fun `getUrgencyLevel due exactly 2 days away is DUE_SOON`() {
        val task = Task(id = "x", title = "Two days", dueDate = today.plusDays(2))
        assertEquals(TaskUrgency.DUE_SOON, task.getUrgencyLevel(today))
    }

    @Test
    fun `getUrgencyLevel due exactly 3 days away is NORMAL`() {
        val task = Task(id = "x", title = "Three days", dueDate = today.plusDays(3))
        assertEquals(TaskUrgency.NORMAL, task.getUrgencyLevel(today))
    }

    @Test
    fun `getUrgencyLevel due exactly 1 day away is DUE_SOON`() {
        val task = Task(id = "x", title = "Tomorrow", dueDate = today.plusDays(1))
        assertEquals(TaskUrgency.DUE_SOON, task.getUrgencyLevel(today))
    }

    @Test
    fun `getUrgencyLevel due yesterday is OVERDUE`() {
        val task = Task(id = "x", title = "Yesterday", dueDate = today.minusDays(1))
        assertEquals(TaskUrgency.OVERDUE, task.getUrgencyLevel(today))
    }

    @Test
    fun `getUrgencyLevel far in the past is still OVERDUE`() {
        val task = Task(id = "x", title = "Ancient", dueDate = today.minusDays(365))
        assertEquals(TaskUrgency.OVERDUE, task.getUrgencyLevel(today))
    }

    // ── CalendarEvent.occursOn ────────────────────────────────────────

    @Test
    fun `occursOn returns true for timed event spanning the date`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Meeting",
            startDateTime = today.atTime(10, 0),
            endDateTime = today.atTime(11, 0)
        )
        assertTrue(event.occursOn(today))
    }

    @Test
    fun `occursOn returns false for timed event on different day`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Meeting",
            startDateTime = today.minusDays(1).atTime(10, 0),
            endDateTime = today.minusDays(1).atTime(11, 0)
        )
        assertFalse(event.occursOn(today))
    }

    @Test
    fun `occursOn returns true for multi-day timed event overlapping the date`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Multi-day",
            startDateTime = today.minusDays(1).atTime(22, 0),
            endDateTime = today.atTime(2, 0)
        )
        assertTrue(event.occursOn(today))
    }

    @Test
    fun `occursOn returns true for all-day event on the date`() {
        val event = CalendarEvent(
            id = "e1",
            title = "All day",
            isAllDay = true,
            allDayStartDate = today,
            allDayEndDateExclusive = today.plusDays(1)
        )
        assertTrue(event.occursOn(today))
    }

    @Test
    fun `occursOn returns false for all-day event on next day`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Tomorrow all day",
            isAllDay = true,
            allDayStartDate = today.plusDays(1),
            allDayEndDateExclusive = today.plusDays(2)
        )
        assertFalse(event.occursOn(today))
    }

    @Test
    fun `occursOn returns true for multi-day all-day event spanning the date`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Conference",
            isAllDay = true,
            allDayStartDate = today.minusDays(1),
            allDayEndDateExclusive = today.plusDays(2)
        )
        assertTrue(event.occursOn(today))
    }

    @Test
    fun `occursOn returns false for all-day event where date equals exclusive end`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Yesterday",
            isAllDay = true,
            allDayStartDate = today.minusDays(1),
            allDayEndDateExclusive = today
        )
        assertFalse(event.occursOn(today))
    }

    @Test
    fun `occursOn returns false for timed event with null startDateTime`() {
        val event = CalendarEvent(
            id = "e1",
            title = "Bad event",
            startDateTime = null,
            endDateTime = null
        )
        assertFalse(event.occursOn(today))
    }

    @Test
    fun `occursOn uses 30 minute default duration when endDateTime is null`() {
        val event = CalendarEvent(
            id = "e1",
            title = "No end",
            startDateTime = today.atTime(23, 45),
            endDateTime = null
        )
        assertTrue(event.occursOn(today))
    }

    // ── buildScheduleMapForDate ───────────────────────────────────────

    @Test
    fun `buildScheduleMapForDate with empty list returns empty map`() {
        val result = buildScheduleMapForDate(emptyList(), today)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildScheduleMapForDate groups events by hour`() {
        val events = listOf(
            CalendarEvent(id = "a", title = "Morning", startDateTime = today.atTime(9, 0), endDateTime = today.atTime(9, 30)),
            CalendarEvent(id = "b", title = "Also morning", startDateTime = today.atTime(9, 15), endDateTime = today.atTime(9, 45)),
            CalendarEvent(id = "c", title = "Afternoon", startDateTime = today.atTime(14, 0), endDateTime = today.atTime(15, 0))
        )
        val result = buildScheduleMapForDate(events, today)
        assertEquals(2, result.size)
        assertEquals(2, result[9]?.size)
        assertEquals(1, result[14]?.size)
    }

    @Test
    fun `buildScheduleMapForDate places all-day events in ALL_DAY_SCHEDULE_HOUR bucket`() {
        val events = listOf(
            CalendarEvent(id = "allday", title = "Holiday", isAllDay = true, allDayStartDate = today, allDayEndDateExclusive = today.plusDays(1))
        )
        val result = buildScheduleMapForDate(events, today)
        assertEquals(1, result.size)
        assertEquals(1, result[ALL_DAY_SCHEDULE_HOUR]?.size)
    }

    @Test
    fun `buildScheduleMapForDate filters out events not on selected date`() {
        val events = listOf(
            CalendarEvent(id = "today", title = "Today", startDateTime = today.atTime(10, 0), endDateTime = today.atTime(11, 0)),
            CalendarEvent(id = "tomorrow", title = "Tomorrow", startDateTime = today.plusDays(1).atTime(10, 0), endDateTime = today.plusDays(1).atTime(11, 0))
        )
        val result = buildScheduleMapForDate(events, today)
        assertEquals(1, result.size)
        assertEquals("Today", result[10]?.first()?.title)
    }

    @Test
    fun `buildScheduleMapForDate sorts all-day events before timed events within same hour`() {
        // Edge case: an all-day event and a timed event that starts at hour 0
        // They'd be in different buckets (ALL_DAY_SCHEDULE_HOUR vs 0), so this
        // tests that the map keys are sorted correctly
        val events = listOf(
            CalendarEvent(id = "timed", title = "Early", startDateTime = today.atTime(0, 30), endDateTime = today.atTime(1, 0)),
            CalendarEvent(id = "allday", title = "Holiday", isAllDay = true, allDayStartDate = today, allDayEndDateExclusive = today.plusDays(1))
        )
        val result = buildScheduleMapForDate(events, today)
        val keys = result.keys.toList()
        assertEquals(ALL_DAY_SCHEDULE_HOUR, keys[0])
        assertEquals(0, keys[1])
    }

    // ── PromotionDraft computed property ──────────────────────────────

    @Test
    fun `PromotionDraft endDateTime uses default 30 minute duration`() {
        val start = today.atTime(14, 0)
        val draft = PromotionDraft(
            task = Task(id = "t", title = "Test"),
            startDateTime = start
        )
        assertEquals(today.atTime(14, 30), draft.endDateTime)
    }

    @Test
    fun `PromotionDraft endDateTime with custom duration`() {
        val start = today.atTime(9, 0)
        val draft = PromotionDraft(
            task = Task(id = "t", title = "Test"),
            startDateTime = start,
            durationMinutes = 90
        )
        assertEquals(today.atTime(10, 30), draft.endDateTime)
    }

    // ── TaskWallUiState defaults ──────────────────────────────────────

    @Test
    fun `TaskWallUiState has sensible defaults`() {
        val state = TaskWallUiState()
        assertTrue(state.tasks.isEmpty())
        assertTrue(state.taskLists.isEmpty())
        assertTrue(state.allTaskLists.isEmpty())
        assertNull(state.selectedTaskListId)
        assertEquals("Tasks", state.selectedTaskListTitle)
        assertFalse(state.isLoading)
        assertFalse(state.isSyncing)
        assertNull(state.error)
        assertNull(state.lastSyncTime)
        assertNull(state.lastSyncSuccess)
        assertFalse(state.hasCalendarScope)
        assertNull(state.promotionDraft)
        assertTrue(state.eventsForRange.isEmpty())
    }

    // ── UndoState ────────────────────────────────────────────────────

    @Test
    fun `UndoState stores task and list id correctly`() {
        val task = Task(id = "t1", title = "Test")
        val undo = UndoState(task = task, taskListId = "list1")
        assertEquals("t1", undo.task.id)
        assertEquals("list1", undo.taskListId)
    }

    // ── TaskListWithTasks ────────────────────────────────────────────

    @Test
    fun `TaskListWithTasks defaults to empty task list`() {
        val list = TaskList(id = "l1", title = "My List")
        val withTasks = TaskListWithTasks(taskList = list)
        assertTrue(withTasks.tasks.isEmpty())
        assertEquals("l1", withTasks.taskList.id)
    }

    @Test
    fun `TaskListWithTasks holds tasks correctly`() {
        val list = TaskList(id = "l1", title = "My List")
        val tasks = listOf(
            Task(id = "t1", title = "Task 1"),
            Task(id = "t2", title = "Task 2")
        )
        val withTasks = TaskListWithTasks(taskList = list, tasks = tasks)
        assertEquals(2, withTasks.tasks.size)
    }

    // ── ThemeMode enum ───────────────────────────────────────────────

    @Test
    fun `ThemeMode has exactly three values`() {
        assertEquals(3, ThemeMode.values().size)
        assertEquals(ThemeMode.AUTO, ThemeMode.valueOf("AUTO"))
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.valueOf("LIGHT"))
    }
}
