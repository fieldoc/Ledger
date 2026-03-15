package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ThreeDayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 3-day landscape calendar view.
 * Shows a shared time column on the left with 3 side-by-side day columns.
 * Each day column shows half-hour slots with events.
 */
@Composable
fun Calendar3DayView(
    centerDate: LocalDate,
    allEvents: List<CalendarEvent>,
    selectedDayOffset: Int,
    selectedSlotIndex: Int,
    selectedEventId: String?,
    taskListTitleByTaskId: Map<String, String> = emptyMap(),
    taskUrgencyByTaskId: Map<String, TaskUrgency> = emptyMap(),
    onSlotActivated: (LocalDate, LocalDateTime) -> Unit,
    onEventActivated: (CalendarEvent) -> Unit,
    onSlotRangeSelected: (SlotDragRange) -> Unit = {},
    weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val dims = rememberLayoutDimensions()
    val density = LocalDensity.current

    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(30_000)
        }
    }

    val days = remember(centerDate) {
        listOf(
            centerDate.minusDays(1),
            centerDate,
            centerDate.plusDays(1)
        )
    }

    // Build slots for each day
    val daySlots = remember(days, allEvents, now) {
        days.map { date ->
            val dayEvents = allEvents.filter { it.occursOn(date) }
            buildHalfHourSlots(date, dayEvents)
        }
    }

    val slotCount = daySlots.firstOrNull()?.size ?: 0

    // Drag selection state
    var dragStartSlotIdx by remember { mutableIntStateOf(-1) }
    var dragCurrentSlotIdx by remember { mutableIntStateOf(-1) }
    var dragDayColumn by remember { mutableIntStateOf(-1) }
    val isDragging = dragStartSlotIdx >= 0

    val dragRange: SlotDragRange? = if (isDragging && dragDayColumn in daySlots.indices) {
        val daySlotList = daySlots[dragDayColumn]
        if (dragStartSlotIdx in daySlotList.indices && dragCurrentSlotIdx in daySlotList.indices) {
            val minIdx = minOf(dragStartSlotIdx, dragCurrentSlotIdx)
            val maxIdx = maxOf(dragStartSlotIdx, dragCurrentSlotIdx)
            SlotDragRange(
                startTime = daySlotList[minIdx].start,
                endTime = daySlotList[maxIdx].start.plusMinutes(30)
            )
        } else null
    } else null

    // Pixel calculations for drag offset → slot/column mapping
    val slotHeightPx = with(density) { dims.daySlotHeightEmpty.toPx() }
    val slotSpacingPx = with(density) { 1.dp.toPx() }
    val slotStepPx = slotHeightPx + slotSpacingPx
    val timeColumnWidthPx = with(density) { dims.dayTimeColumnWidth.toPx() }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var totalWidthPx by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { totalWidthPx = it.width }
            .pointerInput(daySlots, slotStepPx, timeColumnWidthPx, totalWidthPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (totalWidthPx <= 0) return@detectDragGesturesAfterLongPress
                        // Map X to day column (0, 1, 2)
                        val columnAreaWidth = (totalWidthPx - timeColumnWidthPx) / 3f
                        val xInColumns = offset.x - timeColumnWidthPx
                        val col = if (xInColumns < 0) -1
                            else (xInColumns / columnAreaWidth).toInt().coerceIn(0, 2)
                        // Map Y to slot index, accounting for header
                        val yBelowHeader = offset.y - headerHeightPx
                        val idx = (yBelowHeader / slotStepPx).toInt()
                            .coerceIn(0, slotCount - 1)
                        if (col in 0..2) {
                            dragDayColumn = col
                            dragStartSlotIdx = idx
                            dragCurrentSlotIdx = idx
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val yBelowHeader = change.position.y - headerHeightPx
                        val idx = (yBelowHeader / slotStepPx).toInt()
                            .coerceIn(0, slotCount - 1)
                        dragCurrentSlotIdx = idx
                    },
                    onDragEnd = {
                        val range = dragRange
                        if (range != null && range.durationMinutes >= 30) {
                            onSlotRangeSelected(range)
                        }
                        dragStartSlotIdx = -1
                        dragCurrentSlotIdx = -1
                        dragDayColumn = -1
                    },
                    onDragCancel = {
                        dragStartSlotIdx = -1
                        dragCurrentSlotIdx = -1
                        dragDayColumn = -1
                    }
                )
            },
        userScrollEnabled = !isDragging,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Day headers row
        item(key = "headers") {
            Row(modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
            ) {
                // Time column placeholder
                Spacer(modifier = Modifier.width(dims.dayTimeColumnWidth))

                days.forEachIndexed { dayIdx, date ->
                    val isToday = date == LocalDate.now()
                    val isSelectedDay = dayIdx == selectedDayOffset
                    val weatherCondition = weatherForecast[date]
                    val weatherTint = weatherCondition?.tintColor ?: Color.Transparent
                    val headerBg = if (weatherTint != Color.Transparent) {
                        weatherTint.copy(alpha = (weatherTint.alpha * 2f).coerceAtMost(0.12f))
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp, vertical = 4.dp)
                            .background(headerBg, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isToday -> colors.accentPrimary
                                    isSelectedDay -> colors.textPrimary
                                    else -> colors.textMuted
                                }
                            )
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isToday -> colors.accentPrimary
                                    isSelectedDay -> colors.textPrimary
                                    else -> colors.textSecondary
                                }
                            )
                            if (weatherCondition != null && weatherCondition != WeatherCondition.PARTLY_CLOUDY) {
                                Text(
                                    text = weatherCondition.icon,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .width(14.dp)
                                        .height(2.dp)
                                        .background(
                                            colors.accentPrimary.copy(alpha = 0.6f),
                                            RoundedCornerShape(999.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Slot rows — one per half-hour
        items(
            count = slotCount,
            key = { idx -> "slot_$idx" }
        ) { slotIdx ->
            val timeSlot = daySlots[0][slotIdx]
            val slotTime = timeSlot.start
            val slotHeight = dims.daySlotHeightEmpty

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(slotHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shared time label
                Text(
                    text = slotTime.format(ThreeDayTimeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier
                        .width(dims.dayTimeColumnWidth)
                        .padding(end = 4.dp)
                )

                // 3 day columns
                days.forEachIndexed { dayIdx, date ->
                    val slot = daySlots[dayIdx][slotIdx]
                    val hasEvents = slot.events.isNotEmpty()
                    val isSelected = dayIdx == selectedDayOffset && slotIdx == selectedSlotIndex
                    val slotStart = slot.start
                    val slotEnd = slotStart.plusMinutes(30)
                    val hasNow = date == now.toLocalDate() && now >= slotStart && now < slotEnd

                    val slotInDragRange = dragDayColumn == dayIdx && dragRange?.let { range ->
                        val slotEnd = slot.start.plusMinutes(30)
                        slot.start < range.endTime && slotEnd > range.startTime
                    } == true

                    ThreeDaySlotCell(
                        slot = slot,
                        isSelected = isSelected,
                        isInDragRange = slotInDragRange,
                        hasNow = hasNow,
                        selectedEventId = if (dayIdx == selectedDayOffset) selectedEventId else null,
                        taskUrgencyByTaskId = taskUrgencyByTaskId,
                        onSlotActivated = { onSlotActivated(date, slot.start) },
                        onEventActivated = onEventActivated,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ThreeDaySlotCell(
    slot: CalendarTimeSlot,
    isSelected: Boolean,
    isInDragRange: Boolean = false,
    hasNow: Boolean,
    selectedEventId: String?,
    taskUrgencyByTaskId: Map<String, TaskUrgency>,
    onSlotActivated: () -> Unit,
    onEventActivated: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val hasEvents = slot.events.isNotEmpty()
    val isHighlighted = isSelected || isInDragRange

    val bg by animateColorAsState(
        targetValue = when {
            isInDragRange -> colors.accentPrimary.copy(alpha = 0.18f)
            isSelected -> colors.accentPrimary.copy(alpha = 0.12f)
            hasEvents -> colors.surfaceCard
            else -> Color.Transparent
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "threeDaySlotBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> colors.accentPrimary
            hasEvents -> colors.borderColor
            else -> colors.borderColor.copy(alpha = 0.3f)
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "threeDaySlotBorder"
    )

    Box(
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .border(
                width = if (isHighlighted) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { onSlotActivated() }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (hasNow && !hasEvents) {
            val nowPulse by rememberInfiniteTransition(label = "nowPulse3d")
                .animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.75f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "nowAlpha3d"
                )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(colors.accentPrimary.copy(alpha = nowPulse * 0.6f))
            )
        }

        if (hasEvents) {
            val event = slot.events.first()
            val isEventSelected = event.id == selectedEventId
            val urgency = event.sourceTaskId?.let(taskUrgencyByTaskId::get)
            val dotColor = when (urgency) {
                TaskUrgency.OVERDUE -> colors.urgencyOverdue
                TaskUrgency.DUE_TODAY -> colors.urgencyDueToday
                TaskUrgency.DUE_SOON -> colors.urgencyDueSoon
                else -> colors.accentPrimary
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (event.isPromotedTask) {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .width(2.dp)
                            .height(20.dp)
                            .background(dotColor, RoundedCornerShape(1.dp))
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isEventSelected) colors.accentPrimary else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (slot.events.size > 1) {
                    Text(
                        text = "+${slot.events.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }
        }
    }
}
