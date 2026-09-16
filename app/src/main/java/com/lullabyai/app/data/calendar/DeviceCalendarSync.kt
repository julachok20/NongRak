package com.lullabyai.app.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Action
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.AppItem
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Link
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Snapshot
import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.settings.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Two-way sync between NongRak activities and the phone's own calendar - the Google account
 * calendar the parent already uses, through Android's CalendarContract. No Google sign-in or Calendar
 * API: the phone's calendar app syncs to Google Calendar by itself.
 *
 * Links live on this device only. A calendar event id is local to one phone, and each co-parent syncs
 * from their own, so the links cannot be stored in the shared activity row. What to do on each pass
 * is decided by [CalendarSyncPlanner]; this object only reads and writes.
 */
object DeviceCalendarSync {

    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 86_400_000L

    /** SQLite caps bound parameters; stay well under it when looking linked events up by id. */
    private const val ID_QUERY_CHUNK = 400

    private val ENABLED = booleanPreferencesKey("calendar_sync_enabled")
    private val CALENDAR_ID = longPreferencesKey("calendar_sync_calendar_id")
    private val CALENDAR_NAME = stringPreferencesKey("calendar_sync_calendar_name")
    private val LINKS = stringPreferencesKey("calendar_sync_links")
    private val LAST_SYNC = longPreferencesKey("calendar_sync_last_at")

    private val json = Json { ignoreUnknownKeys = true }
    private val linksSerializer = ListSerializer(StoredLink.serializer())

    data class Status(val enabled: Boolean, val calendarName: String?, val lastSyncMillis: Long?)

    data class Report(
        val created: Int = 0,
        val pushedToCalendar: Int = 0,
        val pulledFromCalendar: Int = 0,
        val eventsDeleted: Int = 0,
        val activitiesDeleted: Int = 0,
        val failures: Int = 0
    )

    fun hasPermission(context: Context): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun status(context: Context): Status {
        val prefs = context.dataStore.data.first()
        return Status(
            enabled = prefs[ENABLED] ?: false,
            calendarName = prefs[CALENDAR_NAME],
            lastSyncMillis = prefs[LAST_SYNC]
        )
    }

