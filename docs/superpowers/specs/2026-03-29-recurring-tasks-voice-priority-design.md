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

All metadata is encoded as `||KEY:VALUE||` tags prepended to the `notes` field. The app strips all `||...||` blocks before displaying notes anywhere. User notes never contain `||` in practice; this is an acceptable risk given the low probability.

`encode()` always omits the `PRIORITY` tag entirely when `priority == NORMAL`. Reading absence of the tag is equivalent to NORMAL. This means re-encoding a previously-normal task produces no tag pollution.

### 4.1 Recurrence Tag

```
||RECUR:<frequency>:<interval>:<anchor>||
```

| Field | Values |
|---|---|
| `frequency` | `daily`, `weekly`, `monthly` |
| `interval` | Integer ≥ 1 (1 = every, 2 = every other, etc.) |
| `anchor` | Day-of-week for weekly (`MONDAY`–`SUNDAY`), day-of-month for monthly (`1`–`31`), **empty string** when absent (the trailing `:` is always written). |

**Null/empty bridging rule:** In the tag wire format, an absent anchor is encoded as an empty string (e.g. `||RECUR:daily:1:||`). `decode()` maps an empty string anchor field to `null` in `RecurrenceRule.anchor`. `encode()` maps `null` anchor back to an empty string. This bridging is the responsibility of `TaskMetadata` — no other code should read or write the raw tag format.

**Decode regex:** `decode()` identifies tag blocks using the pattern `\|\|([A-Z]+:[^|]*)\|\|` (greedy match of uppercase key followed by colon-separated values, bounded by `||` delimiters). Any `||` sequences in user notes that do not match this pattern are left in `cleanNotes` unmodified. Tags are matched and removed left-to-right; the remainder after stripping all matches is `cleanNotes`.

**Canonical tag order:** `encode()` always writes tags in this order: RECUR first (if present), then PRIORITY (if present), then user notes. This order is normative — tests should assert it.

**Malformed tag guard:** If a `||RECUR:...||` block cannot be parsed (missing fields, unknown frequency), `decode()` silently ignores it and returns `recurrenceRule = null`. No exception is thrown.

**Round-trip with embedded `||` in user notes:** `encode()` does not sanitize `notes` for embedded `||` sequences. If user notes contain `||`, the combined string may confuse the decoder. This is an accepted known limitation (see Section 4 intro). No escaping is performed.

**PRIORITY tag case-sensitivity:** `decode()` calls `.uppercase()` on the PRIORITY tag value before mapping to `TaskPriority`. The tag format is case-insensitive on read; `encode()` always writes lowercase (`high`).

**Examples:**

| Voice phrase | Encoded tag |
|---|---|
| "every day" | `\|\|RECUR:daily:1:\|\|` |
| "every week on Sunday" | `\|\|RECUR:weekly:1:SUNDAY\|\|` |
| "every 2 weeks on Monday" | `\|\|RECUR:weekly:2:MONDAY\|\|` |
| "every other week" | `\|\|RECUR:weekly:2:\|\|` |
| "every month on the 15th" | `\|\|RECUR:monthly:1:15\|\|` |
| "every month" | `\|\|RECUR:monthly:1:\|\|` |

"Twice a month" and similar sub-monthly multi-occurrence patterns are **out of scope** — see Section 13.

### 4.2 Priority Tag

```
||PRIORITY:high||
```

