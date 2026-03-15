package com.example.todowallapp.ui.components

import com.example.todowallapp.ui.theme.LocalWallColors

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LedgerTheme
import com.example.todowallapp.ui.theme.WallAnimations
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ClockHeader(
    isAmbientMode: Boolean = false,
    isOnline: Boolean = true,
    lastSyncTime: LocalDateTime? = null,
    lastSyncSuccess: Boolean? = null,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }
    val colors = LocalWallColors.current

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000)
        }
    }

    var syncTextTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lastSyncTime) {
        if (lastSyncTime == null) {
            syncTextTick = 0L
            return@LaunchedEffect
        }
        while (true) {
            delay(30_000)
            syncTextTick++
        }
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()) }

    val minutesSinceSync = lastSyncTime?.let {
        @Suppress("UNUSED_EXPRESSION")
        syncTextTick
        ChronoUnit.MINUTES.between(it, LocalDateTime.now())
    }

    val syncText = minutesSinceSync?.let { mins ->
        when {
            mins < 1 -> "Synced just now"
            mins < 30 -> "Synced ${mins}m ago"
            mins < 60 -> "Stale: last sync ${mins}m ago"
            else -> {
                val hours = mins / 60
                "Stale: last sync ${hours}h ago"
            }
        }
    }

    val syncTextColor by animateColorAsState(
        targetValue = when {
            minutesSinceSync == null -> colors.textMuted
            minutesSinceSync < 10 -> colors.textMuted
            minutesSinceSync < 30 -> colors.urgencyDueSoon
            minutesSinceSync < 60 -> colors.urgencyDueToday
            else -> colors.urgencyOverdue
        },
        animationSpec = tween(durationMillis = WallAnimations.MEDIUM),
        label = "syncStalenessColor"
    )

    Row(
        modifier = modifier
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Time & Date Tile
        HeaderBentoTile(isAmbientMode = isAmbientMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentTime.format(timeFormatter),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isAmbientMode) colors.ambientText else colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = currentTime.format(dateFormatter),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isAmbientMode) colors.ambientText.copy(alpha = 0.7f) else colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!isAmbientMode && syncText != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) colors.connectivityOnline else colors.urgencyOverdue)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = syncText,
                                style = MaterialTheme.typography.labelSmall,
                                color = syncTextColor
                            )
                        }
                    }
                }
            }
        }

        if (!isAmbientMode && !isOnline) {
            HeaderBentoTile(
                isAmbientMode = false,
                containerColor = colors.urgencyOverdue.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.urgencyOverdue,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun HeaderBentoTile(
    isAmbientMode: Boolean,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(20.dp)
    
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isAmbientMode) Modifier else Modifier
                    .background(containerColor ?: colors.surfaceCard.copy(alpha = 0.4f))
                    .border(1.dp, Color(0x1AFFFFFF), shape)
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0A)
@Composable
private fun ClockHeaderPreview() {
    LedgerTheme {
        Column {
            ClockHeader(
                isAmbientMode = false,
                isOnline = true,
                lastSyncTime = LocalDateTime.now().minusMinutes(3),
                lastSyncSuccess = true
            )
            Spacer(modifier = Modifier.height(32.dp))
            ClockHeader(
                isAmbientMode = true,
                isOnline = false,
                lastSyncTime = LocalDateTime.now().minusHours(2),
                lastSyncSuccess = false
            )
            Spacer(modifier = Modifier.height(32.dp))
            ClockHeader(
                isAmbientMode = false,
                isOnline = true,
                lastSyncTime = null,
                lastSyncSuccess = null
            )
        }
    }
}
