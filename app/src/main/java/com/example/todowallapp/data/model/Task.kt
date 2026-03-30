package com.example.todowallapp.data.model

import java.time.LocalDateTime
import java.time.LocalDate

/**
 * Represents a single task from Google Tasks
 */
data class Task(
    val id: String,
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    val position: String = "", // Google Tasks uses position for ordering
    val parentId: String? = null, // For subtasks
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    /** Recurrence rule decoded from notes-field metadata tag. */
    val recurrenceRule: RecurrenceRule? = null,
    /** Priority decoded from notes-field metadata tag. */
    val priority: TaskPriority = TaskPriority.NORMAL,
    /** User-visible notes with ||...|| metadata tags stripped. */
    val cleanNotes: String? = null
) {
    /**
     * Determines the urgency level of the task based on due date
     */
    fun getUrgencyLevel(today: LocalDate = LocalDate.now()): TaskUrgency {
        if (isCompleted) return TaskUrgency.COMPLETED
        if (dueDate == null) return TaskUrgency.NORMAL

        val daysUntilDue = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate)

        return when {
            daysUntilDue < 0 -> TaskUrgency.OVERDUE
            daysUntilDue == 0L -> TaskUrgency.DUE_TODAY
            daysUntilDue <= 2 -> TaskUrgency.DUE_SOON
            else -> TaskUrgency.NORMAL
        }
    }
}

private fun urgencySortRank(urgency: TaskUrgency): Int {
    return when (urgency) {
        TaskUrgency.OVERDUE -> 0
        TaskUrgency.DUE_TODAY -> 1
        TaskUrgency.DUE_SOON -> 2
        TaskUrgency.NORMAL -> 3
        TaskUrgency.COMPLETED -> 4
    }
}

fun taskDisplayComparator(today: LocalDate = LocalDate.now()): Comparator<Task> {
    return compareBy<Task> { it.isCompleted }
        .thenBy { urgencySortRank(it.getUrgencyLevel(today)) }
        .thenBy { if (it.priority == TaskPriority.HIGH) 0 else 1 }
        .thenBy { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.position.ifBlank { "~" } }
        .thenByDescending { it.updatedAt }
}

fun sortTasksForDisplay(tasks: List<Task>, today: LocalDate = LocalDate.now()): List<Task> {
    return tasks.sortedWith(taskDisplayComparator(today))
}

/**
 * Urgency levels for visual styling
 */
enum class TaskUrgency {
    COMPLETED,  // Task is done
    OVERDUE,    // Past due date
    DUE_TODAY,  // Due today
    DUE_SOON,   // Due within 2 days
    NORMAL      // No urgency
}

/**
 * Represents a Google Tasks list
 */
data class TaskList(
    val id: String,
    val title: String,
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Mock data for development and preview
 */
object MockData {
    val sampleTasks = listOf(
        Task(
            id = "1",
            title = "Review project proposal",
            notes = "Check budget and timeline sections",
            dueDate = LocalDate.now(),
            isCompleted = false
        ),
        Task(
            id = "2",
            title = "Call dentist for appointment",
            dueDate = LocalDate.now().plusDays(1),
            isCompleted = false
        ),
        Task(
            id = "3",
            title = "Buy groceries",
            notes = "Milk, eggs, bread, vegetables",
            dueDate = LocalDate.now().plusDays(2),
            isCompleted = false
        ),
        Task(
            id = "4",
            title = "Send invoice to client",
            dueDate = LocalDate.now().minusDays(1),
            isCompleted = false
        ),
        Task(
            id = "5",
            title = "Prepare presentation slides",
            notes = "Q4 performance review",
            dueDate = LocalDate.now().plusDays(5),
            isCompleted = false
        ),
        Task(
            id = "6",
            title = "Water the plants",
            isCompleted = true,
            completedAt = LocalDateTime.now().minusHours(2)
        ),
        Task(
            id = "7",
            title = "Schedule team meeting",
            notes = "Discuss sprint planning",
            dueDate = LocalDate.now().plusDays(3),
            isCompleted = false
        )
    )

    val sampleTaskList = TaskList(
        id = "default",
        title = "My Tasks"
    )
}
