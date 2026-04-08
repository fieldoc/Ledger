package com.example.todowallapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.todowallapp.capture.DayOrganizerState
import com.example.todowallapp.capture.repository.VoiceIntent
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.components.DayOrganizerOverlay
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.voice.VoiceInputState

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.occursOn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.ui.components.Calendar3DayView
import com.example.todowallapp.ui.components.CalendarDayView
import com.example.todowallapp.ui.components.SlotDragRange
import com.example.todowallapp.ui.components.CalendarMonthView
import com.example.todowallapp.ui.components.CalendarWeekView
import com.example.todowallapp.ui.components.ClockHeader
import com.example.todowallapp.ui.components.EVENT_ACTION_COUNT
import com.example.todowallapp.ui.components.EventActionMenu
import com.example.todowallapp.ui.components.TaskPickerOverlay
import com.example.todowallapp.ui.components.SettingsPanel
import com.example.todowallapp.ui.components.ViewSwitcherOption
import com.example.todowallapp.ui.components.ViewSwitcherPill
import com.example.todowallapp.ui.components.WaveformVisualizer
import com.example.todowallapp.ui.components.WeekStrip
import com.example.todowallapp.viewmodel.ThemeMode
import com.example.todowallapp.ui.components.buildHalfHourSlots
import com.example.todowallapp.ui.components.taskPickerFocusableCount
import com.example.todowallapp.ui.components.taskPickerHeaderFocusIndex
import com.example.todowallapp.ui.components.taskPickerResolveHeaderListIndex
import com.example.todowallapp.ui.components.taskPickerResolveTask
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val CalendarDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

