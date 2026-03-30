# Recurring Tasks & Voice Priority Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add recurrence rules and semantic priority to tasks, captured via voice using Gemini NLP, stored as hidden `||...||` tags in the Google Tasks notes field, displayed on the wall with a pip indicator and recurrence glyph.

**Architecture:** Notes-field metadata (`||RECUR:..||PRIORITY:high||`) travels with tasks through Google Tasks API. On completion of a recurring task, the ViewModel spawns a new task with the next due date. Gemini prompt is extended with two new sections (recurrence and priority detection) that expand the JSON schema returned by `parseVoiceInputV2`.

**Tech Stack:** Kotlin, Jetpack Compose, Google Tasks API, Gemini REST API (via `GeminiCaptureRepository`), `java.time.LocalDate`, JUnit 4 unit tests.

**Spec:** `docs/superpowers/specs/2026-03-29-recurring-tasks-voice-priority-design.md`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `data/model/TaskMetadata.kt` | **Create** | `RecurrenceFrequency`, `TaskPriority`, `RecurrenceRule` (with `nextDueDate` + `toHumanReadable`), `DecodedMetadata`, `TaskMetadata` encode/decode |
| `data/model/Task.kt` | Modify | Add `cleanNotes`, `recurrenceRule`, `priority` fields; update `taskDisplayComparator` |
| `data/repository/GoogleTasksRepository.kt` | Modify | Add `notes` param to `createTask()`; decode metadata in `toAppTask()` |
| `capture/repository/GeminiCaptureRepository.kt` | Modify | Extend `ParsedVoiceTaskItem`, add prompt sections 9–10, extend `parseVoiceResponseJson()` |
| `viewmodel/TaskWallViewModel.kt` | Modify | Spawn next recurrence after `completeTask()` succeeds |
| `ui/components/TaskItem.kt` | Modify | Priority pip, recurrence glyph, use `cleanNotes` instead of `notes` |
| `ui/screens/TaskWallScreen.kt` | Modify | Show priority pip + recurrence badge in voice draft card |
| `data/model/TaskMetadataTest.kt` | **Create** | Unit tests for encode/decode, `nextDueDate`, `toHumanReadable` |
| `data/model/TaskOrderingTest.kt` | Modify | Add priority sort tests |

---

## Task 1: TaskMetadata — Foundation (New File)

**Files:**
- Create: `app/src/main/java/com/example/todowallapp/data/model/TaskMetadata.kt`
- Create: `app/src/test/java/com/example/todowallapp/data/model/TaskMetadataTest.kt`

- [ ] **Step 1.1: Write failing tests for encode/decode**

Create `app/src/test/java/com/example/todowallapp/data/model/TaskMetadataTest.kt`:

