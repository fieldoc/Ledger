package com.example.todowallapp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.todowallapp.capture.VoiceParsingCoordinator
import com.example.todowallapp.capture.model.CaptureCommitSummary
import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedListDraft
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.example.todowallapp.capture.repository.CaptureCommitOrchestrator
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.VoiceIntent
import com.example.todowallapp.capture.repository.PendingCaptureRecord
import com.example.todowallapp.capture.repository.PendingCaptureStore
import com.example.todowallapp.capture.repository.TaskCommitGateway
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskListWithTasks
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.voice.VoiceCaptureManager
import com.example.todowallapp.voice.VoiceInputState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PhoneCaptureUiState(
    val sessionReady: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val isParsingCapture: Boolean = false,
    val isCommittingCapture: Boolean = false,
    val isValidatingKey: Boolean = false,
    val taskLists: List<TaskListWithTasks> = emptyList(),
    val expandedListIds: Set<String> = emptySet(),
    val parsedCapture: ParsedCapture? = null,
    val pendingCaptures: List<PendingCaptureRecord> = emptyList(),
    val geminiKeyPresent: Boolean = false,
    val showVoiceSheet: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val undoTask: Task? = null
)

class PhoneCaptureViewModel(
    context: Context,
    private val tasksRepository: GoogleTasksRepository,
    private val geminiRepository: GeminiCaptureRepository,
    private val geminiKeyStore: GeminiKeyStore,
    private val pendingCaptureStore: PendingCaptureStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PhoneCaptureUiState(
            geminiKeyPresent = geminiKeyStore.hasApiKey()
        )
    )
    val uiState: StateFlow<PhoneCaptureUiState> = _uiState.asStateFlow()

    private val voiceCaptureManager = VoiceCaptureManager(context.applicationContext)
    val voiceState: StateFlow<VoiceInputState> = voiceCaptureManager.state

    private var lastCapturedImageBytes: ByteArray? = null
    private var activePendingCaptureId: String? = null

    private val voiceParsingCoordinator = VoiceParsingCoordinator(
        voiceCaptureManager, geminiRepository, geminiKeyStore
    )

    private data class PhoneUndoState(val task: Task, val taskListId: String)
    private val _undoState = MutableStateFlow<PhoneUndoState?>(null)
    private var undoTimeoutJob: Job? = null

    private val commitOrchestrator = CaptureCommitOrchestrator(
        gateway = object : TaskCommitGateway {
            override suspend fun createTaskList(title: String): Result<TaskList> {
                return tasksRepository.createTaskList(title)
            }

            override suspend fun createTask(
                taskListId: String,
                title: String,
                dueDateIso: java.time.LocalDate?,
                parentId: String?
            ): Result<Task> {
                return tasksRepository.createTask(
                    taskListId = taskListId,
                    title = title,
                    dueDate = dueDateIso,
                    parentId = parentId
                )
            }
        }
    )

    init {
        loadPendingCaptures()
        voiceParsingCoordinator.configure(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { listWithTasks ->
                    ExistingListRef(id = listWithTasks.taskList.id, title = listWithTasks.taskList.title)
                }
            },
            taskProvider = {
                _uiState.value.taskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map {
                        ExistingTaskRef(id = it.id, title = it.title, listId = list.taskList.id)
                    }
                }.take(30)
            },
            listIdValidator = ::resolveKnownTaskListId
        )
    }

    fun setSessionReady(isReady: Boolean) {
        if (_uiState.value.sessionReady == isReady) return
        _uiState.value = _uiState.value.copy(sessionReady = isReady)
        if (isReady) {
            refreshTaskLists()
        } else {
            _uiState.value = PhoneCaptureUiState(
                sessionReady = false,
                pendingCaptures = _uiState.value.pendingCaptures,
                geminiKeyPresent = geminiKeyStore.hasApiKey()
            )
        }
    }

    fun refreshTaskLists() {
        if (!_uiState.value.sessionReady) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = loadTaskLists()
            result.fold(
                onSuccess = { loaded ->
                    _uiState.value = _uiState.value.copy(
                        taskLists = loaded,
                        isLoading = false,
                        isSyncing = false,
                        geminiKeyPresent = geminiKeyStore.hasApiKey()
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSyncing = false,
                        error = "Failed to load tasks: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    fun refreshTaskListsWithIndicator() {
        if (!_uiState.value.sessionReady) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, error = null)
            val result = loadTaskLists()
            result.fold(
                onSuccess = { loaded ->
                    _uiState.value = _uiState.value.copy(
                        taskLists = loaded,
                        isSyncing = false,
                        geminiKeyPresent = geminiKeyStore.hasApiKey()
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSyncing = false,
                        error = "Failed to refresh tasks: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val taskListId = _uiState.value.taskLists
            .firstOrNull { list -> list.tasks.any { it.id == task.id } }
            ?.taskList
            ?.id
            ?: return

        val isCompleting = !task.isCompleted

        viewModelScope.launch {
            val result = if (task.isCompleted) {
                tasksRepository.uncompleteTask(taskListId, task.id)
            } else {
                tasksRepository.completeTask(taskListId, task.id)
            }

            result.fold(
                onSuccess = {
                    if (isCompleting) {
                        _undoState.value = PhoneUndoState(task, taskListId)
                        _uiState.value = _uiState.value.copy(undoTask = task)
                        undoTimeoutJob?.cancel()
                        undoTimeoutJob = viewModelScope.launch {
                            delay(5_000)
                            _undoState.value = null
                            _uiState.value = _uiState.value.copy(undoTask = null)
                        }
                    }
                    refreshTaskListsWithIndicator()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to update task: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    fun undoCompletion() {
        val undo = _undoState.value ?: return
        undoTimeoutJob?.cancel()
        _undoState.value = null
        _uiState.value = _uiState.value.copy(undoTask = null)
        viewModelScope.launch {
            tasksRepository.uncompleteTask(undo.taskListId, undo.task.id)
            refreshTaskListsWithIndicator()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val taskListId = _uiState.value.taskLists
                .firstOrNull { tl -> tl.tasks.any { it.id == task.id } }
                ?.taskList?.id ?: return@launch
            tasksRepository.deleteTask(taskListId, task.id).onSuccess {
                refreshTaskListsWithIndicator()
            }
        }
    }

    fun toggleListExpanded(listId: String) {
        _uiState.value = _uiState.value.let { state ->
            val current = state.expandedListIds
            state.copy(
                expandedListIds = if (listId in current) current - listId else current + listId
            )
        }
    }

    fun showVoiceSheet() {
        _uiState.value = _uiState.value.copy(showVoiceSheet = true)
    }

    fun hideVoiceSheet() {
        voiceCaptureManager.resetToIdle()
        _uiState.value = _uiState.value.copy(showVoiceSheet = false)
    }

    fun startVoiceInput() {
        voiceCaptureManager.startListening()
    }

    fun stopVoiceInput() {
        voiceCaptureManager.stopListening()
    }

    fun cancelVoiceInput() {
        voiceParsingCoordinator.cancelParse()
        voiceParsingCoordinator.clearMetadata()
        voiceCaptureManager.cancel()
    }

    fun dismissVoiceError() {
        voiceCaptureManager.resetToIdle()
    }

    fun confirmVoiceTasks(overrideListId: String? = null) {
        val voicePreview = voiceCaptureManager.state.value as? VoiceInputState.Preview ?: return
        val response = voicePreview.response
        val defaultListId = _uiState.value.taskLists.firstOrNull()?.taskList?.id

        viewModelScope.launch {
            when (response.intent) {
                VoiceIntent.ADD -> {
                    var anyFailed = false
                    for (task in response.tasks) {
                        if (task.title.isBlank()) continue
                        val listId = resolveKnownTaskListId(overrideListId)
                            ?: resolveKnownTaskListId(task.targetListId)
                            ?: defaultListId
                            ?: continue
                        val result = tasksRepository.createTask(
                            taskListId = listId,
                            title = task.title,
                            dueDate = task.dueDate,
                            parentId = task.parentTaskId
                        )
                        if (result.isFailure) anyFailed = true
                    }
                    if (anyFailed) {
                        voiceCaptureManager.setError("Some tasks failed to save")
                    } else {
                        voiceParsingCoordinator.clearMetadata()
                        voiceCaptureManager.resetToIdle()
                        _uiState.value = _uiState.value.copy(showVoiceSheet = false)
                        refreshTaskListsWithIndicator()
                    }
                }

                VoiceIntent.COMPLETE -> {
                    val targetTask = response.tasks.firstOrNull() ?: return@launch
                    val targetId = targetTask.parentTaskId ?: return@launch
                    val targetListId = findTaskListId(targetId) ?: return@launch
                    tasksRepository.completeTask(targetListId, targetId).fold(
                        onSuccess = {
                            voiceParsingCoordinator.clearMetadata()
                            voiceCaptureManager.resetToIdle()
                            _uiState.value = _uiState.value.copy(showVoiceSheet = false)
                            refreshTaskListsWithIndicator()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to complete task")
                        }
                    )
                }

                VoiceIntent.DELETE -> {
                    val targetTask = response.tasks.firstOrNull() ?: return@launch
                    val targetId = targetTask.parentTaskId ?: return@launch
                    val targetListId = findTaskListId(targetId) ?: return@launch
                    tasksRepository.deleteTask(targetListId, targetId).fold(
                        onSuccess = {
                            voiceParsingCoordinator.clearMetadata()
                            voiceCaptureManager.resetToIdle()
                            _uiState.value = _uiState.value.copy(showVoiceSheet = false)
                            refreshTaskListsWithIndicator()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to delete task")
                        }
                    )
                }

                VoiceIntent.RESCHEDULE -> {
                    voiceCaptureManager.setError("Rescheduling not yet supported")
                }

                VoiceIntent.QUERY -> {
                    voiceParsingCoordinator.clearMetadata()
                    voiceCaptureManager.resetToIdle()
                    _uiState.value = _uiState.value.copy(showVoiceSheet = false)
                }

                VoiceIntent.AMEND -> {
                    val task = response.tasks.firstOrNull()
                    if (task != null && task.title.isNotBlank()) {
                        val listId = resolveKnownTaskListId(overrideListId)
                            ?: resolveKnownTaskListId(task.targetListId)
                            ?: defaultListId
                            ?: return@launch
                        tasksRepository.createTask(
                            taskListId = listId,
                            title = task.title,
                            dueDate = task.dueDate,
                            parentId = task.parentTaskId
                        ).fold(
                            onSuccess = {
                                voiceParsingCoordinator.clearMetadata()
                                voiceCaptureManager.resetToIdle()
                                _uiState.value = _uiState.value.copy(showVoiceSheet = false)
                                refreshTaskListsWithIndicator()
                            },
                            onFailure = {
                                voiceCaptureManager.setError("Failed to save task")
                            }
                        )
                    }
                }
            }
        }
    }

    private fun findTaskListId(taskId: String): String? {
        return _uiState.value.taskLists
            .firstOrNull { list -> list.tasks.any { it.id == taskId } }
            ?.taskList?.id
    }

    private fun resolveKnownTaskListId(candidateId: String?): String? {
        return candidateId?.takeIf { id ->
            _uiState.value.taskLists.any { it.taskList.id == id }
        }
    }

    fun validateAndSaveGeminiKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Gemini key cannot be blank")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isValidatingKey = true, error = null, infoMessage = null)
            val validation = geminiRepository.validateApiKey(key)
            validation.fold(
                onSuccess = {
                    geminiKeyStore.setApiKey(key)
                    _uiState.value = _uiState.value.copy(
                        isValidatingKey = false,
                        geminiKeyPresent = true,
                        infoMessage = "Gemini key saved"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isValidatingKey = false,
                        geminiKeyPresent = geminiKeyStore.hasApiKey(),
                        error = "Gemini key validation failed: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    fun clearGeminiKey() {
        geminiKeyStore.clearApiKey()
        _uiState.value = _uiState.value.copy(
            geminiKeyPresent = false,
            infoMessage = "Gemini key removed"
        )
    }

    fun parseCapturedImage(imageBytes: ByteArray, pendingCaptureId: String? = null) {
        if (!_uiState.value.sessionReady) return

        val apiKey = geminiKeyStore.getApiKey()
        if (apiKey.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Add a Gemini API key in settings before scanning",
                geminiKeyPresent = false
            )
            return
        }

        viewModelScope.launch {
            activePendingCaptureId = pendingCaptureId
            lastCapturedImageBytes = imageBytes
            _uiState.value = _uiState.value.copy(isParsingCapture = true, error = null, infoMessage = null)
            val existingLists = _uiState.value.taskLists.map { ExistingListRef(it.taskList.id, it.taskList.title) }
            val parseResult = geminiRepository.parseCapture(
                apiKey = apiKey,
                imageJpegBytes = imageBytes,
                existingLists = existingLists
            )

            parseResult.fold(
                onSuccess = { parsed ->
                    pendingCaptureId?.let { pendingCaptureStore.updateError(it, null) }
                    _uiState.value = _uiState.value.copy(
                        parsedCapture = parsed,
                        isParsingCapture = false
                    )
                },
                onFailure = { error ->
                    pendingCaptureId?.let { pendingCaptureStore.updateError(it, error.message) }
                    loadPendingCaptures()
                    _uiState.value = _uiState.value.copy(
                        isParsingCapture = false,
                        error = "Failed to parse capture: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    fun saveLastCaptureForRetry() {
        val bytes = lastCapturedImageBytes ?: return
        val saved = pendingCaptureStore.saveCapture(bytes)
        saved.fold(
            onSuccess = {
                loadPendingCaptures()
                _uiState.value = _uiState.value.copy(
                    error = null,
                    infoMessage = "Saved for retry later"
                )
                lastCapturedImageBytes = null
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    error = "Failed to save pending capture: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        )
    }

    fun retryPendingCapture(recordId: String) {
        val bytes = pendingCaptureStore.readCaptureBytes(recordId)
        bytes.fold(
            onSuccess = { image ->
                _uiState.value = _uiState.value.copy(error = null)
                parseCapturedImage(image, pendingCaptureId = recordId)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    error = "Failed to read pending capture: ${error.message ?: error.javaClass.simpleName}"
                )
            }
        )
    }

    fun removePendingCapture(recordId: String) {
        pendingCaptureStore.removeCapture(recordId)
        loadPendingCaptures()
    }

    fun dismissParsedCapture() {
        _uiState.value = _uiState.value.copy(parsedCapture = null, error = null)
        lastCapturedImageBytes = null
    }

    fun updateParsedListName(listLocalId: String, newName: String) {
        val parsed = _uiState.value.parsedCapture ?: return
        val updated = parsed.lists.map { list ->
            if (list.localId == listLocalId) {
                list.copy(name = newName)
            } else {
                list
            }
        }
        _uiState.value = _uiState.value.copy(parsedCapture = parsed.copy(lists = updated))
    }

    fun assignParsedListToExisting(listLocalId: String, existingListId: String?) {
        val parsed = _uiState.value.parsedCapture ?: return
        val taskListNameById = _uiState.value.taskLists.associate { it.taskList.id to it.taskList.title }
        val updated = parsed.lists.map { list ->
            if (list.localId != listLocalId) {
                list
            } else if (existingListId == null) {
                list.copy(target = ListTarget.NEW_LIST, existingListId = null)
            } else {
                list.copy(
                    target = ListTarget.EXISTING,
                    existingListId = existingListId,
                    name = taskListNameById[existingListId] ?: list.name
                )
            }
        }
        _uiState.value = _uiState.value.copy(parsedCapture = parsed.copy(lists = updated))
    }

    fun updateParsedTaskTitle(taskLocalId: String, newTitle: String) {
        val parsed = _uiState.value.parsedCapture ?: return
        val updatedLists = parsed.lists.map { list ->
            list.copy(tasks = list.tasks.mapTasksRecursively { task ->
                if (task.localId == taskLocalId) task.copy(title = newTitle) else task
            })
        }
        _uiState.value = _uiState.value.copy(parsedCapture = parsed.copy(lists = updatedLists))
    }

    fun removeParsedTask(taskLocalId: String) {
        val parsed = _uiState.value.parsedCapture ?: return
        val updatedLists = parsed.lists.map { list ->
            list.copy(tasks = list.tasks.removeTaskRecursively(taskLocalId))
        }.filter { it.tasks.isNotEmpty() }

        _uiState.value = _uiState.value.copy(
            parsedCapture = parsed.copy(lists = updatedLists)
        )
    }

    fun commitParsedCapture() {
        val parsed = _uiState.value.parsedCapture ?: return
        val existingLists = _uiState.value.taskLists.map { ExistingListRef(it.taskList.id, it.taskList.title) }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCommittingCapture = true,
                error = null,
                infoMessage = null
            )
            val summary = runCatching {
                commitOrchestrator.commit(parsed, existingLists)
            }

            summary.fold(
                onSuccess = { commit ->
                    onCommitSucceeded(commit)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isCommittingCapture = false,
                        error = "Failed to commit tasks: ${error.message ?: error.javaClass.simpleName}"
                    )
                }
            )
        }
    }

    private fun onCommitSucceeded(summary: CaptureCommitSummary) {
        val createdListsText = if (summary.createdLists.isEmpty()) {
            "0 lists"
        } else {
            "${summary.createdLists.size} lists"
        }
        val failureText = if (summary.failures.isEmpty()) {
            ""
        } else {
            ", ${summary.failures.size} failed"
        }

        val pendingId = activePendingCaptureId
        if (pendingId != null) {
            pendingCaptureStore.removeCapture(pendingId)
            activePendingCaptureId = null
            loadPendingCaptures()
        }

        _uiState.value = _uiState.value.copy(
            isCommittingCapture = false,
            parsedCapture = null,
            infoMessage = "Added ${summary.createdTasksCount} tasks, $createdListsText$failureText"
        )
        refreshTaskListsWithIndicator()
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(error = null, infoMessage = null)
        lastCapturedImageBytes = null
    }

    private fun loadPendingCaptures() {
        _uiState.value = _uiState.value.copy(
            pendingCaptures = pendingCaptureStore.listPendingCaptures()
        )
    }

    private suspend fun loadTaskLists(): Result<List<TaskListWithTasks>> = withContext(Dispatchers.IO) {
        runCatching {
            val lists = tasksRepository.getTaskLists().getOrThrow()
            val tasksByList = coroutineScope {
                lists.map { list ->
                    async {
                        val tasks = tasksRepository.getTasks(list.id).getOrElse { emptyList() }
                        TaskListWithTasks(
                            taskList = list,
                            tasks = sortTasksForDisplay(tasks)
                        )
                    }
                }
            }.awaitAll()

            tasksByList
                .sortedWith(compareBy<TaskListWithTasks> { it.tasks.none { task -> !task.isCompleted } }.thenBy { it.taskList.title.lowercase() })
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceParsingCoordinator.destroy()
        voiceCaptureManager.destroy()
    }

    class Factory(
        private val context: Context,
        private val tasksRepository: GoogleTasksRepository,
        private val geminiRepository: GeminiCaptureRepository,
        private val geminiKeyStore: GeminiKeyStore,
        private val pendingCaptureStore: PendingCaptureStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PhoneCaptureViewModel(
                context = context,
                tasksRepository = tasksRepository,
                geminiRepository = geminiRepository,
                geminiKeyStore = geminiKeyStore,
                pendingCaptureStore = pendingCaptureStore
            ) as T
        }
    }
}

private fun List<ParsedTaskDraft>.mapTasksRecursively(transform: (ParsedTaskDraft) -> ParsedTaskDraft): List<ParsedTaskDraft> {
    return map { task ->
        val transformed = transform(task)
        transformed.copy(
            subtasks = transformed.subtasks.mapTasksRecursively(transform)
        )
    }
}

private fun List<ParsedTaskDraft>.removeTaskRecursively(taskLocalId: String): List<ParsedTaskDraft> {
    return mapNotNull { task ->
        if (task.localId == taskLocalId) {
            null
        } else {
            task.copy(
                subtasks = task.subtasks.removeTaskRecursively(taskLocalId)
            )
        }
    }
}