@Composable
fun CalendarScreen(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    calendars: List<GoogleCalendar>,
    selectedCalendarId: String,
    hasCalendarScope: Boolean,
    isLoading: Boolean,
    error: String?,
    isOnline: Boolean,
    lastSyncTime: LocalDateTime?,
    lastSyncSuccess: Boolean?,
    currentPage: Int,
    onSwitchPage: (Int) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSelectCalendar: (String) -> Unit,
    onRequestCalendarAccess: () -> Unit,
    onOpenPromotionAt: (LocalDateTime) -> Unit,
    isPromotionDraftOpen: Boolean = false,
    slotAnchorTime: LocalDateTime? = null,
    pendingTasksByList: List<Pair<String, List<Task>>> = emptyList(),
    taskListTitleByTaskId: Map<String, String> = emptyMap(),
    taskUrgencyByTaskId: Map<String, TaskUrgency> = emptyMap(),
    onScheduleTaskAtTime: (Task, LocalDateTime) -> Unit = { _, _ -> },
    onScheduleTaskForRange: (Task, LocalDateTime, Int) -> Unit = { _, _, _ -> },
    onCompletePromotedTask: (String) -> Unit = {},
    onRescheduleEvent: (CalendarEvent) -> Unit = {},
    onDeleteCalendarEvent: (CalendarEvent) -> Unit = {},
    calendarViewMode: CalendarViewMode = CalendarViewMode.MONTH,
    eventsForRange: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
    onViewModeChange: (CalendarViewMode) -> Unit = {},
    onDaySelectedFromGrid: (LocalDate) -> Unit = {},
    weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
    // Settings
    themeMode: ThemeMode = ThemeMode.AUTO,
    lightStartHour: Int = 8,
    lightEndHour: Int = 19,
    sleepStartHour: Int = 23,
    sleepEndHour: Int = 7,
    syncIntervalMinutes: Int = 5,
    onThemeSettingsChange: (ThemeMode, Int, Int) -> Unit = { _, _, _ -> },
    onSleepScheduleChange: (Int, Int) -> Unit = { _, _ -> },
    onSyncIntervalChange: (Int) -> Unit = {},
    geminiKeyPresent: Boolean = false,
    isValidatingGeminiKey: Boolean = false,
    geminiKeyError: String? = null,
    onSaveGeminiKey: (String) -> Unit = {},
    onClearGeminiKey: () -> Unit = {},
    weatherLocation: String = "",
    weatherApiKeyPresent: Boolean = false,
    onSaveWeatherLocation: (String) -> Unit = {},
    onSaveWeatherApiKey: (String) -> Unit = {},
    onClearWeatherApiKey: () -> Unit = {},
    onSearchCities: (suspend (String) -> List<String>) = { emptyList() },
    onDismissCalendarError: () -> Unit = {},
    onSwitchMode: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onPlanDay: () -> Unit = {},
    onRefresh: (() -> Unit)? = null,
    // Day Organizer
    dayOrganizerState: DayOrganizerState = DayOrganizerState.Idle,
    onStartDayOrganizer: () -> Unit = {},
    onStopDayOrganizerListening: () -> Unit = {},
    onAcceptDayPlan: () -> Unit = {},
    onAdjustDayPlan: () -> Unit = {},
    onCancelDayOrganizer: () -> Unit = {},
    onRetryDayOrganizer: () -> Unit = {},
    onDayOrganizerFocusChange: (Int) -> Unit = {},
    // Unified voice
    voiceState: VoiceInputState = VoiceInputState.Idle,
    onStartUnifiedVoice: () -> Unit = {},
    onStopVoice: () -> Unit = {},
    onCancelVoice: () -> Unit = {},
    onConfirmVoice: (targetListId: String?) -> Unit = {},
    onDismissVoiceError: () -> Unit = {},
    voiceStateIdle: Boolean = true,
    hasSeenPlanDayHint: Boolean = true,
    onDismissPlanDayHint: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartUnifiedVoice()
    }

    val startDayOrganizerWithPermission = remember(onStartDayOrganizer, onDismissPlanDayHint) {
        {
            onDismissPlanDayHint()
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                    onStartDayOrganizer()
                }
                else -> {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val startUnifiedVoiceWithPermission = remember(onStartUnifiedVoice) {
        {
            when (PackageManager.PERMISSION_GRANTED) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) -> {
                    onStartUnifiedVoice()
                }
                else -> {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val slots = remember(selectedDate, events) { buildHalfHourSlots(selectedDate, events) }
    val weekStart = remember(selectedDate) { selectedDate.with(DayOfWeek.MONDAY) }
    val eventsByDate = remember(weekStart, events) {
        (0..6).associate { offset ->
            val date = weekStart.plusDays(offset.toLong())
            date to events.filter { event -> event.occursOn(date) }
        }
    }
    val displayMonth = remember(selectedDate) { selectedDate.withDayOfMonth(1) }
    var selectedSlotIndex by remember(selectedDate) { mutableIntStateOf(0) }
    val dims = rememberLayoutDimensions()
    var selectedEventId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var isSettingsFocused by remember { mutableStateOf(false) }
    var isPlanDayFocused by remember { mutableStateOf(false) }
    var isViewSwitcherFocused by remember { mutableStateOf(false) }
    var isDateBarFocused by remember { mutableStateOf(false) }
    var isEditingDate by remember { mutableStateOf(false) }
    var isWeatherExpanded by remember { mutableStateOf(false) }
    var isWeatherFocused by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }
    var taskPickerSlotTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var taskPickerFocusIndex by remember { mutableIntStateOf(0) }
    var taskPickerExpandedList by remember { mutableIntStateOf(0) }
    var pendingDragRange by remember { mutableStateOf<SlotDragRange?>(null) }
    var showEventAction by remember { mutableStateOf(false) }
    var eventActionTarget by remember { mutableStateOf<CalendarEvent?>(null) }
    var eventActionFocus by remember { mutableIntStateOf(0) }
    var voicePreviewFocus by remember { mutableIntStateOf(0) }

    // 3-day view state
    var threeDaySelectedColumn by remember { mutableIntStateOf(1) } // 0=yesterday, 1=today, 2=tomorrow
    var threeDaySlotIndex by remember(selectedDate) { mutableIntStateOf(0) }
    val threeDayAllEvents = remember(eventsForRange) { eventsForRange.values.flatten() }
    val threeDaySlots = remember(selectedDate, threeDayAllEvents) {
        val days = listOf(selectedDate.minusDays(1), selectedDate, selectedDate.plusDays(1))
        days.map { date ->
            val dayEvents = threeDayAllEvents.filter { it.occursOn(date) }
            buildHalfHourSlots(date, dayEvents)
        }
    }
    val threeDaySlotCount = threeDaySlots.firstOrNull()?.size ?: 0

    LaunchedEffect(slots.size) {
        selectedSlotIndex = selectedSlotIndex.coerceIn(0, slots.lastIndex.coerceAtLeast(0))
    }

    // Default to 3-day view in landscape
    LaunchedEffect(dims.isLandscape) {
        if (dims.isLandscape && calendarViewMode == CalendarViewMode.MONTH) {
            onViewModeChange(CalendarViewMode.THREE_DAY)
        }
    }

    LaunchedEffect(slotAnchorTime, selectedDate, slots) {
        val target = slotAnchorTime ?: return@LaunchedEffect
        if (target.toLocalDate() != selectedDate) return@LaunchedEffect
        val index = slots.indexOfFirst { slot ->
            val end = slot.start.plusMinutes(30)
            target >= slot.start && target < end
        }
        if (index >= 0) {
            selectedSlotIndex = index
            selectedEventId = null
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalWallColors.current.surfaceBlack)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false

                // Dismiss first-use hint on any encoder input
                if (!hasSeenPlanDayHint) onDismissPlanDayHint()

                // Voice overlay active — handle encoder navigation
                if (voiceState !is VoiceInputState.Idle) {
                    when (voiceState) {
                        is VoiceInputState.Listening -> {
                            if (keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                                onStopVoice()
                            }
                        }
                        is VoiceInputState.Preview -> {
                            when (keyEvent.key) {
                                Key.DirectionRight, Key.DirectionDown -> {
                                    voicePreviewFocus = 1
                                }
                                Key.DirectionLeft, Key.DirectionUp -> {
                                    voicePreviewFocus = 0
                                }
                                Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                    if (voicePreviewFocus == 0) {
                                        onConfirmVoice((voiceState as VoiceInputState.Preview).targetListId)
                                    } else {
                                        onCancelVoice()
                                    }
                                    voicePreviewFocus = 0
                                }
                                else -> {}
                            }
                        }
                        is VoiceInputState.Error -> {
                            if (keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                                onDismissVoiceError()
                            }
                        }
                        else -> {}
                    }
                    return@onKeyEvent true  // Consume all input when voice overlay is active
                }

                // Day Organizer active — consume all encoder input
                val orgState = dayOrganizerState
                if (orgState !is DayOrganizerState.Idle) {
                    if (keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                        when (orgState) {
                            is DayOrganizerState.Listening -> onStopDayOrganizerListening()
                            is DayOrganizerState.Adjusting -> onStopDayOrganizerListening()
                            is DayOrganizerState.PlanReady -> {
                                when (orgState.focusedAction) {
                                    0 -> onAcceptDayPlan()
                                    1 -> onAdjustDayPlan()
                                    2 -> onCancelDayOrganizer()
                                }
                            }
                            is DayOrganizerState.Error -> {
                                if (orgState.canRetry) onRetryDayOrganizer() else onCancelDayOrganizer()
                            }
                            else -> {}
                        }
                    } else if (keyEvent.key in listOf(Key.DirectionRight, Key.DirectionDown)) {
                        if (orgState is DayOrganizerState.PlanReady) {
                            val next = (orgState.focusedAction + 1).coerceAtMost(2)
                            onDayOrganizerFocusChange(next)
                        }
                    } else if (keyEvent.key in listOf(Key.DirectionLeft, Key.DirectionUp)) {
                        if (orgState is DayOrganizerState.PlanReady) {
                            val prev = (orgState.focusedAction - 1).coerceAtLeast(0)
                            onDayOrganizerFocusChange(prev)
                        }
                    }
                    return@onKeyEvent true  // Consume all input when overlay is active
                }

                // Settings panel open — encoder click dismisses, all other keys consumed
                if (showSettings) {
                    if (keyEvent.key in listOf(Key.Enter, Key.NumPadEnter, Key.Spacebar)) {
                        showSettings = false
                    }
                    return@onKeyEvent true
                }

                // Header focus: settings button
                if (isSettingsFocused) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            isSettingsFocused = false
                            // Skip Voice button if not visible
                            val voiceButtonVisible = voiceState is VoiceInputState.Idle &&
                                dayOrganizerState is DayOrganizerState.Idle
                            if (voiceButtonVisible) isPlanDayFocused = true
                            else isViewSwitcherFocused = true
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            // At very top — drill back to parent mode
                            isSettingsFocused = false
                            when (calendarViewMode) {
                                CalendarViewMode.WEEK -> onViewModeChange(CalendarViewMode.MONTH)
                                CalendarViewMode.DAY -> {
                                    isWeatherFocused = false
                                    onViewModeChange(CalendarViewMode.WEEK)
                                }
                                CalendarViewMode.THREE_DAY -> onViewModeChange(CalendarViewMode.MONTH)
                                CalendarViewMode.MONTH -> {} // already at top level
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            showSettings = true
                            true
                        }
                        else -> true
                    }
                }

                // Header focus: Plan Day button
                if (isPlanDayFocused) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionLeft, Key.DirectionUp -> {
                            isPlanDayFocused = false
                            isSettingsFocused = true
                            true
                        }
                        Key.DirectionRight, Key.DirectionDown -> {
                            isPlanDayFocused = false
                            isViewSwitcherFocused = true
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            startUnifiedVoiceWithPermission()
                            true
                        }
                        else -> true
                    }
                }

                // Header focus: view switcher
                if (isViewSwitcherFocused) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionLeft, Key.DirectionUp -> {
                            isViewSwitcherFocused = false
                            val voiceButtonVisible = voiceState is VoiceInputState.Idle &&
                                dayOrganizerState is DayOrganizerState.Idle
                            if (voiceButtonVisible) isPlanDayFocused = true
                            else isSettingsFocused = true
                            true
                        }
                        Key.DirectionRight, Key.DirectionDown -> {
                            isViewSwitcherFocused = false
                            if (calendarViewMode == CalendarViewMode.DAY || !hasCalendarScope) {
                                isDateBarFocused = true
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onSwitchPage(0)
                            true
                        }
                        else -> true
                    }
                }

                // MONTH mode: encoder navigates days; Enter drills to WEEK
                // UP from first day of month → header
                if (calendarViewMode == CalendarViewMode.MONTH) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            onSelectDate(selectedDate.plusDays(1)); true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            if (selectedDate.dayOfMonth == 1) {
                                isViewSwitcherFocused = true
                            } else {
                                onSelectDate(selectedDate.minusDays(1))
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onDaySelectedFromGrid(selectedDate)
                            onViewModeChange(CalendarViewMode.WEEK)
                            true
                        }
                        else -> false
                    }
                }

                // WEEK mode: encoder navigates days; Enter drills to DAY
                // UP past start of week → header (then UP past header → MONTH)
                if (calendarViewMode == CalendarViewMode.WEEK) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            onSelectDate(selectedDate.plusDays(1)); true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            val weekStartDate = selectedDate.with(DayOfWeek.MONDAY)
                            if (selectedDate <= weekStartDate) {
                                isViewSwitcherFocused = true
                            } else {
                                onSelectDate(selectedDate.minusDays(1))
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            onDaySelectedFromGrid(selectedDate)
                            onViewModeChange(CalendarViewMode.DAY)
                            true
                        }
                        else -> false
                    }
                }

                // THREE_DAY mode: up/down scrolls slots, click activates slot
                // UP from top-left → header
                if (calendarViewMode == CalendarViewMode.THREE_DAY) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            if (threeDaySlotIndex < threeDaySlotCount - 1) {
                                threeDaySlotIndex += 1
                            } else if (threeDaySelectedColumn < 2) {
                                threeDaySelectedColumn += 1
                                threeDaySlotIndex = 0
                            }
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            if (threeDaySlotIndex > 0) {
                                threeDaySlotIndex -= 1
                            } else if (threeDaySelectedColumn > 0) {
                                threeDaySelectedColumn -= 1
                                threeDaySlotIndex = (threeDaySlotCount - 1).coerceAtLeast(0)
                            } else {
                                // At top-left corner — go to header
                                isViewSwitcherFocused = true
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            val slot = threeDaySlots.getOrNull(threeDaySelectedColumn)
                                ?.getOrNull(threeDaySlotIndex)
                            if (slot != null) {
                                if (slot.events.isEmpty()) {
                                    showTaskPicker = true
                                    taskPickerSlotTime = slot.start
                                    taskPickerFocusIndex = 0
                                    taskPickerExpandedList = 0
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }

                // Task picker overlay intercepts keys when visible
                if (showTaskPicker) {
                    val focusCount = taskPickerFocusableCount(pendingTasksByList, taskPickerExpandedList)
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            if (taskPickerFocusIndex > 0) {
                                taskPickerFocusIndex -= 1
                                // Auto-expand if focus lands on a different list header
                                val headerIdx = taskPickerResolveHeaderListIndex(
                                    pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                                )
                                if (headerIdx >= 0 && headerIdx != taskPickerExpandedList) {
                                    val oldExpanded = taskPickerExpandedList
                                    taskPickerExpandedList = headerIdx
                                    // Recalc focus: we moved up onto a header that just expanded.
                                    // The header's position shifts because the old list collapsed.
                                    taskPickerFocusIndex = taskPickerHeaderFocusIndex(
                                        pendingTasksByList, headerIdx, headerIdx
                                    )
                                }
                            }
                            return@onKeyEvent true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (taskPickerFocusIndex < focusCount - 1) {
                                taskPickerFocusIndex += 1
                                val headerIdx = taskPickerResolveHeaderListIndex(
                                    pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                                )
                                if (headerIdx >= 0 && headerIdx != taskPickerExpandedList) {
                                    taskPickerExpandedList = headerIdx
                                    taskPickerFocusIndex = taskPickerHeaderFocusIndex(
                                        pendingTasksByList, headerIdx, headerIdx
                                    )
                                }
                            }
                            return@onKeyEvent true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            val selectedTask = taskPickerResolveTask(
                                pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                            )
                            if (selectedTask != null) {
                                // Task row confirmed — schedule it
                                val time = taskPickerSlotTime
                                if (time != null) {
                                    val dragDuration = pendingDragRange?.durationMinutes
                                    if (dragDuration != null && dragDuration > 0) {
                                        onScheduleTaskForRange(selectedTask, time, dragDuration)
                                    } else {
                                        onScheduleTaskAtTime(selectedTask, time)
                                    }
                                }
                                showTaskPicker = false
                                taskPickerSlotTime = null
                                taskPickerFocusIndex = 0
                                taskPickerExpandedList = 0
                                pendingDragRange = null
                            } else {
                                // Header confirmed — move focus to first task in this list
                                val headerIdx = taskPickerResolveHeaderListIndex(
                                    pendingTasksByList, taskPickerExpandedList, taskPickerFocusIndex
                                )
                                if (headerIdx >= 0) {
                                    taskPickerExpandedList = headerIdx
                                    taskPickerFocusIndex = taskPickerHeaderFocusIndex(
                                        pendingTasksByList, headerIdx, headerIdx
                                    ) + 1 // move past header to first task
                                }
                            }
                            return@onKeyEvent true
                        }
                        Key.Escape, Key.Back -> {
                            showTaskPicker = false
                            taskPickerSlotTime = null
                            taskPickerFocusIndex = 0
                            taskPickerExpandedList = 0
                            pendingDragRange = null
                            return@onKeyEvent true
                        }
                        else -> {
                            showTaskPicker = false
                            taskPickerSlotTime = null
                            taskPickerFocusIndex = 0
                            taskPickerExpandedList = 0
                            return@onKeyEvent true
                        }
                    }
                }

                // Event action menu intercepts keys when visible
                if (showEventAction && eventActionTarget != null) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            if (eventActionFocus > 0) eventActionFocus -= 1
                            return@onKeyEvent true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (eventActionFocus < EVENT_ACTION_COUNT - 1) eventActionFocus += 1
                            return@onKeyEvent true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            val target = eventActionTarget!!
                            when (eventActionFocus) {
                                0 -> target.sourceTaskId?.let { onCompletePromotedTask(it) }
                                1 -> onRescheduleEvent(target)
                                2 -> onDeleteCalendarEvent(target)
                            }
                            showEventAction = false
                            eventActionTarget = null
                            eventActionFocus = 0
                            return@onKeyEvent true
                        }
                        else -> {
                            showEventAction = false
                            eventActionTarget = null
                            eventActionFocus = 0
                            return@onKeyEvent true
                        }
                    }
                }

                if (!hasCalendarScope) {
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            if (isDateBarFocused) {
                                if (isEditingDate) {
                                    onSelectDate(selectedDate.plusDays(1))
                                } else {
                                    isDateBarFocused = false
                                    isViewSwitcherFocused = true
                                }
                            } else {
                                isDateBarFocused = true
                            }
                            return@onKeyEvent true
                        }

                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (isDateBarFocused) {
                                if (isEditingDate) {
                                    onSelectDate(selectedDate.minusDays(1))
                                } else {
                                    isDateBarFocused = false
                                }
                            }
                            return@onKeyEvent true
                        }

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (isDateBarFocused) {
                                isEditingDate = !isEditingDate
                            } else {
                                onRequestCalendarAccess()
                            }
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }

                // DAY mode handler — header focus (settings/viewswitcher) handled above
                when (keyEvent.key) {
                    Key.DirectionUp, Key.DirectionRight -> {
                        if (isDateBarFocused) {
                            if (isEditingDate) {
                                onSelectDate(selectedDate.plusDays(1))
                            } else {
                                isDateBarFocused = false
                                isViewSwitcherFocused = true
                            }
                            true
                        } else if (isWeatherFocused) {
                            isWeatherFocused = false
                            isDateBarFocused = true
                            true
                        } else if (selectedSlotIndex == 0) {
                            // Stop at weather row before date bar
                            isWeatherFocused = true
                            isWeatherExpanded = true
                            true
                        } else {
                            selectedSlotIndex -= 1
                            selectedEventId = null
                            true
                        }
                    }

                    Key.DirectionDown, Key.DirectionLeft -> {
                        if (isDateBarFocused) {
                            if (isEditingDate) {
                                onSelectDate(selectedDate.minusDays(1))
                            } else {
                                isDateBarFocused = false
                                isEditingDate = false
                                // Stop at weather row before slots
                                isWeatherFocused = true
                                isWeatherExpanded = true
                            }
                            true
                        } else if (isWeatherFocused) {
                            isWeatherFocused = false
                            isWeatherExpanded = false
                            if (slots.isNotEmpty()) {
                                selectedSlotIndex = 0
                                selectedEventId = null
                            }
                            true
                        } else if (selectedSlotIndex < slots.lastIndex) {
                            selectedSlotIndex += 1
                            selectedEventId = null
                            true
                        } else {
                            true
                        }
                    }

                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                        if (isWeatherFocused) {
                            isWeatherExpanded = !isWeatherExpanded
                            true
                        } else if (isDateBarFocused) {
                            if (isEditingDate) {
                                // While editing, Enter cycles through available calendars
                                val writableCalendars = calendars.filter { it.isWritable }
                                val nextCalendar = writableCalendars.firstOrNull { it.id != selectedCalendarId }
                                if (nextCalendar != null) {
                                    onSelectCalendar(nextCalendar.id)
                                } else {
                                    isEditingDate = false
                                }
                            } else {
                                isEditingDate = true
                            }
                            true
                        } else {
                            val slot = slots.getOrNull(selectedSlotIndex)
                            if (slot != null) {
                                val selectedEvent = slot.events.firstOrNull { it.id == selectedEventId }
                                if (selectedEvent != null) {
                                    if (selectedEvent.isPromotedTask) {
                                        eventActionTarget = selectedEvent
                                        eventActionFocus = 0
                                        showEventAction = true
                                    }
                                    // Non-promoted events: no-op (open in Google Calendar hint handled visually)
                                    true
                                } else if (slot.events.isNotEmpty()) {
                                    selectedEventId = slot.events.first().id
                                    true
                                } else {
                                    if (isPromotionDraftOpen) {
                                        onOpenPromotionAt(slot.start)
                                    } else {
                                        showTaskPicker = true
                                        taskPickerSlotTime = slot.start
                                        taskPickerFocusIndex = 0
                                        taskPickerExpandedList = 0
                                    }
                                    true
                                }
                            } else {
                                true
                            }
                        }
                    }

                    else -> false
                }
            }
            .padding(
                top = dims.topPadding,
                start = if (calendarViewMode == CalendarViewMode.DAY) dims.dayHorizontalPadding else dims.horizontalPadding,
                end = if (calendarViewMode == CalendarViewMode.DAY) dims.dayHorizontalPadding else dims.horizontalPadding,
                bottom = 12.dp
            ),
        verticalArrangement = Arrangement.spacedBy(dims.calendarElementSpacing)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ClockHeader(
                isAmbientMode = false,
                isOnline = isOnline,
                lastSyncTime = lastSyncTime,
                lastSyncSuccess = lastSyncSuccess,
                onSyncClick = onRefresh
            )
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CalendarSettingsButton(
                    isFocused = isSettingsFocused,
                    onClick = { showSettings = true }
                )
                if (voiceState is VoiceInputState.Idle && dayOrganizerState is DayOrganizerState.Idle) {
                    VoiceButton(
                        isFocused = isPlanDayFocused,
                        onClick = startUnifiedVoiceWithPermission
                    )
                }
                ViewSwitcherPill(
                    options = listOf(
                        ViewSwitcherOption(key = "tasks", label = "Tasks"),
                        ViewSwitcherOption(key = "calendar", label = "Calendar")
                    ),
                    selectedKey = if (currentPage == 0) "tasks" else "calendar",
                    onSelect = { key ->
                        isViewSwitcherFocused = true
                        onSwitchPage(if (key == "tasks") 0 else 1)
                    },
                    modifier = if (isViewSwitcherFocused) {
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(2.dp, LocalWallColors.current.accentPrimary.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                            .padding(2.dp)
                    } else {
                        Modifier
                    }
                )
            }

            // First-use discovery hint for Voice button
            val voiceBtnVisible = voiceState is VoiceInputState.Idle &&
                dayOrganizerState is DayOrganizerState.Idle
            if (voiceBtnVisible && !hasSeenPlanDayHint) {
                var hintVisible by remember { mutableStateOf(true) }
                val hintAlpha by animateFloatAsState(
                    targetValue = if (hintVisible) 0.5f else 0f,
                    animationSpec = tween(600),
                    label = "hintAlpha"
                )
                LaunchedEffect(Unit) {
                    delay(8000)
                    hintVisible = false
                    delay(600) // wait for fade out
                    onDismissPlanDayHint()
                }
                if (hintAlpha > 0f) {
                    Text(
                        text = "Navigate here & click to plan your day",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalWallColors.current.textMuted.copy(alpha = hintAlpha),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 8.dp, top = 4.dp)
                    )
                }
            }
        }

        DateAndCalendarBar(
            selectedDate = selectedDate,
            calendars = calendars,
            selectedCalendarId = selectedCalendarId,
            hasCalendarScope = hasCalendarScope,
            isFocused = isDateBarFocused,
            isEditing = isEditingDate,
            calendarViewMode = calendarViewMode,
            onSelectDate = onSelectDate,
            onSelectCalendar = onSelectCalendar
        )

        // Calendar sub-mode switcher
        ViewSwitcherPill(
            options = listOf(
                ViewSwitcherOption(key = "MONTH", label = "Month"),
                ViewSwitcherOption(key = "WEEK", label = "Week"),
                ViewSwitcherOption(key = "THREE_DAY", label = "3-Day"),
                ViewSwitcherOption(key = "DAY", label = "Day")
            ),
            selectedKey = calendarViewMode.name,
            onSelect = { key -> onViewModeChange(CalendarViewMode.valueOf(key)) }
        )

        // WeekStrip only useful in DAY mode
        if (calendarViewMode == CalendarViewMode.DAY) {
            WeekStrip(
                startDate = weekStart,
                selectedDate = selectedDate,
                eventsByDate = eventsByDate,
                taskUrgencyByTaskId = taskUrgencyByTaskId,
                onDateSelected = onSelectDate
            )
            // Visual break between chrome and timeline content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LocalWallColors.current.dividerColor)
            )
        }

        when {
            !hasCalendarScope -> {
                AccessRequiredCard(onRequestCalendarAccess = onRequestCalendarAccess)
            }

            isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalWallColors.current.accentPrimary)
                }
            }

            else -> {
                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalWallColors.current.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { onDismissCalendarError() }
                    )
                }
                AnimatedContent(
                    targetState = calendarViewMode,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    },
                    label = "calendarViewMode",
                    modifier = Modifier.fillMaxSize()
                ) { mode ->
                    when (mode) {
                        CalendarViewMode.MONTH -> CalendarMonthView(
                            displayMonth = displayMonth,
                            selectedDate = selectedDate,
                            eventsForRange = eventsForRange,
                            weatherForecast = weatherForecast,
                            onDaySelected = { date ->
                                onDaySelectedFromGrid(date)
                                onViewModeChange(CalendarViewMode.WEEK)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        CalendarViewMode.WEEK -> CalendarWeekView(
                            eventsForRange = eventsForRange,
                            selectedDate = selectedDate,
                            weatherForecast = weatherForecast,
                            onDaySelected = { date ->
                                onDaySelectedFromGrid(date)
                                onViewModeChange(CalendarViewMode.DAY)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        CalendarViewMode.THREE_DAY -> Calendar3DayView(
                            centerDate = selectedDate,
                            allEvents = eventsForRange.values.flatten(),
                            selectedDayOffset = threeDaySelectedColumn,
                            selectedSlotIndex = threeDaySlotIndex,
                            selectedEventId = selectedEventId,
                            taskListTitleByTaskId = taskListTitleByTaskId,
                            taskUrgencyByTaskId = taskUrgencyByTaskId,
                            onSlotActivated = { date, time ->
                                val daySlots = threeDaySlots.getOrNull(
                                    listOf(selectedDate.minusDays(1), selectedDate, selectedDate.plusDays(1))
                                        .indexOf(date)
                                )
                                val slot = daySlots?.firstOrNull { it.start == time }
                                if (slot != null && slot.events.isEmpty()) {
                                    showTaskPicker = true
                                    taskPickerSlotTime = time
                                    taskPickerFocusIndex = 0
                                    taskPickerExpandedList = 0
                                }
                            },
                            onEventActivated = { event ->
                                if (event.isPromotedTask) {
                                    eventActionTarget = event
                                    eventActionFocus = 0
                                    showEventAction = true
                                } else {
                                    selectedEventId = event.id
                                }
                            },
                            onSlotRangeSelected = { range ->
                                pendingDragRange = range
                                showTaskPicker = true
                                taskPickerSlotTime = range.startTime
                                taskPickerFocusIndex = 0
                                taskPickerExpandedList = 0
                            },
                            weatherForecast = weatherForecast,
                            modifier = Modifier.fillMaxSize()
                        )
                        CalendarViewMode.DAY -> CalendarDayView(
                            date = selectedDate,
                            events = events,
                            selectedSlotStart = if (!isWeatherFocused && !isDateBarFocused) slots.getOrNull(selectedSlotIndex)?.start else null,
                            selectedEventId = selectedEventId,
                            taskListTitleByTaskId = taskListTitleByTaskId,
                            taskUrgencyByTaskId = taskUrgencyByTaskId,
                            onSlotActivated = { time ->
                                val idx = slots.indexOfFirst { it.start == time }
                                if (idx >= 0) selectedSlotIndex = idx
                                selectedEventId = null
                                val slot = slots.getOrNull(idx)
                                if (slot != null && slot.events.isEmpty()) {
                                    if (isPromotionDraftOpen) {
                                        onOpenPromotionAt(time)
                                    } else {
                                        showTaskPicker = true
                                        taskPickerSlotTime = time
                                        taskPickerFocusIndex = 0
                                        taskPickerExpandedList = 0
                                    }
                                } else {
                                    onOpenPromotionAt(time)
                                }
                            },
                            onEventActivated = { event ->
                                if (event.isPromotedTask) {
                                    eventActionTarget = event
                                    eventActionFocus = 0
                                    showEventAction = true
                                } else {
                                    selectedEventId = event.id
                                }
                            },
                            onSlotRangeSelected = { range ->
                                pendingDragRange = range
                                showTaskPicker = true
                                taskPickerSlotTime = range.startTime
                                taskPickerFocusIndex = 0
                                taskPickerExpandedList = 0
                            },
                            weatherForecast = weatherForecast,
                            isWeatherExpanded = isWeatherExpanded,
                            onToggleWeatherExpanded = { isWeatherExpanded = !isWeatherExpanded },
                            isWeatherFocused = isWeatherFocused,
                            geminiKeyPresent = geminiKeyPresent,
                            dayOrganizerIdle = dayOrganizerState is DayOrganizerState.Idle,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    TaskPickerOverlay(
        visible = showTaskPicker,
        tasksByList = pendingTasksByList,
        focusedIndex = taskPickerFocusIndex,
        expandedListIndex = taskPickerExpandedList,
        onFocusIndex = { taskPickerFocusIndex = it },
        onExpandList = { listIdx ->
            taskPickerExpandedList = listIdx
            taskPickerFocusIndex = taskPickerHeaderFocusIndex(
                pendingTasksByList, listIdx, listIdx
            )
        },
        onSelectTask = { task ->
            val time = taskPickerSlotTime
            if (time != null) {
                val dragDuration = pendingDragRange?.durationMinutes
                if (dragDuration != null && dragDuration > 0) {
                    onScheduleTaskForRange(task, time, dragDuration)
                } else {
                    onScheduleTaskAtTime(task, time)
                }
            }
            showTaskPicker = false
            taskPickerSlotTime = null
            taskPickerFocusIndex = 0
            taskPickerExpandedList = 0
            pendingDragRange = null
        },
        onDismiss = {
            showTaskPicker = false
            taskPickerSlotTime = null
            taskPickerFocusIndex = 0
            taskPickerExpandedList = 0
            pendingDragRange = null
        }
    )

    if (showEventAction && eventActionTarget != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.24f))
                .clickable {
                    showEventAction = false
                    eventActionTarget = null
                    eventActionFocus = 0
                },
            contentAlignment = Alignment.Center
        ) {
            EventActionMenu(
                event = eventActionTarget!!,
                focusedAction = eventActionFocus,
                onFocusAction = { eventActionFocus = it },
                onCompleteTask = {
                    eventActionTarget?.sourceTaskId?.let { onCompletePromotedTask(it) }
                    showEventAction = false
                    eventActionTarget = null
                    eventActionFocus = 0
                },
                onReschedule = {
                    eventActionTarget?.let { onRescheduleEvent(it) }
                    showEventAction = false
                    eventActionTarget = null
                    eventActionFocus = 0
                },
                onRemoveEvent = {
                    eventActionTarget?.let { onDeleteCalendarEvent(it) }
                    showEventAction = false
                    eventActionTarget = null
                    eventActionFocus = 0
                },
                onDismiss = {
                    showEventAction = false
                    eventActionTarget = null
                    eventActionFocus = 0
                },
                modifier = Modifier.clickable(enabled = false, onClick = {})
            )
        }
    }
        // Settings overlay
        if (showSettings) {
            SettingsPanel(
                themeMode = themeMode,
                lightStartHour = lightStartHour,
                lightEndHour = lightEndHour,
                sleepStartHour = sleepStartHour,
                sleepEndHour = sleepEndHour,
                syncIntervalMinutes = syncIntervalMinutes,
                onThemeSettingsChange = onThemeSettingsChange,
                onSleepScheduleChange = onSleepScheduleChange,
                onSyncIntervalChange = onSyncIntervalChange,
                geminiKeyPresent = geminiKeyPresent,
                isValidatingGeminiKey = isValidatingGeminiKey,
                geminiKeyError = geminiKeyError,
                onSaveGeminiKey = onSaveGeminiKey,
                onClearGeminiKey = onClearGeminiKey,
                weatherLocation = weatherLocation,
                weatherApiKeyPresent = weatherApiKeyPresent,
                onSaveWeatherLocation = onSaveWeatherLocation,
                onSaveWeatherApiKey = onSaveWeatherApiKey,
                onClearWeatherApiKey = onClearWeatherApiKey,
                onSearchCities = onSearchCities,
                onSwitchMode = onSwitchMode,
                onSignOut = onSignOut,
                onDismiss = { showSettings = false },
                onPlanDay = {
                    startDayOrganizerWithPermission()
                },
                hasCalendarScope = hasCalendarScope
            )
        }

        // Day Organizer Overlay
        DayOrganizerOverlay(
            state = dayOrganizerState,
            onStopListening = onStopDayOrganizerListening,
            onAccept = onAcceptDayPlan,
            onAdjust = onAdjustDayPlan,
            onCancel = onCancelDayOrganizer,
            onRetry = onRetryDayOrganizer
        )

        // Task voice overlay (when unified voice routes to task action)
        AnimatedVisibility(
            visible = voiceState !is VoiceInputState.Idle,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                when (val state = voiceState) {
                    is VoiceInputState.Listening -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WaveformVisualizer(
                                amplitudeLevel = state.amplitudeLevel,
                                isActive = true,
                                modifier = Modifier.size(200.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            var showHint by remember { mutableStateOf(true) }
                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(5_000)
                                showHint = false
                            }
                            AnimatedVisibility(
                                visible = showHint,
                                exit = fadeOut(tween(800))
                            ) {
                                Text(
                                    text = "add a task, or say \u201Cplan my day\u201D to schedule",
                                    color = LocalWallColors.current.textMuted.copy(alpha = 0.45f),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    is VoiceInputState.Processing -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = LocalWallColors.current.accentPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Processing...", color = LocalWallColors.current.textSecondary)
                        }
                    }
                    is VoiceInputState.Preview -> {
                        val response = state.response
                        val intentLabel = when (response.intent) {
                            VoiceIntent.ADD -> if (response.tasks.size > 1) "Draft Tasks" else "Draft Task"
                            VoiceIntent.COMPLETE -> "Complete Task"
                            VoiceIntent.DELETE -> "Delete Task"
                            VoiceIntent.RESCHEDULE -> "Reschedule Task"
                            VoiceIntent.QUERY -> "Tasks Found"
                            VoiceIntent.AMEND -> "Amended Task"
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(0.7f).padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = LocalWallColors.current.surfaceCard
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    intentLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalWallColors.current.textPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                response.tasks.forEach { task ->
                                    Text(
                                        task.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = LocalWallColors.current.textPrimary
                                    )
                                }
                                if (response.clarification != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        response.clarification,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalWallColors.current.textMuted
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { onConfirmVoice(state.targetListId) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LocalWallColors.current.accentPrimary
                                        )
                                    ) { Text("Confirm") }
                                    OutlinedButton(onClick = onCancelVoice) { Text("Cancel") }
                                }
                            }
                        }
                    }
                    is VoiceInputState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(0.5f).padding(32.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = LocalWallColors.current.surfaceCard
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    state.message,
                                    color = LocalWallColors.current.urgencyOverdue,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = onDismissVoiceError) { Text("Dismiss") }
                            }
                        }
                    }
                    is VoiceInputState.Idle -> {} // Not shown (AnimatedVisibility handles this)
                }
            }
        }
    } // end outer Box

    BackHandler(enabled = showSettings) { showSettings = false }
}

