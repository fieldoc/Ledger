package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes

@Composable
fun EventActionMenu(
    event: CalendarEvent,
    focusedAction: Int,
    onFocusAction: (Int) -> Unit,
    onCompleteTask: () -> Unit,
    onReschedule: () -> Unit,
    onRemoveEvent: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp)

    Column(
        modifier = modifier
            .fillMaxWidth(0.6f)
            .background(LocalWallColors.current.surfaceCard, shape)
            .border(1.dp, LocalWallColors.current.accentPrimary.copy(alpha = 0.4f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelMedium,
            color = LocalWallColors.current.textSecondary,
            maxLines = 1,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        EventActionRow(
            label = "Complete task",
            isFocused = focusedAction == 0,
            onClick = {
                onFocusAction(0)
                onCompleteTask()
            }
        )
        EventActionRow(
            label = "Reschedule",
            isFocused = focusedAction == 1,
            onClick = {
                onFocusAction(1)
                onReschedule()
            }
        )
        EventActionRow(
            label = "Remove from calendar",
            isFocused = focusedAction == 2,
            onClick = {
                onFocusAction(2)
                onRemoveEvent()
            }
        )
    }
}

const val EVENT_ACTION_COUNT = 3

@Composable
private fun EventActionRow(
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isFocused) LocalWallColors.current.accentPrimary.copy(alpha = 0.18f) else LocalWallColors.current.surfaceElevated,
        animationSpec = tween(WallAnimations.SHORT),
        label = "actionRowBg"
    )
    val borderColor = if (isFocused) LocalWallColors.current.accentPrimary else LocalWallColors.current.borderColor
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isFocused) LocalWallColors.current.textPrimary else LocalWallColors.current.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(WallShapes.SmallCornerRadius.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(WallShapes.SmallCornerRadius.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
