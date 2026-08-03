package com.lullabyai.app.data.sleep

/** Age-based wake-window guidance (0–3y) — shared by SleepGuidance + SleepTracker. */
data class WakeWindowBand(
    val ageRangeTh: String,
    val ageRangeEn: String,
    val windowTh: String,
    val windowEn: String,
    val minMonths: Int,
    val maxMonths: Int,
    /** Numeric form of [windowTh]/[windowEn]; the display strings can't drive a progress bar. */
    val minWakeMinutes: Int,
    val maxWakeMinutes: Int
)

object SleepWakeWindows {
    val ALL = listOf(
        WakeWindowBand("0-2 เดือน", "0-2 months", "45-60 นาที", "45-60 min", 0, 2, 45, 60),
        WakeWindowBand("3-4 เดือน", "3-4 months", "1-1.5 ชม.", "1-1.5 hr", 3, 4, 60, 90),
        WakeWindowBand("5-7 เดือน", "5-7 months", "2-3 ชม.", "2-3 hr", 5, 7, 120, 180),
        WakeWindowBand("8-12 เดือน", "8-12 months", "3-4 ชม.", "3-4 hr", 8, 12, 180, 240),
        WakeWindowBand("13-24 เดือน", "13-24 months", "4-5 ชม.", "4-5 hr", 13, 24, 240, 300),
        WakeWindowBand("25-36 เดือน", "25-36 months", "5-6 ชม.", "5-6 hr", 25, 36, 300, 360)
    )

    fun forAgeMonths(ageMonths: Int): WakeWindowBand =
        ALL.firstOrNull { ageMonths in it.minMonths..it.maxMonths } ?: ALL.last()
}
