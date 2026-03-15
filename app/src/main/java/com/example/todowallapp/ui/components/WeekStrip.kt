package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekStrip(
    startDate: LocalDate,
    selectedDate: LocalDate,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    taskUrgencyByTaskId: Map<String, TaskUrgency>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(7) { offset ->
            val date = startDate.plusDays(offset.toLong())
            val isSelected = date == selectedDate
            val isToday = date == today
            val dayEvents = eventsByDate[date].orEmpty()

            val containerColor by animateColorAsState(
                targetValue = if (isSelected) colors.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                animationSpec = tween(WallAnimations.SHORT),
                label = "weekStripContainer"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(containerColor, RoundedCornerShape(10.dp))
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.accentPrimary else colors.textMuted
                )
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) colors.accentPrimary else colors.textPrimary
                )

                if (dayEvents.isEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dayEvents.take(3).forEach { event ->
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = eventDotColor(
                                            event = event,
                                            taskUrgencyByTaskId = taskUrgencyByTaskId
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                if (isToday && !isSelected) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(width = 12.dp, height = 2.dp)
                            .background(colors.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun eventDotColor(
    event: CalendarEvent,
    taskUrgencyByTaskId: Map<String, TaskUrgency>
): Color {
    val colors = LocalWallColors.current
    if (!event.isPromotedTask) return colors.accentPrimary

    return when (event.sourceTaskId?.let(taskUrgencyByTaskId::get)) {
        TaskUrgency.OVERDUE -> colors.urgencyOverdue
        TaskUrgency.DUE_TODAY -> colors.urgencyDueToday
        TaskUrgency.DUE_SOON -> colors.urgencyDueSoon
        else -> colors.accentPrimary
    }
}
