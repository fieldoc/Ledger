package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class PromotionSheetState(
    val taskTitle: String,
    val durationMinutes: Int,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val calendarTitle: String,
    val focusedRow: Int,
    val isAdjusting: Boolean = false
)

@Composable
fun PromotionSheet(
    visible: Boolean,
    state: PromotionSheetState,
    onFocusRow: (Int) -> Unit,
    onAdjustDuration: () -> Unit,
    onAdjustStartTime: () -> Unit,
    onAdjustCalendar: () -> Unit,
    onToggleAdjusting: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)

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
                .heightIn(min = 280.dp)
                .padding(bottom = 36.dp)
                .background(LocalWallColors.current.surfaceCard, shape)
                .border(1.dp, LocalWallColors.current.borderColor, shape)
                .clickable(enabled = false, onClick = {})
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.taskTitle,
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

            PromotionFieldRow(
                label = "Duration",
                value = "${state.durationMinutes} min",
                isFocused = state.focusedRow == 0,
                isAdjusting = state.focusedRow == 0 && state.isAdjusting,
                onClick = {
                    onFocusRow(0)
                    if (state.isAdjusting) return@PromotionFieldRow
                    onAdjustDuration()
                }
            )
            PromotionFieldRow(
                label = "Start time",
                value = "${state.startTime.format(TimeFormatter)}  ends ${state.endTime.format(TimeFormatter)}",
                isFocused = state.focusedRow == 1,
                isAdjusting = state.focusedRow == 1 && state.isAdjusting,
                onClick = {
                    onFocusRow(1)
                    if (state.isAdjusting) return@PromotionFieldRow
                    onAdjustStartTime()
                }
            )
            PromotionFieldRow(
                label = "Calendar",
                value = state.calendarTitle,
                isFocused = state.focusedRow == 2,
                isAdjusting = state.focusedRow == 2 && state.isAdjusting,
                onClick = {
                    onFocusRow(2)
                    if (state.isAdjusting) return@PromotionFieldRow
                    onAdjustCalendar()
                }
            )

            val confirmFocused = state.focusedRow == 3
            val confirmBg by animateColorAsState(
                targetValue = if (confirmFocused) LocalWallColors.current.accentPrimary.copy(alpha = 0.2f) else LocalWallColors.current.surfaceElevated,
                animationSpec = tween(WallAnimations.SHORT),
                label = "promotionConfirm"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(confirmBg, RoundedCornerShape(WallShapes.MediumCornerRadius.dp))
                    .border(
                        width = if (confirmFocused) 1.5.dp else 1.dp,
                        color = if (confirmFocused) LocalWallColors.current.accentPrimary else LocalWallColors.current.borderColor,
                        shape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp)
                    )
                    .clickable {
                        onFocusRow(3)
                        onConfirm()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Confirm",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalWallColors.current.textPrimary,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = if (state.isAdjusting) "Twist to change value \u2022 Click to confirm"
                       else "Twist to move \u2022 Click to adjust",
                style = MaterialTheme.typography.labelSmall,
                color = LocalWallColors.current.textMuted
            )
        }
    }
}

@Composable
private fun PromotionFieldRow(
    label: String,
    value: String,
    isFocused: Boolean,
    isAdjusting: Boolean = false,
    onClick: () -> Unit
) {
    val bgAlpha = when {
        isAdjusting -> 0.28f
        isFocused -> 0.18f
        else -> 0f
    }
    val bg by animateColorAsState(
        targetValue = if (isFocused || isAdjusting) LocalWallColors.current.accentPrimary.copy(alpha = bgAlpha) else LocalWallColors.current.surfaceElevated,
        animationSpec = tween(WallAnimations.SHORT),
        label = "promotionFieldBg"
    )

    val borderPulseAlpha = if (isAdjusting) {
        val infiniteTransition = rememberInfiniteTransition(label = "adjustPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "adjustBorderAlpha"
        )
        alpha
    } else {
        1f
    }

    val borderColor = when {
        isAdjusting -> LocalWallColors.current.accentPrimary.copy(alpha = borderPulseAlpha)
        isFocused -> LocalWallColors.current.accentPrimary
        else -> LocalWallColors.current.borderColor
    }
    val borderWidth = if (isFocused || isAdjusting) 1.5.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(WallShapes.MediumCornerRadius.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(WallShapes.MediumCornerRadius.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalWallColors.current.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalWallColors.current.textPrimary
        )
    }
}



