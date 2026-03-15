package com.example.todowallapp.data.model

data class TaskListWithTasks(
    val taskList: TaskList,
    val tasks: List<Task> = emptyList()
)
