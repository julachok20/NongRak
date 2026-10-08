package com.lullabyai.app.util

import com.lullabyai.app.data.VaccineSetupCatalog

/**
 * Roughly how many hours a child this age sleeps in a day, per standard paediatric guidance.
 *
 * Shared so the bands live in ONE place. They previously existed only inside
 * ChildViewModel.sleepTargetHours(), and onboarding needs the same answer before any child exists
 * to be the active one - copying them would have let two parts of a childcare app disagree about
 * what a healthy amount of sleep is.
 *
 * This is a STARTING POINT for a parent to adjust, never a measurement. Nothing here may be
 * written to a child record as though the parent had said it.
 */
object SleepTarget {

    /** Hours per day for [ageMonths]. Ages beyond the bands fall to the oldest one. */
    fun forAgeMonths(ageMonths: Int): Int = when (ageMonths) {
        in 0..3 -> 15
        in 4..11 -> 14
        in 12..23 -> 13
        else -> 11
    }

    /**
     * The same, from a birthdate.
     *
     * A blank or unparseable birthdate yields the 0-3 month figure, because
     * [VaccineSetupCatalog.ageMonthsFromBirthdate] returns 0 for one. That is precisely why this is
     * only ever a suggested starting value, and why nothing calls it to fill a record unprompted.
     */
    fun forBirthdate(birthdate: String?): Int =
        forAgeMonths(VaccineSetupCatalog.ageMonthsFromBirthdate(birthdate.orEmpty()))
}
