package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.ui.screens.SubtaskProgress
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal val MonthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
internal fun AnimatedTaskCompletion(
    task: Task,
    modifier: Modifier = Modifier,
    content: @Composable (checkmarkAlpha: Float, contentAlpha: Float) -> Unit
) {
    val checkmarkAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 1f else 0f,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "checkmarkAlpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.5f else 1f,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "contentAlpha"
    )

    Box(modifier = modifier) {
        content(checkmarkAlpha, contentAlpha)
    }
}

@Composable
internal fun TaskStatusIndicator(
    isCompleted: Boolean,
    isAmbientMode: Boolean,
    isSelected: Boolean,
    subtaskProgress: SubtaskProgress?,
    checkmarkAlpha: Float = 1f,
    ringSize: Dp = 36.dp,
    innerSize: Dp = 28.dp,
    checkmarkSize: Dp = 20.dp
) {
    val colors = LocalWallColors.current
    val strokeWidth = 2.5.dp
    val targetProgress = subtaskProgress?.fraction?.coerceIn(0f, 1f) ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 300),
        label = "subtaskProgressArc"
    )
    val checkboxFillColor by animateColorAsState(
        targetValue = if (isCompleted) colors.accentWarm else Color.Transparent,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "checkboxFill"
    )

    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        if (!isAmbientMode && !isCompleted && subtaskProgress?.hasSubtasks == true) {
            Box(
                modifier = Modifier
                    .size(ringSize)
                    .drawWithCache {
                        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                        onDrawBehind {
                            drawArc(
                                color = colors.borderColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = stroke
                            )
                            if (animatedProgress > 0f) {
                                drawArc(
                                    color = colors.accentPrimary.copy(alpha = 0.55f),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = stroke
                                )
                            }
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(CircleShape)
                .background(checkboxFillColor)
                .border(
                    width = if (isSelected) 2.dp else 1.5.dp,
                    color = when {
                        isAmbientMode && isSelected -> colors.ambientText.copy(alpha = 0.85f)
                        isAmbientMode -> colors.ambientText.copy(alpha = 0.65f)
                        isSelected -> colors.accentPrimary
                        else -> colors.accentPrimary.copy(alpha = 0.8f)
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.surfaceCard,
                    modifier = Modifier
                        .size(checkmarkSize)
                        .alpha(checkmarkAlpha)
                )
            }
        }
    }
}

@Composable
internal fun DueDateBadge(
    dueDate: LocalDate,
    isAmbientMode: Boolean
) {
    val today = LocalDate.now()
    val daysUntilDue = ChronoUnit.DAYS.between(today, dueDate)
    val isOverdue = daysUntilDue < 0

    val displayText = when {
        daysUntilDue < 0 -> "${-daysUntilDue}d overdue"
        daysUntilDue == 0L -> "Today"
        daysUntilDue == 1L -> "Tomorrow"
        daysUntilDue <= 7 -> "${daysUntilDue}d"
        else -> dueDate.format(MonthDayFormatter)
    }

    val textColor = when {
        isAmbientMode -> LocalWallColors.current.ambientText.copy(alpha = 0.85f)
        isOverdue -> LocalWallColors.current.urgencyOverdue
        else -> LocalWallColors.current.textSecondary.copy(alpha = 0.92f)
    }

    if (isOverdue) {
        Box(
            modifier = Modifier
                .border(
                    1.dp,
                    LocalWallColors.current.urgencyOverdue,
                    RoundedCornerShape(WallShapes.SmallCornerRadius.dp)
                )
                .background(
                    LocalWallColors.current.urgencyOverdueSubtle,
                    shape = RoundedCornerShape(WallShapes.SmallCornerRadius.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall,
                color = textColor
            )
        }
    } else {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