Only written when priority is HIGH. `encode()` emits no tag for `TaskPriority.NORMAL`. Absence of a PRIORITY tag decodes as NORMAL.

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
    /**
     * Returns the next due date after [from] according to this rule.
     *
     * DAILY: returns from + interval days.
     *
     * WEEKLY with valid anchor (full English day name, e.g. "SUNDAY"):
     *   Result = earliest date >= (from + interval*7) that falls on anchor day-of-week.
     *   If (from + interval*7) already falls on anchor day, it is returned as-is (zero advancement).
     *   If anchor is null or invalid, falls back to WEEKLY no-anchor path.
     *
     * WEEKLY without anchor (or invalid anchor):
     *   Returns from + interval*7 days. Pure rolling interval.
     *
     * MONTHLY with valid anchor ("1"-"31"):
     *   Returns the date interval months after from, on day anchor.toInt(), clamped to month end.
     *   Always reads anchor from this RecurrenceRule — never from from.dayOfMonth.
     *   Always computes the target day from anchor.toInt(), so clamped dates never corrupt future spawns.
     *   If anchor is null or invalid (e.g. "32", "FUNDAY"), falls back to MONTHLY no-anchor path.
     *
     * MONTHLY without anchor:
     *   Returns from + interval*30 days (approximation).
     *
     * This function is pure: it never modifies RecurrenceRule.anchor.
     * No upper bound is enforced on interval; unreasonably large values produce distant future dates.
     * No maximum interval cap is applied.
     */
    fun nextDueDate(from: LocalDate): LocalDate

    /**
     * Human-readable label for display in draft cards and recurrence indicators.
     * Anchor values use full uppercase day names (e.g. "SUNDAY", "MONDAY") matching the wire format.
     * Examples:
     *   DAILY/1/null       → "Every day"
     *   DAILY/2/null       → "Every 2 days"
     *   WEEKLY/1/SUNDAY    → "Every Sunday"
     *   WEEKLY/1/null      → "Every week"
     *   WEEKLY/2/MONDAY    → "Every 2 weeks on Monday"
     *   WEEKLY/2/null      → "Every 2 weeks"
     *   MONTHLY/1/15       → "Every month on the 15th"
     *   MONTHLY/1/null     → "Every month"
     *   MONTHLY/3/null     → "Every 3 months"
     * Ordinal suffixes for monthly anchor: 1st, 2nd, 3rd, 4th–20th, 21st, 22nd, 23rd, 24th–30th, 31st.
     */
    fun toHumanReadable(): String
}

enum class TaskPriority { HIGH, NORMAL }

object TaskMetadata {
    /**
     * Encode recurrence and priority as ||...|| tags prepended to [notes].
     * Tag order: RECUR first (if present), then PRIORITY (if present), then notes.
     * NORMAL priority produces no PRIORITY tag.
     * Null recurrence produces no RECUR tag.
     * Returns empty string (not null) when all inputs are null/default (no recurrence, NORMAL, null notes).
     * Callers may pass this return value directly to createTask(notes=...) — an empty notes string
     * is acceptable to the Google Tasks API and is treated as "clear notes."
     * encode() does NOT sanitize notes for embedded || sequences.
     */
    fun encode(notes: String?, recurrence: RecurrenceRule?, priority: TaskPriority): String

    /**
     * Strip all ||KEY:value|| tag blocks from rawNotes and parse into structured metadata.
     * Uses regex \|\|([A-Z]+:[^|]*)\|\| to identify tags. Non-matching || sequences pass through unchanged.
     * decode() calls .uppercase() on tag values before enum mapping — tags are case-insensitive on read.
     * Malformed or unrecognized tag blocks are silently discarded.
     */
    fun decode(rawNotes: String?): DecodedMetadata
}

