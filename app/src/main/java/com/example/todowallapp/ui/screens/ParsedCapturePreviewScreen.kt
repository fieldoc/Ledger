package com.example.todowallapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.capture.model.ListTarget
import com.example.todowallapp.capture.model.ParsedCapture
import com.example.todowallapp.capture.model.ParsedTaskDraft
import com.example.todowallapp.capture.repository.ExistingListRef
import com.example.todowallapp.ui.theme.LocalWallColors

@Composable
fun ParsedCapturePreviewScreen(
    parsedCapture: ParsedCapture,
    existingLists: List<ExistingListRef>,
    isCommitting: Boolean,
    onUpdateListName: (listLocalId: String, newName: String) -> Unit,
    onAssignListToExisting: (listLocalId: String, existingListId: String?) -> Unit,
    onUpdateTaskTitle: (taskLocalId: String, newTitle: String) -> Unit,
    onRemoveTask: (taskLocalId: String) -> Unit,
    onCancel: () -> Unit,
    onAddAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val listSectionShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Review Tasks",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (parsedCapture.warnings.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = colors.urgencyOverdueSubtle.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, colors.urgencyOverdue.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        parsedCapture.warnings.forEach { warning ->
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.urgencyOverdue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(parsedCapture.lists, key = { it.localId }) { listDraft ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = listSectionShape,
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Group Details",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            
                            OutlinedTextField(
                                value = listDraft.name,
                                onValueChange = { onUpdateListName(listDraft.localId, it) },
                                label = { Text("Group Name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = wallOutlinedTextFieldColors()
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Target Destination",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        PillTextAction(
                                            text = "New List",
                                            onClick = { onAssignListToExisting(listDraft.localId, null) },
                                            selected = listDraft.target == ListTarget.NEW_LIST
                                        )
                                    }
                                    items(existingLists, key = { it.id }) { existing ->
                                            PillTextAction(
                                                text = existing.title,
                                                onClick = { onAssignListToExisting(listDraft.localId, existing.id) },
                                                selected = listDraft.existingListId == existing.id
                                            )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            
                            listDraft.tasks.forEach { task ->
                                ParsedTaskEditorRow(
                                    task = task,
                                    depth = 0,
                                    onUpdateTaskTitle = onUpdateTaskTitle,
                                    onRemoveTask = onRemoveTask
                                )
                            }
                        }
                    }
                }
            }

            // High-end Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BottomActionButton(
                    text = "Cancel",
                    prominent = false,
                    enabled = true,
                    onClick = onCancel,
                    modifier = Modifier.weight(0.6f)
                )
                BottomActionButton(
                    text = "Add Tasks",
                    prominent = true,
                    enabled = !isCommitting,
                    onClick = onAddAll,
                    modifier = Modifier.weight(1f)
                )
            }

            if (isCommitting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.accentPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Syncing with Google Tasks...",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ParsedTaskEditorRow(
    task: ParsedTaskDraft,
    depth: Int,
    onUpdateTaskTitle: (taskLocalId: String, newTitle: String) -> Unit,
    onRemoveTask: (taskLocalId: String) -> Unit
) {
    val colors = LocalWallColors.current
    val taskRowShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 14).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(taskRowShape)
                .background(colors.surfaceBlack.copy(alpha = 0.3f))
                .border(1.dp, colors.borderColor.copy(alpha = 0.2f), taskRowShape)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = task.title,
                onValueChange = { onUpdateTaskTitle(task.localId, it) },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                colors = wallOutlinedTextFieldColors()
            )
            IconButton(onClick = { onRemoveTask(task.localId) }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Remove",
                    tint = colors.urgencyOverdue.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        task.subtasks.forEach { child ->
            ParsedTaskEditorRow(
                task = child,
                depth = depth + 1,
                onUpdateTaskTitle = onUpdateTaskTitle,
                onRemoveTask = onRemoveTask
            )
        }
    }
}

@Composable
private fun PillTextAction(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    destructive: Boolean = false
) {
    val colors = LocalWallColors.current
    val actionShape = RoundedCornerShape(999.dp)
    val backgroundColor = when {
        destructive -> colors.urgencyOverdueSubtle.copy(alpha = 0.2f)
        selected -> colors.accentPrimary.copy(alpha = 0.16f)
        else -> colors.surfaceBlack.copy(alpha = 0.2f)
    }
    val borderColor = when {
        destructive -> colors.urgencyOverdue.copy(alpha = 0.4f)
        selected -> colors.accentPrimary.copy(alpha = 0.6f)
        else -> colors.borderColor.copy(alpha = 0.2f)
    }
    val textColor = when {
        destructive -> colors.urgencyOverdue
        selected -> colors.accentPrimary
        else -> colors.textSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = textColor,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(actionShape)
            .background(backgroundColor)
            .border(1.dp, borderColor, actionShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun BottomActionButton(
    text: String,
    prominent: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val shape = CircleShape
    val backgroundColor = if (prominent) {
        if (enabled) colors.accentPrimary else colors.accentPrimary.copy(alpha = 0.3f)
    } else {
        colors.surfaceCard
    }
    val textColor = if (prominent) colors.surfaceBlack else colors.textPrimary

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, if (prominent) Color.Transparent else colors.borderColor.copy(alpha = 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun wallOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = LocalWallColors.current.textPrimary,
    unfocusedTextColor = LocalWallColors.current.textPrimary,
    focusedContainerColor = LocalWallColors.current.surfaceCard,
    unfocusedContainerColor = LocalWallColors.current.surfaceCard,
    cursorColor = LocalWallColors.current.accentPrimary,
    focusedBorderColor = LocalWallColors.current.accentPrimary,
    unfocusedBorderColor = LocalWallColors.current.borderColor,
    focusedLabelColor = LocalWallColors.current.accentPrimary,
    unfocusedLabelColor = LocalWallColors.current.textSecondary,
    focusedPlaceholderColor = LocalWallColors.current.textMuted,
    unfocusedPlaceholderColor = LocalWallColors.current.textMuted
)
