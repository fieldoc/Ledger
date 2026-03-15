# Calendar Week & Month Views Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Month view (default on entering calendar mode) and a Week view (next 7 days from today) to the existing CalendarScreen, with sub-mode switching inside calendar.

**Architecture:** Add a `CalendarViewMode` enum (MONTH / WEEK / DAY), extend the repository with a single-request date-range fetcher, add `eventsForRange` to ViewModel state, and create two new Compose components (`CalendarMonthView`, `CalendarWeekView`) that plug into the existing `CalendarScreen` routing logic.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Google Calendar API (google-api-services-calendar), `java.time` (core library desugaring active), existing WallColors / WallAnimations theme system.

---

## Existing Code Reference

Key files and what they contain:
- `data/model/CalendarEvent.kt` — `CalendarEvent` data class + `occursOn(date)` helper
- `data/model/AppMode.kt` — simple `enum class AppMode { WALL, PHONE }`
- `data/repository/GoogleCalendarRepository.kt` — `getEventsForDate(date, calendarId)` fetches single-day
- `viewmodel/TaskWallViewModel.kt` — `TaskWallUiState` data class (lines 57–82), all calendar state here
- `ui/screens/CalendarScreen.kt` — `CalendarScreen`, `DateAndCalendarBar`, `AccessRequiredCard`
- `ui/components/CalendarDayView.kt` — existing day schedule view (half-hour slots)
- `ui/components/WeekStrip.kt` — 7-day horizontal strip used in current day view
- `ui/components/ViewSwitcherPill.kt` — generic pill switcher (takes `List<ViewSwitcherOption>`)
- `ui/theme/WallColors.kt` — color tokens: `accentPrimary`, `textPrimary`, `textSecondary`, `textDisabled`, `surfaceCard`, `surfaceExpanded`, `borderColor`, `urgencyWarm`, `surfaceBlack`
- `ui/theme/Type.kt` — Inter-based typography scale

---

## Task 1: Add CalendarViewMode Enum

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/data/model/CalendarViewMode.kt`

**Step 1: Create the enum**

```kotlin
package com.example.todowallapp.data.model

enum class CalendarViewMode {
    MONTH,
    WEEK,
    DAY   // existing behavior — kept for drill-down from month/week
}
```

**Step 2: Verify it compiles**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no errors referencing the new file)

---

## Task 2: Extend Repository with Date-Range Fetching

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/data/repository/GoogleCalendarRepository.kt`

**Step 1: Add `getEventsForDateRange` method**

Add this method after `getEventsForDate` in `GoogleCalendarRepository`:

```kotlin
/**
 * Fetches all events across [startDate]..[endDateInclusive] in a single API call,
 * then groups them by the LocalDate on which they occur.
 * All-day events that span multiple days appear in each covered date's list.
 */
suspend fun getEventsForDateRange(
    startDate: LocalDate,
    endDateInclusive: LocalDate,
    calendarId: String = PRIMARY_CALENDAR_ID
): Result<Map<LocalDate, List<CalendarEvent>>> = withContext(Dispatchers.IO) {
    try {
        val service = calendarService
            ?: return@withContext Result.failure(Exception("Calendar service not initialized"))

        val rangeStart = startDate.atStartOfDay(zoneId).toInstant()
        val rangeEnd = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()

        val response = service.events().list(calendarId)
            .setShowDeleted(false)
            .setSingleEvents(true)
            .setOrderBy("startTime")
            .setTimeMin(DateTime(rangeStart.toEpochMilli()))
            .setTimeMax(DateTime(rangeEnd.toEpochMilli()))
            .setMaxResults(500)
            .execute()

        val allEvents = response.items
            ?.mapNotNull { it.toCalendarEvent(calendarId = calendarId, zoneId = zoneId) }
            ?: emptyList()

        // Build a map: each date in range → events that occur on that date
        val dateRange = generateSequence(startDate) { d ->
            d.plusDays(1).takeIf { !it.isAfter(endDateInclusive) }
        }.toList()

        val grouped = dateRange.associateWith { date ->
            allEvents
                .filter { event -> event.occursOn(date) }
                .sortedWith(compareBy(
                    { !it.isAllDay },        // all-day events first
                    { it.startDateTime },
                    { it.title.lowercase() }
                ))
        }

        Result.success(grouped)
    } catch (e: Exception) {
        Log.e("CalendarRepository", "Failed to fetch events for range", e)
        Result.failure(e)
    }
}
```

