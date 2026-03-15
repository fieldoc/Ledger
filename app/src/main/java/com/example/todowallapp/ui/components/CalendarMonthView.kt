package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallColors
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarMonthView(
    displayMonth: LocalDate,
    selectedDate: LocalDate,
    eventsForRange: Map<LocalDate, List<CalendarEvent>>,
    weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()
    val firstDayOfMonth = remember(displayMonth) { displayMonth.withDayOfMonth(1) }
    val lastDayOfMonth = remember(firstDayOfMonth) { firstDayOfMonth.withDayOfMonth(firstDayOfMonth.lengthOfMonth()) }
    val gridStart = remember(firstDayOfMonth) {
        firstDayOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    }
    val gridEnd = remember(lastDayOfMonth) {
        lastDayOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    }
    val gridDays = remember(gridStart, gridEnd) {
        buildList {
            var date = gridStart
            while (!date.isAfter(gridEnd)) {
                add(date)
                date = date.plusDays(1)
            }
        }
    }

    val dims = rememberLayoutDimensions()
    val weekdayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
    val month = firstDayOfMonth.month
    val year = firstDayOfMonth.year
    val weeks = remember(gridDays) { gridDays.chunked(7) }

    Column(modifier = modifier) {
        // Weekday labels — fixed height
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(dims.monthLabelGridGap))

        // Grid — fills all remaining vertical space
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(dims.monthWeekRowSpacing)
        ) {
            weeks.forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            isCurrentMonth = date.month == month && date.year == year,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            events = eventsForRange[date].orEmpty(),
                            weatherTint = weatherForecast[date]?.tintColor ?: Color.Transparent,
                            onDaySelected = onDaySelected,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<CalendarEvent>,
    weatherTint: Color = Color.Transparent,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(8.dp)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isToday -> colors.accentPrimary.copy(alpha = 0.04f)
            weatherTint != Color.Transparent -> weatherTint
            else -> colors.surfaceCard.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "calendarDayBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isToday -> colors.borderFocused
            isSelected -> colors.accentPrimary.copy(alpha = 0.6f)
            else -> colors.surfaceCard.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "calendarDayBorder"
    )
    val dayNumberColor by animateColorAsState(
        targetValue = when {
            isToday -> colors.accentPrimary
            !isCurrentMonth -> colors.textDisabled
            else -> colors.textPrimary
        },
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "calendarDayNumberColor"
    )

    Box(
        modifier = modifier
            .padding(2.dp)
            .background(backgroundColor, shape)
            .border(1.dp, borderColor, shape)
            .clickable { onDaySelected(date) }
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Top
        ) {
            // Day number
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isToday) FontWeight.Bold else MaterialTheme.typography.labelMedium.fontWeight
                ),
                color = dayNumberColor,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (events.isNotEmpty()) {
                Spacer(modifier = Modifier.height(3.dp))

                // Show event titles as small lines — much more useful than dots
                val maxVisible = 3
                val displayEvents = events.take(maxVisible)
                val overflow = events.size - displayEvents.size

                Column(
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    displayEvents.forEach { event ->
                        MonthEventLine(event = event, colors = colors)
                    }
                    if (overflow > 0) {
                        Text(
                            text = "+$overflow",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 1.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthEventLine(
    event: CalendarEvent,
    colors: WallColors
) {
    val barColor = eventDotColor(event = event, colors = colors)
    val lineShape = RoundedCornerShape(3.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(lineShape)
            .background(colors.surfaceBlack.copy(alpha = 0.35f), lineShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thin color indicator bar
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(14.dp)
                .background(barColor)
        )
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            color = if (event.isPromotedTask) colors.accentWarm else colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}

private fun eventDotColor(event: CalendarEvent, colors: WallColors): Color {
    val now = LocalDateTime.now()
    val isOverdue = event.isPromotedTask && (
        event.startDateTime?.isBefore(now) == true ||
        event.allDayStartDate?.isBefore(LocalDate.now()) == true
    )
    return when {
        isOverdue -> colors.urgencyOverdue.copy(alpha = 0.6f)
        event.isPromotedTask -> colors.accentPrimary.copy(alpha = 0.6f)
        event.isAllDay -> colors.accentPrimary.copy(alpha = 0.6f)
        else -> colors.textSecondary.copy(alpha = 0.5f)
    }
}
