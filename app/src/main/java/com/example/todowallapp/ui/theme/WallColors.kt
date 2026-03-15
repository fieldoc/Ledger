package com.example.todowallapp.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.todowallapp.data.model.TaskUrgency

data class WallColors(
    val surfaceBackground: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceExpanded: Color,
    val surfaceBlack: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val ambientText: Color,
    val accentPrimary: Color,
    val accentSubtle: Color,
    val accentWarm: Color,
    val urgencyOverdue: Color,
    val urgencyDueToday: Color,
    val urgencyDueSoon: Color,
    val urgencyOverdueSubtle: Color,
    val borderColor: Color,
    val dividerColor: Color,
    val connectivityOnline: Color,
    val textFaint: Color,
    val borderFocused: Color,
    val rimGloss: Color,
    val rimGlossStrong: Color,
    val isDark: Boolean
)

fun darkWallColors(): WallColors = WallColors(
    surfaceBackground = SurfaceBackground,
    surfaceCard = SurfaceCard,
    surfaceElevated = SurfaceElevated,
    surfaceExpanded = SurfaceExpanded,
    surfaceBlack = SurfaceBlack,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    textDisabled = TextDisabled,
    ambientText = AmbientText,
    accentPrimary = AccentPrimary,
    accentSubtle = AccentSubtle,
    accentWarm = AccentWarm,
    urgencyOverdue = UrgencyOverdue,
    urgencyDueToday = UrgencyDueToday,
    urgencyDueSoon = UrgencyDueSoon,
    urgencyOverdueSubtle = UrgencyOverdueSubtle,
    borderColor = BorderColor,
    dividerColor = DividerColor,
    connectivityOnline = ConnectivityOnline,
    textFaint = Color(0xFF333333),
    borderFocused = Color(0x3380CBC4),  // rgba(128,203,196,0.2)
    rimGloss = Color(0x1AFFFFFF),
    rimGlossStrong = Color(0x26FFFFFF),
    isDark = true
)

fun lightWallColors(): WallColors = WallColors(
    surfaceBackground = LightSurfaceBackground,
    surfaceCard = LightSurfaceCard,
    surfaceElevated = LightSurfaceElevated,
    surfaceExpanded = LightSurfaceExpanded,
    surfaceBlack = LightSurfaceBlack,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    textDisabled = LightTextDisabled,
    ambientText = LightAmbientText,
    accentPrimary = LightAccentPrimary,
    accentSubtle = LightAccentSubtle,
    accentWarm = LightAccentWarm,
    urgencyOverdue = LightUrgencyOverdue,
    urgencyDueToday = LightUrgencyDueToday,
    urgencyDueSoon = LightUrgencyDueSoon,
    urgencyOverdueSubtle = LightUrgencyOverdueSubtle,
    borderColor = LightBorderColor,
    dividerColor = LightDividerColor,
    connectivityOnline = LightConnectivityOnline,
    textFaint = Color(0xFFCCCCCC),
    borderFocused = Color(0x3300897B),
    rimGloss = Color(0x1A000000),
    rimGlossStrong = Color(0x26000000),
    isDark = false
)

fun WallColors.urgencyColor(urgency: TaskUrgency): Color = when (urgency) {
    TaskUrgency.OVERDUE -> urgencyOverdue
    TaskUrgency.DUE_TODAY -> urgencyDueToday
    TaskUrgency.DUE_SOON -> urgencyDueSoon
    TaskUrgency.NORMAL -> textSecondary
    TaskUrgency.COMPLETED -> textSecondary.copy(alpha = 0.5f)
}

val LocalWallColors = staticCompositionLocalOf<WallColors> {
    error("No WallColors provided")
}
