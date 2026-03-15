# Weather in Zoomed Calendar Views

**Date:** 2026-03-15
**Status:** Implemented

## Problem

Weather forecast data (`Map<LocalDate, WeatherCondition>`) was only surfaced in Month and Week views as subtle background tints. The Day and 3-Day zoomed views — where users spend the most time planning — had no weather awareness at all. The larger real estate in these views could convey more detail while staying unobtrusive.

## Constraints

- **Data**: Daily-level `WeatherCondition` enum only (icon + tintColor). No temperature, no hourly breakdown. API call excludes hourly data.
- **Hardware**: Rotary encoder with 3 inputs (CW, CCW, click). No keyboard shortcuts.
- **Philosophy**: "Whisper, don't shout." Weather is ambient context, not primary content.

## Design

### Day View — Weather Context Strip

A slim, full-width strip between the date bar and the time slots in the LazyColumn.

**Default (collapsed):**
- ~32dp tall card with left accent bar in strengthened tint color
- Weather icon (titleSmall size) + condition name ("Clear", "Rain", etc.) in labelMedium
- Background uses tintColor at ~2.5x normal alpha (still very subtle, max 15%)
- `+`/`−` indicator on the right edge hints at expandability

**Expanded (on click/touch):**
- Reveals a 3-day mini-forecast row (today + next 2 days) below the strip
- Each day gets a small card with: day label, icon, condition name
- Today's card uses accentPrimary for the label; others use textSecondary
- AnimatedVisibility with expand/fade for smooth transition
- Uses the same `weatherForecast` map — no new data or API calls needed

### 3-Day View — Column Header Integration

- Weather icons added directly beneath the day number in each column header
- Column header background gets the tintColor wash at ~2x alpha (max 12%)
- PARTLY_CLOUDY is skipped (no icon, no tint) to avoid noise on neutral days
- No click-to-expand needed — 3 days of weather are already visible side-by-side

## Files Changed

- `WeatherContextStrip.kt` — New shared component (strip + mini-forecast)
- `CalendarDayView.kt` — Added `weatherForecast`, `isWeatherExpanded`, `onToggleWeatherExpanded` params; renders strip as first LazyColumn item
- `Calendar3DayView.kt` — Added `weatherForecast` param; icons + tint in column headers
- `CalendarScreen.kt` — Added `isWeatherExpanded` state; passes weather params to both views

## What Was Not Done

- No temperature display (data not available without changing API exclude params)
- No hourly weather breakdown (excluded from OWM API call)
- No weather overlay on individual time slots (would conflict with event chips)
- No always-visible multi-day forecast in day view (too much visual weight — expand on demand instead)
