package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations

@Composable
fun WaveformVisualizer(
    amplitudeLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "voice-pulse")

    // Breathing animation for inner dot
    val breathe by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Three pulse rings, staggered 600ms apart
    val ring1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOut)
        ),
        label = "ring1"
    )
    val ring2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOut),
            initialStartOffset = StartOffset(600)
        ),
        label = "ring2"
    )
    val ring3 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOut),
            initialStartOffset = StartOffset(1200)
        ),
        label = "ring3"
    )

    val activeAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(WallAnimations.SHORT),
        label = "active"
    )

    // Smooth the raw amplitude so the visual feels organic rather than jumpy.
    val smoothedAmplitude by animateFloatAsState(
        targetValue = amplitudeLevel.coerceIn(0f, 1f),
        animationSpec = tween(120, easing = EaseOut),
        label = "amplitude"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(200.dp)
            .graphicsLayer { alpha = activeAlpha }
    ) {
        // Pulse rings and center dot
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = 24.dp.toPx()
            val amp = smoothedAmplitude

            // Draw 3 expanding pulse rings — voice amplitude boosts ring opacity.
            val ringAmpBoost = 1f + amp * 2f
            for (scale in floatArrayOf(ring1, ring2, ring3)) {
                val normalizedProgress = ((scale - 0.8f) / 1.7f).coerceIn(0f, 1f)
                val ringAlpha = (1f - normalizedProgress) * 0.06f * ringAmpBoost

                drawCircle(
                    color = colors.accentPrimary.copy(alpha = ringAlpha.coerceAtMost(0.35f)),
                    radius = baseRadius * scale,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Center dot — radial gradient background (48dp diameter = 24dp radius)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.accentPrimary.copy(alpha = 0.25f + amp * 0.35f),
                        colors.accentPrimary.copy(alpha = 0.08f + amp * 0.15f)
                    ),
                    center = center,
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = center
            )

            // Center dot border — brightens with voice.
            drawCircle(
                color = colors.accentPrimary.copy(alpha = 0.2f + amp * 0.4f),
                radius = 24.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Inner breathing dot (16dp baseline) — amplitude expands radius and alpha.
            val ampScale = 1f + amp * 1.4f
            drawCircle(
                color = colors.accentPrimary.copy(
                    alpha = (0.4f * breathe / 1.2f + amp * 0.5f).coerceAtMost(0.95f)
                ),
                radius = 8.dp.toPx() * breathe * ampScale,
                center = center
            )
        }

        // "Listening" label below the dot
        Text(
            text = "Listening",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp
            ),
            color = colors.textMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun WaveformVisualizerPreview() {
    LedgerTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .padding(16.dp)
        ) {
            WaveformVisualizer(
                amplitudeLevel = 0.7f,
                isActive = true
            )
            WaveformVisualizer(
                amplitudeLevel = 0.7f,
                isActive = false
            )
        }
    }
}
