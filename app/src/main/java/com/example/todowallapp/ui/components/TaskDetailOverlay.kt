package com.example.todowallapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskPriority
import com.example.todowallapp.ui.screens.SubtaskProgress
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallShapes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DetailDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val DetailTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

@Composable
fun TaskDetailOverlay(
    visible: Boolean,
    task: Task?,
    listName: String,
    subtaskProgress: SubtaskProgress? = null,
    scheduledTime: LocalDateTime? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible || task == null) return

    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .widthIn(min = 320.dp, max = 500.dp)
                .background(colors.surfaceElevated, shape)
                .border(1.dp, colors.borderColor, shape)
                .padding(vertical = 12.dp)
                .semantics { dialog() }
        ) {
            // Title
            Text(
                text = "Task Details",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Notes (scrollable)
            if (!task.cleanNotes.isNullOrBlank()) {
                HorizontalDivider(
                    color = colors.borderColor,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        text = task.cleanNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        overflow = TextOverflow.Visible
                    )
                }
            }

            HorizontalDivider(
                color = colors.borderColor,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Metadata rows
            Column(
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                // Due date
                if (task.dueDate != null) {
                    val dueLabel = formatDueLabel(task.dueDate)
                    DetailMetadataRow(
                        icon = "\uD83D\uDCC5",
                        label = "Due: $dueLabel"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Priority
                if (task.priority != TaskPriority.NORMAL) {
                    val priorityLabel = when (task.priority) {
                        TaskPriority.HIGH -> "High"
                        TaskPriority.MEDIUM -> "Medium"
                        TaskPriority.NORMAL -> "Normal"
                    }
                    DetailMetadataRow(
                        icon = "\u25CF",
                        label = "Priority: $priorityLabel"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Recurrence
                if (task.recurrenceRule != null) {
                    DetailMetadataRow(
                        icon = "\u21BB",
                        label = task.recurrenceRule.toHumanReadable()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // List name
                DetailMetadataRow(
                    icon = "\uD83D\uDCC1",
                    label = "List: $listName"
                )

                // Subtask progress
                if (subtaskProgress?.hasSubtasks == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    DetailMetadataRow(
                        icon = "\uD83D\uDD22",
                        label = "Subtasks: ${subtaskProgress.completed}/${subtaskProgress.total} done"
                    )
                }

                // Scheduled time
                if (scheduledTime != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val timeLabel = "${scheduledTime.toLocalDate().format(DetailDateFormatter)} at ${scheduledTime.format(DetailTimeFormatter)}"
                    DetailMetadataRow(
                        icon = "\u23F0",
                        label = "Scheduled: $timeLabel"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun DetailMetadataRow(icon: String, label: String) {
    val colors = LocalWallColors.current
    Text(
        text = "$icon  $label",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary
    )
}

private fun formatDueLabel(dueDate: LocalDate): String {
    val today = LocalDate.now()
    val days = ChronoUnit.DAYS.between(today, dueDate)
    return when {
        days < -1 -> "${-days} days overdue"
        days == -1L -> "Yesterday (overdue)"
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days <= 7 -> "In $days days"
        else -> dueDate.format(DetailDateFormatter)
    }
}
