# Design: Recurring Tasks & Voice Priority
**Date:** 2026-03-29
**Status:** Approved
**Scope:** Two new voice-captured task attributes — recurrence rules and semantic priority — stored via notes-field metadata and surfaced on the wall display.

---

## 1. Problem Statement

The app currently has no way to express that a task repeats (e.g. "clean the kitchen every Sunday") or that the user considers it especially important ("I absolutely need to return that call tomorrow"). Both attributes are natural to speak but have no home in the current data model or voice pipeline.

---

## 2. Constraints

- **Google Tasks API** has no native recurrence or priority fields. Only `title`, `notes`, `due`, `status`, `parentId` are writable.
- **No Room database** exists in the project — adding one is a heavy dependency to avoid if possible.
- **Physical input** is encoder-only (rotate + single click). No hold gestures are reliable on hardware.
- **UI must stay calm** — no loud color changes, no gamification, no badges that shout.

---

## 3. Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Where to store metadata | Notes-field hidden tags | Travels with the task across devices/reinstalls; no new dependencies |
| On recurring task completion | Create fresh task with next due date | Preserves existing completion animation; clean history |
| Recurrence patterns | Simple + interval (daily/weekly/monthly, every N) | Covers real use cases; speakable naturally |
| Priority effect | Pin to top of list | Most functional signal on a wall display |
| Priority visual | Subtle left-edge pip (filled circle) | Visible from 6ft; distinct from urgency amber |

---

## 4. Metadata Format

All metadata is encoded as `||KEY:VALUE||` tags prepended to the `notes` field. The app strips all `||...||` blocks before displaying notes anywhere.

### 4.1 Recurrence Tag

```
||RECUR:<frequency>:<interval>:<anchor>||
```

| Field | Values |
|---|---|
| `frequency` | `daily`, `weekly`, `monthly` |
| `interval` | Integer ≥ 1 (1 = every, 2 = every other, etc.) |
| `anchor` | Day-of-week for weekly (`MONDAY`–`SUNDAY`), day-of-month for monthly (`1`–`31`), empty for no anchor |

**Examples:**

| Voice phrase | Encoded tag |
|---|---|
| "every day" | `\|\|RECUR:daily:1:\|\|` |
| "every week on Sunday" | `\|\|RECUR:weekly:1:SUNDAY\|\|` |
| "every 2 weeks on Monday" | `\|\|RECUR:weekly:2:MONDAY\|\|` |
| "every other week" | `\|\|RECUR:weekly:2:\|\|` |
| "every month on the 15th" | `\|\|RECUR:monthly:1:15\|\|` |
| "twice a month" | `\|\|RECUR:monthly:1:15\|\|` *(interval 2 weeks approx)* |

### 4.2 Priority Tag

```
||PRIORITY:high||
```

Only written when priority is HIGH. Normal tasks have no priority tag (absence = normal).

### 4.3 Combined Example

A high-priority weekly recurring task with user notes:
```
||RECUR:weekly:1:SUNDAY||PRIORITY:high||Remember to use the new mop
```

Displayed notes on wall: `Remember to use the new mop`

---

## 5. Data Model Changes

### 5.1 New types (new file: `data/model/TaskMetadata.kt`)

```kotlin
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int,         // 1 = every occurrence, 2 = every other, etc.
    val anchor: String?        // "MONDAY"-"SUNDAY" for weekly; "1"-"31" for monthly; null = no anchor
) {
    fun nextDueDate(from: LocalDate): LocalDate { ... }
}

enum class TaskPriority { HIGH, NORMAL }

object TaskMetadata {
    fun encode(notes: String?, recurrence: RecurrenceRule?, priority: TaskPriority): String
    fun decode(rawNotes: String?): DecodedMetadata
}

data class DecodedMetadata(
    val recurrenceRule: RecurrenceRule?,
    val priority: TaskPriority,
    val cleanNotes: String?
)
```

### 5.2 Extended `Task` model

```kotlin
data class Task(
    // ... existing fields ...
    val recurrenceRule: RecurrenceRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL
)
```

`Task` is populated from `toAppTask()` by decoding the `notes` field via `TaskMetadata.decode()`.

### 5.3 Extended `ParsedVoiceTaskItem`

```kotlin
data class ParsedVoiceTaskItem(
    // ... existing fields ...
    val recurrenceRule: RecurrenceRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL
)
```

---

## 6. Next-Due-Date Calculation

`RecurrenceRule.nextDueDate(from: LocalDate)` logic:

| Frequency | Anchor | Calculation |
|---|---|---|
| DAILY | — | `from + interval days` |
| WEEKLY | day-of-week | Find next occurrence of that day after `from`, then add `(interval - 1) * 7` days |
| WEEKLY | none | `from + interval * 7 days` |
| MONTHLY | day number | Same day `interval` months ahead (clamped to last day of month if needed) |
| MONTHLY | none | `from + interval * 30 days` *(approximation)* |

---

## 7. Gemini Prompt Extension

