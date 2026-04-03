# ToDoWallApp — Comprehensive Feature Review

**Date**: 2026-03-14
**Reviewed by**: 7 parallel analysis agents across 7 lenses

---

## Executive Summary

The app's foundation is solid — MVVM architecture, Google Tasks/Calendar API integration, rotary encoder navigation, ambient modes, and voice capture are all structurally in place. However, several specced features are incomplete or missing their wall-screen UI, and a handful of kiosk-critical gaps (burn-in protection, error dismissal, encoder accessibility) need attention before this is truly "set and forget" on a wall.

**Scorecard:**
- Spec Fidelity: **4 PASS, 1 PARTIAL, 4 FAIL** out of 9 binding specs
- Input Completeness: **3 critical features unreachable** via encoder
- Core Loop: **2 trust-breaking bugs** (100-task cap, subtask collapsing)
- Failure Recovery: **2 kiosk-breaking gaps** (undismissable errors, no reconnect sync)
- Visual: **Card contrast near zero**, several text elements too small for wall
- Ambient/Kiosk: **Burn-in protection is dead code**
- Feature Gaps: Voice Phase 2 is built; task reorder and multi-account are not

---

## 1. Input Completeness (Encoder: 3 Inputs Only)

The rotary encoder sends exactly 3 keycodes: LEFT_ARROW (CCW), RIGHT_ARROW (CW), ENTER (click).

### What Works via Encoder
| Feature | Status |
|---------|--------|
| Navigate tasks up/down | Works |
| Expand/collapse accordion folders | Works |
| Complete/uncomplete tasks | Works (click for children, double-click for parents) |
| Open settings | Works (SETTINGS_BUTTON in focus order) |
| Navigate all settings items | Works |
| Switch to calendar view | Works (ViewSwitcherPill focusable) |
| Calendar date/event navigation | Works (all modes) |
| Calendar event action menu | Works |
| Voice input | Works (header voice button, single click) |
| Context menu on task | Works (double-click on focused task) |
| Wake from ambient | Works (any input) |

### DEAD Features (No Encoder Access)

| Feature | Problem | Recommendation |
|---------|---------|----------------|
| **Voice draft card / confirmation** | `VoiceInputState.Preview` exists in model but NO wall UI renders it. Voice input is a dead-end — speech is captured but can never be confirmed. | **P0**: Implement draft card per Design Spec #6 |
| **Undo after task completion** | `UndoToast` component exists but is **never rendered** in the UI tree. Even if rendered, it's touch-only. | **P0**: Render UndoToast, add encoder focus node during undo window |
| **Context menu (restore/delete)** | ~~Requires long-click (touch only)~~ **FIXED 2026-04-02**: Double-click on focused task opens context menu. | ~~P1~~ Done |
| **Settings panel close** | No "Close" item in settings list. Dismissal requires Android back button or touch. | **P2**: Add "Close" item at top/bottom of settings list |
| **Voice FAB button** | ~~Touch-only, not in focus order~~ **FIXED 2026-04-02**: Replaced with header voice button in encoder focus order. | ~~Acceptable~~ Done |

### Unreachable but Harmless Key Handlers
- `Key.DirectionUp` — aliased with reachable keys throughout
- `Key.Spacebar` — aliased with Enter throughout
- `Key.NumPadEnter` — aliased with Enter throughout
- `Key.Escape` in CalendarScreen — mitigated by `else` catch-all branch

---

## 2. Core Loop & Sync Integrity

### Sync Mechanism
- **Auto-sync**: `while(true)` loop with configurable interval (default 5 min), persisted to DataStore
- **Exponential backoff**: `baseDelay * 2^failures`, capped at 3 failures (max 40-minute interval)
- **Trigger points**: Auto-sync timer, manual refresh, after voice task creation, initial sign-in load
- **Parallel fetch**: All task lists fetched concurrently via `coroutineScope { async {} }`

### Optimistic Updates
- Task completion: saves original → optimistic UI update → API call → revert on failure
- Task deletion: optimistic remove → API call → full refresh on failure
- Undo: 5-second window, properly cancels timer on explicit undo

### CRITICAL Issues

| Issue | Impact | Details |
|-------|--------|---------|
| **100-task pagination cap** | Tasks silently lost | `setMaxResults(100)` with no `nextPageToken` handling. Users with >100 tasks in a list lose visibility of overflow tasks. Directly violates the "wall holds everything" promise. |
| **Subtasks collapsed by default** | Mental load not externalized | Children only visible when `expandedParentId == group.parent.id`. Requires encoder interaction to reveal. Spec says "never collapsed or hidden." |
| **Auto-sync collapses subtasks** | Context destroyed | `if (isSyncing && expandedParentId != null) { expandedParentId = null }` — every 5-minute sync closes any expanded subtask group. |
| **No staleness indicator** | False trust | If offline for hours, wall shows stale data with no visual warning. `lastSyncSuccess` auto-clears after 3 seconds. |
| **No auth token recovery** | Silent degradation | If Google token expires mid-session, API calls fail with exponential backoff. No code path to re-authenticate. Wall goes stale without explanation. |
| **Mutex skip returns success** | Masked failures | `if (refreshMutex.isLocked) return true` — concurrent refresh requests falsely report success. |
| **`showHidden(false)` hides completed subtasks** | Incomplete hierarchy | Completed subtask progress may undercount. |
| **Double API call for completion** | Latency | `completeTask()` GETs then PATCHes (2 calls). A direct PATCH would halve latency. |

### Undo Race Condition
If the undo timer fires *before* an API failure response arrives, undo state disappears. The revert still works, but the user sees the undo bar vanish and then the task flip back — confusing on a wall device.

---

## 3. Ambient & Kiosk Behavior

### Implementation Status

| Feature | Spec | Status | Notes |
|---------|------|--------|-------|
| Quiet Mode (30s idle) | Show 2-3 tasks faintly | **Built** | Shows top 3 by urgency, 42% alpha |
| Sleep Mode (schedule) | True black, configurable hours | **Built** | Alpha=0, defaults 23:00-07:00, persisted to DataStore |
| Sleep Mode (ambient light) | Enter sleep when room dark | **Built** | Uses TYPE_LIGHT sensor, <10 lux for 30s triggers transition |
| Encoder wake from ambient | Instant wake | **Built** | `wakeUp()` on all inputs + tap-to-wake overlay |
| Sync during sleep | Continue syncing | **Built** | Auto-sync runs independently of ambient tier |
| Kiosk immersive mode | Fullscreen, no system bars | **Built** | Dual-API approach (modern + deprecated flags), re-asserted on focus change |
| Screen always on | FLAG_KEEP_SCREEN_ON | **Built** | Set for WALL mode only |
| Memory stability | No leaks over weeks | **Acceptable** | Clean lifecycle, no accumulation, stateless repository |

### CRITICAL Issues

| Issue | Risk Level | Details |
|-------|------------|---------|
| **Anti-burn-in offset is dead code** | **HIGH** | `ambientPromptOffsetXDp/YDp` are computed every 180s but **never applied to any Modifier**. Quiet mode content stays at the same fixed position forever. One-line fix: apply as `Modifier.offset()`. |
| **PageIndicator dots always visible** | **HIGH** | Rendered in `WallModeContent` (MainActivity) **outside** `TaskWallScreen`, unaffected by `screenAlpha` dimming. Dots persist even during sleep mode. Will burn into OLED. |
| **No hardware brightness control** | **MEDIUM** | Sleep mode only sets Compose alpha to 0. LCD backlight stays on. Need `WindowManager.LayoutParams.screenBrightness = 0f` for LCD panels. OLED is fine (black pixels = off). |
| **No wake-on-light** | **LOW** | Light sensor only transitions TO quiet/sleep, never FROM. If room lights come on, device stays asleep until encoder input. |
| **Sleep-to-Quiet transition missing** | **LOW** | When sleep schedule ends (e.g., 7am), device stays in SLEEP until encoder input. No auto-demotion to Quiet. |
| **Clock timer runs during sleep** | **TRIVIAL** | `while(true) { delay(1000) }` in ClockHeader runs when clock isn't visible. Harmless but wasteful. |

