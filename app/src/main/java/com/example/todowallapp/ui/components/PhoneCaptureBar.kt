package com.example.todowallapp.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations

@Composable
fun PhoneCaptureHub(
    onCameraClick: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .height(68.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(34.dp),
                spotColor = colors.accentPrimary.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    listOf(colors.surfaceElevated, colors.surfaceCard)
                )
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle inner rim light
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(1.dp)
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(34.dp))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mic-first: larger teal accent button
            CaptureActionPill(
                icon = Icons.Outlined.Mic,
                label = "Voice",
                onClick = onVoiceClick,
                containerColor = colors.accentPrimary.copy(alpha = 0.1f),
                contentColor = colors.accentPrimary,
                isPrimary = true,
                pillSize = 40.dp,
                borderColor = colors.borderFocused
            )

            // Camera: smaller, neutral secondary
            CaptureActionPill(
                icon = Icons.Outlined.CameraAlt,
                label = "Scan",
                onClick = onCameraClick,
                containerColor = Color.White.copy(alpha = 0.03f),
                contentColor = colors.textMuted,
                pillSize = 36.dp,
                borderColor = colors.borderColor
            )
        }
    }
}

@Composable
private fun CaptureActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    isPrimary: Boolean = false,
    pillSize: androidx.compose.ui.unit.Dp = 36.dp,
    borderColor: Color = Color.Transparent
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(WallAnimations.SHORT),
        label = "pillScale"
    )

    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, CircleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        color = containerColor,
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = if (isPrimary) 20.dp else 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(if (isPrimary) pillSize else (pillSize - 4.dp))
            )
            if (isPrimary) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
