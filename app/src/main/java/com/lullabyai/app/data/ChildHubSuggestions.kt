package com.lullabyai.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Picks the ONE thing worth nudging a parent about on the Child Hub, from what they have actually
 * recorded. Pure: the caller maps the result to Thai/English copy and a route.
 *
 * One suggestion at a time, by design: a strip that lists five chores reads as noise and gets
 * ignored, and the parent who opens this screen is usually mid-task. When nothing is due the strip
 * disappears rather than inventing filler.
 */
object ChildHubSuggestions {

    /** Growth is expected roughly monthly; older than this and the chart stops being useful. */
    const val GROWTH_STALE_DAYS = 30L

    /** A shot this close needs booking, not just noting. */
    const val VACCINE_SOON_DAYS = 14L

    /** An illness logged within this window is probably still running. */
    const val ILLNESS_ACTIVE_DAYS = 3L

    enum class Kind {
        VACCINE_OVERDUE,
        ILLNESS_FOLLOW_UP,
        VACCINE_SOON,
        GROWTH_NEVER,
        GROWTH_STALE,
        MILESTONE,
        SLEEP_TODAY
    }

    /** Everything the decision needs, already reduced to dates by the caller. */
    data class Signals(
        val today: LocalDate,
        /** Most recent weight/height entry, or null if this child has none. */
        val lastGrowthOn: LocalDate? = null,
        /** Soonest not-yet-given dose with a date, or null. */
        val nextVaccineDueOn: LocalDate? = null,
        /** Most recent illness/symptom entry, or null. */
        val lastIllnessOn: LocalDate? = null,
        /** Most recent sleep entry, or null. */
        val lastSleepOn: LocalDate? = null,
        /** Age-appropriate milestones not ticked yet. */
        val pendingMilestones: Int = 0,
        /** Below this age sleep is logged daily; above it, nobody does and nagging is wrong. */
        val ageMonths: Int? = null
    )

    /** @param days how late/soon/stale the thing is — the UI puts this number in the sentence. */
    data class Suggestion(val kind: Kind, val days: Long = 0)

    private const val SLEEP_NAG_MAX_AGE_MONTHS = 12

    /**
     * Ordered by what actually costs the child if missed: a late vaccine, then a running illness,
     * then a booking, then tracking gaps. Anything merely "nice to log" sits last.
     */
    fun suggest(s: Signals): Suggestion? {
        val due = s.nextVaccineDueOn
        if (due != null && due.isBefore(s.today)) {
            return Suggestion(Kind.VACCINE_OVERDUE, ChronoUnit.DAYS.between(due, s.today))
        }

        val illness = s.lastIllnessOn
        if (illness != null && !illness.isAfter(s.today)) {
            val since = ChronoUnit.DAYS.between(illness, s.today)
            if (since <= ILLNESS_ACTIVE_DAYS) return Suggestion(Kind.ILLNESS_FOLLOW_UP, since)
        }

        if (due != null) {
            val until = ChronoUnit.DAYS.between(s.today, due)
            if (until <= VACCINE_SOON_DAYS) return Suggestion(Kind.VACCINE_SOON, until)
        }

        val growth = s.lastGrowthOn
        if (growth == null) return Suggestion(Kind.GROWTH_NEVER)
        val growthAge = ChronoUnit.DAYS.between(growth, s.today)
        if (growthAge >= GROWTH_STALE_DAYS) return Suggestion(Kind.GROWTH_STALE, growthAge)

        if (s.pendingMilestones > 0) {
            return Suggestion(Kind.MILESTONE, s.pendingMilestones.toLong())
        }

        // Only for babies, and only when today has nothing yet.
        val age = s.ageMonths
        if (age != null && age <= SLEEP_NAG_MAX_AGE_MONTHS && s.lastSleepOn != s.today) {
            return Suggestion(Kind.SLEEP_TODAY)
        }

        return null
    }
}
