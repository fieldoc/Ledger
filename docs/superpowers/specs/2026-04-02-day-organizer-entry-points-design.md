# Day Organizer Entry Points — Design Spec

**Date:** 2026-04-02
**Status:** Final
**Feature:** Make day organizer and voice input reachable from the physical encoder

---

## 1. Problem Statement

The day organizer exists but is unreachable from the physical encoder (3 inputs: CW, CCW, click). Hold detection was removed because it doesn't work on the ESP32 BLE HID hardware. The voice FAB mentioned in the original day organizer spec was never implemented as an encoder-navigable button. Users cannot plan their day without a touchscreen.

Similarly, while TaskWallScreen has a voice button in the header, CalendarScreen has no voice entry point, and the task context menu lacks a "voice add" option for users who are already focused on a specific task.

## 2. Features

### 2.1 CalendarScreen "Plan Day" Header Button

**What:** Add a "Plan Day" button in the CalendarScreen header area, alongside the existing Settings button and ViewSwitcher pill.

**Why this over context menu on date header:** The date bar already has complex focus behavior (editing mode, calendar selection). Adding double-click would conflict. A header button follows the established pattern from TaskWallScreen's VoiceShortcutButton.

**Behavior:**
- Circular button with calendar-plus icon (Icons.Outlined.EditCalendar or similar)
- Appears only when: Gemini key is configured AND voice state is idle AND not in ambient mode
- Focus order: Settings → **PlanDay** → ViewSwitcher
- Click: Request audio permission, then call `onStartDayOrganizer()`
- Visual: Same style as CalendarSettingsButton (48dp circle, glow on focus)

**Assumption:** Using `Icons.Outlined.EditCalendar` for the Plan Day icon (falls back to `Icons.Outlined.CalendarMonth` if not available in the Material icons set).

### 2.2 TaskWallScreen "Plan Day" in Settings Panel

**What:** Add a "Plan Day" action to the Settings panel under a new "ACTIONS" section at the top.

**Why settings panel over context menu:** The view switcher doesn't have a double-click pattern. Adding it to settings is discoverable and doesn't add encoder navigation complexity. Users already know how to open settings (navigate to button, click).

**Behavior:**
- New "ACTIONS" section at top of SettingsPanel
- Single item: "Plan My Day" with calendar icon
- Only visible when Gemini key is configured
- Click: Dismiss settings → switch to Calendar page (DAY view) → start day organizer
- Callback: `onPlanDay: () -> Unit` passed through SettingsPanel

### 2.3 "Add by Voice" in Task Context Menu

**What:** Add "Add by Voice" as a context menu action on tasks in TaskWallScreen.

**Why:** Provides an alternative voice entry point without needing to navigate to the header button. When you're already focused on a task list area, double-click → "Add by Voice" is faster.

**Behavior:**
- New action: `TaskContextMenuAction(id = "voice", label = "Add by Voice")`
- Added to the INCOMPLETE task context menu actions (after existing items)
- Click: Dismiss context menu → start voice input (same as header voice button)
- Only shown when Gemini key is configured (voice parsing needs it)

**Assumption:** Voice parsing requires Gemini key. If no key, this action is hidden.

### 2.4 Visual Affordance in CalendarDayView

**What:** In DAY view, show a subtle "Plan your day" hint in empty morning slots.

**Behavior:**
- Appears in the first empty morning slot (between 7:00-10:00 AM range)
- Only shows when: Gemini key configured, day organizer is idle, no events in that slot
- Visual: Faded text "Plan your day" in `textMuted` color at 0.4 alpha + small waveform icon
- Not focusable or interactive — purely visual whisper
- Disappears when any event exists in the morning or day organizer has been used today

**Assumption:** "Morning" defined as 7:00-10:00 AM. The hint picks the first empty slot in that range.

### 2.5 First-Use Discovery Hint

**What:** One-time hint near the Plan Day button on first calendar visit with Gemini key configured.

**Behavior:**
- DataStore preference: `has_seen_plan_day_hint` (boolean, default false)
- On CalendarScreen composition, if hint not seen AND Gemini key present: show a subtle text below the Plan Day button
- Text: "Navigate here & click to plan your day" in `textMuted` at 0.5 alpha
- Auto-dismisses after 8 seconds OR on any encoder input OR after first day organizer use
- Sets `has_seen_plan_day_hint = true` permanently
- No animations, no overlays — just quiet text that fades in and out

**Assumption:** 8 seconds is long enough to read while glancing at the wall.

## 3. Encoder Navigation Changes

### CalendarScreen Focus Order (updated)
```
Settings Button → Plan Day Button → View Switcher → Date Bar → [view-specific content]
```

### TaskWallScreen (unchanged)
Context menu gets one more item ("Add by Voice") but navigation pattern is identical.

## 4. Files to Modify

| File | Change |
|------|--------|
| `CalendarScreen.kt` | Add PlanDayButton to header, focus state, key handling |
| `TaskWallScreen.kt` | Add "Add by Voice" to context menu actions |
| `SettingsPanel.kt` | Add ACTIONS section with "Plan My Day" |
| `CalendarDayView.kt` | Add visual affordance in empty morning slots |
| `TaskWallViewModel.kt` | Add `hasSeenPlanDayHint` DataStore pref, `planDayFromTaskWall()` |
| `MainActivity.kt` | Wire new callbacks (onPlanDay for settings) |

## 5. Non-Goals

- No new voice FAB — replaced by header button
- No phone entry point (Phase 2 per original spec)
- No changes to the day organizer state machine itself
- No changes to the DayOrganizerOverlay or plan preview UI