**Step 2: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 3: Extend ViewModel State + Logic

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

**Step 1: Add new fields to `TaskWallUiState`**

In the `TaskWallUiState` data class (around line 57), add after `calendarError`:

```kotlin
val calendarViewMode: CalendarViewMode = CalendarViewMode.MONTH,
val eventsForRange: Map<LocalDate, List<CalendarEvent>> = emptyMap(),
val calendarRangeStart: LocalDate = LocalDate.now(),  // first date of current view range
```

Also add the import at the top of the file:
```kotlin
import com.example.todowallapp.data.model.CalendarViewMode
```

**Step 2: Add `setCalendarViewMode` function to ViewModel**

Find the existing calendar functions (e.g., `selectCalendarDate`, `selectCalendar`) and add:

```kotlin
fun setCalendarViewMode(mode: CalendarViewMode) {
    _uiState.update { it.copy(calendarViewMode = mode) }
    // Reload range data for the newly selected mode
    loadCalendarRange(mode, _uiState.value.selectedCalendarDate)
}

private fun loadCalendarRange(mode: CalendarViewMode, anchor: LocalDate) {
    viewModelScope.launch {
        _uiState.update { it.copy(isCalendarLoading = true, calendarError = null) }
        val calendarId = _uiState.value.selectedCalendarId

        val (start, end) = when (mode) {
            CalendarViewMode.MONTH -> {
                val firstOfMonth = anchor.withDayOfMonth(1)
                // Visible grid: from Monday on or before the 1st, to Sunday on or after last day
                val gridStart = firstOfMonth.with(
                    java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SUNDAY)
                )
                val lastOfMonth = firstOfMonth.with(
                    java.time.temporal.TemporalAdjusters.lastDayOfMonth()
                )
                val gridEnd = lastOfMonth.with(
                    java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY)
                )
                Pair(gridStart, gridEnd)
            }
            CalendarViewMode.WEEK -> {
                val today = LocalDate.now()
                Pair(today, today.plusDays(6))
            }
            CalendarViewMode.DAY -> {
                // Day view uses existing calendarEvents — no range fetch needed
                _uiState.update { it.copy(isCalendarLoading = false) }
                return@launch
            }
        }

        _uiState.update { it.copy(calendarRangeStart = start) }

        calendarRepository.getEventsForDateRange(start, end, calendarId)
            .onSuccess { grouped ->
                _uiState.update { it.copy(
                    eventsForRange = grouped,
                    isCalendarLoading = false,
                    calendarError = null
                ) }
            }
            .onFailure { error ->
                _uiState.update { it.copy(
                    isCalendarLoading = false,
                    calendarError = error.message
                ) }
            }
    }
}
```

**Step 3: Trigger range load when calendar mode is entered**

Find the existing function that is called when the calendar tab is selected (look for where `hasCalendarScope` is checked or `selectedCalendarDate` is first set). Add a call to `loadCalendarRange(CalendarViewMode.MONTH, LocalDate.now())` there, so the month view pre-loads on entry.

Also modify `selectCalendarDate` to reload the range for WEEK/MONTH mode when date changes:

```kotlin
fun selectCalendarDate(date: LocalDate) {
    _uiState.update { it.copy(selectedCalendarDate = date) }
    val mode = _uiState.value.calendarViewMode
    if (mode == CalendarViewMode.DAY) {
        loadCalendarForDate(date)  // existing single-day fetch call
    } else {
        loadCalendarRange(mode, date)
    }
}
```

