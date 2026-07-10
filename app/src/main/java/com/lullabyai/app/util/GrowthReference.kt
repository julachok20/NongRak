package com.lullabyai.app.util

import androidx.compose.ui.graphics.Color

/**
 * Approximate, gender-neutral WHO Child Growth Standards bands (0–36 months) — roughly P3–P97,
 * widened slightly for safety. This is NOT a substitute for a doctor's percentile chart; it exists
 * so the growth cards stop showing a hardcoded "Normal range" label for every value (e.g. a 114kg
 * entry was previously always labeled normal). Values are linearly interpolated between the
 * breakpoints below; ages beyond 36 months reuse the 36-month band (app is scoped to 0–3y).
 */
object GrowthReference {
    // month -> (min kg, max kg)
    private val weightBandsKg = listOf(
        0 to (2.0 to 4.5), 1 to (3.0 to 5.8), 2 to (3.8 to 6.6), 3 to (4.4 to 7.4),
        4 to (5.0 to 8.0), 6 to (5.8 to 9.3), 9 to (6.7 to 10.5), 12 to (7.2 to 11.3),
        18 to (8.0 to 12.6), 24 to (8.6 to 13.9), 30 to (9.2 to 15.0), 36 to (9.7 to 16.0)
    )
    // month -> (min cm, max cm)
    private val heightBandsCm = listOf(
        0 to (45.0 to 55.0), 1 to (49.0 to 59.0), 2 to (52.0 to 63.0), 3 to (55.0 to 66.0),
        4 to (57.0 to 68.0), 6 to (61.0 to 72.0), 9 to (65.0 to 76.0), 12 to (68.0 to 80.0),
        18 to (73.0 to 86.0), 24 to (78.0 to 92.0), 30 to (82.0 to 97.0), 36 to (86.0 to 102.0)
    )

    /** Public band lookup for charting (min, max) at a given age — same approximate bands used by
     *  weightStatus/heightStatus, exposed so a growth-history chart can draw a reference range. */
    fun band(ageMonths: Int, isWeight: Boolean): Pair<Double, Double> = bandAt(ageMonths, if (isWeight) weightBandsKg else heightBandsCm)

    private fun bandAt(ageMonths: Int, bands: List<Pair<Int, Pair<Double, Double>>>): Pair<Double, Double> {
        val clamped = ageMonths.coerceIn(0, 36)
        val lo = bands.lastOrNull { it.first <= clamped } ?: bands.first()
        val hi = bands.firstOrNull { it.first >= clamped } ?: bands.last()
        if (lo.first == hi.first) return lo.second
        val t = (clamped - lo.first).toDouble() / (hi.first - lo.first)
        val min = lo.second.first + (hi.second.first - lo.second.first) * t
        val max = lo.second.second + (hi.second.second - lo.second.second) * t
        return min to max
    }

    data class GrowthStatus(val labelTh: String, val labelEn: String, val color: Color)

    private fun statusFor(value: Double, min: Double, max: Double): GrowthStatus = when {
        value > max * 1.75 || value < min * 0.5 ->
            GrowthStatus("ผิดปกติมาก ควรพบแพทย์", "Highly unusual — see a doctor", Color(0xFFC62828))
        value > max -> GrowthStatus("สูงกว่าเกณฑ์", "Above range", Color(0xFFEF6C00))
        value < min -> GrowthStatus("ต่ำกว่าเกณฑ์", "Below range", Color(0xFFEF6C00))
        else -> GrowthStatus("อยู่ในเกณฑ์ปกติ", "Normal range", Color(0xFF2E7D32))
    }

    fun weightStatus(weightKg: Double?, ageMonths: Int): GrowthStatus? {
        val w = weightKg ?: return null
        val (min, max) = bandAt(ageMonths, weightBandsKg)
        return statusFor(w, min, max)
    }

    fun heightStatus(heightCm: Double?, ageMonths: Int): GrowthStatus? {
        val h = heightCm ?: return null
        val (min, max) = bandAt(ageMonths, heightBandsCm)
        return statusFor(h, min, max)
    }
}
