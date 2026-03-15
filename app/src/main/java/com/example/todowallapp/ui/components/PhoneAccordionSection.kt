package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import kotlinx.coroutines.delay

@Composable
fun PhoneAccordionSection(
    title: String,
    taskCount: Int,
    isExpanded: Boolean,
    peekText: String?,
    sectionIndex: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    var sectionVisible by rememberSaveable(title) { mutableStateOf(false) }
    val colors = LocalWallColors.current

    LaunchedEffect(sectionIndex) {
        delay(sectionIndex * 60L)
        sectionVisible = true
    }

    AnimatedVisibility(
        visible = sectionVisible,
        enter = fadeIn(tween(WallAnimations.MEDIUM)) + slideInVertically(
            initialOffsetY = { 20 },
            animationSpec = tween(WallAnimations.MEDIUM)
        )
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(if (isExpanded) colors.surfaceCard.copy(alpha = 0.3f) else colors.surfaceCard.copy(alpha = 0.15f))
        ) {
            PhoneAccordionHeader(
                title = title,
                taskCount = taskCount,
                isExpanded = isExpanded,
                peekText = peekText,
                onToggle = onToggle
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(tween(WallAnimations.SHORT)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(tween(WallAnimations.SHORT))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun PhoneAccordionHeader(
    title: String,
    taskCount: Int,
    isExpanded: Boolean,
    peekText: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "phoneChevronRotation"
    )
    val taskCountLabel = if (taskCount == 1) "1 task" else "$taskCount tasks"

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isExpanded) colors.accentPrimary else colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = taskCountLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentPrimary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentPrimary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
            )
        }

        AnimatedVisibility(
            visible = !isExpanded && !peekText.isNullOrBlank(),
            enter = fadeIn(tween(WallAnimations.SHORT)),
            exit = fadeOut(tween(WallAnimations.SHORT))
        ) {
            Text(
                text = peekText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .padding(bottom = 8.dp)
            )
        }

        if (!isExpanded) {
            HorizontalDivider(
                color = colors.borderColor.copy(alpha = 0.3f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}
