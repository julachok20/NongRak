package com.lullabyai.app.data.calendar

import kotlin.math.abs

/**
 * Decides what one pass of two-way calendar sync should do. Pure: no Android, no network, no clock.
 *
 * Scope (the product decision): NongRak activities are mirrored into the phone's calendar, and only
 * events NongRak created are followed back. Moving, renaming or deleting one of those in Google
 * Calendar updates the activity; the parent's other calendar events are never read in.
 *
 * Changes are detected three-way, against what both sides looked like at the last sync
 * ([Link.lastSynced]), because neither side keeps a trustworthy edit timestamp:
 *  - only the app changed      -> push the activity to the event
 *  - only the calendar changed -> pull the event into the activity
 *  - both changed              -> the app wins; it is the copy co-parents and caregivers share
 *
 * Two rules stop a calendar problem from becoming data loss in a shared schedule:
 *  - The caller must not call [plan] when the calendar could not be read. An empty event map from a
 *    failed query would otherwise read as "every event was deleted".
 *  - If most linked events vanish in one pass, that is an account removed or a calendar hidden, not
 *    a parent deleting events one at a time. The links are dropped and nothing is deleted.
 */
object CalendarSyncPlanner {

    /** Calendar providers round times; a difference smaller than this is not an edit. */
    private const val TIME_TOLERANCE_MILLIS = 60_000L

    /**
     * Below this many links a "most of them vanished" ratio is too small a sample: deleting two of
     * three events is an ordinary thing to do, not evidence the calendar went away.
     */
    private const val BULK_LOSS_MIN_LINKS = 5

    /** The fields that are mirrored, and the only ones compared. */
    data class Snapshot(
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
        val allDay: Boolean
    )

    /** One activity as it exists now in NongRak. */
    data class AppItem(val activityId: String, val snapshot: Snapshot)

    /** A link this device made on an earlier pass. */
    data class Link(
        val childId: String,
        val activityId: String,
        val eventId: Long,
        val lastSynced: Snapshot
    )

    sealed interface Action {
        data class CreateEvent(val activityId: String, val snapshot: Snapshot) : Action
        data class UpdateEvent(val activityId: String, val eventId: Long, val snapshot: Snapshot) : Action
        data class DeleteEvent(val activityId: String, val eventId: Long) : Action
        data class UpdateActivity(val activityId: String, val eventId: Long, val snapshot: Snapshot) : Action
        data class DeleteActivity(val activityId: String, val eventId: Long) : Action
        data class Unlink(val activityId: String) : Action
    }

    data class Plan(val actions: List<Action>, val bulkLoss: Boolean)

    /**
     * @param childId only this child's links are considered; another child's activities are not
     *   loaded, so their absence here means nothing.
     * @param appItems every activity that should be mirrored now, PLUS every linked activity that
     *   still exists even if it has moved out of the window. A linked activity missing from this
     *   list is treated as deleted in the app.
     * @param calendarEvents the linked events that currently exist, by event id, from a read that
     *   succeeded.
     */
    fun plan(
        childId: String,
        appItems: List<AppItem>,
        links: List<Link>,
        calendarEvents: Map<Long, Snapshot>
    ): Plan {
        val mine = links.filter { it.childId == childId }
        val appById = appItems.associateBy { it.activityId }

        val vanished = mine.count { it.eventId !in calendarEvents }
        if (mine.size >= BULK_LOSS_MIN_LINKS && vanished * 2 > mine.size) {
            return Plan(
                actions = mine.filter { it.eventId !in calendarEvents }.map { Action.Unlink(it.activityId) },
                bulkLoss = true
            )
        }

        val actions = mutableListOf<Action>()
        for (link in mine) {
            val app = appById[link.activityId]
            val event = calendarEvents[link.eventId]
            when {
                app == null && event == null -> actions += Action.Unlink(link.activityId)
                app == null -> actions += Action.DeleteEvent(link.activityId, link.eventId)
                event == null -> actions += Action.DeleteActivity(link.activityId, link.eventId)
                else -> {
                    val appChanged = !same(app.snapshot, link.lastSynced)
                    val eventChanged = !same(event, link.lastSynced)
                    if (appChanged) {
                        actions += Action.UpdateEvent(link.activityId, link.eventId, app.snapshot)
                    } else if (eventChanged) {
                        actions += Action.UpdateActivity(link.activityId, link.eventId, event)
                    }
                }
            }
        }

        val linked = mine.mapTo(HashSet()) { it.activityId }
        appItems.filter { it.activityId !in linked }
            .forEach { actions += Action.CreateEvent(it.activityId, it.snapshot) }

        return Plan(actions, bulkLoss = false)
    }

    /** Equal for sync purposes: same title and all-day flag, times within provider rounding. */
    fun same(a: Snapshot, b: Snapshot): Boolean =
        a.title.trim() == b.title.trim() &&
            a.allDay == b.allDay &&
            abs(a.startMillis - b.startMillis) < TIME_TOLERANCE_MILLIS &&
            abs(a.endMillis - b.endMillis) < TIME_TOLERANCE_MILLIS
}