Two new numbered sections added to `buildVoicePromptV2()`:

### Section 9 — RECURRENCE DETECTION

Instruct Gemini to detect recurring phrasing and return a structured `recurrence` object:

- "every day" / "daily" → `{frequency: "daily", interval: 1, anchor: null}`
- "every week on Sunday" → `{frequency: "weekly", interval: 1, anchor: "SUNDAY"}`
- "every 2 weeks" / "every other week" → `{frequency: "weekly", interval: 2, anchor: null}`
- "weekly" with no day → `{frequency: "weekly", interval: 1, anchor: null}`
- "every month" → `{frequency: "monthly", interval: 1, anchor: null}`
- "every month on the 15th" → `{frequency: "monthly", interval: 1, anchor: "15"}`
- No recurrence language → `null`

### Section 10 — PRIORITY DETECTION

Strong urgency signals → `priority: "high"`:
- Intensity words: "need to", "must", "have to", "absolutely", "critical", "important", "do not forget", "don't forget", "make sure", "can't miss", "really need"
- Stress/emphasis patterns: all-caps words, repeated emphasis ("I NEED to"), heavy hedging ("whatever you do")
- Default: `priority: "normal"`

### Updated JSON schema

```json
{
  "intent": "...",
  "tasks": [
    {
      "title": "string",
      "dueDate": "YYYY-MM-DD|null",
      "preferredTime": "morning|afternoon|evening|null",
      "targetListId": "string|null",
      "newListName": "string|null",
      "parentTaskId": "string|null",
      "confidence": 0.0,
      "duplicateOf": "string|null",
      "recurrence": {
        "frequency": "daily|weekly|monthly",
        "interval": 1,
        "anchor": "string|null"
      } | null,
      "priority": "high|normal"
    }
  ],
  "clarification": "string|null"
}
```

---

## 8. Repository Changes

`GoogleTasksRepository.createTask()` gains a `notes` parameter:

```kotlin
suspend fun createTask(
    taskListId: String,
    title: String,
    dueDate: LocalDate? = null,
    parentId: String? = null,
    notes: String? = null      // ← new
): Result<Task>
```

---

## 9. ViewModel: Recurring Task Spawning

After a successful `completeTask()` call, the ViewModel checks `task.recurrenceRule`:

```
if task.recurrenceRule != null:
    nextDue = task.recurrenceRule.nextDueDate(from = task.dueDate ?: today)
    encodedNotes = TaskMetadata.encode(task.cleanNotes, task.recurrenceRule, task.priority)
    tasksRepository.createTask(
        taskListId = currentListId,
        title = task.title,
        dueDate = nextDue,
        notes = encodedNotes
    )
    refresh task list
```

The completed task stays completed (drifts to bottom as normal). The new instance appears at the appropriate sorted position.

---

## 10. Sorting Changes

`taskDisplayComparator` updated: HIGH priority tasks sort before NORMAL within the same completion tier, above all urgency levels:

```
isCompleted → priorityRank (HIGH=0, NORMAL=1) → urgencyRank → dueDate → position → updatedAt
```

---

## 11. UI Changes

### 11.1 Priority pip (TaskItem.kt)

A small filled circle (5dp, accent color, 70% opacity) on the left edge of the task card, visible only when `priority == HIGH`. Positioned vertically centered alongside the task title row. No tooltip, no label — presence alone is the signal.

### 11.2 Recurrence indicator (TaskItem.kt)

A small `↻` glyph rendered as `labelSmall` text after the task title, in `onSurface.copy(alpha = 0.4f)`. Appears only when `recurrenceRule != null`. Communicates "this will come back" without demanding attention.

### 11.3 Draft card preview

The voice confirmation draft card (already implemented) shows:
- Priority pip if `priority == HIGH`
- `↻ Every Sunday` style badge if `recurrenceRule != null` (compact human-readable form)

---

## 12. Files Changed

| File | Change |
|---|---|
| `data/model/TaskMetadata.kt` | **New** — RecurrenceRule, TaskPriority, TaskMetadata utility |
| `data/model/Task.kt` | Add `recurrenceRule`, `priority` fields; decode in `toAppTask()` |
| `data/repository/GoogleTasksRepository.kt` | Add `notes` param to `createTask()` |
| `capture/repository/GeminiCaptureRepository.kt` | Extend `ParsedVoiceTaskItem`, extend prompt, extend JSON parser |
| `viewmodel/TaskWallViewModel.kt` | Spawn next recurrence on completion; pass notes to createTask |
| `ui/components/TaskItem.kt` | Priority pip + recurrence glyph |
| `ui/screens/TaskWallScreen.kt` | Draft card preview shows recurrence badge + priority pip |

---

## 13. Out of Scope

- Editing or cancelling a recurrence after creation (requires new UI flow)
- "Last day of month" anchor
- Calendar-based recurrence (Google Calendar recurring events)
- Priority levels beyond HIGH/NORMAL
- Recurrence history / streak tracking
