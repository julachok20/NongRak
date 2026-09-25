package com.lullabyai.app.util

/**
 * What a day's milk and water should look like at a given age, for the recorder's hydration card.
 *
 * These are guides, not the DRI: the Thai DRI 2563 this app bundles gives energy and nutrients, not
 * millilitres. Milk is therefore derived from the child's own energy target at the density of
 * breast milk, and water follows the paediatric advice the screen also prints in words — none under
 * six months, small sips with meals after that.
 *
 * Every function returns null where no honest number exists, so the card shows a dash rather than a
 * target the parent could fail against.
 *
 * (NutritionRepository has its own kcal-per-ml constant for the opposite conversion — turning
 * logged millilitres into the day's energy.)
 */
object HydrationGuide {

    /** Breast milk and infant formula both run about 0.67 kcal per millilitre. */
    const val MILK_KCAL_PER_ML = 0.67

    /**
     * Millilitres of milk a day, from [energyKcal] — the child's own DRI energy figure.
     *
     * Only for babies whose diet is still mostly milk. Once solids are the bulk of the day (from
     * about a year) a millilitre target would be wrong in both directions, so this returns null and
     * the card shows what was drunk with no bar.
     */
    fun milkTargetMl(ageMonths: Int, energyKcal: Int?): Int? {
        if (ageMonths >= 12 || energyKcal == null || energyKcal <= 0) return null
        // Under six months milk is the whole diet; from six to twelve months solids take a growing
        // share, so only part of the energy should come from milk.
        val fromMilk = if (ageMonths < 6) 1.0 else 0.6
        return (energyKcal * fromMilk / MILK_KCAL_PER_ML).toInt()
    }

    /**
     * Millilitres of plain water a day.
     *
     * Under six months: 0. A breastfed or formula-fed baby needs no water, and water in place of
     * milk is a real risk, so this is a deliberate zero and not a missing value.
     * 6-11 months: sips with meals, around 120 ml a day.
     * A year and up: roughly a cup per year of age, stopping at four cups.
     */
    fun waterTargetMl(ageMonths: Int): Int = when {
        ageMonths < 6 -> 0
        ageMonths < 12 -> 120
        else -> (ageMonths / 12).coerceAtMost(4) * 240
    }

    /** True when offering plain water is against the advice for this age — the screen must say so. */
    fun waterNotAdvised(ageMonths: Int): Boolean = ageMonths < 6

    enum class Balance { NONE_YET, LOW, GOOD, OVER, UNKNOWN }

    /** How today's total compares with [target]. A null target means there is nothing to compare. */
    fun balance(amountMl: Int, target: Int?): Balance = when {
        target == null || target <= 0 -> Balance.UNKNOWN
        amountMl <= 0 -> Balance.NONE_YET
        amountMl >= target * 1.25 -> Balance.OVER
        amountMl >= target * 0.8 -> Balance.GOOD
        else -> Balance.LOW
    }

    /** Millilitres to fluid ounces, the unit the bottle is marked in. */
    fun toOunces(ml: Int): Double = ml / 29.5735
}
