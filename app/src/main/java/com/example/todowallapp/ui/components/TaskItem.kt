package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.urgencyColor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.MockData
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.screens.SubtaskProgress
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val BadgeTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val PROMOTE_THRESHOLD = 0.4375f

private fun Task.accessibilityDescription(today: LocalDate = LocalDate.now()): String {
    val status = if (isCompleted) "Completed" else "Pending"
    val dueDescription = when (val due = dueDate) {
        null -> "No due date"
        else -> {
            val daysUntilDue = ChronoUnit.DAYS.between(today, due)
            when {
                daysUntilDue < 0 -> "${-daysUntilDue} days overdue"
                daysUntilDue == 0L -> "Due today"
                daysUntilDue == 1L -> "Due tomorrow"
                daysUntilDue <= 7 -> "Due in $daysUntilDue days"
                else -> "Due ${due.format(MonthDayFormatter)}"
            }
        }
    }

    val notesDescription = notes?.takeIf { it.isNotBlank() }?.let { ", Note: $it" }.orEmpty()
    return "$status task. $title. $dueDescription$notesDescription"
}

@Composable
fun TaskItem(
    task: Task,
    isSelected: Boolean,
    isChild: Boolean = false,
    isAmbientMode: Boolean = false,
    scheduledTaskIds: Set<String> = emptySet(),
    scheduledStartTime: LocalDateTime? = null,
    subtaskProgress: SubtaskProgress? = null,
    isExpanded: Boolean = false,
    holdProgressFraction: Float = 0f,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onScheduleTask: (() -> Unit)? = null,
    onOpenScheduledSlot: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val urgency = remember(task.id, task.dueDate, task.isCompleted) {
        task.getUrgencyLevel()
    }

    AnimatedTaskCompletion(task = task, modifier = modifier) { checkmarkAlpha, completionContentAlpha ->
        TaskItemContent(
            task = task,
            urgency = urgency,
            isSelected = isSelected,
            isChild = isChild,
            isAmbientMode = isAmbientMode,
            isScheduled = task.id in scheduledTaskIds,
            scheduledStartTime = scheduledStartTime,
            subtaskProgress = subtaskProgress,
            isExpanded = isExpanded,
            holdProgressFraction = holdProgressFraction,
            checkmarkAlpha = checkmarkAlpha,
            completionContentAlpha = completionContentAlpha,
            onClick = onClick,
            onLongClick = onLongClick,
            onScheduleTask = onScheduleTask,
            onOpenScheduledSlot = onOpenScheduledSlot
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskItemContent(
    task: Task,
    urgency: TaskUrgency,
    isSelected: Boolean,
    isChild: Boolean = false,
    isAmbientMode: Boolean,
    isScheduled: Boolean,
    scheduledStartTime: LocalDateTime?,
    subtaskProgress: SubtaskProgress?,
    isExpanded: Boolean,
    holdProgressFraction: Float,
    checkmarkAlpha: Float,
    completionContentAlpha: Float,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onScheduleTask: (() -> Unit)? = null,
    onOpenScheduledSlot: (() -> Unit)? = null
) {
    val today = LocalDate.now()
    val accessibilityDescription = remember(
        task.id,
        task.title,
        task.notes,
        task.isCompleted,
        task.dueDate,
        today
    ) {
        task.accessibilityDescription(today)
    }
    val ambientContentAlpha by animateFloatAsState(
        targetValue = when {
            isAmbientMode && isSelected -> 0.9f
            isAmbientMode -> 0.42f
            else -> 1f
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "ambientContentAlpha"
    )

    val cardShape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    val showUrgencyAccent = !task.isCompleted && urgency != TaskUrgency.NORMAL && !isAmbientMode
    val taskColors = LocalWallColors.current
    val urgencyAccentColor = if (urgency == TaskUrgency.COMPLETED || urgency == TaskUrgency.NORMAL) {
        Color.Transparent
    } else {
        taskColors.urgencyColor(urgency)
    }

    val contentAlpha = if (isAmbientMode) ambientContentAlpha else completionContentAlpha
    val colors = taskColors

    // --- Hybrid surface treatment ---
    val cardBackground by animateColorAsState(
        targetValue = when {
            isAmbientMode -> Color.Transparent
            isSelected -> colors.surfaceCard
            else -> Color.Transparent // borderline resting: transparent
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "taskBackground"
    )
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isAmbientMode && isSelected -> colors.ambientText.copy(alpha = 0.55f)
            task.isCompleted -> colors.dividerColor
            isSelected -> colors.borderFocused
            else -> colors.borderColor
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "taskBorderColor"
    )
    val glowElevation by animateDpAsState(
        targetValue = if (isSelected && !isAmbientMode) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "taskGlowElevation"
    )
    val completedAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted && !isAmbientMode) 0.35f else 1f,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "completedAlpha"
    )

    // --- Completion ripple (Task 6) ---
    val rippleProgress = remember { Animatable(0f) }
    LaunchedEffect(task.isCompleted) {
        if (task.isCompleted) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, tween(WallAnimations.SHORT))
        }
    }

    val animatedUrgencyAlpha by animateFloatAsState(
        targetValue = if (showUrgencyAccent) 1f else 0f,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "urgencyBarAlpha"
    )
    val animatedUrgencyColor by animateColorAsState(
        targetValue = urgencyAccentColor,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "urgencyBarColor"
    )
    val holdProgressArcColor by animateColorAsState(
        targetValue = if (holdProgressFraction < PROMOTE_THRESHOLD) {
            taskColors.accentPrimary
        } else {
            taskColors.urgencyDueToday
        },
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "holdProgressArcColor"
    )
    
    val connectingLineColor = if (isChild) colors.textMuted.copy(alpha = 0.3f) else Color.Transparent
    val shouldUseSelectionLayer = glowElevation > 0.dp && !isAmbientMode
    val focusBorderColor by animateColorAsState(
        targetValue = when {
            !isSelected || isAmbientMode -> Color.Transparent
            showUrgencyAccent -> animatedUrgencyColor.copy(alpha = 0.45f)
            else -> colors.accentPrimary.copy(alpha = 0.35f)
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "focusBorderGlow"
    )
    val cardContainerModifier = if (shouldUseSelectionLayer) {
        Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = -1f }
            .shadow(
                elevation = glowElevation,
                shape = cardShape,
                spotColor = if (showUrgencyAccent) animatedUrgencyColor.copy(alpha = 0.6f) else colors.accentPrimary.copy(alpha = 0.4f),
                ambientColor = Color.Black.copy(alpha = 0.3f),
                clip = false
            )
            .border(1.5.dp, focusBorderColor, cardShape)
    } else {
        Modifier.fillMaxWidth()
    }

    Box(
        modifier = cardContainerModifier
            .alpha(completedAlpha)
            .then(
                if (isChild) {
                    Modifier.drawBehind {
                        val lineX = -(8.dp.toPx())
                        val strokeWidth = 1.5.dp.toPx()
                        drawLine(
                            color = connectingLineColor,
                            start = Offset(lineX, 0f),
                            end = Offset(lineX, size.height),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    shape = cardShape
                    clip = true
                }
                .combinedClickable(
                    role = Role.Checkbox,
                    onClickLabel = if (task.isCompleted) "Mark task as incomplete" else "Mark task as complete",
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = if (onLongClick != null) "Show task options" else null
                )
                .background(cardBackground)
                .border(1.dp, cardBorderColor, cardShape)
                .alpha(contentAlpha)
        ) {
            // Rim Gloss (1dp highlight)
            if (!isAmbientMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent, 
                                    if (showUrgencyAccent) animatedUrgencyColor.copy(alpha = 0.4f) else Color(0x33FFFFFF), 
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // Temperature-based Urgency Line
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(8.dp)
                    .align(Alignment.CenterStart)
                    .alpha(animatedUrgencyAlpha)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(animatedUrgencyColor, animatedUrgencyColor.copy(alpha = 0.2f))
                        )
                    )
            )
            
            if (isSelected && holdProgressFraction > 0f) {
                HoldProgressArc(
                    progress = holdProgressFraction,
                    color = holdProgressArcColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                TaskStatusIndicator(
                    isCompleted = task.isCompleted,
                    isAmbientMode = isAmbientMode,
                    isSelected = isSelected,
                    subtaskProgress = subtaskProgress,
                    checkmarkAlpha = checkmarkAlpha
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isAmbientMode) LocalWallColors.current.ambientText else LocalWallColors.current.textPrimary,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ScheduledCalendarBadge(
                                visible = isScheduled && !task.isCompleted,
                                isAmbientMode = isAmbientMode,
                                scheduledStartTime = scheduledStartTime,
                                onClick = if (!isAmbientMode) onOpenScheduledSlot else null
                            )
                            if (!task.isCompleted && !isScheduled && !isAmbientMode && onScheduleTask != null) {
                                Text(
                                    text = "\u23F1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalWallColors.current.textMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = "Schedule task",
                                            onClick = onScheduleTask
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            SubtaskCountBadge(
                                subtaskProgress = subtaskProgress,
                                isExpanded = isExpanded,
                                isAmbientMode = isAmbientMode
                            )
                        }
                    }

                    if (!task.notes.isNullOrBlank() && !task.isCompleted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = task.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isAmbientMode) LocalWallColors.current.ambientText.copy(alpha = 0.82f) else LocalWallColors.current.textSecondary.copy(alpha = 0.94f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (task.dueDate != null && !task.isCompleted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DueDateBadge(
                            dueDate = task.dueDate,
                            isAmbientMode = isAmbientMode
                        )
                    }
                }
            }
        }

        // Completion ripple overlay (Task 6)
        if (rippleProgress.value > 0f && rippleProgress.value < 1f) {
            Canvas(modifier = Modifier.matchParentSize().clip(cardShape)) {
                val checkboxCenter = Offset(24.dp.toPx(), size.height / 2)
                val maxRadius = size.width
                val currentRadius = maxRadius * rippleProgress.value
                val rippleAlpha = (1f - rippleProgress.value) * 0.15f
                val rippleColor = lerp(
                    colors.accentPrimary,
                    colors.accentWarm,
                    rippleProgress.value
                )
                drawCircle(
                    color = rippleColor.copy(alpha = rippleAlpha),
                    radius = currentRadius,
                    center = checkboxCenter
                )
            }
        }
    }
}

@Composable
private fun ScheduledCalendarBadge(
    visible: Boolean,
    isAmbientMode: Boolean,
    scheduledStartTime: LocalDateTime?,
    onClick: (() -> Unit)? = null
) {
    if (!visible) return
    val shape = RoundedCornerShape(4.dp)
    val borderColor = if (isAmbientMode) {
        LocalWallColors.current.ambientText.copy(alpha = 0.45f)
    } else {
        LocalWallColors.current.accentPrimary.copy(alpha = 0.45f)
    }
    val fillColor = if (isAmbientMode) {
        LocalWallColors.current.ambientText.copy(alpha = 0.12f)
    } else {
        LocalWallColors.current.accentPrimary.copy(alpha = 0.12f)
    }
    val textColor = if (isAmbientMode) {
        LocalWallColors.current.ambientText.copy(alpha = 0.88f)
    } else {
        LocalWallColors.current.textPrimary
    }
    val label = remember(scheduledStartTime) {
        formatScheduledLabel(scheduledStartTime)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "Open scheduled time", onClick = onClick)
                } else {
                    Modifier
                }
            )
            .height(20.dp)
            .clip(shape)
            .background(fillColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1
        )
    }
}

