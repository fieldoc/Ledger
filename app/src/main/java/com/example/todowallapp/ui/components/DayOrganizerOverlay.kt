package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.todowallapp.capture.DayOrganizerState
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.ui.theme.LocalWallColors
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DayOrganizerOverlay(
    state: DayOrganizerState,
    onStopListening: () -> Unit,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current

    AnimatedVisibility(
        visible = state !is DayOrganizerState.Idle,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is DayOrganizerState.Listening -> {
                    ListeningContent(
                        label = "PLANNING YOUR DAY",
                        hint = "tell me your tasks and how long each will take",
                        hintExample = "e.g.  groceries 30 min · dentist 1 hour · gym 45 min",
                        amplitudeLevel = state.amplitudeLevel,
                        onStop = onStopListening
                    )
                }
                is DayOrganizerState.Adjusting -> {
                    ListeningContent(
                        label = "ADJUSTING PLAN",
                        amplitudeLevel = state.amplitudeLevel,
                        onStop = onStopListening
                    )
                }
                is DayOrganizerState.Processing -> {
                    ProcessingContent(isAdjustment = state.isAdjustment)
                }
                is DayOrganizerState.PlanReady -> {
                    PlanPreviewContent(
                        plan = state.plan,
                        focusedAction = state.focusedAction,
                        onAccept = onAccept,
                        onAdjust = onAdjust,
                        onCancel = onCancel
                    )
                }
                is DayOrganizerState.Confirming -> {
                    ProcessingContent(label = "Creating events...")
                }
                is DayOrganizerState.Error -> {
                    ErrorContent(
                        message = state.message,
                        canRetry = state.canRetry,
                        onRetry = onRetry,
                        onDismiss = onCancel
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ListeningContent(
    label: String,
    hint: String? = null,
    hintExample: String? = null,
    amplitudeLevel: Float,
    onStop: () -> Unit
) {
    val colors = LocalWallColors.current

    // Hint auto-fades after 5 seconds so experienced users aren't distracted
    var showHint by remember(label) { mutableStateOf(hint != null) }
    if (hint != null) {
        LaunchedEffect(label) {
            delay(5_000)
            showHint = false
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onStop)
    ) {
        Text(
            text = label,
            color = colors.textMuted,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))
        WaveformVisualizer(
            amplitudeLevel = amplitudeLevel,
            isActive = true,
            modifier = Modifier.size(200.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Contextual hint — fades out after 5s
        AnimatedVisibility(
            visible = showHint,
            exit = fadeOut(tween(800))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = hint ?: "",
                    color = colors.textMuted.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                hintExample?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = colors.textMuted.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        Text(
            text = "Click to finish speaking",
            color = colors.textMuted.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ProcessingContent(
    isAdjustment: Boolean = false,
    label: String? = null
) {
    val colors = LocalWallColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = colors.accentPrimary,
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = label ?: if (isAdjustment) "Adjusting plan..." else "Planning your day...",
            color = colors.textMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PlanPreviewContent(
    plan: DayPlan,
    focusedAction: Int,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalWallColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .fillMaxHeight(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceBlack)
            .border(1.dp, colors.dividerColor, RoundedCornerShape(16.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (plan.targetDate == java.time.LocalDate.now()) "Today's Plan"
                       else plan.targetDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE's Plan")),
                color = colors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = plan.summary,
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }

        // Low-confidence warning
        plan.warning?.let { warning ->
            Text(
                text = warning,
                color = colors.accentWarm.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Timeline
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(plan.blocks) { block ->
                PlanBlockRow(block = block)
            }
        }

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val actions = listOf("Accept" to onAccept, "Adjust" to onAdjust, "Cancel" to onCancel)
            actions.forEachIndexed { index, (label, action) ->
                val isFocused = index == focusedAction
                val isAccept = index == 0
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isAccept && isFocused) colors.accentPrimary
                            else if (isFocused) colors.surfaceCard
                            else colors.surfaceBlack
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            color = if (isFocused) colors.accentPrimary else colors.dividerColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = action)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isAccept && isFocused) colors.surfaceBlack else colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanBlockRow(block: PlanBlock) {
    val colors = LocalWallColors.current
    val isExisting = block.isExistingEvent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time label
        Text(
            text = block.startTime.format(timeFmt),
            color = colors.textMuted.copy(alpha = if (isExisting) 0.4f else 0.6f),
            fontSize = 11.sp,
            modifier = Modifier.width(44.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Block card
        val borderColor = if (isExisting) colors.dividerColor else colors.planAccent.copy(alpha = 0.4f)
        val bgColor = if (isExisting) colors.surfaceCard.copy(alpha = 0.5f) else colors.surfaceCard

        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .then(
                    if (!isExisting) Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                    else Modifier
                )
                .padding(start = 0.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                // Left accent bar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(
                            if (isExisting) colors.accentPrimary.copy(alpha = 0.4f)
                            else colors.planAccent
                        )
                )
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        text = block.title,
                        color = colors.textPrimary.copy(alpha = if (isExisting) 0.6f else 1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val timeRange = "${block.startTime.format(timeFmt)} - ${block.endTime.format(timeFmt)}"
                    val categoryLabel = block.category.name.lowercase()
                        .replaceFirstChar { it.uppercase() }
                    val lineLabel = if (isExisting) "$timeRange  \u00B7  Existing" else "$timeRange  \u00B7  $categoryLabel"
                    Text(
                        text = lineLabel,
                        color = colors.textMuted.copy(alpha = if (isExisting) 0.4f else 0.6f),
                        fontSize = 10.sp
                    )
                    block.notes?.let { notes ->
                        Text(
                            text = notes,
                            color = colors.textMuted.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWallColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.dividerColor, RoundedCornerShape(16.dp))
            .clickable(onClick = if (canRetry) onRetry else onDismiss)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Planning Error",
            color = colors.accentWarm,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = colors.textMuted,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (canRetry) "Click to try again" else "Click to dismiss",
            color = colors.textMuted.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
