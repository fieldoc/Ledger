# Design: Task Management Gaps — Search, Notes, Reorder, Priority, Recurrence
**Date:** 2026-04-02
**Status:** Approved
**Scope:** Five foundational task management features adapted for rotary-encoder-only wall kiosk interaction.

---

## 1. Problem Statement

The wall app handles task display well but lacks basic management capabilities users expect:

1. **Search/Filter** — No way to find a specific task across lists
2. **Task Notes** — Google Tasks notes are in the data model but only show as 1 truncated line
3. **Manual Reordering** — No way to reprioritize tasks (drag is impossible with encoder)
4. **Priority Levels** — Only HIGH/NORMAL; no MEDIUM tier
5. **Recurring Task Management** — Creation infrastructure exists but no encoder-based create/edit/skip/remove

---

## 2. Constraints

- **Encoder-only input**: 3 inputs — CW (navigate down), CCW (navigate up), click (select). No hold detection on hardware.
- **Double-click**: Already mapped to context menu open.
- **Calm UI**: No visual noise, no persistent chrome additions beyond what's necessary.
- **Google Tasks API**: No native priority/recurrence fields. Metadata stored in notes via `||KEY:VALUE||` tags (existing pattern).
- **Offline**: Core features (search, filter) must work client-side. API-dependent features (reorder, priority sync) degrade gracefully.
- **100-task cap per list**: Existing limitation; search/filter operates within this bound.

---

## 3. Feature 1: Search & Filter

### 3.1 Entry Point

A new focus node — magnifying glass icon (🔍) — in the top-left area, alongside the existing settings gear. Rendered at reduced opacity (0.4) when unfocused, brightens to full on focus. Follows the same `FocusNode` pattern as the settings button.

Focus order: `[Search] → [Settings] → [Folder Headers → Tasks...]`

### 3.2 Search & Filter Overlay

Clicking the search icon opens a centered overlay card (same visual language as `TaskContextMenu`):

```
┌───────────────────────────────┐
│  Find Tasks                   │
│                               │
│  ▶ 🎙 Voice Search            │
│  ──────────────────           │
│  Quick Filters:               │
│    Overdue                    │
│    Due Today                  │
│    Due This Week              │
│    High Priority              │
│    Recurring                  │
│                               │
│  [Clear All Filters]          │
└───────────────────────────────┘
```

- CW/CCW navigates items. Click toggles a filter or activates voice search.
- Multiple filters can be active simultaneously (AND logic).
- Active filters are highlighted with accent background.

### 3.3 Voice Search

Clicking "Voice Search" triggers the existing voice overlay (dim + waveform). The transcription is used for **client-side fuzzy text matching** — no Gemini needed.

Search algorithm:
1. Split query into lowercase words
2. For each task across all lists, score = count of query words found in `task.title.lowercase()` + `task.cleanNotes?.lowercase()`
3. Tasks with score > 0 are results, sorted by score descending, then by urgency

### 3.4 Results View

When search/filter is active:
- Accordion folders are replaced by a **flat cross-list result list**
- Each result task card shows a small **list-name badge** (labelSmall, muted) below the title
- Navigation works identically to normal task list (CW/CCW scroll, click to complete/interact)
- Double-click on a result opens context menu as normal

### 3.5 Active Filter Indicator

When any filter or search is active, a muted tag appears below the clock header: `"🔍 Overdue · High Priority"` (joined active filter names). Clicking the search icon re-opens the overlay with current state.

### 3.6 Exiting Search/Filter

- Navigate to the search icon and click → re-opens overlay → click "Clear All Filters"
- Or: overlay has "Clear All Filters" at the bottom as a focusable action
- Clearing filters restores the normal accordion folder view

### 3.7 Data Model

```kotlin
enum class TaskFilter {
    OVERDUE, DUE_TODAY, DUE_THIS_WEEK, HIGH_PRIORITY, RECURRING
}
```

New `TaskWallUiState` fields:
```kotlin
val searchQuery: String? = null,          // active voice search text
val activeFilters: Set<TaskFilter> = emptySet(),
val isSearchOverlayVisible: Boolean = false
```

