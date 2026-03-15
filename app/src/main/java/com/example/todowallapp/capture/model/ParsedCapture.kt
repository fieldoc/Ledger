package com.example.todowallapp.capture.model

import java.time.LocalDate
import java.util.UUID

data class ParsedCapture(
    val lists: List<ParsedListDraft>,
    val warnings: List<String> = emptyList()
)

data class ParsedListDraft(
    val localId: String = UUID.randomUUID().toString(),
    val name: String,
    val target: ListTarget,
    val existingListId: String? = null,
    val tasks: List<ParsedTaskDraft> = emptyList()
)

data class ParsedTaskDraft(
    val localId: String = UUID.randomUUID().toString(),
    val title: String,
    val dueDate: LocalDate? = null,
    val subtasks: List<ParsedTaskDraft> = emptyList()
)

enum class ListTarget {
    EXISTING,
    NEW_LIST
}

data class CaptureCommitFailure(
    val listName: String,
    val taskTitle: String?,
    val reason: String
)

data class CaptureCommitSummary(
    val createdLists: List<String> = emptyList(),
    val createdTasksCount: Int = 0,
    val failures: List<CaptureCommitFailure> = emptyList()
)