**Step 4: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 4: Create CalendarMonthView Component

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/CalendarMonthView.kt`

**Design spec:**
- 7-column grid (Sun–Sat columns, Sun = column 0)
- Row 0: day-of-week headers (S M T W T F S) in `labelSmall`, `textSecondary`
- Rows 1–6: day cells for all visible dates (padding days from prev/next month shown muted)
- Each cell:
  - Day number in `labelMedium` or `bodySmall`
  - Today: filled circle background in `accentPrimary` (muted, 20% opacity), number in `accentPrimary`
  - Selected date: ring border `accentPrimary` (1dp)
  - Padding days (different month): number in `textDisabled`
  - Up to 3 event dots beneath the day number (small 5dp circles, colored by urgency):
    - Promoted task event → `urgencyWarm`
    - All-day event → `accentPrimary` (60% opacity)
    - Timed event → `textSecondary` (50% opacity)
  - If > 3 events: show `+N` in `labelSmall` `textDisabled`
- Month navigation: `onPrevMonth` / `onNextMonth` callbacks (month title shown in `DateAndCalendarBar`)
- Clicking a day: calls `onDaySelected(date)` → ViewModel updates selectedDate + switches to WEEK or DAY view

```kotlin
package com.example.todowallapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.ui.theme.LocalWallColors
import com.example.todowallapp.ui.theme.WallAnimations
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val DAY_HEADERS = listOf("S", "M", "T", "W", "T", "F", "S")
private const val MAX_EVENT_DOTS = 3