@Composable
private fun DateAndCalendarBar(
    selectedDate: LocalDate,
    calendars: List<GoogleCalendar>,
    selectedCalendarId: String,
    hasCalendarScope: Boolean,
    isFocused: Boolean,
    isEditing: Boolean,
    calendarViewMode: CalendarViewMode = CalendarViewMode.DAY,
    onSelectDate: (LocalDate) -> Unit,
    onSelectCalendar: (String) -> Unit
) {
    val shape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp)
    val selectedCalendar = calendars.firstOrNull { it.id == selectedCalendarId }

    val borderColor by animateColorAsState(
        targetValue = when {
            isEditing -> LocalWallColors.current.accentPrimary
            isFocused -> LocalWallColors.current.accentPrimary.copy(alpha = 0.6f)
            else -> LocalWallColors.current.borderColor
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "dateBarBorder"
    )

    val borderWidth = if (isFocused || isEditing) 1.5.dp else 1.dp

    val dateTextColor by animateColorAsState(
        targetValue = if (isEditing) LocalWallColors.current.accentPrimary else LocalWallColors.current.textPrimary,
        animationSpec = tween(WallAnimations.SHORT),
        label = "dateBarTextColor"
    )

    val dateLabel = when (calendarViewMode) {
        CalendarViewMode.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        else -> selectedDate.format(CalendarDateFormatter)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalWallColors.current.surfaceCard, shape)
            .border(borderWidth, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderButton(text = "<", onClick = {
                if (calendarViewMode == CalendarViewMode.MONTH) {
                    onSelectDate(selectedDate.minusMonths(1))
                } else {
                    onSelectDate(selectedDate.minusDays(1))
                }
            })
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                color = dateTextColor
            )
            HeaderButton(text = ">", onClick = {
                if (calendarViewMode == CalendarViewMode.MONTH) {
                    onSelectDate(selectedDate.plusMonths(1))
                } else {
                    onSelectDate(selectedDate.plusDays(1))
                }
            })
        }

        if (hasCalendarScope) {
            val writableCalendars = calendars.filter { it.isWritable }
            val nextCalendar = writableCalendars.firstOrNull { it.id != selectedCalendarId }
            HeaderButton(
                text = selectedCalendar?.title ?: "Calendar",
                onClick = { nextCalendar?.let { onSelectCalendar(it.id) } }
            )
        }
    }
}