### Active-Mode Burn-In Risks
- ClockHeader (fixed corner position, date is static for hours)
- ViewSwitcherPill ("Tasks / Calendar" — fixed top-right)
- Settings gear icon (fixed position, hidden during ambient — lower risk)
- Folder section borders and card backgrounds during extended active use

**Recommendation**: Add 1-2dp global pixel shift every few minutes in active mode (standard for always-on displays).

---

## 4. Failure & Recovery

### Error Handling Architecture
- **Repository layer**: All methods return `Result<T>`, errors logged via `Log.e/w`. Consistent, no swallowing.
- **ViewModel layer**: Sets `uiState.error` on failures, `lastSyncSuccess` for sync status (auto-clears in 3s).
- **Auth layer**: Silent sign-in with 3s timeout, falls back to `NotAuthenticated`.
- **Voice layer**: Maps all SpeechRecognizer error codes to human-readable messages, Gemini parse failures fall back to raw text.

### User-Visible Error States

| Error | What User Sees | Dismissal |
|-------|---------------|-----------|
| Offline | "OFFLINE" badge + red dot in ClockHeader | Auto-clears on reconnect |
| Sync failure | ErrorBadge with message text | **Touch only** (`onDismiss`) |
| Calendar error | Error text in CalendarScreen | Via `clearCalendarError()` |
| Voice error | VoiceInputState.Error with message | Dismiss button |
| Auth expired | Sign-in screen | Tap sign-in button |

### CRITICAL Gaps

| Gap | Impact | Fix |
|-----|--------|-----|
| **ErrorBadge undismissable via encoder** | On a wall kiosk, error messages persist **forever** until someone physically taps the screen. | Auto-dismiss after 10s, or add encoder-navigable dismiss. |
| **No reconnect-triggered sync** | When WiFi returns, app does NOT immediately re-sync. Waits for next auto-sync cycle (up to 40 min with backoff). `consecutiveSyncFailures` never resets without a successful sync. | Collect `isOnline` flow, trigger refresh + reset failures on `false→true` transition. |
| **DataStore read has no error handling** | `loadSettings()` in `init {}` has no try/catch. Corrupted DataStore crashes the ViewModel at construction — app becomes unusable. | Wrap in try/catch with defaults, or use `.catch { emit(emptyPreferences()) }`. |
| **No auth token expiry detection** | No 401/`UserRecoverableAuthIOException` detection. Expired tokens produce generic "Failed to load" errors with no recovery path. | Detect 401, attempt `silentSignIn()` + re-initialize, retry once. |
| **No auto-dismiss for voice errors** | Voice error overlay persists indefinitely if no one is nearby. | Auto-return to Idle after 5s. |

### Silent Failure Modes
- `loadTasksForAllLists()` shows only first error when multiple lists fail — others silently lost
- `parseDateTime()` returns `LocalDateTime.now()` on parse failure — wrong dates with only a log warning
- `findTaskListIdForTask()` silently falls back to `selectedTaskListId` for orphaned tasks — API calls could target wrong list
- All DataStore writes (sleep schedule, sync interval, theme, calendar date) have no try/catch
- Auto-sync failures during ambient mode are hidden (`ErrorBadge` hidden when `isAmbientMode`)

---

## 5. Spec Fidelity (9 Binding Design Specs)

| # | Spec | Verdict | Key Evidence |
|---|------|---------|--------------|
| 1 | Completed tasks drift to bottom (~300ms ease-in-out) | **FAIL** | No positional/reorder animation. Only opacity changes (checkmark alpha fade). No `animateItemPlacement`. |
| 2 | Focus state = gentle glow (soft halo, not border) | **PASS** | `shadow(elevation=24.dp, spotColor=accentPrimary@0.3f)` + rim gloss. Minor: also has a 1dp border highlight (not in spec). |
| 3 | Subtasks indented + connecting line (always visible) | **FAIL** | No vertical connecting line drawn (isChild param unused). Subtasks collapsed by default behind `expandedParentId`. Spec says "never collapsed." |
| 4 | Accordion folders sorted by urgency (peek preview, bloom) | **PARTIAL** | Sorting correct. Accordion works. Missing: collapsed peek preview (no first-task text shown). Uses spring physics instead of spec's ~300ms ease-in-out. |
| 5 | Voice = full-screen dim + waveform (no live transcription) | **FAIL** | `WaveformVisualizer` component exists but is **never rendered** on wall screen. No dim overlay, no visual feedback during voice. |
| 6 | Voice confirmation = draft card (no auto-commit) | **FAIL** | `VoiceInputState.Preview` data model exists. `onConfirmVoice`/`onCancelVoice` callbacks wired. But NO wall UI renders the draft card. Phone mode has it; wall mode doesn't. |
| 7 | Clock = small corner utility (under ~48dp) | **PASS** | Compact header row. ~60dp actual height (slightly over spec due to padding), but functionally a utility element, not a dominating header. |
| 8 | Urgency = warm temperature shift (muted amber, not red) | **PASS** | `UrgencyOverdue=#C97C52` (terracotta), `UrgencyDueToday=#D4A06A` (amber), `UrgencyDueSoon=#B8A88A` (sand). No red anywhere. Correct. |
| 9 | Ambient = two-tier system (quiet 30s, sleep schedule) | **PASS** | Quiet at 30s (3 tasks, 42% alpha). Sleep on schedule + light sensor. Encoder wakes. All working. |

---

## 6. Visual Hierarchy & Readability

### Typography Scale

**Font**: Plus Jakarta Sans (Google Fonts). Good UI font, moderate x-height. Not optimized for distance reading like Inter or signage fonts.

| Element | Size | Wall Readable (6-8ft)? |
|---------|------|----------------------|
| Quiet mode clock | 32sp | Marginal — struggles at 8ft |
| Clock header time | 24sp (headlineMedium) | **Too small** — competes with task titles |
| Task titles | 22sp (titleLarge) | Adequate — minimum viable |
| Empty state heading | 24sp | Adequate |
| Empty state body | 18sp | Marginal |
| Action pill labels | 16sp | Marginal |
| Folder headers | 16sp (labelLarge) | **Too small** |
| Task notes | 16sp (bodyMedium) | **Too small** for distance (secondary info) |
| Date text | 14sp (labelMedium) | **Too small** — unreadable at 6ft |
| Due date badges | 12-14sp | **Too small** — urgency info should be prominent |
| Sync status | 12sp (labelSmall) | Acceptable as non-critical info |
| Task count badge | 10sp | **Unreadable** from wall distance |
| Breadcrumb | 9sp | **Invisible** from distance |
| "COMPLETED" label | 9sp | **Invisible** from distance |
| "Overdue" whisper | 8sp | **Invisible** from distance |
| Expand indicator | 8sp | **Invisible** — decorative only |

### Color & Contrast

