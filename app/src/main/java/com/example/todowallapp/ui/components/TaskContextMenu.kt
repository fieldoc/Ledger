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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val isDestructive: Boolean = false,
    /** If non-null, clicking opens a sub-menu with these items instead of firing directly. */
    val subActions: List<TaskContextMenuAction>? = null,
    /** Suffix indicator shown on the right (e.g., "●" for current priority). */
    val trailingIndicator: String? = null
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
    var subMenu by remember { mutableStateOf<TaskContextMenuAction?>(null) }
    var subMenuSelectedIndex by remember { mutableStateOf(0) }

    // Reset sub-menu state when menu becomes visible
    val currentActions = subMenu?.subActions ?: actions
    val currentTitle = subMenu?.label ?: title
    val currentSelectedIndex = if (subMenu != null) subMenuSelectedIndex else selectedActionIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = {
                if (subMenu != null) {
                    subMenu = null
                    subMenuSelectedIndex = 0
                } else {
                    onDismiss()
                }
            })
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
                text = currentTitle,
                style = MaterialTheme.typography.titleMedium,
                color = LocalWallColors.current.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = LocalWallColors.current.borderColor)

            currentActions.forEachIndexed { index, action ->
                val isSelected = index == currentSelectedIndex
                val rowBackground by animateColorAsState(
                    targetValue = if (isSelected) LocalWallColors.current.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(WallAnimations.SHORT),
                    label = "taskContextMenuRow"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBackground)
                        .clickable {
                            if (action.subActions != null) {
                                subMenu = action
                                subMenuSelectedIndex = 0
                            } else {
                                onActionSelected(action)
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (action.isDestructive) LocalWallColors.current.textSecondary else LocalWallColors.current.textPrimary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (action.trailingIndicator != null) {
                            Text(
                                text = action.trailingIndicator,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalWallColors.current.accentPrimary
                            )
                        }
                        if (action.subActions != null) {
                            Text(
                                text = "\u25B6",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalWallColors.current.textMuted
                            )
                        }
                    }
                }
            }

            // Back action when in sub-menu
            if (subMenu != null) {
                HorizontalDivider(color = LocalWallColors.current.borderColor)
                val backSelected = currentSelectedIndex == currentActions.size
                val backBackground by animateColorAsState(
                    targetValue = if (backSelected) LocalWallColors.current.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                    animationSpec = tween(WallAnimations.SHORT),
                    label = "backRow"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backBackground)
                        .clickable {
                            subMenu = null
                            subMenuSelectedIndex = 0
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "\u25C0  Back",
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalWallColors.current.textMuted
                    )
                }
            }
        }
    }
}