```kotlin
package com.example.todowallapp.data.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskMetadataTest {

    // ── encode ──────────────────────────────────────────────────────────────

    @Test
    fun `encode with recurrence and high priority prepends both tags`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "SUNDAY")
        val result = TaskMetadata.encode("my notes", rule, TaskPriority.HIGH)
        assertEquals("||RECUR:weekly:1:SUNDAY||||PRIORITY:high||my notes", result)
    }

    @Test
    fun `encode with recurrence only omits priority tag`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, 1, null)
        val result = TaskMetadata.encode(null, rule, TaskPriority.NORMAL)
        assertEquals("||RECUR:daily:1:||", result)
    }

    @Test
    fun `encode with priority only omits recurrence tag`() {
        val result = TaskMetadata.encode("notes", null, TaskPriority.HIGH)
        assertEquals("||PRIORITY:high||notes", result)
    }

    @Test
    fun `encode with all null returns empty string`() {
        val result = TaskMetadata.encode(null, null, TaskPriority.NORMAL)
        assertEquals("", result)
    }

    @Test
    fun `encode NORMAL priority produces no tag`() {
        val result = TaskMetadata.encode("text", null, TaskPriority.NORMAL)
        assertEquals("text", result)
    }

    @Test
    fun `encode null anchor writes empty anchor field with trailing colon`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, 1, null)
        val result = TaskMetadata.encode(null, rule, TaskPriority.NORMAL)
        assertTrue(result.contains("||RECUR:daily:1:||"))
    }

    // ── decode ──────────────────────────────────────────────────────────────

    @Test
    fun `decode weekly recurrence with anchor`() {
        val decoded = TaskMetadata.decode("||RECUR:weekly:1:SUNDAY||clean the kitchen")
        assertEquals(RecurrenceFrequency.WEEKLY, decoded.recurrenceRule?.frequency)
        assertEquals(1, decoded.recurrenceRule?.interval)
        assertEquals("SUNDAY", decoded.recurrenceRule?.anchor)
        assertEquals("clean the kitchen", decoded.cleanNotes)
    }

    @Test
    fun `decode daily recurrence with no anchor`() {
        val decoded = TaskMetadata.decode("||RECUR:daily:2:||")
        assertEquals(RecurrenceFrequency.DAILY, decoded.recurrenceRule?.frequency)
        assertEquals(2, decoded.recurrenceRule?.interval)
        assertNull(decoded.recurrenceRule?.anchor)
        assertNull(decoded.cleanNotes)
    }

    @Test
    fun `decode high priority`() {
        val decoded = TaskMetadata.decode("||PRIORITY:high||my task notes")
        assertEquals(TaskPriority.HIGH, decoded.priority)
        assertEquals("my task notes", decoded.cleanNotes)
    }

    @Test
    fun `decode absence of priority tag returns NORMAL`() {
        val decoded = TaskMetadata.decode("just notes")
        assertEquals(TaskPriority.NORMAL, decoded.priority)
        assertEquals("just notes", decoded.cleanNotes)
    }

    @Test
    fun `decode null input returns defaults`() {
        val decoded = TaskMetadata.decode(null)
        assertNull(decoded.recurrenceRule)
        assertEquals(TaskPriority.NORMAL, decoded.priority)
        assertNull(decoded.cleanNotes)
    }

    @Test
    fun `decode malformed RECUR tag returns null recurrence`() {
        val decoded = TaskMetadata.decode("||RECUR:weekly||leftover")
        assertNull(decoded.recurrenceRule)
        // Malformed tag is silently discarded; remaining text passes through
    }

    @Test
    fun `decode unrecognized frequency returns null recurrence`() {
        val decoded = TaskMetadata.decode("||RECUR:biweekly:2:MONDAY||notes")
        assertNull(decoded.recurrenceRule)
        assertEquals("notes", decoded.cleanNotes)
    }

    @Test
    fun `decode is case-insensitive for PRIORITY value`() {
        val decoded = TaskMetadata.decode("||PRIORITY:HIGH||")
        assertEquals(TaskPriority.HIGH, decoded.priority)
    }

    @Test
    fun `decode leaves non-tag pipe sequences in cleanNotes`() {
        val decoded = TaskMetadata.decode("use the || operator here")
        assertEquals("use the || operator here", decoded.cleanNotes)
        assertNull(decoded.recurrenceRule)
    }

    @Test
    fun `encode then decode round-trips correctly`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "15")
        val encoded = TaskMetadata.encode("buy groceries", rule, TaskPriority.HIGH)
        val decoded = TaskMetadata.decode(encoded)
        assertEquals(RecurrenceFrequency.MONTHLY, decoded.recurrenceRule?.frequency)
        assertEquals(1, decoded.recurrenceRule?.interval)
        assertEquals("15", decoded.recurrenceRule?.anchor)
        assertEquals(TaskPriority.HIGH, decoded.priority)
        assertEquals("buy groceries", decoded.cleanNotes)
    }

    // ── nextDueDate ──────────────────────────────────────────────────────────

    @Test
    fun `nextDueDate DAILY adds interval days`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, 3, null)
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 1), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY with anchor snaps to correct day`() {
        // from = Sunday March 29, interval=1, anchor=MONDAY → April 5 + 0 extra = April 6
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "MONDAY")
        val from = LocalDate.of(2026, 3, 29) // Sunday
        assertEquals(LocalDate.of(2026, 4, 6), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY biweekly with anchor`() {
        // from = Sunday March 29, interval=2, anchor=MONDAY → March 29+14=April 12 (Sun) → next Mon = April 13
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, "MONDAY")
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 13), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY anchor same day as candidate returns candidate`() {
        // from = Monday March 30, interval=1, anchor=MONDAY → March 30+7=April 6 (Mon) → already Monday → April 6
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "MONDAY")
        val from = LocalDate.of(2026, 3, 30) // Monday
        assertEquals(LocalDate.of(2026, 4, 6), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY no anchor is pure rolling interval`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, null)
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 12), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate MONTHLY with anchor day`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "15")
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 15), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate MONTHLY clamps to month end for 31st in February`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "31")
        val from = LocalDate.of(2026, 1, 29)
        // Next month (Feb 2026) has 28 days — clamp to Feb 28
        assertEquals(LocalDate.of(2026, 2, 28), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate MONTHLY anchor preserved after clamping`() {
        // After Feb 28 spawn, calling nextDueDate again with anchor "31" should yield March 31
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "31")
        val from = LocalDate.of(2026, 2, 28) // clamped date
        assertEquals(LocalDate.of(2026, 3, 31), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate MONTHLY invalid anchor falls back to 30-day approximation`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "FUNDAY")
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 28), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY invalid anchor falls back to pure interval`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "FUNDAY")
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 5), rule.nextDueDate(from))
    }

    // ── toHumanReadable ──────────────────────────────────────────────────────

    @Test
    fun `toHumanReadable DAILY 1`() {
        assertEquals("Every day", RecurrenceRule(RecurrenceFrequency.DAILY, 1, null).toHumanReadable())
    }

    @Test
    fun `toHumanReadable DAILY 3`() {
        assertEquals("Every 3 days", RecurrenceRule(RecurrenceFrequency.DAILY, 3, null).toHumanReadable())
    }

    @Test
    fun `toHumanReadable WEEKLY 1 with anchor`() {
        assertEquals("Every Sunday", RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "SUNDAY").toHumanReadable())
    }

    @Test
    fun `toHumanReadable WEEKLY 1 no anchor`() {
        assertEquals("Every week", RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, null).toHumanReadable())
    }

    @Test
    fun `toHumanReadable WEEKLY 2 with anchor`() {
        assertEquals("Every 2 weeks on Monday", RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, "MONDAY").toHumanReadable())
    }

    @Test
    fun `toHumanReadable MONTHLY 1 with anchor uses ordinal`() {
        assertEquals("Every month on the 15th", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "15").toHumanReadable())
    }

    @Test
    fun `toHumanReadable MONTHLY ordinal 11th 12th 13th use th not st nd rd`() {
        assertEquals("Every month on the 11th", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "11").toHumanReadable())
        assertEquals("Every month on the 12th", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "12").toHumanReadable())
        assertEquals("Every month on the 13th", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "13").toHumanReadable())
    }

    @Test
    fun `toHumanReadable MONTHLY 1 no anchor`() {
        assertEquals("Every month", RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, null).toHumanReadable())
    }

    @Test
    fun `toHumanReadable MONTHLY 3 no anchor`() {
        assertEquals("Every 3 months", RecurrenceRule(RecurrenceFrequency.MONTHLY, 3, null).toHumanReadable())
    }
}
```

- [ ] **Step 1.2: Run tests to confirm they fail**

```
gradlew test --tests com.example.todowallapp.data.model.TaskMetadataTest
```
Expected: compilation errors (types not found yet) — confirms tests are wired.

- [ ] **Step 1.3: Implement `TaskMetadata.kt`**

Create `app/src/main/java/com/example/todowallapp/data/model/TaskMetadata.kt`:

```kotlin
package com.example.todowallapp.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }

