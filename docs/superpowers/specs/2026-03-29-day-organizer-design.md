# Day Organizer — Design Spec

**Date:** 2026-03-29
**Status:** Draft
**Feature:** Gemini-powered conversational day planning

---

## 1. Problem Statement

The user has a wall-mounted kiosk showing tasks and calendar. Currently, turning a mental model of "what I need to do today" into a structured, time-blocked calendar requires manual event creation one at a time. The Day Organizer lets the user voice-dump everything they want and need to do, and Gemini produces an intelligent, time-blocked plan that accounts for existing calendar events, task types (active vs passive), energy curves, errand clustering, and logistics.

## 2. User Flow

```
Calendar View (any mode)
  └─ Click voice FAB
      └─ Full-screen dim + waveform ("PLANNING YOUR DAY")
          └─ User speaks brain dump
              └─ Click to finish speaking
                  └─ Processing spinner ("Planning your day...")
                      └─ Gemini returns structured plan
                          └─ Plan Preview overlay (time-blocked timeline)
                              ├─ [Accept] → Promote all blocks to Google Calendar → transient banner → return to calendar
                              ├─ [Adjust] → Voice listening again ("ADJUSTING PLAN") → Gemini re-plans → new Preview
                              └─ [Cancel] → Return to calendar, nothing saved
```

### Entry Points

1. **Wall (primary):** Voice FAB on all calendar views (MONTH, WEEK, THREE_DAY, DAY). Mirrors the existing voice FAB on the task page. Encoder-navigable — rotate to focus, click to activate.
2. **Phone (secondary):** PhoneCaptureViewModel gets a "Plan Day" mode. User speaks into phone, plan appears on wall. (Phase 2 — not in this spec.)

### Target Day

- Defaults to **today** (remainder of the day from current time).
- If the utterance explicitly names another day ("plan my Sunday", "for tomorrow I need to..."), Gemini targets that day instead.
- The plan starts from the current hour (rounded to next half-hour) for today, or from a configurable wake time (default 7:00 AM) for future days.

## 3. Architecture

### 3.1 New Components

| Component | Type | Purpose |
|-----------|------|---------|
| `DayOrganizerCoordinator` | Class | Owns the planning state machine. Orchestrates voice → Gemini → plan → adjust loop. |
| `DayOrganizerState` | Sealed class | State machine: Idle, Listening, Processing, PlanReady, Adjusting, Confirming, Error |
| `DayPlan` | Data class | Gemini's output: list of `PlanBlock` items with times, labels, categories |
| `PlanBlock` | Data class | Single time block: title, start, end, category, isExisting, notes |
| `BlockCategory` | Enum | ACTIVE, PASSIVE, ERRAND, SOCIAL, LEISURE, EXISTING_EVENT |
| Gemini prompt: `buildDayPlanPrompt()` | Function in `GeminiCaptureRepository` | Constructs the planning prompt with context |
| Gemini prompt: `buildPlanAdjustmentPrompt()` | Function in `GeminiCaptureRepository` | Constructs the adjustment prompt with previous plan + change request |
| `DayOrganizerOverlay` | Composable | Plan preview UI: timeline, action bar, encoder navigation |
| Voice FAB on CalendarScreen | Composable | Reuses `VoiceFab` pattern from TaskWallScreen |

### 3.2 Reused Components

| Component | How it's reused |
|-----------|-----------------|
| `VoiceCaptureManager` | Speech-to-text for brain dump and adjustments. Same `startListening()` / `stopListening()` / amplitude flow. |
| `GoogleCalendarRepository` | `getEventsForDateRange()` to fetch existing events. `createEvent()` to promote plan blocks. |
| `GoogleTasksRepository` | `getTaskLists()` + `getTasks()` to give Gemini awareness of existing tasks. |
| `GeminiKeyStore` | API key retrieval. |
| `WaveformVisualizer` | Listening state visual feedback. |
| `TaskWallViewModel` | Exposes `DayOrganizerState` as a new StateFlow. Delegates to coordinator. |

### 3.3 State Machine

