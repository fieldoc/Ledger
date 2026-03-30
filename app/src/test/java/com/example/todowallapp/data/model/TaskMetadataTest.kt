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
        // from = Sunday March 29, interval=1, anchor=MONDAY → candidate=April 5 (Sun) → next Mon = April 6
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, "MONDAY")
        val from = LocalDate.of(2026, 3, 29) // Sunday
        assertEquals(LocalDate.of(2026, 4, 6), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY biweekly with anchor`() {
        // from = Sunday March 29, interval=2 → candidate = April 12 (Sun) → next Mon = April 13
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, "MONDAY")
        val from = LocalDate.of(2026, 3, 29)
        assertEquals(LocalDate.of(2026, 4, 13), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate WEEKLY anchor same day as candidate returns candidate`() {
        // from = Monday March 30, interval=1 → candidate = April 6 (Mon) → already Monday → April 6
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
        assertEquals(LocalDate.of(2026, 2, 28), rule.nextDueDate(from))
    }

    @Test
    fun `nextDueDate MONTHLY anchor preserved after clamping`() {
        // After clamping to Feb 28, calling again with anchor "31" should yield March 31
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, 1, "31")
        val from = LocalDate.of(2026, 2, 28)
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