enum class TaskPriority { HIGH, NORMAL }

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val anchor: String?
) {
    /**
     * Returns the next due date after [from].
     * See spec Section 6 for full semantics.
     */
    fun nextDueDate(from: LocalDate): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> from.plusDays(interval.toLong())

        RecurrenceFrequency.WEEKLY -> {
            val candidate = from.plusDays(interval.toLong() * 7)
            val dow = anchor?.let { runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull() }
            if (dow != null) {
                // Snap to anchor day: if candidate is already that day, return as-is
                if (candidate.dayOfWeek == dow) candidate
                else candidate.with(TemporalAdjusters.next(dow))
            } else {
                candidate
            }
        }

        RecurrenceFrequency.MONTHLY -> {
            val targetDay = anchor?.toIntOrNull()
            if (targetDay != null) {
                val targetMonth = from.plusMonths(interval.toLong())
                val maxDay = targetMonth.lengthOfMonth()
                targetMonth.withDayOfMonth(minOf(targetDay, maxDay))
            } else {
                from.plusDays(interval.toLong() * 30)
            }
        }
    }

    fun toHumanReadable(): String = when (frequency) {
        RecurrenceFrequency.DAILY -> if (interval == 1) "Every day" else "Every $interval days"

        RecurrenceFrequency.WEEKLY -> {
            val dayLabel = anchor?.let {
                runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull()
                    ?.name?.lowercase()?.replaceFirstChar { c -> c.uppercase() }
            }
            when {
                interval == 1 && dayLabel != null -> "Every $dayLabel"
                interval == 1 -> "Every week"
                dayLabel != null -> "Every $interval weeks on $dayLabel"
                else -> "Every $interval weeks"
            }
        }

        RecurrenceFrequency.MONTHLY -> {
            val dayNum = anchor?.toIntOrNull()
            when {
                interval == 1 && dayNum != null -> "Every month on the ${ordinal(dayNum)}"
                interval == 1 -> "Every month"
                dayNum != null -> "Every $interval months on the ${ordinal(dayNum)}"
                else -> "Every $interval months"
            }
        }
    }
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