New ViewModel methods:
- `toggleFilter(filter: TaskFilter)`
- `setSearchQuery(query: String)`
- `clearSearch()` — clears query + filters
- `filteredTasks: List<Pair<Task, String>>` — derived: matching tasks with their list names

---

## 4. Feature 2: Task Notes / Detail View

### 4.1 Inline Note Expansion

When a task is encoder-focused, the card expands to show up to **3 lines** of `cleanNotes` (currently 1 line). Unfocused tasks revert to 1 line. Animation: ~250ms ease-in-out height transition.

Implementation: Change `maxLines` parameter in TaskItem's notes `Text` composable from 1 to `if (isSelected) 3 else 1`, with `animateContentSize()` on the card.

### 4.2 Task Detail Overlay

A new context menu action **"View Details"** (first position for all tasks) opens a full detail overlay:

```
┌───────────────────────────────────┐
│  Task Details                     │
│  ─────────────────────────        │
│  Buy groceries                    │
│                                   │
│  Need to get: milk, eggs, bread,  │
│  cheese, vegetables for the soup, │
│  and don't forget paper towels    │
│  ─────────────────────────        │
│  📅 Due: Tomorrow                 │
│  ● Priority: High                 │
│  ↻ Every week on Sunday           │
│  📁 List: Shopping                │
│  🔢 Subtasks: 3/5 done           │
│                                   │
│  [Close]                          │
└───────────────────────────────────┘
```

- Centered card, same visual style as context menu
- CW/CCW scrolls notes if they exceed the visible area
- Click on [Close] dismisses
- Read-only — editing happens on phone via Google Tasks app

### 4.3 What the Detail Overlay Shows

| Field | Source | Shown when |
|-------|--------|------------|
| Title | `task.title` | Always |
| Notes | `task.cleanNotes` | When not null/empty |
| Due date | `task.dueDate` with urgency label | When not null |
| Priority | `task.priority` label + pip | When not NORMAL |
| Recurrence | `task.recurrenceRule?.toHumanReadable()` | When not null |
| List name | From parent `TaskListWithTasks.title` | Always |
| Subtask progress | From `SubtaskProgress` map | When task has children |
| Scheduled time | From `scheduledTaskTimes` map | When scheduled |

---

## 5. Feature 3: Manual Reordering (Move Mode)

### 5.1 Move Mode

Context menu → **"Reorder"** enters move mode on the focused task.

**Visual state**:
- Card border changes to warm amber (`colors.accentWarm`)
- Slight vertical stretch (2dp extra padding)
- Position indicator appears: `"3 of 12"` as labelSmall at top-right of card

**Encoder behavior in move mode**:
- CW = move task one position DOWN (swap with task below)
- CCW = move task one position UP (swap with task above)
- Click = confirm position and exit move mode
- Each movement: task card animates to new position (~200ms), adjacent tasks shift

**After confirmation**: Single Google Tasks `tasks.move` API call with final position. Optimistic local update during movement; revert on API failure.

### 5.2 Pin to Top

Separate context menu action **"Pin to Top"**. One click, no mode. Calls `tasks.move(previous=null)` to place the task first in its list.

### 5.3 Constraints

- Reorder only within the same list (Google Tasks API limitation)
- Only pending (incomplete) tasks can be reordered
- Subtasks reorder within their parent group
- Tasks at list boundaries (first/last) stop moving at the edge

### 5.4 API Integration

New repository method:
```kotlin
suspend fun moveTask(
    taskListId: String,
    taskId: String,
    previousTaskId: String?  // null = move to first position
): Result<Task>
```

Wraps `client.tasks().move(taskListId, taskId).setPrevious(previousTaskId).execute()`.

### 5.5 ViewModel State

```kotlin
val reorderModeTaskId: String? = null,    // task currently being reordered
val reorderOriginalIndex: Int? = null     // for revert on cancel/failure
```

New methods: `enterReorderMode(taskId)`, `moveReorderUp()`, `moveReorderDown()`, `confirmReorder()`, `cancelReorder()`

---

## 6. Feature 4: Priority Levels

### 6.1 Three-Level Priority

