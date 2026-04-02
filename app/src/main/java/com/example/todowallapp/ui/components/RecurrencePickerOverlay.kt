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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.RecurrenceFrequency
import com.example.todowallapp.data.model.RecurrenceRule
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import com.example.todowallapp.ui.theme.WallShapes
import java.time.DayOfWeek
import java.time.LocalDate

/** Quick-pick recurrence patterns shown in the first screen. */
private data class QuickPattern(
    val label: String,
    val rule: RecurrenceRule
)

private fun buildQuickPatterns(taskDueDate: LocalDate?): List<QuickPattern> {
    val dayOfWeek = (taskDueDate ?: LocalDate.now()).dayOfWeek.name
    val dayOfMonth = (taskDueDate ?: LocalDate.now()).dayOfMonth.toString()
    return listOf(
        QuickPattern("Every day", RecurrenceRule(RecurrenceFrequency.DAILY, 1, null)),
        QuickPattern("Every week", RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, dayOfWeek)),
        QuickPattern("Every 2 weeks", RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, dayOfWeek)),
        QuickPattern("Every month", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, dayOfMonth))
    )
}

private enum class PickerScreen { QUICK, CUSTOM }

@Composable
fun RecurrencePickerOverlay(
    visible: Boolean,
    currentRule: RecurrenceRule?,
    taskDueDate: LocalDate?,
    selectedIndex: Int,
    onSelectRule: (RecurrenceRule) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val colors = LocalWallColors.current
    val shape = RoundedCornerShape(WallShapes.CardCornerRadius.dp)
    var screen by remember { mutableStateOf(PickerScreen.QUICK) }

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
            when (screen) {
                PickerScreen.QUICK -> QuickPickerContent(
                    taskDueDate = taskDueDate,
                    currentRule = currentRule,
                    selectedIndex = selectedIndex,
                    onSelectRule = onSelectRule,
                    onCustom = { screen = PickerScreen.CUSTOM },
                    onDismiss = onDismiss
                )
                PickerScreen.CUSTOM -> CustomPickerContent(
                    currentRule = currentRule,
                    onSelectRule = onSelectRule,
                    onBack = { screen = PickerScreen.QUICK },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun QuickPickerContent(
    taskDueDate: LocalDate?,
    currentRule: RecurrenceRule?,
    selectedIndex: Int,
    onSelectRule: (RecurrenceRule) -> Unit,
    onCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWallColors.current
    val patterns = remember(taskDueDate) { buildQuickPatterns(taskDueDate) }

    Text(
        text = if (currentRule != null) "Edit Recurrence" else "Set Recurrence",
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
    HorizontalDivider(color = colors.borderColor)

    patterns.forEachIndexed { index, pattern ->
        val isSelected = selectedIndex == index
        val isCurrent = currentRule == pattern.rule
        PickerItem(
            label = pattern.label,
            isSelected = isSelected,
            isCurrent = isCurrent,
            onClick = { onSelectRule(pattern.rule) }
        )
    }

    HorizontalDivider(
        color = colors.borderColor,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    PickerItem(
        label = "Custom\u2026",
        isSelected = selectedIndex == patterns.size,
        isCurrent = false,
        onClick = onCustom
    )

    HorizontalDivider(
        color = colors.borderColor,
        modifier = Modifier.padding(vertical = 4.dp)
    )

    PickerItem(
        label = "Cancel",
        isSelected = selectedIndex == patterns.size + 1,
        isCurrent = false,
        onClick = onDismiss
    )
}

@Composable
private fun CustomPickerContent(
    currentRule: RecurrenceRule?,
    onSelectRule: (RecurrenceRule) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalWallColors.current
    var frequency by remember {
        mutableStateOf(currentRule?.frequency ?: RecurrenceFrequency.WEEKLY)
    }
    var interval by remember {
        mutableIntStateOf(currentRule?.interval ?: 1)
    }
    var anchorDay by remember {
        mutableStateOf(
            currentRule?.anchor?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?: LocalDate.now().dayOfWeek
        )
    }
    var anchorDayOfMonth by remember {
        mutableIntStateOf(
            currentRule?.anchor?.toIntOrNull() ?: LocalDate.now().dayOfMonth
        )
    }

    Text(
        text = "Custom Recurrence",
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
    HorizontalDivider(color = colors.borderColor)

    Spacer(modifier = Modifier.height(8.dp))

    // Frequency selector
    CustomField(
        label = "Repeat",
        value = frequency.name.lowercase().replaceFirstChar { it.uppercase() },
        onCycleUp = {
            frequency = when (frequency) {
                RecurrenceFrequency.DAILY -> RecurrenceFrequency.WEEKLY
                RecurrenceFrequency.WEEKLY -> RecurrenceFrequency.MONTHLY
                RecurrenceFrequency.MONTHLY -> RecurrenceFrequency.DAILY
            }
        },
        onCycleDown = {
            frequency = when (frequency) {
                RecurrenceFrequency.DAILY -> RecurrenceFrequency.MONTHLY
                RecurrenceFrequency.WEEKLY -> RecurrenceFrequency.DAILY
                RecurrenceFrequency.MONTHLY -> RecurrenceFrequency.WEEKLY
            }
        }
    )

    // Interval selector
    CustomField(
        label = "Every",
        value = "$interval ${frequencyUnit(frequency, interval)}",
        onCycleUp = { interval = (interval + 1).coerceAtMost(12) },
        onCycleDown = { interval = (interval - 1).coerceAtLeast(1) }
    )

    // Anchor selector (context-dependent)
    when (frequency) {
        RecurrenceFrequency.WEEKLY -> {
            CustomField(
                label = "On",
                value = anchorDay.name.lowercase().replaceFirstChar { it.uppercase() },
                onCycleUp = { anchorDay = DayOfWeek.entries[(anchorDay.ordinal + 1) % 7] },
                onCycleDown = { anchorDay = DayOfWeek.entries[(anchorDay.ordinal + 6) % 7] }
            )
        }
        RecurrenceFrequency.MONTHLY -> {
            CustomField(
                label = "On day",
                value = "$anchorDayOfMonth",
                onCycleUp = { anchorDayOfMonth = (anchorDayOfMonth % 31) + 1 },
                onCycleDown = { anchorDayOfMonth = if (anchorDayOfMonth == 1) 31 else anchorDayOfMonth - 1 }
            )
        }
        RecurrenceFrequency.DAILY -> { /* no anchor needed */ }
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(color = colors.borderColor)

    // Save
    PickerItem(
        label = "Save",
        isSelected = false,
        isCurrent = false,
        onClick = {
            val anchor = when (frequency) {
                RecurrenceFrequency.WEEKLY -> anchorDay.name
                RecurrenceFrequency.MONTHLY -> anchorDayOfMonth.toString()
                RecurrenceFrequency.DAILY -> null
            }
            onSelectRule(RecurrenceRule(frequency, interval, anchor))
        }
    )

    // Back
    PickerItem(
        label = "Back",
        isSelected = false,
        isCurrent = false,
        onClick = onBack
    )
}

@Composable
private fun PickerItem(
    label: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalWallColors.current
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) colors.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(WallAnimations.SHORT),
        label = "pickerItemRow"
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
            color = if (isCurrent) colors.accentPrimary else colors.textPrimary
        )
        if (isCurrent) {
            Text(
                text = "\u2713",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.accentPrimary
            )
        }
    }
}

@Composable
private fun CustomField(
    label: String,
    value: String,
    onCycleUp: () -> Unit,
    onCycleDown: () -> Unit
) {
    val colors = LocalWallColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u25C0",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.clickable(onClick = onCycleDown)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary
            )
            Text(
                text = "\u25B6",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.clickable(onClick = onCycleUp)
            )
        }
    }
}

private fun frequencyUnit(frequency: RecurrenceFrequency, interval: Int): String = when (frequency) {
    RecurrenceFrequency.DAILY -> if (interval == 1) "day" else "days"
    RecurrenceFrequency.WEEKLY -> if (interval == 1) "week" else "weeks"
    RecurrenceFrequency.MONTHLY -> if (interval == 1) "month" else "months"
}