data class DecodedMetadata(
    val recurrenceRule: RecurrenceRule?,
    val priority: TaskPriority,
    val cleanNotes: String?
)

object TaskMetadata {
    // Regex: matches ||UPPERCASE_KEY:anything-except-pipe||
    private val TAG_REGEX = Regex("""\|\|([A-Z]+:[^|]*)\|\|""")

    fun encode(notes: String?, recurrence: RecurrenceRule?, priority: TaskPriority): String {
        val sb = StringBuilder()
        if (recurrence != null) {
            sb.append("||RECUR:${recurrence.frequency.name.lowercase()}:${recurrence.interval}:${recurrence.anchor ?: ""}||")
        }
        if (priority == TaskPriority.HIGH) {
            sb.append("||PRIORITY:high||")
        }
        if (!notes.isNullOrEmpty()) sb.append(notes)
        return sb.toString()
    }

    fun decode(rawNotes: String?): DecodedMetadata {
        if (rawNotes.isNullOrEmpty()) return DecodedMetadata(null, TaskPriority.NORMAL, null)

        var recurrenceRule: RecurrenceRule? = null
        var priority = TaskPriority.NORMAL
        val cleanNotes = TAG_REGEX.replace(rawNotes) { match ->
            val content = match.groupValues[1]
            val parts = content.split(":")
            when (parts.firstOrNull()) {
                "RECUR" -> {
                    // Expected: RECUR:frequency:interval:anchor
                    if (parts.size >= 4) {
                        val freq = runCatching {
                            RecurrenceFrequency.valueOf(parts[1].uppercase())
                        }.getOrNull()
                        val interval = parts[2].toIntOrNull()
                        val anchor = parts[3].takeIf { it.isNotEmpty() }
                        if (freq != null && interval != null) {
                            recurrenceRule = RecurrenceRule(freq, interval, anchor)
                        }
                    }
                    "" // strip tag from notes
                }
                "PRIORITY" -> {
                    priority = runCatching {
                        TaskPriority.valueOf(parts.getOrNull(1)?.uppercase() ?: "")
                    }.getOrDefault(TaskPriority.NORMAL)
                    "" // strip tag from notes
                }
                else -> match.value // unrecognized tag: leave as-is
            }
        }.trimStart()

        return DecodedMetadata(
            recurrenceRule = recurrenceRule,
            priority = priority,
            cleanNotes = cleanNotes.ifEmpty { null }
        )
    }
}
```

- [ ] **Step 1.4: Run tests — all must pass**

```
gradlew test --tests com.example.todowallapp.data.model.TaskMetadataTest
```
Expected: all green. Fix any failures before proceeding.

- [ ] **Step 1.5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/data/model/TaskMetadata.kt
git add app/src/test/java/com/example/todowallapp/data/model/TaskMetadataTest.kt
git commit -m "feat: add TaskMetadata — RecurrenceRule, TaskPriority, encode/decode"
```

---

## Task 2: Extend Task Model + Sort Comparator

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/data/model/Task.kt`
- Modify: `app/src/test/java/com/example/todowallapp/data/model/TaskOrderingTest.kt`

- [ ] **Step 2.1: Write failing tests for priority sort**

Add to the bottom of `TaskOrderingTest.kt`:

```kotlin
    @Test
    fun `sortTasksForDisplay places HIGH priority before NORMAL at same urgency`() {
        val tasks = listOf(
            Task(id = "normal-a", title = "A", dueDate = today.plusDays(5), priority = TaskPriority.NORMAL),
            Task(id = "high-b",   title = "B", dueDate = today.plusDays(5), priority = TaskPriority.HIGH),
            Task(id = "normal-c", title = "C", dueDate = today.plusDays(5), priority = TaskPriority.NORMAL)
        )
        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        assertEquals("high-b", sorted.first())
    }

    @Test
    fun `sortTasksForDisplay HIGH priority does not float above completed tasks`() {
        val tasks = listOf(
            Task(id = "done",     title = "Done", isCompleted = true,  priority = TaskPriority.HIGH),
            Task(id = "pending",  title = "Pending", isCompleted = false, priority = TaskPriority.NORMAL)
        )
        val sorted = sortTasksForDisplay(tasks, today).map { it.id }
        assertEquals(listOf("pending", "done"), sorted)
    }
```

- [ ] **Step 2.2: Run tests — they must fail**

```
gradlew test --tests com.example.todowallapp.data.model.TaskOrderingTest
```
Expected: compilation errors (TaskPriority unknown on Task constructor).

- [ ] **Step 2.3: Update `Task.kt`**

In `app/src/main/java/com/example/todowallapp/data/model/Task.kt`:

1. **No imports needed** — `TaskMetadata.kt` and `Task.kt` are in the same package (`com.example.todowallapp.data.model`). Same-package imports are unnecessary in Kotlin.

2. Add three new fields to `Task` data class (after `updatedAt`):
```kotlin
    val recurrenceRule: RecurrenceRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val cleanNotes: String? = null
