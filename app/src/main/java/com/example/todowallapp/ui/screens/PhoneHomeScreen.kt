package com.example.todowallapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.Task
import com.example.todowallapp.data.model.sortTasksForDisplay
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.example.todowallapp.ui.components.PhoneAccordionSection
import com.example.todowallapp.ui.components.PhoneTaskItem
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallShapes
import com.example.todowallapp.viewmodel.PhoneCaptureUiState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.todowallapp.ui.components.PhoneCaptureHub
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
fun PhoneHomeScreen(
    uiState: PhoneCaptureUiState,
    onTaskToggle: (Task) -> Unit,
    onToggleListExpanded: ((String) -> Unit)? = null,
    onRetryPendingCapture: (String) -> Unit,
    onRemovePendingCapture: (String) -> Unit,
    onCameraClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSaveCaptureForRetry: () -> Unit,
    onDismissMessage: () -> Unit,
    onDeleteTask: ((Task) -> Unit)? = null,
    onUndoCompletion: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var localExpandedListIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val expandedListIds = if (onToggleListExpanded != null || uiState.expandedListIds.isNotEmpty()) {
        uiState.expandedListIds
    } else {
        localExpandedListIds.toSet()
    }
    val colors = LocalWallColors.current
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000 * 60L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Top Utility Cluster
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isLoading || uiState.isParsingCapture || uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.accentPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Sync",
                        tint = colors.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = colors.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bold Header with Glass-style background
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = colors.surfaceCard.copy(alpha = 0.3f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x0FFFFFFF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accentPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "My Tasks",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Text(
                        text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.textSecondary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            uiState.error?.let { error ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.urgencyOverdueSubtle.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.urgencyOverdue.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.urgencyOverdue
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "Save for later",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = onSaveCaptureForRetry)
                            )
                            Text(
                                text = "Dismiss",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                                modifier = Modifier.clickable(onClick = onDismissMessage)
                            )
                        }
                    }
                }
            }

            uiState.infoMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.3f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accentPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDismissMessage)
                            .padding(14.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (uiState.pendingCaptures.isNotEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp),
                            color = LocalWallColors.current.surfaceCard,
                            contentColor = LocalWallColors.current.textPrimary
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Pending Retries",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = LocalWallColors.current.accentPrimary
                                )
                                uiState.pendingCaptures.forEach { pending ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Capture ${pending.id.take(8)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = LocalWallColors.current.textPrimary
                                            )
                                            pending.lastError?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = LocalWallColors.current.urgencyOverdue
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text(
                                                text = "Retry",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = LocalWallColors.current.accentPrimary,
                                                modifier = Modifier.clickable { onRetryPendingCapture(pending.id) }
                                            )
                                            Text(
                                                text = "Delete",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = LocalWallColors.current.urgencyOverdue,
                                                modifier = Modifier.clickable { onRemovePendingCapture(pending.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.taskLists.isEmpty() && !uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tasks yet.\nTap the mic to capture something.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = LocalWallColors.current.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                uiState.taskLists.forEachIndexed { index, listWithTasks ->
                    item(key = "accordion_${listWithTasks.taskList.id}") {
                        val groups = remember(listWithTasks.tasks) {
                            groupTasksForPhone(sortTasksForDisplay(listWithTasks.tasks))
                        }
                        val pendingGroups = groups.filter { !it.parent.isCompleted }
                        val completedGroups = groups.filter { it.parent.isCompleted }
                        val listId = listWithTasks.taskList.id
                        val isExpanded = listId in expandedListIds

                        PhoneAccordionSection(
                            title = listWithTasks.taskList.title,
                            taskCount = pendingGroups.size,
                            isExpanded = isExpanded,
                            peekText = pendingGroups.firstOrNull()?.parent?.title
                                ?: completedGroups.firstOrNull()?.parent?.title,
                            sectionIndex = index,
                            onToggle = {
                                if (onToggleListExpanded != null) {
                                    onToggleListExpanded(listId)
                                } else {
                                    localExpandedListIds = if (listId in localExpandedListIds) {
                                        localExpandedListIds - listId
                                    } else {
                                        localExpandedListIds + listId
                                    }
                                }
                            }
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pendingGroups.forEach { group ->
                                    PhoneTaskItem(
                                        task = group.parent,
                                        onTaskToggle = onTaskToggle,
                                        children = group.children,
                                        onToggleChildComplete = onTaskToggle,
                                        onLongClick = if (onDeleteTask != null) {
                                            { taskToDelete = group.parent }
                                        } else null
                                    )
                                }

                                if (completedGroups.isNotEmpty()) {
                                    Text(
                                        text = "${completedGroups.size} completed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalWallColors.current.textMuted,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                    completedGroups.forEach { group ->
                                        PhoneTaskItem(
                                            task = group.parent,
                                            onTaskToggle = onTaskToggle,
                                            children = group.children,
                                            onToggleChildComplete = onTaskToggle,
                                            onLongClick = if (onDeleteTask != null) {
                                                { taskToDelete = group.parent }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        PhoneCaptureHub(
            onCameraClick = onCameraClick,
            onVoiceClick = onVoiceClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
        )

        // Undo banner
        AnimatedVisibility(
            visible = uiState.undoTask != null && onUndoCompletion != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.3f)),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Task completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Undo",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accentPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onUndoCompletion?.invoke() }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    taskToDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete task?") },
            text = { Text("\"${task.title}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTask?.invoke(task)
                    taskToDelete = null
                }) {
                    Text("Delete", color = colors.urgencyOverdue)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class PhoneTaskGroup(
    val parent: Task,
    val children: List<Task>
)

private fun groupTasksForPhone(tasks: List<Task>): List<PhoneTaskGroup> {
    val parents = tasks.filter { it.parentId == null }
    val parentIds = parents.mapTo(mutableSetOf()) { it.id }
    val childrenByParent = tasks
        .filter { it.parentId != null && it.parentId in parentIds }
        .groupBy { it.parentId!! }

    val orphanChildren = tasks.filter { it.parentId != null && it.parentId !in parentIds }
    val parentGroups = parents.map { parent ->
        PhoneTaskGroup(
            parent = parent,
            children = childrenByParent[parent.id].orEmpty()
        )
    }

    return parentGroups + orphanChildren.map { orphan ->
        PhoneTaskGroup(
            parent = orphan,
            children = emptyList()
        )
    }
}
