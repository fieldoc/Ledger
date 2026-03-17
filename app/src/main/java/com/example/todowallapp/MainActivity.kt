package com.example.todowallapp

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todowallapp.auth.AuthState
import com.example.todowallapp.auth.GoogleAuthManager
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.capture.repository.GeminiCaptureRepository
import com.example.todowallapp.capture.repository.PendingCaptureStore
import com.example.todowallapp.capture.repository.ScanCaptureResult
import com.example.todowallapp.capture.repository.ScannerRepository
import com.example.todowallapp.data.model.AppMode
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.repository.GoogleCalendarRepository
import com.example.todowallapp.data.repository.GoogleTasksRepository
import com.example.todowallapp.data.repository.ModePreferenceRepository
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.repository.WeatherRepository
import com.example.todowallapp.security.GeminiKeyStore
import com.example.todowallapp.security.WeatherKeyStore
import com.example.todowallapp.ui.components.PageIndicator
import com.example.todowallapp.ui.components.PhoneSettingsSheet
import com.example.todowallapp.ui.components.PhoneVoiceBottomSheet
import com.example.todowallapp.ui.components.PromotionSheet
import com.example.todowallapp.ui.components.PromotionSheetState
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.ui.screens.CalendarScreen
import com.example.todowallapp.ui.screens.ModeSelectorScreen
import com.example.todowallapp.ui.screens.ParsedCapturePreviewScreen
import com.example.todowallapp.ui.screens.PhoneHomeScreen
import com.example.todowallapp.ui.screens.SignInScreen
import com.example.todowallapp.ui.screens.TaskWallScreen
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.viewmodel.PhoneCaptureViewModel
import com.example.todowallapp.viewmodel.TaskWallViewModel
import com.example.todowallapp.viewmodel.ThemeMode
import com.example.todowallapp.voice.VoiceInputState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    private lateinit var authManager: GoogleAuthManager
    private var appliedMode: AppMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authManager = GoogleAuthManager(this)
        enableEdgeToEdge()
        applyWindowMode(null)

        setContent {
            TaskWallApp(
                authManager = authManager,
                onModeUiChanged = ::applyWindowMode
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyWindowMode(appliedMode)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowMode(appliedMode)
        }
    }

    private fun applyWindowMode(mode: AppMode?) {
        appliedMode = mode
        if (mode == AppMode.WALL) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun TaskWallApp(
    authManager: GoogleAuthManager,
    onModeUiChanged: (AppMode?) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    val tasksRepository = remember { GoogleTasksRepository(appContext) }
    val calendarRepository = remember { GoogleCalendarRepository(appContext) }
    val modeRepository = remember { ModePreferenceRepository(appContext) }
    val scannerRepository = remember { ScannerRepository() }
    val geminiCaptureRepository = remember { GeminiCaptureRepository() }
    val geminiKeyStore = remember { GeminiKeyStore(appContext) }
    val pendingCaptureStore = remember { PendingCaptureStore(appContext) }
    val weatherKeyStore = remember { WeatherKeyStore(appContext) }
    val weatherRepository = remember { WeatherRepository(appContext, weatherKeyStore) }
    val wallViewModel: TaskWallViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return TaskWallViewModel(
                        context = appContext,
                        authManager = authManager,
                        tasksRepository = tasksRepository,
                        calendarRepository = calendarRepository,
                        weatherRepository = weatherRepository
                    ) as T
                }
            }
        }
    )

    val phoneViewModel: PhoneCaptureViewModel = viewModel(
        factory = remember {
            PhoneCaptureViewModel.Factory(
                context = appContext,
                tasksRepository = tasksRepository,
                geminiRepository = geminiCaptureRepository,
                geminiKeyStore = geminiKeyStore,
                pendingCaptureStore = pendingCaptureStore
            )
        }
    )

    val wallUiState by wallViewModel.uiState.collectAsState()
    val wallVoiceState by wallViewModel.voiceState.collectAsState()
    val phoneUiState by phoneViewModel.uiState.collectAsState()
    val phoneVoiceState by phoneViewModel.voiceState.collectAsState()
    val selectedMode by modeRepository.modePreferenceFlow.collectAsState(initial = null)
    val themeMode by wallViewModel.themeMode.collectAsState()
    val lightStartHour by wallViewModel.lightStartHour.collectAsState()
    val lightEndHour by wallViewModel.lightEndHour.collectAsState()
    val syncIntervalMinutes by wallViewModel.syncIntervalMinutes.collectAsState()

    var isDarkTheme by remember { mutableStateOf(true) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var isSigningIn by remember { mutableStateOf(false) }
    var showPhoneSettings by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (val parsed = scannerRepository.parseActivityResult(result.resultCode, result.data)) {
            is ScanCaptureResult.Success -> {
                if (activity == null) return@rememberLauncherForActivityResult
                scope.launch {
                    scannerRepository.readAndCompressJpeg(activity, parsed.imageUri).fold(
                        onSuccess = { bytes -> phoneViewModel.parseCapturedImage(bytes) },
                        onFailure = {
                            Toast.makeText(context, "Failed to read scanned image", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            ScanCaptureResult.Cancelled -> Unit
            is ScanCaptureResult.Error -> Toast.makeText(context, parsed.message, Toast.LENGTH_SHORT).show()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        authManager.handleSignInResult(result.data).fold(
            onSuccess = { account ->
                signInError = null
                wallViewModel.onSignedIn(account)
            },
            onFailure = { error ->
                signInError = error.message ?: "Sign-in failed"
            }
        )
    }

    val calendarScopeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSigningIn = false
        authManager.handleSignInResult(result.data).fold(
            onSuccess = { account ->
                signInError = null
                wallViewModel.onSignedIn(account)
            },
            onFailure = { error ->
                signInError = error.message ?: "Calendar access grant failed"
            }
        )
    }

    LaunchedEffect(themeMode, lightStartHour, lightEndHour) {
        while (true) {
            isDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.AUTO -> {
                    val hour = LocalTime.now().hour
                    val inLightWindow = if (lightStartHour < lightEndHour) {
                        hour in lightStartHour until lightEndHour
                    } else {
                        hour >= lightStartHour || hour < lightEndHour
                    }
                    !inLightWindow
                }
            }
            delay(60_000L)
        }
    }

    LaunchedEffect(wallUiState.authState, wallUiState.isLoading, selectedMode) {
        val sessionReady = wallUiState.authState is AuthState.Authenticated && !wallUiState.isLoading
        phoneViewModel.setSessionReady(sessionReady)
        onModeUiChanged(if (wallUiState.authState is AuthState.Authenticated) selectedMode else null)
    }

    LedgerTheme(darkTheme = isDarkTheme) {
        when (val authState = wallUiState.authState) {
            is AuthState.Loading -> SignInScreen(isLoading = true, error = null, onSignInClick = { })
            is AuthState.NotAuthenticated -> SignInScreen(
                isLoading = isSigningIn,
                error = signInError,
                onSignInClick = {
                    isSigningIn = true
                    signInError = null
                    signInLauncher.launch(authManager.getSignInIntent())
                }
            )
            is AuthState.Error -> SignInScreen(
                isLoading = false,
                error = authState.message,
                onSignInClick = {
                    isSigningIn = true
                    signInError = null
                    signInLauncher.launch(authManager.getSignInIntent())
                }
            )
            is AuthState.Authenticated -> {
                when (selectedMode) {
                    null -> ModeSelectorScreen(
                        onSelectMode = { mode ->
                            scope.launch { modeRepository.setModePreference(mode) }
                        },
                        onSignOut = { wallViewModel.signOut() }
                    )

                    AppMode.WALL -> WallModeContent(
                        viewModel = wallViewModel,
                        voiceState = wallVoiceState,
                        weatherKeyStore = weatherKeyStore,
                        weatherRepository = weatherRepository,
                        onSwitchMode = { scope.launch { modeRepository.setModePreference(null) } },
                        onRequestCalendarAccess = {
                            isSigningIn = true
                            signInError = null
                            calendarScopeLauncher.launch(authManager.getCalendarReconsentIntent())
                        },
                        onSetBrightness = { brightness ->
                            (context as? Activity)?.let {
                                val lp = it.window.attributes
                                lp.screenBrightness = brightness
                                it.window.attributes = lp
                            }
                        }
                    )

                    AppMode.PHONE -> PhoneModeContent(
                        phoneUiState = phoneUiState,
                        phoneVoiceState = phoneVoiceState,
                        phoneViewModel = phoneViewModel,
                        scannerRepository = scannerRepository,
                        activity = activity,
                        scope = scope,
                        themeMode = themeMode,
                        syncIntervalMinutes = syncIntervalMinutes,
                        showPhoneSettings = showPhoneSettings,
                        weatherLocation = weatherKeyStore.getLocation() ?: "",
                        weatherApiKeyPresent = weatherKeyStore.hasApiKey(),
                        onShowPhoneSettings = { showPhoneSettings = true },
                        onHidePhoneSettings = { showPhoneSettings = false },
                        onLaunchScanner = { request -> scanLauncher.launch(request) },
                        onThemeModeChange = { mode ->
                            wallViewModel.updateThemeSettings(mode, lightStartHour, lightEndHour)
                        },
                        onSyncIntervalChange = wallViewModel::updateSyncInterval,
                        onSaveWeatherLocation = { location ->
                            weatherKeyStore.setLocation(location)
                            wallViewModel.refreshWeather()
                        },
                        onSaveWeatherApiKey = { key ->
                            weatherKeyStore.setApiKey(key)
                            wallViewModel.refreshWeather()
                        },
                        onClearWeatherApiKey = {
                            weatherKeyStore.clearApiKey()
                        },
                        onSearchCities = { query ->
                            weatherRepository.searchCities(query).map { suggestion ->
                                listOfNotNull(suggestion.name, suggestion.state, suggestion.country)
                                    .joinToString(", ")
                            }
                        },
                        onSwitchMode = { scope.launch { modeRepository.setModePreference(null) } },
                        onSignOut = { wallViewModel.signOut() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WallModeContent(
    viewModel: TaskWallViewModel,
    voiceState: VoiceInputState,
    weatherKeyStore: WeatherKeyStore,
    weatherRepository: WeatherRepository,
    onSwitchMode: () -> Unit,
    onRequestCalendarAccess: () -> Unit,
    onSetBrightness: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val lightStartHour by viewModel.lightStartHour.collectAsState()
    val lightEndHour by viewModel.lightEndHour.collectAsState()
    val sleepStartHour by viewModel.sleepStartHour.collectAsState()
    val sleepEndHour by viewModel.sleepEndHour.collectAsState()
    val weatherForecast by viewModel.weatherForecast.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val geminiKeyPresent by viewModel.geminiKeyPresent.collectAsState()
    val isValidatingGeminiKey by viewModel.isValidatingGeminiKey.collectAsState()
    val geminiKeyError by viewModel.geminiKeyError.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val selectedViewKey by remember(pagerState.currentPage) {
        derivedStateOf { if (pagerState.currentPage == 0) "tasks" else "calendar" }
    }
    var promotionFocusRow by remember { mutableIntStateOf(0) }
    var promotionIsAdjusting by remember { mutableStateOf(false) }
    var calendarSlotAnchor by remember { mutableStateOf<LocalDateTime?>(null) }

    LaunchedEffect(uiState.promotionDraft?.task?.id) {
        promotionFocusRow = 0
        promotionIsAdjusting = false
    }

    LaunchedEffect(uiState.promotionSuccessMessage) {
        val message = uiState.promotionSuccessMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.clearPromotionSuccessMessage()
    }

    val pendingTasksByList = remember(uiState.allTaskLists) {
        uiState.allTaskLists.mapNotNull { listWithTasks ->
            val pending = listWithTasks.tasks.filter { !it.isCompleted }
            if (pending.isEmpty()) null else listWithTasks.taskList.title to pending
        }
    }
    val taskListTitleByTaskId = remember(uiState.allTaskLists) {
        buildMap {
            uiState.allTaskLists.forEach { listWithTasks ->
                listWithTasks.tasks.forEach { task ->
                    put(task.id, listWithTasks.taskList.title)
                }
            }
        }
    }
    val taskUrgencyByTaskId = remember(uiState.allTaskLists) {
        buildMap<String, TaskUrgency> {
            uiState.allTaskLists.forEach { listWithTasks ->
                listWithTasks.tasks.forEach { task ->
                    put(task.id, task.getUrgencyLevel())
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    TaskWallScreen(
                        taskLists = uiState.allTaskLists,
                        onTaskToggle = viewModel::toggleTaskCompletion,
                        onTaskDelete = viewModel::deleteTask,
                        onScheduleTask = { task -> viewModel.openPromotionDraft(task) },
                        onRefresh = viewModel::refresh,
                        onSwitchToCalendar = { scope.launch { pagerState.animateScrollToPage(1) } },
                        selectedViewKey = selectedViewKey,
                        scheduledTaskIds = uiState.scheduledTaskEventIds.keys,
                        scheduledTaskTimes = uiState.scheduledTaskTimes,
                        onOpenScheduledTask = { _, scheduledAt ->
                            scope.launch {
                                if (scheduledAt != null) {
                                    calendarSlotAnchor = scheduledAt
                                    viewModel.selectCalendarDate(scheduledAt.toLocalDate())
                                }
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        onSelectTaskList = viewModel::selectTaskList,
                        voiceState = voiceState,
                        onStartVoice = viewModel::startVoiceInput,
                        onStopVoice = viewModel::stopVoiceInput,
                        onCancelVoice = viewModel::cancelVoiceInput,
                        onConfirmVoice = { listId -> viewModel.confirmVoiceTasks(listId) },
                        onDismissVoiceError = viewModel::dismissVoiceError,
                        error = uiState.error,
                        onDismissError = viewModel::clearError,
                        isSyncing = uiState.isSyncing,
                        isLoading = uiState.isLoading,
                        isOnline = isOnline,
                        lastSyncTime = uiState.lastSyncTime,
                        lastSyncSuccess = uiState.lastSyncSuccess,
                        undoVisible = undoState != null,
                        undoMessage = undoState?.task?.title,
                        onUndo = viewModel::undoCompletion,
                        onDismissUndo = viewModel::dismissUndo,
                        themeMode = themeMode,
                        lightStartHour = lightStartHour,
                        lightEndHour = lightEndHour,
                        sleepStartHour = sleepStartHour,
                        sleepEndHour = sleepEndHour,
                        syncIntervalMinutes = syncIntervalMinutes,
                        onThemeSettingsChange = { mode, lightStart, lightEnd ->
                            viewModel.updateThemeSettings(mode, lightStart, lightEnd)
                        },
                        onSleepScheduleChange = viewModel::updateSleepSchedule,
                        onSyncIntervalChange = viewModel::updateSyncInterval,
                        geminiKeyPresent = geminiKeyPresent,
                        isValidatingGeminiKey = isValidatingGeminiKey,
                        geminiKeyError = geminiKeyError,
                        onSaveGeminiKey = viewModel::validateAndSaveGeminiKey,
                        onClearGeminiKey = viewModel::clearGeminiKey,
                        weatherLocation = weatherKeyStore.getLocation() ?: "",
                        weatherApiKeyPresent = weatherKeyStore.hasApiKey(),
                        onSaveWeatherLocation = { location ->
                            weatherKeyStore.setLocation(location)
                        },
                        onSaveWeatherApiKey = { key ->
                            weatherKeyStore.setApiKey(key)
                        },
                        onClearWeatherApiKey = {
                            weatherKeyStore.clearApiKey()
                        },
                        onSearchCities = { query ->
                            weatherRepository.searchCities(query).map { suggestion ->
                                "${suggestion.name}, ${suggestion.country}"
                            }
                        },
                        onSwitchMode = onSwitchMode,
                        onSignOut = viewModel::signOut,
                        onSetBrightness = onSetBrightness,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                1 -> {
                    CalendarScreen(
                        selectedDate = uiState.selectedCalendarDate,
                        events = uiState.eventsForRange[uiState.selectedCalendarDate] ?: emptyList(),
                        calendars = uiState.calendars,
                        selectedCalendarId = uiState.selectedCalendarId,
                        hasCalendarScope = uiState.hasCalendarScope,
                        isLoading = uiState.isCalendarLoading,
                        error = uiState.calendarError,
                        isOnline = isOnline,
                        lastSyncTime = uiState.lastSyncTime,
                        lastSyncSuccess = uiState.lastSyncSuccess,
                        currentPage = pagerState.currentPage,
                        onSwitchPage = { pageIndex -> scope.launch { pagerState.animateScrollToPage(pageIndex) } },
                        onSelectDate = viewModel::selectCalendarDate,
                        onSelectCalendar = viewModel::selectCalendar,
                        onRequestCalendarAccess = onRequestCalendarAccess,
                        onOpenPromotionAt = viewModel::setPromotionStartTime,
                        isPromotionDraftOpen = uiState.promotionDraft != null,
                        slotAnchorTime = calendarSlotAnchor,
                        pendingTasksByList = pendingTasksByList,
                        taskListTitleByTaskId = taskListTitleByTaskId,
                        taskUrgencyByTaskId = taskUrgencyByTaskId,
                        onScheduleTaskAtTime = { task, start -> viewModel.openPromotionDraft(task, start) },
                        onScheduleTaskForRange = { task, start, durationMin ->
                            viewModel.openPromotionDraft(task, start, durationMin)
                        },
                        onCompletePromotedTask = viewModel::completeTaskById,
                        onRescheduleEvent = viewModel::reschedulePromotedEvent,
                        onDeleteCalendarEvent = { event ->
                            viewModel.deleteCalendarEvent(event.id, event.calendarId)
                        },
                        calendarViewMode = uiState.calendarViewMode,
                        eventsForRange = uiState.eventsForRange,
                        onViewModeChange = { mode -> viewModel.setCalendarViewMode(mode) },
                        onDaySelectedFromGrid = { date -> viewModel.selectCalendarDate(date) },
                        weatherForecast = weatherForecast,
                        themeMode = themeMode,
                        lightStartHour = lightStartHour,
                        lightEndHour = lightEndHour,
                        sleepStartHour = sleepStartHour,
                        sleepEndHour = sleepEndHour,
                        syncIntervalMinutes = syncIntervalMinutes,
                        onThemeSettingsChange = { mode, lightStart, lightEnd ->
                            viewModel.updateThemeSettings(mode, lightStart, lightEnd)
                        },
                        onSleepScheduleChange = viewModel::updateSleepSchedule,
                        onSyncIntervalChange = viewModel::updateSyncInterval,
                        geminiKeyPresent = geminiKeyPresent,
                        isValidatingGeminiKey = isValidatingGeminiKey,
                        geminiKeyError = geminiKeyError,
                        onSaveGeminiKey = viewModel::validateAndSaveGeminiKey,
                        onClearGeminiKey = viewModel::clearGeminiKey,
                        weatherLocation = weatherKeyStore.getLocation() ?: "",
                        weatherApiKeyPresent = weatherKeyStore.hasApiKey(),
                        onSaveWeatherLocation = { location ->
                            weatherKeyStore.setLocation(location)
                        },
                        onSaveWeatherApiKey = { key ->
                            weatherKeyStore.setApiKey(key)
                        },
                        onClearWeatherApiKey = {
                            weatherKeyStore.clearApiKey()
                        },
                        onSearchCities = { query ->
                            weatherRepository.searchCities(query).map { suggestion ->
                                "${suggestion.name}, ${suggestion.country}"
                            }
                        },
                        onSwitchMode = onSwitchMode,
                        onSignOut = viewModel::signOut,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        var calendarInitialized by remember { mutableStateOf(false) }
        LaunchedEffect(pagerState.currentPage, uiState.hasCalendarScope) {
            if (pagerState.currentPage == 1 && uiState.hasCalendarScope) {
                if (!calendarInitialized) {
                    // Default to MONTH view on first calendar entry only
                    viewModel.setCalendarViewMode(CalendarViewMode.MONTH)
                    calendarInitialized = true
                }
            }
        }

        // Hide during sleep schedule to prevent OLED burn-in
        val currentHour = remember { java.time.LocalTime.now().hour }
        val inSleepSchedule = if (sleepStartHour > sleepEndHour) {
            currentHour >= sleepStartHour || currentHour < sleepEndHour
        } else {
            currentHour in sleepStartHour until sleepEndHour
        }
        if (!inSleepSchedule) {
            PageIndicator(
                pageCount = 2,
                currentPage = pagerState.currentPage,
                onPageSelected = { page ->
                    scope.launch { pagerState.animateScrollToPage(page) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }

        uiState.promotionDraft?.let { draft ->
            val calendarTitle = uiState.calendars.firstOrNull { it.id == draft.calendarId }?.title ?: "Primary"
            PromotionSheet(
                visible = true,
                state = PromotionSheetState(
                    taskTitle = draft.task.title,
                    durationMinutes = draft.durationMinutes,
                    startTime = draft.startDateTime,
                    endTime = draft.endDateTime,
                    calendarTitle = calendarTitle,
                    focusedRow = promotionFocusRow,
                    isAdjusting = promotionIsAdjusting
                ),
                onFocusRow = { row ->
                    promotionFocusRow = row.coerceIn(0, 3)
                    promotionIsAdjusting = false
                },
                onAdjustDuration = { viewModel.cyclePromotionDuration(forward = true) },
                onAdjustStartTime = { viewModel.shiftPromotionStartByMinutes(15) },
                onAdjustCalendar = { viewModel.cyclePromotionCalendar(forward = true) },
                onToggleAdjusting = { promotionIsAdjusting = !promotionIsAdjusting },
                onConfirm = {
                    promotionIsAdjusting = false
                    viewModel.confirmPromotion()
                },
                onDismiss = {
                    promotionIsAdjusting = false
                    viewModel.dismissPromotionDraft()
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PhoneModeContent(
    phoneUiState: com.example.todowallapp.viewmodel.PhoneCaptureUiState,
    phoneVoiceState: VoiceInputState,
    phoneViewModel: PhoneCaptureViewModel,
    scannerRepository: ScannerRepository,
    activity: Activity?,
    scope: kotlinx.coroutines.CoroutineScope,
    themeMode: ThemeMode,
    syncIntervalMinutes: Int,
    showPhoneSettings: Boolean,
    weatherLocation: String = "",
    weatherApiKeyPresent: Boolean = false,
    onShowPhoneSettings: () -> Unit,
    onHidePhoneSettings: () -> Unit,
    onLaunchScanner: (androidx.activity.result.IntentSenderRequest) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onSaveWeatherLocation: (String) -> Unit = {},
    onSaveWeatherApiKey: (String) -> Unit = {},
    onClearWeatherApiKey: () -> Unit = {},
    onSearchCities: (suspend (String) -> List<String>) = { emptyList() },
    onSwitchMode: () -> Unit,
    onSignOut: () -> Unit
) {
    if (phoneUiState.parsedCapture != null) {
        ParsedCapturePreviewScreen(
            parsedCapture = phoneUiState.parsedCapture!!,
            existingLists = phoneUiState.taskLists.map { ExistingListRef(it.taskList.id, it.taskList.title) },
            isCommitting = phoneUiState.isCommittingCapture,
            onUpdateListName = phoneViewModel::updateParsedListName,
            onAssignListToExisting = phoneViewModel::assignParsedListToExisting,
            onUpdateTaskTitle = phoneViewModel::updateParsedTaskTitle,
            onRemoveTask = phoneViewModel::removeParsedTask,
            onCancel = phoneViewModel::dismissParsedCapture,
            onAddAll = phoneViewModel::commitParsedCapture
        )
    } else {
        PhoneHomeScreen(
            uiState = phoneUiState,
            onTaskToggle = phoneViewModel::toggleTaskCompletion,
            onRetryPendingCapture = phoneViewModel::retryPendingCapture,
            onRemovePendingCapture = phoneViewModel::removePendingCapture,
            onCameraClick = {
                if (activity == null) return@PhoneHomeScreen
                val availability = scannerRepository.checkPlayServicesAvailability(activity)
                if (!availability.isAvailable) {
                    if (availability.canResolveInUi) {
                        scannerRepository.showPlayServicesResolutionDialog(activity)
                    } else {
                        Toast.makeText(activity, availability.message ?: "Scanner unavailable", Toast.LENGTH_SHORT).show()
                    }
                    return@PhoneHomeScreen
                }
                scope.launch {
                    scannerRepository.createScanIntentSender(activity).fold(
                        onSuccess = onLaunchScanner,
                        onFailure = { error ->
                            Toast.makeText(activity, "Scanner failed: ${error.message ?: "unknown error"}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            },
            onVoiceClick = phoneViewModel::showVoiceSheet,
            onRefreshClick = phoneViewModel::refreshTaskListsWithIndicator,
            onSettingsClick = onShowPhoneSettings,
            onSaveCaptureForRetry = phoneViewModel::saveLastCaptureForRetry,
            onDismissMessage = phoneViewModel::clearMessage,
            onToggleListExpanded = phoneViewModel::toggleListExpanded,
            onDeleteTask = phoneViewModel::deleteTask,
            onUndoCompletion = phoneViewModel::undoCompletion
        )
    }

    PhoneVoiceBottomSheet(
        visible = phoneUiState.showVoiceSheet,
        voiceState = phoneVoiceState,
        onDismiss = phoneViewModel::hideVoiceSheet,
        onStartListening = phoneViewModel::startVoiceInput,
        onStopListening = phoneViewModel::stopVoiceInput,
        onCancelListening = phoneViewModel::cancelVoiceInput,
        onConfirm = { listId -> phoneViewModel.confirmVoiceTasks(listId) },
        onDismissError = phoneViewModel::dismissVoiceError
    )

    PhoneSettingsSheet(
        visible = showPhoneSettings,
        themeMode = themeMode,
        syncIntervalMinutes = syncIntervalMinutes,
        geminiKeyPresent = phoneUiState.geminiKeyPresent,
        isValidatingKey = phoneUiState.isValidatingKey,
        error = phoneUiState.error,
        weatherLocation = weatherLocation,
        weatherApiKeyPresent = weatherApiKeyPresent,
        onDismiss = onHidePhoneSettings,
        onThemeModeChange = onThemeModeChange,
        onSyncIntervalChange = onSyncIntervalChange,
        onSaveGeminiKey = phoneViewModel::validateAndSaveGeminiKey,
        onClearGeminiKey = phoneViewModel::clearGeminiKey,
        onSaveWeatherLocation = onSaveWeatherLocation,
        onSaveWeatherApiKey = onSaveWeatherApiKey,
        onClearWeatherApiKey = onClearWeatherApiKey,
        onSearchCities = onSearchCities,
        onSwitchMode = {
            onHidePhoneSettings()
            onSwitchMode()
        },
        onSignOut = {
            onHidePhoneSettings()
            onSignOut()
        }
    )
}