| Surface Pair | Contrast Ratio | Assessment |
|-------------|---------------|------------|
| Text Primary (#EEEEEE) vs Background (#121212) | ~16:1 | Excellent |
| Text Secondary (#BDBDBD) vs Background | ~11:1 | Good |
| Text Muted (#757575) vs Background | ~4.4:1 | Passes AA for large text only |
| Text Disabled (#555555) vs Background | ~2.7:1 | **Fails WCAG AA** |
| Card (#1C1C1C) vs Background (#121212) | **~1.15:1** | **Nearly indistinguishable** |
| Border (#1E1E1E) vs Background (#121212) | ~1.1:1 | **Nearly invisible** |

### Critical Visual Issues

1. **Card-to-background contrast is nearly zero.** Unfocused cards are transparent; focused cards use #1C1C1C on #121212 (~1.15:1). From distance, tasks float in an undifferentiated dark field. Recommendation: raise unfocused card to at least #222222 or add visible resting border.

2. **Focus glow relies on platform shadow.** `Modifier.shadow()` produces inconsistent results across GPUs — some render almost no glow. A custom `drawBehind` with radial gradient would be more reliable for the "luminous halo visible from 6ft" spec.

3. **Completed tasks at 0.35 alpha + #555555 text** = essentially invisible on the wall. Intentional for de-emphasis but may be too aggressive.

4. **`fontScale` defined but unused.** `LayoutDimensions.fontScale = 0.92f` for landscape exists but is never consumed by any text composable.

### Urgency Colors (Well Done)
The terracotta/amber palette is excellent — warm temperature ramp from overdue to due-soon without triggering stress. `accentWarm` (#CF8E8E, soft rose) for completed state is a nice touch.

---

## 7. Feature Gaps & Natural Extensions

### Specced But Unbuilt

| Feature | Status | Priority |
|---------|--------|----------|
| Voice Phase 2 (AI parsing) | **BUILT** (single-task only) | Multi-task utterances not supported |
| Ambient light sensing | **BUILT** (hardware sensor, better than spec's camera approach) | Complete |
| Task reordering from wall | **NOT BUILT** | Low — ordering is secondary to capture |
| Multi-account support | **NOT BUILT** | Low — niche for a personal wall |
| Text-based task creation on wall | **NOT BUILT** | Low — impractical with encoder hardware |

### "What If...?" Feature Extensions

#### 1. Gemini-Powered Day Organizer (**HIGHEST IMPACT**)

**Concept**: A conversational morning routine. Talk to the wall about what you want to do today. The AI pulls your existing tasks/calendar, suggests things you should do (exercise, chores, errands — things that are good for you but take mental load to schedule), negotiates time blocks conversationally, and produces a finalized day schedule in the calendar.

**Why this matters**: The core insight — "the chaos isn't that you don't know what to do, it's that fitting it all together takes executive function." This feature directly attacks scheduling Tetris, the hardest part for ADHD/chaotic brains. The AI handles the cognitive overhead of time-blocking so you don't have to.

**Implementation**:
- New state machine: `OrganizerState` (Idle → Listening → AIThinking → ProposalShown → NegotiatingSlot → Finalized)
- Extends `GeminiCaptureRepository` with multi-turn conversation support
- New Compose overlay showing proposed time blocks as cards (accept/reject/modify via encoder)
- Final confirmation creates Google Calendar events
- Leverages all existing APIs (Gemini, Google Tasks, Google Calendar)

**Complexity**: Large | **Risk**: Multi-turn Gemini latency could feel sluggish

#### 2. Weather-Tinted Calendar Days (**PASSIVE, HIGH-GLANCE VALUE**)

**Concept**: Calendar month/week cells show weather forecast as a subtle color tint. Warm golden = sunny, cool blue-grey = rainy, soft white = snow. Helps plan outdoor errands without adding visual clutter.

**Why this matters**: Removes the hidden mental load of "should I do this outdoor errand today or will it rain?" Passive ambient awareness — no interaction required.

**Implementation**:
- New `WeatherRepository` using OpenWeatherMap free tier (1000 calls/day)
- Returns `Map<LocalDate, WeatherCondition>` (SUNNY, CLOUDY, RAINY, SNOWY, STORMY)
- Subtle background tints in `CalendarMonthView`/`CalendarWeekView` cells
- New color tokens: very low-alpha weather tints (e.g., `Color(0x15FFD700)` for sunny)
- Settings: location config or auto-detect

**Complexity**: Small-Medium | **API**: OpenWeatherMap free tier + location permission

#### 3. "Next Action" Spotlight (**SMALL EFFORT, BIG IMPACT**)

**Concept**: In Quiet Mode, instead of showing 3 generic urgent tasks, show THE single most actionable task with rich context — what it is, which project, and when the next free calendar slot to work on it is. The wall whispers: "Your next move is..."

**Why this matters**: Decision paralysis. A chaotic brain looking at 30 tasks freezes. ONE clear next action eliminates the "what should I do now?" loop. Core GTD principle, especially powerful for ADHD.

**Implementation**: Modify existing `quietModeTasks` computation. Show 1 task with richer context (list name, due date, subtask count, next free slot from calendar). Mostly UI/logic changes to existing ambient mode.

**Complexity**: Small

#### 4. Habit Streak Dots (**GENTLE ACCOUNTABILITY**)

**Concept**: For recurring tasks (detected by identical titles weekly), show a subtle row of dots below the task — filled for weeks completed, hollow for missed. No numbers, no streaks, no celebrations. Just a quiet visual record.

**Why this matters**: "Am I actually doing this regularly?" Without tracking, you either worry about consistency (adding anxiety) or stop doing the thing. The dots provide a gentle, non-judgmental answer.

**Implementation**: Analyze completion history from Google Tasks API, pattern-detect recurring tasks, add small dot row to `TaskItem`. May need local persistence since Tasks API historical data may be limited.

**Complexity**: Medium

#### 5. Energy-Aware Task Ordering (**CIRCADIAN ADAPTATION**)

**Concept**: Subtly reorder/highlight tasks based on time of day. Morning: creative/thinking tasks float up. Post-lunch: routine/admin. Evening: low-energy tasks. User configures energy profile once (morning person / night owl).

**Why this matters**: "What should I work on right now?" — especially when energy is low and decision-making is depleted. The wall adapts to your rhythm so you don't have to think about task-energy matching.

**Implementation**: New setting for energy profile. Modify `sortTasksForDisplay()` to factor in time-of-day. Could use Gemini to classify tasks by energy requirement, or use list names as proxy.

**Complexity**: Medium

#### 6. Ambient Notification Digest (**PHONE-CHECKING REDUCTION**)

**Concept**: In Quiet Mode, show a faint digest of important notifications — missed calls, messages, calendar reminders. When fully active, notifications are hidden. "2 missed calls, 1 message from Mom."

**Why this matters**: The constant pull to check your phone — "did someone message me?" If the wall whispers the answer, you don't need to pick up the phone. Directly serves the "wall holds information so your brain doesn't have to" principle.

**Implementation**: Android `NotificationListenerService` (special permission required). Filter/categorize notifications. Render in Quiet Mode below tasks.

**Complexity**: Medium | **Concern**: Privacy — needs careful filtering for wall-visible content.

#### 7. Passive Package/Delivery Tracker (**AMBIENT AWARENESS**)

**Concept**: Small section showing incoming package deliveries with estimated arrival. Parsed from Gmail (shipping confirmations) or manual tracking numbers.

**Why this matters**: "When is that thing arriving?" — surprisingly high background anxiety for online shoppers.

**Complexity**: Large (email parsing is fragile) | **API**: Gmail API or AfterShip

---

## Priority Matrix

### P0 — Kiosk-Breaking (Fix Before Wall Deployment)

| Issue | Lens | Fix Effort |
|-------|------|------------|
| Voice draft card not rendered on wall | Input, Spec | Medium |
| UndoToast never rendered | Input | Small |
| ErrorBadge undismissable via encoder | Failure | Small |
| Anti-burn-in offset is dead code | Kiosk | Trivial (one line) |
| PageIndicator visible during sleep | Kiosk | Small |
| No reconnect-triggered sync | Failure | Small |
| DataStore read crash on corruption | Failure | Small |

### P1 — Trust & Reliability

| Issue | Lens | Fix Effort |
|-------|------|------------|
| 100-task pagination cap | Core Loop | Medium |
| Subtasks collapsed (spec says always visible) | Core Loop, Spec | Medium |
| Auto-sync collapses subtasks | Core Loop | Small |
| No auth token expiry recovery | Failure, Core Loop | Medium |
| No staleness indicator | Core Loop | Small |
| Card-to-background contrast ~1.15:1 | Visual | Small |

### P2 — Polish & Spec Compliance

| Issue | Lens | Fix Effort |
|-------|------|------------|
| No task drift-to-bottom animation | Spec | Medium |
| No vertical connecting line for subtasks | Spec | Small |
| No collapsed folder peek preview | Spec | Medium |
| Focus glow inconsistent across GPUs | Visual | Medium |
| Clock header too small (24sp) | Visual | Trivial |
| Many text elements too small for wall | Visual | Small |
| Context menu unreachable via encoder | Input | Medium |
| Settings panel has no close button | Input | Trivial |
| LCD brightness not controlled in sleep | Kiosk | Small |

### P3 — Feature Extensions (by Impact)

1. **Next Action Spotlight** — Small effort, directly reduces decision paralysis
2. **Day Organizer (Gemini)** — Highest impact but large effort; the dream feature
3. **Weather-Tinted Calendar** — Small-medium effort, perfect ambient awareness
4. **Energy-Aware Task Ordering** — Medium effort, circadian adaptation
5. **Community Events Discovery** — Medium-large effort, fights isolation, builds local connection
6. **Habit Streak Dots** — Medium effort, gentle accountability
7. **Notification Digest** — Medium effort, reduces phone-checking; privacy concerns
8. **Package Tracker** — Large effort, fragile implementation

---

## 8. Feature Extensions — Deep Dive

Each extension below is evaluated through the lens of the app's core philosophy: **the wall holds complexity so the mind doesn't have to**. For a feature to belong here, it must reduce a specific type of mental overhead passively, fit the calm aesthetic, and feel like a natural part of an always-on wall display — not a phone app bolted onto a screen.

---

### 8.1 — Gemini-Powered Day Organizer

> *"The chaos isn't that you don't know what to do — it's that fitting it all together takes executive function."*

#### The Problem It Solves

You wake up. You know you should work on your app. You know the groceries need doing. You should probably call your mom. The gym would be good. And there's that dentist appointment at 2pm. None of these are hard. But **arranging them into a coherent day** — that's the part that eats executive function. You sit down to plan and 20 minutes later you've reorganized your task list instead of starting anything.

This is scheduling Tetris: fitting variable-duration, variable-priority items into fixed time slots while respecting constraints (store hours, energy levels, travel time). Neurotypical brains do this semi-automatically. ADHD/chaotic brains burn enormous cognitive fuel on it — or avoid it entirely and wing it (leading to missed tasks and guilt).

#### The UX Flow

**Trigger**: A dedicated "Plan My Day" focus node that appears in the morning (configurable window, e.g., 6am-10am). The wall subtly pulses the node's glow during the planning window to invite interaction without demanding it. Activated via single click on the focus node.

**Phase 1 — The Wall Asks**

The screen dims (like voice mode). A calm, centered text appears:

```
What do you want to do today?
```

The waveform visualizer activates. The user speaks freely:

> "I want to work on my app for a couple hours, go to the gym, and maybe do some cleaning."

Gemini receives: the utterance + today's Google Calendar events + all pending tasks across lists + the current day/time + weather forecast (if weather extension is active).

**Phase 2 — The Wall Suggests**

The AI returns a proposed day plan as time blocks. The wall shows them as a vertical timeline of cards:

```
┌─────────────────────────────┐
│  8:00  Morning routine      │  (inferred)
│  9:00  Work on app          │  (from speech)
│ 11:00  Call Mom (15min)     │  (from tasks — suggested)
│ 11:30  Grocery run          │  (from tasks — suggested)
│  1:00  Lunch                │  (inferred)
│  2:00  Dentist              │  (from calendar — locked)
│  3:30  Gym                  │  (from speech)
│  5:00  Clean apartment      │  (from speech)
│  6:30  Free time            │  (buffer)
└─────────────────────────────┘
```

Key behaviors:
- **Calendar events are locked** (shown with a subtle lock icon, different card surface). The AI schedules around them.
- **Tasks from your lists are suggested** with a soft "from your tasks" label. The AI noticed "Call Mom" has been sitting in your list for a week and gently surfaces it.
- **Buffer time is inserted** between blocks. The AI doesn't pack the day wall-to-wall — it leaves breathing room, because a realistic plan is one you'll actually follow.
- **Duration estimates** come from Gemini's judgment (or user-specified: "a couple hours").

**Phase 3 — Negotiation**

The user can interact with the proposed plan using the encoder:
- **Rotate** to highlight a block
- **Click** to cycle through options for that block:
  - Accept (default)
  - Move earlier / Move later (AI re-shuffles around it)
  - Remove (AI fills the gap)
  - Change duration
- **Voice input** (via header button) to give verbal adjustments: "Actually, move the gym to morning" — Gemini re-proposes.

This negotiation loop continues until the user is satisfied. The wall shows a running "Looks good? Click to lock it in" prompt at the bottom.

**Phase 4 — Commit**

Encoder click on the confirmation prompt. The wall:
1. Creates Google Calendar events for each block (with the task title as event title)
2. Optionally links tasks to their calendar slots (so completing the task removes the event, or vice versa)
3. Smoothly transitions back to the normal task wall, which now shows today's calendar with the organized schedule
4. A brief "Day planned" confirmation fades in and out

**Phase 5 — Throughout the Day**

The wall's Quiet Mode adapts. Instead of showing "top 3 urgent tasks," it shows:
```
Now:  Work on app  (until 11:00)
Next: Call Mom     (11:00)
```

This is the "Next Action Spotlight" (Extension 8.3) working in concert with the day plan — the wall always knows what you should be doing right now.

#### Architecture

```
New files:
├── data/model/DayPlan.kt              # TimeBlock data class, DayPlanState sealed class
├── data/repository/DayOrganizerRepository.kt  # Multi-turn Gemini conversation
├── ui/screens/DayOrganizerOverlay.kt   # The planning overlay composable
└── ui/components/TimeBlockCard.kt      # Individual time block card

Modified files:
├── viewmodel/TaskWallViewModel.kt      # DayPlanState, organizer trigger, commit logic
├── ui/screens/TaskWallScreen.kt        # Overlay trigger, quiet mode adaptation
└── capture/repository/GeminiCaptureRepository.kt  # Extend with multi-turn support
```

**Gemini Prompt Design** (critical):

The prompt must include:
- Current date/time and timezone
- All calendar events for today (with times) — marked as immovable
- All pending tasks across all lists (with due dates, urgency)
- The user's spoken intent
- Weather forecast (if available) — influences outdoor activity scheduling
- Energy profile (if Extension 8.5 is active) — morning person vs. night owl
- Instructions: suggest 1-2 tasks the user didn't mention but should do, insert buffer time, respect meal times, be realistic about human energy

The response format should be structured JSON:
```json
{
  "blocks": [
    {"time": "09:00", "duration_min": 120, "title": "Work on app", "source": "speech", "task_id": null},
    {"time": "11:00", "duration_min": 15, "title": "Call Mom", "source": "suggested", "task_id": "abc123"}
  ],
  "suggestions_rationale": "I noticed 'Call Mom' has been on your list for 8 days..."
}
```

**Multi-Turn**: Unlike single-shot voice capture, this needs conversation context. Options:
1. **Gemini Chat API** (preferred) — maintains conversation state server-side
2. **Context accumulation** — append all prior utterances + AI responses to each new prompt (simpler, but token cost grows)

#### Edge Cases & Risks

| Risk | Mitigation |
|------|------------|
| Gemini latency (2-5s per turn) | Show a thinking animation between phases. Pre-fetch calendar/tasks before the user speaks. |
| Over-packed day proposals | Prompt engineering: instruct Gemini to leave 30% of the day unscheduled. "A realistic day, not an optimistic one." |
| User says something vague ("just a chill day") | Gemini should respond with a minimal plan: keep calendar events, suggest 1-2 light tasks, leave the rest open. |
| Network failure mid-conversation | Cache the current proposal locally. Show "Offline — using last proposal" and allow commit of cached plan. |
| User ignores the plan entirely | No consequence. The calendar events exist but the wall doesn't nag. It just shows the next block in quiet mode. The user's agency is preserved. |
| Privacy of spoken content | Same as existing voice capture — processed via Gemini API. No new privacy surface. |

#### Why This Is The Dream Feature

This is the feature that transforms the wall from a **passive display** into an **active cognitive partner**. Every other feature shows you information. This one *thinks for you* — specifically, it handles the exact type of thinking that chaotic brains struggle with most. The wall becomes a gentle executive function prosthetic.

---

### 8.2 — Weather-Tinted Calendar Days

#### The Problem It Solves

"Should I do the grocery run today or tomorrow?" You check your phone's weather app. You check your calendar. You hold both in working memory. You decide. Multiply by every outdoor-sensitive decision across a week.

The weather tint eliminates this entirely. You glance at the calendar and **intuitively know** which days are good for outdoor errands. No app-switching, no cognitive load — just ambient awareness baked into a view you're already looking at.

#### Visual Design

The tint must be extremely subtle — a background wash, not a painted surface. The calendar cell's content (date number, event dots) must remain fully legible. Think of it as the cell having a very faint mood.

| Condition | Tint | Hex (at ~8% opacity) | Feel |
|-----------|------|----------------------|------|
| Clear/Sunny | Warm gold | `Color(0x14FFD54F)` | Gentle warmth, inviting |
| Partly Cloudy | Neutral (no tint) | — | Default state, no extra info |
| Cloudy/Overcast | Cool grey | `Color(0x0A90A4AE)` | Slightly muted, indoor day |
| Rain | Slate blue | `Color(0x12607D8B)` | Cool, stay-inside feeling |
| Snow | Soft white/lavender | `Color(0x0ECFD8DC)` | Clean, quiet |
| Storm/Severe | Muted amber | `Color(0x12FFAB91)` | Caution without alarm — matches urgency palette |

**Week View Enhancement**: In CalendarWeekView, each day row gets the tint. Additionally, a tiny weather icon (sun, cloud, raindrop — 12dp, muted color) appears next to the date label. Subtle enough to be ignorable, informative enough at a glance.

**Month View**: Day cells get the background tint only. No icons — too small. The color gradient across a week tells the story.

#### Architecture

```
New files:
├── data/model/WeatherCondition.kt       # Enum: CLEAR, CLOUDY, RAIN, SNOW, STORM
├── data/model/WeatherForecast.kt        # Data class: Map<LocalDate, WeatherCondition> + temp
├── data/repository/WeatherRepository.kt # API client + caching
└── ui/theme/WeatherColors.kt            # Tint color tokens

Modified files:
├── viewmodel/TaskWallViewModel.kt       # weatherForecast StateFlow, daily refresh
├── ui/components/CalendarMonthView.kt   # Apply tint to day cells
├── ui/components/CalendarWeekView.kt    # Apply tint to day rows + optional icon
├── ui/components/SettingsPanel.kt       # Location setting
└── ui/theme/WallColors.kt              # Add weather tint tokens
```

**API Choice**: OpenWeatherMap's "One Call 3.0" — single request returns 8-day forecast. Free tier: 1000 calls/day. With one call per app launch + daily refresh, this is ~2 calls/day. Abundant headroom.

**Caching**: Store last forecast in DataStore (serialized as JSON). Show cached forecast when offline. Weather data is valid for hours, so aggressive caching is fine.

**Location**: Three options (in settings):
1. Manual city entry (simplest, no permissions)
2. Android Fused Location Provider (requires permission, but one-shot — not continuous tracking)
3. IP-based geolocation (no permission, approximate but usually sufficient for weather)

Default to manual city to avoid permission prompts on a kiosk device.

#### Interaction with Day Organizer

If both extensions are active, the Day Organizer's Gemini prompt includes weather data. "It's going to rain after 2pm" → the AI schedules your grocery run in the morning. The tint on the calendar then visually confirms why the blocks are arranged that way. The two features reinforce each other.

---

### 8.3 — "Next Action" Spotlight

#### The Problem It Solves

Quiet Mode currently shows 3 tasks sorted by urgency. But urgency and actionability aren't the same thing. Your most urgent task might be "Pay rent" (due tomorrow, 2 minutes to do) while the task you *should* work on right now is "Draft project proposal" (due next week, but needs focused time). Showing 3 items still leaves you choosing.

The Next Action Spotlight shows **one task** with enough context to start immediately, eliminating the decision entirely.

#### UX Design

In Quiet Mode, the wall shows:

```
                    10:47 AM
              Thursday, March 14

        ─────────────────────────────
              Your next move:

           Draft project proposal
           Work Projects  ·  due Mar 20
           ~2 hours  ·  3 subtasks

           Next free slot: now until 1:00 PM
        ─────────────────────────────
```

**Selection Algorithm** (priority cascade):
1. If Day Organizer plan exists → show current time block's task
2. If overdue task exists → show highest-urgency overdue
3. If due-today task exists → show it
4. Else → apply heuristic:
   - Weight by: urgency score × days-sitting-in-list × estimated-effort-match-for-time-available
   - If calendar integration active: only suggest tasks that fit in the next free time slot
   - If energy profile active: filter by current energy window

**Subtle detail**: The "Next free slot" line only appears when Google Calendar is connected. It checks the calendar for the next gap ≥30 minutes and tells you when you could actually do this. This is the kind of context that brains waste cycles computing manually.

#### Architecture Change

Minimal — this modifies existing code:

```kotlin
// In TaskWallScreen.kt, replace quietModeTasks computation:
val spotlightTask = remember(allTasks, calendarEvents, currentTime) {
    computeNextAction(allTasks, calendarEvents, currentTime, energyProfile)
}
```

New composable `NextActionSpotlight` replaces the current `QuietModeContent` task list with a single, richer card. The clock display stays. Total change: ~100 lines of new code, ~20 lines modified.

**Complexity**: Small. This could ship in a single session.

---

### 8.4 — Habit Streak Dots

#### The Problem It Solves

You added "Go to the gym" to your task list. You complete it. Next week, you add it again. Complete it. Repeat. But after a few weeks, you have no visibility into whether you're actually consistent. Did you go 3 times last month or 6? Without feedback, you either:
1. Worry about it (adding mental load), or
2. Stop caring (the habit dies)

The dots provide **gentle, non-judgmental visibility** into your patterns. They don't celebrate or punish. They just show reality.

#### Visual Design

Below a task title identified as recurring, a row of small dots appears:

```
┌──────────────────────────────────────────┐
│  ○ Go to the gym                         │
│  ● ● ○ ● ● ● ○ ●                        │
│                    ↑ this week (current)  │
└──────────────────────────────────────────┘
```

- `●` = completed that week (filled, `textSecondary` color)
- `○` = not completed that week (hollow, `textMuted` color)
- 8 dots = 8 weeks of history (2 months of visibility)
- Dot size: 5dp diameter, 4dp spacing
- No labels, no percentages, no streak counts
- The current week's dot fills in real-time when you complete the task

**Why no numbers**: Streak counts create pressure. "I'm on a 14-day streak" becomes "I can't break my streak" — which is anxiety, not calm. The dots let you see the pattern without quantifying it. A few hollow dots among filled ones is just... life. No judgment.

#### Recurring Task Detection

The app doesn't have explicit recurring task support (Google Tasks doesn't have a recurrence field). Detection must be heuristic:

```kotlin
data class HabitPattern(
    val title: String,           // normalized (lowercased, trimmed)
    val weeklyCompletions: List<Boolean>,  // last 8 weeks
    val confidence: Float        // 0-1, based on how reliably it appears
)

fun detectRecurringTasks(completionHistory: List<CompletedTask>): List<HabitPattern> {
    // Group completed tasks by normalized title
    // For each title that appears in 3+ of the last 8 weeks → mark as recurring
    // Confidence = weeks_appeared / 8
    // Only show dots for confidence > 0.3 (appeared at least 3 of 8 weeks)
}
```

**Data Source Challenge**: Google Tasks API's `list()` with `showCompleted(true)` and `completedMin`/`completedMax` parameters can retrieve completed tasks within a date range. But it only returns tasks that still exist (not permanently deleted). If users clear completed tasks, history is lost.

**Mitigation**: Store completion events locally in DataStore or a Room database. When a task is completed, log `{title, completedDate}`. This local history persists even if the task is deleted from Google Tasks. Storage is minimal (~100 bytes per completion event).

#### Architecture

```
New files:
├── data/model/HabitPattern.kt           # Data class
├── data/repository/HabitRepository.kt   # Local history + detection logic
└── data/local/CompletionHistoryDao.kt   # Room DAO (or DataStore serialization)

Modified files:
├── viewmodel/TaskWallViewModel.kt       # habitPatterns StateFlow, log completions
├── ui/components/TaskItem.kt            # Render dot row below recurring tasks
└── build.gradle.kts                     # Room dependency (if using Room)
```

**Complexity**: Medium. The detection logic is simple, but adding local persistence (Room or DataStore) for completion history is new infrastructure.

---

### 8.5 — Energy-Aware Task Ordering

#### The Problem It Solves

At 2pm, after lunch, your task list shows "Design new API architecture" at the top because it's due soonest. But you're in a post-lunch energy dip — you'll stare at it for 20 minutes, feel bad about not starting, and then scroll Instagram instead.

Meanwhile, "Reply to 3 emails" is further down the list. It's a 15-minute low-energy task that you could knock out right now and feel good about. But you didn't think to look for it because the sort order doesn't know about your energy.

#### How It Works

The wall maintains a simple energy model:

```
Morning    (6am-12pm):  HIGH energy    → Creative, complex, deep-focus tasks
Afternoon  (12pm-3pm):  LOW energy     → Admin, routine, communication tasks
Late Aft.  (3pm-6pm):   MEDIUM energy  → Moderate tasks, planning
Evening    (6pm-10pm):  WINDING DOWN   → Light tasks, personal, review
```

Users configure their profile in Settings by choosing from presets:
- **Morning Person**: default (above)
- **Night Owl**: HIGH shifts to evening, LOW in morning
- **Steady**: MEDIUM all day (no reordering)
- **Custom**: tap-to-adjust per time block (future enhancement)

Tasks are classified by energy requirement:
- **HIGH**: Tasks containing keywords like "design", "write", "plan", "build", "create", or tasks in lists named "Projects", "Development"
- **LOW**: Tasks with "email", "call", "buy", "clean", "schedule", or tasks in lists named "Errands", "Admin"
- **MEDIUM**: Everything else

**Better approach** (Phase 2): Ask Gemini to classify tasks once when they're synced, cache the classification. Much more accurate than keyword matching.

#### Visual Effect

This is NOT about hiding tasks or creating separate sections. It's about **subtle sort boosting**:

- Tasks matching the current energy window get a slight sort weight boost (+1 to their position score)
- The effect: energy-appropriate tasks float up 1-3 positions, not to the absolute top
- No visual indicator needed — the user just notices that the "right" tasks tend to be near the top when they look

**Optional**: A nearly-invisible energy indicator in the clock header — a tiny gradient bar (4dp tall) showing the current energy phase color. Gold = high, blue = low, green = medium. So subtle it's only noticeable if you're looking for it.

#### Architecture

```
New files:
├── data/model/EnergyProfile.kt          # Profile presets, time-to-energy mapping
├── data/model/TaskEnergyLevel.kt        # HIGH, MEDIUM, LOW enum
└── util/TaskEnergyClassifier.kt         # Keyword-based or Gemini-based classification

Modified files:
├── viewmodel/TaskWallViewModel.kt       # energyProfile setting, sort weight adjustment
├── data/model/Task.kt                   # Optional energyLevel cached field
├── ui/components/SettingsPanel.kt       # Energy profile selector
└── ui/components/ClockHeader.kt         # Optional energy phase indicator
```

**Complexity**: Medium. The classification is the interesting part — keyword matching is fast but crude, Gemini is accurate but adds API calls. A hybrid (keyword for common patterns, Gemini for ambiguous tasks, cached once) is the sweet spot.

---

### 8.6 — Ambient Notification Digest

#### The Problem It Solves

The phone buzzes. Was it important? You pick it up to check. 20 minutes later you've checked three apps, responded to two messages, and completely lost the thread of what you were doing. The notification wasn't even important — it was a promotional email.

The wall can act as a **notification triage layer**. It shows you just enough to know whether you need to pick up your phone. If the answer is "no" (and it usually is), you stay present. If the answer is "yes" (Mom called twice), you know immediately without the phone-pickup trap.

#### What It Shows (And Doesn't)

**Shown** (in Quiet Mode only, beneath the task spotlight):

```
                    ─────────────
                    2 missed calls
                    1 message from Mom
                    ─────────────
```

- **Categories, not content**: "1 message from Mom" — not the message text. This respects privacy (wall-mounted = potentially visible to others) while giving you the information you need.
- **Aggregated**: "3 messages from WhatsApp" — not three separate notifications.
- **Filtered**: Only calls, messages, and calendar reminders. No promotional emails, no app updates, no social media.

**Not shown**:
- Message content/preview (privacy)
- App notifications (promotional noise)
- Anything during Active mode (you're already engaged with the wall)
- Anything during Sleep mode (you're sleeping)

#### UX Considerations

**Privacy control in Settings**:
- **Off** (default) — no notification access
- **Calls only** — just missed call count
- **Calls + Messages** — call count + message sender names (no content)
- **Full** — calls + messages + calendar reminders

**Visual treatment**: Very faint, very small (labelSmall/10sp). This is background information — less prominent than the Next Action task. It should feel like a whisper, not a notification badge screaming for attention.

**Timing**: The digest updates when Quiet Mode enters (30s idle) and refreshes every 5 minutes. It does NOT update in real-time — this isn't a notification center, it's a digest. The wall checks periodically and summarizes.

#### Architecture

```
New files:
├── service/NotificationDigestService.kt   # NotificationListenerService subclass
├── data/model/DigestItem.kt               # Category, sender, count
├── data/repository/NotificationDigestRepository.kt  # Filter, aggregate, expose StateFlow

Modified files:
├── viewmodel/TaskWallViewModel.kt         # notificationDigest StateFlow
├── ui/screens/TaskWallScreen.kt           # Render digest in Quiet Mode
├── ui/components/SettingsPanel.kt         # Privacy level selector
└── AndroidManifest.xml                    # NotificationListenerService declaration
```

**Permission**: `NotificationListenerService` requires the user to explicitly grant access in Android Settings → Notification Access. This is a one-time setup step. The app should guide the user to this setting if the feature is enabled.

**Complexity**: Medium. The `NotificationListenerService` API is well-documented but somewhat finicky. The filtering/aggregation logic is the core value — getting it right (not too noisy, not too quiet) requires tuning.

**Risk**: Privacy. This needs to be off by default, clearly explained, and easy to disable. A wall-mounted device in a shared space showing "1 message from [person]" could be unwanted. The user must explicitly opt in.

---

### 8.7 — Passive Package/Delivery Tracker

#### The Problem It Solves

"When is that thing arriving? Did I miss the delivery?" You ordered something online 4 days ago. You have a vague sense it should arrive "soon." You check the tracking page. It says "in transit." You check again tomorrow. "In transit." You check 3 more times. This low-grade tracking anxiety occupies background mental threads for days.

The wall could simply show: `📦 Arriving Thursday` — and the thought is externalized.

#### Design

A small, unobtrusive section (or special task-like card) in the task list:

```
┌──────────────────────────────────────────┐
│  📦  Amazon order         Arriving today │
│  📦  IKEA shelf           In transit     │
└──────────────────────────────────────────┘
```

- Uses the same card style as tasks, but with a package icon and `accentWarm` tint
- Sorted by arrival date (soonest first)
- "Arriving today" gets the same warm treatment as due-today tasks
- "Delivered" items fade to completed style and auto-dismiss after 24h
- Maximum 3-4 visible (the wall isn't a shipping dashboard)

#### Data Sources (from simplest to most complex)

1. **Manual entry** (simplest): User adds a "tracking" task via voice. "Track my Amazon package, arriving Thursday." The Gemini voice parser detects the intent and creates a delivery entry instead of a task. Lowest effort, but requires user to know the date.

2. **Gmail parsing** (medium): Use Gmail API (same Google OAuth) to scan for shipping confirmation emails. Look for patterns: "Your order has shipped", "Estimated delivery: March 17". Extract tracking numbers and dates.
   - Pro: Automatic, no user effort
   - Con: Email parsing is inherently fragile. Different retailers use different formats. Needs regex patterns per retailer or an AI parsing layer.

3. **Tracking API** (most reliable): Use AfterShip or 17track API. User pastes tracking numbers (via voice: "Track package 1Z999AA10123456784"). API returns structured delivery status.
   - Pro: Reliable structured data
   - Con: Requires API key, tracking number input, additional dependency

**Recommendation**: Start with option 1 (voice-based manual entry). It's free, private, and requires zero new infrastructure. The Gemini prompt already parses due dates from natural language — extending it to recognize "arriving Thursday" vs "due Thursday" is a small prompt modification.

Phase 2 could add Gmail parsing for automatic detection, but only if the user explicitly connects Gmail (separate OAuth scope: `gmail.readonly`).

#### Architecture

```
New files:
├── data/model/Delivery.kt               # title, estimatedDate, status, trackingNumber?
├── data/repository/DeliveryRepository.kt # Manages delivery entries (DataStore or Room)

Modified files:
├── viewmodel/TaskWallViewModel.kt        # deliveries StateFlow
├── capture/repository/GeminiCaptureRepository.kt  # Detect delivery intent in voice
├── ui/screens/TaskWallScreen.kt          # Render delivery cards section
```

**Complexity**: Small for Phase 1 (voice-based manual), Large for Phase 2 (Gmail parsing).

---

### 8.8 — Local Events & Community Discovery

#### The Problem It Solves

There's a neighborhood potluck this Saturday. A small jazz night at the café three blocks away. A free pottery workshop at the community center. A local band playing at the pub you walk past every day. You'd enjoy any of these — but you never find out about them until they've already happened, or you hear about them secondhand a week later.

The irony: you live in a community full of activity, but your relationship with it is passive. You see the same streets, the same storefronts. The actual social fabric — the events, the gatherings, the things that turn a neighborhood into a *community* — is invisible to you because it lives scattered across Facebook event pages, community boards, and Instagram stories you don't follow.

This isn't about Ticketmaster pushing arena concerts at you. It's about the wall being a **window into your local world** — the small, human-scale things happening within walking or short-driving distance that you'd actually enjoy if you knew about them.

#### The Philosophy: Anti-Advertising

This feature must be **aggressively anti-corporate**. The distinction:

| This Feature IS | This Feature IS NOT |
|-----------------|---------------------|
| Your neighbor's garage sale | "50% off at Target this weekend" |
| Jazz night at the café 3 blocks away | "Drake at the arena 200km away — tickets from $180" |
| Community garden volunteer day | Sponsored brand activations |
| Local band at the pub | Algorithmically promoted content |
| Free yoga in the park | Gym membership promotions |
| Neighborhood potluck | Restaurant advertising |
| Farmers market this Sunday | Corporate food festival |
| Art show at a local gallery | Museum blockbuster exhibit tour |

The filter must be ruthless: **small scale, close distance, community-driven**. If a corporation is the primary organizer, it's out. If the venue capacity is >500, it's probably out. If it's >10km away, it needs to be genuinely special (a beloved annual festival, not a random concert).

#### How It Learns Your Preferences

**Phase 1 — Cold Start (First 2 Weeks)**

No preference data yet. The wall shows a curated sample of nearby events across categories:

```
Categories sampled:
• Music (local venues, open mics)
• Food (markets, pop-ups, community meals)
• Art (galleries, workshops, craft nights)
• Outdoors (group walks, park cleanups, gardening)
• Social (meetups, game nights, potlucks)
• Learning (workshops, talks, skill shares)
• Fitness (group runs, free classes, pickup sports)
```

Events appear as gentle suggestions in the calendar view — a subtle marker on days with nearby events. In Quiet Mode, if an event is happening today or tomorrow, it might appear beneath the Next Action spotlight:

```
        ─────────────────────────────
              Your next move:
           Draft project proposal
        ─────────────────────────────

              Nearby this weekend:
           Open mic · The Hollow Bar · Sat 8pm
           Farmers market · Central Park · Sun 9am
        ─────────────────────────────
```

**Phase 2 — Implicit Learning**

The wall learns from two signals:
1. **Accept/dismiss via encoder**: When an event suggestion appears, the user can click (interested → adds to calendar) or twist away (not interested → logged as negative signal).
2. **Category affinity over time**: If you consistently dismiss music events but click on food events, the music frequency drops and food rises. This is a simple affinity score per category, not a complex recommendation engine.

```kotlin
data class EventPreferences(
    val categoryAffinities: Map<EventCategory, Float>,  // 0.0 to 1.0
    val maxDistanceKm: Float,                           // default 5.0
    val preferredDays: Set<DayOfWeek>,                  // when you're open to events
    val dismissed: Set<String>                          // event IDs already rejected
)
```

The affinity adjustment is gentle: each accept bumps the category by +0.05, each dismiss by -0.02. This means it takes many dismissals to fully suppress a category, and occasional surprises still get through. The wall should occasionally surface a "wildcard" event outside your usual preferences — because part of community discovery is finding things you didn't know you'd enjoy.

**Phase 3 — Gemini-Enhanced Curation**

Send the user's preference profile + available events to Gemini for smarter filtering:

> "Given this user enjoys food events and outdoor activities, dislikes loud music venues, and lives at [location], rank these 20 events by likely interest. Also, flag any event that seems like corporate advertising rather than genuine community activity."

Gemini can catch nuances that keyword filtering can't: a "Free Yoga in the Park" organized by a community member is different from a "Free Yoga in the Park — Brought to You by Lululemon."

#### Frequency & Intrusiveness

This feature must be **whisper-quiet**. It's not a feed. Rules:

- **Maximum 2-3 event suggestions per day**, shown only during Quiet Mode
- **Never during Active mode** — when you're using the wall for tasks, events are invisible
- **Weekend peek**: On Thursday/Friday, show a "This Weekend" section with 2-3 nearby events. This is the sweet spot — enough lead time to plan, close enough to feel relevant.
- **No notifications, no urgency colors, no countdown timers**. Events don't have the same urgency model as tasks. They exist as gentle options, not obligations.
- **Easy to turn off entirely** in Settings. One toggle.

#### Calendar Integration

When the user clicks "interested" on an event:
1. A Google Calendar event is created (title, time, location, description with source link)
2. The event appears in the calendar view with a distinctive tint (community purple? a new `accentCommunity` color) — visually distinct from self-created events and tasks
3. If the Day Organizer (8.1) is active, it automatically accounts for the event when planning the day
4. If Weather Tints (8.2) is active, the user can see at a glance if the outdoor music festival they're considering is on a rainy day

#### Data Sources

This is the hardest part. Local events are scattered across platforms with varying API quality:

**Tier 1 — Structured APIs (most reliable)**

| Source | What It Covers | API | Free Tier |
|--------|---------------|-----|-----------|
| Eventbrite | Workshops, classes, community events | REST API | Yes (1000 calls/day) |
| Meetup.com | Group meetups, interest-based | GraphQL API | Limited free tier |
| Yelp Events | Local business events | Fusion API | Yes (5000 calls/day) |

**Tier 2 — Web Scraping / Parsing (medium reliability)**

| Source | What It Covers | Approach |
|--------|---------------|----------|
| Facebook Events | The richest source — most local events live here | Facebook Graph API (restrictive), or Firecrawl scraping of public event pages |
| Local newspaper/city websites | Community boards, city-organized events | Firecrawl per-city (configurable URLs in settings) |
| Community bulletin boards (e.g., Nextdoor-style) | Hyperlocal — garage sales, block parties | Generally no API; manual URL scraping |

**Tier 3 — Aggregators**

| Source | What It Covers | Notes |
|--------|---------------|-------|
| Google Events (via Search API) | Aggregated from multiple sources | Unreliable for small events, biased toward commercial |
| AllEvents.in | Community-focused aggregator | API available, good international coverage |

**Recommended Approach**:
1. Start with **Eventbrite API** (good coverage of workshops, community events, free/low-cost events) + a configurable list of **local URLs to scrape via Firecrawl** (user adds their city's event page, community center website, etc.)
2. Phase 2: Add Facebook Events via Graph API if available, or scraping of public pages
3. Feed all results through **Gemini for curation** — filter out corporate/promotional events, rank by user preferences, classify by category

#### UX Flow for Adding Local Sources

In Settings, a "Community Sources" section:

```
┌─────────────────────────────────────────┐
│  Community Events                       │
│                                         │
│  Location: [Portland, OR]               │
│  Radius:   [5 km]                       │
│                                         │
│  Sources:                               │
│    ✓ Eventbrite (built-in)              │
│    ✓ Local pages:                       │
│      • pdx.events/calendar              │
│      • multnomahcounty.us/events        │
│      • Add URL...                       │
│                                         │
│  Categories: [all enabled]              │
│  Suggestions per day: [2-3]             │
└─────────────────────────────────────────┘
```

Users can add URLs of local event pages. The app scrapes these periodically (daily) via Firecrawl/web parsing, extracts events, and runs them through the Gemini filter. This is the most flexible approach — it works for any city without requiring dedicated API support.

#### Visual Design on the Wall

**In Calendar Month View**: Days with nearby events get a small community dot (like existing event dots, but in a distinct `accentCommunity` color — a muted purple/violet, e.g., `Color(0xFF9E8FB4)`). This is in addition to the user's own event dots.

**In Calendar Week View**: Community events appear as faded chips below the user's own events, visually subordinate:

```
┌──────────────────────────────────────────────┐
│  Saturday, Mar 21                            │
│                                              │
│  ● Dentist 2:00 PM                          │ (your event)
│  ● Dinner with Alex 7:00 PM                 │ (your event)
│                                              │
│  ○ Farmers Market · Central Park · 9am-1pm  │ (community, faded)
│  ○ Open Mic · The Hollow · 8pm              │ (community, faded)
└──────────────────────────────────────────────┘
```

The `○` prefix and faded treatment make it clear these are suggestions, not commitments. They're there if you're curious; invisible if you're not.

**In Quiet Mode** (when events are happening today/tomorrow):

```
              Nearby tomorrow:
         Farmers Market · 9am · 0.8 km
```

One line. Maximum two events. Gone if nothing relevant.

#### Architecture

```
New files:
├── data/model/LocalEvent.kt                  # title, time, location, distance, category, source, isConfirmed
├── data/model/EventCategory.kt               # Enum: MUSIC, FOOD, ART, OUTDOORS, SOCIAL, LEARNING, FITNESS
├── data/model/EventPreferences.kt            # Category affinities, maxDistance, dismissed
├── data/repository/LocalEventsRepository.kt  # Aggregates from multiple sources
├── data/repository/EventbriteClient.kt       # Eventbrite API wrapper
├── data/repository/LocalEventScraper.kt      # Firecrawl-based scraping of user-added URLs
├── data/local/EventPreferencesDao.kt         # Room DAO for preferences + dismissed events
└── ui/components/CommunityEventChip.kt       # Composable for event suggestion display

Modified files:
├── viewmodel/TaskWallViewModel.kt            # localEvents StateFlow, preference updates
├── ui/components/CalendarMonthView.kt        # Community event dots
├── ui/components/CalendarWeekView.kt         # Community event chips (faded)
├── ui/screens/TaskWallScreen.kt              # Quiet Mode event suggestions
├── ui/components/SettingsPanel.kt            # Community sources configuration
├── ui/theme/WallColors.kt                    # accentCommunity color token
└── capture/repository/GeminiCaptureRepository.kt  # Event curation prompt
```

#### Edge Cases & Risks

| Risk | Mitigation |
|------|------------|
| Events source returns corporate/sponsored results | Gemini curation filter specifically prompted to reject corporate organizers, venues >500 capacity, events >maxDistance |
| User's area has very few events | Show "Nothing nearby this week" gracefully, or widen radius temporarily. Don't show empty states that make the user feel isolated. |
| Scraping reliability varies by website | Cache last successful scrape. Show cached events with "last updated" indicator. Fail silently — never show an error for missing event data. |
| Privacy: location data sent to APIs | Location is already needed for weather. Use the same setting. Eventbrite queries use city-level, not exact address. |
| Events are stale (already happened) | Filter by date on every display. Events in the past auto-disappear. Daily refresh catches cancellations. |
| User overwhelmed by suggestions | Hard cap at 2-3/day. Frequency drops further if dismissal rate is high. |
| "Filter bubble" from preference learning | Mandatory 1-in-5 wildcard event from a low-affinity category. Prevents the system from only showing food events to a food person — you might discover you love pottery. |

#### Why This Belongs on the Wall

The wall is always there. You walk past it getting coffee. You glance at it while cooking. In those ambient moments, seeing "Open mic at the café tonight" plants a seed. You don't have to decide now. But the seed is planted, and later when someone asks "what should we do tonight?" — you have an answer that didn't cost you any cognitive effort to find.

This is the opposite of scrolling through event apps or Facebook. The wall **curates and whispers**. No infinite scroll. No FOMO-inducing "238 people interested." Just: here's a thing, near you, soon, that matches what you tend to enjoy. Take it or leave it.

The deeper purpose: for someone with a chaotic brain who tends toward isolation (because going out requires planning, and planning requires executive function), the wall removes the planning barrier. The event is surfaced. The date is in your calendar. The Day Organizer accounts for it. All you have to do is show up.

---

### Extension Synergy Map

These features aren't isolated — they compound:

```
                    ┌─────────────────────┐
                    │   Day Organizer     │
                    │   (8.1)             │
                    └──────┬──────────────┘
                           │ uses
              ┌────────────┼────────────────┐
              ▼            ▼                ▼
     ┌────────────┐  ┌──────────┐  ┌────────────────┐
     │  Weather   │  │  Energy  │  │  Next Action   │
     │  Tints     │  │  Profile │  │  Spotlight     │
     │  (8.2)     │  │  (8.5)   │  │  (8.3)         │
     └─────┬──────┘  └────┬─────┘  └───────┬────────┘
           │              │                │
           │    informs   │   adapts       │ shows current
           │    outdoor   │   task sort    │ time block
           │    scheduling│                │
           ▼              ▼                ▼
     ┌─────────────────────────────────────────────┐
     │          Calendar View (existing)            │
     │    weather tints + organized day blocks      │
     │    + community event dots & chips            │
     └─────────────────────────────────────────────┘
           ▲                                ▲
           │ events feed into               │ events shown
           │ day planning                   │ in quiet mode
     ┌─────┴────────┐              ┌───────┴────────┐
     │ Community    │              │ Habit Dots     │
     │ Events (8.8) │              │ (8.4)          │
     │              │              │                │
     │ discovers    │              │ independent    │
     │ local things │              │ but enriches   │
     │ to do        │              │ task cards     │
     └──────────────┘              └────────────────┘

     ┌──────────────┐     ┌──────────────────┐
     │ Notification │     │ Package          │
     │ Digest (8.6) │     │ Tracker (8.7)    │
     │              │     │                  │
     │ independent  │     │ independent      │
     │ reduces phone│     │ ambient info     │
     │ checking     │     │                  │
     └──────────────┘     └──────────────────┘
```

The **Day Organizer** is the hub — it can consume weather, energy profile, task data, *and community events* to produce the optimal day plan. ("There's a farmers market at 9am near you — want me to slot in a grocery run there instead of the store?") The **Next Action Spotlight** then becomes the real-time view into that plan. **Community Events** feeds into both the calendar view and the Day Organizer, creating a loop where local activities naturally weave into your day. Everything else is independent and additive.

### Recommended Implementation Order

| Phase | Features | Rationale |
|-------|----------|-----------|
| **Phase 1** | Next Action Spotlight (8.3) | Smallest effort, immediate impact. Improves existing Quiet Mode. Ships in one session. |
| **Phase 2** | Weather Tints (8.2) | Small-medium effort. Purely additive — doesn't change existing behavior. Visual delight. |
| **Phase 3** | Habit Streak Dots (8.4) | Medium effort. Adds local persistence infrastructure (Room) that Phase 4+ can reuse. |
| **Phase 4** | Day Organizer (8.1) | Large effort but the defining feature. Requires multi-turn Gemini, new overlay, calendar event creation. Benefits from weather data (Phase 2) being available. |
| **Phase 5** | Energy-Aware Ordering (8.5) | Medium effort. Enhances Day Organizer's scheduling intelligence. |
| **Phase 6** | Community Events (8.8) | Medium-large effort. Benefits from calendar view (existing), weather tints (Phase 2), and Day Organizer (Phase 4) being in place. The Day Organizer can weave local events into your day plan. |
| **Phase 7** | Notification Digest (8.6) | Medium effort, privacy-sensitive. Better as a later addition once the core is solid. |
| **Phase 8** | Package Tracker (8.7) | Start with voice-based manual entry (small). Gmail parsing is a future enhancement. |

---

*Generated by 7 parallel analysis agents examining: Input Completeness, Core Loop & Sync, Ambient/Kiosk Behavior, Failure & Recovery, Spec Fidelity, Visual Hierarchy & Readability, Feature Gaps & Extensions.*