@Composable
fun CalendarMonthView(
    displayMonth: LocalDate,          // any date within the month to display
    selectedDate: LocalDate,
    eventsForRange: Map<LocalDate, List<CalendarEvent>>,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()

    // Build visible grid dates
    val firstOfMonth = displayMonth.withDayOfMonth(1)
    val gridStart = firstOfMonth.with(
        java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)
    )
    val lastOfMonth = firstOfMonth.with(
        java.time.temporal.TemporalAdjusters.lastDayOfMonth()
    )
    val gridEnd = lastOfMonth.with(
        java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)
    )
    val gridDates = generateSequence(gridStart) { d ->
        d.plusDays(1).takeIf { !it.isAfter(gridEnd) }
    }.toList()
    val rowCount = gridDates.size / 7

    Column(modifier = modifier.fillMaxWidth()) {
        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            DAY_HEADERS.forEach { header ->
                Text(
                    text = header,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid rows
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0..6) {
                    val date = gridDates[row * 7 + col]
                    val isCurrentMonth = date.month == firstOfMonth.month
                    val isToday = date == today
                    val isSelected = date == selectedDate
                    val events = eventsForRange[date] ?: emptyList()

                    MonthDayCell(
                        date = date,
                        isCurrentMonth = isCurrentMonth,
                        isToday = isToday,
                        isSelected = isSelected,
                        events = events,
                        onSelected = { onDaySelected(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (row < rowCount - 1) Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current

    val numberColor = when {
        !isCurrentMonth -> colors.textDisabled
        isToday -> colors.accentPrimary
        else -> colors.textPrimary
    }

    val cellBg = when {
        isToday -> colors.accentPrimary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }

    val borderMod = if (isSelected && !isToday)
        Modifier.border(1.dp, colors.accentPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
    else Modifier

    val displayedEvents = events.take(MAX_EVENT_DOTS)
    val overflow = events.size - MAX_EVENT_DOTS

    Column(
        modifier = modifier
            .then(borderMod)
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg)
            .clickable(onClick = onSelected)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            ),
            color = numberColor,
            textAlign = TextAlign.Center
        )

        if (events.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                displayedEvents.forEachIndexed { index, event ->
                    if (index > 0) Spacer(modifier = Modifier.width(2.dp))
                    val dotColor = when {
                        event.isPromotedTask -> colors.urgencyWarm
                        event.isAllDay -> colors.accentPrimary.copy(alpha = 0.6f)
                        else -> colors.textSecondary.copy(alpha = 0.5f)
                    }
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
                if (overflow > 0) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "+$overflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDisabled
                    )
                }
            }
        }
    }
}
```

**Step 2: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 5: Create CalendarWeekView Component

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/ui/components/CalendarWeekView.kt`

**Design spec:**
- 7 rows, one per day (today → today+6)
- Each row:
  - Left column (~72dp): day label — "Today" / "Mon 8" / "Tue 9" etc. in `labelMedium`
  - Today row: warm subtle background band (accentPrimary at 8% opacity)
  - Selected row: gentle glow border (matching app's encoder focus design)
  - Right side: event chips for that day, wrapping horizontally
  - Empty day: a faint "No events" label in `textDisabled`
- Event chip:
  - Background: `surfaceCard` (promoted) or `surfaceExpanded` (regular)
  - Left accent line (2dp): `urgencyWarm` for promoted, `accentPrimary` for timed, `textSecondary` for all-day
  - Time string (if timed): `labelSmall` `textSecondary`
  - Title: `labelMedium` `textPrimary`, single line, ellipsis
- Clicking a row calls `onDaySelected(date)` → ViewModel sets selectedDate + switches to DAY view
- Encoder focus highlight: `isSelectedDate` parameter drives the glow row (same warm glow as TaskItem design)

```kotlin
package com.example.todowallapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.ui.theme.LocalWallColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@Composable
fun CalendarWeekView(
    eventsForRange: Map<LocalDate, List<CalendarEvent>>,
    selectedDate: LocalDate,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalWallColors.current
    val today = LocalDate.now()
    val days = (0..6).map { today.plusDays(it.toLong()) }

    Column(modifier = modifier.fillMaxWidth()) {
        days.forEachIndexed { index, date ->
            if (index > 0) Spacer(modifier = Modifier.height(8.dp))
            WeekDayRow(
                date = date,
                isToday = date == today,
                isSelected = date == selectedDate,
                events = eventsForRange[date] ?: emptyList(),
                onSelected = { onDaySelected(date) }
            )
        }
    }
}

@Composable
private fun WeekDayRow(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    onSelected: () -> Unit
) {
    val colors = LocalWallColors.current

    val rowBg = when {
        isToday -> colors.accentPrimary.copy(alpha = 0.07f)
        else -> Color.Transparent
    }

    val dayLabel = when {
        isToday -> "Today"
        else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US) +
                " " + date.dayOfMonth
    }

    val borderMod = if (isSelected)
        Modifier.border(1.dp, colors.accentPrimary.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
    else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderMod)
            .clip(RoundedCornerShape(10.dp))
            .background(rowBg)
            .clickable(onClick = onSelected)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Day label
        Text(
            text = dayLabel,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isToday) colors.accentPrimary else colors.textSecondary
        )

        // Events column
        if (events.isEmpty()) {
            Text(
                text = "No events",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textDisabled
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                events.forEach { event ->
                    WeekEventChip(event = event)
                }
            }
        }
    }
}

@Composable
private fun WeekEventChip(event: CalendarEvent) {
    val colors = LocalWallColors.current

    val accentColor = when {
        event.isPromotedTask -> colors.urgencyWarm
        event.isAllDay -> colors.accentPrimary.copy(alpha = 0.7f)
        else -> colors.textSecondary.copy(alpha = 0.5f)
    }

    val chipBg = if (event.isPromotedTask)
        colors.surfaceCard
    else
        colors.surfaceExpanded

    val timeLabel = when {
        event.isAllDay -> "All day"
        event.startDateTime != null ->
            event.startDateTime.toLocalTime().format(TIME_FMT).lowercase()
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(chipBg)
            .padding(start = 0.dp, end = 8.dp, top = 0.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(32.dp)
                .background(accentColor)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f).padding(vertical = 5.dp)) {
            if (timeLabel.isNotEmpty()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }
            Text(
                text = event.title,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

**Step 2: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 6: Update CalendarScreen to Route by View Mode

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt`

**Step 1: Add imports**

Add to the import block:
```kotlin
import com.example.todowallapp.data.model.CalendarViewMode
import com.example.todowallapp.ui.components.CalendarMonthView
import com.example.todowallapp.ui.components.CalendarWeekView
```

**Step 2: Update CalendarScreen signature to accept new ViewModel callbacks**

The `CalendarScreen` composable should receive:
```kotlin
calendarViewMode: CalendarViewMode,
eventsForRange: Map<LocalDate, List<CalendarEvent>>,
onViewModeChange: (CalendarViewMode) -> Unit,
onDaySelectedFromGrid: (LocalDate) -> Unit,  // from month/week → drill to day
```

These are passed from the call site (wherever `CalendarScreen` is instantiated, likely `TaskWallScreen.kt` or `MainActivity.kt`).

**Step 3: Add the sub-switcher pill**

Inside `CalendarScreen`, just below the `WeekStrip` (or replacing it for MONTH/WEEK modes), add:

```kotlin
// Sub-mode switcher — only visible inside calendar mode
val calViewOptions = listOf(
    ViewSwitcherOption(key = "MONTH", label = "Month"),
    ViewSwitcherOption(key = "WEEK", label = "Week"),
    ViewSwitcherOption(key = "DAY", label = "Day")
)
ViewSwitcherPill(
    options = calViewOptions,
    selectedKey = calendarViewMode.name,
    onSelect = { key ->
        onViewModeChange(CalendarViewMode.valueOf(key))
    },
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
)
```

**Step 4: Replace the main content area with view-mode routing**

Replace the current `if (!hasCalendarScope) ... else CalendarDayView(...)` block with:

```kotlin
when {
    !hasCalendarScope -> AccessRequiredCard(...)
    isCalendarLoading -> CircularProgressIndicator(...)
    calendarError != null -> Text(calendarError, ...)
    calendarViewMode == CalendarViewMode.MONTH -> CalendarMonthView(
        displayMonth = selectedDate,
        selectedDate = selectedDate,
        eventsForRange = eventsForRange,
        onDaySelected = { date ->
            onDaySelectedFromGrid(date)
            onViewModeChange(CalendarViewMode.WEEK)  // drill from month → week
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
    calendarViewMode == CalendarViewMode.WEEK -> CalendarWeekView(
        eventsForRange = eventsForRange,
        selectedDate = selectedDate,
        onDaySelected = { date ->
            onDaySelectedFromGrid(date)
            onViewModeChange(CalendarViewMode.DAY)  // drill from week → day
        },
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
    else -> CalendarDayView(...)  // existing day view, unchanged
}
```

**Step 5: Update `DateAndCalendarBar` for month navigation**

When `calendarViewMode == CalendarViewMode.MONTH`, the prev/next arrows should move by one month instead of one day:

In `DateAndCalendarBar`, change the arrow `onClick` to accept lambdas or check viewMode:
```kotlin
onPrevClick = {
    if (calendarViewMode == CalendarViewMode.MONTH) {
        onSelectDate(selectedDate.minusMonths(1))
    } else {
        onSelectDate(selectedDate.minusDays(1))
    }
},
onNextClick = {
    if (calendarViewMode == CalendarViewMode.MONTH) {
        onSelectDate(selectedDate.plusMonths(1))
    } else {
        onSelectDate(selectedDate.plusDays(1))
    }
}
```

Also update the date label in `DateAndCalendarBar` to show month+year when in MONTH mode:
```kotlin
val dateLabel = when (calendarViewMode) {
    CalendarViewMode.MONTH -> selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    else -> selectedDate.format(CalendarDateFormatter)  // existing "Friday, Mar 7"
}
```

**Step 6: Hide WeekStrip in MONTH and WEEK modes**

WeekStrip is redundant when showing the full month grid or 7-day view. Wrap it:
```kotlin
if (calendarViewMode == CalendarViewMode.DAY) {
    WeekStrip(...)
}
```

**Step 7: Verify compile and test**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 7: Wire Up ViewModel Calls at the Call Site

**Files:**
- Modify: wherever `CalendarScreen(...)` is called — likely `ui/screens/TaskWallScreen.kt` or `MainActivity.kt`

**Step 1: Find the CalendarScreen call site**

Search for `CalendarScreen(` in the codebase to find where it's instantiated.

**Step 2: Pass new parameters from ViewModel state**

```kotlin
CalendarScreen(
    // existing params...
    calendarViewMode = uiState.calendarViewMode,
    eventsForRange = uiState.eventsForRange,
    onViewModeChange = { mode -> viewModel.setCalendarViewMode(mode) },
    onDaySelectedFromGrid = { date -> viewModel.selectCalendarDate(date) },
    // existing params...
)
```

**Step 3: Trigger initial MONTH load when calendar becomes visible**

Find the code that switches to calendar tab/mode. Add:
```kotlin
// When calendar tab is selected, load month view data
viewModel.setCalendarViewMode(CalendarViewMode.MONTH)
```

**Step 4: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 8: Encoder Navigation for Month View

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/CalendarScreen.kt`

**Design:** In MONTH mode, encoder rotate = move selected date by 1 day. Encoder click = drill to week view for that date. In WEEK mode, encoder rotate = move between the 7 rows. Encoder click = drill to day view.

**Step 1: Extend `onKeyEvent` in CalendarScreen**

The existing `onKeyEvent` handler in `CalendarScreen` already handles `Key.DirectionUp`, `Key.DirectionDown`, `Key.DirectionLeft`, `Key.DirectionRight`, `Key.Enter`. Extend it:

```kotlin
// In the onKeyEvent block, add MONTH mode routing:
CalendarViewMode.MONTH -> when (key) {
    Key.DirectionRight -> { onSelectDate(selectedDate.plusDays(1)); true }
    Key.DirectionLeft  -> { onSelectDate(selectedDate.minusDays(1)); true }
    Key.DirectionDown  -> { onSelectDate(selectedDate.plusDays(7)); true }
    Key.DirectionUp    -> { onSelectDate(selectedDate.minusDays(7)); true }
    Key.Enter -> {
        onDaySelectedFromGrid(selectedDate)
        onViewModeChange(CalendarViewMode.WEEK)
        true
    }
    else -> false
}
CalendarViewMode.WEEK -> when (key) {
    Key.DirectionDown -> { onSelectDate(selectedDate.plusDays(1)); true }
    Key.DirectionUp   -> { onSelectDate(selectedDate.minusDays(1)); true }
    Key.Enter -> {
        onDaySelectedFromGrid(selectedDate)
        onViewModeChange(CalendarViewMode.DAY)
        true
    }
    else -> false
}
```

**Step 2: Verify compile**

Run: `gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 9: Final Polish — Animations & Empty States

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarMonthView.kt`
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/CalendarWeekView.kt`

**Step 1: Add AnimatedContent transition on view mode switch in CalendarScreen**

Wrap the `when (calendarViewMode)` block:

```kotlin
AnimatedContent(
    targetState = calendarViewMode,
    transitionSpec = {
        fadeIn(tween(WallAnimations.MEDIUM)) togetherWith
        fadeOut(tween(WallAnimations.SHORT))
    },
    label = "calendarViewMode"
) { mode ->
    when (mode) {
        CalendarViewMode.MONTH -> CalendarMonthView(...)
        CalendarViewMode.WEEK  -> CalendarWeekView(...)
        CalendarViewMode.DAY   -> CalendarDayView(...)
    }
}
```

**Step 2: Handle loading/error state for empty eventsForRange**

In `CalendarMonthView` and `CalendarWeekView`, ensure graceful rendering when `eventsForRange` is empty (e.g., `eventsForRange[date] ?: emptyList()` already handles this — confirm it's in place).

**Step 3: Verify compile and build**

Run: `gradlew assembleDebug`
Expected: BUILD SUCCESSFUL with APK produced

---

## Implementation Order Summary

1. `CalendarViewMode.kt` (new file, trivial)
2. `GoogleCalendarRepository.kt` (add range fetcher)
3. `TaskWallViewModel.kt` (state + logic)
4. `CalendarMonthView.kt` (new file, full component)
5. `CalendarWeekView.kt` (new file, full component)
6. `CalendarScreen.kt` (wire routing + sub-switcher + key events)
7. Call site wiring (TaskWallScreen or MainActivity)
8. Encoder key events in CalendarScreen
9. AnimatedContent polish

---

## Color / Theme Reference

All colors come from `LocalWallColors.current`:
- `accentPrimary` — slate blue, used for selected/today highlights
- `urgencyWarm` — muted amber/terracotta, for promoted task events
- `textPrimary` — main readable text
- `textSecondary` — subdued labels (times, day headers)
- `textDisabled` — muted padding-month numbers, "no events"
- `surfaceCard` — slightly elevated card surface
- `surfaceExpanded` — lighter surface for week event chips
- `borderColor` — subtle border for separators
- `surfaceBlack` — deep background

`WallAnimations.MEDIUM` and `WallAnimations.SHORT` are existing animation duration constants.