data class DecodedMetadata(
    val recurrenceRule: RecurrenceRule?,
    val priority: TaskPriority,
    val cleanNotes: String?     // notes with all ||...|| blocks removed; null if nothing remains
)
```

Performance note: `TaskMetadata.decode()` is called once per task during `toAppTask()`. At the 100-task hard cap this is 100 lightweight string parses per sync — negligible; no caching needed.

### 5.2 Extended `Task` model

The existing `Task` data class (in `data/model/Task.kt`) already has a `notes: String?` field that holds the raw Google Tasks notes value as returned by the API. This field is unchanged — it continues to store raw notes including any `||...||` tags. Three new fields are added:

```kotlin
data class Task(
    // ... existing fields including notes: String? (raw, unchanged) ...
    val recurrenceRule: RecurrenceRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val cleanNotes: String? = null   // notes with metadata tags stripped; use this for ALL display
)
```

`Task` is populated from `toAppTask()`, a private extension function on `com.google.api.services.tasks.model.Task` in `data/repository/GoogleTasksRepository.kt`. It decodes the raw `notes` field via `TaskMetadata.decode()`:
- `task.recurrenceRule` ← `decoded.recurrenceRule`
- `task.priority` ← `decoded.priority`
- `task.cleanNotes` ← `decoded.cleanNotes`
- The existing `task.notes` field retains the raw value (including tags) for re-encoding on spawn.

**Display migration (required):** Any existing code that renders `task.notes` to the UI must be changed to render `task.cleanNotes` instead. This applies specifically to `TaskItem.kt` where notes are currently displayed beneath the task title. Failing to do this will expose raw `||...||` tags to the user. This is listed explicitly in the Files Changed table (Section 12).

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
| WEEKLY | valid day-of-week | Result = earliest date >= (from + interval×7) that falls on anchor day-of-week. If (from + interval×7) already falls on anchor day, it is returned as-is (zero advancement). **Example (interval=2, MONDAY):** from=Sun March 29 → March 29+14=Sun April 12 → next Monday = **April 13**. **Example (interval=1, MONDAY):** from=Sun March 29 → March 29+7=Sun April 5 → next Monday = **April 6**. **Example (from is already anchor day, interval=1, MONDAY):** from=Mon March 30 → March 30+7=Mon April 6 → already Monday → return **April 6**. |
| WEEKLY | null or invalid | `from + interval * 7 days`. Pure rolling interval — does not snap to a weekday. Unanchored weekly/monthly tasks use rolling intervals; "complete late" causes drift. This is accepted — see Section 13. |
| MONTHLY | valid "1"–"31" | `anchor.toInt()`-th day of the month that is `interval` months after `from.month`, clamped to month end if needed. Always reads day from `anchor`, never from `from.dayOfMonth`. Always computes from original anchor — clamped dates never corrupt future spawns. |
| MONTHLY | null or invalid | `from + interval * 30 days` (approximation). |

### 6.1 First-occurrence date for recurring tasks with no spoken due date

When voice input contains recurrence language but no explicit start date (e.g. "clean the kitchen every Sunday"), Gemini **must infer** the first `dueDate` using these rules (specified in the prompt — see Section 7):

| Pattern | First dueDate |
|---|---|
| WEEKLY with anchor day | Next future occurrence of that day from today |
| DAILY | Tomorrow |
| WEEKLY without anchor | Today + 7 days |
| MONTHLY with anchor day | Next future occurrence of that day-of-month |
| MONTHLY without anchor | Today + 30 days |

The `from` argument in `nextDueDate()` therefore always has a concrete `LocalDate` (never falls back to today at spawn time). The ViewModel spawn pseudocode uses `task.dueDate ?: LocalDate.now()` only as a defensive fallback for legacy tasks that somehow lack a due date.

---

## 7. Gemini Prompt Extension

Two new numbered sections added to `buildVoicePromptV2()`:

### Section 9 — RECURRENCE DETECTION

`buildVoicePromptV2()` already injects today's date as a literal string in its `CURRENT CONTEXT` preamble (e.g. `Today: 2026-03-29 (SUNDAY)`). All relative date calculations for recurrence first-occurrence inference are relative to that injected date — Gemini does not use any internal date sense.

Instruct Gemini to detect recurring phrasing and return a structured `recurrence` object. When recurrence is detected but no start date is spoken, Gemini must infer the first occurrence date as specified in Section 6.1:

- "every day" / "daily" → `{frequency: "daily", interval: 1, anchor: null}`, dueDate = tomorrow
- "every week on Sunday" → `{frequency: "weekly", interval: 1, anchor: "SUNDAY"}`, dueDate = next Sunday
- "every 2 weeks" / "every other week" → `{frequency: "weekly", interval: 2, anchor: null}`, dueDate = today+14
- "weekly" with no day → `{frequency: "weekly", interval: 1, anchor: null}`, dueDate = today+7
- "every month" → `{frequency: "monthly", interval: 1, anchor: null}`, dueDate = today+30
- "every month on the 15th" → `{frequency: "monthly", interval: 1, anchor: "15"}`, dueDate = next 15th
- No recurrence language → `recurrence: null`

### Section 10 — PRIORITY DETECTION

Strong urgency signals → `priority: "high"`:
- Intensity words: "need to", "must", "have to", "absolutely", "critical", "important", "do not forget", "don't forget", "make sure", "can't miss", "really need"
- Stress/emphasis patterns: all-caps words, repeated emphasis ("I NEED to"), heavy hedging ("whatever you do")
- Default: `priority: "normal"`

**Parsing fallback:** The JSON parser in `GeminiCaptureRepository` must treat any unrecognized `priority` value (e.g. `"medium"`, `"urgent"`, missing field, parse exception) as `TaskPriority.NORMAL`. Never throw on unexpected priority values.

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

**JSON-to-Kotlin deserialization rules** (implemented in `parseVoiceResponseJson()`):
- `recurrence` absent or JSON null → `ParsedVoiceTaskItem.recurrenceRule = null`
- `recurrence.frequency` string → `RecurrenceFrequency.valueOf(str.uppercase())`; on unrecognized value (e.g. `"biweekly"`, `"yearly"`), discard the entire recurrence block → `recurrenceRule = null` (do NOT default to WEEKLY)
- `recurrence.interval` → Int, default `1` on parse failure
- `recurrence.anchor` null or absent → `RecurrenceRule.anchor = null`; non-null string → passed as-is
- `priority` string → `TaskPriority.valueOf(str.uppercase())`, default `TaskPriority.NORMAL` on any failure or absence
- Any exception during recurrence block parsing → `recurrenceRule = null` (whole block silently dropped)
- Anchor string validation: anchor values are passed through as-is from the tag or JSON. Out-of-range values (e.g. `"32"`, `"FUNDAY"`) are stored on `RecurrenceRule.anchor` unchanged; `nextDueDate()` must handle invalid anchor strings gracefully by falling back to the no-anchor path for that frequency.
- Insert Sections 9 and 10 immediately before the existing JSON schema block in `buildVoicePromptV2()`.

---

## 8. Repository Changes

`GoogleTasksRepository.createTask()` gains a `notes` parameter:

```kotlin
suspend fun createTask(
    taskListId: String,
    title: String,
    dueDate: LocalDate? = null,
    parentId: String? = null,
    notes: String? = null      // ← new; passed to Google Tasks API notes field
): Result<Task>
```

---

## 9. ViewModel: Recurring Task Spawning

After a successful `completeTask(taskListId, taskId)` call, the ViewModel checks `task.recurrenceRule`.

**`from` date for spawn:** Always use `LocalDate.now()` at the moment `completeTask()` returns — i.e., the actual completion date. Do NOT use `task.dueDate` as `from`. Using the original due date would cause a task completed two weeks late to spawn immediately overdue (the next anchor after an old date may already be in the past). Using today ensures the next instance is always in the future relative to when the user actually completed it.

```
if task.recurrenceRule != null:
    nextDue = task.recurrenceRule.nextDueDate(from = LocalDate.now())
    encodedNotes = TaskMetadata.encode(task.cleanNotes, task.recurrenceRule, task.priority)
    result = tasksRepository.createTask(
        taskListId = taskListId,   // same list the completed task belonged to — NOT currentListId
        title = task.title,
        dueDate = nextDue,
        notes = encodedNotes,
        parentId = task.parentId   // preserve subtask hierarchy; spawned copy is a child of the same parent
    )
    if result.isFailure:
        emit existing sync error state (ClockHeader error indicator)
        // Recurrence chain is broken; user is notified via sync error UI.
        // No automatic retry. No local queue. User must manually re-complete or recreate.
    else:
        refresh task list