```

3. Update `urgencySortRank` is already correct. Update `taskDisplayComparator` — replace:
```kotlin
fun taskDisplayComparator(today: LocalDate = LocalDate.now()): Comparator<Task> {
    return compareBy<Task> { it.isCompleted }
        .thenBy { urgencySortRank(it.getUrgencyLevel(today)) }
```
with:
```kotlin
fun taskDisplayComparator(today: LocalDate = LocalDate.now()): Comparator<Task> {
    return compareBy<Task> { it.isCompleted }
        .thenBy { if (it.priority == TaskPriority.HIGH) 0 else 1 }
        .thenBy { urgencySortRank(it.getUrgencyLevel(today)) }
```

- [ ] **Step 2.4: Run tests — all must pass**

```
gradlew test --tests com.example.todowallapp.data.model.TaskOrderingTest
```
Expected: all green including new priority tests.

- [ ] **Step 2.5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/data/model/Task.kt
git add app/src/test/java/com/example/todowallapp/data/model/TaskOrderingTest.kt
git commit -m "feat: add recurrenceRule, priority, cleanNotes to Task; update sort comparator"
```

---

## Task 3: Repository — `createTask` notes + `toAppTask` decode

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt`

- [ ] **Step 3.1: Add `notes` parameter to `createTask()`**

In `GoogleTasksRepository.kt`, find `createTask(` (around line 190) and add `notes: String? = null` as the last parameter:

```kotlin
suspend fun createTask(
    taskListId: String,
    title: String,
    dueDate: LocalDate? = null,
    parentId: String? = null,
    notes: String? = null
): Result<Task> = withContext(Dispatchers.IO) {
    withTasksService { service ->
        val googleTask = com.google.api.services.tasks.model.Task()
            .setTitle(title)

        if (dueDate != null) {
            val dueInstant = dueDate
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC)
                .toInstant()
            googleTask.due = dueInstant.toString()
        }

        if (!notes.isNullOrEmpty()) {
            googleTask.notes = notes
        }

        val insertRequest = service.tasks().insert(taskListId, googleTask)
        if (parentId != null) {
            insertRequest.parent = parentId
        }
        val created = insertRequest.execute()
        created.toAppTask()
    }
}
```

- [ ] **Step 3.2: Update `toAppTask()` to decode metadata**

Find the `toAppTask()` private extension function (around line 235). Add import at file top:
```kotlin
import com.example.todowallapp.data.model.TaskMetadata
```

Inside `toAppTask()`, after the existing field assignments, add the metadata decode. Replace the return call to include new fields. The function should look like:

```kotlin
private fun com.google.api.services.tasks.model.Task.toAppTask(): Task {
    val decoded = TaskMetadata.decode(notes)
    return Task(
        id = id ?: "",
        title = title ?: "",
        notes = notes,                          // raw, including tags
        cleanNotes = decoded.cleanNotes,        // display-safe
        recurrenceRule = decoded.recurrenceRule,
        priority = decoded.priority,
        isCompleted = status == "completed",
        completedAt = if (status == "completed") parseDateTime(completed) else null,  // preserve existing guard
        dueDate = parseDueDate(due),
        position = position ?: "",
        parentId = parent,                      // Google Tasks API field is `parent`, not `parentTaskId`
        updatedAt = parseDateTime(updated) ?: LocalDateTime.now()
    )
}
```

- [ ] **Step 3.3: Build to verify no compile errors**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. Fix any type errors.

- [ ] **Step 3.4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/data/repository/GoogleTasksRepository.kt
git commit -m "feat: add notes param to createTask; decode metadata in toAppTask"
```

---

## Task 4: Gemini Parser Extension

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt`

- [ ] **Step 4.1: Extend `ParsedVoiceTaskItem`**

In `GeminiCaptureRepository.kt`, find `data class ParsedVoiceTaskItem` and add two new fields at the end:

```kotlin
data class ParsedVoiceTaskItem(
    val title: String,
    val dueDate: LocalDate?,
    val preferredTime: PreferredTime?,
    val targetListId: String?,
    val newListName: String?,
    val parentTaskId: String?,
    val confidence: Float,
    val duplicateOf: String?,
    val recurrenceRule: RecurrenceRule? = null,    // ← new
    val priority: TaskPriority = TaskPriority.NORMAL  // ← new
)
```

Add imports at top of file:
```kotlin
import com.example.todowallapp.data.model.RecurrenceFrequency
import com.example.todowallapp.data.model.RecurrenceRule
import com.example.todowallapp.data.model.TaskPriority
```

- [ ] **Step 4.2: Extend `parseVoiceResponseJson()` to deserialize recurrence and priority**

Find the `parseVoiceResponseJson()` private function. In the per-task JSON parsing block (inside `tasksArray.map { element ->`), add after the `duplicateOf` assignment:

```kotlin
val recurrenceRule = runCatching {
    val recObj = obj.getAsJsonObject("recurrence")
    if (recObj != null && !recObj.isJsonNull) {
        val freqStr = recObj.stringValue("frequency") ?: ""
        val freq = runCatching {
            RecurrenceFrequency.valueOf(freqStr.uppercase())
        }.getOrNull() ?: throw IllegalArgumentException("unknown frequency: $freqStr")
        val interval = recObj.get("interval")?.asInt ?: 1
        val anchor = recObj.stringValue("anchor")
        RecurrenceRule(freq, interval, anchor)
    } else null
}.getOrNull()

val priority = runCatching {
    val pStr = obj.stringValue("priority") ?: "normal"
    TaskPriority.valueOf(pStr.uppercase())
}.getOrDefault(TaskPriority.NORMAL)
```

Then add `recurrenceRule` and `priority` to the `ParsedVoiceTaskItem(...)` constructor call at the end of the map block:

```kotlin
ParsedVoiceTaskItem(
    title = title,
    dueDate = dueDate,
    preferredTime = preferredTime,
    targetListId = targetListId,
    newListName = newListName,
    parentTaskId = parentTaskId,
    confidence = confidence.coerceIn(0f, 1f),
    duplicateOf = duplicateOf,
    recurrenceRule = recurrenceRule,    // ← new
    priority = priority                  // ← new
)
```

- [ ] **Step 4.3: Extend `buildVoicePromptV2()` — add sections 9 and 10**

Find `buildVoicePromptV2()`. Locate the line that starts `8) RESPONSE FORMAT` (or the numbered section just before the JSON schema). Insert **before** that section:

```kotlin
            9) RECURRENCE DETECTION
               Detect recurring phrasing and populate the "recurrence" object. Today is $todayDate.
               When recurrence is detected but no start date is spoken, infer the first dueDate:
               - "every day" / "daily"           → frequency: "daily",   interval: 1, anchor: null,  dueDate: tomorrow ($todayDate.plusDays(1))
               - "every week on [day]"            → frequency: "weekly",  interval: 1, anchor: "[DAY]", dueDate: next [day] from today
               - "every [n] weeks on [day]"       → frequency: "weekly",  interval: n, anchor: "[DAY]", dueDate: next [day] from today
               - "every other week" / "biweekly"  → frequency: "weekly",  interval: 2, anchor: null,  dueDate: today+14
               - "weekly" with no day             → frequency: "weekly",  interval: 1, anchor: null,  dueDate: today+7
               - "every month on the [n]th"       → frequency: "monthly", interval: 1, anchor: "[n]", dueDate: next [n]th from today
               - "every month"                    → frequency: "monthly", interval: 1, anchor: null,  dueDate: today+30
               - No recurrence language           → recurrence: null
               anchor for weekly must be a full uppercase day name: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
               anchor for monthly must be a numeric string: "1" through "31"

            10) PRIORITY / URGENCY DETECTION
                Detect strong urgency signals in the transcript and set "priority":
                - Signals for "high": "need to", "must", "have to", "absolutely", "critical", "important",
                  "do not forget", "don't forget", "make sure", "can't miss", "really need",
                  all-caps emphasis (e.g. "I NEED to"), repeated stress, "whatever you do"
                - Default: "normal"
                This field applies per-task — multi-task utterances may have mixed priorities.
