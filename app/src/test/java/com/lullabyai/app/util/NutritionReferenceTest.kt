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

    @Test fun `sodium over the range is flagged even with gaps`() {
        assertEquals(Status.TOO_MUCH, DayNutrition.rangeStatus(900.0, 225.0..675.0, missing = 3))
        assertEquals(Status.ENOUGH, DayNutrition.rangeStatus(400.0, 225.0..675.0, missing = 0))
    }
}
