package com.example.todowallapp.capture.repository

import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CaptureCommitOrchestratorTest {

    @Test
    fun `creates parent before child when committing recursive tasks`() = runBlocking {
        val operations = mutableListOf<String>()
        val gateway = object : TaskCommitGateway {
            private var listCounter = 0
            private var taskCounter = 0

            override suspend fun createTaskList(title: String): Result<TaskList> {
                listCounter += 1
                operations += "list:$title"
                return Result.success(TaskList(id = "list-$listCounter", title = title))
            }

            override suspend fun createTask(
                taskListId: String,
                title: String,
                dueDateIso: LocalDate?,
                parentId: String?
            ): Result<Task> {
                taskCounter += 1
                operations += "task:$title parent=${parentId ?: "root"}"
                return Result.success(Task(id = "task-$taskCounter", title = title, parentId = parentId))
            }
        }

        val orchestrator = CaptureCommitOrchestrator(gateway)
        val capture = ParsedCapture(
            lists = listOf(
                ParsedListDraft(
                    name = "Inbox",
                    target = ListTarget.NEW_LIST,
                    tasks = listOf(
                        ParsedTaskDraft(
                            title = "Parent",
                            subtasks = listOf(
                                ParsedTaskDraft(title = "Child")
                            )
                        )
                    )
                )
            )
        )

        val summary = orchestrator.commit(capture, existingLists = emptyList())

        assertEquals(1, summary.createdLists.size)
        assertEquals(2, summary.createdTasksCount)
        assertEquals(
            listOf(
                "list:Inbox",
                "task:Parent parent=root",
                "task:Child parent=task-1"
            ),
            operations
        )
    }
}
