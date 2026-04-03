# Auto-Dev Plan: Day Organizer Entry Points

**Branch:** `day-organizer-entry-points`
**Spec:** `docs/superpowers/specs/2026-04-02-day-organizer-entry-points-design.md`

---

## Task Overview

| # | Task | Files | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | CalendarScreen Plan Day header button | CalendarScreen.kt | None | pending |
| 2 | SettingsPanel "Plan My Day" action | SettingsPanel.kt, TaskWallScreen.kt, CalendarScreen.kt, MainActivity.kt, TaskWallViewModel.kt | None | pending |
| 3 | "Add by Voice" in task context menu | TaskWallScreen.kt | None | pending |
| 4 | Visual affordance in CalendarDayView | CalendarDayView.kt | None | pending |
| 5 | First-use discovery hint | CalendarScreen.kt, TaskWallViewModel.kt, MainActivity.kt | Task 1 (needs Plan Day button to exist) | pending |

**Parallelizable:** Tasks 1, 2, 3, 4 are independent (different primary files). Task 5 depends on Task 1.

---

## Task 1: CalendarScreen Plan Day Header Button

**Goal:** Add encoder-navigable "Plan Day" button in CalendarScreen header, between Settings and ViewSwitcher.

**Changes to `CalendarScreen.kt`:**

1. Add focus state variable: `var isPlanDayFocused by remember { mutableStateOf(false) }`

2. Update key handling for Settings → PlanDay → ViewSwitcher focus chain:
   - When `isSettingsFocused` and Down/Right pressed: set `isPlanDayFocused = true` (instead of `isViewSwitcherFocused`)
   - Add new `isPlanDayFocused` key handler block:
     - Up/Left → `isSettingsFocused = true`
     - Down/Right → `isViewSwitcherFocused = true`
     - Enter/Space → `startDayOrganizerWithPermission()`
   - When `isViewSwitcherFocused` and Up/Left: set `isPlanDayFocused = true` (instead of `isSettingsFocused`)

3. Add `PlanDayButton` composable (private, same file):
   - Same style as `CalendarSettingsButton` (48dp box, rounded, glow on focus)
   - Icon: `Icons.Outlined.Mic` (same mic icon as VoiceFab, since it triggers voice-based planning)
   - Label: contentDescription = "Plan your day"
   - Only rendered when `geminiKeyPresent && voiceStateIdle && dayOrganizerState is DayOrganizerState.Idle && hasCalendarScope`

4. Render in header Row between CalendarSettingsButton and ViewSwitcherPill (line ~750-772)

5. Update MONTH/WEEK/THREE_DAY/DAY "up to header" navigation to use `isPlanDayFocused` where appropriate (or keep going to `isViewSwitcherFocused` — the chain will handle it)

**Assumption:** Reuse `Icons.Outlined.Mic` since this triggers voice input for day planning. Using `EditCalendar` would need material-icons-extended dependency which may not be present.

---

## Task 2: SettingsPanel "Plan My Day" Action

**Goal:** Add a "Plan My Day" item to SettingsPanel that switches to Calendar DAY view and starts day organizer.

**Changes to `SettingsPanel.kt`:**

1. Add `PLAN_DAY` to `SettingsItemType` enum (at the top, before THEME_MODE)
2. Add parameter: `onPlanDay: () -> Unit = {}`
3. Add parameter: `geminiKeyPresent` is already there
4. Add parameter: `hasCalendarScope: Boolean = false`
5. In `items` list builder: if `geminiKeyPresent && hasCalendarScope`, add `PLAN_DAY` at index 0
6. Render PLAN_DAY item as a simple action row: "Plan My Day" with accent color, mic icon
7. On Enter/Space when focused on PLAN_DAY: call `onPlanDay()`

**Changes to `TaskWallScreen.kt`:**
- Pass new `onPlanDay` callback through SettingsPanel call site
- Add `onPlanDay: () -> Unit = {}` parameter to TaskWallScreen
- Add `hasCalendarScope: Boolean = false` parameter (or derive from existing props)

