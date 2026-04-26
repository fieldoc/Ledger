package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallColors
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.min

private val ThreeDayTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val START_HOUR = 7

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
        if (currentTime.hour < START_HOUR) return@LaunchedEffect
        val currentSlotIdx = ((currentTime.hour - START_HOUR) * 2 + currentTime.minute / 30)
            .coerceIn(0, slotCount - 1)
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

    val timeColumnWidthPx = with(density) { dims.dayTimeColumnWidth.toPx() }
    var totalWidthPx by remember { mutableIntStateOf(0) }

    val columnWidthDp: Dp = if (totalWidthPx > 0) {
        with(density) { ((totalWidthPx - timeColumnWidthPx) / 3f).toDp().coerceAtLeast(0.dp) }
    } else 0.dp

    // Animated x-offset for the focused-column halo — glides between columns.
    val focusedLeft by animateDpAsState(
        targetValue = dims.dayTimeColumnWidth + columnWidthDp * selectedDayOffset.coerceIn(0, 2),
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "threeDayFocusedColumnLeft"
    )

    // Halo alphas are tuned per theme. Dark backgrounds need slightly more accent
    // to read; light backgrounds make even small alphas feel saturated.
    val haloAlphaEdge = if (colors.isDark) 0.10f else 0.05f
    val haloAlphaMid = if (colors.isDark) 0.06f else 0.025f
    val haloBorderAlpha = if (colors.isDark) 0.15f else 0.20f
    val focusedHeaderBorderAlpha = if (colors.isDark) 0.40f else 0.45f

    Column(modifier = modifier.fillMaxSize().onSizeChanged { totalWidthPx = it.width }) {
        // Frozen day headers — single baseline-aligned row, no underline.
        Row(modifier = Modifier.fillMaxWidth()) {
            // Time column placeholder (aligns with the time labels below)
            Spacer(modifier = Modifier.width(dims.dayTimeColumnWidth))

            days.forEachIndexed { dayIdx, date ->
                val isToday = date == LocalDate.now()
                val isSelectedDay = dayIdx == selectedDayOffset
                val weatherCondition = weatherForecast[date]
                val weatherTint = weatherCondition?.tintColor(colors.isDark) ?: Color.Transparent
                val maxHeaderAlpha = if (colors.isDark) 0.25f else 0.12f
                val weatherBg = if (weatherTint != Color.Transparent) {
                    weatherTint.copy(alpha = (weatherTint.alpha * 2f).coerceAtMost(maxHeaderAlpha))
                } else {
                    Color.Transparent
                }

                val headerShape = RoundedCornerShape(6.dp)
                // Focused header: surfaceCard base + a subtle accent overlay so the swatch
                // reads in both themes without hardcoded hex.
                val focusedHeaderTintAlpha = if (colors.isDark) 0.10f else 0.07f
                val headerModifier = if (isSelectedDay) {
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .background(colors.surfaceCard, headerShape)
                        .background(
                            colors.accentPrimary.copy(alpha = focusedHeaderTintAlpha),
                            headerShape
                        )
                        .border(
                            1.dp,
                            colors.accentPrimary.copy(alpha = focusedHeaderBorderAlpha),
                            headerShape
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                } else {
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp, vertical = 2.dp)
                        .background(weatherBg, headerShape)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                }

                Row(
                    modifier = headerModifier,
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = headerDowColor(isToday, isSelectedDay, colors),
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color = headerNumColor(isToday, isSelectedDay, colors),
                        modifier = Modifier.alignByBaseline()
                    )
                    if (weatherCondition != null && weatherCondition != WeatherCondition.PARTLY_CLOUDY) {
                        Text(
                            text = weatherCondition.icon,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted.copy(alpha = 0.7f),
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
        }

        // Slot grid container — wraps LazyColumn so we can overlay halo + hairlines
        // across the entire grid height (not per-row).
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Focused-column halo — drawn ONCE behind the slot grid so it glides
            //    between columns instead of stuttering.
            if (columnWidthDp > 0.dp) {
                Box(
                    modifier = Modifier
                        .offset(x = focusedLeft)
                        .width(columnWidthDp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                0f to colors.accentPrimary.copy(alpha = haloAlphaEdge),
                                0.5f to colors.accentPrimary.copy(alpha = haloAlphaMid),
                                1f to colors.accentPrimary.copy(alpha = haloAlphaEdge)
                            ),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            colors.accentPrimary.copy(alpha = haloBorderAlpha),
                            RoundedCornerShape(6.dp)
                        )
                )
            }

            // 2. Slot rows
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time gutter — show actual time during "now" row, hour marks otherwise.
                        val gutterText = when {
                            isNowRow -> now.format(ThreeDayTimeFormatter)
                            isOnTheHour || anyHasEvents -> slotTime.format(ThreeDayTimeFormatter)
                            else -> ""
                        }
                        val gutterColor = when {
                            isNowRow -> colors.accentPrimary
                            anyHasEvents || isOnTheHour -> colors.textSecondary
                            else -> colors.textDisabled
                        }
                        Text(
                            text = gutterText,
                            style = MaterialTheme.typography.labelSmall,
                            color = gutterColor,
                            fontWeight = if (isNowRow) FontWeight.SemiBold else FontWeight.Normal,
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

                            // Multi-slot event handling: render the chip ONLY at the start slot,
                            // expanded to span all covered rows. Continuation slots paint nothing,
                            // so the tall chip from the start slot covers them visually.
                            //
                            // Pick the first event that ACTUALLY starts in this slot — not just
                            // slot.events.first(), which could be a continuation. This makes a
                            // concurrent new event B at slot N visible even when an earlier
                            // event A is continuing through slot N.
                            val startingEvents = slot.events.filter { event ->
                                val s = event.startDateTime
                                s != null && s >= slotStart && s < slotEnd
                            }
                            val eventStartsHere = startingEvents.isNotEmpty()
                            val renderedEvent = startingEvents.firstOrNull()

                            // Span + end derive from the rendered event's bounds. We use
                            // `slot.start` as a fallback for startDateTime to avoid `!!` —
                            // upstream `buildHalfHourSlots` filters null startDateTimes, so this
                            // path is unreachable in practice but the type system doesn't know.
                            val renderedStart = renderedEvent?.startDateTime ?: slot.start
                            val renderedEnd = renderedEvent?.endDateTime
                                ?: renderedStart.plusMinutes(30)

                            val eventSpanSlots = if (renderedEvent != null) {
                                val durationMin = java.time.Duration
                                    .between(renderedStart, renderedEnd)
                                    .toMinutes()
                                    .coerceAtLeast(30L)
                                val rawSpan = ((durationMin + 29L) / 30L).toInt()
                                val maxSpan = slotCount - slotIdx
                                rawSpan.coerceIn(1, maxSpan)
                            } else 1

                            // Past-of-now recede (today's column only).
                            // For chips: fade based on the event's END, not the start cell — a
                            // 09:00–11:00 meeting at 10:30 is currently ongoing and must NOT be
                            // dimmed just because slot 09:00 is in the past.
                            // For continuation slots (events but eventStartsHere=false): alpha
                            // doesn't matter since the cell renders nothing.
                            // For empty cells: fade based on the slot's end vs now.
                            val isToday = date == LocalDate.now()
                            val targetAlpha = when {
                                renderedEvent != null ->
                                    if (isToday && renderedEnd <= now) 0.5f else 1f
                                slot.events.isNotEmpty() -> 1f
                                else -> if (isToday && slotEnd <= now) 0.5f else 1f
                            }
                            val cellAlpha by animateFloatAsState(
                                targetValue = targetAlpha,
                                animationSpec = tween(WallAnimations.SHORT),
                                label = "threeDayPastAlpha"
                            )

                            ThreeDaySlotCell(
                                slot = slot,
                                renderedEvent = renderedEvent,
                                isOnTheHour = isOnTheHour,
                                isSelected = isSelected,
                                isInDragRange = slotInDragRange,
                                hasNow = hasNow,
                                rowHeight = rowHeight,
                                spanSlots = eventSpanSlots,
                                eventStartsHere = eventStartsHere,
                                now = now,
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
                                    .alpha(cellAlpha)
                            )
                        }
                    }
                }
            }

            // 3. Inter-column hairlines — full-grid-height vertical 1dp dividers between
            //    columns. Drawn over the grid; pointer events continue to pass through the
            //    underlying LazyColumn since these are 1dp and don't intercept gestures.
            if (columnWidthDp > 0.dp) {
                listOf(1, 2).forEach { i ->
                    Box(
                        modifier = Modifier
                            .offset(x = dims.dayTimeColumnWidth + columnWidthDp * i)
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(colors.dividerColor.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

private fun headerDowColor(isToday: Boolean, isSelectedDay: Boolean, colors: WallColors): Color =
    when {
        isToday -> colors.accentPrimary
        isSelectedDay -> colors.textPrimary
        else -> colors.textMuted
    }

private fun headerNumColor(isToday: Boolean, isSelectedDay: Boolean, colors: WallColors): Color =
    when {
        isToday -> colors.accentPrimary
        isSelectedDay -> colors.textPrimary
        else -> colors.textSecondary
    }

@Composable
private fun ThreeDaySlotCell(
    slot: CalendarTimeSlot,
    renderedEvent: CalendarEvent?,
    isOnTheHour: Boolean,
    isSelected: Boolean,
    isInDragRange: Boolean = false,
    hasNow: Boolean,
    rowHeight: Dp,
    spanSlots: Int,
    eventStartsHere: Boolean,
    now: LocalDateTime,
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

        // Now indicator — single precise hairline at the actual minute, with a leading dot
        // and a soft outer glow. Replaces the old pulsing line + tinted band.
        if (hasNow && !hasEvents) {
            val nowFraction = (now.minute % 30) / 30f
            val lineY = rowHeight * nowFraction
            // Soft outer glow around the line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = lineY - 1.dp)
                    .height(3.dp)
                    .align(Alignment.TopStart)
                    .background(colors.accentPrimary.copy(alpha = 0.12f))
            )
            // Crisp 1.5dp line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = lineY)
                    .height(1.5.dp)
                    .align(Alignment.TopStart)
                    .background(colors.accentPrimary)
            )
            // Leading dot
            Box(
                modifier = Modifier
                    .offset(x = (-3).dp, y = lineY - 3.dp)
                    .size(7.dp)
                    .align(Alignment.TopStart)
                    .background(colors.accentPrimary, CircleShape)
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

        // Ghost plan block — dashed outline + spine, no fill. Reads as provisional at distance.
        if (ghostBlock != null && !hasEvents) {
            val ghostColor = colors.planAccent
            val dashStrokeWidthPx = with(LocalDensity.current) { 1.dp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp, vertical = 2.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = ghostColor.copy(alpha = 0.60f),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(
                            width = dashStrokeWidthPx,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
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
        }

        // Event chip — only at the start slot. Continuation slots paint nothing so the tall
        // chip from the start slot covers them.
        //
        // Cross-row overdraw safety: covered slots gate every render path on `!hasEvents`
        // (hour-line, selection, now-line, ghost) and `eventStartsHere` (chip), so they
        // produce no paint operations at the chip's overflow Y range. zIndex(1f) is a belt-
        // and-suspenders lift so the chip stacks above siblings within the cell. The
        // remaining LazyColumn scroll-out limitation (chip disappears when start slot leaves
        // the viewport) is accepted scope; an overlay refactor is out of scope here.
        if (hasEvents && eventStartsHere && renderedEvent != null) {
            val event = renderedEvent
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

            // Multi-slot height: span * rowHeight + (span - 1) * 1dp of vertical spacing.
            val chipHeight = rowHeight * spanSlots + 1.dp * (spanSlots - 1)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chipHeight)
                    .zIndex(1f)
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
                    // Quiet overflow: up to three 3dp dots, then "+N" only if extras > 3.
                    val extras = slot.events.size - 1
                    if (extras > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(min(extras, 3)) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .background(colors.textMuted, CircleShape)
                                )
                            }
                            if (extras > 3) {
                                Text(
                                    text = "+${extras - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
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
                        .fillMaxWidth()
                        .height(chipHeight)
                        .padding(horizontal = 1.dp, vertical = 2.dp)
                        .clip(cellShape)
                        .background(colors.accentWarm.copy(alpha = highlightAlpha.value))
                )
            }
        }
    }
}