```

Then update the JSON schema block within section 8 (RESPONSE FORMAT) to include the new fields:

```json
               {
                 "intent": "add|complete|reschedule|delete|query|amend",
                 "tasks": [
                   {
                     "title": "string",
                     "dueDate": "YYYY-MM-DD|null",
                     "preferredTime": "morning|afternoon|evening|null",
                     "targetListId": "string|null",
                     "newListName": "string|null",
                     "parentTaskId": "string|null",
                     "confidence": 0.0,
                     "duplicateOf": "existing-task-id|null",
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

**Important:** Renumber the existing section 8 (RESPONSE FORMAT) to section 11, and the existing section 9 (CLEAN OUTPUT) to section 12. Or simply add the two new sections as numbered 9 and 10 immediately before the response format section — the existing numbering in the prompt does not need to be perfect, Gemini follows prose instructions regardless of numbers.

- [ ] **Step 4.4: Build to verify no compile errors**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4.5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/capture/repository/GeminiCaptureRepository.kt
git commit -m "feat: extend voice parser with recurrence and priority fields"
```

---

## Task 5: ViewModel — Recurrence Spawn on Completion

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt`

- [ ] **Step 5.1: Add recurrence spawn logic after `completeTask()` success**

In `TaskWallViewModel.kt`, find `fun completeTask(task: Task)` (around line 595). The function looks up `taskListId` via `findTaskListIdForTask()` and calls `tasksRepository.completeTask(taskListId, task.id)`.

After the `val result = tasksRepository.completeTask(taskListId, task.id)` call and its success handling, add:

```kotlin
// Spawn next recurrence if this was a recurring task
if (result.isSuccess && task.recurrenceRule != null) {
    spawnNextRecurrence(task, taskListId)
}
```

Then add the `spawnNextRecurrence` private function (add it near the other private helper functions in the ViewModel):

```kotlin
private fun spawnNextRecurrence(task: Task, taskListId: String) {
    viewModelScope.launch {
        // Best-effort offline pre-check (ConnectivityMonitor exposes isOnline: StateFlow<Boolean>)
        if (!isOnline.value) {
            _uiState.update { it.copy(error = "Recurring task completed, but next instance couldn't be scheduled — device is offline.") }
            return@launch
        }

        val nextDue = task.recurrenceRule!!.nextDueDate(from = java.time.LocalDate.now())
        val encodedNotes = TaskMetadata.encode(task.cleanNotes, task.recurrenceRule, task.priority)

        val result = tasksRepository.createTask(
            taskListId = taskListId,
            title = task.title,
            dueDate = nextDue,
            parentId = task.parentId,
            notes = encodedNotes.ifEmpty { null }
        )

        if (result.isFailure) {
            _uiState.update { it.copy(error = "Next recurrence couldn't be scheduled. Sync when online to retry.") }
        } else {
            performRefresh(showSyncIndicator = false)  // performRefresh is private suspend — call from this coroutine
        }
    }
}
```

Add import at top of ViewModel file if not already present:
```kotlin
import com.example.todowallapp.data.model.TaskMetadata
```

- [ ] **Step 5.2: Build to verify no compile errors**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. If `isOnline` is not directly accessible, check — it is `val isOnline: StateFlow<Boolean>` from `ConnectivityMonitor` already exposed on the ViewModel. Use `.value` to read it synchronously.

- [ ] **Step 5.3: Also pass notes when voice-confirmed tasks are created**

In `TaskWallViewModel.kt`, find `confirmVoiceTasks()` (around line 739). Inside the loop that calls `tasksRepository.createTask(...)` for `VoiceIntent.ADD`, add the encoded notes:

```kotlin
val encodedNotes = TaskMetadata.encode(
    notes = null,
    recurrence = task.recurrenceRule,
    priority = task.priority
)
tasksRepository.createTask(
    taskListId = listId,
    title = task.title,
    dueDate = task.dueDate,
    parentId = task.parentTaskId,
    notes = encodedNotes.ifEmpty { null }
)
```

Search for the existing `tasksRepository.createTask(` call inside `confirmVoiceTasks` and add the two new parameters. The exact context will be something like:
```kotlin
tasksRepository.createTask(
    taskListId = listId,
    title = parsedTask.title,
    dueDate = parsedTask.dueDate,
    parentId = parsedTask.parentTaskId
)
```
Replace with the version that includes `notes`.

- [ ] **Step 5.4: Build again**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/viewmodel/TaskWallViewModel.kt
git commit -m "feat: spawn next recurrence after completing recurring task"
```

---

## Task 6: TaskItem UI — Priority Pip + Recurrence Glyph

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt`

- [ ] **Step 6.1: Migrate `task.notes` display to `task.cleanNotes`**

In `TaskItem.kt`, find every reference to `task.notes` used for **display** (lines 165, 381, 384). These must be changed to `task.cleanNotes`.

Line 381 area:
```kotlin
// BEFORE:
if (!task.notes.isNullOrBlank() && !task.isCompleted) {
    ...
    text = task.notes,
// AFTER:
if (!task.cleanNotes.isNullOrBlank() && !task.isCompleted) {
    ...
    text = task.cleanNotes,
```

Also find line 165 where `task.notes` may be passed as a parameter — change to `task.cleanNotes`.

- [ ] **Step 6.2: Add priority pip**

In `TaskItem.kt`, find the Row that contains the task checkbox and title (the main content row). Add a priority pip as a small circle on the left edge. Locate where the leading row starts — it will be something like a `Row(verticalAlignment = Alignment.CenterVertically)` containing the checkbox and title text.

Add before the checkbox (or as a leading element):

```kotlin
// Priority pip
if (task.priority == TaskPriority.HIGH) {
    Box(
        modifier = Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(LocalWallColors.current.accentPrimary.copy(alpha = 0.7f))
            .align(Alignment.CenterVertically)
    )
    Spacer(modifier = Modifier.width(6.dp))
}
```

Add required imports:
```kotlin
import androidx.compose.foundation.shape.CircleShape
import com.example.todowallapp.data.model.TaskPriority
import com.example.todowallapp.ui.theme.LocalWallColors
```

- [ ] **Step 6.3: Add recurrence glyph after title**

Find where the task title `Text()` is rendered. Immediately after it, add:

```kotlin
if (task.recurrenceRule != null) {
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = "\u21BB", // ↻
        style = MaterialTheme.typography.labelSmall,
        color = LocalWallColors.current.textSecondary.copy(alpha = 0.4f)
    )
}
```

Add import:
```kotlin
import com.example.todowallapp.data.model.RecurrenceRule
```

(If the ↻ glyph doesn't render with Plus Jakarta Sans, replace `"\u21BB"` with an Icon:
```kotlin
Icon(
    imageVector = Icons.Default.Autorenew,
    contentDescription = "Recurring",
    modifier = Modifier.size(12.dp),
    tint = LocalWallColors.current.textSecondary.copy(alpha = 0.4f)
)
```
)

- [ ] **Step 6.4: Build**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6.5: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/components/TaskItem.kt
git commit -m "feat: add priority pip and recurrence glyph to TaskItem; use cleanNotes for display"
```

---

## Task 7: Draft Card — Recurrence Badge + Priority Pip

**Files:**
- Modify: `app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt`

- [ ] **Step 7.1: Add priority pip and recurrence badge in the per-task draft card loop**

In `TaskWallScreen.kt`, find the voice preview draft card section (around line 1307). The loop is:

```kotlin
response.tasks.forEachIndexed { index, task ->
    ...
    Text(task.title.ifBlank { response.rawTranscript }, ...)
    if (task.dueDate != null || task.preferredTime != null) { ... }
    if (task.duplicateOf != null) { ... }
}
```

After the `duplicateOf` block (after line 1342), add before the closing `}` of the loop:

```kotlin
// Priority pip + recurrence badge
val hasPriority = task.priority == TaskPriority.HIGH
val hasRecurrence = task.recurrenceRule != null
if (hasPriority || hasRecurrence) {
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasPriority) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(colors.accentPrimary.copy(alpha = 0.7f))
            )
        }
        if (hasRecurrence) {
            Text(
                text = "\u21BB ${task.recurrenceRule!!.toHumanReadable()}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary.copy(alpha = 0.6f)
            )
        }
    }
}
```

Add imports if not already present:
```kotlin
import androidx.compose.foundation.shape.CircleShape
import com.example.todowallapp.data.model.TaskPriority
```

- [ ] **Step 7.2: Build**

```
gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7.3: Run all unit tests**

