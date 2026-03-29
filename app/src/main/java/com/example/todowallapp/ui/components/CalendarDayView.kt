package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val SlotTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class CalendarTimeSlot(
    val start: LocalDateTime,
    val events: List<CalendarEvent>
)

sealed interface CalendarSlotItem {
    data class SingleSlot(val slot: CalendarTimeSlot) : CalendarSlotItem

    data class CompressedRange(
        val startTime: LocalDateTime,
        val endTime: LocalDateTime,
        val slotCount: Int,
        val slots: List<CalendarTimeSlot>
    ) : CalendarSlotItem
}

fun buildHalfHourSlots(
    date: LocalDate,
    events: List<CalendarEvent>,
    startHour: Int = 7,
    endHourExclusive: Int = 23
): List<CalendarTimeSlot> {
    val slots = mutableListOf<CalendarTimeSlot>()
    var cursor = date.atTime(startHour, 0)
    val end = date.atTime(endHourExclusive, 0)
    while (cursor < end) {
        val next = cursor.plusMinutes(30)
        val slotEvents = events.filter { event ->
            val eventStart = event.startDateTime ?: return@filter false
            val eventEnd = event.endDateTime ?: eventStart.plusMinutes(30)
            eventStart < next && eventEnd > cursor
        }
        slots += CalendarTimeSlot(start = cursor, events = slotEvents)
        cursor = next
    }
    return slots
}

fun buildCompressedSlots(
    date: LocalDate,
    events: List<CalendarEvent>,
    now: LocalDateTime,
    startHour: Int = 7,
    endHourExclusive: Int = 23,
    minimumCompressibleSlots: Int = 3
): List<CalendarSlotItem> {
    val allSlots = buildHalfHourSlots(
        date = date,
        events = events,
        startHour = startHour,
        endHourExclusive = endHourExclusive
    )

    val isToday = date == now.toLocalDate()
    val nowWindowStart = now.minusHours(1)
    val nowWindowEnd = now.plusHours(1)

    val result = mutableListOf<CalendarSlotItem>()
    var emptyRun = mutableListOf<CalendarTimeSlot>()

    fun inNowWindow(slot: CalendarTimeSlot): Boolean {
        if (!isToday) return false
        val slotEnd = slot.start.plusMinutes(30)
        return slot.start < nowWindowEnd && slotEnd > nowWindowStart
    }

    fun flushEmptyRun() {
        if (emptyRun.isEmpty()) return

        if (emptyRun.size >= minimumCompressibleSlots) {
            result += CalendarSlotItem.CompressedRange(
                startTime = emptyRun.first().start,
                endTime = emptyRun.last().start.plusMinutes(30),
                slotCount = emptyRun.size,
                slots = emptyRun.toList()
            )
        } else {
            emptyRun.forEach { result += CalendarSlotItem.SingleSlot(it) }
        }
        emptyRun = mutableListOf()
    }

    allSlots.forEach { slot ->
        val hasEvents = slot.events.isNotEmpty()
        if (!hasEvents && !inNowWindow(slot)) {
            emptyRun += slot
        } else {
            flushEmptyRun()
            result += CalendarSlotItem.SingleSlot(slot)
        }
    }
    flushEmptyRun()

    return result
}

/**
 * Represents a drag-selected range of time slots.
 * [startTime] is always <= [endTime] regardless of drag direction.
 */
data class SlotDragRange(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
) {
    val durationMinutes: Int
        get() = java.time.Duration.between(startTime, endTime).toMinutes().toInt()
}