```
                 ┌─────────────────────────────────────────┐
                 │                                         │
                 ▼                                         │
    ┌───────┐  click   ┌───────────┐  speech   ┌────────────┐
    │ Idle  │ ──FAB──▶ │ Listening │ ──done──▶ │ Processing │
    └───────┘          └───────────┘           └────────────┘
        ▲                    │                       │
        │                    │ cancel                │ Gemini responds
        │                    ▼                       ▼
        │               ┌───────┐             ┌───────────┐
        │               │ Idle  │             │ PlanReady │ ◀──┐
        │               └───────┘             └───────────┘    │
        │                                      │  │  │         │
        │                              Accept  │  │  │ Adjust  │
        │                                      │  │  │         │
        │                                      ▼  │  ▼         │
        │                          ┌───────────┐  │  ┌──────────┐
        │                          │ Confirming│  │  │ Adjusting│
        │                          └───────────┘  │  └──────────┘
        │                               │         │       │
        │                     success   │  cancel  │       │ Gemini responds
        │                               ▼         ▼       │
        │                          ┌───────┐  ┌───────┐   │
        └──────────────────────────│ Idle  │  │ Idle  │   │
                                   └───────┘  └───────┘   │
                                                          │
                                       ┌──────────────────┘
                                       │ (returns to PlanReady
                                       │  with updated plan)
                                       ▼
```

**States:**

```kotlin
sealed class DayOrganizerState {
    object Idle : DayOrganizerState()

    data class Listening(
        val amplitudeLevel: Float = 0f,
        val isAdjustment: Boolean = false    // true when adjusting existing plan
    ) : DayOrganizerState()

    data class Processing(
        val isAdjustment: Boolean = false
    ) : DayOrganizerState()

    data class PlanReady(
        val plan: DayPlan,
        val focusedAction: Int = 0           // 0=Accept, 1=Adjust, 2=Cancel
    ) : DayOrganizerState()

    data class Adjusting(
        val previousPlan: DayPlan,
        val amplitudeLevel: Float = 0f
    ) : DayOrganizerState()

    object Confirming : DayOrganizerState()

    data class Error(
        val message: String,
        val canRetry: Boolean = true
    ) : DayOrganizerState()
}
```

### 3.4 Data Models

```kotlin
data class DayPlan(
    val targetDate: LocalDate,
    val blocks: List<PlanBlock>,
    val summary: String,                     // "8 blocks, 6 new — 2 errands clustered"
    val confidence: Float,                   // 0.0-1.0 from Gemini
    val warning: String? = null              // present when confidence < 0.5
)

data class PlanBlock(
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val category: BlockCategory,
    val isExistingEvent: Boolean,            // true = already on calendar, not promotable
    val existingEventId: String? = null,     // reference to existing CalendarEvent
    val notes: String? = null,               // e.g. "change over ~1:00" for laundry
    val sourceTaskId: String? = null,        // link to Google Task if applicable
    val sourceTaskListId: String? = null
)

enum class BlockCategory {
    ACTIVE,           // cognitively demanding: deep work, project tasks
    PASSIVE,          // low-attention: laundry, waiting for delivery
    ERRAND,           // location-based: bank, grocery, post office
    SOCIAL,           // calls, meetings, hangouts
    LEISURE,          // reading, walks, relaxation
    EXISTING_EVENT    // already on calendar — displayed but not created
}
```

## 4. Gemini Prompt Architecture

### 4.1 Initial Planning Prompt

The prompt receives:
- **Raw transcription** of the user's brain dump
- **Existing calendar events** for the target date (title, start, end, all-day flag)
- **Existing tasks** across all lists (title, due date, urgency, list name)
- **Current time** (so the plan starts from now, not midnight)
- **Target date** (today by default, or explicitly named)

**System instruction (condensed):**

