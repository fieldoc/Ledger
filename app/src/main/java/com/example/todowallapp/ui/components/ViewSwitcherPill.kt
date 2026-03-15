package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations

@Immutable
data class ViewSwitcherOption(
    val key: String,
    val label: String,
    val badgeCount: Int? = null,
    val enabled: Boolean = true
)

@Composable
fun ViewSwitcherPill(
    options: List<ViewSwitcherOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAmbientMode: Boolean = false
) {
    if (options.isEmpty()) return
    val containerShape = RoundedCornerShape(999.dp)

    Row(
        modifier = modifier
            .clip(containerShape)
            .background(if (isAmbientMode) LocalWallColors.current.surfaceBlack.copy(alpha = 0.52f) else LocalWallColors.current.surfaceExpanded.copy(alpha = 0.88f))
            .border(1.dp, LocalWallColors.current.borderColor, containerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = option.key == selectedKey
            val backgroundColor by animateColorAsState(
                targetValue = when {
                    !option.enabled -> Color.Transparent
                    isSelected && isAmbientMode -> LocalWallColors.current.accentPrimary.copy(alpha = 0.16f)
                    isSelected -> LocalWallColors.current.accentPrimary.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                animationSpec = tween(WallAnimations.MEDIUM),
                label = "viewSwitcherBackground"
            )
            val textColor by animateColorAsState(
                targetValue = when {
                    !option.enabled -> LocalWallColors.current.textDisabled
                    isSelected && isAmbientMode -> LocalWallColors.current.textPrimary.copy(alpha = 0.92f)
                    isSelected -> LocalWallColors.current.textPrimary
                    else -> LocalWallColors.current.textPrimary.copy(alpha = 0.78f)
                },
                animationSpec = tween(WallAnimations.SHORT),
                label = "viewSwitcherTextColor"
            )
            val itemAlpha by animateFloatAsState(
                targetValue = if (option.enabled) 1f else 0.55f,
                animationSpec = tween(WallAnimations.SHORT),
                label = "viewSwitcherItemAlpha"
            )

            Box(
                modifier = Modifier
                    .clip(containerShape)
                    .background(backgroundColor)
                    .clickable(
                        enabled = option.enabled,
                        role = Role.Tab
                    ) {
                        if (!isSelected) onSelect(option.key)
                    }
                    .semantics {
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .alpha(itemAlpha),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )

                    option.badgeCount
                        ?.takeIf { it > 0 }
                        ?.let { count ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) LocalWallColors.current.surfaceCard else LocalWallColors.current.surfaceCard.copy(alpha = 0.72f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) LocalWallColors.current.accentPrimary else LocalWallColors.current.textPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun ViewSwitcherPillPreview() {
    LedgerTheme {
        ViewSwitcherPill(
            options = listOf(
                ViewSwitcherOption(key = "tasks", label = "Tasks", badgeCount = 8),
                ViewSwitcherOption(key = "calendar", label = "Calendar")
            ),
            selectedKey = "calendar",
            onSelect = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun ViewSwitcherPillAmbientPreview() {
    LedgerTheme {
        ViewSwitcherPill(
            options = listOf(
                ViewSwitcherOption(key = "month", label = "Month"),
                ViewSwitcherOption(key = "agenda", label = "Agenda", enabled = false)
            ),
            selectedKey = "month",
            onSelect = {},
            isAmbientMode = true
        )
    }
}


