package com.example.todowallapp.ui.theme

import androidx.compose.ui.graphics.Color

// SURFACES - Soft Graphite neutral palette (no blue/warm bias)
val SurfaceBackground = Color(0xFF121212)     // True neutral base
val SurfaceCard = Color(0xFF1E1E1E)           // Card surface (focus fill) — raised for better contrast
val SurfaceElevated = Color(0xFF262626)       // Elevated overlays
val SurfaceBlack = Color(0xFF0A0A0A)          // Sleep mode / deepest
val SurfaceExpanded = Color(0xFF222222)        // Nested/expanded surface — raised proportionally

// TEXT - Neutral gray hierarchy
val TextPrimary = Color(0xFFEEEEEE)           // Primary text
val TextSecondary = Color(0xFFBDBDBD)         // Secondary text
val TextMuted = Color(0xFF757575)             // Muted text
val TextDisabled = Color(0xFF555555)          // Disabled/completed text
val AmbientText = Color.White                 // Ambient mode (unchanged)

// ACCENTS - Mint Teal primary + Soft Rose secondary
val AccentPrimary = Color(0xFF80CBC4)         // Teal focus/action accent
val AccentSubtle = Color(0xFFB2DFDB)          // Subtle teal for backgrounds
val AccentWarm = Color(0xFFCF8E8E)            // Soft rose for completed/structural warmth

// URGENCY - Warm amber/terracotta tones for due date urgency
val UrgencyOverdue = Color(0xFFC97C52)          // Rich terracotta for overdue
val UrgencyDueToday = Color(0xFFD4A06A)         // Warm amber for due-today
val UrgencyDueSoon = Color(0xFFB8A88A)          // Very subtle warmth for due-soon
val UrgencyOverdueSubtle = Color(0xFF3D2E24)    // Dark warm background for overdue badges

// STATES - Minimal, using only accent color variations
val StateCompleted = AccentPrimary.copy(alpha = 0.4f)  // Muted for completed
val StateUrgent = UrgencyOverdue                        // Use warm amber instead of blue
val StateSubtle = AccentPrimary.copy(alpha = 0.15f)    // Very subtle backgrounds

// STRUCTURE - Neutral borders for Soft Graphite
val BorderColor = Color(0xFF2A2A2A)           // Card border — raised from 1E to 2A for visible edges
val DividerColor = Color(0xFF1A1A1A)          // Subtle divider

// LEGACY COMPATIBILITY (using AccentPrimary variations instead of separate colors)
val Primary = AccentPrimary                   // Alias for compatibility
val AccentSuccess = AccentPrimary.copy(alpha = 0.6f)  // Success state using accent (was green)
val AccentError = UrgencyOverdue                        // Error/urgency uses warm amber now

// CONNECTIVITY
val ConnectivityOnline = AccentPrimary.copy(alpha = 0.6f)  // Teal online indicator
val ConnectivityOffline = AccentWarm                         // Rose offline indicator

// LIGHT MODE SURFACES - Warm linen (neutral, not blue)
val LightSurfaceBackground = Color(0xFFFAFAF8)
val LightSurfaceCard = Color(0xFFF5F4F0)
val LightSurfaceElevated = Color(0xFFEDECE8)
val LightSurfaceExpanded = Color(0xFFF0EFEB)
val LightSurfaceBlack = Color(0xFFE8E7E3)

// LIGHT MODE TEXT - Warm dark, not pure black
val LightTextPrimary = Color(0xFF1C1A17)
val LightTextSecondary = Color(0xFF4A4540)
val LightTextMuted = Color(0xFF8A8178)
val LightTextDisabled = Color(0xFFADA89F)
val LightAmbientText = Color(0xFF1C1A17)

// LIGHT MODE ACCENTS - Teal/rose in daylight
val LightAccentPrimary = Color(0xFF00897B)    // Deeper teal for light bg contrast
val LightAccentSubtle = Color(0xFFB2DFDB)
val LightAccentWarm = Color(0xFFC06666)       // Soft rose for light mode (matches dark #CF8E8E intent)

// LIGHT MODE URGENCY - Richer saturation in daylight
val LightUrgencyOverdue = Color(0xFFBF4E30)
val LightUrgencyDueToday = Color(0xFFC47B20)
val LightUrgencyDueSoon = Color(0xFF9A8B6B)
val LightUrgencyOverdueSubtle = Color(0xFFFAEAE5)

// LIGHT MODE STATES
val LightStateCompleted = LightAccentPrimary.copy(alpha = 0.35f)
val LightStateSubtle = LightAccentPrimary.copy(alpha = 0.12f)

// LIGHT MODE STRUCTURE
val LightBorderColor = Color(0x1A1C1A17)
val LightDividerColor = Color(0xFFDDD9D3)

// LIGHT MODE CONNECTIVITY
val LightConnectivityOnline = LightAccentPrimary.copy(alpha = 0.6f)
val LightConnectivityOffline = LightUrgencyOverdue
