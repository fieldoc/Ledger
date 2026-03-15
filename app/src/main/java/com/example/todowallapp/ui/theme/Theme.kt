package com.example.todowallapp.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkWallDisplayColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = SurfaceBlack,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextPrimary,

    secondary = AccentSubtle,
    onSecondary = SurfaceBlack,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextPrimary,

    tertiary = AccentWarm,
    onTertiary = SurfaceBlack,
    tertiaryContainer = SurfaceElevated,
    onTertiaryContainer = TextPrimary,

    background = SurfaceBackground,
    onBackground = TextPrimary,

    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceExpanded,
    onSurfaceVariant = TextSecondary,
    surfaceTint = AccentPrimary,

    inverseSurface = Color.White,
    inverseOnSurface = SurfaceBlack,
    inversePrimary = AccentPrimary,

    error = UrgencyOverdue,
    onError = TextPrimary,
    errorContainer = StateSubtle,
    onErrorContainer = TextPrimary,

    outline = DividerColor,
    outlineVariant = BorderColor,

    scrim = Color.Black.copy(alpha = 0.5f)
)

private val LightWallDisplayColorScheme = lightColorScheme(
    primary = LightAccentPrimary,
    onPrimary = Color.White,
    primaryContainer = LightAccentSubtle,
    onPrimaryContainer = LightTextPrimary,

    secondary = LightAccentWarm,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,

    tertiary = LightUrgencyOverdue,
    onTertiary = Color.White,
    tertiaryContainer = LightUrgencyOverdueSubtle,
    onTertiaryContainer = LightTextPrimary,

    background = LightSurfaceBackground,
    onBackground = LightTextPrimary,

    surface = LightSurfaceCard,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceExpanded,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = LightAccentPrimary,

    inverseSurface = SurfaceBlack,
    inverseOnSurface = TextPrimary,
    inversePrimary = LightAccentPrimary,

    error = LightUrgencyOverdue,
    onError = Color.White,
    errorContainer = LightUrgencyOverdueSubtle,
    onErrorContainer = LightTextPrimary,

    outline = LightDividerColor,
    outlineVariant = LightBorderColor,

    scrim = Color.Black.copy(alpha = 0.35f)
)

@Composable
fun LedgerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val wallColors = if (darkTheme) darkWallColors() else lightWallColors()
    val colorScheme = if (darkTheme) DarkWallDisplayColorScheme else LightWallDisplayColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = wallColors.surfaceBackground.toArgb()
            window.navigationBarColor = wallColors.surfaceBackground.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            val isLightBars = !darkTheme
            insetsController.isAppearanceLightStatusBars = isLightBars
            insetsController.isAppearanceLightNavigationBars = isLightBars
        }
    }

    CompositionLocalProvider(LocalWallColors provides wallColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Custom shape definitions for the app
object WallShapes {
    val CardCornerRadius = 10 // dp
    val SmallCornerRadius = 6 // dp
    val MediumCornerRadius = 12 // dp
    val LargeCornerRadius = 16 // dp
}

// Animation durations for consistent feel
object WallAnimations {
    const val SHORT = 200
    const val MEDIUM = 300
    const val LONG = 350
    const val STAGGER_ENTER = 40
    const val STAGGER_EXIT = 25
}
