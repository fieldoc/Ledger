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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.todowallapp.capture.DayOrganizerState
import com.example.todowallapp.data.model.DayPlan
import com.example.todowallapp.data.model.Flexibility
import com.example.todowallapp.data.model.PlanBlock
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.utils.AppHapticPattern
import com.example.todowallapp.ui.utils.performAppHaptic
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
    onRetryFailed: (List<PlanBlock>) -> Unit = {},
    onSetPendingRemove: (Int?) -> Unit = {},
    onConfirmRemoveBlock: (Int) -> Unit = {},
    taskNameById: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val view = LocalView.current
    val context = LocalContext.current

    // Track previous state to detect Confirming->Idle (acceptance complete)
    var previousState by remember { mutableStateOf<DayOrganizerState>(DayOrganizerState.Idle) }

    LaunchedEffect(state) {
        val prev = previousState
        when {
            state is DayOrganizerState.Processing && prev is DayOrganizerState.Idle ->
                performAppHaptic(view, context, AppHapticPattern.NAVIGATE)
            state is DayOrganizerState.PlanReady && prev !is DayOrganizerState.PlanReady ->
                performAppHaptic(view, context, AppHapticPattern.CONFIRM)
            state is DayOrganizerState.Error ->
                performAppHaptic(view, context, AppHapticPattern.ERROR)
            state is DayOrganizerState.Idle && prev is DayOrganizerState.Confirming ->
                performAppHaptic(view, context, AppHapticPattern.CONFIRM)
        }
        previousState = state
    }

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
                is DayOrganizerState.Processing -> {
                    ProcessingContent(isAdjustment = state.isAdjustment)
                }
                is DayOrganizerState.PlanReady -> {
                    PlanPreviewContent(
                        plan = state.plan,
                        focusedIndex = state.focusedIndex,
                        pendingRemoveIndex = state.pendingRemoveIndex,
                        taskNameById = taskNameById,
                        onAccept = onAccept,
                        onAdjust = onAdjust,
                        onCancel = onCancel,
                        onSetPendingRemove = onSetPendingRemove,
                        onConfirmRemoveBlock = onConfirmRemoveBlock
                    )
                }
                is DayOrganizerState.Confirming -> {
                    val label = if (state.current > 0) {
                        "Creating event ${state.current} of ${state.total}..."
                    } else {
                        "Creating events..."
                    }
                    ProcessingContent(label = label)
                }
                is DayOrganizerState.PartialSuccess -> {
                    PartialSuccessContent(
                        createdCount = state.createdCount,
                        failedBlocks = state.failedBlocks,
                        onRetryFailed = { onRetryFailed(state.failedBlocks) },
                        onDismiss = onCancel
                    )
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

        // Contextual hint -- fades out after 5s
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
    focusedIndex: Int,
    pendingRemoveIndex: Int?,
    taskNameById: Map<String, String>,
    onAccept: () -> Unit,
    onAdjust: () -> Unit,
    onCancel: () -> Unit,
    onSetPendingRemove: (Int?) -> Unit,
    onConfirmRemoveBlock: (Int) -> Unit
) {
    val colors = LocalWallColors.current
    val blockCount = plan.blocks.size

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
            itemsIndexed(plan.blocks) { index, block ->
                val isFocused = index == focusedIndex
                val isPendingRemove = pendingRemoveIndex == index
                PlanBlockRow(
                    block = block,
                    isFocused = isFocused,
                    isPendingRemove = isPendingRemove,
                    taskNameById = taskNameById,
                    onClick = {
                        if (isFocused) {
                            if (isPendingRemove) {
                                onConfirmRemoveBlock(index)
                            } else {
                                onSetPendingRemove(index)
                            }
                        }
                    }
                )
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
                val actionIndex = blockCount + index
                val isFocused = actionIndex == focusedIndex
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
private fun PartialSuccessContent(
    createdCount: Int,
    failedBlocks: List<PlanBlock>,
    onRetryFailed: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWallColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.dividerColor, RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Partially Created",
            color = colors.accentWarm,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$createdCount event${if (createdCount != 1) "s" else ""} created, ${failedBlocks.size} failed.",
            color = colors.textPrimary,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Failed: ${failedBlocks.joinToString(", ") { it.title }}",
            color = colors.textMuted,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentPrimary)
                    .clickable(onClick = onRetryFailed)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Retry failed",
                    color = colors.surfaceBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, colors.dividerColor, RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dismiss",
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PlanBlockRow(
    block: PlanBlock,
    isFocused: Boolean,
    isPendingRemove: Boolean,
    taskNameById: Map<String, String>,
    onClick: () -> Unit
) {
    val colors = LocalWallColors.current
    val isExisting = block.isExistingEvent
    val isLowConfidence = block.confidence < 0.6f
    val isRigid = block.flexibility == Flexibility.RIGID

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
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
        val borderColor = when {
            isPendingRemove -> colors.accentWarm
            isFocused && isLowConfidence -> colors.accentWarm.copy(alpha = 0.35f)
            isFocused -> colors.accentPrimary
            isExisting -> colors.dividerColor
            else -> colors.planAccent.copy(alpha = 0.4f)
        }
        val bgColor = when {
            isPendingRemove -> colors.accentWarm.copy(alpha = 0.1f)
            isFocused -> colors.surfaceCard
            isExisting -> colors.surfaceCard.copy(alpha = 0.5f)
            else -> colors.surfaceCard
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .border(
                        width = if (isFocused || isPendingRemove) 1.5.dp else if (!isExisting) 1.dp else 0.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .animateContentSize(animationSpec = tween(250))
            ) {
                Row(Modifier.fillMaxWidth()) {
                    // Left accent bar
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(
                                when {
                                    isLowConfidence -> colors.accentWarm.copy(alpha = 0.35f)
                                    isExisting -> colors.accentPrimary.copy(alpha = 0.4f)
                                    else -> colors.planAccent
                                }
                            )
                    )
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (isFocused) {
                            // Expanded: title
                            Text(
                                text = block.title,
                                color = colors.textPrimary.copy(alpha = if (isExisting) 0.6f else 1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            // Expanded: time range + category + rigid marker
                            val timeRange = "${block.startTime.format(timeFmt)} - ${block.endTime.format(timeFmt)}"
                            val categoryLabel = block.category.name.lowercase()
                                .replaceFirstChar { it.uppercase() }
                            val rigidSuffix = if (isRigid) " \u00B7 Fixed" else ""
                            val lineLabel = if (isExisting) {
                                "$timeRange  \u00B7  Existing$rigidSuffix"
                            } else {
                                "$timeRange  \u00B7  $categoryLabel$rigidSuffix"
                            }
                            Text(
                                text = lineLabel,
                                color = colors.textMuted.copy(alpha = if (isExisting) 0.4f else 0.6f),
                                fontSize = 10.sp
                            )
                            // Expanded: notes
                            block.notes?.let { notes ->
                                Text(
                                    text = notes,
                                    color = colors.textMuted.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                            // Expanded: source task name
                            block.sourceTaskId?.let { taskId ->
                                val taskName = taskNameById[taskId]
                                if (taskName != null) {
                                    Text(
                                        text = "from: $taskName",
                                        color = colors.textMuted.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                            // Expanded: low-confidence indicator
                            if (isLowConfidence) {
                                Text(
                                    text = "Low confidence (${(block.confidence * 100).toInt()}%)",
                                    color = colors.accentWarm.copy(alpha = 0.6f),
                                    fontSize = 9.sp
                                )
                            }
                        } else {
                            // Compact (unfocused): title + time only, single line
                            Text(
                                text = "${block.title}  ${block.startTime.format(timeFmt)}-${block.endTime.format(timeFmt)}",
                                color = colors.textPrimary.copy(alpha = if (isExisting) 0.4f else 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Pending remove overlay
            if (isPendingRemove) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceBlack.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Remove? Click to confirm",
                        color = colors.accentWarm,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
