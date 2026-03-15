package com.example.todowallapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.voice.VoiceInputState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PhoneVoiceBottomSheet(
    visible: Boolean,
    voiceState: VoiceInputState,
    onDismiss: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onCancelListening: () -> Unit,
    onConfirm: (String?) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colors = LocalWallColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = colors.surfaceBlack,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.85f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderColor.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(20.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (voiceState) {
                    is VoiceInputState.Listening -> "Listening..."
                    VoiceInputState.Processing -> "Thinking..."
                    is VoiceInputState.Preview -> "Is this right?"
                    is VoiceInputState.Error -> "Something went wrong"
                    else -> "Voice Capture"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (voiceState is VoiceInputState.Listening) colors.accentPrimary else colors.textPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                when (voiceState) {
                    VoiceInputState.Idle -> {
                        Text(
                            text = "Speak a task title naturally.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    is VoiceInputState.Listening -> {
                        ListeningPulse()
                    }

                    VoiceInputState.Processing -> {
                        CircularProgressIndicator(color = colors.accentPrimary)
                    }

                    is VoiceInputState.Preview -> {
                        Surface(
                            color = if (voiceState.clarification != null) 
                                colors.urgencyDueSoon.copy(alpha = 0.1f) 
                            else colors.surfaceCard,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                width = 1.dp, 
                                color = if (voiceState.clarification != null) 
                                    colors.urgencyDueSoon.copy(alpha = 0.4f) 
                                else colors.accentPrimary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (voiceState.clarification != null) {
                                    Text(
                                        text = voiceState.clarification,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.accentPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Text(
                                        text = "\"${voiceState.transcribedText}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = voiceState.transcribedText,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    is VoiceInputState.Error -> {
                        Text(
                            text = voiceState.message,
                            color = colors.urgencyOverdue,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            // High-end Action Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (voiceState) {
                    VoiceInputState.Idle -> {
                        ActionPill(
                            label = "Start Listening",
                            onClick = onStartListening,
                            primary = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    is VoiceInputState.Listening -> {
                        ActionPill(
                            label = "Stop",
                            onClick = onStopListening,
                            primary = true,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            label = "Cancel",
                            onClick = onCancelListening,
                            modifier = Modifier.weight(0.5f)
                        )
                    }

                    VoiceInputState.Processing -> {
                        ActionPill(
                            label = "Cancel",
                            onClick = onCancelListening,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    is VoiceInputState.Preview -> {
                        ActionPill(
                            label = "Add Task",
                            onClick = { onConfirm(null) },
                            primary = true,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            label = "Retry",
                            onClick = onStartListening,
                            modifier = Modifier.weight(0.6f)
                        )
                    }

                    is VoiceInputState.Error -> {
                        ActionPill(
                            label = "Retry",
                            onClick = onStartListening,
                            primary = true,
                            modifier = Modifier.weight(1f)
                        )
                        ActionPill(
                            label = "Dismiss",
                            onClick = onDismissError,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = if (primary) colors.accentPrimary else colors.surfaceCard,
        shape = CircleShape,
        border = if (primary) null else BorderStroke(1.dp, colors.borderColor.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (primary) colors.surfaceBlack else colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ListeningPulse() {
    val colors = LocalWallColors.current
    val transition = rememberInfiniteTransition(label = "pulse")
    
    val outerScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerPulseScale"
    )
    val outerAlpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerPulseAlpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Echo ring
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(outerScale)
                .clip(CircleShape)
                .background(colors.accentPrimary.copy(alpha = outerAlpha))
        )
        // Core
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(colors.accentPrimary, colors.accentPrimary.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Mic,
                contentDescription = null,
                tint = colors.surfaceBlack,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