@Composable
private fun HoldProgressArc(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(24.dp)
    ) {
        val strokeWidth = 2.5.dp.toPx()
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun formatScheduledLabel(scheduledStartTime: LocalDateTime?): String {
    if (scheduledStartTime == null) return "Scheduled"
    val date = scheduledStartTime.toLocalDate()
    val today = LocalDate.now()
    val dayLabel = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(MonthDayFormatter)
    }
    return "$dayLabel ${scheduledStartTime.format(BadgeTimeFormatter)}"
}

@Composable
private fun SubtaskCountBadge(
    subtaskProgress: SubtaskProgress?,
    isExpanded: Boolean,
    isAmbientMode: Boolean
) {
    if (isAmbientMode || subtaskProgress?.hasSubtasks != true) return
    val badgeAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 0.5f else 1f,
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "subtaskBadgeAlpha"
    )
    Text(
        text = "${subtaskProgress.completed}/${subtaskProgress.total}",
        style = MaterialTheme.typography.labelSmall,
        color = LocalWallColors.current.textMuted.copy(alpha = badgeAlpha)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun TaskItemPreview() {
    LedgerTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            TaskItem(
                task = MockData.sampleTasks[0],
                isSelected = true
            )
            TaskItem(
                task = MockData.sampleTasks[3],
                isSelected = false
            )
            TaskItem(
                task = MockData.sampleTasks[5],
                isSelected = false
            )
        }
    }
}


