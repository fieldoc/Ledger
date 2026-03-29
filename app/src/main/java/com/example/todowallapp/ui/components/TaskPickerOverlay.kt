package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    expandedListIndex: Int,
    onFocusIndex: (Int) -> Unit,
    onExpandList: (Int) -> Unit,
    onSelectTask: (Task) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp

    // Build accordion flat list: all headers visible, only expanded list's tasks shown
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    val flatItems = buildList {
        nonEmptyLists.forEachIndexed { listIndex, (listName, tasks) ->
            add(PickerItem.ListHeader(listName, listIndex, tasks.size))
            if (listIndex == expandedListIndex) {
                tasks.forEach { add(PickerItem.TaskRow(it)) }
            }
        }
    }

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
                itemsIndexed(flatItems) { index, item ->
                    when (item) {
                        is PickerItem.ListHeader -> {
                            val isFocused = index == focusedIndex
                            PickerListHeader(
                                listName = item.listName,
                                taskCount = item.taskCount,
                                isExpanded = item.listIndex == expandedListIndex,
                                isFocused = isFocused,
                                onClick = { onExpandList(item.listIndex) }
                            )
                        }
                        is PickerItem.TaskRow -> {
                            val isFocused = index == focusedIndex
                            TaskPickerRow(
                                task = item.task,
                                isFocused = isFocused,
                                onClick = {
                                    onFocusIndex(index)
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
 * Returns the total number of focusable items in the accordion picker.
 * Includes list headers + tasks in the currently expanded list only.
 */
fun taskPickerFocusableCount(tasksByList: List<Pair<String, List<Task>>>, expandedListIndex: Int): Int {
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    val headerCount = nonEmptyLists.size
    val expandedTaskCount = nonEmptyLists.getOrNull(expandedListIndex)?.second?.size ?: 0
    return headerCount + expandedTaskCount
}

/**
 * Resolves whether the focused index points to a task row.
 * Returns the Task if so, null if it's a list header.
 */
fun taskPickerResolveTask(
    tasksByList: List<Pair<String, List<Task>>>,
    expandedListIndex: Int,
    focusedIndex: Int
): Task? {
    var idx = 0
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    for ((listIdx, pair) in nonEmptyLists.withIndex()) {
        if (idx == focusedIndex) return null // header
        idx++
        if (listIdx == expandedListIndex) {
            for (task in pair.second) {
                if (idx == focusedIndex) return task
                idx++
            }
        }
    }
    return null
}

/**
 * Resolves the list index if the focused index points to a header.
 * Returns -1 if it's a task row, not a header.
 */
fun taskPickerResolveHeaderListIndex(
    tasksByList: List<Pair<String, List<Task>>>,
    expandedListIndex: Int,
    focusedIndex: Int
): Int {
    var idx = 0
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    for ((listIdx, _) in nonEmptyLists.withIndex()) {
        if (idx == focusedIndex) return listIdx
        idx++
        if (listIdx == expandedListIndex) {
            idx += nonEmptyLists[listIdx].second.size
        }
    }
    return -1
}

/**
 * Computes the focus index for the header of the given list.
 */
fun taskPickerHeaderFocusIndex(
    tasksByList: List<Pair<String, List<Task>>>,
    expandedListIndex: Int,
    targetListIndex: Int
): Int {
    var idx = 0
    val nonEmptyLists = tasksByList.filter { it.second.isNotEmpty() }
    for ((listIdx, _) in nonEmptyLists.withIndex()) {
        if (listIdx == targetListIndex) return idx
        idx++ // header
        if (listIdx == expandedListIndex) {
            idx += nonEmptyLists[listIdx].second.size
        }
    }
    return idx
}

@Composable
private fun PickerListHeader(
    listName: String,
    taskCount: Int,
    isExpanded: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWallColors.current
    val bg by animateColorAsState(
        targetValue = if (isFocused) colors.accentPrimary.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(WallAnimations.SHORT),
        label = "headerBg"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "chevron"
    )
    val headerShape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, headerShape)
            .then(
                if (isFocused) Modifier.border(
                    1.dp,
                    colors.accentPrimary.copy(alpha = 0.3f),
                    headerShape
                ) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u25BE", // ▾ triangle
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
            )
            Text(
                text = listName,
                style = MaterialTheme.typography.titleSmall,
                color = if (isFocused) colors.textPrimary else colors.textSecondary
            )
        }
        Text(
            text = "$taskCount task${if (taskCount != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            modifier = Modifier.alpha(0.7f)
        )
    }
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
            .padding(start = 24.dp) // indented under the list header
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
                color = LocalWallColors.current.accentWarm,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private sealed class PickerItem {
    data class ListHeader(val listName: String, val listIndex: Int, val taskCount: Int) : PickerItem()
    data class TaskRow(val task: Task) : PickerItem()
}