@Composable
fun CalendarDayView(
    date: LocalDate,
    events: List<CalendarEvent>,
    selectedSlotStart: LocalDateTime?,
    selectedEventId: String?,
    taskListTitleByTaskId: Map<String, String> = emptyMap(),
    taskUrgencyByTaskId: Map<String, TaskUrgency> = emptyMap(),
    onSlotActivated: (LocalDateTime) -> Unit,
    onEventActivated: (CalendarEvent) -> Unit,
    onSlotRangeSelected: (SlotDragRange) -> Unit = {},
    weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
    onToggleWeatherExpanded: () -> Unit = {},
    isWeatherExpanded: Boolean = false,
    isWeatherFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dims = rememberLayoutDimensions()
    val density = LocalDensity.current
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(30_000)
        }
    }

    val allSlots = remember(date, events) {
        buildHalfHourSlots(date = date, events = events)
    }

    val slotItems = remember(date, events, now) {
        buildCompressedSlots(date = date, events = events, now = now)
    }

    var expandedRangeStarts by remember(date) { mutableStateOf(setOf<LocalDateTime>()) }

    // Auto-scroll to current time, positioned ~1/4 from the top
    val listState = rememberLazyListState()
    LaunchedEffect(date) {
        if (date != LocalDate.now()) return@LaunchedEffect
        val currentTime = LocalDateTime.now()
        if (currentTime.hour < 7) return@LaunchedEffect // before grid start, no scroll needed
        val weatherOffset = if (weatherForecast.containsKey(date)) 1 else 0
        var targetIdx = slotItems.size - 1 // default to end if past grid
        for ((idx, item) in slotItems.withIndex()) {
            when (item) {
                is CalendarSlotItem.SingleSlot -> {
                    if (currentTime < item.slot.start.plusMinutes(30)) {
                        targetIdx = idx; break
                    }
                }
                is CalendarSlotItem.CompressedRange -> {
                    if (currentTime < item.endTime) {
                        targetIdx = idx; break
                    }
                }
            }
        }
        // Scroll 2 items before current time → ~1 hour of past visible above
        listState.scrollToItem(maxOf(0, targetIdx + weatherOffset - 2))
    }

    // Drag selection state
    var dragStartSlotIdx by remember { mutableIntStateOf(-1) }
    var dragCurrentSlotIdx by remember { mutableIntStateOf(-1) }
    val isDragging = dragStartSlotIdx >= 0

    val dragRange: SlotDragRange? = if (isDragging && dragStartSlotIdx in allSlots.indices && dragCurrentSlotIdx in allSlots.indices) {
        val minIdx = minOf(dragStartSlotIdx, dragCurrentSlotIdx)
        val maxIdx = maxOf(dragStartSlotIdx, dragCurrentSlotIdx)
        SlotDragRange(
            startTime = allSlots[minIdx].start,
            endTime = allSlots[maxIdx].start.plusMinutes(30)
        )
    } else null

    // Map each rendered slotItem to its allSlots index (for drag-to-create)
    val weatherItemCount = if (weatherForecast.containsKey(date)) 1 else 0
    val slotItemToAllSlotsIdx = remember(slotItems, allSlots) {
        slotItems.map { item ->
            when (item) {
                is CalendarSlotItem.SingleSlot ->
                    allSlots.indexOfFirst { it.start == item.slot.start }
                is CalendarSlotItem.CompressedRange ->
                    allSlots.indexOfFirst { it.start == item.startTime }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .pointerInput(allSlots, slotItemToAllSlotsIdx, weatherItemCount) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // Use layout info for scroll-aware, weather-strip-aware hit testing
                        val hitItem = listState.layoutInfo.visibleItemsInfo.find { item ->
                            offset.y >= item.offset && offset.y < item.offset + item.size
                        }
                        if (hitItem != null) {
                            val slotItemIdx = hitItem.index - weatherItemCount
                            if (slotItemIdx in slotItemToAllSlotsIdx.indices) {
                                val idx = slotItemToAllSlotsIdx[slotItemIdx]
                                if (idx in allSlots.indices) {
                                    dragStartSlotIdx = idx
                                    dragCurrentSlotIdx = idx
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val visibleSlots = listState.layoutInfo.visibleItemsInfo
                            .filter { it.index >= weatherItemCount }
                        val hitItem = visibleSlots.find { item ->
                            change.position.y >= item.offset &&
                                change.position.y < item.offset + item.size
                        }
                        val rawIdx = when {
                            hitItem != null -> hitItem.index - weatherItemCount
                            visibleSlots.isNotEmpty() &&
                                change.position.y < visibleSlots.first().offset ->
                                visibleSlots.first().index - weatherItemCount
                            visibleSlots.isNotEmpty() ->
                                visibleSlots.last().index - weatherItemCount
                            else -> -1
                        }
                        if (rawIdx in slotItemToAllSlotsIdx.indices) {
                            val idx = slotItemToAllSlotsIdx[rawIdx]
                            if (idx >= 0) {
                                dragCurrentSlotIdx = idx.coerceIn(0, allSlots.lastIndex)
                            }
                        }
                    },
                    onDragEnd = {
                        // Read state directly — composed dragRange is stale inside pointerInput
                        val startIdx = dragStartSlotIdx
                        val endIdx = dragCurrentSlotIdx
                        if (startIdx in allSlots.indices && endIdx in allSlots.indices) {
                            val minIdx = minOf(startIdx, endIdx)
                            val maxIdx = maxOf(startIdx, endIdx)
                            val range = SlotDragRange(
                                startTime = allSlots[minIdx].start,
                                endTime = allSlots[maxIdx].start.plusMinutes(30)
                            )
                            if (range.durationMinutes >= 30) {
                                onSlotRangeSelected(range)
                            }
                        }
                        dragStartSlotIdx = -1
                        dragCurrentSlotIdx = -1
                    },
                    onDragCancel = {
                        dragStartSlotIdx = -1
                        dragCurrentSlotIdx = -1
                    }
                )
            },
        userScrollEnabled = !isDragging,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Weather context strip — shown if forecast data exists for this date
        if (weatherForecast.containsKey(date)) {
            item(key = "weather_strip") {
                WeatherContextStrip(
                    date = date,
                    weatherForecast = weatherForecast,
                    isExpanded = isWeatherExpanded,
                    onToggleExpanded = onToggleWeatherExpanded,
                    isFocused = isWeatherFocused,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        items(
            items = slotItems,
            key = { item ->
                when (item) {
                    is CalendarSlotItem.SingleSlot -> item.slot.start.toString()
                    is CalendarSlotItem.CompressedRange -> "compressed_${item.startTime}"
                }
            }
        ) { item ->
            when (item) {
                is CalendarSlotItem.SingleSlot -> {
                    val slotInDragRange = dragRange?.let { range ->
                        val slotEnd = item.slot.start.plusMinutes(30)
                        item.slot.start < range.endTime && slotEnd > range.startTime
                    } == true
                    CalendarSlotRow(
                        slot = item.slot,
                        isSelectedSlot = item.slot.start == selectedSlotStart,
                        isInDragRange = slotInDragRange,
                        selectedEventId = selectedEventId,
                        taskListTitleByTaskId = taskListTitleByTaskId,
                        taskUrgencyByTaskId = taskUrgencyByTaskId,
                        now = now,
                        onSlotActivated = onSlotActivated,
                        onEventActivated = onEventActivated
                    )
                }

                is CalendarSlotItem.CompressedRange -> {
                    val containsSelectedSlot = selectedSlotStart?.let {
                        it >= item.startTime && it < item.endTime
                    } == true
                    val isExpanded = containsSelectedSlot || item.startTime in expandedRangeStarts

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        CompressedSlotRow(
                            range = item,
                            isExpanded = isExpanded,
                            onToggleExpanded = {
                                expandedRangeStarts = if (item.startTime in expandedRangeStarts) {
                                    expandedRangeStarts - item.startTime
                                } else {
                                    expandedRangeStarts + item.startTime
                                }
                            }
                        )

                        if (isExpanded) {
                            item.slots.forEach { slot ->
                                CalendarSlotRow(
                                    slot = slot,
                                    isSelectedSlot = slot.start == selectedSlotStart,
                                    selectedEventId = selectedEventId,
                                    taskListTitleByTaskId = taskListTitleByTaskId,
                                    taskUrgencyByTaskId = taskUrgencyByTaskId,
                                    now = now,
                                    onSlotActivated = onSlotActivated,
                                    onEventActivated = onEventActivated
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSlotRow(
    slot: CalendarTimeSlot,
    isSelectedSlot: Boolean,
    isInDragRange: Boolean = false,
    selectedEventId: String?,
    taskListTitleByTaskId: Map<String, String>,
    taskUrgencyByTaskId: Map<String, TaskUrgency>,
    now: LocalDateTime,
    onSlotActivated: (LocalDateTime) -> Unit,
    onEventActivated: (CalendarEvent) -> Unit
) {
    val colors = LocalWallColors.current
    val dims = rememberLayoutDimensions()
    val slotStart = slot.start
    val slotEnd = slotStart.plusMinutes(30)
    val isNowSlot = now >= slotStart && now < slotEnd
    val hasEvents = slot.events.isNotEmpty()
    val isOnTheHour = slotStart.minute == 0
    val isHighlighted = isSelectedSlot || isInDragRange

    // Three-tier height: events > hour marks > half-hour marks
    val rowHeight = when {
        hasEvents -> dims.daySlotHeightWithEvents
        isOnTheHour -> dims.daySlotHeightEmptyHour
        else -> dims.daySlotHeightEmptyHalfHour
    }

    val nowPulse by rememberInfiniteTransition(label = "nowPulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nowPulseAlpha"
    )

    // Full-width "now" band wraps the entire row
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNowSlot) Modifier.background(
                    colors.accentPrimary.copy(alpha = 0.06f),
                    RoundedCornerShape(6.dp)
                ) else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .pointerInput(slot.start) {
                    detectTapGestures { onSlotActivated(slot.start) }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Time label: hour marks bold, half-hours muted ──
            Text(
                text = slotStart.format(SlotTimeFormatter),
                style = if (isOnTheHour || hasEvents)
                    MaterialTheme.typography.labelLarge
                else
                    MaterialTheme.typography.labelSmall,
                color = when {
                    isNowSlot -> colors.accentPrimary
                    hasEvents || isOnTheHour -> colors.textSecondary
                    else -> colors.textDisabled
                },
                modifier = Modifier
                    .width(dims.dayTimeColumnWidth)
                    .padding(end = if (dims.isLandscape) 6.dp else 10.dp)
            )

            // ── Slot content: tiered rendering ──
            when {
                // Tier 1: Event slot — full card with events
                hasEvents -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(dims.daySlotBoxHeightWithEvents)
                            .background(
                                when {
                                    isInDragRange -> colors.accentPrimary.copy(alpha = 0.18f)
                                    isSelectedSlot -> colors.accentPrimary.copy(alpha = 0.12f)
                                    else -> colors.surfaceCard
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = if (isHighlighted) 1.5.dp else 1.dp,
                                color = if (isHighlighted) colors.accentPrimary else colors.borderColor,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            slot.events.take(2).forEach { event ->
                                Box(modifier = Modifier.weight(1f, fill = false)) {
                                    EventChip(
                                        event = event,
                                        isSelected = event.id == selectedEventId,
                                        sourceTaskListTitle = event.sourceTaskId?.let(taskListTitleByTaskId::get),
                                        sourceTaskUrgency = event.sourceTaskId?.let(taskUrgencyByTaskId::get),
                                        onActivated = { onEventActivated(event) }
                                    )
                                }
                            }
                            if (slot.events.size > 2) {
                                Text(
                                    text = "+${slot.events.size - 2}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }
                }

                // Selected/dragged empty slot — visible interactive state
                isHighlighted -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isOnTheHour) 32.dp else 20.dp)
                            .background(
                                if (isInDragRange) colors.accentPrimary.copy(alpha = 0.15f)
                                else colors.accentPrimary.copy(alpha = 0.10f),
                                RoundedCornerShape(6.dp)
                            )
                            .border(
                                1.dp,
                                colors.accentPrimary.copy(alpha = 0.5f),
                                RoundedCornerShape(6.dp)
                            )
                    )
                }

                // Now slot (empty) — pulsing dot + accent line
                isNowSlot -> {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(colors.accentPrimary.copy(alpha = nowPulse), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .weight(1f)
                                .height(1.5.dp)
                                .background(colors.accentPrimary.copy(alpha = 0.45f))
                        )
                    }
                }

                // Tier 2: Empty hour mark — faint divider line
                isOnTheHour -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(colors.dividerColor)
                    )
                }

                // Tier 3: Empty half-hour — no visual element
                else -> {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompressedSlotRow(
    range: CalendarSlotItem.CompressedRange,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val colors = LocalWallColors.current
    val dims = rememberLayoutDimensions()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dims.dayCompressedSlotHeight)
            .clickable(onClick = onToggleExpanded)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left faint line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.dividerColor.copy(alpha = 0.5f))
        )
        // Centered label
        Text(
            text = if (isExpanded)
                "Hide ${range.slotCount} slots"
            else
                "${range.slotCount} empty \u00b7 ${range.startTime.format(SlotTimeFormatter)}\u2013${range.endTime.format(SlotTimeFormatter)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDisabled,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        // Right faint line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.dividerColor.copy(alpha = 0.5f))
        )
    }
}

@Composable
private fun EventChip(
    event: CalendarEvent,
    isSelected: Boolean,
    sourceTaskListTitle: String?,
    sourceTaskUrgency: TaskUrgency?,
    onActivated: () -> Unit
) {
    val colors = LocalWallColors.current
    val accentColor = when (sourceTaskUrgency) {
        TaskUrgency.OVERDUE -> colors.urgencyOverdue
        TaskUrgency.DUE_TODAY -> colors.urgencyDueToday
        TaskUrgency.DUE_SOON -> colors.urgencyDueSoon
        else -> colors.accentPrimary
    }

    val bg by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentPrimary.copy(alpha = 0.2f)
            event.isPromotedTask -> accentColor.copy(alpha = 0.10f)
            else -> colors.surfaceElevated
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "calendarEventBg"
    )

    val borderCol by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentPrimary
            event.isPromotedTask -> accentColor.copy(alpha = 0.5f)
            else -> colors.borderColor
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "calendarEventBorder"
    )

    val timeRange = remember(event) {
        val start = event.startDateTime
        val end = event.endDateTime
        if (start != null && end != null) {
            "${start.format(SlotTimeFormatter)}\u2009\u2013\u2009${end.format(SlotTimeFormatter)}"
        } else null
    }

    val chipShape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(chipShape)
            .background(bg)
            .border(1.dp, borderCol, chipShape)
            .clickable(onClick = onActivated)
            .height(IntrinsicSize.Min)
    ) {
        // Left accent bar — urgency-colored spine
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(accentColor)
        )

        Column(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Text(
                text = if (!sourceTaskListTitle.isNullOrBlank()) {
                    "$sourceTaskListTitle \u2013 ${event.title}"
                } else {
                    event.title
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (timeRange != null) {
                Text(
                    text = timeRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
        }
    }
}
