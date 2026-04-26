package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
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
 * Renders [startDate, startDate+1, startDate+2] — typically today + next two days.
 * Shared time column on the left with 3 side-by-side day columns.
 * Each day column shows half-hour slots with events.
 */
@Composable
fun Calendar3DayView(
    startDate: LocalDate,
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
    planBlocks: List<PlanBlock> = emptyList(),
    recentlyCreatedEventIds: Set<String> = emptySet(),
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

    val days = remember(startDate) {
        listOf(
            startDate,
            startDate.plusDays(1),
            startDate.plusDays(2)
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

    // Ghost block lookup: maps the slot index where a ghost block STARTS to the block,
    // per day column. Only ghost (non-existing-event) blocks are surfaced as previews.
    val ghostBlocksByDayAndSlot: Map<Pair<Int, Int>, PlanBlock> = remember(days, planBlocks, slotCount) {
        if (planBlocks.isEmpty() || slotCount == 0) {
            emptyMap()
        } else {
            buildMap {
                planBlocks.asSequence()
                    .filter { !it.isExistingEvent }
                    .forEach { block ->
                        val blockDate = block.startTime.toLocalDate()
                        val dayIdx = days.indexOf(blockDate)
                        if (dayIdx < 0) return@forEach
                        val slotsForDay = daySlots.getOrNull(dayIdx) ?: return@forEach
                        val idx = slotsForDay.indexOfFirst { slot ->
                            val end = slot.start.plusMinutes(30)
                            block.startTime >= slot.start && block.startTime < end
                        }
                        if (idx >= 0) {
                            // Only one chip per slot — first writer wins
                            putIfAbsent(dayIdx to idx, block)
                        }
                    }
            }
        }
    }

    // Drag selection state
    var dragStartSlotIdx by remember { mutableIntStateOf(-1) }
    var dragCurrentSlotIdx by remember { mutableIntStateOf(-1) }
    var dragDayColumn by remember { mutableIntStateOf(-1) }
    val isDragging = dragStartSlotIdx >= 0

    // Auto-scroll to current time, positioned ~1/4 from the top
    val listState = rememberLazyListState()
    LaunchedEffect(startDate) {
        if (!days.any { it == LocalDate.now() }) return@LaunchedEffect
        val currentTime = LocalDateTime.now()
        if (currentTime.hour < 7) return@LaunchedEffect
        // Slot index within the half-hour grid (0-based from startHour=7)
        val currentSlotIdx = ((currentTime.hour - 7) * 2 + currentTime.minute / 30)
            .coerceIn(0, slotCount - 1)
        // -2 to show ~1 hour of past above current time
        listState.scrollToItem(maxOf(0, currentSlotIdx - 2))
    }

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

    Column(modifier = modifier.fillMaxSize().onSizeChanged { totalWidthPx = it.width }) {
        // Frozen day headers — always visible above the scrollable time slots
        Row(modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { headerHeightPx = it.height }
        ) {
            // Time column placeholder (aligns with the time labels below)
            Spacer(modifier = Modifier.width(dims.dayTimeColumnWidth))

            days.forEachIndexed { dayIdx, date ->
                val isToday = date == LocalDate.now()
                val isSelectedDay = dayIdx == selectedDayOffset
                val weatherCondition = weatherForecast[date]
                val weatherTint = weatherCondition?.tintColor(colors.isDark) ?: Color.Transparent
                val maxHeaderAlpha = if (colors.isDark) 0.25f else 0.12f
                val headerBg = if (weatherTint != Color.Transparent) {
                    weatherTint.copy(alpha = (weatherTint.alpha * 2f).coerceAtMost(maxHeaderAlpha))
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

        // Scrollable time slots
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(daySlots, timeColumnWidthPx, totalWidthPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            if (totalWidthPx <= 0) return@detectDragGesturesAfterLongPress
                            val columnAreaWidth = (totalWidthPx - timeColumnWidthPx) / 3f
                            val xInColumns = offset.x - timeColumnWidthPx
                            val col = if (xInColumns < 0) -1
                                else (xInColumns / columnAreaWidth).toInt().coerceIn(0, 2)
                            val hitItem = listState.layoutInfo.visibleItemsInfo.find { item ->
                                offset.y >= item.offset && offset.y < item.offset + item.size
                            }
                            // Slots start at index 0 (no header item in the list)
                            val idx = if (hitItem != null) {
                                hitItem.index.coerceIn(0, slotCount - 1)
                            } else -1
                            if (col in 0..2 && idx >= 0) {
                                dragDayColumn = col
                                dragStartSlotIdx = idx
                                dragCurrentSlotIdx = idx
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val visibleSlots = listState.layoutInfo.visibleItemsInfo
                            val hitItem = visibleSlots.find { item ->
                                change.position.y >= item.offset &&
                                    change.position.y < item.offset + item.size
                            }
                            val idx = when {
                                hitItem != null -> hitItem.index
                                visibleSlots.isNotEmpty() &&
                                    change.position.y < visibleSlots.first().offset ->
                                    visibleSlots.first().index
                                visibleSlots.isNotEmpty() ->
                                    visibleSlots.last().index
                                else -> dragCurrentSlotIdx
                            }
                            dragCurrentSlotIdx = idx.coerceIn(0, slotCount - 1)
                        },
                        onDragEnd = {
                            val startIdx = dragStartSlotIdx
                            val endIdx = dragCurrentSlotIdx
                            val dayCol = dragDayColumn
                            if (startIdx >= 0 && endIdx >= 0 && dayCol in daySlots.indices) {
                                val daySlotList = daySlots[dayCol]
                                if (startIdx in daySlotList.indices && endIdx in daySlotList.indices) {
                                    val minIdx = minOf(startIdx, endIdx)
                                    val maxIdx = maxOf(startIdx, endIdx)
                                    val range = SlotDragRange(
                                        startTime = daySlotList[minIdx].start,
                                        endTime = daySlotList[maxIdx].start.plusMinutes(30)
                                    )
                                    if (range.durationMinutes >= 30) {
                                        onSlotRangeSelected(range)
                                    }
                                }
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
            // Slot rows — one per half-hour
            items(
                count = slotCount,
                key = { idx -> "slot_$idx" }
            ) { slotIdx ->
                val timeSlot = daySlots[0][slotIdx]
                val slotTime = timeSlot.start
                val isOnTheHour = slotTime.minute == 0
                val anyHasEvents = daySlots.any { it[slotIdx].events.isNotEmpty() } ||
                    daySlots.indices.any { ghostBlocksByDayAndSlot.containsKey(it to slotIdx) }
                val isNowRow = days.any { date ->
                    val slot = daySlots[days.indexOf(date)][slotIdx]
                    date == now.toLocalDate() && now >= slot.start && now < slot.start.plusMinutes(30)
                }

                // Three-tier height: any-column events > hour marks > half-hour marks
                val rowHeight = when {
                    anyHasEvents -> dims.daySlotHeightWithEvents
                    isOnTheHour -> dims.daySlotHeightEmptyHour
                    else -> dims.daySlotHeightEmptyHalfHour
                }

                // Full-width "now" band wraps the entire row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isNowRow) Modifier.background(
                                colors.accentPrimary.copy(alpha = 0.06f),
                                RoundedCornerShape(6.dp)
                            ) else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time label: show on hour marks only (suppress half-hour noise)
                        Text(
                            text = if (isOnTheHour || anyHasEvents)
                                slotTime.format(ThreeDayTimeFormatter)
                            else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isNowRow -> colors.accentPrimary
                                anyHasEvents || isOnTheHour -> colors.textSecondary
                                else -> colors.textDisabled
                            },
                            modifier = Modifier
                                .width(dims.dayTimeColumnWidth)
                                .padding(end = 4.dp)
                        )

                        // 3 day columns
                        days.forEachIndexed { dayIdx, date ->
                            val slot = daySlots[dayIdx][slotIdx]
                            val isSelected = dayIdx == selectedDayOffset && slotIdx == selectedSlotIndex
                            val slotStart = slot.start
                            val slotEnd = slotStart.plusMinutes(30)
                            val hasNow = date == now.toLocalDate() && now >= slotStart && now < slotEnd

                            val slotInDragRange = dragDayColumn == dayIdx && dragRange?.let { range ->
                                slot.start < range.endTime && slotEnd > range.startTime
                            } == true

                            ThreeDaySlotCell(
                                slot = slot,
                                isOnTheHour = isOnTheHour,
                                isSelected = isSelected,
                                isInDragRange = slotInDragRange,
                                hasNow = hasNow,
                                selectedEventId = if (dayIdx == selectedDayOffset) selectedEventId else null,
                                taskUrgencyByTaskId = taskUrgencyByTaskId,
                                ghostBlock = ghostBlocksByDayAndSlot[dayIdx to slotIdx],
                                recentlyCreatedEventIds = recentlyCreatedEventIds,
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
    }
}

@Composable
private fun ThreeDaySlotCell(
    slot: CalendarTimeSlot,
    isOnTheHour: Boolean,
    isSelected: Boolean,
    isInDragRange: Boolean = false,
    hasNow: Boolean,
    selectedEventId: String?,
    taskUrgencyByTaskId: Map<String, TaskUrgency>,
    ghostBlock: PlanBlock? = null,
    recentlyCreatedEventIds: Set<String> = emptySet(),
    onSlotActivated: () -> Unit,
    onEventActivated: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val hasEvents = slot.events.isNotEmpty()
    val isHighlighted = isSelected || isInDragRange

    // Hoisted out of conditional to satisfy Compose composition rules
    val nowPulse by rememberInfiniteTransition(label = "nowPulse3d").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nowAlpha3d"
    )

    val cellShape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures { onSlotActivated() } }
    ) {
        // Hour-boundary hairline — visible only on empty on-the-hour cells to create rhythm
        if (!hasEvents && isOnTheHour) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(colors.dividerColor.copy(alpha = 0.6f))
            )
        }

        // Now indicator: pulsing accent line inside the current-day column
        if (hasNow && !hasEvents) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .align(Alignment.Center)
                    .background(colors.accentPrimary.copy(alpha = nowPulse * 0.55f))
            )
        }

        // Selection / drag highlight on empty cells
        if (isHighlighted && !hasEvents) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
                    .background(
                        if (isInDragRange) colors.accentPrimary.copy(alpha = 0.18f)
                        else colors.accentPrimary.copy(alpha = 0.10f),
                        cellShape
                    )
                    .border(1.dp, colors.accentPrimary.copy(alpha = 0.5f), cellShape)
            )
        }

        // Ghost plan block — translucent preview of a Day Organizer block on an empty slot
        if (ghostBlock != null && !hasEvents) {
            val ghostColor = colors.planAccent
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
                    .clip(cellShape)
                    .background(ghostColor.copy(alpha = 0.10f))
                    .border(1.dp, ghostColor.copy(alpha = 0.30f), cellShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(ghostColor.copy(alpha = 0.55f))
                )
                Text(
                    text = ghostBlock.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = ghostColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        // Event chip — left accent bar + title (compact for 3-column layout)
        if (hasEvents) {
            val event = slot.events.first()
            val isEventSelected = event.id == selectedEventId
            val urgency = event.sourceTaskId?.let(taskUrgencyByTaskId::get)
            val accentColor = when (urgency) {
                TaskUrgency.OVERDUE -> colors.urgencyOverdue
                TaskUrgency.DUE_TODAY -> colors.urgencyDueToday
                TaskUrgency.DUE_SOON -> colors.urgencyDueSoon
                else -> colors.accentPrimary
            }

            val chipBg by animateColorAsState(
                targetValue = when {
                    isInDragRange -> colors.accentPrimary.copy(alpha = 0.18f)
                    isEventSelected -> colors.accentPrimary.copy(alpha = 0.20f)
                    event.isPromotedTask -> accentColor.copy(alpha = 0.10f)
                    else -> colors.surfaceCard
                },
                animationSpec = tween(WallAnimations.SHORT),
                label = "threeDayChipBg"
            )

            val chipBorder by animateColorAsState(
                targetValue = when {
                    isHighlighted -> colors.accentPrimary
                    event.isPromotedTask -> accentColor.copy(alpha = 0.45f)
                    else -> colors.borderColor
                },
                animationSpec = tween(WallAnimations.SHORT),
                label = "threeDayChipBorder"
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
                    .clip(cellShape)
                    .background(chipBg)
                    .border(1.dp, chipBorder, cellShape)
                    .clickable { onEventActivated(event) }
            ) {
                // Left accent bar — urgency-colored spine
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(accentColor)
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            // Pulse highlight for recently created events — fades over 2 seconds
            if (event.id in recentlyCreatedEventIds) {
                val highlightAlpha = remember(event.id) { Animatable(0.35f) }
                LaunchedEffect(event.id) {
                    highlightAlpha.animateTo(0f, animationSpec = tween(durationMillis = 2000))
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 1.dp, vertical = 2.dp)
                        .clip(cellShape)
                        .background(colors.accentWarm.copy(alpha = highlightAlpha.value))
                )
            }
        }
    }
}
