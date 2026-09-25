package com.lullabyai.app.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The pantry's two safety questions, kept out of the UI so they can be tested: how close a stored
 * ingredient is to its expiry date, and where a child is in the 3-4 day watch for a new food.
 *
 * Both deliberately refuse to guess. An ingredient with no expiry date is not "fresh", it is
 * UNKNOWN; a food with no trial for THIS child is not "safe", it is UNTESTED. A green badge nobody
 * earned is the one thing this screen must not show a parent.
 *
 * (Expense receipts have their own expiry reminders in ProductExpirationWorker — different table,
 * different job: that one pushes notifications about things you bought, this one sorts the fridge.)
 */
object PantrySafety {

    /** How many days from [today] an ingredient is still good for. Null when nobody entered a date. */
    fun daysLeft(expiresOn: String?, today: LocalDate = LocalDate.now()): Long? {
        val date = runCatching { LocalDate.parse(expiresOn.orEmpty().take(10)) }.getOrNull() ?: return null
        return ChronoUnit.DAYS.between(today, date)
    }

    enum class Freshness { EXPIRED, URGENT, SOON, FINE, UNKNOWN }

    /** URGENT is "cook it today or tomorrow"; SOON fills the design's "ภายใน 3 วัน" list. */
    fun freshness(expiresOn: String?, today: LocalDate = LocalDate.now()): Freshness {
        val days = daysLeft(expiresOn, today) ?: return Freshness.UNKNOWN
        return when {
            days < 0 -> Freshness.EXPIRED
            days <= 1 -> Freshness.URGENT
            days <= 3 -> Freshness.SOON
            else -> Freshness.FINE
        }
    }

    /** The design's "ใกล้หมดอายุ ต้องรีบปรุง" section: already gone, or due within three days. */
    fun needsCookingSoon(expiresOn: String?, today: LocalDate = LocalDate.now()): Boolean =
        freshness(expiresOn, today) in setOf(Freshness.EXPIRED, Freshness.URGENT, Freshness.SOON)

    /**
     * Introduce one new food at a time and watch for [WATCH_DAYS] days before judging it — the
     * 3-4 day wait rule, counted at its safer end.
     */
    const val WATCH_DAYS = 4

    enum class TrialState { UNTESTED, WATCHING, DUE_TO_DECIDE, SAFE, ALLERGIC }

    /**
     * Where this child stands on a food. [result] and [startedOn] come from that child's own
     * food_trials row; no row means UNTESTED, never SAFE.
     */
    fun trialState(result: String?, startedOn: String?, today: LocalDate = LocalDate.now()): TrialState =
        when (result) {
            "safe" -> TrialState.SAFE
            "allergic" -> TrialState.ALLERGIC
            "testing" -> {
                val day = watchDay(startedOn, today)
                if (day != null && day >= WATCH_DAYS) TrialState.DUE_TO_DECIDE else TrialState.WATCHING
            }
            else -> TrialState.UNTESTED
        }

    /** Which day of the watch this is, counting the first day as 1. Null when the date is unreadable. */
    fun watchDay(startedOn: String?, today: LocalDate = LocalDate.now()): Int? {
        val start = runCatching { LocalDate.parse(startedOn.orEmpty().take(10)) }.getOrNull() ?: return null
        return (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceAtLeast(1)
    }

    /** "ทดสอบวันที่ 2/4" — null unless a watch is actually running. */
    fun watchLabel(result: String?, startedOn: String?, isThai: Boolean, today: LocalDate = LocalDate.now()): String? {
        if (result != "testing") return null
        val day = watchDay(startedOn, today)?.coerceAtMost(WATCH_DAYS) ?: return null
        return if (isThai) "ทดสอบวันที่ $day/$WATCH_DAYS" else "Day $day of $WATCH_DAYS"
    }
}
