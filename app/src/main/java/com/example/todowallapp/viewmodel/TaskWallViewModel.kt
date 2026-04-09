package com.example.todowallapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.todowallapp.auth.AuthState
import com.example.todowallapp.auth.GoogleAuthManager
import com.example.todowallapp.capture.DayOrganizerCoordinator
import com.example.todowallapp.capture.DayOrganizerState
import com.example.todowallapp.capture.RescheduleRetryContext
import com.example.todowallapp.capture.VoiceParsingCoordinator
import com.example.todowallapp.capture.router.VoiceIntentRouter
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.ExistingTaskRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.ParsedVoiceResponse
import com.example.todowallapp.capture.repository.VoiceIntent
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.data.model.EnergyProfile
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.PromotionAnchor
import com.example.todowallapp.data.model.PromotionDraft
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskList
import com.example.todowallapp.data.model.TaskListWithTasks
import com.example.todowallapp.data.model.TaskFilter
import com.example.todowallapp.data.model.TaskMetadata
import com.example.todowallapp.data.model.TaskPriority
import com.example.todowallapp.data.model.RecurrenceRule
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val scheduledTaskEventIds: Map<String, String> = emptyMap(),
    val scheduledTaskTimes: Map<String, LocalDateTime> = emptyMap(),
    val promotionSuccessMessage: String? = null,
    val error: String? = null,
    val lastSyncTime: LocalDateTime? = null,
    val lastSyncSuccess: Boolean? = null,
    val calendarViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val eventsForRange: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    val transientMessage: String? = null,
    // Search & filter
    val searchQuery: String? = null,
    val activeFilters: Set<TaskFilter> = emptySet(),
    val isSearchActive: Boolean = false,
    // Reorder mode
    val reorderModeTaskId: String? = null
)

enum class UndoAction { COMPLETE, DELETE }

