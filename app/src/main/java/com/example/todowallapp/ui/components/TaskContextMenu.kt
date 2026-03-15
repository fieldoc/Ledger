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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes

data class TaskContextMenuAction(
    val id: String,
    val label: String,
    val isDestructive: Boolean = false
)

@Composable
fun TaskContextMenu(
    visible: Boolean,
    title: String,
    actions: List<TaskContextMenuAction>,
    selectedActionIndex: Int,
    onActionSelected: (TaskContextMenuAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .widthIn(min = 280.dp, max = 420.dp)
                .background(LocalWallColors.current.surfaceElevated, shape)
                .border(1.dp, LocalWallColors.current.borderColor, shape)
                .padding(vertical = 8.dp)
                .semantics { dialog() }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = LocalWallColors.current.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = LocalWallColors.current.borderColor)

            actions.forEachIndexed { index, action ->
                val isSelected = index == selectedActionIndex
                val rowBackground by animateColorAsState(
                    targetValue = if (isSelected) LocalWallColors.current.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(WallAnimations.SHORT),
                    label = "taskContextMenuRow"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .clickable { onActionSelected(action) }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (action.isDestructive) LocalWallColors.current.textSecondary else LocalWallColors.current.textPrimary
                    )
                }
            }
        }
    }
}


