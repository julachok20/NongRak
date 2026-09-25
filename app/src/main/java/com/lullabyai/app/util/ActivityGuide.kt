package com.lullabyai.app.util

/**
 * How much a small child should move in a day, and how that sits beside what they ate.
 *
 * The target is MINUTES, not calories. The WHO 2019 guidance for under-fives is given in time —
 * 30 minutes of tummy time and floor play a day for babies who do not yet walk, 180 minutes of
 * activity spread through the day once they do. Energy burned by a toddler cannot be measured by a
 * phone, so kcal is only ever an estimate of the activities someone actually logged, and it is
 * never used to judge whether the child ate too much.
 */
object ActivityGuide {

    /** Minutes of active play a day for this age, from the WHO under-five guidance. */
    fun dailyMinutesGoal(ageMonths: Int): Int = if (ageMonths < 12) 30 else 180

    /** What the goal means in words, so the number on screen is never just a number. */
    fun goalLabel(ageMonths: Int, isThai: Boolean): String = when {
        ageMonths < 12 -> if (isThai) "เวลานอนคว่ำและเล่นบนพื้น วันละ 30 นาที (เกณฑ์ WHO)"
        else "30 minutes a day of tummy time and floor play (WHO)"
        else -> if (isThai) "เล่นและเคลื่อนไหว วันละ 180 นาที กระจายตลอดวัน (เกณฑ์ WHO)"
        else "180 minutes of play and movement spread through the day (WHO)"
    }

    enum class Progress { NONE_YET, STARTED, ALMOST, MET }

    fun progress(minutesToday: Int, goalMinutes: Int): Progress = when {
        minutesToday <= 0 -> Progress.NONE_YET
        goalMinutes <= 0 || minutesToday >= goalMinutes -> Progress.MET
        minutesToday >= goalMinutes * 0.6 -> Progress.ALMOST
        else -> Progress.STARTED
    }

    /**
     * How eating and moving line up today. Deliberately not a calorie balance: for a child this age
     * the useful question is whether both sides are being looked after, and a shortfall in either is
     * answered by more play or more food, never by less food.
     */
    enum class Balance { NO_DATA, BOTH_GOOD, MOVE_MORE, EAT_MORE, EARLY }

    fun balance(intakeKcal: Int, targetKcal: Int, minutesToday: Int, goalMinutes: Int): Balance {
        if (targetKcal <= 0 || goalMinutes <= 0) return Balance.NO_DATA
        if (intakeKcal <= 0 && minutesToday <= 0) return Balance.NO_DATA
        val ateEnough = intakeKcal >= targetKcal * 0.8
        val movedEnough = minutesToday >= goalMinutes * 0.8
        return when {
            ateEnough && movedEnough -> Balance.BOTH_GOOD
            ateEnough && !movedEnough -> Balance.MOVE_MORE
            !ateEnough && movedEnough -> Balance.EAT_MORE
            // Early in the day both are naturally short; saying "do more of everything" helps nobody.
            else -> Balance.EARLY
        }
    }

    /** Average kcal a day over [days] of logs, for the weekly strip. Null when nothing was logged. */
    fun dailyAverage(totalKcal: Int, days: Int): Int? =
        if (days <= 0 || totalKcal <= 0) null else totalKcal / days
}