data class UndoState(
    val task: Task,
    val taskListId: String,
    val action: UndoAction = UndoAction.COMPLETE
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

    // Plan Day hint
    private val _hasSeenPlanDayHint = MutableStateFlow(true) // default true, DataStore overrides to false if unseen
    val hasSeenPlanDayHint: StateFlow<Boolean> = _hasSeenPlanDayHint.asStateFlow()

    // Gemini grounding setting
    private val _geminiGroundingEnabled = MutableStateFlow(false)
    val geminiGroundingEnabled: StateFlow<Boolean> = _geminiGroundingEnabled.asStateFlow()

    private val _lastGroundingLatencyMs = MutableStateFlow<Long?>(null)
    val lastGroundingLatencyMs: StateFlow<Long?> = _lastGroundingLatencyMs.asStateFlow()

    // Gemini key state (for wall-mode settings)
    private val _geminiKeyPresent = MutableStateFlow(geminiKeyStore.hasApiKey())
    val geminiKeyPresent: StateFlow<Boolean> = _geminiKeyPresent.asStateFlow()

    // Plan undo state
    data class PlanUndoState(val eventCount: Int, val eventIds: List<String>)
    private val _planUndoState = MutableStateFlow<PlanUndoState?>(null)
    val planUndoState: StateFlow<PlanUndoState?> = _planUndoState.asStateFlow()
    private var planUndoTimeoutJob: Job? = null

    // Recently created event IDs (for highlight animation)
    private val _recentlyCreatedEventIds = MutableStateFlow<Set<String>>(emptySet())
    val recentlyCreatedEventIds: StateFlow<Set<String>> = _recentlyCreatedEventIds.asStateFlow()

    // Energy profile setting
    private val _energyProfile = MutableStateFlow(EnergyProfile.BALANCED)
    val energyProfile: StateFlow<EnergyProfile> = _energyProfile.asStateFlow()

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
    private val hasSeenPlanDayHintKey = booleanPreferencesKey("has_seen_plan_day_hint")
    private val geminiGroundingEnabledKey = booleanPreferencesKey("gemini_grounding_enabled")
    private val energyProfileKey = stringPreferencesKey("energy_profile")

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
    /** When true, the next voice result gets routed through VoiceIntentRouter before
     *  forwarding to either VoiceParsingCoordinator or DayOrganizerCoordinator.
     *  Reset to false after routing completes. Accessed only on main thread. */
    private var unifiedVoiceRouting = false

    private val voiceParsingCoordinator = VoiceParsingCoordinator(
        voiceCaptureManager, geminiCaptureRepository, geminiKeyStore
    )

    private val dayOrganizerCoordinator by lazy {
        DayOrganizerCoordinator(
            voiceCaptureManager = voiceCaptureManager,
            geminiCaptureRepository = geminiCaptureRepository,
            geminiKeyStore = geminiKeyStore,
            calendarRepository = calendarRepository,
            tasksRepository = tasksRepository
        )
    }

    val dayOrganizerState: StateFlow<DayOrganizerState>
        get() = dayOrganizerCoordinator.state

    // Last sync success tracking
    private var syncFeedbackJob: Job? = null
    private var promotionAnchorExpiryJob: Job? = null
    private var _promotionAnchor: PromotionAnchor? = null

    private val durationOptionsMinutes = listOf(15, 30, 45, 60, 90, 120)
    private val promotionTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        geminiCaptureRepository.latencyCallback = { tag, ms ->
            if (tag == "grounding") {
                _lastGroundingLatencyMs.value = ms
            }
            android.util.Log.d("GeminiLatency", "[$tag] ${ms}ms")
        }
        // Configure voice parsing with a routing-aware callback wrapper.
        // When unifiedVoiceRouting is true, the callback classifies intent first
        // and may redirect to the day organizer instead of task parsing.
        val listProvider = {
            _uiState.value.taskLists.map { list ->
                ExistingListRef(id = list.id, title = list.title)
            }
        }
        val taskProvider = {
            _uiState.value.allTaskLists.flatMap { list ->
                list.tasks.filter { it.parentId == null && !it.isCompleted }.map {
                    ExistingTaskRef(id = it.id, title = it.title, listId = list.taskList.id)
                }
            }.take(30)
        }
        voiceParsingCoordinator.configure(
            scope = viewModelScope,
            listProvider = listProvider,
            taskProvider = taskProvider,
            listIdValidator = ::resolveKnownTaskListId
        )

        // Wrap the rawResultCallback set by configure() with intent routing
        val taskVoiceCallback = voiceCaptureManager.rawResultCallback
        voiceCaptureManager.rawResultCallback = { rawText ->
            if (unifiedVoiceRouting) {
                unifiedVoiceRouting = false
                val routed = VoiceIntentRouter.classifyIntent(rawText)
                when (routed) {
                    is VoiceIntentRouter.RoutedIntent.TaskAction -> {
                        taskVoiceCallback?.invoke(rawText)
                    }
                    is VoiceIntentRouter.RoutedIntent.DayPlanning -> {
                        voiceCaptureManager.resetToIdle()
                        routeToDayOrganizer()
                    }
                }
            } else {
                taskVoiceCallback?.invoke(rawText)
            }
        }
        loadSettings()
        checkAuthState()
        observeConnectivity()
        observeVoiceState()
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
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
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
            _hasSeenPlanDayHint.value = prefs[hasSeenPlanDayHintKey] ?: false
            prefs[geminiGroundingEnabledKey]?.let { _geminiGroundingEnabled.value = it }
            _energyProfile.value = prefs[energyProfileKey]
                ?.let { saved -> EnergyProfile.entries.firstOrNull { it.name == saved } }
                ?: EnergyProfile.BALANCED

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
                if (task.recurrenceRule != null) {
                    spawnNextRecurrence(task, taskListId)
                }
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
     * Spawn a fresh recurring task instance after the previous one is completed.
     * Uses LocalDate.now() as the 'from' date so the next due date rolls forward
     * from today regardless of when the task was originally due.
     */
    private suspend fun spawnNextRecurrence(completedTask: Task, taskListId: String) {
        val rule = completedTask.recurrenceRule ?: return
        val nextDue = rule.nextDueDate(LocalDate.now())
        val encodedNotes = TaskMetadata.encode(completedTask.cleanNotes, rule, completedTask.priority)
        tasksRepository.createTask(
            taskListId = taskListId,
            title = completedTask.title,
            dueDate = nextDue,
            parentId = completedTask.parentId,
            notes = encodedNotes
        ).onFailure { error ->
            Log.w("TaskWallVM", "Failed to spawn recurrence for '${completedTask.title}': ${error.message}")
            _uiState.update { it.copy(error = "Recurring task created but next instance failed: ${error.message}") }
        }
        performRefresh(showSyncIndicator = false)
    }

    /**
     * Undo the last task completion
     */
    fun undoCompletion() {
        val undo = _undoState.value ?: return
        undoTimeoutJob?.cancel()
        _undoState.value = null
        when (undo.action) {
            UndoAction.COMPLETE -> uncompleteTask(undo.task)
            UndoAction.DELETE -> {
                // Re-create the deleted task
                viewModelScope.launch {
                    val encodedNotes = TaskMetadata.encode(
                        undo.task.cleanNotes,
                        undo.task.recurrenceRule,
                        undo.task.priority
                    )
                    tasksRepository.createTask(
                        taskListId = undo.taskListId,
                        title = undo.task.title,
                        dueDate = undo.task.dueDate,
                        parentId = undo.task.parentId,
                        notes = encodedNotes.ifEmpty { null }
                    ).onSuccess { refresh() }
                }
            }
        }
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
        if (!isOnline.value) {
            setTransientMessage("No internet connection — voice input requires network access.")
            return
        }
        voiceParsingCoordinator.cancelParse()
        voiceParsingCoordinator.clearMetadata()
        voiceCaptureManager.startListening()
    }

    /**
     * Unified voice capture: starts listening, then routes the transcription
     * to either task voice (ADD/COMPLETE/etc.) or day planning based on what
     * the user said. This is the single entry point for all mic buttons.
     */
    fun startUnifiedVoiceCapture() {
        if (!isOnline.value) {
            setTransientMessage("No internet connection — voice input requires network access.")
            return
        }
        voiceParsingCoordinator.cancelParse()
        voiceParsingCoordinator.clearMetadata()
        unifiedVoiceRouting = true
        voiceCaptureManager.startListening()
    }

    private fun routeToDayOrganizer() {
        if (!_uiState.value.hasCalendarScope) {
            setTransientMessage("Day planning requires calendar access. Grant permission in Settings.")
            return
        }
        if (!geminiKeyStore.hasApiKey()) {
            setTransientMessage("Day planning requires a Gemini API key. Add one in Settings.")
            return
        }

        // Enter day planner listening mode — the trigger phrase ("plan my day")
        // was just a mode switch, not content. The user will now dictate their
        // actual tasks/durations for Gemini to schedule.
        dayOrganizerCoordinator.startListening(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { list ->
                    ExistingListRef(id = list.id, title = list.title)
                }
            },
            taskProvider = {
                _uiState.value.allTaskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map { task ->
                        ExistingTaskRef(
                            id = task.id,
                            title = task.title,
                            listId = list.taskList.id,
                            listTitle = list.taskList.title,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            preferredTime = task.preferredTime,
                            recurrenceInfo = task.recurrenceRule?.toHumanReadable()
                        )
                    }
                }.take(40)
            },
            eventsProvider = {
                val today = java.time.LocalDate.now()
                val events = calendarRepository.getEventsForDateRange(
                    today, today, _uiState.value.selectedCalendarId
                ).getOrElse { emptyMap() }

                events[today]?.map { event ->
                    if (event.isAllDay) {
                        "All day ${event.title} (all-day)"
                    } else {
                        val start = event.startDateTime?.toLocalTime()
                        val end = event.endDateTime?.toLocalTime()
                        val timeRange = "${start?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}-${end?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}"
                        val durationMin = if (start != null && end != null) {
                            java.time.Duration.between(start, end).toMinutes()
                        } else null
                        val durationSuffix = durationMin?.let { " (${it}min)" } ?: ""
                        "$timeRange ${event.title}$durationSuffix"
                    }
                } ?: emptyList()
            },
            selectedCalendarId = _uiState.value.selectedCalendarId,
            weatherProvider = {
                val todayWeather = _weatherForecast.value[java.time.LocalDate.now()]
                todayWeather?.name
            },
            wakeHour = _sleepEndHour.value,
            sleepHour = _sleepStartHour.value,
            focusedListTitle = _uiState.value.selectedTaskListTitle.takeIf {
                _uiState.value.selectedTaskListId != null
            }
        )
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
                    // Cache of newly created lists: newListName → created list ID
                    val createdListCache = mutableMapOf<String, String>()
                    // Cache of created parent tasks: sharedParentName → created task ID
                    val createdParentCache = mutableMapOf<String, String>()

                    for (task in response.tasks) {
                        if (task.title.isBlank()) continue

                        val listId = resolveKnownTaskListId(overrideListId)
                            ?: resolveKnownTaskListId(task.targetListId)
                            ?: task.newListName?.let { name ->
                                // Reuse already-created list if same name appeared earlier
                                createdListCache[name] ?: run {
                                    tasksRepository.createTaskList(name).fold(
                                        onSuccess = { created ->
                                            createdListCache[name] = created.id
                                            created.id
                                        },
                                        onFailure = {
                                            anyFailed = true
                                            null
                                        }
                                    )
                                }
                            }
                            ?: defaultListId
                            ?: continue

                        // Resolve parent ID: explicit parentTaskId, or sharedParentName grouping
                        val resolvedParentId = task.parentTaskId
                            ?: task.sharedParentName?.let { parentName ->
                                createdParentCache[parentName] ?: run {
                                    // Check if parent already exists in this list
                                    val existingParent = _uiState.value.allTaskLists
                                        .firstOrNull { it.taskList.id == listId }
                                        ?.tasks
                                        ?.firstOrNull { it.title.equals(parentName, ignoreCase = true) && !it.isCompleted }
                                    if (existingParent != null) {
                                        createdParentCache[parentName] = existingParent.id
                                        existingParent.id
                                    } else {
                                        // Create the parent task first
                                        tasksRepository.createTask(
                                            taskListId = listId,
                                            title = parentName
                                        ).fold(
                                            onSuccess = { created ->
                                                createdParentCache[parentName] = created.id
                                                created.id
                                            },
                                            onFailure = {
                                                anyFailed = true
                                                null
                                            }
                                        )
                                    }
                                }
                            }

                        val encodedNotes = TaskMetadata.encode(
                            null, task.recurrenceRule, task.priority,
                            task.preferredTime?.name
                        )
                        val result = tasksRepository.createTask(
                            taskListId = listId,
                            title = task.title,
                            dueDate = task.dueDate,
                            parentId = resolvedParentId,
                            notes = encodedNotes.ifEmpty { null }
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
                    // Find the actual Task object for undo support
                    val actualTask = _uiState.value.allTaskLists
                        .flatMap { it.tasks }
                        .firstOrNull { it.id == targetId }
                    tasksRepository.completeTask(targetListId, targetId).fold(
                        onSuccess = {
                            voiceParsingCoordinator.clearMetadata()
                            voiceCaptureManager.resetToIdle()
                            // Set undo state like encoder completion does
                            if (actualTask != null) {
                                _undoState.value = UndoState(
                                    task = actualTask,
                                    taskListId = targetListId,
                                    action = UndoAction.COMPLETE
                                )
                                undoTimeoutJob?.cancel()
                                undoTimeoutJob = viewModelScope.launch {
                                    delay(UNDO_TIMEOUT_MS)
                                    _undoState.value = null
                                }
                            }
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
                    // Find the actual Task object for undo support
                    val actualTask = _uiState.value.allTaskLists
                        .flatMap { it.tasks }
                        .firstOrNull { it.id == targetId }
                    tasksRepository.deleteTask(targetListId, targetId).fold(
                        onSuccess = {
                            voiceParsingCoordinator.clearMetadata()
                            voiceCaptureManager.resetToIdle()
                            // Set undo state so deletion can be reversed
                            if (actualTask != null) {
                                _undoState.value = UndoState(
                                    task = actualTask,
                                    taskListId = targetListId,
                                    action = UndoAction.DELETE
                                )
                                undoTimeoutJob?.cancel()
                                undoTimeoutJob = viewModelScope.launch {
                                    delay(UNDO_TIMEOUT_MS)
                                    _undoState.value = null
                                }
                            }
                            refresh()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to delete task")
                        }
                    )
                }

                VoiceIntent.RESCHEDULE -> {
                    val task = response.tasks.firstOrNull() ?: run {
                        voiceCaptureManager.setError("Couldn't find that task")
                        return@launch
                    }
                    val targetId = task.parentTaskId ?: run {
                        voiceCaptureManager.setError("Couldn't identify which task to reschedule")
                        return@launch
                    }
                    val targetListId = findTaskListId(targetId) ?: run {
                        voiceCaptureManager.setError("Couldn't find that task in your lists")
                        return@launch
                    }
                    voiceParsingCoordinator.clearMetadata()
                    voiceCaptureManager.resetToIdle()
                    tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
                        onSuccess = {
                            val dateLabel = if (task.dueDate == null) "cleared"
                                           else task.dueDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d"))
                            setTransientMessage("Moved to $dateLabel")
                            refresh()
                        },
                        onFailure = {
                            voiceCaptureManager.setError("Failed to reschedule task")
                        }
                    )
                }

                VoiceIntent.QUERY -> {
                    voiceParsingCoordinator.clearMetadata()
                    voiceCaptureManager.resetToIdle()
                    // Build a summary of pending tasks for the user
                    val allPending = _uiState.value.allTaskLists
                        .flatMap { it.tasks }
                        .filter { !it.isCompleted }
                    val today = LocalDate.now()
                    val overdue = allPending.count { it.getUrgencyLevel(today) == com.example.todowallapp.data.model.TaskUrgency.OVERDUE }
                    val dueToday = allPending.count { it.getUrgencyLevel(today) == com.example.todowallapp.data.model.TaskUrgency.DUE_TODAY }
                    val nextDue = allPending
                        .filter { it.dueDate != null }
                        .minByOrNull { it.dueDate!! }
                    val summary = buildString {
                        append("${allPending.size} pending task${if (allPending.size != 1) "s" else ""}")
                        if (overdue > 0) append(" \u00B7 $overdue overdue")
                        if (dueToday > 0) append(" \u00B7 $dueToday due today")
                        if (nextDue != null) {
                            append(" \u00B7 Next: ${nextDue.title}")
                        }
                    }
                    setTransientMessage(summary)
                }

                VoiceIntent.AMEND -> {
                    // Amend: treat as a new ADD with the corrected task
                    val task = response.tasks.firstOrNull()
                    if (task != null && task.title.isNotBlank()) {
                        val listId = resolveKnownTaskListId(overrideListId)
                            ?: resolveKnownTaskListId(task.targetListId)
                            ?: task.newListName?.let { name ->
                                tasksRepository.createTaskList(name).getOrNull()?.id
                            }
                            ?: defaultListId
                            ?: return@launch
                        val encodedNotes = TaskMetadata.encode(
                            null, task.recurrenceRule, task.priority,
                            task.preferredTime?.name
                        )
                        tasksRepository.createTask(
                            taskListId = listId,
                            title = task.title,
                            dueDate = task.dueDate,
                            parentId = task.parentTaskId,
                            notes = encodedNotes.ifEmpty { null }
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

    // ── Transient message (auto-dismissing 2-second banner) ──────────────────

    private var transientMessageJob: Job? = null

    private fun setTransientMessage(message: String) {
        _uiState.update { it.copy(transientMessage = message) }
        transientMessageJob?.cancel()
        transientMessageJob = viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(transientMessage = null) }
        }
    }

    fun clearTransientMessage() {
        transientMessageJob?.cancel()
        _uiState.update { it.copy(transientMessage = null) }
    }

    // ── Voice state observer: auto-confirm high-confidence RESCHEDULE ─────────

    private fun observeVoiceState() {
        viewModelScope.launch {
            voiceCaptureManager.state.collect { state ->
                if (state is VoiceInputState.Preview &&
                    state.response.intent == VoiceIntent.RESCHEDULE &&
                    (state.response.tasks.firstOrNull()?.confidence ?: 0f) > 0.70f) {
                    autoConfirmReschedule(state.response)
                }
            }
        }
    }

    private suspend fun autoConfirmReschedule(response: ParsedVoiceResponse) {
        val task = response.tasks.firstOrNull() ?: run {
            voiceCaptureManager.setError("Couldn't find that task")
            return
        }
        val targetId = task.parentTaskId ?: run {
            voiceCaptureManager.setError("Couldn't identify which task to reschedule")
            return
        }
        val targetListId = findTaskListId(targetId) ?: run {
            voiceCaptureManager.setError("Couldn't find that task in your lists")
            return
        }
        voiceCaptureManager.resetToIdle()
        voiceParsingCoordinator.clearMetadata()
        tasksRepository.updateTaskDueDate(targetListId, targetId, task.dueDate).fold(
            onSuccess = {
                val dateLabel = if (task.dueDate == null) "cleared"
                               else task.dueDate.format(java.time.format.DateTimeFormatter.ofPattern("EEE MMM d"))
                setTransientMessage("Moved to $dateLabel")
                refresh()
            },
            onFailure = {
                voiceCaptureManager.setError("Failed to reschedule task")
            }
        )
    }

    /**
     * Called when the user taps Retry on a low-confidence reschedule preview card.
     * Arms the coordinator with retry context, then starts a new listening session.
     */
    fun retryReschedule() {
        val response = voiceParsingCoordinator.lastResponse ?: return
        val task = response.tasks.firstOrNull() ?: return
        val targetId = task.parentTaskId ?: return
        val ctx = RescheduleRetryContext(
            originalTranscript = response.rawTranscript,
            targetTaskTitle = task.title,
            targetTaskId = targetId,
            firstParsedDate = task.dueDate
        )
        voiceParsingCoordinator.armRescheduleRetry(ctx)
        voiceCaptureManager.resetToIdle()
        voiceCaptureManager.startListening()
    }

    private fun resolveKnownTaskListId(candidateId: String?): String? {
        return candidateId?.takeIf { id ->
            _uiState.value.taskLists.any { list -> list.id == id }
        }
    }

    // ── Day Organizer ──────────────────────────────────────────────────

    fun startDayOrganizer() {
        if (dayOrganizerCoordinator.state.value !is DayOrganizerState.Idle) return

        if (!isOnline.value) {
            setTransientMessage("No internet connection — day planner requires network access.")
            return
        }

        dayOrganizerCoordinator.startListening(
            scope = viewModelScope,
            listProvider = {
                _uiState.value.taskLists.map { list ->
                    ExistingListRef(id = list.id, title = list.title)
                }
            },
            taskProvider = {
                _uiState.value.allTaskLists.flatMap { list ->
                    list.tasks.filter { it.parentId == null && !it.isCompleted }.map { task ->
                        ExistingTaskRef(
                            id = task.id,
                            title = task.title,
                            listId = list.taskList.id,
                            listTitle = list.taskList.title,
                            dueDate = task.dueDate,
                            priority = task.priority,
                            preferredTime = task.preferredTime,
                            recurrenceInfo = task.recurrenceRule?.toHumanReadable()
                        )
                    }
                }.take(40)
            },
            eventsProvider = {
                val today = java.time.LocalDate.now()
                val events = calendarRepository.getEventsForDateRange(
                    today, today, _uiState.value.selectedCalendarId
                ).getOrElse { emptyMap() }

                events[today]?.map { event ->
                    if (event.isAllDay) {
                        "All day ${event.title} (all-day)"
                    } else {
                        val start = event.startDateTime?.toLocalTime()
                        val end = event.endDateTime?.toLocalTime()
                        val timeRange = "${start?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}-${end?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "?"}"
                        val durationMin = if (start != null && end != null) {
                            java.time.Duration.between(start, end).toMinutes()
                        } else null
                        val durationSuffix = durationMin?.let { " (${it}min)" } ?: ""
                        "$timeRange ${event.title}$durationSuffix"
                    }
                } ?: emptyList()
            },
            selectedCalendarId = _uiState.value.selectedCalendarId,
            weatherProvider = {
                val todayWeather = _weatherForecast.value[java.time.LocalDate.now()]
                todayWeather?.name // text-only — no emoji to avoid leaking into Gemini titles
            },
            wakeHour = _sleepEndHour.value,   // sleep-end = wake-up time
            sleepHour = _sleepStartHour.value, // sleep-start = bedtime
            focusedListTitle = _uiState.value.selectedTaskListTitle.takeIf {
                _uiState.value.selectedTaskListId != null
            },
            groundingContextProvider = if (_geminiGroundingEnabled.value && isOnline.value) {
                {
                    val apiKey = geminiKeyStore.getApiKey()
                    val location = try {
                        WeatherKeyStore(context).getLocation()
                    } catch (e: Exception) {
                        null
                    }
                    if (apiKey != null) {
                        geminiCaptureRepository.fetchGroundingContext(
                            apiKey = apiKey,
                            location = location,
                            date = java.time.LocalDate.now()
                        )
                    } else null
                }
            } else null,
            energyProfile = _energyProfile.value
        )
    }

    fun stopDayOrganizerListening() {
        val state = dayOrganizerCoordinator.state.value
        when (state) {
            is DayOrganizerState.Listening -> dayOrganizerCoordinator.stopListening()
            is DayOrganizerState.Adjusting -> dayOrganizerCoordinator.stopAdjustmentListening()
            else -> {}
        }
    }

    fun acceptDayPlan() {
        viewModelScope.launch {
            val result = dayOrganizerCoordinator.acceptPlan()
            result.fold(
                onSuccess = { count ->
                    val createdIds = dayOrganizerCoordinator.getLastCreatedEventIds()

                    // Set recently-created IDs for highlight animation (clear after 3s)
                    _recentlyCreatedEventIds.value = createdIds.toSet()
                    viewModelScope.launch {
                        delay(3_000)
                        _recentlyCreatedEventIds.value = emptySet()
                    }

                    // Set plan undo state with 8-second timeout
                    _planUndoState.value = PlanUndoState(eventCount = count, eventIds = createdIds)
                    planUndoTimeoutJob?.cancel()
                    planUndoTimeoutJob = viewModelScope.launch {
                        delay(8_000)
                        _planUndoState.value = null
                    }

                    loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
                },
                onFailure = { error ->
                    setTransientMessage("Failed to create events: ${error.message}")
                }
            )
            // Only restore voice pipeline when coordinator has fully finished (not PartialSuccess)
            if (dayOrganizerCoordinator.state.value is DayOrganizerState.Idle) {
                restoreVoicePipelineCallback()
            }
        }
    }

    fun undoPlanAcceptance() {
        val state = _planUndoState.value ?: return
        planUndoTimeoutJob?.cancel()
        _planUndoState.value = null
        _recentlyCreatedEventIds.value = emptySet()
        viewModelScope.launch {
            var deleted = 0
            for (eventId in state.eventIds) {
                val result = calendarRepository.deleteEvent(eventId, _uiState.value.selectedCalendarId)
                if (result.isSuccess) deleted++
            }
            setTransientMessage("Removed $deleted event${if (deleted != 1) "s" else ""} from calendar")
            loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
        }
    }

    fun dismissPlanUndo() {
        planUndoTimeoutJob?.cancel()
        _planUndoState.value = null
    }

    fun adjustDayPlan() {
        dayOrganizerCoordinator.startAdjustment()
    }

    fun cancelDayOrganizer() {
        dayOrganizerCoordinator.cancel()
        restoreVoicePipelineCallback()
    }

    fun retryDayOrganizer() {
        cancelDayOrganizer()
        startDayOrganizer()
    }

    fun setDayOrganizerFocus(index: Int) {
        dayOrganizerCoordinator.setFocusedIndex(index)
    }

    fun setPendingRemoveBlock(index: Int?) {
        dayOrganizerCoordinator.setPendingRemove(index)
    }

    fun confirmRemoveBlock(index: Int) {
        dayOrganizerCoordinator.confirmRemoveBlock(index)
    }

    fun setEnergyProfile(profile: EnergyProfile) {
        _energyProfile.value = profile
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[energyProfileKey] = profile.name
            }
        }
    }

    /** Derived task name lookup from allTaskLists for display in plan preview. */
    val taskNameById: StateFlow<Map<String, String>> = _uiState.map { state ->
        state.allTaskLists.flatMap { it.tasks.map { t -> t.id to t.title } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Exposes transient adjustment error messages from the coordinator for UI toast display. */
    val dayOrganizerAdjustmentErrors = dayOrganizerCoordinator.adjustmentErrors

    fun retryFailedDayPlanBlocks(blocks: List<com.example.todowallapp.data.model.PlanBlock>) {
        viewModelScope.launch {
            val result = dayOrganizerCoordinator.retryFailedBlocks(blocks)
            result.fold(
                onSuccess = { count ->
                    setTransientMessage("$count additional event${if (count != 1) "s" else ""} created")
                    loadCalendarRangeInternal(_uiState.value.calendarViewMode, _uiState.value.selectedCalendarDate)
                },
                onFailure = { error ->
                    setTransientMessage("Retry failed: ${error.message}")
                }
            )
            if (dayOrganizerCoordinator.state.value is DayOrganizerState.Idle) {
                restoreVoicePipelineCallback()
            }
        }
    }

    private fun restoreVoicePipelineCallback() {
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

        return calendarRepository.getEventsForDateRange(start, end, calendarId).fold(
            onSuccess = { grouped ->
                val allEvents = grouped.values.flatten()
                val promotedEventIds = mutableMapOf<String, String>()
                val promotedStartTimes = mutableMapOf<String, LocalDateTime>()
                for (event in allEvents) {
                    val taskId = event.sourceTaskId
                    if (!event.isPromotedTask || taskId.isNullOrBlank()) continue
                    promotedEventIds[taskId] = event.id
                    val startTime = event.startDateTime
                        ?: event.allDayStartDate?.atStartOfDay()
                        ?: continue
                    promotedStartTimes[taskId] = startTime
                }
                _uiState.update { state ->
                    val mergedEventIds = state.scheduledTaskEventIds.toMutableMap().apply {
                        putAll(promotedEventIds)
                    }
                    val mergedStartTimes = state.scheduledTaskTimes.toMutableMap().apply {
                        putAll(promotedStartTimes)
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
            ?: _promotionAnchor?.endsAt
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
                        _promotionAnchor = PromotionAnchor(
                            previousEventTitle = event.title,
                            endsAt = event.endDateTime ?: draft.endDateTime
                        )
                        state.copy(
                            promotionDraft = null,
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

    fun dismissPlanDayHint() {
        _hasSeenPlanDayHint.value = true
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[hasSeenPlanDayHintKey] = true
            }
        }
    }

    fun setGeminiGroundingEnabled(enabled: Boolean) {
        _geminiGroundingEnabled.value = enabled
        viewModelScope.launch {
            context.dataStore.edit { prefs ->
                prefs[geminiGroundingEnabledKey] = enabled
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
            _promotionAnchor = null
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
        val today = LocalDate.now()
        return sortTaskListsByNearestDueDate(
            taskLists.map { listWithTasks ->
                listWithTasks.copy(tasks = sortTasksForDisplay(listWithTasks.tasks, today))
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

    // ─── Search & Filter ───

    fun toggleFilter(filter: TaskFilter) {
        _uiState.update { state ->
            val newFilters = if (filter in state.activeFilters) {
                state.activeFilters - filter
            } else {
                state.activeFilters + filter
            }
            state.copy(
                activeFilters = newFilters,
                isSearchActive = newFilters.isNotEmpty() || state.searchQuery != null
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = true) }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = null, activeFilters = emptySet(), isSearchActive = false) }
    }

    /**
     * Returns tasks matching the current search query and/or filters,
     * paired with their parent list name. Cross-list.
     */
    fun getFilteredTasks(): List<Pair<Task, String>> {
        val state = _uiState.value
        if (!state.isSearchActive) return emptyList()
        val today = LocalDate.now()
        val queryWords = state.searchQuery?.lowercase()?.split(" ")?.filter { it.isNotBlank() }

        return state.allTaskLists.flatMap { listWithTasks ->
            listWithTasks.tasks
                .filter { task -> matchesFilters(task, state.activeFilters, today) }
                .filter { task -> matchesQuery(task, queryWords) }
                .filter { !it.isCompleted }
                .map { task -> task to listWithTasks.taskList.title }
        }.sortedWith(
            compareBy<Pair<Task, String>> { (task, _) ->
                if (task.priority == TaskPriority.HIGH) 0
                else if (task.priority == TaskPriority.MEDIUM) 1
                else 2
            }.thenBy { (task, _) -> task.dueDate ?: LocalDate.MAX }
        )
    }

    private fun matchesFilters(task: Task, filters: Set<TaskFilter>, today: LocalDate): Boolean {
        if (filters.isEmpty()) return true
        return filters.all { filter ->
            when (filter) {
                TaskFilter.OVERDUE -> task.getUrgencyLevel(today) == com.example.todowallapp.data.model.TaskUrgency.OVERDUE
                TaskFilter.DUE_TODAY -> task.getUrgencyLevel(today) == com.example.todowallapp.data.model.TaskUrgency.DUE_TODAY
                TaskFilter.DUE_THIS_WEEK -> task.dueDate != null && java.time.temporal.ChronoUnit.DAYS.between(today, task.dueDate) in 0..7
                TaskFilter.HIGH_PRIORITY -> task.priority == TaskPriority.HIGH || task.priority == TaskPriority.MEDIUM
                TaskFilter.RECURRING -> task.recurrenceRule != null
            }
        }
    }

    private fun matchesQuery(task: Task, queryWords: List<String>?): Boolean {
        if (queryWords.isNullOrEmpty()) return true
        val searchable = "${task.title} ${task.cleanNotes.orEmpty()}".lowercase()
        return queryWords.any { word -> word in searchable }
    }

    // ─── Priority ───

    fun setTaskPriority(task: Task, newPriority: TaskPriority) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            // Optimistic update
            updateTaskAcrossLists(task.id) { it.copy(priority = newPriority) }

            val encodedNotes = TaskMetadata.encode(
                task.cleanNotes, task.recurrenceRule, newPriority
            )
            val result = tasksRepository.updateTaskNotes(taskListId, task.id, encodedNotes)
            result.onFailure { error ->
                // Revert
                updateTaskAcrossLists(task.id) { it.copy(priority = task.priority) }
                _uiState.update { it.copy(error = "Failed to update priority: ${error.message}") }
            }
        }
    }

    // ─── Reorder ───

    fun enterReorderMode(taskId: String) {
        _uiState.update { it.copy(reorderModeTaskId = taskId) }
    }

    fun cancelReorderMode() {
        _uiState.update { it.copy(reorderModeTaskId = null) }
    }

    /**
     * Swap the reordering task one position in the given direction within its list.
     * Returns the new focus key if the swap happened.
     */
    fun moveReorder(direction: Int): Boolean {
        val state = _uiState.value
        val taskId = state.reorderModeTaskId ?: return false

        val listWithTasks = state.allTaskLists.firstOrNull { lwt ->
            lwt.tasks.any { it.id == taskId }
        } ?: return false

        val pendingTasks = listWithTasks.tasks.filter { !it.isCompleted }
        val currentIndex = pendingTasks.indexOfFirst { it.id == taskId }
        if (currentIndex < 0) return false

        val targetIndex = currentIndex + direction
        if (targetIndex < 0 || targetIndex >= pendingTasks.size) return false

        // Swap locally
        val mutableTasks = listWithTasks.tasks.toMutableList()
        val fullCurrentIndex = mutableTasks.indexOfFirst { it.id == taskId }
        val fullTargetIndex = mutableTasks.indexOfFirst { it.id == pendingTasks[targetIndex].id }
        val temp = mutableTasks[fullCurrentIndex]
        mutableTasks[fullCurrentIndex] = mutableTasks[fullTargetIndex]
        mutableTasks[fullTargetIndex] = temp

        val updatedLists = state.allTaskLists.map { lwt ->
            if (lwt.taskList.id == listWithTasks.taskList.id) {
                lwt.copy(tasks = mutableTasks)
            } else lwt
        }
        _uiState.update { it.copy(allTaskLists = updatedLists) }
        return true
    }

    fun confirmReorder() {
        val state = _uiState.value
        val taskId = state.reorderModeTaskId ?: return
        _uiState.update { it.copy(reorderModeTaskId = null) }

        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(taskId) ?: return@launch
            val listWithTasks = state.allTaskLists.firstOrNull { lwt ->
                lwt.tasks.any { it.id == taskId }
            } ?: return@launch
            val pendingTasks = listWithTasks.tasks.filter { !it.isCompleted }
            val index = pendingTasks.indexOfFirst { it.id == taskId }
            val previousTaskId = if (index > 0) pendingTasks[index - 1].id else null

            val result = tasksRepository.moveTask(taskListId, taskId, previousTaskId)
            result.onFailure {
                _uiState.update { s -> s.copy(error = "Failed to save position: ${it.message}") }
                performRefresh(showSyncIndicator = false)
            }
        }
    }

    fun pinTaskToTop(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch
            val result = tasksRepository.moveTask(taskListId, task.id, null)
            result.onSuccess { performRefresh(showSyncIndicator = false) }
            result.onFailure {
                _uiState.update { s -> s.copy(error = "Failed to pin task: ${it.message}") }
            }
        }
    }

    // ─── Recurrence management ───

    fun setTaskRecurrence(task: Task, rule: RecurrenceRule) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            // Optimistic update
            updateTaskAcrossLists(task.id) { it.copy(recurrenceRule = rule) }

            val encodedNotes = TaskMetadata.encode(task.cleanNotes, rule, task.priority)
            val result = tasksRepository.updateTaskNotes(taskListId, task.id, encodedNotes)
            result.onFailure { error ->
                updateTaskAcrossLists(task.id) { it.copy(recurrenceRule = task.recurrenceRule) }
                _uiState.update { it.copy(error = "Failed to set recurrence: ${error.message}") }
            }
        }
    }

    fun removeTaskRecurrence(task: Task) {
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            updateTaskAcrossLists(task.id) { it.copy(recurrenceRule = null) }

            val encodedNotes = TaskMetadata.encode(task.cleanNotes, null, task.priority)
            val result = tasksRepository.updateTaskNotes(taskListId, task.id, encodedNotes)
            result.onFailure { error ->
                updateTaskAcrossLists(task.id) { it.copy(recurrenceRule = task.recurrenceRule) }
                _uiState.update { it.copy(error = "Failed to remove recurrence: ${error.message}") }
            }
        }
    }

    fun skipRecurrence(task: Task) {
        // Complete the task but suppress spawning the next instance
        viewModelScope.launch {
            val taskListId = findTaskListIdForTask(task.id) ?: return@launch

            _undoState.value = UndoState(task = task, taskListId = taskListId)
            undoTimeoutJob?.cancel()
            undoTimeoutJob = viewModelScope.launch {
                delay(UNDO_TIMEOUT_MS)
                _undoState.value = null
            }

            val completedTask = task.copy(isCompleted = true, completedAt = LocalDateTime.now())
            inFlightTaskIds.add(task.id)
            updateTaskAcrossLists(task.id) { completedTask }

            val result = tasksRepository.completeTask(taskListId, task.id)
            result.onSuccess {
                inFlightTaskIds.remove(task.id)
                updateTaskAcrossLists(task.id) { it }
                // Deliberately NOT calling spawnNextRecurrence
            }
            result.onFailure { error ->
                inFlightTaskIds.remove(task.id)
                updateTaskAcrossLists(task.id) { task }
                _undoState.value = null
                undoTimeoutJob?.cancel()
                _uiState.update { it.copy(error = "Failed to skip occurrence: ${error.message}") }
            }
        }
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
