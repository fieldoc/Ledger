package com.example.todowallapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.example.todowallapp.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val jakartaFont = GoogleFont("Plus Jakarta Sans")

private val JakartaFontFamily = FontFamily(
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = jakartaFont, fontProvider = provider, weight = FontWeight.Bold),
)

// Typography optimized for wall-mounted display
// Larger sizes for readability from a distance
val Typography = Typography(
    // Large clock display
    displayLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 72.sp,
        lineHeight = 88.sp,
        letterSpacing = (-2).sp
    ),
    // Medium clock/header
    displayMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-1).sp
    ),
    // Date display
    displaySmall = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    // Section headers
    headlineLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    // List section titles
    headlineMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    // Subsection headers
    headlineSmall = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    // Task item title - primary text
    titleLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    // Task item subtitle
    titleMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp
    ),
    // Small titles
    titleSmall = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    // Body text
    bodyLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp
    ),
    // Secondary body text
    bodyMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    // Small body text
    bodySmall = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    // Labels and badges
    labelLarge = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    ),
    // Small labels
    labelMedium = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    // Tiny labels
    labelSmall = TextStyle(
        fontFamily = JakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
