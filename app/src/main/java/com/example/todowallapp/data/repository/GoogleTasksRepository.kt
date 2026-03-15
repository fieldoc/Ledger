package com.example.todowallapp.data.repository

import android.content.Context
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import java.security.KeyStore
import java.time.Instant
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
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

        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        val transport = NetHttpTransport.Builder()
            .setSslSocketFactory(sslContext.socketFactory)
            .build()

        tasksService = Tasks.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Ledger")
            .build()
    }

    /**
     * Get all task lists
     */
    suspend fun getTaskLists(): Result<List<TaskList>> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

            val response = service.tasklists().list().execute()
            val taskLists = response.items?.map { googleTaskList ->
                TaskList(
                    id = googleTaskList.id,
                    title = googleTaskList.title,
                    updatedAt = parseDateTime(googleTaskList.updated)
                )
            } ?: emptyList()
            Log.d("TasksRepo", "Fetched ${taskLists.size} task lists")

            Result.success(taskLists)
        } catch (e: Exception) {
            Log.e("TasksRepo", "Failed to fetch task lists", e)
            Result.failure(e)
        }
    }

    /**
     * Get tasks from a specific task list
     */
    suspend fun getTasks(taskListId: String): Result<List<Task>> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

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

                Log.d("TasksRepo", "Task: '${googleTask.title}' parent=${googleTask.parent} position=${googleTask.position}")

                Task(
                    id = googleTask.id,
                    title = googleTask.title ?: "",
                    notes = googleTask.notes,
                    dueDate = parseDueDate(googleTask.due),
                    isCompleted = googleTask.status == "completed",
                    completedAt = if (googleTask.completed != null) {
                        parseDateTime(googleTask.completed)
                    } else null,
                    position = googleTask.position ?: "",
                    parentId = googleTask.parent,
                    updatedAt = parseDateTime(googleTask.updated)
                )
            }.let(::sortTasksForDisplay)
            Log.d("TasksRepo", "Fetched ${tasks.size} tasks from list $taskListId")

            Result.success(tasks)
        } catch (e: Exception) {
            Log.e("TasksRepo", "Failed to fetch tasks for list $taskListId", e)
            Result.failure(e)
        }
    }

    /**
     * Mark a task as completed
     */
    suspend fun completeTask(taskListId: String, taskId: String): Result<Task> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

            // Get the current task
            val currentTask = service.tasks().get(taskListId, taskId).execute()

            // Update status to completed
            currentTask.status = "completed"

            // Execute update
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()

            val task = Task(
                id = updatedTask.id,
                title = updatedTask.title ?: "",
                notes = updatedTask.notes,
                dueDate = parseDueDate(updatedTask.due),
                isCompleted = updatedTask.status == "completed",
                completedAt = parseDateTimeOrNull(updatedTask.completed),
                position = updatedTask.position ?: "",
                parentId = updatedTask.parent,
                updatedAt = parseDateTime(updatedTask.updated)
            )

            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a task as not completed (uncomplete)
     */
    suspend fun uncompleteTask(taskListId: String, taskId: String): Result<Task> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

            // Get the current task
            val currentTask = service.tasks().get(taskListId, taskId).execute()

            // Update status to needsAction (not completed)
            currentTask.status = "needsAction"
            currentTask.completed = null

            // Execute update
            val updatedTask = service.tasks().update(taskListId, taskId, currentTask).execute()

            val task = Task(
                id = updatedTask.id,
                title = updatedTask.title ?: "",
                notes = updatedTask.notes,
                dueDate = parseDueDate(updatedTask.due),
                isCompleted = updatedTask.status == "completed",
                completedAt = parseDateTimeOrNull(updatedTask.completed),
                position = updatedTask.position ?: "",
                parentId = updatedTask.parent,
                updatedAt = parseDateTime(updatedTask.updated)
            )

            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskListId: String, taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

            service.tasks().delete(taskListId, taskId).execute()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTaskList(title: String): Result<TaskList> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )
            val request = com.google.api.services.tasks.model.TaskList()
                .setTitle(title)
            val created = service.tasklists().insert(request).execute()

            Result.success(
                TaskList(
                    id = created.id,
                    title = created.title ?: title,
                    updatedAt = parseDateTime(created.updated)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(
        taskListId: String,
        title: String,
        dueDate: LocalDate? = null,
        parentId: String? = null
    ): Result<Task> = withContext(Dispatchers.IO) {
        try {
            val service = tasksService ?: return@withContext Result.failure(
                Exception("Tasks service not initialized")
            )

            val googleTask = com.google.api.services.tasks.model.Task()
                .setTitle(title)

            if (dueDate != null) {
                val dueInstant = dueDate
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .toInstant()
                googleTask.due = dueInstant.toString()
            }

            val insertRequest = service.tasks().insert(taskListId, googleTask)
            if (parentId != null) {
                insertRequest.parent = parentId
            }
            val created = insertRequest.execute()

            Result.success(
                Task(
                    id = created.id,
                    title = created.title ?: title,
                    dueDate = parseDueDate(created.due),
                    isCompleted = created.status == "completed",
                    completedAt = parseDateTimeOrNull(created.completed),
                    parentId = created.parent,
                    position = created.position ?: "",
                    updatedAt = parseDateTime(created.updated)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
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
                e.javaClass.simpleName == "UserRecoverableAuthIOException" -> true
                e.message?.contains("401") == true -> true
                else -> false
            }
        }
    }

    /**
     * Parse RFC 3339 timestamp to LocalDateTime
     */
    private fun parseDateTime(rfc3339: String?): LocalDateTime {
        if (rfc3339 == null) return LocalDateTime.now()
        return try {
            val instant = Instant.parse(rfc3339)
            LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        } catch (e: Exception) {
            Log.w("TasksRepo", "Failed to parse datetime: ", e)
            LocalDateTime.now()
        }
    }

    private fun parseDateTimeOrNull(rfc3339: String?): LocalDateTime? {
        if (rfc3339 == null) return null
        return try {
            val instant = Instant.parse(rfc3339)
            LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        } catch (e: Exception) {
            Log.w("TasksRepo", "Failed to parse nullable datetime: ", e)
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
