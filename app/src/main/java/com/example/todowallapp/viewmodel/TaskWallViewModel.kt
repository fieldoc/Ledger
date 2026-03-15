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
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.PromotionAnchor
import com.example.todowallapp.data.model.PromotionDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.buildScheduleMapForDate
import com.example.todowallapp.data.model.sortTasksForDisplay
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.data.repository.WeatherRepository
import com.example.todowallapp.data.repository.dataStore
import com.example.todowallapp.security.FirebaseKeySync
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
data class TaskListWithTasks(
    val taskList: TaskList,
    val tasks: List<Task> = emptyList()
)

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
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val calendarScheduleMap: Map<Int, List<CalendarEvent>> = emptyMap(),
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
    private val firebaseKeySync: FirebaseKeySync? = null
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

    private var autoSyncJob: Job? = null
    private val refreshMutex = Mutex()
    private var consecutiveSyncFailures = 0
    private var isReauthenticating = false
    private var voiceParseJob: Job? = null
    private var parsedVoiceDueDate: LocalDate? = null
    private var parsedVoiceTargetListId: String? = null

    // Last sync success tracking
    private var syncFeedbackJob: Job? = null
    private var promotionAnchorExpiryJob: Job? = null

    private val durationOptionsMinutes = listOf(15, 30, 45, 60, 90, 120)
    private val promotionTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        configureVoiceInputParsing()
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
                    consecutiveSyncFailures = 0
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

            _uiState.value = _uiState.value.copy(
                selectedCalendarDate = selectedCalendarDate
            )
        }
    }

    /**
     * Check if user is already authenticated
     */
    fun checkAuthState() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(authState = AuthState.Loading)

            if (authManager.isSignedIn()) {
                val account = authManager.getCurrentAccount()
                if (account != null) {
                    onSignedIn(account)
                } else {
                    _uiState.value = _uiState.value.copy(authState = AuthState.NotAuthenticated)
                }
            } else {
                // Show sign-in immediately, then try silent sign-in briefly in the background
                _uiState.value = _uiState.value.copy(authState = AuthState.NotAuthenticated)

                val result = try {
                    withTimeout(3_000) { authManager.silentSignIn() }
                } catch (e: TimeoutCancellationException) {
                    Result.failure(e)
                }
                result.fold(
                    onSuccess = { account -> onSignedIn(account) },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(authState = AuthState.NotAuthenticated)
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
            consecutiveSyncFailures = 0
            val currentAccount = authManager.getCurrentAccount()
            val calendarAccount = sequenceOf(account, currentAccount)
                .filterNotNull()
                .firstOrNull { authManager.hasCalendarScope(it) }
            val hasCalendarScope = calendarAccount != null
            _uiState.value = _uiState.value.copy(
                authState = AuthState.Authenticated(account),
                isLoading = true,
                hasCalendarScope = hasCalendarScope,
                calendarError = null
            )

            // Initialize the repository
            tasksRepository.initialize(account)

            // Firebase: authenticate and pull keys from cloud
            firebaseKeySync?.signIn(account)
            val keysUpdated = firebaseKeySync?.pullKeys() ?: false
            if (keysUpdated) {
                Log.d("TaskWallViewModel", "API keys synced from Firebase")
            }

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
                _uiState.value = _uiState.value.copy(
                    calendars = emptyList(),
                    calendarEvents = emptyList(),
                    calendarScheduleMap = emptyMap(),
                    calendarError = "Calendar access needed - press Enter to grant"
                )
            }

            // Start auto-sync
            startAutoSync()
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

                _uiState.value = _uiState.value.copy(
                    taskLists = sortedTaskLists.map { it.taskList },
                    allTaskLists = sortedTaskLists,
                    selectedTaskListId = selectedId,
                    selectedTaskListTitle = selectedList?.title ?: "Tasks",
                    tasks = selectedTasks,
                    isLoading = false
                )
                true
            },
            onFailure = { error ->
                // If this is an auth error, re-throw so performRefresh can attempt re-auth
                if (error is Exception && GoogleTasksRepository.isAuthError(error)) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    throw error
                }
                val message = error.message?.let { ": $it" } ?: ""
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load task lists: ${error.javaClass.simpleName}$message",
                    isLoading = false,
                    lastSyncSuccess = false
                )
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
        _uiState.value = _uiState.value.copy(
            lastSyncTime = LocalDateTime.now(),
            error = errors.firstOrNull(),
            lastSyncSuccess = syncSuccess
        )
        scheduleSyncFeedbackClear()

        return allTasks
    }

    /**
     * Auto-clear sync success indicator after 3 seconds
     */
    private fun scheduleSyncFeedbackClear() {
        syncFeedbackJob?.cancel()
        syncFeedbackJob = viewModelScope.launch {
            delay(3_000)
            // Only clear success feedback; keep failure visible so staleness is apparent
            if (_uiState.value.lastSyncSuccess == true) {
                _uiState.value = _uiState.value.copy(lastSyncSuccess = null)
            }
        }
    }

    /**
     * Attempt silent re-authentication and retry the failed operation.
     * Uses isReauthenticating flag to prevent re-auth loops.
     */
    private suspend fun attemptReauthAndRetry(action: suspend () -> Unit) {
        if (isReauthenticating) return
        isReauthenticating = true
        try {
            Log.d("TaskWallVM", "Auth token expired, attempting silent re-authentication")
            val result = authManager.silentSignIn()
            result.onSuccess { account ->
                Log.d("TaskWallVM", "Silent re-auth succeeded, retrying operation")
                onSignedIn(account)
                consecutiveSyncFailures = 0
                action()
            }.onFailure { error ->
                Log.e("TaskWallVM", "Silent re-auth failed: ${error.message}")
                _uiState.value = _uiState.value.copy(
                    error = "Session expired. Please sign in again.",
                    authState = AuthState.NotAuthenticated
                )
            }
        } finally {
            isReauthenticating = false
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

        if (refreshMutex.isLocked) return true

        if (!isOnline.value) {
            if (showSyncIndicator) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = "Offline: waiting for connection to sync",
                    lastSyncSuccess = false
                )
                scheduleSyncFeedbackClear()
            }
            return false
        }

        return refreshMutex.withLock {
            if (showSyncIndicator) {
                _uiState.value = _uiState.value.copy(isSyncing = true)
            }
            try {
                val selectedTaskListId = _uiState.value.selectedTaskListId
                val selectedCalendarDate = _uiState.value.selectedCalendarDate
                val hasCalendarScope = _uiState.value.hasCalendarScope

                val (tasksLoaded, calendarLoaded) = coroutineScope {
                    val tasksDeferred = async { loadTaskLists(selectedTaskListId) }
                    val calendarDeferred = async {
                        if (hasCalendarScope) {
                            loadCalendarForDateInternal(selectedCalendarDate)
                        } else {
                            true
                        }
                    }
                    tasksDeferred.await() to calendarDeferred.await()
                }

                val syncSuccess = tasksLoaded && calendarLoaded && (_uiState.value.lastSyncSuccess != false)
                consecutiveSyncFailures = if (syncSuccess) {
                    0
                } else {
                    (consecutiveSyncFailures + 1).coerceAtMost(3)
                }
                // Refresh weather (cache handles throttling — only hits API every 3h)
                if (syncSuccess) refreshWeather()
                // Pull API keys from Firebase (no-ops if not configured)
                firebaseKeySync?.pullKeys()
                syncSuccess
            } catch (e: Exception) {
                // Auth errors are re-thrown from loadTaskLists/loadTasksForAllLists
                if (GoogleTasksRepository.isAuthError(e)) {
                    Log.w("TaskWallVM", "Auth error during sync, attempting re-authentication", e)
                    _uiState.value = _uiState.value.copy(isSyncing = false)
                    attemptReauthAndRetry { performRefresh(showSyncIndicator) }
                    return@withLock false
                }
                // Non-auth exceptions: let the finally block clean up
                consecutiveSyncFailures = (consecutiveSyncFailures + 1).coerceAtMost(3)
                false
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
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

            _uiState.value = _uiState.value.copy(
                selectedTaskListId = taskListId,
                selectedTaskListTitle = taskList?.title ?: "Tasks",
                tasks = selectedListWithTasks?.tasks.orEmpty()
            )

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
                delay(5_000)
                _undoState.value = null
            }

            val completedTask = task.copy(isCompleted = true, completedAt = LocalDateTime.now())

            // Optimistic update
            updateTaskAcrossLists(task.id) { completedTask }

            // API call
            val result = tasksRepository.completeTask(taskListId, task.id)

            result.onSuccess { serverTask ->
                updateTaskAcrossLists(task.id) { serverTask }
            }
            result.onFailure { error ->
                // Revert on failure
                updateTaskAcrossLists(task.id) { task }
                _undoState.value = null
                undoTimeoutJob?.cancel()
                _uiState.value = _uiState.value.copy(error = "Failed to complete task: ${error.message}")
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
            updateTaskAcrossLists(task.id) { uncompletedTask }

            // API call
            val result = tasksRepository.uncompleteTask(taskListId, task.id)

            result.onSuccess { serverTask ->
                updateTaskAcrossLists(task.id) { serverTask }
            }
            result.onFailure { error ->
                // Revert on failure
                updateTaskAcrossLists(task.id) { task }
                _uiState.value = _uiState.value.copy(error = "Failed to uncomplete task: ${error.message}")
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            removeTaskAcrossLists(task.id)

            val result = tasksRepository.deleteTask(taskListId, task.id)

            result.onFailure { error ->
                refresh()
                _uiState.value = _uiState.value.copy(error = "Failed to delete task: ${error.message}")
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
        voiceParseJob?.cancel()
        clearVoiceParsingMetadata()
        voiceCaptureManager.startListening()
    }

    fun stopVoiceInput() {
        voiceCaptureManager.stopListening()
    }

    fun cancelVoiceInput() {
        voiceParseJob?.cancel()
        clearVoiceParsingMetadata()
        voiceCaptureManager.cancel()
    }

    fun confirmVoiceTask(targetListId: String? = null) {
        val currentState = voiceCaptureManager.state.value
        if (currentState is VoiceInputState.Preview) {
            viewModelScope.launch {
                val taskListId = resolveKnownTaskListId(targetListId)
                    ?: resolveKnownTaskListId(currentState.targetListId)
                    ?: resolveKnownTaskListId(parsedVoiceTargetListId)
                    ?: _uiState.value.selectedTaskListId
                    ?: _uiState.value.taskLists.firstOrNull()?.id
                    ?: return@launch
                val dueDate = currentState.dueDate ?: parsedVoiceDueDate
                val result = tasksRepository.createTask(
                    taskListId = taskListId,
                    title = currentState.transcribedText,
                    dueDate = dueDate
                )
                result.fold(
                    onSuccess = {
                        clearVoiceParsingMetadata()
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

    fun dismissVoiceError() {
        clearVoiceParsingMetadata()
        voiceCaptureManager.resetToIdle()
    }

    private fun configureVoiceInputParsing() {
        voiceCaptureManager.rawResultCallback = { rawText ->
            handleRawVoiceTranscription(rawText)
        }
    }

    private fun handleRawVoiceTranscription(rawText: String) {
        val normalizedText = rawText.trim()
        if (normalizedText.isEmpty()) {
            voiceCaptureManager.setError("Didn't catch that")
            return
        }

        voiceParseJob?.cancel()
        voiceParseJob = viewModelScope.launch {
            val existingLists = _uiState.value.taskLists.map { list ->
                ExistingListRef(id = list.id, title = list.title)
            }

            val apiKey = geminiKeyStore.getApiKey()
            if (apiKey == null) {
                clearVoiceParsingMetadata()
                voiceCaptureManager.showPreview(transcribedText = normalizedText)
                return@launch
            }

            val parseResult = geminiCaptureRepository.parseVoiceInput(
                apiKey = apiKey,
                rawText = normalizedText,
                existingLists = existingLists,
                todayDate = LocalDate.now()
            )

            parseResult.fold(
                onSuccess = { parsed ->
                    parsedVoiceDueDate = parsed.dueDate
                    parsedVoiceTargetListId = resolveKnownTaskListId(parsed.targetListId)
                    voiceCaptureManager.showPreview(
                        transcribedText = parsed.title.ifBlank { normalizedText },
                        dueDate = parsedVoiceDueDate,
                        targetListId = parsedVoiceTargetListId
                    )
                },
                onFailure = {
                    clearVoiceParsingMetadata()
                    voiceCaptureManager.showPreview(transcribedText = normalizedText)
                }
            )
        }
    }

    private fun clearVoiceParsingMetadata() {
        parsedVoiceDueDate = null
        parsedVoiceTargetListId = null
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
                    val state = _uiState.value
                    val taskId = state.scheduledTaskEventIds.entries.firstOrNull { it.value == eventId }?.key
                    val updatedEventIds = state.scheduledTaskEventIds.toMutableMap()
                    val updatedTimes = state.scheduledTaskTimes.toMutableMap()
                    if (taskId != null) {
                        updatedEventIds.remove(taskId)
                        updatedTimes.remove(taskId)
                    }
                    _uiState.value = state.copy(
                        scheduledTaskEventIds = updatedEventIds,
                        scheduledTaskTimes = updatedTimes
                    )
                    loadCalendarForDateInternal(_uiState.value.selectedCalendarDate)
                },
                onFailure = { error ->
                    val message = error.message?.let { ": $it" } ?: ""
                    _uiState.value = _uiState.value.copy(
                        calendarError = "Failed to delete event${message}"
                    )
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
            _uiState.value = _uiState.value.copy(selectedCalendarDate = date)
            persistSelectedCalendarDate(date)
            val mode = _uiState.value.calendarViewMode
            when (mode) {
                CalendarViewMode.DAY -> loadCalendarForDateInternal(date)
                CalendarViewMode.THREE_DAY -> {
                    loadCalendarForDateInternal(date)
                    loadCalendarRange(mode, date)
                }
                else -> loadCalendarRange(mode, date)
            }
        }
    }

    fun selectCalendar(calendarId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedCalendarId = calendarId)
            val mode = _uiState.value.calendarViewMode
            when (mode) {
                CalendarViewMode.DAY -> loadCalendarForDateInternal(_uiState.value.selectedCalendarDate)
                CalendarViewMode.THREE_DAY -> {
                    loadCalendarForDateInternal(_uiState.value.selectedCalendarDate)
                    loadCalendarRange(mode, _uiState.value.selectedCalendarDate)
                }
                else -> loadCalendarRange(mode, _uiState.value.selectedCalendarDate)
            }
        }
    }

    fun loadCalendarForSelectedDate() {
        viewModelScope.launch {
            loadCalendarForDateInternal(_uiState.value.selectedCalendarDate)
        }
    }

    fun setCalendarViewMode(mode: CalendarViewMode) {
        _uiState.value = _uiState.value.copy(calendarViewMode = mode)
        loadCalendarRange(mode, _uiState.value.selectedCalendarDate)
    }

    private fun loadCalendarRange(mode: CalendarViewMode, anchor: LocalDate) {
        if (mode == CalendarViewMode.DAY) return  // DAY uses existing per-date flow

        viewModelScope.launch {
            if (!_uiState.value.hasCalendarScope) return@launch
            _uiState.value = _uiState.value.copy(isCalendarLoading = true, calendarError = null)
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
                else -> return@launch
            }

            _uiState.value = _uiState.value.copy(calendarRangeStart = start)

            calendarRepository.getEventsForDateRange(start, end, calendarId)
                .onSuccess { grouped ->
                    _uiState.value = _uiState.value.copy(
                        eventsForRange = grouped,
                        isCalendarLoading = false,
                        calendarError = null
                    )
                }
                .onFailure { error ->
                    if (isCalendarForbidden(error)) {
                        handleCalendarForbidden(error)
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isCalendarLoading = false,
                            calendarError = error.message
                        )
                    }
                }
        }
    }

    fun openPromotionDraft(
        task: Task,
        prefilledStartTime: LocalDateTime? = null,
        prefilledDurationMinutes: Int? = null
    ) {
        if (_uiState.value.authState !is AuthState.Authenticated) return
        if (!_uiState.value.hasCalendarScope) {
            _uiState.value = _uiState.value.copy(
                calendarError = "Calendar access needed - press Enter to grant"
            )
            return
        }

        val selectedCalendarId = _uiState.value.selectedCalendarId
            .takeIf { id -> _uiState.value.calendars.any { it.id == id && it.isWritable } }
            ?: _uiState.value.calendars.firstOrNull { it.isWritable }?.id
            ?: GoogleCalendarRepository.PRIMARY_CALENDAR_ID

        val defaultStart = prefilledStartTime
            ?: _uiState.value.promotionAnchor?.endsAt
            ?: nextQuarterHour(LocalDateTime.now())

        val duration = prefilledDurationMinutes
            ?: _uiState.value.promotionDraft?.durationMinutes
                ?.takeIf { it in durationOptionsMinutes }
            ?: 30

        _uiState.value = _uiState.value.copy(
            promotionDraft = PromotionDraft(
                task = task,
                startDateTime = defaultStart,
                durationMinutes = duration,
                calendarId = selectedCalendarId
            )
        )
    }

    fun dismissPromotionDraft() {
        _uiState.value = _uiState.value.copy(promotionDraft = null)
    }

    fun cyclePromotionDuration(forward: Boolean) {
        val draft = _uiState.value.promotionDraft ?: return
        val currentIndex = durationOptionsMinutes.indexOf(draft.durationMinutes).takeIf { it >= 0 } ?: 1
        val nextIndex = if (forward) {
            (currentIndex + 1) % durationOptionsMinutes.size
        } else {
            (currentIndex - 1 + durationOptionsMinutes.size) % durationOptionsMinutes.size
        }
        _uiState.value = _uiState.value.copy(
            promotionDraft = draft.copy(durationMinutes = durationOptionsMinutes[nextIndex])
        )
    }

    fun shiftPromotionStartByMinutes(minutes: Long) {
        val draft = _uiState.value.promotionDraft ?: return
        _uiState.value = _uiState.value.copy(
            promotionDraft = draft.copy(startDateTime = draft.startDateTime.plusMinutes(minutes))
        )
    }

    fun cyclePromotionCalendar(forward: Boolean) {
        val draft = _uiState.value.promotionDraft ?: return
        val writableCalendars = _uiState.value.calendars.filter { it.isWritable }
        if (writableCalendars.isEmpty()) return
        val currentIndex = writableCalendars.indexOfFirst { it.id == draft.calendarId }.takeIf { it >= 0 } ?: 0
        val nextIndex = if (forward) {
            (currentIndex + 1) % writableCalendars.size
        } else {
            (currentIndex - 1 + writableCalendars.size) % writableCalendars.size
        }
        val nextCalendarId = writableCalendars[nextIndex].id
        _uiState.value = _uiState.value.copy(
            selectedCalendarId = nextCalendarId,
            promotionDraft = draft.copy(calendarId = nextCalendarId)
        )
    }

    fun setPromotionStartTime(startDateTime: LocalDateTime) {
        val draft = _uiState.value.promotionDraft ?: return
        _uiState.value = _uiState.value.copy(
            promotionDraft = draft.copy(startDateTime = startDateTime)
        )
    }

    fun confirmPromotion() {
        val draft = _uiState.value.promotionDraft ?: return
        viewModelScope.launch {
            if (_uiState.value.authState !is AuthState.Authenticated) return@launch
            if (!_uiState.value.hasCalendarScope) {
                _uiState.value = _uiState.value.copy(
                    calendarError = "Calendar access needed - press Enter to grant",
                    promotionDraft = null
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(isCalendarLoading = true, calendarError = null)

            val result = calendarRepository.createEvent(
                task = draft.task,
                startDateTime = draft.startDateTime,
                endDateTime = draft.endDateTime,
                calendarId = draft.calendarId
            )

            result.fold(
                onSuccess = { event ->
                    val updatedScheduleMap = _uiState.value.scheduledTaskEventIds.toMutableMap()
                    updatedScheduleMap[draft.task.id] = event.id
                    val updatedScheduleTimes = _uiState.value.scheduledTaskTimes.toMutableMap()
                    updatedScheduleTimes[draft.task.id] = draft.startDateTime
                    loadCalendarForDateInternal(_uiState.value.selectedCalendarDate)
                    _uiState.value = _uiState.value.copy(
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
                    schedulePromotionAnchorExpiry()
                },
                onFailure = { error ->
                    if (isCalendarForbidden(error)) {
                        handleCalendarForbidden(error)
                        return@fold
                    }
                    val message = error.message?.let { ": $it" } ?: ""
                    _uiState.value = _uiState.value.copy(
                        promotionDraft = null,
                        isCalendarLoading = false,
                        promotionSuccessMessage = null,
                        calendarError = "Failed to create calendar event${message}"
                    )
                }
            )
        }
    }

    fun clearPromotionSuccessMessage() {
        _uiState.value = _uiState.value.copy(promotionSuccessMessage = null)
    }

    fun clearCalendarError() {
        _uiState.value = _uiState.value.copy(calendarError = null)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Sign out the user
     */
    fun signOut() {
        viewModelScope.launch {
            autoSyncJob?.cancel()
            consecutiveSyncFailures = 0
            authManager.signOut()
            _uiState.value = TaskWallUiState(authState = AuthState.NotAuthenticated)
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
        val backoffMultiplier = 1L shl consecutiveSyncFailures.coerceAtMost(3)
        return baseDelay * backoffMultiplier
    }

    private suspend fun loadCalendarForDateInternal(date: LocalDate): Boolean {
        if (_uiState.value.authState !is AuthState.Authenticated) return false
        if (!_uiState.value.hasCalendarScope) {
            _uiState.value = _uiState.value.copy(
                selectedCalendarDate = date,
                calendars = emptyList(),
                calendarEvents = emptyList(),
                calendarScheduleMap = emptyMap(),
                isCalendarLoading = false,
                calendarError = "Calendar access needed - press Enter to grant"
            )
            return false
        }

        _uiState.value = _uiState.value.copy(
            selectedCalendarDate = date,
            isCalendarLoading = true,
            calendarError = null
        )

        return calendarRepository.getEventsForDate(
            date = date,
            calendarId = _uiState.value.selectedCalendarId
        ).fold(
            onSuccess = { events ->
                val promotedEventIdsByTask = events
                    .asSequence()
                    .filter { it.isPromotedTask && !it.sourceTaskId.isNullOrBlank() }
                    .associate { event -> event.sourceTaskId!! to event.id }
                val promotedStartTimesByTask = events
                    .asSequence()
                    .filter { it.isPromotedTask && !it.sourceTaskId.isNullOrBlank() }
                    .mapNotNull { event ->
                        val taskId = event.sourceTaskId ?: return@mapNotNull null
                        val start = event.startDateTime ?: event.allDayStartDate?.atStartOfDay() ?: return@mapNotNull null
                        taskId to start
                    }
                    .toMap()
                val mergedEventIds = _uiState.value.scheduledTaskEventIds.toMutableMap().apply {
                    putAll(promotedEventIdsByTask)
                }
                val mergedStartTimes = _uiState.value.scheduledTaskTimes.toMutableMap().apply {
                    putAll(promotedStartTimesByTask)
                }
                _uiState.value = _uiState.value.copy(
                    calendarEvents = events,
                    calendarScheduleMap = buildScheduleMapForDate(events, date),
                    scheduledTaskEventIds = mergedEventIds,
                    scheduledTaskTimes = mergedStartTimes,
                    isCalendarLoading = false,
                    calendarError = null
                )
                true
            },
            onFailure = { error ->
                if (isCalendarForbidden(error)) {
                    handleCalendarForbidden(error)
                    return@fold false
                }
                val message = error.message?.let { ": $it" } ?: ""
                _uiState.value = _uiState.value.copy(
                    calendarEvents = emptyList(),
                    calendarScheduleMap = emptyMap(),
                    isCalendarLoading = false,
                    calendarError = "Failed to load calendar${message}"
                )
                false
            }
        )
    }

    private suspend fun loadCalendars() {
        calendarRepository.getCalendars().fold(
            onSuccess = { calendars ->
                val writableCalendars = calendars.filter { it.isWritable }
                val selected = _uiState.value.selectedCalendarId
                    .takeIf { id -> writableCalendars.any { it.id == id } }
                    ?: writableCalendars.firstOrNull { it.isPrimary }?.id
                    ?: writableCalendars.firstOrNull()?.id
                    ?: GoogleCalendarRepository.PRIMARY_CALENDAR_ID
                _uiState.value = _uiState.value.copy(
                    calendars = calendars,
                    selectedCalendarId = selected
                )
            },
            onFailure = { error ->
                if (isCalendarForbidden(error)) {
                    handleCalendarForbidden(error)
                    return@fold
                }
                val message = error.message?.let { ": $it" } ?: ""
                _uiState.value = _uiState.value.copy(
                    calendars = emptyList(),
                    selectedCalendarId = GoogleCalendarRepository.PRIMARY_CALENDAR_ID,
                    calendarError = "Failed to load calendars$message"
                )
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
            delay(2 * 60 * 60 * 1000L)
            _uiState.value = _uiState.value.copy(promotionAnchor = null)
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
            _uiState.value = _uiState.value.copy(
                hasCalendarScope = false,
                calendars = emptyList(),
                calendarEvents = emptyList(),
                calendarScheduleMap = emptyMap(),
                promotionDraft = null,
                isCalendarLoading = false,
                calendarError = "Calendar access needed - press Enter to grant"
            )
            return
        }

        val detail = error.message?.takeIf { it.isNotBlank() } ?: "Forbidden"
        _uiState.value = _uiState.value.copy(
            promotionDraft = null,
            isCalendarLoading = false,
            calendarError = "Calendar API denied access (403). Verify Calendar API is enabled for your OAuth client. $detail"
        )
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

        _uiState.value = currentState.copy(
            allTaskLists = normalizedTaskLists,
            taskLists = normalizedTaskLists.map { it.taskList },
            tasks = selectedTasks
        )
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
        voiceParseJob?.cancel()
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
            val firebaseKeySync = FirebaseKeySync(geminiKeyStore, weatherKeyStore)
            return TaskWallViewModel(
                context = context,
                authManager = authManager,
                tasksRepository = tasksRepository,
                calendarRepository = calendarRepository,
                geminiKeyStore = geminiKeyStore,
                geminiCaptureRepository = geminiCaptureRepository,
                firebaseKeySync = firebaseKeySync
            ) as T
        }
    }
}
