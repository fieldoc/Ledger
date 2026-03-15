package com.example.todowallapp.ui.screens

import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.occursOn
import androidx.compose.animation.AnimatedContent
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
import com.example.todowallapp.ui.components.ViewSwitcherOption
import com.example.todowallapp.ui.components.ViewSwitcherPill
import com.example.todowallapp.ui.components.WeekStrip
import com.example.todowallapp.ui.components.buildHalfHourSlots
import com.example.todowallapp.ui.components.taskPickerRowCount
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
    modifier: Modifier = Modifier
) {
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
    var isViewSwitcherFocused by remember { mutableStateOf(false) }
    var isDateBarFocused by remember { mutableStateOf(false) }
    var isEditingDate by remember { mutableStateOf(false) }
    var isWeatherExpanded by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }
    var taskPickerSlotTime by remember { mutableStateOf<LocalDateTime?>(null) }
    var taskPickerFocusIndex by remember { mutableIntStateOf(0) }
    var pendingDragRange by remember { mutableStateOf<SlotDragRange?>(null) }
    var showEventAction by remember { mutableStateOf(false) }
    var eventActionTarget by remember { mutableStateOf<CalendarEvent?>(null) }
    var eventActionFocus by remember { mutableIntStateOf(0) }

    // 3-day view state
    var threeDaySelectedColumn by remember { mutableIntStateOf(1) } // 0=yesterday, 1=today, 2=tomorrow
    var threeDaySlotIndex by remember(selectedDate) { mutableIntStateOf(0) }
    val threeDaySlots = remember(selectedDate, events) {
        val days = listOf(selectedDate.minusDays(1), selectedDate, selectedDate.plusDays(1))
        days.map { date ->
            val dayEvents = events.filter { it.occursOn(date) }
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

                // MONTH mode: encoder navigates days; Enter drills to WEEK
                if (calendarViewMode == CalendarViewMode.MONTH) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            onSelectDate(selectedDate.plusDays(1)); true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            onSelectDate(selectedDate.minusDays(1)); true
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
                if (calendarViewMode == CalendarViewMode.WEEK) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            onSelectDate(selectedDate.plusDays(1)); true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            onSelectDate(selectedDate.minusDays(1)); true
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
                if (calendarViewMode == CalendarViewMode.THREE_DAY) {
                    return@onKeyEvent when (keyEvent.key) {
                        Key.DirectionRight, Key.DirectionDown -> {
                            if (threeDaySlotIndex < threeDaySlotCount - 1) {
                                threeDaySlotIndex += 1
                            }
                            true
                        }
                        Key.DirectionLeft, Key.DirectionUp -> {
                            if (threeDaySlotIndex > 0) {
                                threeDaySlotIndex -= 1
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            val dayDate = listOf(
                                selectedDate.minusDays(1),
                                selectedDate,
                                selectedDate.plusDays(1)
                            )[threeDaySelectedColumn]
                            val slot = threeDaySlots.getOrNull(threeDaySelectedColumn)
                                ?.getOrNull(threeDaySlotIndex)
                            if (slot != null) {
                                if (slot.events.isEmpty()) {
                                    showTaskPicker = true
                                    taskPickerSlotTime = slot.start
                                    taskPickerFocusIndex = 0
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }

                // Task picker overlay intercepts keys when visible
                if (showTaskPicker) {
                    val rowCount = taskPickerRowCount(pendingTasksByList)
                    when (keyEvent.key) {
                        Key.DirectionUp, Key.DirectionRight -> {
                            if (taskPickerFocusIndex > 0) {
                                taskPickerFocusIndex -= 1
                            }
                            return@onKeyEvent true
                        }
                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (taskPickerFocusIndex < rowCount - 1) {
                                taskPickerFocusIndex += 1
                            }
                            return@onKeyEvent true
                        }
                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            // Find the task at focusedIndex
                            var count = 0
                            val selectedTask = pendingTasksByList.firstNotNullOfOrNull { (_, tasks) ->
                                tasks.firstOrNull { _ ->
                                    val match = count == taskPickerFocusIndex
                                    count++
                                    match
                                }
                            }
                            if (selectedTask != null && taskPickerSlotTime != null) {
                                onScheduleTaskAtTime(selectedTask, taskPickerSlotTime!!)
                            }
                            showTaskPicker = false
                            taskPickerSlotTime = null
                            taskPickerFocusIndex = 0
                            return@onKeyEvent true
                        }
                        else -> {
                            showTaskPicker = false
                            taskPickerSlotTime = null
                            taskPickerFocusIndex = 0
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
                        Key.Escape -> {
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
                            } else if (!isViewSwitcherFocused) {
                                isDateBarFocused = true
                            }
                            return@onKeyEvent true
                        }

                        Key.DirectionDown, Key.DirectionLeft -> {
                            if (isViewSwitcherFocused) {
                                isViewSwitcherFocused = false
                                isDateBarFocused = true
                            } else if (isDateBarFocused) {
                                if (isEditingDate) {
                                    onSelectDate(selectedDate.minusDays(1))
                                } else {
                                    isDateBarFocused = false
                                }
                            }
                            return@onKeyEvent true
                        }

                        Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                            if (isViewSwitcherFocused) {
                                onSwitchPage(0)
                            } else if (isDateBarFocused) {
                                isEditingDate = !isEditingDate
                            } else {
                                onRequestCalendarAccess()
                            }
                            return@onKeyEvent true
                        }
                    }
                    return@onKeyEvent false
                }

                when (keyEvent.key) {
                    Key.DirectionUp, Key.DirectionRight -> {
                        if (isViewSwitcherFocused) {
                            true
                        } else if (isDateBarFocused) {
                            if (isEditingDate) {
                                onSelectDate(selectedDate.plusDays(1))
                            } else {
                                isDateBarFocused = false
                                isViewSwitcherFocused = true
                            }
                            true
                        } else if (selectedSlotIndex == 0) {
                            isDateBarFocused = true
                            true
                        } else {
                            selectedSlotIndex -= 1
                            selectedEventId = null
                            true
                        }
                    }

                    Key.DirectionDown, Key.DirectionLeft -> {
                        if (isViewSwitcherFocused) {
                            isViewSwitcherFocused = false
                            isDateBarFocused = true
                            true
                        } else if (isDateBarFocused) {
                            if (isEditingDate) {
                                onSelectDate(selectedDate.minusDays(1))
                            } else {
                                isDateBarFocused = false
                                isEditingDate = false
                                if (slots.isNotEmpty()) {
                                    selectedSlotIndex = 0
                                    selectedEventId = null
                                }
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
                        if (isViewSwitcherFocused) {
                            onSwitchPage(0)
                            true
                        } else if (isDateBarFocused) {
                            isEditingDate = !isEditingDate
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
            .padding(top = dims.topPadding, start = dims.horizontalPadding, end = dims.horizontalPadding, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(dims.calendarElementSpacing)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ClockHeader(
                isAmbientMode = false,
                isOnline = isOnline,
                lastSyncTime = lastSyncTime,
                lastSyncSuccess = lastSyncSuccess
            )
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
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .then(
                        if (isViewSwitcherFocused) {
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(2.dp, LocalWallColors.current.accentPrimary.copy(alpha = 0.8f), RoundedCornerShape(999.dp))
                                .padding(2.dp)
                        } else {
                            Modifier
                        }
                    )
            )
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
                        modifier = Modifier.padding(horizontal = 4.dp)
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
                            allEvents = events,
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
                            },
                            weatherForecast = weatherForecast,
                            modifier = Modifier.fillMaxSize()
                        )
                        CalendarViewMode.DAY -> CalendarDayView(
                            date = selectedDate,
                            events = events,
                            selectedSlotStart = slots.getOrNull(selectedSlotIndex)?.start,
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
                            },
                            weatherForecast = weatherForecast,
                            isWeatherExpanded = isWeatherExpanded,
                            onToggleWeatherExpanded = { isWeatherExpanded = !isWeatherExpanded },
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
        onFocusIndex = { taskPickerFocusIndex = it },
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
            pendingDragRange = null
        },
        onDismiss = {
            showTaskPicker = false
            taskPickerSlotTime = null
            taskPickerFocusIndex = 0
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
    } // end outer Box
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



