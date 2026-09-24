package com.lullabyai.app.util

import com.lullabyai.app.util.DayNutrition.Portion
import com.lullabyai.app.util.DayNutrition.Source
import com.lullabyai.app.util.DayNutrition.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * The nutrition statistics page's numbers: the bundled DRI 2563 and food table (read from the real
 * asset files, so a transcription slip fails here), the name matcher, and the day arithmetic.
 */
class NutritionReferenceTest {

    companion object {
        @BeforeClass @JvmStatic fun setUp() {
            DriReference.install(File("src/main/assets/nutrition/dri_th_2563.json").readText())
            ThaiFoodTable.install(File("src/main/assets/nutrition/thai_fct_2561.json").readText())
        }
    }

    private fun near(expected: Double, actual: Double?, tol: Double = 0.01) =
        assertTrue("expected $expected, got $actual", actual != null && kotlin.math.abs(expected - actual) <= tol)

    // ---------------------------------------------------------------- DRI

    @Test fun `a 2-year-old boy gets the 1-3 y figures from the book`() {
        val t = DriReference.forChild(24, "male")!!
        assertEquals(1050, t.energyKcal)
        near(16.0, t.proteinG)
        near(500.0, t.values["calcium"])
        near(5.0, t.values["iron"])
        near(4.4, t.values["zinc"])
        near(300.0, t.values["vitaminA"])
        assertEquals(225.0..675.0, t.ranges["sodium"])
    }

    @Test fun `carbohydrate and fat are the DRI energy shares, fat wider before age two`() {
        val boy2 = DriReference.forChild(24, "male")!!          // 25-35 % fat from 24 months
        near(1050 * 0.45 / 4, boy2.carbG!!.start); near(1050 * 0.65 / 4, boy2.carbG!!.endInclusive)
        near(1050 * 0.25 / 9, boy2.fatG!!.start); near(1050 * 0.35 / 9, boy2.fatG!!.endInclusive)
        val boy18m = DriReference.forChild(18, "male")!!        // 35-40 % fat before 24 months
        near(1050 * 0.35 / 9, boy18m.fatG!!.start); near(1050 * 0.40 / 9, boy18m.fatG!!.endInclusive)
    }

    @Test fun `sex not set averages boys and girls`() {
        assertEquals(1015, DriReference.forChild(24, null)!!.energyKcal)   // (1050 + 980) / 2
    }

    @Test fun `under six months breast milk covers it and there is no protein figure`() {
        val t = DriReference.forChild(3, "female")!!
        assertTrue(t.breastMilkCovers)
        assertEquals(460, t.energyKcal)
        assertNull(t.proteinG)
    }

    @Test fun `9-12 year olds use their own sex's group`() {
        val girl = DriReference.forChild(120, "female")!!
        assertEquals(1650, girl.energyKcal)
        near(12.5, girl.values["iron"])
        assertEquals(1800, DriReference.forChild(120, "male")!!.energyKcal)
    }

    @Test fun `past 12 years there is no target rather than a wrong one`() {
        assertNull(DriReference.forChild(200, "male"))
    }

    // ---------------------------------------------------------------- food table

    @Test fun `not analysed stays unknown instead of becoming zero`() {
        val noodle = ThaiFoodTable.byId("01001")!!               // magnesium is "-" in the book
        assertFalse("magnesium" in noodle.n)
        near(29.0, noodle.n["calcium"])
    }

    @Test fun `a misprinted energy is replaced by the book's own macros`() {
        val cherry = ThaiFoodTable.byId("05048")!!               // prints 49 kcal against 31.9 g carb
        assertTrue(cherry.kcalPer100g()!! > 120)
        val egg = ThaiFoodTable.byId("08003")!!
        near(143.0, egg.kcalPer100g())                            // normal rows keep the printed value
    }

    @Test fun `everyday words find the table row`() {
        assertEquals("01017", ThaiFoodTable.match("ข้าวสวย")!!.food.id)
        assertEquals("08003", ThaiFoodTable.match("ไข่ต้ม 1 ฟอง")!!.food.id)
        assertEquals("09011", ThaiFoodTable.match("นมแม่")!!.food.id)
        assertEquals("05021", ThaiFoodTable.match("กล้วยหอม")!!.food.id)   // longer alias beats "กล้วย"
        assertEquals("11044", ThaiFoodTable.match("ข้าวผัดหมู")!!.food.id)
        assertEquals("08003", ThaiFoodTable.match("ไข่ไก่, ทั้งฟอง")!!.food.id)
    }

    @Test fun `a dish the table lacks is not forced onto a loosely similar row`() {
        assertNull(ThaiFoodTable.match("ข้าวต้มหมู"))            // "ข้าว" alone covers too little
        assertNull(ThaiFoodTable.match("พิซซ่าฮาวายเอี้ยน"))
    }

    @Test fun `spot checks across every bundled group have energy`() {
        listOf("01017", "02013", "03011", "04121", "05015", "06050", "07035", "08003", "09003", "11007", "12001").forEach { id ->
            val f = ThaiFoodTable.byId(id)
            assertNotNull(id, f)
            assertTrue("$id has no energy", f!!.kcalPer100g() != null)
        }
    }

    // ---------------------------------------------------------------- day arithmetic