**Changes to `CalendarScreen.kt`:**
- Same for CalendarScreen's SettingsPanel call site

**Changes to `MainActivity.kt`:**
- Wire `onPlanDay` for TaskWallScreen: dismiss settings → switch to calendar page → set DAY mode → start day organizer
- Wire `onPlanDay` for CalendarScreen: dismiss settings → start day organizer (already on calendar page)

**Changes to `TaskWallViewModel.kt`:**
- Add `fun planDayFromTaskWall()` that sets calendar view mode to DAY and starts day organizer
- Or handle this in MainActivity by composing existing functions

---

## Task 3: "Add by Voice" in Task Context Menu

**Goal:** Add "Add by Voice" to the incomplete-task context menu in TaskWallScreen.

**Changes to `TaskWallScreen.kt`:**

1. In `contextMenuActions` remember block (line ~367), add to the `buildList` for incomplete tasks:
   ```kotlin
   if (geminiKeyPresent) {
       add(TaskContextMenuAction(id = "voice", label = "Add by Voice"))
   }
   ```
   Insert before the "delete" action (last item, before destructive actions).

2. In the context menu action handler (where `action.id` is matched), add case for `"voice"`:
   - Dismiss context menu
   - Call `startVoiceWithPermission()`

3. Ensure `geminiKeyPresent` is available in scope (it's already a parameter of TaskWallScreen).

---

## Task 4: Visual Affordance in CalendarDayView

**Goal:** Show subtle "Plan your day" text in first empty morning slot.

**Changes to `CalendarDayView.kt`:**

1. Add parameters:
   - `geminiKeyPresent: Boolean = false`
   - `dayOrganizerIdle: Boolean = true`

2. In the slot rendering logic (around line 678-692), for empty hour marks in the 7-10 AM range:
   - If `geminiKeyPresent && dayOrganizerIdle` and this is the FIRST empty morning slot:
   - Instead of just a divider, show faded "Plan your day" text
   - Use `colors.textMuted.copy(alpha = 0.35f)`, small font (`labelSmall`)
   - Track via a `var morningHintShown` flag to only show once per render

3. Wire new params from CalendarScreen → CalendarDayView call site.

4. Wire from MainActivity → CalendarScreen (geminiKeyPresent already passed, dayOrganizerState already passed).

---

## Task 5: First-Use Discovery Hint

**Goal:** One-time hint near Plan Day button on first calendar visit with Gemini key.

**Changes to `TaskWallViewModel.kt`:**
1. Add DataStore key: `private val hasSeenPlanDayHintKey = booleanPreferencesKey("has_seen_plan_day_hint")`
2. Add StateFlow: `val hasSeenPlanDayHint: StateFlow<Boolean>`
3. Add function: `fun dismissPlanDayHint()` — sets preference to true
4. Load in `loadSettings()`

**Changes to `CalendarScreen.kt`:**
1. Add parameters: `hasSeenPlanDayHint: Boolean = true`, `onDismissPlanDayHint: () -> Unit = {}`
2. When Plan Day button is visible AND `!hasSeenPlanDayHint`:
   - Show subtle text below header: "Navigate here & click to plan your day"
   - Use `textMuted.copy(alpha = 0.5f)`, `labelSmall`
   - LaunchedEffect auto-dismiss after 8 seconds
   - Dismiss on any key event
   - Call `onDismissPlanDayHint()` on dismiss
3. Also dismiss when day organizer starts (in the `startDayOrganizerWithPermission` block)

**Changes to `MainActivity.kt`:**
- Wire `hasSeenPlanDayHint` and `onDismissPlanDayHint` to CalendarScreen

---

## Execution Order

1. **Parallel batch:** Tasks 1, 2, 3, 4
2. **Sequential:** Task 5 (after Task 1 completes)
3. **Build check** after each task
4. **Code review** after each task