    /** Turns sync on against the phone's best writable calendar. Returns that calendar's name. */
    suspend fun enable(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            return@withContext Result.failure(IllegalStateException("calendar_permission_denied"))
        }
        val calendar = runCatching { pickCalendar(context) }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("calendar_not_found"))
        context.dataStore.edit {
            it[ENABLED] = true
            it[CALENDAR_ID] = calendar.first
            it[CALENDAR_NAME] = calendar.second
        }
        Result.success(calendar.second)
    }

    /**
     * Turns sync off and forgets the links. Events already in the calendar are left where they are:
     * clearing entries out of a parent's calendar as a side effect of a toggle would be a surprise.
     */
    suspend fun disable(context: Context) {
        context.dataStore.edit {
            it[ENABLED] = false
            it.remove(LINKS)
            it.remove(CALENDAR_ID)
            it.remove(CALENDAR_NAME)
        }
    }

    /**
     * One sync pass for [childId].
     *
     * @param windowItems activities that should be on the calendar now (the caller's upcoming window).
     * @param fetchByIds loads linked activities that may have moved outside the window. If it fails
     *   the pass is abandoned: an activity that merely failed to load must never read as deleted.
     */
    suspend fun run(
        context: Context,
        childId: String,
        windowItems: List<ChildActivity>,
        fetchByIds: suspend (List<String>) -> Result<List<ChildActivity>>,
        updateActivity: suspend (
            id: String, title: String, startsAt: String, endsAt: String?, allDay: Boolean
        ) -> Result<Unit>,
        deleteActivity: suspend (id: String) -> Result<Unit>
    ): Result<Report> {
        if (!hasPermission(context)) {
            return Result.failure(IllegalStateException("calendar_permission_denied"))
        }
        val prefs = context.dataStore.data.first()
        if (prefs[ENABLED] != true) return Result.success(Report())
        val calendarId = prefs[CALENDAR_ID]
            ?: return Result.failure(IllegalStateException("calendar_not_found"))
        val stored = decodeLinks(prefs[LINKS])
            ?: return Result.failure(IllegalStateException("calendar_sync_state_corrupt"))
        val zone = ZoneId.systemDefault()

        val calendarPresent = withContext(Dispatchers.IO) {
            runCatching { calendarExists(context, calendarId) }.getOrDefault(false)
        }
        if (!calendarPresent) {
            // The calendar itself is gone (account removed, calendar deleted). Forget this child's
            // links instead of reading every missing event as the parent deleting it.
            saveLinks(context, stored.filterNot { it.childId == childId })
            return Result.failure(IllegalStateException("calendar_not_found"))
        }

        val mine = stored.filter { it.childId == childId }
        val windowIds = windowItems.mapTo(HashSet()) { it.id }
        val outsideWindow = mine.map { it.activityId }.filter { it !in windowIds }
        val fetched = if (outsideWindow.isEmpty()) emptyList()
        else fetchByIds(outsideWindow).getOrElse { return Result.failure(it) }

        val appItems = (windowItems + fetched)
            .distinctBy { it.id }
            .mapNotNull { a -> snapshotOf(a, zone)?.let { AppItem(a.id, it) } }

        val events = withContext(Dispatchers.IO) {
            runCatching { readEvents(context, mine.map { it.eventId }) }
        }.getOrElse { return Result.failure(IllegalStateException("calendar_read_failed")) }

        val plan = CalendarSyncPlanner.plan(childId, appItems, mine.map { it.toLink() }, events)

        val links = mine.associateBy { it.activityId }.toMutableMap()
        var created = 0
        var pushed = 0
        var pulled = 0
        var eventsDeleted = 0
        var activitiesDeleted = 0
        var failures = 0

        for (action in plan.actions) {
            val done = runCatching {
                when (action) {
                    is Action.CreateEvent -> {
                        val eventId = withContext(Dispatchers.IO) {
                            insertEvent(context, calendarId, action.snapshot)
                        } ?: error("calendar insert returned no id")
                        links[action.activityId] =
                            StoredLink.of(childId, action.activityId, eventId, action.snapshot)
                        created++
                    }
                    is Action.UpdateEvent -> {
                        val touched = withContext(Dispatchers.IO) {
                            updateEvent(context, action.eventId, action.snapshot)
                        }
                        check(touched) { "calendar update touched no rows" }
                        links[action.activityId] =
                            StoredLink.of(childId, action.activityId, action.eventId, action.snapshot)
                        pushed++
                    }
                    is Action.DeleteEvent -> {
                        withContext(Dispatchers.IO) { deleteEvent(context, action.eventId) }
                        links.remove(action.activityId)
                        eventsDeleted++
                    }
                    is Action.UpdateActivity -> {
                        val fields = activityFields(action.snapshot, zone)
                        updateActivity(
                            action.activityId, action.snapshot.title.trim(),
                            fields.startsAt, fields.endsAt, fields.allDay
                        ).getOrThrow()
                        links[action.activityId] =
                            StoredLink.of(childId, action.activityId, action.eventId, action.snapshot)
                        pulled++
                    }
                    is Action.DeleteActivity -> {
                        deleteActivity(action.activityId).getOrThrow()
                        links.remove(action.activityId)
                        activitiesDeleted++
                    }
                    is Action.Unlink -> links.remove(action.activityId)
                }
            }.isSuccess
            // A failed action keeps its old link, so the same difference is retried next pass
            // rather than forgotten.
            if (!done) failures++
        }

        saveLinks(context, stored.filterNot { it.childId == childId } + links.values)
        context.dataStore.edit { it[LAST_SYNC] = System.currentTimeMillis() }

        if (plan.bulkLoss) return Result.failure(IllegalStateException("calendar_bulk_loss"))
        return Result.success(
            Report(created, pushed, pulled, eventsDeleted, activitiesDeleted, failures)
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Conversions
    // ---------------------------------------------------------------------------------------------

    /** How an activity looks to the calendar. Null when its start cannot be parsed. */
    fun snapshotOf(a: ChildActivity, zone: ZoneId = ZoneId.systemDefault()): Snapshot? {
        val start = runCatching { OffsetDateTime.parse(a.startsAt) }.getOrNull() ?: return null
        return if (a.allDay) {
            // All-day calendar events are pinned to UTC midnights; anything else drifts a day for
            // everyone east of UTC, which is all of Thailand.
            val day = start.atZoneSameInstant(zone).toLocalDate()
            val startUtc = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            Snapshot(a.title.trim(), startUtc, startUtc + DAY_MILLIS, allDay = true)
        } else {
            val startMs = start.toInstant().toEpochMilli()
            val endMs = a.endsAt
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
                ?.takeIf { it > startMs }
                ?: (startMs + HOUR_MILLIS)
            Snapshot(a.title.trim(), startMs, endMs, allDay = false)
        }
    }

    private data class ActivityFields(val startsAt: String, val endsAt: String?, val allDay: Boolean)

    /** What a calendar edit means for the activity row. */
    private fun activityFields(s: Snapshot, zone: ZoneId): ActivityFields =
        if (s.allDay) {
            val day = Instant.ofEpochMilli(s.startMillis).atZone(ZoneOffset.UTC).toLocalDate()
            ActivityFields(day.atStartOfDay(zone).toOffsetDateTime().toString(), null, true)
        } else {
            ActivityFields(
                Instant.ofEpochMilli(s.startMillis).atZone(zone).toOffsetDateTime().toString(),
                Instant.ofEpochMilli(s.endMillis).atZone(zone).toOffsetDateTime().toString(),
                false
            )
        }

    // ---------------------------------------------------------------------------------------------
    // Calendar provider
    // ---------------------------------------------------------------------------------------------

    /** (id, display name) of the best visible, writable calendar: a primary Google calendar first. */
    private fun pickCalendar(context: Context): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE
        )
        var bestId = -1L
        var bestName = ""
        var bestScore = -1
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                val visible = c.getInt(6) == 1
                if (!writable || !visible) continue
                val account = c.getString(2).orEmpty()
                val isGoogle = c.getString(3) == "com.google"
                val isPrimary = account.isNotBlank() && c.getString(4) == account
                val score = (if (isGoogle) 2 else 0) + (if (isPrimary) 1 else 0)
                if (score > bestScore) {
                    bestId = c.getLong(0)
                    bestName = c.getString(1) ?: account
                    bestScore = score
                }
            }
        }
        return if (bestScore >= 0) bestId to bestName else null
    }

    private fun calendarExists(context: Context, calendarId: Long): Boolean =
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(calendarId.toString()),
            null
        )?.use { it.count > 0 } ?: false

    /**
     * Linked events that still exist. Throws when the provider cannot be queried, so a read failure
     * can never pass for "all of them were deleted".
     */
    private fun readEvents(context: Context, eventIds: Collection<Long>): Map<Long, Snapshot> {
        val out = HashMap<Long, Snapshot>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DELETED
        )
        eventIds.distinct().chunked(ID_QUERY_CHUNK).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events._ID} IN ($placeholders)",
                chunk.map { it.toString() }.toTypedArray(),
                null
            ) ?: throw IllegalStateException("calendar_read_failed")
            cursor.use { c ->
                while (c.moveToNext()) {
                    // Marked deleted but not yet purged by the provider: gone as far as the parent sees.
                    if (c.getInt(5) == 1) continue
                    val start = c.getLong(2)
                    val end = if (c.isNull(3)) start + HOUR_MILLIS else c.getLong(3)
                    out[c.getLong(0)] = Snapshot(c.getString(1).orEmpty().trim(), start, end, c.getInt(4) == 1)
                }
            }
        }
        return out
    }

    private fun eventValues(s: Snapshot): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.TITLE, s.title)
        put(CalendarContract.Events.DTSTART, s.startMillis)
        put(CalendarContract.Events.DTEND, s.endMillis)
        put(CalendarContract.Events.ALL_DAY, if (s.allDay) 1 else 0)
        put(
            CalendarContract.Events.EVENT_TIMEZONE,
            if (s.allDay) "UTC" else ZoneId.systemDefault().id
        )
    }

    private fun insertEvent(context: Context, calendarId: Long, s: Snapshot): Long? {
        val values = eventValues(s).apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            // Only set on insert: a description the parent later writes in their calendar is theirs.
            put(CalendarContract.Events.DESCRIPTION, "จาก NongRak")
        }
        return context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?.lastPathSegment?.toLongOrNull()
    }

    private fun updateEvent(context: Context, eventId: Long, s: Snapshot): Boolean =
        context.contentResolver.update(eventUri(eventId), eventValues(s), null, null) > 0

    private fun deleteEvent(context: Context, eventId: Long): Boolean =
        context.contentResolver.delete(eventUri(eventId), null, null) > 0

    private fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

    // ---------------------------------------------------------------------------------------------
    // Link storage
    // ---------------------------------------------------------------------------------------------

    /** Null when the stored links cannot be read - reported, rather than silently re-creating every event. */
    private fun decodeLinks(raw: String?): List<StoredLink>? =
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(linksSerializer, raw) }.getOrNull()

    private suspend fun saveLinks(context: Context, links: List<StoredLink>) {
        context.dataStore.edit { it[LINKS] = json.encodeToString(linksSerializer, links) }
    }
}

@Serializable
private data class StoredLink(
    val childId: String,
    val activityId: String,
    val eventId: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
) {
    fun toLink() = Link(childId, activityId, eventId, Snapshot(title, startMillis, endMillis, allDay))

    companion object {
        fun of(childId: String, activityId: String, eventId: Long, s: Snapshot) =
            StoredLink(childId, activityId, eventId, s.title, s.startMillis, s.endMillis, s.allDay)
    }
}
