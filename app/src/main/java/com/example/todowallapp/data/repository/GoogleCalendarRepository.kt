package com.example.todowallapp.data.repository

import android.content.Context
import android.util.Log
import com.example.todowallapp.data.model.CalendarEvent
import com.example.todowallapp.data.model.occursOn
import com.example.todowallapp.data.model.GoogleCalendar
import com.example.todowallapp.data.model.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.calendar.model.CalendarListEntry
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

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

        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustManagerFactory.trustManagers, null)

        val transport = NetHttpTransport.Builder()
            .setSslSocketFactory(sslContext.socketFactory)
            .build()

        calendarService = Calendar.Builder(
            transport,
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Ledger")
            .build()
    }

    suspend fun getCalendars(): Result<List<GoogleCalendar>> = withContext(Dispatchers.IO) {
        try {
            val service = calendarService ?: return@withContext Result.failure(
                Exception("Calendar service not initialized")
            )
            val items = service.calendarList()
                .list()
                .setShowDeleted(false)
                .setMinAccessRole("reader")
                .execute()
                .items
                .orEmpty()

            val calendars = items
                .mapNotNull(::mapCalendarEntry)
                .sortedWith(
                    compareByDescending<GoogleCalendar> { it.isPrimary }
                        .thenBy { it.title.lowercase() }
                )

            Result.success(calendars)
        } catch (e: Exception) {
            Log.e("CalendarRepo", "Failed to load calendars", e)
            Result.failure(e)
        }
    }

    suspend fun getEventsForDate(
        date: LocalDate,
        calendarId: String = PRIMARY_CALENDAR_ID
    ): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            val service = calendarService ?: return@withContext Result.failure(
                Exception("Calendar service not initialized")
            )

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

            val events = response.items
                ?.mapNotNull { it.toCalendarEvent(calendarId = calendarId, zoneId = zoneId) }
                ?.sortedWith(
                    compareBy<CalendarEvent> { it.isAllDay.not() }
                        .thenBy { it.startDateTime ?: it.allDayStartDate?.atStartOfDay() ?: LocalDateTime.MIN }
                        .thenBy { it.title.lowercase() }
                )
                ?: emptyList()

            Result.success(events)
        } catch (e: Exception) {
            Log.e("CalendarRepo", "Failed to load events for $date", e)
            Result.failure(e)
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
        try {
            val service = calendarService ?: return@withContext Result.failure(
                Exception("Calendar service not initialized")
            )

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

            val grouped = dateRange.associateWith { date ->
                allEvents
                    .filter { event -> event.occursOn(date) }
                    .sortedWith(
                        compareBy<CalendarEvent> { it.isAllDay.not() }
                            .thenBy { it.startDateTime ?: it.allDayStartDate?.atStartOfDay() ?: LocalDateTime.MIN }
                            .thenBy { it.title.lowercase() }
                    )
            }

            Result.success(grouped)
        } catch (e: Exception) {
            Log.e("CalendarRepo", "Failed to load events for range $startDate..$endDateInclusive", e)
            Result.failure(e)
        }
    }

    suspend fun createEvent(
        task: Task,
        startDateTime: LocalDateTime,
        endDateTime: LocalDateTime,
        calendarId: String = PRIMARY_CALENDAR_ID
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            if (!endDateTime.isAfter(startDateTime)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Event end time must be after start time")
                )
            }

            val service = calendarService ?: return@withContext Result.failure(
                Exception("Calendar service not initialized")
            )

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
            val mapped = created.toCalendarEvent(calendarId = calendarId, zoneId = zoneId)
                ?: return@withContext Result.failure(
                    IllegalStateException("Calendar API returned an invalid event payload")
                )

            Result.success(mapped)
        } catch (e: Exception) {
            Log.e("CalendarRepo", "Failed to create event", e)
            Result.failure(e)
        }
    }

    suspend fun deleteEvent(
        calendarId: String,
        eventId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = calendarService ?: return@withContext Result.failure(
                Exception("Calendar service not initialized")
            )
            service.events().delete(calendarId, eventId).execute()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CalendarRepo", "Failed to delete event $eventId", e)
            Result.failure(e)
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
