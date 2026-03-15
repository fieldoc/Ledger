package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.format.DateTimeFormatter

private val DueDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun TaskPickerOverlay(
    visible: Boolean,
    tasksByList: List<Pair<String, List<Task>>>,
    focusedIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onSelectTask: (Task) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    // Build flat list of items with group headers interleaved
    val flatItems = buildList {
        tasksByList.forEach { (listName, tasks) ->
            if (tasks.isNotEmpty()) {
                add(PickerItem.Header(listName))
                tasks.forEach { add(PickerItem.TaskRow(it)) }
            }
        }
    }

    // Map focusedIndex to only TaskRow items
    val taskRowIndices = flatItems.indices.filter { flatItems[it] is PickerItem.TaskRow }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.36f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = maxHeight)
                .padding(bottom = 36.dp)
                .background(LocalWallColors.current.surfaceCard, shape)
                .border(1.dp, LocalWallColors.current.borderColor, shape)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pick a task to schedule",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalWallColors.current.textPrimary
                )
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalWallColors.current.textSecondary,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(flatItems) { flatIndex, item ->
                    when (item) {
                        is PickerItem.Header -> {
                            Text(
                                text = item.listName,
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalWallColors.current.textMuted,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
                            )
                        }
                        is PickerItem.TaskRow -> {
                            val taskIndex = taskRowIndices.indexOf(flatIndex)
                            val isFocused = taskIndex == focusedIndex
                            TaskPickerRow(
                                task = item.task,
                                isFocused = isFocused,
                                onClick = {
                                    onFocusIndex(taskIndex)
                                    onSelectTask(item.task)
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Twist to browse \u2022 Click to schedule",
                style = MaterialTheme.typography.labelSmall,
                color = LocalWallColors.current.textMuted,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/**
 * Returns the total number of selectable task rows in the picker.
 */
fun taskPickerRowCount(tasksByList: List<Pair<String, List<Task>>>): Int {
    return tasksByList.sumOf { it.second.size }
}

@Composable
private fun TaskPickerRow(
    task: Task,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isFocused) LocalWallColors.current.accentPrimary.copy(alpha = 0.18f) else LocalWallColors.current.surfaceElevated,
        animationSpec = tween(WallAnimations.SHORT),
        label = "pickerRowBg"
    )
    val borderColor = if (isFocused) LocalWallColors.current.accentPrimary else LocalWallColors.current.borderColor
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(WallShapes.MediumCornerRadius.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(WallShapes.MediumCornerRadius.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWallColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (task.dueDate != null) {
            Text(
                text = task.dueDate.format(DueDateFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = LocalWallColors.current.textMuted,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private sealed class PickerItem {
    data class Header(val listName: String) : PickerItem()
    data class TaskRow(val task: Task) : PickerItem()
}
