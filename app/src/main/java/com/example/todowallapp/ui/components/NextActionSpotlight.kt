package com.example.todowallapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.theme.LocalWallColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SpotlightTask(
    val task: Task,
    val listName: String,
    val subtaskCount: Int,
    val subtaskCompleted: Int
)

/**
 * Priority cascade for selecting the single most actionable task:
 * 1. Overdue tasks (oldest due date first)
 * 2. Due-today tasks
 * 3. Due-soon tasks (within 3 days)
 * 4. Tasks with due dates (nearest first)
 * 5. Tasks without due dates (by position — first in list)
 */
fun computeNextAction(
    allTasks: List<Pair<String, Task>>,
    subtaskCounts: Map<String, Pair<Int, Int>>
): SpotlightTask? {
    if (allTasks.isEmpty()) return null

    val today = LocalDate.now()
    val scored = allTasks
        .filter { (_, task) -> !task.isCompleted && task.parentId == null }
        .sortedWith(
            compareBy<Pair<String, Task>> { (_, task) ->
                when (task.getUrgencyLevel()) {
                    TaskUrgency.OVERDUE -> 0
                    TaskUrgency.DUE_TODAY -> 1
                    TaskUrgency.DUE_SOON -> 2
                    TaskUrgency.NORMAL -> 3
                    TaskUrgency.COMPLETED -> 4
                }
            }.thenBy { (_, task) ->
                task.dueDate?.let { ChronoUnit.DAYS.between(today, it) } ?: Long.MAX_VALUE
            }
        )

    val (listName, bestTask) = scored.firstOrNull() ?: return null
    val (total, completed) = subtaskCounts[bestTask.id] ?: (0 to 0)
    return SpotlightTask(bestTask, listName, total, completed)
}

@Composable
fun NextActionSpotlight(
    spotlight: SpotlightTask,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your next move",
            style = MaterialTheme.typography.labelLarge,
            color = colors.accentPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.alpha(0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surfaceCard.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    colors.accentPrimary.copy(alpha = 0.08f),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = spotlight.task.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                val contextParts = buildList {
                    add(spotlight.listName)
                    spotlight.task.dueDate?.let { due ->
                        val daysUntil = ChronoUnit.DAYS.between(today, due)
                        add(
                            when {
                                daysUntil < 0 -> "${-daysUntil}d overdue"
                                daysUntil == 0L -> "due today"
                                daysUntil == 1L -> "due tomorrow"
                                daysUntil <= 7 -> "due in ${daysUntil}d"
                                else -> "due ${due.format(MonthDayFormatter)}"
                            }
                        )
                    }
                    if (spotlight.subtaskCount > 0) {
                        add("${spotlight.subtaskCompleted}/${spotlight.subtaskCount} subtasks")
                    }
                }

                Text(
                    text = contextParts.joinToString("  \u00B7  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (spotlight.task.getUrgencyLevel() == TaskUrgency.OVERDUE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.urgencyOverdue.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
