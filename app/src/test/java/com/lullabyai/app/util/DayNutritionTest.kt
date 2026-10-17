package com.lullabyai.app.util

import com.lullabyai.app.util.DayNutrition.Portion
import com.lullabyai.app.util.DayNutrition.Source
import com.lullabyai.app.util.DayNutrition.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayNutritionTest {

    @Test
    fun `portion weight comes from logged kcal and the table energy`() {
        assertEquals(200.0, DayNutrition.gramsFromKcal(200, 100.0)!!, 1e-6)
        assertEquals(50.0, DayNutrition.gramsFromKcal(80, 160.0)!!, 1e-6)
        assertNull(DayNutrition.gramsFromKcal(null, 100.0))
        assertNull(DayNutrition.gramsFromKcal(80, 0.0))
    }

    @Test
    fun `nutrients scale from the same portion and gaps are counted`() {
        val rice = Portion("ข้าว", Source.TABLE, grams = 100.0, per100 = mapOf("kcal" to 130.0, "protein" to 2.5))
        val egg = Portion("ไข่", Source.AI, grams = 50.0, per100 = mapOf("kcal" to 140.0))
        val unknown = Portion("นมแม่เข้าเต้า 10 นาที", Source.NO_PORTION)
        val totals = DayNutrition.total(listOf(rice, egg, unknown), listOf("kcal", "protein"))

        assertEquals(200.0, totals.amount["kcal"]!!, 1e-6)          // 130 + 70
        assertEquals(2.5, totals.amount["protein"]!!, 1e-6)          // egg had no protein figure
        assertEquals(1, totals.missing["protein"])
        assertEquals(1, totals.tableCount)
        assertEquals(1, totals.aiCount)
        assertEquals(1, totals.uncounted.size)
    }

    @Test
    fun `a gap is incomplete not low unless the known amount already covers the target`() {
        assertEquals(Status.ENOUGH, DayNutrition.status(9.0, 10.0, missing = 2))
        assertEquals(Status.INCOMPLETE, DayNutrition.status(4.0, 10.0, missing = 1))
        assertEquals(Status.FAIRLY_LOW, DayNutrition.status(6.0, 10.0, missing = 0))
        assertEquals(Status.LOW, DayNutrition.status(2.0, 10.0, missing = 0))
    }

    @Test
    fun `sodium over the range is too much even when some foods had no figure`() {
        val range = 200.0..400.0
        assertEquals(Status.TOO_MUCH, DayNutrition.rangeStatus(500.0, range, missing = 1))
        assertEquals(Status.ENOUGH, DayNutrition.rangeStatus(300.0, range, missing = 0))
        assertEquals(Status.INCOMPLETE, DayNutrition.rangeStatus(100.0, range, missing = 1))
        assertEquals(Status.ENOUGH, DayNutrition.rangeStatus(100.0, range, missing = 0))
    }
}
