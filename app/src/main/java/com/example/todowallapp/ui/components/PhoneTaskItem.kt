package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.TaskUrgency
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.urgencyColor
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import kotlinx.coroutines.delay

import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneTaskItem(
    task: Task,
    onTaskToggle: (Task) -> Unit,
    children: List<Task> = emptyList(),
    onToggleChildComplete: (Task) -> Unit = onTaskToggle,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val cardShape = RoundedCornerShape(8.dp)
    val urgency = remember(task.id, task.dueDate, task.isCompleted) { task.getUrgencyLevel() }
    val showUrgencyBar = !task.isCompleted && urgency != TaskUrgency.NORMAL
    val urgencyBarColor = if (showUrgencyBar) colors.urgencyColor(urgency) else Color.Transparent
    val urgencyAlpha by animateFloatAsState(
        targetValue = if (showUrgencyBar) 1f else 0f,
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "phoneUrgencyAlpha"
    )
    var childrenExpanded by rememberSaveable(task.id) { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Squish animation: scale down and slightly flatten
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "phoneTaskPressScale"
    )
    
    val childrenChevronRotation by animateFloatAsState(
        targetValue = if (childrenExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = WallAnimations.SHORT),
        label = "phoneSubtaskChevron"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedTaskCompletion(task = task) { checkmarkAlpha, contentAlpha ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .clip(cardShape)
                    .border(1.dp, colors.borderColor, cardShape)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Checkbox,
                        onClickLabel = if (task.isCompleted) "Mark task as incomplete" else "Mark task as complete",
                        onClick = { onTaskToggle(task) },
                        onLongClick = onLongClick
                    )
                    .background(if (task.isCompleted) Color.Transparent else colors.surfaceCard.copy(alpha = 0.15f))
                    .alpha(contentAlpha)
            ) {
                // Rim highlight (Gloss)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, colors.rimGlossStrong, Color.Transparent)
                            )
                        )
                )

                // Refined Urgency Gradient
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.CenterStart)
                        .alpha(urgencyAlpha)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(urgencyBarColor, urgencyBarColor.copy(alpha = 0.3f))
                            )
                        )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    TaskStatusIndicator(
                        isCompleted = task.isCompleted,
                        isAmbientMode = false,
                        isSelected = false,
                        subtaskProgress = null,
                        checkmarkAlpha = checkmarkAlpha,
                        ringSize = 26.dp,
                        innerSize = 20.dp,
                        checkmarkSize = 13.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!task.cleanNotes.isNullOrBlank() && !task.isCompleted) {
                            Text(
                                text = task.cleanNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (task.dueDate != null && !task.isCompleted) {
                        Spacer(modifier = Modifier.width(10.dp))
                        DueDateBadge(
                            dueDate = task.dueDate,
                            isAmbientMode = false
                        )
                    }

                    if (children.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.borderColor.copy(alpha = 0.2f))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (childrenExpanded) "Collapse subtasks" else "Expand subtasks"
                                ) {
                                    childrenExpanded = !childrenExpanded
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = children.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (childrenExpanded) "Collapse subtasks" else "Expand subtasks",
                                tint = colors.textMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .rotate(childrenChevronRotation)
                            )
                        }
                    }
                }
            }
        }

        if (children.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp)
            ) {
                children.forEachIndexed { index, child ->
                    var childVisible by remember(child.id) { mutableStateOf(false) }

                    LaunchedEffect(childrenExpanded, child.id) {
                        if (childrenExpanded) {
                            delay(index * WallAnimations.STAGGER_ENTER.toLong())
                            childVisible = true
                        } else {
                            delay(index * WallAnimations.STAGGER_EXIT.toLong())
                            childVisible = false
                        }
                    }

                    AnimatedVisibility(
                        visible = childVisible,
                        enter = expandVertically(tween(WallAnimations.MEDIUM)) + fadeIn(tween(WallAnimations.SHORT)),
                        exit = shrinkVertically(tween(WallAnimations.SHORT)) + fadeOut(tween(WallAnimations.SHORT))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(44.dp)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(colors.borderColor.copy(alpha = 0.3f), Color.Transparent)
                                        )
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            PhoneSubtaskItem(
                                task = child,
                                onToggleComplete = { onToggleChildComplete(child) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSubtaskItem(
    task: Task,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    AnimatedTaskCompletion(task = task, modifier = modifier) { checkmarkAlpha, contentAlpha ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Checkbox, onClick = onToggleComplete)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskStatusIndicator(
                isCompleted = task.isCompleted,
                isAmbientMode = false,
                isSelected = false,
                subtaskProgress = null,
                checkmarkAlpha = checkmarkAlpha,
                ringSize = 22.dp,
                innerSize = 16.dp,
                checkmarkSize = 10.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary.copy(alpha = contentAlpha),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