@Composable
private fun HeaderButton(
    text: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .background(LocalWallColors.current.surfaceBlack.copy(alpha = 0.35f), shape)
            .border(1.dp, LocalWallColors.current.borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = LocalWallColors.current.textPrimary
        )
    }
}

@Composable
private fun AccessRequiredCard(
    onRequestCalendarAccess: () -> Unit
) {
    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalWallColors.current.surfaceCard, shape)
            .border(1.dp, LocalWallColors.current.accentPrimary.copy(alpha = 0.55f), shape)
            .clickable(onClick = onRequestCalendarAccess)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Calendar access needed",
            style = MaterialTheme.typography.titleMedium,
            color = LocalWallColors.current.textPrimary
        )
        Text(
            text = "Select or tap to grant Google Calendar permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWallColors.current.textSecondary
        )
        Text(
            text = "Grant access",
            style = MaterialTheme.typography.labelLarge,
            color = LocalWallColors.current.accentPrimary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun CalendarSettingsButton(isFocused: Boolean, onClick: () -> Unit) {
    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.surfaceCard.copy(alpha = 0.6f) else colors.surfaceCard.copy(alpha = 0.2f),
        animationSpec = tween(WallAnimations.SHORT),
        label = "settingsBg"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) colors.accentPrimary.copy(alpha = 0.5f) else colors.rimGloss, shape)
            .clickable(onClick = onClick, interactionSource = interactionSource, indication = null),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "Settings",
            tint = if (isFocused) colors.accentPrimary else colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun VoiceButton(isFocused: Boolean, onClick: () -> Unit) {
    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.surfaceCard.copy(alpha = 0.6f) else colors.surfaceCard.copy(alpha = 0.2f),
        animationSpec = tween(WallAnimations.SHORT),
        label = "planDayBg"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) colors.accentPrimary.copy(alpha = 0.5f) else colors.rimGloss, shape)
            .clickable(onClick = onClick, interactionSource = interactionSource, indication = null),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Mic,
            contentDescription = "Voice input",
            tint = if (isFocused) colors.accentPrimary else colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}