Expand `TaskPriority`:
```kotlin
enum class TaskPriority { HIGH, MEDIUM, NORMAL }
```

### 6.2 Metadata Encoding

- `||PRIORITY:high||` → HIGH
- `||PRIORITY:medium||` → MEDIUM
- No tag → NORMAL

Backward compatible: `decode()` already uses `TaskPriority.valueOf(str.uppercase())` which handles "MEDIUM" automatically. `encode()` needs to emit the tag for MEDIUM (currently only emits for HIGH).

### 6.3 Visual Indicators

| Level | Indicator | Description |
|-------|-----------|-------------|
| HIGH | ● filled pip | 5dp circle, `accentPrimary` at 70% — existing |
| MEDIUM | ◐ hollow pip | 5dp circle, `accentPrimary` at 35%, 1dp border only |
| NORMAL | (none) | No indicator — existing |

### 6.4 Sort Order

`taskDisplayComparator` updated:
```
isCompleted → priorityRank(HIGH=0, MEDIUM=1, NORMAL=2) → urgencyRank → dueDate → position → updatedAt
```

### 6.5 Setting Priority via Context Menu

Context menu → **"Priority"** → sub-menu replaces main menu content:

```
┌─────────────────────────┐
│  Set Priority            │
│                          │
│  ▶ ● High               │
│    ◐ Medium    ← current │
│    ○ Normal              │
│                          │
│  [Back]                  │
└─────────────────────────┘
```

Current priority is highlighted. Click selects, updates metadata, syncs via `updateTaskNotes()`, and dismisses.

### 6.6 API Sync

New repository method:
```kotlin
suspend fun updateTaskNotes(
    taskListId: String,
    taskId: String,
    notes: String
): Result<Task>
```

Wraps `client.tasks().patch(taskListId, taskId, Task().setNotes(notes)).execute()`.

When priority changes, the ViewModel:
1. Re-encodes metadata: `TaskMetadata.encode(task.cleanNotes, task.recurrenceRule, newPriority, task.preferredTime)`
2. Calls `updateTaskNotes()` with the encoded string
3. Updates local UI optimistically

### 6.7 Voice Integration

Update Gemini prompt Section 10 (PRIORITY DETECTION):
- HIGH: "must", "need to", "critical", "don't forget", "can't miss"
- MEDIUM: "should", "would be good to", "try to", "important", "want to"
- NORMAL: everything else

Update `parseVoiceResponseJson()` to handle `"medium"` value.

---

## 7. Feature 5: Recurring Task Encoder Management

### 7.1 New Context Menu Actions

| Action | Shown when | Effect |
|--------|-----------|--------|
| Make Recurring | `task.recurrenceRule == null` | Opens recurrence picker |
| Edit Recurrence | `task.recurrenceRule != null` | Opens picker with current values |
| Remove Recurrence | `task.recurrenceRule != null` | Strips RECUR tag after confirmation |
| Skip This Time | `task.recurrenceRule != null` && `!task.isCompleted` | Completes without spawning next |

### 7.2 Recurrence Picker Overlay

**Quick patterns** (covers ~90% of use cases):

```
┌─────────────────────────────┐
│  Set Recurrence             │
│                             │
│  ▶ Every day                │
│    Every weekday (Mon-Fri)  │
│    Every week               │
│    Every 2 weeks            │
│    Every month              │
│  ──────────────────         │
│    Custom...                │
│                             │
│  [Cancel]                   │
└─────────────────────────────┘
```

Click on a quick pattern immediately sets the recurrence and dismisses.

**Quick pattern mapping:**
| Pattern | RecurrenceRule |
|---------|---------------|
| Every day | `DAILY, 1, null` |
| Every weekday | `WEEKLY, 1, null` + special handling (out of scope for v1 — use DAILY,1 as approximation, or skip this option) |
| Every week | `WEEKLY, 1, <current due date's day-of-week or null>` |
| Every 2 weeks | `WEEKLY, 2, <current due date's day-of-week or null>` |
| Every month | `MONTHLY, 1, <current due date's day-of-month or null>` |

Note: "Every weekday" doesn't map cleanly to the existing RecurrenceRule model (which supports single anchor only). **Drop this option for v1** — 4 quick patterns + Custom is sufficient.

