package com.example.todowallapp.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }

enum class TaskPriority { HIGH, NORMAL }

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int,
    /** "MONDAY"–"SUNDAY" for WEEKLY; "1"–"31" for MONTHLY; null = no anchor */
    val anchor: String?
) {
    /**
     * Returns the next due date after [from].
     *
     * DAILY: from + interval days.
     *
     * WEEKLY with valid anchor (full English day name e.g. "SUNDAY"):
     *   candidate = from + interval*7; if candidate is already on anchor day return it,
     *   otherwise advance forward to the next occurrence of that day.
     *   Falls back to no-anchor path on invalid anchor.
     *
     * WEEKLY without anchor (or invalid anchor):
     *   from + interval*7 days. Pure rolling interval.
     *
     * MONTHLY with valid anchor ("1"–"31"):
     *   anchor.toInt()-th day, interval months ahead, clamped to month end.
     *   Always reads anchor from this rule — never from.dayOfMonth.
     *   Falls back to no-anchor path on invalid anchor.
     *
     * MONTHLY without anchor:
     *   from + interval*30 days (approximation).
     *
     * This function is pure — it never modifies RecurrenceRule.anchor.
     */
    fun nextDueDate(from: LocalDate): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> from.plusDays(interval.toLong())

        RecurrenceFrequency.WEEKLY -> {
            val candidate = from.plusDays(interval.toLong() * 7)
            val dow = anchor?.let { runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull() }
            if (dow != null) {
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

    /**
     * Human-readable label for display in draft cards and recurrence indicators.
     *
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
     *
     * Ordinal: 11, 12, 13 always use "th" regardless of last digit.
     */
    fun toHumanReadable(): String = when (frequency) {
        RecurrenceFrequency.DAILY ->
            if (interval == 1) "Every day" else "Every $interval days"

        RecurrenceFrequency.WEEKLY -> {
            val dayLabel = anchor?.let {
                runCatching { DayOfWeek.valueOf(it.uppercase()) }.getOrNull()
                    ?.name?.lowercase()?.replaceFirstChar { c -> c.uppercase() }
            }
            when {
                interval == 1 && dayLabel != null -> "Every $dayLabel"
                interval == 1                     -> "Every week"
                dayLabel != null                  -> "Every $interval weeks on $dayLabel"
                else                              -> "Every $interval weeks"
            }
        }

        RecurrenceFrequency.MONTHLY -> {
            val dayNum = anchor?.toIntOrNull()
            when {
                interval == 1 && dayNum != null -> "Every month on the ${ordinal(dayNum)}"
                interval == 1                   -> "Every month"
                dayNum != null                  -> "Every $interval months on the ${ordinal(dayNum)}"
                else                            -> "Every $interval months"
            }
        }
    }
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n in 11..13  -> "th"
        n % 10 == 1  -> "st"
        n % 10 == 2  -> "nd"
        n % 10 == 3  -> "rd"
        else         -> "th"
    }
    return "$n$suffix"
}

data class DecodedMetadata(
    val recurrenceRule: RecurrenceRule?,
    val priority: TaskPriority,
    val cleanNotes: String?
)

object TaskMetadata {

    // Matches ||UPPERCASE_KEY:anything-except-pipe||
    private val TAG_REGEX = Regex("""\|\|([A-Z]+:[^|]*)\|\|""")

    /**
     * Encode recurrence and priority as ||...|| tags prepended to [notes].
     * Tag order: RECUR first (if present), then PRIORITY (if present), then user notes.
     * NORMAL priority emits no PRIORITY tag. Null recurrence emits no RECUR tag.
     * Returns empty string when all inputs are null/default.
     * Does NOT sanitize [notes] for embedded || sequences.
     */
    fun encode(notes: String?, recurrence: RecurrenceRule?, priority: TaskPriority): String {
        val sb = StringBuilder()
        if (recurrence != null) {
            val anchorPart = recurrence.anchor ?: ""
            sb.append("||RECUR:${recurrence.frequency.name.lowercase()}:${recurrence.interval}:$anchorPart||")
        }
        if (priority == TaskPriority.HIGH) {
            sb.append("||PRIORITY:high||")
        }
        if (!notes.isNullOrEmpty()) sb.append(notes)
        return sb.toString()
    }

    /**
     * Strip all ||KEY:value|| tag blocks from [rawNotes] and parse into structured metadata.
     * Uses regex [A-Z]+:[^|]* to identify tags. Non-matching || sequences pass through unchanged.
     * Tag values are uppercased before enum mapping (case-insensitive on read).
     * Malformed or unrecognized tag blocks are silently discarded.
     */
    fun decode(rawNotes: String?): DecodedMetadata {
        if (rawNotes.isNullOrEmpty()) return DecodedMetadata(null, TaskPriority.NORMAL, null)

        var recurrenceRule: RecurrenceRule? = null
        var priority = TaskPriority.NORMAL

        val cleanNotes = TAG_REGEX.replace(rawNotes) { match ->
            val content = match.groupValues[1]
            val parts = content.split(":")
            when (parts.firstOrNull()) {
                "RECUR" -> {
                    // Expected format: RECUR:frequency:interval:anchor
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
                    "" // strip tag from output
                }
                "PRIORITY" -> {
                    priority = runCatching {
                        TaskPriority.valueOf(parts.getOrNull(1)?.uppercase() ?: "")
                    }.getOrDefault(TaskPriority.NORMAL)
                    "" // strip tag from output
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
