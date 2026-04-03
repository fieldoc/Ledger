package com.example.todowallapp.data.repository

import android.content.Context
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskMetadata
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Repository for interacting with Google Tasks API
 */
class GoogleTasksRepository(
    private val context: Context
) {
    private var tasksService: Tasks? = null

    /**
     * Initialize the Tasks service with the signed-in account
     */
    fun initialize(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(TasksScopes.TASKS)
        ).apply {
            selectedAccount = account.account
        }

        val transport = GoogleApiTransportFactory.createTransport()

        tasksService = Tasks.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(GoogleApiTransportFactory.APP_NAME)
            .build()
    }

    private inline fun <T> withTasksService(block: (Tasks) -> T): Result<T> {
        val service = tasksService ?: return Result.failure(
            IllegalStateException("Tasks service not initialized")
        )
        return runCatching { block(service) }
    }

    /**
     * Get all task lists
     */
    suspend fun getTaskLists(): Result<List<TaskList>> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val response = service.tasklists().list().execute()
            val taskLists = response.items?.map { googleTaskList ->
                TaskList(
                    id = googleTaskList.id,
                    title = googleTaskList.title,
                    updatedAt = parseDateTime(googleTaskList.updated) ?: LocalDateTime.MIN
                )
            } ?: emptyList()
            Log.d("TasksRepo", "Fetched ${taskLists.size} task lists")
            taskLists
        }
    }

    /**
     * Get tasks from a specific task list
     */
    suspend fun getTasks(taskListId: String): Result<List<Task>> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val allGoogleTasks = mutableListOf<com.google.api.services.tasks.model.Task>()
            var pageToken: String? = null
            do {
                val request = service.tasks().list(taskListId)
                    .setShowCompleted(true)
                    .setShowHidden(false)
                    .setMaxResults(100)
                if (pageToken != null) {
                    request.setPageToken(pageToken)
                }
                val response = request.execute()
                response.items?.let { allGoogleTasks.addAll(it) }
                pageToken = response.nextPageToken
            } while (pageToken != null)

            val tasks = allGoogleTasks.mapNotNull { googleTask ->
                // Skip deleted tasks
                if (googleTask.deleted == true) return@mapNotNull null

                googleTask.toAppTask()
            }.let(::sortTasksForDisplay)
            Log.d("TasksRepo", "Fetched ${tasks.size} tasks from list $taskListId")
            tasks
        }
    }

    /**
     * Mark a task as completed
     */
    suspend fun completeTask(taskListId: String, taskId: String): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            // Get the current task
            val currentTask = service.tasks().get(taskListId, taskId).execute()

            // Update status to completed
            currentTask.status = "completed"

            // Execute update
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()
            updatedTask.toAppTask()
        }
    }

    /**
     * Mark a task as not completed (uncomplete)
     */
    suspend fun uncompleteTask(taskListId: String, taskId: String): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            // Get the current task
            val currentTask = service.tasks().get(taskListId, taskId).execute()

            // Update status to needsAction (not completed)
            currentTask.status = "needsAction"
            currentTask.completed = null

            // Execute update
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()
            updatedTask.toAppTask()
        }
    }

    /**
     * Update the due date of an existing task.
     * Pass null to clear the due date.
     */
    suspend fun updateTaskDueDate(
        taskListId: String,
        taskId: String,
        dueDate: LocalDate?
    ): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val currentTask = service.tasks().get(taskListId, taskId).execute()
            if (dueDate != null) {
                val dueInstant = dueDate
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .toInstant()
                currentTask.due = dueInstant.toString()
            } else {
                currentTask.due = ""   // empty string clears the due date in Google Tasks API
            }
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()
            updatedTask.toAppTask()
        }
    }

    suspend fun deleteTask(taskListId: String, taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            service.tasks().delete(taskListId, taskId).execute()
            Unit
        }
    }

    suspend fun createTaskList(title: String): Result<TaskList> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val request = com.google.api.services.tasks.model.TaskList()
                .setTitle(title)
            val created = service.tasklists().insert(request).execute()

            TaskList(
                id = created.id,
                title = created.title ?: title,
                updatedAt = parseDateTime(created.updated) ?: LocalDateTime.MIN
            )
        }
    }

    suspend fun createTask(
        taskListId: String,
        title: String,
        dueDate: LocalDate? = null,
        parentId: String? = null,
        notes: String? = null
    ): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val googleTask = com.google.api.services.tasks.model.Task()
                .setTitle(title)

            if (dueDate != null) {
                val dueInstant = dueDate
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .toInstant()
                googleTask.due = dueInstant.toString()
            }

            if (!notes.isNullOrEmpty()) {
                googleTask.notes = notes
            }

            val insertRequest = service.tasks().insert(taskListId, googleTask)
            if (parentId != null) {
                insertRequest.parent = parentId
            }
            val created = insertRequest.execute()
            created.toAppTask()
        }
    }

    /**
     * Move a task to a new position within a list.
     * @param previousTaskId ID of the task to place after, or null to move to first position
     */
    suspend fun moveTask(
        taskListId: String,
        taskId: String,
        previousTaskId: String? = null
    ): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val request = service.tasks().move(taskListId, taskId)
            if (previousTaskId != null) {
                request.previous = previousTaskId
            }
            val movedTask = request.execute()
            movedTask.toAppTask()
        }
    }

    /**
     * Update only the notes field of a task. Used for metadata changes
     * (priority, recurrence) without touching other fields.
     */
    suspend fun updateTaskNotes(
        taskListId: String,
        taskId: String,
        notes: String
    ): Result<Task> = withContext(Dispatchers.IO) {
        withTasksService { service ->
            val currentTask = service.tasks().get(taskListId, taskId).execute()
            currentTask.notes = notes.ifEmpty { null }
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()
            updatedTask.toAppTask()
        }
    }

    companion object {
        /**
         * Detect whether an exception represents an auth/token error (401, expired token, etc.)
         * Used by the ViewModel to decide whether to attempt silent re-authentication.
         */
        fun isAuthError(e: Exception): Boolean {
            return when {
                e is com.google.api.client.googleapis.json.GoogleJsonResponseException && e.statusCode == 401 -> true
                e is com.google.api.client.http.HttpResponseException && e.statusCode == 401 -> true
                e is UserRecoverableAuthIOException -> true
                else -> false
            }
        }
    }

    /**
     * Convert a Google Tasks API Task to our app's Task model.
     * Decodes ||...|| metadata tags from the notes field to populate
     * recurrenceRule, priority, and cleanNotes.
     */
    private fun com.google.api.services.tasks.model.Task.toAppTask(): Task {
        val decoded = TaskMetadata.decode(notes)
        return Task(
            id = id ?: "",
            title = title ?: "",
            notes = notes,
            isCompleted = status == "completed",
            dueDate = parseDueDate(due),
            position = position ?: "",
            parentId = parent,
            updatedAt = parseDateTime(updated) ?: LocalDateTime.MIN,
            completedAt = if (status == "completed") parseDateTime(completed) else null,
            recurrenceRule = decoded.recurrenceRule,
            priority = decoded.priority,
            cleanNotes = decoded.cleanNotes,
            preferredTime = decoded.preferredTime
        )
    }

    /**
     * Parse RFC 3339 timestamp to LocalDateTime, returning null for null or invalid input.
     */
    private fun parseDateTime(rfc3339: String?): LocalDateTime? {
        if (rfc3339 == null) return null
        return try {
            val instant = Instant.parse(rfc3339)
            LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        } catch (e: Exception) {
            Log.w("TasksRepo", "Failed to parse datetime: ", e)
            null
        }
    }

    /**
     * Parse due date from Google Tasks (RFC 3339 date only)
     */
    private fun parseDueDate(due: String?): LocalDate? {
        if (due == null) return null
        return try {
            // Google Tasks due dates are in format "yyyy-MM-dd'T'00:00:00.000Z"
            LocalDate.parse(due.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            Log.w("TasksRepo", "Failed to parse due date: ", e)
            null
        }
    }
}
