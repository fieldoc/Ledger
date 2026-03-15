package com.example.todowallapp.capture.repository

import com.example.todowallapp.capture.model.CaptureCommitFailure
import com.example.todowallapp.capture.model.CaptureCommitSummary
import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import kotlinx.coroutines.withTimeout

interface TaskCommitGateway {
    suspend fun createTaskList(title: String): Result<TaskList>
    suspend fun createTask(
        taskListId: String,
        title: String,
        dueDateIso: java.time.LocalDate?,
        parentId: String?
    ): Result<Task>
}

class CaptureCommitOrchestrator(
    private val gateway: TaskCommitGateway,
    private val routingResolver: ListRoutingResolver = ListRoutingResolver(),
    private val operationTimeoutMs: Long = 15_000L
) {

    suspend fun commit(
        parsedCapture: ParsedCapture,
        existingLists: List<ExistingListRef>
    ): CaptureCommitSummary {
        val routedCapture = routingResolver.resolve(parsedCapture, existingLists)
        val createdListTitles = mutableListOf<String>()
        val failures = mutableListOf<CaptureCommitFailure>()
        var createdTasksCount = 0

        for (listDraft in routedCapture.lists) {
            val listId = when (listDraft.target) {
                ListTarget.EXISTING -> listDraft.existingListId
                ListTarget.NEW_LIST -> null
            } ?: run {
                val listResult = withTimeout(operationTimeoutMs) {
                    gateway.createTaskList(listDraft.name)
                }
                listResult.fold(
                    onSuccess = { created ->
                        createdListTitles += created.title
                        created.id
                    },
                    onFailure = { error ->
                        failures += CaptureCommitFailure(
                            listName = listDraft.name,
                            taskTitle = null,
                            reason = "Failed to create list: ${error.message ?: error.javaClass.simpleName}"
                        )
                        null
                    }
                )
            }

            if (listId == null) continue

            for (task in listDraft.tasks) {
                createdTasksCount += createTaskRecursively(
                    taskListId = listId,
                    listName = listDraft.name,
                    task = task,
                    parentId = null,
                    failures = failures
                )
            }
        }

        return CaptureCommitSummary(
            createdLists = createdListTitles,
            createdTasksCount = createdTasksCount,
            failures = failures
        )
    }

    private suspend fun createTaskRecursively(
        taskListId: String,
        listName: String,
        task: ParsedTaskDraft,
        parentId: String?,
        failures: MutableList<CaptureCommitFailure>
    ): Int {
        val createdTask = withTimeout(operationTimeoutMs) {
            gateway.createTask(
                taskListId = taskListId,
                title = task.title,
                dueDateIso = task.dueDate,
                parentId = parentId
            )
        }.getOrElse { error ->
            failures += CaptureCommitFailure(
                listName = listName,
                taskTitle = task.title,
                reason = error.message ?: error.javaClass.simpleName
            )
            return 0
        }

        var createdCount = 1
        task.subtasks.forEach { child ->
            createdCount += createTaskRecursively(
                taskListId = taskListId,
                listName = listName,
                task = child,
                parentId = createdTask.id,
                failures = failures
            )
        }
        return createdCount
    }
}