```

**Offline behavior:** The `ConnectivityMonitor` pre-flight check is a best-effort early exit — if offline is detected before the call, skip the `createTask()` call and emit the error immediately. This is not a guarantee: the device may go offline between the pre-flight check and the API call. The `result.isFailure` branch is the authoritative error handler for both offline and transient API failures in all cases. If `createTask()` fails for any reason, the existing sync error indicator in `ClockHeader` is surfaced. The completed task remains completed. Offline recurrence spawning / local retry queuing is out of scope (see Section 13).

The completed task stays completed (drifts to bottom as normal). The new instance appears at the appropriate sorted position after list refresh.

**`taskListId` source:** The list ID used for spawning is the same `taskListId` passed to `completeTask()` — i.e., the list the original task belongs to. This is already present in the ViewModel's completion call path and does not require storing `listId` on `Task`.

---

## 10. Sorting Changes

`taskDisplayComparator` updated: HIGH priority tasks sort before NORMAL within the same completion tier, above all urgency levels:

```
isCompleted → priorityRank (HIGH=0, NORMAL=1) → urgencyRank → dueDate → position → updatedAt
```

---

## 11. UI Changes

### 11.1 Priority pip (TaskItem.kt)

A small filled circle (5dp, `WallColors.accent` — the cool primary accent, **not** `accentWarm` which is reserved for time-urgency) at 70% opacity, on the left edge of the task card. Visible only when `priority == HIGH`. Positioned vertically centered alongside the task title row. No tooltip, no label.

### 11.2 Recurrence indicator (TaskItem.kt)

A small `↻` character (Unicode U+21BB) rendered as `labelSmall` text after the task title, in `onSurface.copy(alpha = 0.4f)`. Appears only when `recurrenceRule != null`. If the character does not render correctly with Plus Jakarta Sans, fall back to `Icons.Default.Autorenew` (Material Icons) sized at `12.sp` equivalent (`Modifier.size(12.dp)`). This communicates "this will come back" without demanding attention.

### 11.3 Draft card preview

The draft card reads `recurrenceRule` and `priority` from the `ParsedVoiceTaskItem` held in `VoiceInputState.Preview`.

The draft card shows:
- Priority pip (same 5dp circle, same color) if `priority == HIGH`
- `↻ <recurrenceRule.toHumanReadable()>` badge (e.g. `↻ Every Sunday`) if `recurrenceRule != null`, rendered as `labelSmall` in muted accent color. No max-width truncation required — the longest possible string (`"Every 2 weeks on Monday"`, ~22 chars at labelSmall) fits within the draft card on landscape tablet at 1080p+.

---

## 12. Files Changed

| File | Change |
|---|---|
| `data/model/TaskMetadata.kt` | **New** — `RecurrenceRule`, `TaskPriority`, `TaskMetadata`, `DecodedMetadata` |
| `data/model/Task.kt` | Add `recurrenceRule`, `priority`, `cleanNotes` fields; `taskDisplayComparator` (top-level fn in this file) updated with priority sort tier |
| `data/repository/GoogleTasksRepository.kt` | Add `notes` param to `createTask()`; update `toAppTask()` (private extension fn in this file) to call `TaskMetadata.decode()`; `completeTask()` already accepts `taskListId: String` — no signature change needed |
| `capture/repository/GeminiCaptureRepository.kt` | Extend `ParsedVoiceTaskItem`; add prompt sections 9–10 before JSON schema block in `buildVoicePromptV2()`; extend `parseVoiceResponseJson()` (private fn in this file) with recurrence + priority deserialization |
| `viewmodel/TaskWallViewModel.kt` | Spawn next recurrence on completion; pre-flight offline guard; pass `notes` + `parentId` to `createTask()` |
| `ui/components/TaskItem.kt` | Priority pip; recurrence glyph (`↻` / `Icons.Default.Autorenew`); migrate `task.notes` display to `task.cleanNotes` |
| `ui/screens/TaskWallScreen.kt` | Draft card: recurrence badge (`↻ rule.toHumanReadable()`) + priority pip, read from `VoiceInputState.Preview` |

---

## 13. Out of Scope

- Editing or cancelling a recurrence after creation (requires new UI flow)
- "Last day of month" anchor
- "Twice a month" and sub-monthly multi-occurrence patterns
- Calendar-based recurrence (Google Calendar recurring events)
- Priority levels beyond HIGH/NORMAL
- Recurrence history / streak tracking
- Offline recurrence spawning / local queue for failed spawns
- Schedule drift correction for unanchored weekly/monthly recurrences (rolling interval from completion is accepted behavior)
- Notes field length guard (40-character metadata overhead is well within Google Tasks' 8192-character notes limit)
- Task-to-calendar-event promotion for recurring tasks (promoted recurring tasks are out of scope; the recurrence rule travels with the task but has no effect on calendar events)