```
You are a day planning assistant. The user has described what they want to accomplish.
Produce a time-blocked schedule as a JSON array of plan blocks.

Rules:
1. NEVER move or modify existing calendar events. Schedule around them.
2. Categorize each block: ACTIVE (cognitively demanding), PASSIVE (low-attention, may need proximity),
   ERRAND (location-based), SOCIAL (calls, meetings), LEISURE (reading, relaxation).
3. Energy curve: schedule ACTIVE tasks in the morning (before noon), PASSIVE/LEISURE in the evening.
   ERRAND blocks should cluster together to minimize travel.
4. For PASSIVE tasks with follow-up (e.g., laundry needs changeover), note the follow-up in the
   "notes" field and schedule a brief follow-up block.
5. Estimate durations reasonably. If the user provided durations, use them.
6. If the user mentioned an existing Google Task by name (fuzzy match against existingTasks),
   set sourceTaskId and sourceTaskListId. Otherwise leave null.
7. Leave buffer time between blocks (at least 15 minutes between location changes).
8. For today, start from {currentTime rounded to next half-hour}. For future days, start from 7:00 AM.
9. Dog walks typically 30 minutes. Runs typically 30-45 minutes. Bank visits ~30 minutes.
   Use common sense for unlisted durations.
10. Return confidence 0.0-1.0 for the overall plan quality. If confidence < 0.5,
    add a "warning" field explaining what you're unsure about.

Response JSON schema:
{
  "targetDate": "YYYY-MM-DD",
  "confidence": 0.0-1.0,
  "warning": "optional — present when confidence < 0.5",
  "summary": "human-readable summary",
  "blocks": [
    {
      "title": "string",
      "startTime": "HH:mm",
      "endTime": "HH:mm",
      "category": "ACTIVE|PASSIVE|ERRAND|SOCIAL|LEISURE",
      "isExistingEvent": false,
      "existingEventId": null,
      "notes": "optional string",
      "sourceTaskId": null,
      "sourceTaskListId": null
    }
  ]
}
```

### 4.2 Adjustment Prompt

When the user requests changes, the prompt includes:
- **Previous plan** (full JSON)
- **Adjustment request** (raw transcription)
- **Same context** (events, tasks, time)

System instruction appends:

```
The user wants to modify the previous plan. Apply their requested changes while keeping
the rest of the plan intact. Re-optimize timing if the change cascades (e.g., moving a
block earlier frees a later slot). Return the complete updated plan, not just the diff.
```

### 4.3 Model Selection

Use `gemini-2.5-flash` (not the lite variant used for voice capture). Day planning requires stronger reasoning about temporal constraints, logistics, and energy modeling. The response is a single structured JSON — no streaming needed.

**Configuration:**
- Temperature: 0.3 (slightly creative for scheduling trade-offs, but mostly deterministic)
- Response MIME type: `application/json`
- Timeout: 30s connect, 45s read (planning prompts are heavier than voice parsing)
- Retries: 2 on 429/500/503, 2s/4s backoff

## 5. UI Design

### 5.1 Voice FAB on Calendar Views

Identical to the task page voice FAB:
- 56.dp circular button, `accentPrimary` background at 85% alpha
- Material `Icons.Outlined.Mic`, 24.dp, `surfaceBlack` tint
- Positioned bottom-end with 32.dp padding
- Visible when: not loading, not in ambient mode, Gemini key present, `DayOrganizerState` is Idle
- Click: checks audio permission, then calls `viewModel.startDayOrganizer()`

**Encoder navigation:** The FAB is a focus node in the calendar's key event chain. In DAY/THREE_DAY mode, navigating past the last time slot focuses the FAB. In MONTH/WEEK mode, navigating past the last date focuses it. Click activates.

### 5.2 Listening Overlay

Reuses the existing voice overlay pattern from TaskWallScreen:
- Full-screen `Box`, `Color.Black.copy(alpha = 0.75f)`
- Centered `WaveformVisualizer` (200.dp) driven by `amplitudeLevel`
- Label above waveform: "PLANNING YOUR DAY" (or "ADJUSTING PLAN" if `isAdjustment`)
- Label below waveform: "Click to finish speaking"
- Single click stops listening and transitions to Processing
- Back/Escape cancels and returns to Idle

### 5.3 Processing State

