package com.example.todowallapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.todowallapp.auth.AuthState
import com.example.todowallapp.auth.GoogleAuthManager
import com.example.todowallapp.capture.VoiceParsingCoordinator
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.VoiceIntent
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.PromotionAnchor
import com.example.todowallapp.data.model.PromotionDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskListWithTasks
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.data.repository.WeatherRepository
import com.example.todowallapp.data.repository.dataStore
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.security.WeatherKeyStore
import com.example.todowallapp.util.ConnectivityMonitor
import com.example.todowallapp.voice.VoiceCaptureManager
import com.example.todowallapp.voice.VoiceInputState
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * UI State for the Task Wall screen
 */
data class TaskWallUiState(
    val authState: AuthState = AuthState.Loading,
    val tasks: List<Task> = emptyList(),
    val taskLists: List<TaskList> = emptyList(),
    val allTaskLists: List<TaskListWithTasks> = emptyList(),
    val selectedTaskListId: String? = null,
    val selectedTaskListTitle: String = "Tasks",
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val selectedCalendarDate: LocalDate = LocalDate.now(),
    val selectedCalendarId: String = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
    val calendars: List<GoogleCalendar> = emptyList(),
    val isCalendarLoading: Boolean = false,
    val calendarError: String? = null,
    val hasCalendarScope: Boolean = false,
    val promotionDraft: PromotionDraft? = null,
    val promotionAnchor: PromotionAnchor? = null,
    val scheduledTaskEventIds: Map<String, String> = emptyMap(),
    val scheduledTaskTimes: Map<String, LocalDateTime> = emptyMap(),
    val promotionSuccessMessage: String? = null,
    val error: String? = null,
    val lastSyncTime: LocalDateTime? = null,
    val lastSyncSuccess: Boolean? = null,
    val calendarViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val eventsForRange: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val calendarRangeStart: LocalDate = LocalDate.now()
)

data class UndoState(
    val task: Task,
    val taskListId: String
)

enum class ThemeMode {
    AUTO,
    DARK,
    LIGHT
}

/**
 * ViewModel for the Task Wall screen
 */
