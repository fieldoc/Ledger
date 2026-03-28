package com.example.todowallapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todowallapp.data.model.WeatherCondition
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val StripShape = RoundedCornerShape(10.dp)
private val MiniCardShape = RoundedCornerShape(8.dp)

/**
 * A slim weather context strip for the Day view.
 * Shows the current day's condition with icon + label.
 * When [isExpanded], reveals a 3-day mini-forecast below.
 */
@Composable
fun WeatherContextStrip(
    date: LocalDate,
    weatherForecast: Map<LocalDate, WeatherCondition>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    isFocused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val condition = weatherForecast[date] ?: return
    val colors = LocalWallColors.current
    val tint = condition.tintColor(colors.isDark)
    // Strengthen the tint slightly for the strip background
    val maxStripAlpha = if (colors.isDark) 0.30f else 0.15f
    val maxBarAlpha = if (colors.isDark) 0.65f else 0.5f
    val stripBg = if (tint != Color.Transparent) {
        tint.copy(alpha = (tint.alpha * 2.5f).coerceAtMost(maxStripAlpha))
    } else {
        colors.surfaceCard.copy(alpha = 0.5f)
    }
    val barColor = if (tint != Color.Transparent) {
        tint.copy(alpha = (tint.alpha * 6f).coerceAtMost(maxBarAlpha))
    } else {
        colors.borderColor
    }

    val focusModifier = if (isFocused) {
        Modifier.border(2.dp, colors.accentPrimary.copy(alpha = 0.8f), StripShape)
    } else {
        Modifier
    }
    Column(modifier = modifier.fillMaxWidth()) {
        // Primary strip — today's weather
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(StripShape)
                .then(focusModifier)
                .background(stripBg, StripShape)
                .clickable(onClick = onToggleExpanded)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(barColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = condition.icon,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 7.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = condition.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                letterSpacing = 0.3.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Expand hint
            Text(
                text = if (isExpanded) "−" else "+",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
        }

        // Expanded: 3-day mini-forecast
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(250)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Show today + next 2 days
                for (offset in 0..2) {
                    val forecastDate = date.plusDays(offset.toLong())
                    val forecastCondition = weatherForecast[forecastDate]
                    if (forecastCondition != null) {
                        MiniWeatherCard(
                            date = forecastDate,
                            condition = forecastCondition,
                            isToday = offset == 0,
                            colors = colors,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniWeatherCard(
    date: LocalDate,
    condition: WeatherCondition,
    isToday: Boolean,
    colors: WallColors,
    modifier: Modifier = Modifier
) {
    val tint = condition.tintColor(colors.isDark)
    val maxCardAlpha = if (colors.isDark) 0.25f else 0.12f
    val cardBg = if (tint != Color.Transparent) {
        tint.copy(alpha = (tint.alpha * 2f).coerceAtMost(maxCardAlpha))
    } else {
        colors.surfaceCard.copy(alpha = 0.4f)
    }

    val dayLabel = if (isToday) {
        "Today"
    } else {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    Column(
        modifier = modifier
            .clip(MiniCardShape)
            .background(cardBg, MiniCardShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isToday) colors.accentPrimary else colors.textSecondary
        )
        Text(
            text = condition.icon,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = condition.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            letterSpacing = 0.2.sp
        )
    }
}

/**
 * Human-readable name for the weather condition.
 */
private val WeatherCondition.displayName: String
    get() = when (this) {
        WeatherCondition.CLEAR -> "Clear"
        WeatherCondition.PARTLY_CLOUDY -> "Partly Cloudy"
        WeatherCondition.CLOUDY -> "Cloudy"
        WeatherCondition.RAIN -> "Rain"
        WeatherCondition.SNOW -> "Snow"
        WeatherCondition.STORM -> "Storm"
    }
