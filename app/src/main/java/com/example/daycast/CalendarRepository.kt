package com.example.daycast

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.util.Calendar
import java.util.TimeZone

// =========================================
// Model
// =========================================

data class CalendarEvent(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val calendarId: Long = 0L,
    val allDay: Boolean = false,
    val displayColor: Int = 0
)

enum class RecurrenceRule(val rrule: String?, val label: String) {
    NONE(null, "Does not repeat"),
    DAILY("FREQ=DAILY", "Every day"),
    WEEKLY("FREQ=WEEKLY", "Every week"),
    MONTHLY("FREQ=MONTHLY", "Every month")
}

// =========================================
// Repository
// =========================================

class CalendarRepository(private val context: Context) {

    fun getPrimaryCalendarId(): Long {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.ACCOUNT_TYPE} = 'com.google' AND ${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null, null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection, null, null, null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }

        return 1L
    }

    fun getEventsForMonth(year: Int, month: Int): Map<Int, List<CalendarEvent>> {
        val start = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month + 1, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return queryEvents(start, end).groupBy { event ->
            Calendar.getInstance().apply { timeInMillis = event.startTime }
                .get(Calendar.DAY_OF_MONTH)
        }
    }

    fun getEventsForDay(year: Int, month: Int, day: Int): List<CalendarEvent> {
        val start = Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59); set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        return queryEvents(start, end)
    }

    // Returns all events in an arbitrary time range — used by week view
    fun getEventsForDateRange(startTime: Long, endTime: Long): List<CalendarEvent> =
        queryEvents(startTime, endTime)

    private fun queryEvents(startTime: Long, endTime: Long): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DISPLAY_COLOR
        )
        val selection =
            "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?)" +
                    " AND ${CalendarContract.Events.DELETED} = 0"

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(startTime.toString(), endTime.toString()),
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return emptyList()

        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(
                        CalendarEvent(
                            id = it.getLong(0),
                            title = it.getString(1) ?: "No title",
                            description = it.getString(2) ?: "",
                            startTime = it.getLong(3),
                            endTime = it.getLong(4),
                            calendarId = it.getLong(5),
                            allDay = it.getInt(6) == 1,
                            displayColor = it.getInt(7)
                        )
                    )
                }
            }
        }
    }

    fun insertEvent(
        title: String,
        description: String = "",
        startTime: Long,
        endTime: Long,
        allDay: Boolean = false,
        recurrenceRule: RecurrenceRule = RecurrenceRule.NONE
    ): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, getPrimaryCalendarId())
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            if (recurrenceRule.rrule != null) {
                // Recurring events require RRULE + DURATION instead of DTEND
                put(CalendarContract.Events.RRULE, recurrenceRule.rrule)
                val durationMinutes = ((endTime - startTime) / 60_000L).coerceAtLeast(30L)
                put(CalendarContract.Events.DURATION, "PT${durationMinutes}M")
            } else {
                put(CalendarContract.Events.DTEND, endTime)
            }
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri?.lastPathSegment?.toLongOrNull()
    }

    fun deleteEvent(eventId: Long) {
        val uri = CalendarContract.Events.CONTENT_URI.buildUpon()
            .appendPath(eventId.toString()).build()
        context.contentResolver.delete(uri, null, null)
    }
}