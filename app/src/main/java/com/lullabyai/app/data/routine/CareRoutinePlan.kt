package com.lullabyai.app.data.routine

/**
 * Turns "the child's repeating day" plus "what was ticked today" into the timeline the screen draws.
 *
 * Pure: no clock, no Android. The caller passes the current minute, which is also what makes the
 * "now" behaviour testable — a routine screen that is wrong at 23:59 is wrong exactly when a tired
 * caregiver is reading it.
 */
object CareRoutinePlan {

    enum class Status {
        /** Ticked today. */
        DONE,
        /** Deliberately not done today (the child slept through a feed, say). */
        SKIPPED,
        /** Its slot contains "now": the thing the caregiver should be doing. */
        CURRENT,
        /** Its slot has passed with nothing recorded. */
        MISSED,
        /** Still ahead today. */
        UPCOMING
    }

    /** A routine item as stored, reduced to what the timeline needs. */
    data class Entry(
        val id: String,
        /** Minutes since local midnight, 0..1439. */
        val minuteOfDay: Int,
        val title: String,
        val category: String = "other",
        val detail: String = "",
        /** What to have ready before the slot comes round, or what it aims at. */
        val prepNote: String = ""
    )

    /** Today's record for one item, if any. */
    data class Log(
        val routineId: String,
        val done: Boolean,
        val note: String = "",
        val actorName: String = "",
        /** Minutes since midnight when it was recorded, or null if unknown. */
        val recordedMinute: Int? = null
    )

    data class Slot(val entry: Entry, val status: Status, val log: Log?)

    data class Day(
        val slots: List<Slot>,
        val done: Int,
        val skipped: Int,
        val total: Int,
        /** Whole percent of items ticked done; 0 when the child has no routine yet. */
        val percent: Int,
        /** The item in progress, if the current time falls inside one. */
        val current: Slot?,
        /** The next thing that has not happened yet. */
        val next: Slot?
    )

    /**
     * @param nowMinuteOfDay minutes since local midnight.
     *
     * An unlogged item counts as CURRENT from its own time until the next item's time, and MISSED
     * after that. The last item of the day therefore stays CURRENT until midnight rather than
     * flipping to missed the minute after it was due — nobody wants "missed" on the bedtime routine
     * at 19:31 because the bottle took longer than planned.
     */
    fun build(
        entries: List<Entry>,
        logs: List<Log>,
        nowMinuteOfDay: Int
    ): Day {
        val ordered = entries.sortedBy { it.minuteOfDay }
        val byId = logs.associateBy { it.routineId }

        val slots = ordered.mapIndexed { index, entry ->
            val log = byId[entry.id]
            val nextStart = ordered.getOrNull(index + 1)?.minuteOfDay ?: MINUTES_IN_DAY
            val status = when {
                log != null && log.done -> Status.DONE
                log != null -> Status.SKIPPED
                nowMinuteOfDay < entry.minuteOfDay -> Status.UPCOMING
                nowMinuteOfDay < nextStart -> Status.CURRENT
                else -> Status.MISSED
            }
            Slot(entry, status, log)
        }

        val done = slots.count { it.status == Status.DONE }
        val skipped = slots.count { it.status == Status.SKIPPED }
        return Day(
            slots = slots,
            done = done,
            skipped = skipped,
            total = slots.size,
            percent = if (slots.isEmpty()) 0 else done * 100 / slots.size,
            current = slots.firstOrNull { it.status == Status.CURRENT },
            next = slots.firstOrNull { it.status == Status.UPCOMING }
        )
    }

    private const val MINUTES_IN_DAY = 24 * 60

    /** "07:15" for a stored `time` value; tolerant of "07:15:00" and of junk (returns null). */
    fun parseMinuteOfDay(time: String?): Int? {
        val parts = time?.trim()?.split(":") ?: return null
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** Back to "07:15" for display and for writing a `time` column. */
    fun formatMinuteOfDay(minute: Int): String {
        val safe = ((minute % MINUTES_IN_DAY) + MINUTES_IN_DAY) % MINUTES_IN_DAY
        return "%02d:%02d".format(safe / 60, safe % 60)
    }
}
