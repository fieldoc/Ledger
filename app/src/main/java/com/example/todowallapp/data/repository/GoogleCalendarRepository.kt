package com.example.todowallapp.data.repository

import android.content.Context
import android.util.Log
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Repository for interacting with Google Calendar API.
 */
class GoogleCalendarRepository(
    private val context: Context
) {
    companion object {
        const val PRIMARY_CALENDAR_ID = "primary"
        private const val TASK_TAG_REGEX = "\\[todowallapp:task:([^\\]]+)]"
    }

    private var calendarService: Calendar? = null

    /**
     * Initialize the Calendar service with the signed-in account.
     */
    fun initialize(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(CalendarScopes.CALENDAR_EVENTS)
        ).apply {
            selectedAccount = account.account
        }

        val transport = GoogleApiTransportFactory.createTransport()

        calendarService = Calendar.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName(GoogleApiTransportFactory.APP_NAME)
            .build()
    }

    private inline fun <T> withCalendarService(block: (Calendar) -> T): Result<T> {
        val service = calendarService ?: return Result.failure(
            IllegalStateException("Calendar service not initialized")
        )
        return runCatching { block(service) }
    }

    suspend fun getCalendars(): Result<List<GoogleCalendar>> = withContext(Dispatchers.IO) {
        withCalendarService { service ->
            val items = service.calendarList()
                .list()
                .setShowDeleted(false)
                .setMinAccessRole("reader")
                .execute()
                .items
                .orEmpty()

            items
                .mapNotNull(::mapCalendarEntry)
                .sortedWith(
                    compareByDescending<GoogleCalendar> { it.isPrimary }
                        .thenBy { it.title.lowercase() }
                )
        }
    }

    suspend fun getEventsForDate(
        date: LocalDate,
        calendarId: String = PRIMARY_CALENDAR_ID
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        withCalendarService { service ->
            val zoneId = ZoneId.systemDefault()
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = service.events().list(calendarId)
                .setShowDeleted(false)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setTimeMin(DateTime(dayStart.toEpochMilli()))
                .setTimeMax(DateTime(dayEnd.toEpochMilli()))
                .setMaxResults(250)
                .execute()

            response.items
                ?.mapNotNull { it.toCalendarEvent(calendarId = calendarId, zoneId = zoneId) }
                ?.sortedWith(
                    compareBy<CalendarEvent> { it.isAllDay.not() }
                        .thenBy { it.startDateTime ?: it.allDayStartDate?.atStartOfDay() ?: LocalDateTime.MIN }
                        .thenBy { it.title.lowercase() }
                )
                ?: emptyList()
        }
    }

    /**
     * Fetches all events across [startDate]..[endDateInclusive] in a single API call,
     * then groups them by the LocalDate on which they occur.
     * All-day events that span multiple days appear in each covered date's list.
     */
    suspend fun getEventsForDateRange(
        startDate: LocalDate,
        endDateInclusive: LocalDate,
        calendarId: String = PRIMARY_CALENDAR_ID
    ): Result<Map<LocalDate, List<CalendarEvent>>> = withContext(Dispatchers.IO) {
        withCalendarService { service ->
            val zoneId = ZoneId.systemDefault()
            val rangeStart = startDate.atStartOfDay(zoneId).toInstant()
            val rangeEnd = endDateInclusive.plusDays(1).atStartOfDay(zoneId).toInstant()

            val response = service.events().list(calendarId)
                .setShowDeleted(false)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .setTimeMin(DateTime(rangeStart.toEpochMilli()))
                .setTimeMax(DateTime(rangeEnd.toEpochMilli()))
                .setMaxResults(500)
                .execute()

            val allEvents = response.items
                ?.mapNotNull { it.toCalendarEvent(calendarId = calendarId, zoneId = zoneId) }
                ?: emptyList()

            // Build map: each date in range → events that occur on that date
            val dateRange = generateSequence(startDate) { d ->
                d.plusDays(1).takeIf { !it.isAfter(endDateInclusive) }
            }.toList()

            dateRange.associateWith { date ->
                allEvents
                    .filter { event -> event.occursOn(date) }
                    .sortedWith(
                        compareBy<CalendarEvent> { it.isAllDay.not() }
                            .thenBy { it.startDateTime ?: it.allDayStartDate?.atStartOfDay() ?: LocalDateTime.MIN }
                            .thenBy { it.title.lowercase() }
                    )
            }
        }
    }

    suspend fun createEvent(
        task: Task,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        calendarId: String = PRIMARY_CALENDAR_ID
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!endDateTime.isAfter(startDateTime)) {
            return@withContext Result.failure(
                IllegalArgumentException("Event end time must be after start time")
            )
        }

        withCalendarService { service ->
            val zoneId = ZoneId.systemDefault()
            val startInstant = startDateTime.atZone(zoneId).toInstant()
            val endInstant = endDateTime.atZone(zoneId).toInstant()
            val taskTag = "[todowallapp:task:${task.id}]"
            val description = buildString {
                append("Scheduled from Ledger")
                append('\n')
                append(taskTag)
                if (!task.notes.isNullOrBlank()) {
                    append('\n')
                    append(task.notes.trim())
                }
            }

            val event = Event()
                .setSummary(task.title)
                .setDescription(description)
                .setExtendedProperties(
                    com.google.api.services.calendar.model.Event.ExtendedProperties()
                        .setPrivate(mapOf("todowall_task_id" to task.id))
                )
                .setStart(
                    EventDateTime()
                        .setDateTime(DateTime(startInstant.toEpochMilli()))
                        .setTimeZone(zoneId.id)
                )
                .setEnd(
                    EventDateTime()
                        .setDateTime(DateTime(endInstant.toEpochMilli()))
                        .setTimeZone(zoneId.id)
                )

            val created = service.events().insert(calendarId, event).execute()
            created.toCalendarEvent(calendarId = calendarId, zoneId = zoneId)
                ?: throw IllegalStateException("Calendar API returned an invalid event payload")
        }
    }

    suspend fun deleteEvent(
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        withCalendarService { service ->
            service.events().delete(calendarId, eventId).execute()
            Unit
        }
    }

    private fun mapCalendarEntry(entry: CalendarListEntry): GoogleCalendar? {
        val id = entry.id ?: return null
        val title = entry.summary ?: id
        val accessRole = entry.accessRole ?: "reader"
        val isWritable = accessRole == "owner" || accessRole == "writer"
        return GoogleCalendar(
            id = id,
            title = title,
            isPrimary = entry.primary == true || id == PRIMARY_CALENDAR_ID,
            isWritable = isWritable
        )
    }

    private fun Event.toCalendarEvent(
        calendarId: String,
        zoneId: ZoneId
    ): CalendarEvent? {
        val eventId = id ?: return null

        val startDateOnly = parseDateOnly(start?.date)
        val endDateOnly = parseDateOnly(end?.date)
        val startDateTime = parseDateTime(start?.dateTime, zoneId)
        val endDateTime = parseDateTime(end?.dateTime, zoneId)

        val isAllDay = startDateOnly != null && startDateTime == null
        if (startDateOnly == null && startDateTime == null) return null

        val taskIdFromExt = extendedProperties
            ?.private
            ?.get("todowall_task_id")
            ?.takeIf { it.isNotBlank() }
        val taskIdFromDescription = Regex(TASK_TAG_REGEX)
            .find(description.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
        val sourceTaskId = taskIdFromExt ?: taskIdFromDescription
        return CalendarEvent(
            id = eventId,
            calendarId = calendarId,
            title = summary ?: "",
            description = description,
            location = location,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            isAllDay = isAllDay,
            allDayStartDate = startDateOnly,
            allDayEndDateExclusive = endDateOnly,
            isPromotedTask = sourceTaskId != null,
            sourceTaskId = sourceTaskId,
            htmlLink = htmlLink
        )
    }

    private fun parseDateOnly(dateTime: DateTime?): LocalDate? {
        if (dateTime == null) return null
        return try {
            LocalDate.parse(dateTime.toStringRfc3339().take(10))
        } catch (e: Exception) {
            Log.w("CalendarRepo", "Failed to parse date-only value", e)
            null
        }
    }

    private fun parseDateTime(dateTime: DateTime?, zoneId: ZoneId): LocalDateTime? {
        if (dateTime == null) return null
        return try {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(dateTime.value), zoneId)
        } catch (e: Exception) {
            Log.w("CalendarRepo", "Failed to parse datetime value", e)
            null
        }
    }
}
