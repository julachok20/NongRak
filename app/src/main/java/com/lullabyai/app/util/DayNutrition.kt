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
        val per100: Map<String, Double> = emptyMap(),
        /** The food_logs row this came from, so a screen can put the numbers back on its own card. */
        val logId: String? = null
    ) {
        /** This serving's amount of [key], or null when the weight or the figure is unknown. */
        fun amountOf(key: String): Double? {
            val g = grams ?: return null
            val per = per100[key] ?: return null
            return per * g / 100.0
        }
    }

    data class Totals(
        /** Sum over portions that have both a weight and a value for the nutrient. */
        val amount: Map<String, Double>,
        /** Portions with a weight but no value for the nutrient (not analysed / not returned). */
        val missing: Map<String, Int>,
        val portions: List<Portion>,
        /** Thai 5 food groups eaten today (see [thaiGroups]); filled in by the caller. */
        val groups: Set<Int> = emptySet(),
        /** Foods neither the table nor the AI could place in a group. */
        val groupsUnknown: List<String> = emptyList()
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

    /**
     * Which of the Thai "อาหารหลัก 5 หมู่" a single food belongs to, from its food-table group:
     * 1 protein (meat, fish, egg, milk, legumes), 2 carbohydrate (cereals, starchy roots),
     * 3 minerals (vegetables), 4 vitamins (fruit), 5 fat. Null for mixed dishes (11), desserts (12)
     * and foods not in the table — those need the AI to say what is in them.
     * ponytail: no fat-share rule. Egg, milk and nuts are หมู่ 1 in the Thai scheme even though most
     * of their energy is fat; หมู่ 5 means added oil, which กรมอนามัย tells parents to put in baby food,
     * so "missing หมู่ 5" on a plain rice-egg-banana day is the right prompt.
     */
    fun thaiGroups(tableGroup: Int?): Set<Int>? = when (tableGroup) {
        1, 2 -> setOf(2)
        3, 6, 7, 8, 9 -> setOf(1)
        4 -> setOf(3)
        5 -> setOf(4)
        else -> null
    }

    /**
     * Daily dietary fibre in grams: "age in years + 5", the rule Thai and US paediatrics both give
     * parents. The Thai DRI 2563 tables this app bundles (energy, protein, vitamins, minerals) carry
     * no fibre figure, so this is the one target that is a rule of thumb rather than a book value.
     * Null under two years: before that no body recommends a fibre amount for a child.
     */
    fun fibreTargetG(ageMonths: Int): Double? =
        if (ageMonths < 24) null else (ageMonths / 12) + 5.0

    /**
     * What one serving is genuinely good for: the nutrients it covers at least [minShare] of the
     * whole day's target for, best first. This is what turns into a "ธาตุเหล็กสูง" chip, so the
     * threshold is a real claim about the day and not a flattering label on a spoonful.
     */
    fun strongPoints(amounts: Map<String, Double>, targets: Map<String, Double>, minShare: Double = 0.25): List<String> =
        targets.mapNotNull { (key, target) ->
            val amount = amounts[key] ?: return@mapNotNull null
            val share = if (target > 0) amount / target else 0.0
            if (share >= minShare) key to share else null
        }.sortedByDescending { it.second }.map { it.first }

    enum class Risk { LOW, WATCH, HIGH, UNKNOWN }

    /**
     * Overweight risk from the WHO BMI-for-age z-score (the long-term signal) and today's energy
     * share of the DRI (the short-term one). One day over target is only worth watching; the body
     * measurement decides HIGH.
     */
    fun overweightRisk(bmiZ: Double?, energyShare: Double?): Risk = when {
        bmiZ != null && bmiZ > 2 -> Risk.HIGH
        bmiZ != null && bmiZ > 1 -> Risk.WATCH
        energyShare != null && energyShare > 1.2 -> Risk.WATCH
        bmiZ == null -> Risk.UNKNOWN
        else -> Risk.LOW
    }

    /** Average share of target over the given (amount, target) pairs, each capped at 100 % so one
     *  big surplus cannot hide a shortfall elsewhere. Null when nothing has a target. */
    fun overallShare(pairs: List<Pair<Double, Double>>): Double? =
        pairs.filter { it.second > 0 }.takeIf { it.isNotEmpty() }
            ?.map { (a, t) -> (a / t).coerceAtMost(1.0) }?.average()

    /** Against a range (sodium): over the top is a problem even with gaps, since it is a floor. */
    fun rangeStatus(amount: Double, range: ClosedFloatingPointRange<Double>, missing: Int): Status = when {
        amount > range.endInclusive -> Status.TOO_MUCH
        amount >= range.start -> Status.ENOUGH
        missing > 0 -> Status.INCOMPLETE
        else -> Status.ENOUGH   // under the sodium range is not a concern for a child's day
    }
}