    @Test fun `grams come from the logged kcal`() {
        near(100.0, DayNutrition.gramsFromKcal(143, 143.0))
        near(50.0, DayNutrition.gramsFromKcal(71, 142.0))
        assertNull(DayNutrition.gramsFromKcal(null, 143.0))
        assertNull(DayNutrition.gramsFromKcal(100, 0.0))
    }

    @Test fun `totals scale per 100 g and count missing values separately`() {
        val t = DayNutrition.total(
            listOf(
                Portion("egg", Source.TABLE, grams = 50.0, per100 = mapOf("iron" to 1.8, "calcium" to 70.0)),
                Portion("rice", Source.TABLE, grams = 200.0, per100 = mapOf("calcium" to 0.0)),
                Portion("unknown dish", Source.NO_DATA)
            ),
            listOf("iron", "calcium")
        )
        near(0.9, t.amount["iron"])
        near(35.0, t.amount["calcium"])
        assertEquals(1, t.missing["iron"])        // rice has no iron figure — unknown, not zero
        assertNull(t.missing["calcium"])
        assertEquals(1, t.uncounted.size)
        assertEquals(2, t.tableCount)
    }

    @Test fun `status never calls a nutrient low on missing data`() {
        assertEquals(Status.ENOUGH, DayNutrition.status(4.6, 5.0, missing = 2))
        assertEquals(Status.INCOMPLETE, DayNutrition.status(2.0, 5.0, missing = 1))
        assertEquals(Status.FAIRLY_LOW, DayNutrition.status(3.0, 5.0, missing = 0))
        assertEquals(Status.LOW, DayNutrition.status(1.0, 5.0, missing = 0))
    }

    // ---------------------------------------------------------------- main nutrition page

    @Test fun `table groups map onto the Thai 5 food groups`() {
        val g = { id: String -> DayNutrition.thaiGroups(ThaiFoodTable.byId(id)!!.group) }
        assertEquals(setOf(2), g("01017"))          // steamed rice
        assertEquals(setOf(1), g("08003"))          // egg is หมู่ 1 even though most of its energy is fat
        assertEquals(setOf(1), g("09011"))          // breast milk
        assertEquals(setOf(4), g("05021"))          // banana
        assertNull(g("11044"))                      // fried rice is a dish: the AI decides
    }

    @Test fun `overweight risk leans on the body measurement, not one big day`() {
        assertEquals(DayNutrition.Risk.HIGH, DayNutrition.overweightRisk(2.4, 0.8))
        assertEquals(DayNutrition.Risk.WATCH, DayNutrition.overweightRisk(1.3, 1.0))
        assertEquals(DayNutrition.Risk.WATCH, DayNutrition.overweightRisk(0.0, 1.3))
        assertEquals(DayNutrition.Risk.LOW, DayNutrition.overweightRisk(0.2, 1.1))
        assertEquals(DayNutrition.Risk.UNKNOWN, DayNutrition.overweightRisk(null, 1.0))
    }

    @Test fun `overall share caps each nutrient so a surplus cannot hide a gap`() {
        near(0.83, DayNutrition.overallShare(listOf(14.0 to 16.0, 55.0 to 70.0, 420.0 to 500.0, 6.5 to 8.0)))
        near(0.75, DayNutrition.overallShare(listOf(30.0 to 15.0, 5.0 to 10.0)))
        assertNull(DayNutrition.overallShare(listOf(3.0 to 0.0)))
    }

    // ---------------------------------------------------------------- food log cards

    @Test fun `fibre target is the age-plus-five rule, and nothing under two years`() {
        assertNull(DayNutrition.fibreTargetG(18))
        near(7.0, DayNutrition.fibreTargetG(24))      // 2 y + 5
        near(10.0, DayNutrition.fibreTargetG(65))     // 5 y + 5
    }

    @Test fun `a serving is only called high in what it really covers a quarter of`() {
        val targets = mapOf("iron" to 8.0, "calcium" to 500.0, "protein" to 16.0)
        val amounts = mapOf("iron" to 2.4, "calcium" to 40.0, "protein" to 6.0)
        assertEquals(listOf("protein", "iron"), DayNutrition.strongPoints(amounts, targets))   // best first
        assertEquals(emptyList<String>(), DayNutrition.strongPoints(mapOf("iron" to 1.0), targets))
        assertEquals(emptyList<String>(), DayNutrition.strongPoints(mapOf("iron" to 5.0), mapOf("iron" to 0.0)))
    }

    @Test fun `a serving's own amount comes from its weight`() {
        val p = Portion("egg", Source.TABLE, grams = 50.0, per100 = mapOf("iron" to 1.8))
        near(0.9, p.amountOf("iron"))
        assertNull(p.amountOf("calcium"))                                    // not in the table row
        assertNull(Portion("x", Source.NO_PORTION, per100 = mapOf("iron" to 1.8)).amountOf("iron"))
    }

    @Test fun `sodium over the range is flagged even with gaps`() {
        assertEquals(Status.TOO_MUCH, DayNutrition.rangeStatus(900.0, 225.0..675.0, missing = 3))
        assertEquals(Status.ENOUGH, DayNutrition.rangeStatus(400.0, 225.0..675.0, missing = 0))
    }
}