```
gradlew test
```
Expected: all green.

- [ ] **Step 7.4: Commit**

```bash
git add app/src/main/java/com/example/todowallapp/ui/screens/TaskWallScreen.kt
git commit -m "feat: show priority pip and recurrence badge in voice draft card"
```

---

## Task 8: Final Build Verification

- [ ] **Step 8.1: Clean build**

```
gradlew clean assembleDebug
```
Expected: BUILD SUCCESSFUL with no warnings about unresolved references.

- [ ] **Step 8.2: Run full test suite**

```
gradlew test
```
Expected: all tests pass.

- [ ] **Step 8.3: Manual smoke-test checklist**

On the device:
1. Say "Clean the kitchen every Sunday" → draft card should show `↻ Every Sunday`, confirm → task appears with `↻` glyph
2. Say "I absolutely MUST call the bank tomorrow" → draft card should show priority pip, confirm → task appears at top of list with pip
3. Complete the recurring task → new instance should appear with the next Sunday's due date
4. Check Google Tasks on phone → task notes should contain `||RECUR:weekly:1:SUNDAY||`, notes display on wall should be clean
5. Say "Remind me every 2 weeks on Monday to pay bills" → `↻ Every 2 weeks on Monday`
6. Say "Every month on the 15th pay rent" → `↻ Every month on the 15th`

---

## Appendix: Test Run Commands

```bash
# Run TaskMetadata tests only
gradlew test --tests com.example.todowallapp.data.model.TaskMetadataTest

# Run all model tests
gradlew test --tests com.example.todowallapp.data.model.*

# Run all unit tests
gradlew test

# Clean + full build
gradlew clean assembleDebug
```
