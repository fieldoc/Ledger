package com.example.todowallapp.ui.utils

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Orientation-adaptive layout dimensions for the wall display.
 * Landscape tightens vertical spacing and constrains content width
 * so cards don't stretch across the full wide screen.
 */
@Immutable
data class LayoutDimensions(
    val isLandscape: Boolean,
    /** Max width for the main content column (Dp.Unspecified = fill) */
    val contentMaxWidth: Dp,
    /** Top padding of the main screen column */
    val topPadding: Dp,
    /** Horizontal padding of the main screen column */
    val horizontalPadding: Dp,
    /** Vertical gap between the header row and the content */
    val headerContentGap: Dp,
    /** Vertical spacing between folder sections in the LazyColumn */
    val folderSpacing: Dp,
    /** Bottom content padding in the LazyColumn (room for voice FAB) */
    val bottomContentPadding: Dp,
    /** Scale factor for body-level text (1.0 = normal) */
    val fontScale: Float,

    // ── Calendar Day View ──
    /** Height of a time slot row with events */
    val daySlotHeightWithEvents: Dp,
    /** Height of a time slot row without events */
    val daySlotHeightEmpty: Dp,
    /** Height of the slot box (inside the row) with events */
    val daySlotBoxHeightWithEvents: Dp,
    /** Height of the slot box without events */
    val daySlotBoxHeightEmpty: Dp,
    /** Width of the time label column */
    val dayTimeColumnWidth: Dp,
    /** Height of compressed (empty range) slot rows */
    val dayCompressedSlotHeight: Dp,
    /** Max width for event chip text (Dp.Unspecified = no limit) */
    val dayEventChipMaxWidth: Dp,

    // ── Calendar Month View ──
    /** Aspect ratio for day cells (1f = square) */
    val monthCellAspectRatio: Float,
    /** Vertical spacing between week rows */
    val monthWeekRowSpacing: Dp,
    /** Gap between weekday labels and grid */
    val monthLabelGridGap: Dp,

    // ── Calendar Week View ──
    /** Vertical spacing between day rows */
    val weekRowSpacing: Dp,
    /** Vertical padding inside each day row */
    val weekRowVerticalPadding: Dp,

    // ── Calendar common ──
    /** Vertical spacing between calendar screen elements */
    val calendarElementSpacing: Dp
)

val PortraitDimensions = LayoutDimensions(
    isLandscape = false,
    contentMaxWidth = Dp.Unspecified,
    topPadding = 16.dp,
    horizontalPadding = 40.dp,
    headerContentGap = 24.dp,
    folderSpacing = 20.dp,
    bottomContentPadding = 60.dp,
    fontScale = 1f,
    // Calendar Day
    daySlotHeightWithEvents = 80.dp,
    daySlotHeightEmpty = 56.dp,
    daySlotBoxHeightWithEvents = 76.dp,
    daySlotBoxHeightEmpty = 48.dp,
    dayTimeColumnWidth = 66.dp,
    dayCompressedSlotHeight = 36.dp,
    dayEventChipMaxWidth = 180.dp,
    // Calendar Month
    monthCellAspectRatio = 1f,
    monthWeekRowSpacing = 4.dp,
    monthLabelGridGap = 8.dp,
    // Calendar Week
    weekRowSpacing = 8.dp,
    weekRowVerticalPadding = 10.dp,
    // Calendar common
    calendarElementSpacing = 12.dp
)

val LandscapeDimensions = LayoutDimensions(
    isLandscape = true,
    contentMaxWidth = 720.dp,
    topPadding = 8.dp,
    horizontalPadding = 24.dp,
    headerContentGap = 12.dp,
    folderSpacing = 12.dp,
    bottomContentPadding = 40.dp,
    fontScale = 0.92f,
    // Calendar Day — tighter rows to fit more on screen
    daySlotHeightWithEvents = 64.dp,
    daySlotHeightEmpty = 44.dp,
    daySlotBoxHeightWithEvents = 60.dp,
    daySlotBoxHeightEmpty = 38.dp,
    dayTimeColumnWidth = 54.dp,
    dayCompressedSlotHeight = 30.dp,
    dayEventChipMaxWidth = Dp.Unspecified,
    // Calendar Month — wider cells for landscape aspect ratio
    monthCellAspectRatio = 1.4f,
    monthWeekRowSpacing = 2.dp,
    monthLabelGridGap = 4.dp,
    // Calendar Week — compact rows
    weekRowSpacing = 4.dp,
    weekRowVerticalPadding = 6.dp,
    // Calendar common
    calendarElementSpacing = 8.dp
)

@Composable
fun rememberLayoutDimensions(): LayoutDimensions {
    val configuration = LocalConfiguration.current
    return remember(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            LandscapeDimensions
        } else {
            PortraitDimensions
        }
    }
}