class TaskWallViewModel(
    private val context: Context,
    private val authManager: GoogleAuthManager,
    private val tasksRepository: GoogleTasksRepository,
    private val calendarRepository: GoogleCalendarRepository,
    private val geminiKeyStore: GeminiKeyStore = GeminiKeyStore(context),
    private val geminiCaptureRepository: GeminiCaptureRepository = GeminiCaptureRepository(),
    private val weatherRepository: WeatherRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskWallUiState())
    val uiState: StateFlow<TaskWallUiState> = _uiState.asStateFlow()
    private val voiceCaptureManager = VoiceCaptureManager(context)
    val voiceState: StateFlow<VoiceInputState> = voiceCaptureManager.state

    // Undo state
    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()
    private var undoTimeoutJob: Job? = null

    // Connectivity monitoring
    private val connectivityMonitor = ConnectivityMonitor(context)
    val isOnline: StateFlow<Boolean> = connectivityMonitor.isOnline

    // Settings state
    private val _sleepStartHour = MutableStateFlow(23)
    val sleepStartHour: StateFlow<Int> = _sleepStartHour.asStateFlow()

    private val _sleepEndHour = MutableStateFlow(7)
    val sleepEndHour: StateFlow<Int> = _sleepEndHour.asStateFlow()

    private val _syncIntervalMinutes = MutableStateFlow(5)
    val syncIntervalMinutes: StateFlow<Int> = _syncIntervalMinutes.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _lightStartHour = MutableStateFlow(8)
    val lightStartHour: StateFlow<Int> = _lightStartHour.asStateFlow()

    private val _lightEndHour = MutableStateFlow(19)
    val lightEndHour: StateFlow<Int> = _lightEndHour.asStateFlow()

    // Gemini key state (for wall-mode settings)
    private val _geminiKeyPresent = MutableStateFlow(geminiKeyStore.hasApiKey())
    val geminiKeyPresent: StateFlow<Boolean> = _geminiKeyPresent.asStateFlow()

    private val _isValidatingGeminiKey = MutableStateFlow(false)
    val isValidatingGeminiKey: StateFlow<Boolean> = _isValidatingGeminiKey.asStateFlow()

    private val _geminiKeyError = MutableStateFlow<String?>(null)
    val geminiKeyError: StateFlow<String?> = _geminiKeyError.asStateFlow()

    // Weather forecast
    private val _weatherForecast = MutableStateFlow<Map<LocalDate, WeatherCondition>>(emptyMap())
    val weatherForecast: StateFlow<Map<LocalDate, WeatherCondition>> = _weatherForecast.asStateFlow()

    // Preference keys
    private val selectedTaskListIdKey = stringPreferencesKey("selected_task_list_id")
    private val sleepStartHourKey = intPreferencesKey("sleep_start_hour")
    private val sleepEndHourKey = intPreferencesKey("sleep_end_hour")
    private val syncIntervalMinutesKey = intPreferencesKey("sync_interval_minutes")
    private val selectedCalendarDateKey = stringPreferencesKey("selected_calendar_date")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val lightStartHourKey = intPreferencesKey("light_start_hour")
    private val lightEndHourKey = intPreferencesKey("light_end_hour")

    companion object {
        private const val SILENT_SIGN_IN_TIMEOUT_MS = 3_000L
        private const val UNDO_TIMEOUT_MS = 5_000L
        private const val SYNC_FEEDBACK_CLEAR_MS = 3_000L
        private const val PROMOTION_ANCHOR_EXPIRY_MS = 7_200_000L
    }

    private var autoSyncJob: Job? = null
    private val refreshMutex = Mutex()
    private val consecutiveSyncFailures = java.util.concurrent.atomic.AtomicInteger(0)
    private val isReauthenticating = java.util.concurrent.atomic.AtomicBoolean(false)
    private val signInMutex = Mutex()
    private val inFlightTaskIds = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val voiceParsingCoordinator = VoiceParsingCoordinator(
        voiceCaptureManager, geminiCaptureRepository, geminiKeyStore
    )

    // Last sync success tracking
    private var syncFeedbackJob: Job? = null
    private var promotionAnchorExpiryJob: Job? = null

    private val durationOptionsMinutes = listOf(15, 30, 45, 60, 90, 120)
    private val promotionTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        voiceParsingCoordinator.configure(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { list ->
                    ExistingListRef(id = list.id, title = list.title)
                }
            },
            taskProvider = {
                _uiState.value.allTaskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map {
                        ExistingTaskRef(id = it.id, title = it.title, listId = list.taskList.id)
                    }
                }.take(30)
            },
            listIdValidator = ::resolveKnownTaskListId
        )
        loadSettings()
        checkAuthState()
        observeConnectivity()
    }

    /** Sync immediately when connectivity returns; reset exponential backoff. */
    private fun observeConnectivity() {
        viewModelScope.launch {
            var wasOnline = isOnline.value
            isOnline.collect { online ->
                if (online && !wasOnline) {
                    consecutiveSyncFailures.set(0)
                    if (_uiState.value.authState is AuthState.Authenticated) {
                        performRefresh(showSyncIndicator = true)
                    }
                }
                wasOnline = online
            }
        }
    }

    /**
     * Load saved settings from DataStore
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = try {
                context.dataStore.data.first()
            } catch (e: Exception) {
                android.util.Log.e("TaskWallViewModel", "DataStore read failed, using defaults", e)
                return@launch
            }
            prefs[sleepStartHourKey]?.let { _sleepStartHour.value = it }
            prefs[sleepEndHourKey]?.let { _sleepEndHour.value = it }
            prefs[syncIntervalMinutesKey]?.let { _syncIntervalMinutes.value = it }
            _themeMode.value = prefs[themeModeKey]
                ?.let { savedMode -> ThemeMode.entries.firstOrNull { it.name == savedMode } }
                ?: ThemeMode.AUTO
            _lightStartHour.value = prefs[lightStartHourKey] ?: 8
            _lightEndHour.value = prefs[lightEndHourKey] ?: 19

            val selectedCalendarDate = prefs[selectedCalendarDateKey]
                ?.let { savedDate -> parseLocalDate(savedDate) }
                ?: LocalDate.now()

            _uiState.update { it.copy(
                selectedCalendarDate = selectedCalendarDate
            ) }
        }
    }

    /**
     * Check if user is already authenticated
     */
    fun checkAuthState() {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.Loading) }

            if (authManager.isSignedIn()) {
                val account = authManager.getCurrentAccount()
                if (account != null) {
                    onSignedIn(account)
                } else {
                    _uiState.update { it.copy(authState = AuthState.NotAuthenticated) }
                }
            } else {
                // Show sign-in immediately, then try silent sign-in briefly in the background
                _uiState.update { it.copy(authState = AuthState.NotAuthenticated) }

                val result = try {
                    withTimeout(SILENT_SIGN_IN_TIMEOUT_MS) { authManager.silentSignIn() }
                } catch (e: TimeoutCancellationException) {
                    Result.failure(e)
                }
                result.fold(
                    onSuccess = { account -> onSignedIn(account) },
                    onFailure = {
                        _uiState.update { it.copy(authState = AuthState.NotAuthenticated) }
                    }
                )
            }
        }
    }

    /**
     * Handle successful sign-in
     */
    fun onSignedIn(account: GoogleSignInAccount) {
        viewModelScope.launch {
            signInMutex.withLock {
                consecutiveSyncFailures.set(0)
                val currentAccount = authManager.getCurrentAccount()
                val calendarAccount = sequenceOf(account, currentAccount)
                    .filterNotNull()
                    .firstOrNull { authManager.hasCalendarScope(it) }
                val hasCalendarScope = calendarAccount != null
                _uiState.update { it.copy(
                    authState = AuthState.Authenticated(account),
                    isLoading = true,
                    hasCalendarScope = hasCalendarScope,
                    calendarError = null
                ) }

                // Initialize the repository
                tasksRepository.initialize(account)

                if (calendarAccount != null) {
                    calendarRepository.initialize(calendarAccount)
                    loadCalendars()
                }

                // Load saved task list preference
                val savedTaskListId = context.dataStore.data
                    .map { preferences -> preferences[selectedTaskListIdKey] }
                    .first()

                // Load task lists
                loadTaskLists(savedTaskListId)

                if (hasCalendarScope) {
                    loadCalendarRange(CalendarViewMode.MONTH, _uiState.value.selectedCalendarDate)
                } else {
                    _uiState.update { it.copy(
                        calendars = emptyList(),
                        eventsForRange = emptyMap(),
                        calendarError = "Calendar access needed - press Enter to grant"
                    ) }
                }

                // Start auto-sync
                startAutoSync()
            }
        }
    }

    /**
     * Load available task lists
     */
    private suspend fun loadTaskLists(preferredTaskListId: String? = null): Boolean {
        val result = tasksRepository.getTaskLists()

        return result.fold(
            onSuccess = { taskLists ->
                val taskListWithTasks = loadTasksForAllLists(taskLists)
                val sortedTaskLists = normalizeTaskLists(taskListWithTasks)

                val selectedId = preferredTaskListId
                    ?.takeIf { preferredId -> sortedTaskLists.any { it.taskList.id == preferredId } }
                    ?: sortedTaskLists.firstOrNull()?.taskList?.id

                val selectedList = sortedTaskLists.find { it.taskList.id == selectedId }?.taskList
                val selectedTasks = sortedTaskLists.find { it.taskList.id == selectedId }?.tasks.orEmpty()

                _uiState.update { it.copy(
                    taskLists = sortedTaskLists.map { it.taskList },
                    allTaskLists = sortedTaskLists,
                    selectedTaskListId = selectedId,
                    selectedTaskListTitle = selectedList?.title ?: "Tasks",
                    tasks = selectedTasks,
                    isLoading = false
                ) }
                true
            },
            onFailure = { error ->
                // If this is an auth error, re-throw so performRefresh can attempt re-auth
                if (error is Exception && GoogleTasksRepository.isAuthError(error)) {
                    _uiState.update { it.copy(isLoading = false) }
                    throw error
                }
                val message = error.message?.let { ": $it" } ?: ""
                _uiState.update { it.copy(
                    error = "Failed to load task lists: ${error.javaClass.simpleName}$message",
                    isLoading = false,
                    lastSyncSuccess = false
                ) }
                scheduleSyncFeedbackClear()
                false
            }
        )
    }

    /**
     * Load tasks for all available lists
     */
    private suspend fun loadTasksForAllLists(taskLists: List<TaskList>): List<TaskListWithTasks> {
        val results = coroutineScope {
            taskLists.map { taskList ->
                async {
                    taskList to tasksRepository.getTasks(taskList.id)
                }
            }
        }.awaitAll()

        // Check if any task fetch failed with an auth error — if so, re-throw for re-auth
        val authError = results.firstNotNullOfOrNull { (_, tasksResult) ->
            tasksResult.exceptionOrNull()?.let { error ->
                if (error is Exception && GoogleTasksRepository.isAuthError(error)) error else null
            }
        }
        if (authError != null) throw authError

        val errors = mutableListOf<String>()
        val allTasks = results.map { (taskList, tasksResult) ->
            val tasks = tasksResult.getOrElse { error ->
                errors += "Failed to load '${taskList.title}': ${error.message ?: error.javaClass.simpleName}"
                emptyList()
            }
            TaskListWithTasks(taskList = taskList, tasks = tasks)
        }

        val syncSuccess = errors.isEmpty()
        _uiState.update { it.copy(
            lastSyncTime = LocalDateTime.now(),
            error = errors.firstOrNull(),
            lastSyncSuccess = syncSuccess
        ) }
        scheduleSyncFeedbackClear()

        return allTasks
    }

    /**
     * Auto-clear sync success indicator after 3 seconds
     */
    private fun scheduleSyncFeedbackClear() {
        syncFeedbackJob?.cancel()
        syncFeedbackJob = viewModelScope.launch {
            delay(SYNC_FEEDBACK_CLEAR_MS)
            // Only clear success feedback; keep failure visible so staleness is apparent
            _uiState.update { state ->
                if (state.lastSyncSuccess == true) state.copy(lastSyncSuccess = null) else state
            }
        }
    }

    /**
     * Attempt silent re-authentication and retry the failed operation.
     * Uses isReauthenticating flag to prevent re-auth loops.
     */
    private suspend fun attemptReauthAndRetry(action: suspend () -> Unit) {
        if (!isReauthenticating.compareAndSet(false, true)) return
        try {
            Log.d("TaskWallVM", "Auth token expired, attempting silent re-authentication")
            val result = authManager.silentSignIn()
            result.onSuccess { account ->
                Log.d("TaskWallVM", "Silent re-auth succeeded, retrying operation")
                onSignedIn(account)
                consecutiveSyncFailures.set(0)
                action()
            }.onFailure { error ->
                Log.e("TaskWallVM", "Silent re-auth failed: ${error.message}")
                _uiState.update { it.copy(
                    error = "Session expired. Please sign in again.",
                    authState = AuthState.NotAuthenticated
                ) }
            }
        } finally {
            isReauthenticating.set(false)
        }
    }

    /**
     * Refresh tasks from the API
     */
    fun refresh() {
        viewModelScope.launch {
            performRefresh(showSyncIndicator = true)
        }
    }

    fun refreshWeather() {
        val repo = weatherRepository ?: return
        viewModelScope.launch {
            _weatherForecast.value = repo.getForecast()
        }
    }

    private suspend fun performRefresh(showSyncIndicator: Boolean): Boolean {
        if (_uiState.value.authState !is AuthState.Authenticated) return false

        if (refreshMutex.isLocked) return false

        if (!isOnline.value) {
            if (showSyncIndicator) {
                _uiState.update { it.copy(
                    isSyncing = false,
                    error = "Offline: waiting for connection to sync",
                    lastSyncSuccess = false
                ) }
                scheduleSyncFeedbackClear()
            }
            return false
        }

        return refreshMutex.withLock {
            if (showSyncIndicator) {
                _uiState.update { it.copy(isSyncing = true) }
            }
            try {
                val selectedTaskListId = _uiState.value.selectedTaskListId
                val selectedCalendarDate = _uiState.value.selectedCalendarDate
                val hasCalendarScope = _uiState.value.hasCalendarScope

                val (tasksLoaded, calendarLoaded) = coroutineScope {
                    val tasksDeferred = async { loadTaskLists(selectedTaskListId) }
                    val calendarDeferred = async {
                        if (hasCalendarScope) {
                            loadCalendarRangeInternal(_uiState.value.calendarViewMode, selectedCalendarDate)
                        } else {
                            true
                        }
                    }
                    tasksDeferred.await() to calendarDeferred.await()
                }

                val syncSuccess = tasksLoaded && calendarLoaded && (_uiState.value.lastSyncSuccess != false)
                if (syncSuccess) {
                    consecutiveSyncFailures.set(0)
                } else {
                    consecutiveSyncFailures.updateAndGet { (it + 1).coerceAtMost(3) }
                }
                // Refresh weather (cache handles throttling — only hits API every 3h)
                if (syncSuccess) refreshWeather()
                syncSuccess
            } catch (e: Exception) {
                // Auth errors are re-thrown from loadTaskLists/loadTasksForAllLists
                if (GoogleTasksRepository.isAuthError(e)) {
                    Log.w("TaskWallVM", "Auth error during sync, attempting re-authentication", e)
                    _uiState.update { it.copy(isSyncing = false) }
                    attemptReauthAndRetry { performRefresh(showSyncIndicator) }
                    return@withLock false
                }
                // Non-auth exceptions: let the finally block clean up
                consecutiveSyncFailures.updateAndGet { (it + 1).coerceAtMost(3) }
                false
            } finally {
                _uiState.update { it.copy(isSyncing = false) }
            }
        }
    }

    /**
     * Select a different task list
     */
    fun selectTaskList(taskListId: String) {
        viewModelScope.launch {
            val selectedListWithTasks = _uiState.value.allTaskLists.find { it.taskList.id == taskListId }
            val taskList = selectedListWithTasks?.taskList

            _uiState.update { it.copy(
                selectedTaskListId = taskListId,
                selectedTaskListTitle = taskList?.title ?: "Tasks",
                tasks = selectedListWithTasks?.tasks.orEmpty()
            ) }

            // Save preference
            context.dataStore.edit { preferences ->
                preferences[selectedTaskListIdKey] = taskListId
            }
        }
    }

    /**
     * Mark a task as completed
     */
    fun completeTask(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            // Save undo state before completing
            _undoState.value = UndoState(task = task, taskListId = taskListId)
            undoTimeoutJob?.cancel()
            undoTimeoutJob = viewModelScope.launch {
                delay(UNDO_TIMEOUT_MS)
                _undoState.value = null
            }

            val completedTask = task.copy(isCompleted = true, completedAt = LocalDateTime.now())

            // Optimistic update
            inFlightTaskIds.add(task.id)
            updateTaskAcrossLists(task.id) { completedTask }

            // API call
            val result = tasksRepository.completeTask(taskListId, task.id)

            result.onSuccess { serverTask ->
                inFlightTaskIds.remove(task.id)
                updateTaskAcrossLists(task.id) { serverTask }
            }
            result.onFailure { error ->
                inFlightTaskIds.remove(task.id)
                // Revert on failure
                updateTaskAcrossLists(task.id) { task }
                _undoState.value = null
                undoTimeoutJob?.cancel()
                val exception = error as? Exception
                if (exception != null && GoogleTasksRepository.isAuthError(exception)) {
                    attemptReauthAndRetry { performRefresh(false) }
                    return@launch
                }
                _uiState.update { it.copy(error = "Failed to complete task: ${error.message}") }
            }
        }
    }

    /**
     * Undo the last task completion
     */
    fun undoCompletion() {
        val undo = _undoState.value ?: return
        undoTimeoutJob?.cancel()
        _undoState.value = null
        uncompleteTask(undo.task)
    }

    /**
     * Dismiss the undo snackbar without undoing
     */
    fun dismissUndo() {
        undoTimeoutJob?.cancel()
        _undoState.value = null
    }

    /**
     * Mark a task as not completed
     */
    fun uncompleteTask(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch
            val uncompletedTask = task.copy(isCompleted = false, completedAt = null)

            // Optimistic update
            inFlightTaskIds.add(task.id)
            updateTaskAcrossLists(task.id) { uncompletedTask }

            // API call
            val result = tasksRepository.uncompleteTask(taskListId, task.id)

            result.onSuccess { serverTask ->
                inFlightTaskIds.remove(task.id)
                updateTaskAcrossLists(task.id) { serverTask }
            }
            result.onFailure { error ->
                inFlightTaskIds.remove(task.id)
                // Revert on failure
                updateTaskAcrossLists(task.id) { task }
                val exception = error as? Exception
                if (exception != null && GoogleTasksRepository.isAuthError(exception)) {
                    attemptReauthAndRetry { performRefresh(false) }
                    return@launch
                }
                _uiState.update { it.copy(error = "Failed to uncomplete task: ${error.message}") }
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            // Save state before removing for local revert on failure
            val snapshotLists = _uiState.value.allTaskLists
            removeTaskAcrossLists(task.id)

            val result = tasksRepository.deleteTask(taskListId, task.id)

            result.onFailure { error ->
                // Re-insert the task by restoring the snapshot
                updateUiWithLists(_uiState.value, normalizeTaskLists(snapshotLists))
                val exception = error as? Exception
                if (exception != null && GoogleTasksRepository.isAuthError(exception)) {
                    attemptReauthAndRetry { performRefresh(false) }
                    return@launch
                }
                _uiState.update { it.copy(error = "Failed to delete task: ${error.message}") }
            }
        }
    }

    /**
     * Toggle task completion
     */
    fun toggleTaskCompletion(task: Task) {
        if (task.isCompleted) {
            uncompleteTask(task)
        } else {
            completeTask(task)
        }
    }

    fun startVoiceInput() {
        voiceParsingCoordinator.cancelParse()
        voiceParsingCoordinator.clearMetadata()
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

    fun confirmVoiceTasks(overrideListId: String? = null) {
        val currentState = voiceCaptureManager.state.value
        if (currentState !is VoiceInputState.Preview) return
        val response = currentState.response

        viewModelScope.launch {
            val defaultListId = _uiState.value.selectedTaskListId
                ?: _uiState.value.taskLists.firstOrNull()?.id

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
                        refresh()
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
                            refresh()
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
                            refresh()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to delete task")
                        }
                    )
                }

                VoiceIntent.RESCHEDULE -> {
                    // Reschedule not yet supported (no updateTask API method)
                    voiceCaptureManager.setError("Rescheduling not yet supported")
                }

                VoiceIntent.QUERY -> {
                    // Query intent: preview shows matching tasks, no action needed
                    voiceParsingCoordinator.clearMetadata()
                    voiceCaptureManager.resetToIdle()
                }

                VoiceIntent.AMEND -> {
                    // Amend: treat as a new ADD with the corrected task
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
                                refresh()
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
        return _uiState.value.allTaskLists
            .firstOrNull { list -> list.tasks.any { it.id == taskId } }
            ?.taskList?.id
    }

    fun dismissVoiceError() {
        voiceParsingCoordinator.clearMetadata()
        voiceCaptureManager.resetToIdle()
    }

    private fun resolveKnownTaskListId(candidateId: String?): String? {
        return candidateId?.takeIf { id ->
            _uiState.value.taskLists.any { list -> list.id == id }
        }
    }

    fun getPendingTasksByList(): List<Pair<String, List<Task>>> {
        return _uiState.value.allTaskLists.mapNotNull { listWithTasks ->
            val pendingTasks = listWithTasks.tasks.filter { !it.isCompleted }
            if (pendingTasks.isEmpty()) null
            else listWithTasks.taskList.title to pendingTasks
        }
    }

    fun completeTaskById(taskId: String) {
        val task = _uiState.value.allTaskLists
            .flatMap { it.tasks }
            .firstOrNull { it.id == taskId }
            ?: return
        completeTask(task)
    }

    fun deleteCalendarEvent(eventId: String, calendarId: String) {
        viewModelScope.launch {
            val result = calendarRepository.deleteEvent(calendarId, eventId)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        val taskId = state.scheduledTaskEventIds.entries.firstOrNull { it.value == eventId }?.key
                        val updatedEventIds = state.scheduledTaskEventIds.toMutableMap()
                        val updatedTimes = state.scheduledTaskTimes.toMutableMap()
                        if (taskId != null) {
                            updatedEventIds.remove(taskId)
                            updatedTimes.remove(taskId)
                        }
                        state.copy(
                            scheduledTaskEventIds = updatedEventIds,
                            scheduledTaskTimes = updatedTimes
                        )
                    }
                    loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
                },
                onFailure = { error ->
                    val message = error.message?.let { ": $it" } ?: ""
                    _uiState.update { it.copy(
                        calendarError = "Failed to delete event${message}"
                    ) }
                }
            )
        }
    }

    fun reschedulePromotedEvent(event: CalendarEvent) {
        val sourceTaskId = event.sourceTaskId ?: return
        val task = _uiState.value.allTaskLists
            .flatMap { it.tasks }
            .firstOrNull { it.id == sourceTaskId }
            ?: return
        openPromotionDraft(task, event.startDateTime)
    }

    fun selectCalendarDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCalendarDate = date) }
            persistSelectedCalendarDate(date)
            loadCalendarRangeInternal(_uiState.value.calendarViewMode, date)
        }
    }

    fun selectCalendar(calendarId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCalendarId = calendarId) }
            loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
        }
    }

    fun loadCalendarForSelectedDate() {
        loadCalendarRange(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
    }

    fun setCalendarViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(calendarViewMode = mode) }
        loadCalendarRange(mode, _uiState.value.selectedCalendarDate)
    }

    private fun loadCalendarRange(mode: CalendarViewMode, anchor: LocalDate) {
        viewModelScope.launch { loadCalendarRangeInternal(mode, anchor) }
    }

    private suspend fun loadCalendarRangeInternal(mode: CalendarViewMode, anchor: LocalDate): Boolean {
        if (_uiState.value.authState !is AuthState.Authenticated) return false
        if (!_uiState.value.hasCalendarScope) return false
        _uiState.update { it.copy(isCalendarLoading = true, calendarError = null) }
        val calendarId = _uiState.value.selectedCalendarId

        val (start, end) = when (mode) {
            CalendarViewMode.MONTH -> {
                val firstOfMonth = anchor.withDayOfMonth(1)
                val gridStart = firstOfMonth.with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)
                )
                val lastOfMonth = firstOfMonth.with(
                    java.time.temporal.TemporalAdjusters.lastDayOfMonth()
                )
                val gridEnd = lastOfMonth.with(
                    java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY)
                )
                Pair(gridStart, gridEnd)
            }
            CalendarViewMode.WEEK -> {
                val today = LocalDate.now()
                Pair(today, today.plusDays(6))
            }
            CalendarViewMode.THREE_DAY -> {
                Pair(anchor.minusDays(1), anchor.plusDays(1))
            }
            CalendarViewMode.DAY -> {
                Pair(anchor, anchor)
            }
        }

        _uiState.update { it.copy(calendarRangeStart = start) }

        return calendarRepository.getEventsForDateRange(start, end, calendarId).fold(
            onSuccess = { grouped ->
                val allEvents = grouped.values.flatten()
                val promotedEventIdsByTask = allEvents
                    .asSequence()
                    .filter { it.isPromotedTask && !it.sourceTaskId.isNullOrBlank() }
                    .associate { it.sourceTaskId!! to it.id }
                val promotedStartTimesByTask = allEvents
                    .asSequence()
                    .filter { it.isPromotedTask && !it.sourceTaskId.isNullOrBlank() }
                    .mapNotNull { event ->
                        val taskId = event.sourceTaskId ?: return@mapNotNull null
                        val startTime = event.startDateTime ?: event.allDayStartDate?.atStartOfDay() ?: return@mapNotNull null
                        taskId to startTime
                    }
                    .toMap()
                _uiState.update { state ->
                    val mergedEventIds = state.scheduledTaskEventIds.toMutableMap().apply {
                        putAll(promotedEventIdsByTask)
                    }
                    val mergedStartTimes = state.scheduledTaskTimes.toMutableMap().apply {
                        putAll(promotedStartTimesByTask)
                    }
                    state.copy(
                        eventsForRange = grouped,
                        scheduledTaskEventIds = mergedEventIds,
                        scheduledTaskTimes = mergedStartTimes,
                        isCalendarLoading = false,
                        calendarError = null
                    )
                }
                true
            },
            onFailure = { error ->
                if (isCalendarForbidden(error)) {
                    handleCalendarForbidden(error)
                } else {
                    _uiState.update { it.copy(
                        isCalendarLoading = false,
                        calendarError = error.message
                    ) }
                }
                false
            }
        )
    }

    fun openPromotionDraft(
        task: Task,
        prefilledStartTime: LocalDateTime? = null,
        prefilledDurationMinutes: Int? = null
    ) {
        if (_uiState.value.authState !is AuthState.Authenticated) return
        if (!_uiState.value.hasCalendarScope) {
            _uiState.update { it.copy(
                calendarError = "Calendar access needed - press Enter to grant"
            ) }
            return
        }

        val currentState = _uiState.value
        val selectedCalendarId = currentState.selectedCalendarId
            .takeIf { id -> currentState.calendars.any { it.id == id && it.isWritable } }
            ?: currentState.calendars.firstOrNull { it.isWritable }?.id
            ?: GoogleCalendarRepository.PRIMARY_CALENDAR_ID

        val defaultStart = prefilledStartTime
            ?: currentState.promotionAnchor?.endsAt
            ?: nextQuarterHour(LocalDateTime.now())

        val duration = prefilledDurationMinutes
            ?: currentState.promotionDraft?.durationMinutes
                ?.takeIf { it in durationOptionsMinutes }
            ?: 30

        _uiState.update { it.copy(
            promotionDraft = PromotionDraft(
                task = task,
                startDateTime = defaultStart,
                durationMinutes = duration,
                calendarId = selectedCalendarId
            )
        ) }
    }

    fun dismissPromotionDraft() {
        _uiState.update { it.copy(promotionDraft = null) }
    }

    fun cyclePromotionDuration(forward: Boolean) {
        _uiState.update { state ->
            val draft = state.promotionDraft ?: return@update state
            val currentIndex = durationOptionsMinutes.indexOf(draft.durationMinutes).takeIf { it >= 0 } ?: 1
            val nextIndex = if (forward) {
                (currentIndex + 1) % durationOptionsMinutes.size
            } else {
                (currentIndex - 1 + durationOptionsMinutes.size) % durationOptionsMinutes.size
            }
            state.copy(
                promotionDraft = draft.copy(durationMinutes = durationOptionsMinutes[nextIndex])
            )
        }
    }

    fun shiftPromotionStartByMinutes(minutes: Long) {
        _uiState.update { state ->
            val draft = state.promotionDraft ?: return@update state
            state.copy(
                promotionDraft = draft.copy(startDateTime = draft.startDateTime.plusMinutes(minutes))
            )
        }
    }

    fun cyclePromotionCalendar(forward: Boolean) {
        _uiState.update { state ->
            val draft = state.promotionDraft ?: return@update state
            val writableCalendars = state.calendars.filter { it.isWritable }
            if (writableCalendars.isEmpty()) return@update state
            val currentIndex = writableCalendars.indexOfFirst { it.id == draft.calendarId }.takeIf { it >= 0 } ?: 0
            val nextIndex = if (forward) {
                (currentIndex + 1) % writableCalendars.size
            } else {
                (currentIndex - 1 + writableCalendars.size) % writableCalendars.size
            }
            val nextCalendarId = writableCalendars[nextIndex].id
            state.copy(
                selectedCalendarId = nextCalendarId,
                promotionDraft = draft.copy(calendarId = nextCalendarId)
            )
        }
    }

    fun setPromotionStartTime(startDateTime: LocalDateTime) {
        _uiState.update { state ->
            val draft = state.promotionDraft ?: return@update state
            state.copy(
                promotionDraft = draft.copy(startDateTime = startDateTime)
            )
        }
    }

    fun confirmPromotion() {
        val draft = _uiState.value.promotionDraft ?: return
        viewModelScope.launch {
            if (_uiState.value.authState !is AuthState.Authenticated) return@launch
            if (!_uiState.value.hasCalendarScope) {
                _uiState.update { it.copy(
                    calendarError = "Calendar access needed - press Enter to grant",
                    promotionDraft = null
                ) }
                return@launch
            }

            _uiState.update { it.copy(isCalendarLoading = true, calendarError = null) }

            val result = calendarRepository.createEvent(
                task = draft.task,
                startDateTime = draft.startDateTime,
                endDateTime = draft.endDateTime,
                calendarId = draft.calendarId
            )

            result.fold(
                onSuccess = { event ->
                    loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
                    _uiState.update { state ->
                        val updatedScheduleMap = state.scheduledTaskEventIds.toMutableMap()
                        updatedScheduleMap[draft.task.id] = event.id
                        val updatedScheduleTimes = state.scheduledTaskTimes.toMutableMap()
                        updatedScheduleTimes[draft.task.id] = draft.startDateTime
                        state.copy(
                            promotionDraft = null,
                            promotionAnchor = PromotionAnchor(
                                previousEventTitle = event.title,
                                endsAt = event.endDateTime ?: draft.endDateTime
                            ),
                            scheduledTaskEventIds = updatedScheduleMap,
                            scheduledTaskTimes = updatedScheduleTimes,
                            promotionSuccessMessage = "Scheduled \"${draft.task.title}\" at ${
                                draft.startDateTime.format(promotionTimeFormatter)
                            }"
                        )
                    }
                    schedulePromotionAnchorExpiry()
                },
                onFailure = { error ->
                    if (isCalendarForbidden(error)) {
                        handleCalendarForbidden(error)
                        return@fold
                    }
                    val message = error.message?.let { ": $it" } ?: ""
                    _uiState.update { it.copy(
                        promotionDraft = null,
                        isCalendarLoading = false,
                        promotionSuccessMessage = null,
                        calendarError = "Failed to create calendar event${message}"
                    ) }
                }
            )
        }
    }

    fun clearPromotionSuccessMessage() {
        _uiState.update { it.copy(promotionSuccessMessage = null) }
    }

    fun clearCalendarError() {
        _uiState.update { it.copy(calendarError = null) }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Sign out the user
     */
    fun signOut() {
        viewModelScope.launch {
            autoSyncJob?.cancel()
            consecutiveSyncFailures.set(0)
            authManager.signOut()
            _uiState.update { TaskWallUiState(authState = AuthState.NotAuthenticated) }
        }
    }

    /**
     * Update sleep schedule and persist to DataStore
     */
    fun updateSleepSchedule(startHour: Int, endHour: Int) {
        _sleepStartHour.value = startHour
        _sleepEndHour.value = endHour
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[sleepStartHourKey] = startHour
                prefs[sleepEndHourKey] = endHour
            }
        }
    }

    /**
     * Update theme mode/light-hour schedule and persist to DataStore
     */
    fun updateThemeSettings(
        mode: ThemeMode,
        lightStart: Int = 8,
        lightEnd: Int = 19
    ) {
        _themeMode.value = mode
        _lightStartHour.value = lightStart
        _lightEndHour.value = lightEnd
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[themeModeKey] = mode.name
                prefs[lightStartHourKey] = lightStart
                prefs[lightEndHourKey] = lightEnd
            }
        }
    }

    /**
     * Update sync interval and persist to DataStore, restarting auto-sync
     */
    fun updateSyncInterval(minutes: Int) {
        _syncIntervalMinutes.value = minutes
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[syncIntervalMinutesKey] = minutes
            }
        }
        // Restart auto-sync with the new interval
        startAutoSync()
    }

    /**
     * Start auto-sync timer
     */
    private fun startAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            while (true) {
                delay(nextAutoSyncDelayMillis())
                if (_uiState.value.authState !is AuthState.Authenticated) continue
                if (!isOnline.value) continue

                performRefresh(showSyncIndicator = false)
            }
        }
    }

    private fun nextAutoSyncDelayMillis(): Long {
        val baseDelay = _syncIntervalMinutes.value * 60_000L
        val backoffMultiplier = 1L shl consecutiveSyncFailures.get().coerceAtMost(3)
        return baseDelay * backoffMultiplier
    }


    private suspend fun loadCalendars() {
        calendarRepository.getCalendars().fold(
            onSuccess = { calendars ->
                val writableCalendars = calendars.filter { it.isWritable }
                _uiState.update { state ->
                    val selected = state.selectedCalendarId
                        .takeIf { id -> writableCalendars.any { it.id == id } }
                        ?: writableCalendars.firstOrNull { it.isPrimary }?.id
                        ?: writableCalendars.firstOrNull()?.id
                        ?: GoogleCalendarRepository.PRIMARY_CALENDAR_ID
                    state.copy(
                        calendars = calendars,
                        selectedCalendarId = selected
                    )
                }
            },
            onFailure = { error ->
                if (isCalendarForbidden(error)) {
                    handleCalendarForbidden(error)
                    return@fold
                }
                val message = error.message?.let { ": $it" } ?: ""
                _uiState.update { it.copy(
                    calendars = emptyList(),
                    selectedCalendarId = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
                    calendarError = "Failed to load calendars$message"
                ) }
            }
        )
    }

    private suspend fun persistSelectedCalendarDate(date: LocalDate) {
        context.dataStore.edit { prefs ->
            prefs[selectedCalendarDateKey] = date.toString()
        }
    }

    private fun parseLocalDate(rawDate: String): LocalDate? {
        return runCatching { LocalDate.parse(rawDate) }.getOrNull()
    }

    private fun schedulePromotionAnchorExpiry() {
        promotionAnchorExpiryJob?.cancel()
        promotionAnchorExpiryJob = viewModelScope.launch {
            delay(PROMOTION_ANCHOR_EXPIRY_MS)
            _uiState.update { it.copy(promotionAnchor = null) }
        }
    }

    private fun nextQuarterHour(base: LocalDateTime): LocalDateTime {
        val truncated = base.withSecond(0).withNano(0)
        val remainder = truncated.minute % 15
        return if (remainder == 0) truncated else truncated.plusMinutes((15 - remainder).toLong())
    }

    private fun isCalendarForbidden(error: Throwable): Boolean {
        return when (error) {
            is GoogleJsonResponseException -> error.statusCode == 403
            else -> {
                val message = error.message.orEmpty()
                "403" in message || "forbidden" in message.lowercase()
            }
        }
    }

    private fun handleCalendarForbidden(error: Throwable) {
        val account = (_uiState.value.authState as? AuthState.Authenticated)?.account
        val hasScopeNow = authManager.hasCalendarScope(account)

        if (!hasScopeNow) {
            _uiState.update { it.copy(
                hasCalendarScope = false,
                calendars = emptyList(),
                eventsForRange = emptyMap(),
                promotionDraft = null,
                isCalendarLoading = false,
                calendarError = "Calendar access needed - press Enter to grant"
            ) }
            return
        }

        val detail = error.message?.takeIf { it.isNotBlank() } ?: "Forbidden"
        _uiState.update { it.copy(
            promotionDraft = null,
            isCalendarLoading = false,
            calendarError = "Calendar API denied access (403). Verify Calendar API is enabled for your OAuth client. $detail"
        ) }
    }

    private fun normalizeTaskLists(taskLists: List<TaskListWithTasks>): List<TaskListWithTasks> {
        return sortTaskListsByNearestDueDate(
            taskLists.map { listWithTasks ->
                listWithTasks.copy(tasks = sortTasksForDisplay(listWithTasks.tasks))
            }
        )
    }

    private fun updateUiWithLists(
        currentState: TaskWallUiState,
        normalizedTaskLists: List<TaskListWithTasks>
    ) {
        val selectedTasks = normalizedTaskLists
            .find { it.taskList.id == currentState.selectedTaskListId }
            ?.tasks
            .orEmpty()

        _uiState.update {
            currentState.copy(
                allTaskLists = normalizedTaskLists,
                taskLists = normalizedTaskLists.map { it.taskList },
                tasks = selectedTasks
            )
        }
    }

    private fun updateTaskAcrossLists(taskId: String, update: (Task) -> Task) {
        val currentState = _uiState.value
        var changed = false
        val updatedLists = currentState.allTaskLists.map { listWithTasks ->
            val taskIndex = listWithTasks.tasks.indexOfFirst { it.id == taskId }
            if (taskIndex < 0) {
                listWithTasks
            } else {
                changed = true
                val updatedTasks = listWithTasks.tasks.toMutableList()
                updatedTasks[taskIndex] = update(updatedTasks[taskIndex])
                // Keep folder order stable during optimistic updates; only re-sort tasks in the touched folder.
                listWithTasks.copy(tasks = sortTasksForDisplay(updatedTasks))
            }
        }

        if (!changed) return

        updateUiWithLists(
            currentState = currentState,
            normalizedTaskLists = updatedLists
        )
    }

    private fun removeTaskAcrossLists(taskId: String) {
        val currentState = _uiState.value
        var changed = false
        val updatedLists = currentState.allTaskLists.map { listWithTasks ->
            val updatedTasks = listWithTasks.tasks.filterNot { task -> task.id == taskId }
            if (updatedTasks.size == listWithTasks.tasks.size) {
                listWithTasks
            } else {
                changed = true
                listWithTasks.copy(tasks = updatedTasks)
            }
        }

        if (!changed) return

        updateUiWithLists(
            currentState = currentState,
            normalizedTaskLists = updatedLists
        )
    }

    override fun onCleared() {
        super.onCleared()
        autoSyncJob?.cancel()
        voiceParsingCoordinator.destroy()
        undoTimeoutJob?.cancel()
        syncFeedbackJob?.cancel()
        promotionAnchorExpiryJob?.cancel()
        voiceCaptureManager.rawResultCallback = null
        voiceCaptureManager.destroy()
        connectivityMonitor.destroy()
    }

    private fun findTaskListIdForTask(taskId: String): String? {
        return _uiState.value.allTaskLists
            .firstOrNull { listWithTasks -> listWithTasks.tasks.any { it.id == taskId } }
            ?.taskList
            ?.id
            ?: _uiState.value.selectedTaskListId
    }

    private fun sortTaskListsByNearestDueDate(taskLists: List<TaskListWithTasks>): List<TaskListWithTasks> {
        data class IndexedList(
            val index: Int,
            val listWithTasks: TaskListWithTasks,
            val nearestPendingDueDate: LocalDate?
        )

        return taskLists
            .withIndex()
            .map { indexed ->
                IndexedList(
                    index = indexed.index,
                    listWithTasks = indexed.value,
                    nearestPendingDueDate = indexed.value.tasks
                        .asSequence()
                        .filter { !it.isCompleted }
                        .mapNotNull { it.dueDate }
                        .minOrNull()
                )
            }
            .sortedWith { first, second ->
                val firstDue = first.nearestPendingDueDate
                val secondDue = second.nearestPendingDueDate

                when {
                    firstDue != null && secondDue != null -> firstDue.compareTo(secondDue)
                    firstDue != null -> -1
                    secondDue != null -> 1
                    else -> {
                        val updatedCompare = second.listWithTasks.taskList.updatedAt
                            .compareTo(first.listWithTasks.taskList.updatedAt)
                        if (updatedCompare != 0) updatedCompare else first.index.compareTo(second.index)
                    }
                }
            }
            .map { it.listWithTasks }
    }

    // ── Gemini key management (for wall-mode settings) ──

    fun validateAndSaveGeminiKey(rawKey: String) {
        val key = rawKey.trim()
        if (key.isBlank()) {
            _geminiKeyError.value = "Gemini key cannot be blank"
            return
        }
        viewModelScope.launch {
            _isValidatingGeminiKey.value = true
            _geminiKeyError.value = null
            geminiCaptureRepository.validateApiKey(key).fold(
                onSuccess = {
                    geminiKeyStore.setApiKey(key)
                    _isValidatingGeminiKey.value = false
                    _geminiKeyPresent.value = true
                    _geminiKeyError.value = null
                },
                onFailure = { error ->
                    _isValidatingGeminiKey.value = false
                    _geminiKeyPresent.value = geminiKeyStore.hasApiKey()
                    _geminiKeyError.value = "Validation failed: ${error.message ?: error.javaClass.simpleName}"
                }
            )
        }
    }

    fun clearGeminiKey() {
        geminiKeyStore.clearApiKey()
        _geminiKeyPresent.value = false
        _geminiKeyError.value = null
    }

    /**
     * Factory for creating the ViewModel with dependencies
     */
    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val authManager = GoogleAuthManager(context)
            val tasksRepository = GoogleTasksRepository(context)
            val calendarRepository = GoogleCalendarRepository(context)
            val geminiKeyStore = GeminiKeyStore(context)
            val weatherKeyStore = WeatherKeyStore(context)
            val geminiCaptureRepository = GeminiCaptureRepository()
            return TaskWallViewModel(
                context = context,
                authManager = authManager,
                tasksRepository = tasksRepository,
                calendarRepository = calendarRepository,
                geminiKeyStore = geminiKeyStore,
                geminiCaptureRepository = geminiCaptureRepository
            ) as T
        }
    }
}