**Custom editor** (selected via "Custom..."):

```
┌─────────────────────────────┐
│  Custom Recurrence          │
│                             │
│  Repeat: [Weekly    ▼]     │
│  Every:  [1         ▼]     │
│  On:     [Monday    ▼]     │
│                             │
│  [Save]  [Cancel]           │
└─────────────────────────────┘
```

- 3 fields + 2 buttons = 5 focus nodes
- Click on a `[▼]` field enters edit mode: CW/CCW cycles values, click confirms
- "Repeat" cycles: Daily / Weekly / Monthly
- "Every" cycles: 1–12
- "On" cycles: day-of-week (for Weekly) or day-of-month 1–31 (for Monthly); hidden for Daily
- [Save] encodes and syncs; [Cancel] dismisses

### 7.3 Remove Recurrence

Confirmation overlay: "Remove recurrence from '{task.title}'? The task will remain but won't repeat."
- [Remove] / [Cancel]
- On confirm: re-encode notes without recurrence, call `updateTaskNotes()`

### 7.4 Skip This Time

Calls `completeTask()` with a `skipSpawn: Boolean = false` parameter. When `true`, the ViewModel's post-completion recurrence check is bypassed. The task completes and drifts to the bottom as normal, but no next instance is created.

Implementation: Add `skipSpawn` parameter to `completeTask()` in ViewModel. The existing spawn logic checks `if (task.recurrenceRule != null && !skipSpawn)`.

---

## 8. Context Menu Consolidation

### 8.1 Pending Task Menu

```
1. View Details
2. Priority        → sub-menu
3. Reorder         → enters move mode
4. Pin to Top
5. [Make Recurring | Edit Recurrence]  → contextual
6. [Skip This Time]                    → only if recurring
7. [Remove Recurrence]                 → only if recurring
8. Schedule Task   → existing
9. Delete Task     → existing, destructive
```

Typical visible count: 6-7 items (non-recurring tasks show 6, recurring show 8 with skip/remove).

### 8.2 Completed Task Menu

```
1. View Details
2. Restore to Pending  → existing
3. Delete Task         → existing
```

### 8.3 Sub-Menu Pattern

When an action has a sub-menu (Priority), clicking it replaces the main menu content with the sub-menu items plus a [Back] option. Same CW/CCW + click navigation. No nested flyouts.

---

## 9. Files Changed

| File | Change |
|------|--------|
| `data/model/TaskMetadata.kt` | Add `MEDIUM` to `TaskPriority`; update `encode()` to emit `medium` tag |
| `data/model/Task.kt` | Update `taskDisplayComparator` with MEDIUM priority rank; add `TaskFilter` enum |
| `data/repository/GoogleTasksRepository.kt` | Add `moveTask()` and `updateTaskNotes()` methods |
| `viewmodel/TaskWallViewModel.kt` | Add search/filter state, reorder state, and all new methods |
| `ui/components/TaskContextMenu.kt` | Support sub-menus and dynamic action lists |
| `ui/components/TaskItem.kt` | MEDIUM priority pip; 3-line notes on focus; list-name badge for search results |
| `ui/components/SearchFilterOverlay.kt` | **New** — search/filter overlay composable |
| `ui/components/TaskDetailOverlay.kt` | **New** — full task details view |
| `ui/components/RecurrencePickerOverlay.kt` | **New** — recurrence pattern selection + custom editor |
| `ui/screens/TaskWallScreen.kt` | Search icon focus node; search results view; move mode key handling; overlay wiring |
| `capture/repository/GeminiCaptureRepository.kt` | Update prompt for MEDIUM priority detection |

---

## 10. Out of Scope

- Note editing from the wall (use Google Tasks on phone)
- "Every weekday" recurrence pattern (doesn't map to single-anchor RecurrenceRule)
- Recurrence history / streak tracking
- Cross-list task moving (Google Tasks API limitation)
- Advanced search (date-range queries, regex) — voice + preset filters cover needs
- Filter persistence across sessions (filters reset on app restart)
- Batch operations (multi-select tasks for bulk priority/delete)
