package com.lullabyai.app.util

/**
 * One day's nutrient totals from logged foods, and how each total compares with the DRI target.
 * Pure — no Android, no AI — so the arithmetic is unit-tested.
 *
 * A food log holds a name and the kcal of the portion, not its weight. The weight comes from the
 * energy: grams = logged kcal ÷ kcal per 100 g × 100. Every other nutrient then scales from the same
 * per-100 g row, so the portion estimate is used once and the nutrient mix stays the official one.
 */
object DayNutrition {

    enum class Source {
        /** Per-100 g values from ตารางแสดงคุณค่าทางโภชนาการของอาหารไทย. */
        TABLE,
        /** Not in the table; per-100 g values estimated by AI. */
        AI,
        /** Composition known but the portion is not (no kcal logged, or breastfed by minutes). */
        NO_PORTION,
        /** Neither the table nor the AI could say what this is. */
        NO_DATA
    }

    data class Portion(
        val label: String,
        val source: Source,
        /** The table row used, for TABLE. */
        val matchedName: String? = null,
        val grams: Double? = null,
        val per100: Map<String, Double> = emptyMap()
    )

    data class Totals(
        /** Sum over portions that have both a weight and a value for the nutrient. */
        val amount: Map<String, Double>,
        /** Portions with a weight but no value for the nutrient (not analysed / not returned). */
        val missing: Map<String, Int>,
        val portions: List<Portion>
    ) {
        val tableCount get() = portions.count { it.source == Source.TABLE }
        val aiCount get() = portions.count { it.source == Source.AI }
        val uncounted get() = portions.filter { it.source == Source.NO_PORTION || it.source == Source.NO_DATA }
    }

    fun gramsFromKcal(loggedKcal: Int?, kcalPer100: Double?): Double? {
        if (loggedKcal == null || loggedKcal <= 0 || kcalPer100 == null || kcalPer100 <= 0.0) return null
        return loggedKcal / kcalPer100 * 100.0
    }

    fun total(portions: List<Portion>, keys: Collection<String>): Totals {
        val amount = mutableMapOf<String, Double>()
        val missing = mutableMapOf<String, Int>()
        portions.filter { it.grams != null && it.grams > 0.0 }.forEach { p ->
            val factor = (p.grams ?: 0.0) / 100.0
            keys.forEach { k ->
                val v = p.per100[k]
                if (v == null) missing[k] = (missing[k] ?: 0) + 1
                else amount[k] = (amount[k] ?: 0.0) + v * factor
            }
        }
        return Totals(amount, missing, portions)
    }

    enum class Status { ENOUGH, FAIRLY_LOW, LOW, TOO_MUCH, INCOMPLETE }

    /**
     * Against a single-value target. At or above 90 % is enough whatever else is unknown. Below
     * that, a nutrient some foods had no figure for is INCOMPLETE rather than low: the missing
     * foods may well have covered it, and "ต่ำ" on missing data is the worse lie.
     */
    fun status(amount: Double, target: Double, missing: Int): Status {
        if (target <= 0.0) return Status.ENOUGH
        val pct = amount / target
        return when {
            pct >= 0.9 -> Status.ENOUGH
            missing > 0 -> Status.INCOMPLETE
            pct >= 0.5 -> Status.FAIRLY_LOW
            else -> Status.LOW
        }
    }

    /** Against a range (sodium): over the top is a problem even with gaps, since it is a floor. */
    fun rangeStatus(amount: Double, range: ClosedFloatingPointRange<Double>, missing: Int): Status = when {
        amount > range.endInclusive -> Status.TOO_MUCH
        amount >= range.start -> Status.ENOUGH
        missing > 0 -> Status.INCOMPLETE
        else -> Status.ENOUGH   // under the sodium range is not a concern for a child's day
    }
}
