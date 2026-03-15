package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes

@Composable
fun UndoToast(
    visible: Boolean,
    taskTitle: String? = null,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = taskTitle
        ?.takeIf { it.isNotBlank() }
        ?.let { "Completed \"$it\"" }
        ?: "Task completed"

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = WallAnimations.MEDIUM)),
            exit = fadeOut(animationSpec = tween(durationMillis = WallAnimations.MEDIUM))
        ) {
            val cardShape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)

            Row(
                modifier = Modifier
                    .clip(cardShape)
                    .background(LocalWallColors.current.surfaceElevated)
                    .border(1.dp, LocalWallColors.current.borderColor, cardShape)
                    .semantics { contentDescription = "$message. Undo available." }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalWallColors.current.textSecondary
                )

                Spacer(modifier = Modifier.width(24.dp))

                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalWallColors.current.accentPrimary,
                    modifier = Modifier.clickable(onClick = onUndo)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Dismiss",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalWallColors.current.textSecondary,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, widthDp = 400, heightDp = 100)
@Composable
private fun UndoToastVisiblePreview() {
    LedgerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            UndoToast(
                visible = true,
                onUndo = {},
                onDismiss = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A, widthDp = 400, heightDp = 100)
@Composable
private fun UndoToastHiddenPreview() {
    LedgerTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            UndoToast(
                visible = false,
                onUndo = {},
                onDismiss = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
            )
        }
    }
}