- Same full-screen dim
- Centered `CircularProgressIndicator` (48.dp)
- Text: "Planning your day..."
- Not interruptible (no cancel during Gemini call — it's short)

### 5.4 Plan Preview Overlay

The core new UI. A scrollable timeline showing the complete day plan.

**Layout:**
```
┌─────────────────────────────────────────┐
│  Today's Plan          8 blocks · 6 new │  ← Header
├─────────────────────────────────────────┤
│  7:00  Dog walk (morning)               │  ← PlanBlock (new)
│         7:00 - 7:30 · Active            │
│                                         │
│  7:30  Run                              │  ← PlanBlock (new)
│         7:30 - 8:15 · Active · Peak     │
│                                         │
│  9:00  Team standup                     │  ← Existing event (dimmed)
│         9:00 - 9:30 · Existing          │
│                                         │
│  9:30  Deep work: Project X             │  ← PlanBlock (new)
│         9:30 - 11:30 · Active · Focus   │
│  ...                                    │
├─────────────────────────────────────────┤
│   [ Accept ]    [ Adjust ]    [ Cancel ]│  ← Action bar
└─────────────────────────────────────────┘
```

**Visual distinction between new and existing:**
- **New plan blocks:** `surfaceCard` background with dashed border in plan accent color (muted green, e.g., `Color(0xFF5A8B5A)` at low alpha). Left accent bar in plan color.
- **Existing events:** Same styling as normal calendar events but at reduced opacity (60%). Not selectable.
- **Category labels:** Shown as subtle text beneath the time range ("Active · Peak focus", "Passive · Home needed", "Errand cluster").
- **Notes:** Shown inline in italics ("change over ~1:00").
- **No emoji in block titles.** Titles are plain text only — consistent with the app's calm, typographic design language.
- **Low-confidence warning:** If `plan.warning` is non-null (confidence < 0.5), show a muted warning line below the header: e.g., "Some timing may be off — consider adjusting."

**Encoder navigation in PlanReady state:**
- Rotate cycles through the 3 action buttons: Accept (0), Adjust (1), Cancel (2)
- The focused button gets the standard glow highlight
- Click on Accept → Confirming state
- Click on Adjust → Adjusting state (voice listening)
- Click on Cancel → Idle (return to calendar)
- The timeline itself is scrollable but not encoder-navigable item-by-item (it's a read-only preview, not an editor)

### 5.5 Confirmation Flow

When the user clicks Accept:
1. State transitions to `Confirming`
2. All `PlanBlock` items where `isExistingEvent == false` are promoted to Google Calendar events via `calendarRepository.createEvent()`
3. Events are created sequentially (not parallel) to avoid rate limiting
4. On success: transient message "✓ N events added to your calendar" (2-second auto-dismiss)
5. State returns to Idle, calendar view refreshes to show new events
6. On partial failure: transient error "Created N/M events — some failed" + state returns to Idle

### 5.6 Error State

- Card with "Planning Error" header, error message, "Click to dismiss"
- If `canRetry`: "Click to try again" instead — returns to Listening
- Same visual pattern as voice error card

## 6. ViewModel Integration

### 6.1 New State Exposure

```kotlin
// In TaskWallViewModel
private val _dayOrganizerState = MutableStateFlow<DayOrganizerState>(DayOrganizerState.Idle)
val dayOrganizerState: StateFlow<DayOrganizerState> = _dayOrganizerState.asStateFlow()
```

### 6.2 New Public Methods

```kotlin
fun startDayOrganizer()              // Transitions Idle → Listening, starts VoiceCaptureManager
fun stopDayOrganizerListening()      // Transitions Listening → Processing, stops recording, sends to Gemini
fun acceptDayPlan()                  // Transitions PlanReady → Confirming, creates calendar events
fun adjustDayPlan()                  // Transitions PlanReady → Adjusting (Listening)
fun cancelDayOrganizer()             // Any state → Idle
fun retryDayOrganizer()              // Error → Listening
```

### 6.3 Coordinator Lifecycle

`DayOrganizerCoordinator` is created lazily on first `startDayOrganizer()` call. It holds:
- Reference to `VoiceCaptureManager` (shared with voice capture pipeline)
- Reference to `GeminiCaptureRepository` (shared)
- The current `DayPlan` (for adjustment context)
- The raw transcription (for adjustment context)

**Conflict with voice capture:** The `VoiceCaptureManager` is shared. When Day Organizer is active, the coordinator takes ownership of `rawResultCallback`. When Day Organizer returns to Idle, ownership reverts to `VoiceParsingCoordinator`. This is a simple swap — only one pipeline is active at a time.

## 7. Calendar Event Promotion

When the plan is accepted, each non-existing `PlanBlock` becomes a Google Calendar event:

```kotlin
for (block in plan.blocks.filter { !it.isExistingEvent }) {
    val result = calendarRepository.createEvent(
        title = block.title,
        startDateTime = block.startTime,
        endDateTime = block.endTime,
        calendarId = selectedCalendarId,
        description = buildDescription(block)   // category, notes, source task link
    )
    // Track success/failure count
}
```

**Description format:**
```
[Day Organizer] Active · Peak focus
Notes: change over ~1:00
Source task: "Buy groceries" (list: Errands)
```

The `[Day Organizer]` tag allows future identification of plan-created events (for potential "undo plan" feature later).

If a `PlanBlock` has `sourceTaskId` set, the event is also tagged with `[todowallapp:task:{taskId}]` — the same convention used by task-to-event promotion. This means the task will show as "scheduled" in the task wall view.

## 8. Edge Cases

| Scenario | Handling |
|----------|----------|
| No Gemini API key configured | FAB hidden (same as task voice FAB) |
| No calendar access granted | Show AccessRequiredCard instead of plan preview |
| Empty brain dump (silence or too short) | Error: "I didn't catch that. Click to try again." |
| No available time slots (day fully booked) | Gemini returns plan with only existing events + summary: "Your day is fully booked. No new blocks could be scheduled." Accept button disabled. |
| Gemini returns invalid JSON | Error with retry. Falls back gracefully — no partial state. |
| User says "plan my Saturday" on a Wednesday | Gemini extracts target date. Plan starts from wake time (7:00 AM), fetches Saturday's events. |
| Adjustment contradicts physics (overlap) | Gemini resolves — it always returns a complete re-plan, not a patch. |
| Network loss during Gemini call | Error: "Couldn't reach the planning service. Check your connection." Retry available. |
| Network loss during event creation | Partial creation tracked. Message: "Created N/M events." Already-created events remain. |
| VoiceCaptureManager already in use (voice task add active) | Day Organizer FAB not shown while voice state is not Idle. Mutually exclusive. |

## 9. Scope Boundaries

### In Scope (this spec)
- Voice FAB on calendar views
- Brain dump → Gemini planning → preview → accept/adjust/cancel
- Calendar event promotion for accepted plans
- Hardcoded energy curve in Gemini prompt
- Wall-only (encoder + single click interaction)

### Out of Scope (future)
- Phone mode entry point (Phase 2)
- User-configurable energy profile / morning-person vs night-owl
- Learning from past scheduling patterns
- Undo entire plan (delete all plan-created events)
- Integration with Google Tasks (creating tasks from plan, not just linking existing)
- Multi-day planning ("plan my weekend")
- Recurring plan templates ("every Monday looks like this")

## 10. File Changes

| File | Changes |
|------|---------|
| **NEW** `capture/DayOrganizerCoordinator.kt` | State machine, Gemini orchestration, plan data models |
| **NEW** `ui/components/DayOrganizerOverlay.kt` | Plan preview timeline, action bar, listening/processing states |
| `capture/repository/GeminiCaptureRepository.kt` | Add `buildDayPlanPrompt()`, `buildPlanAdjustmentPrompt()`, `parseDayPlan()` |
| `viewmodel/TaskWallViewModel.kt` | Add `dayOrganizerState` StateFlow, 6 public methods, coordinator wiring |
| `ui/screens/CalendarScreen.kt` | Add voice FAB, overlay rendering, encoder navigation for FAB + plan preview |
| `data/model/CalendarEvent.kt` | Add `DayPlan`, `PlanBlock`, `BlockCategory` data classes (or new file) |
| `ui/theme/Color.kt` | Add plan accent color (`planAccent = Color(0xFF5A8B5A)`) |
