package com.example.todowallapp.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallColors
import com.example.todowallapp.ui.utils.rememberLayoutDimensions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val EventTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val CardShape = RoundedCornerShape(12.dp)
private val EventChipShape = RoundedCornerShape(8.dp)

@Composable
fun CalendarWeekView(
    eventsForRange: Map<LocalDate, List<CalendarEvent>>,
    selectedDate: LocalDate,
    weatherForecast: Map<LocalDate, WeatherCondition> = emptyMap(),
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val dims = rememberLayoutDimensions()
    val colors = LocalWallColors.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dims.weekRowSpacing)
    ) {
        repeat(7) { offset ->
            val date = today.plusDays(offset.toLong())
            val events = eventsForRange[date].orEmpty()
            val isToday = date == today
            val isTomorrow = date == today.plusDays(1)
            val isSelected = date == selectedDate
            val isNearFuture = offset <= 2 // today, tomorrow, day after
            val weatherCondition = weatherForecast[date]

            // Weight: today gets more space, near-future slightly more, distant days baseline
            val cardWeight = when {
                isToday -> 1.4f
                isTomorrow -> 1.2f
                isNearFuture -> 1.1f
                else -> 1f
            }

            WeekDayCard(
                date = date,
                events = events,
                isToday = isToday,
                isTomorrow = isTomorrow,
                isSelected = isSelected,
                isNearFuture = isNearFuture,
                weatherCondition = weatherCondition,
                colors = colors,
                onDaySelected = { onDaySelected(date) },
                modifier = Modifier.weight(cardWeight)
            )
        }
    }
}

@Composable
private fun WeekDayCard(
    date: LocalDate,
    events: List<CalendarEvent>,
    isToday: Boolean,
    isTomorrow: Boolean,
    isSelected: Boolean,
    isNearFuture: Boolean,
    weatherCondition: WeatherCondition?,
    colors: WallColors,
    onDaySelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val glowColor = colors.accentPrimary
    val weatherTint = weatherCondition?.tintColor ?: Color.Transparent

    val cardBackground = when {
        isToday -> colors.surfaceCard
        weatherTint != Color.Transparent -> weatherTint
        isNearFuture -> colors.surfaceCard.copy(alpha = 0.7f)
        else -> colors.surfaceExpanded.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.drawBehind {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                isAntiAlias = true
                                color = glowColor.copy(alpha = 0.25f).toArgb()
                                setShadowLayer(
                                    16.dp.toPx(), 0f, 0f,
                                    glowColor.copy(alpha = 0.35f).toArgb()
                                )
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                12.dp.toPx(), 12.dp.toPx(),
                                paint
                            )
                        }
                    }
                } else Modifier
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(CardShape)
                .background(cardBackground, CardShape)
                .border(
                    width = if (isSelected) 1.dp else 0.5.dp,
                    color = if (isSelected) glowColor.copy(alpha = 0.5f)
                    else colors.borderColor.copy(alpha = 0.5f),
                    shape = CardShape
                )
                .clickable(onClick = onDaySelected)
        ) {
            // Left accent bar — today gets a teal bar, others get a subtle divider
            if (isToday) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(colors.accentPrimary)
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            colors.borderColor.copy(
                                alpha = if (isNearFuture) 0.6f else 0.3f
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 10.dp,
                        bottom = 8.dp
                    )
            ) {
                // Day header row — always at top
                DayHeader(
                    date = date,
                    isToday = isToday,
                    isTomorrow = isTomorrow,
                    eventCount = events.size,
                    weatherCondition = weatherCondition,
                    colors = colors
                )

                // Events fill remaining space
                if (events.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // Show up to 4 events for near-future, 3 for distant
                    val maxEvents = if (isNearFuture) 4 else 3
                    val displayEvents = events.take(maxEvents)
                    val overflow = events.size - displayEvents.size

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        displayEvents.forEach { event ->
                            WeekEventChip(
                                event = event,
                                isCompact = !isNearFuture,
                                colors = colors
                            )
                        }
                        if (overflow > 0) {
                            Text(
                                text = "+$overflow more",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                } else {
                    // Empty day — push content up, show subtle hint
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "No events",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDisabled
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    isTomorrow: Boolean,
    eventCount: Int,
    weatherCondition: WeatherCondition?,
    colors: WallColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Day name — today/tomorrow get full labels, others get full weekday name
            Text(
                text = when {
                    isToday -> "Today"
                    isTomorrow -> "Tomorrow"
                    else -> date.dayOfWeek.getDisplayName(
                        TextStyle.FULL, Locale.getDefault()
                    )
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isToday || isTomorrow) FontWeight.SemiBold
                else FontWeight.Normal,
                color = when {
                    isToday -> colors.accentPrimary
                    isTomorrow -> colors.textPrimary
                    else -> colors.textSecondary
                }
            )

            // Date number
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Light,
                color = if (isToday) colors.accentPrimary.copy(alpha = 0.7f)
                else colors.textMuted
            )

            // Weather icon
            if (weatherCondition != null &&
                weatherCondition != WeatherCondition.PARTLY_CLOUDY
            ) {
                Text(
                    text = weatherCondition.icon,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
            }
        }

        // Event count for days that have events
        if (eventCount > 0) {
            Text(
                text = "$eventCount ${if (eventCount == 1) "event" else "events"}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun WeekEventChip(
    event: CalendarEvent,
    isCompact: Boolean,
    colors: WallColors
) {
    val timeLabel = when {
        event.isAllDay -> "All day"
        event.startDateTime != null ->
            event.startDateTime.format(EventTimeFormatter).lowercase()
        else -> null
    }

    val barColor = when {
        event.isPromotedTask -> colors.accentWarm
        event.isAllDay -> colors.accentPrimary.copy(alpha = 0.6f)
        else -> colors.accentPrimary.copy(alpha = 0.3f)
    }

    val chipBackground = when {
        event.isPromotedTask -> colors.surfaceExpanded
        else -> colors.surfaceBlack.copy(alpha = 0.4f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EventChipShape)
            .background(chipBackground, EventChipShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left color bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(if (isCompact) 28.dp else 36.dp)
                .background(barColor)
        )

        if (isCompact) {
            // Compact: time and title inline
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Full: time above title for better readability
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (timeLabel != null) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        letterSpacing = 0.2.sp
                    )
                }
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (event.isPromotedTask) FontWeight.Medium
                    else FontWeight.Normal
                )
            }
        }
    }
}
