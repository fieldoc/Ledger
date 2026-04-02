package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.TaskFilter
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes

private val filterLabels = mapOf(
    TaskFilter.OVERDUE to "Overdue",
    TaskFilter.DUE_TODAY to "Due Today",
    TaskFilter.DUE_THIS_WEEK to "Due This Week",
    TaskFilter.HIGH_PRIORITY to "High Priority",
    TaskFilter.RECURRING to "Recurring"
)

@Composable
fun SearchFilterOverlay(
    visible: Boolean,
    activeFilters: Set<TaskFilter>,
    selectedIndex: Int,
    hasActiveSearch: Boolean,
    onVoiceSearch: () -> Unit,
    onToggleFilter: (TaskFilter) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)

    // Items: 0 = Voice Search, 1..5 = filters, 6 = Clear All
    val allItems = buildList {
        add("voice_search")
        TaskFilter.entries.forEach { add(it.name) }
        if (activeFilters.isNotEmpty() || hasActiveSearch) add("clear_all")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = modifier
                .align(Alignment.Center)
                .widthIn(min = 280.dp, max = 420.dp)
                .background(colors.surfaceElevated, shape)
                .border(1.dp, colors.borderColor, shape)
                .padding(vertical = 8.dp)
                .semantics { dialog() }
        ) {
            Text(
                text = "Find Tasks",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = colors.borderColor)

            // Voice Search item
            SearchFilterItem(
                label = "\uD83C\uDFA4  Voice Search",
                isSelected = selectedIndex == 0,
                isActive = hasActiveSearch,
                onClick = onVoiceSearch
            )

            HorizontalDivider(
                color = colors.borderColor,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = "Quick Filters",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // Filter items
            TaskFilter.entries.forEachIndexed { filterIndex, filter ->
                val itemIndex = filterIndex + 1
                val isActive = filter in activeFilters
                SearchFilterItem(
                    label = filterLabels[filter] ?: filter.name,
                    isSelected = selectedIndex == itemIndex,
                    isActive = isActive,
                    onClick = { onToggleFilter(filter) }
                )
            }

            // Clear All (only shown when filters are active)
            if (activeFilters.isNotEmpty() || hasActiveSearch) {
                HorizontalDivider(
                    color = colors.borderColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                SearchFilterItem(
                    label = "Clear All Filters",
                    isSelected = selectedIndex == allItems.size - 1,
                    isActive = false,
                    onClick = onClearAll
                )
            }
        }
    }
}

@Composable
private fun SearchFilterItem(
    label: String,
    isSelected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWallColors.current
    val rowBackground by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentPrimary.copy(alpha = 0.16f)
            isActive -> colors.accentPrimary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        animationSpec = tween(WallAnimations.SHORT),
        label = "searchFilterRow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isActive) colors.accentPrimary else colors.textPrimary
        )
        if (isActive) {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.accentPrimary
            )
        }
    }
}
