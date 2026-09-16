package com.lullabyai.app.util

import com.lullabyai.app.util.GrowthReference.Indicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.pow

class GrowthReferenceTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadWhoTables() {
            // Gradle runs unit tests from the module directory, so the bundled asset files are here.
            GrowthReference.install(Indicator.entries.associateWith { File("src/main/assets/who/${it.file}.txt").readText() })
        }

        private fun at(ind: Indicator, sex: Int, day: Int, z: Double): Double {
            val t = GrowthReference.lms(ind, sex, day)!!
            return t.m * (1 + t.l * t.s * z).pow(1 / t.l)
        }
    }

    @Test
    fun `WHO median is z 0 and the plus 2 SD value is z 2`() {
        assertEquals(0.0, GrowthReference.zScore(Indicator.WEIGHT, 3.3464, 0, "male")!!, 1e-4)
        assertEquals(2.0, GrowthReference.zScore(Indicator.WEIGHT, at(Indicator.WEIGHT, 1, 365, 2.0), 365, "male")!!, 1e-6)
        assertEquals("อยู่ในเกณฑ์ปกติ", GrowthReference.weightStatus(3.3464, 0, "male")!!.labelTh)
    }

    @Test
    fun `same weight reads differently at different ages`() {
        // 9 kg: normal for a 1-year-old boy, far too heavy for a newborn.
        assertEquals("อยู่ในเกณฑ์ปกติ", GrowthReference.weightStatus(9.0, 365, "male")!!.labelTh)
        assertNotEquals("อยู่ในเกณฑ์ปกติ", GrowthReference.weightStatus(9.0, 0, "male")!!.labelTh)
    }

    @Test
    fun `typo values are flagged, not called normal or overweight`() {
        assertEquals("ค่านี้ดูผิดปกติ ตรวจสอบตัวเลขที่บันทึก", GrowthReference.weightStatus(114.0, 365, "female")!!.labelTh)
    }

    @Test
    fun `short and thin use age and sex`() {
        val short = at(Indicator.HEIGHT, 2, 730, -2.5)
        assertEquals("ค่อนข้างเตี้ย ต่ำกว่าเกณฑ์", GrowthReference.heightStatus(short, 730, "female")!!.labelTh)

        // A BMI that is thin at 3 years is not thin at birth: the old fixed cut-off ignored this.
        val thinBmi = at(Indicator.BMI, 1, 1095, -2.5)
        assertEquals("ผอมกว่าเกณฑ์", GrowthReference.weightForHeightStatus(thinBmi, 100.0, 1095, "male")!!.labelTh)
        assertNotEquals("ผอมกว่าเกณฑ์", GrowthReference.weightForHeightStatus(thinBmi, 100.0, 0, "male")!!.labelTh)
    }

    @Test
    fun `unknown age gives no status and over 5 years says so`() {
        assertNull(GrowthReference.ageDaysAt(null, "2026-01-01T00:00:00+00:00"))
        assertNull(GrowthReference.weightStatus(10.0, null, "male"))
        assertEquals("เกินช่วงอ้างอิง WHO (0–5 ปี)", GrowthReference.weightStatus(20.0, 2000, "male")!!.labelTh)
    }

    @Test
    fun `sex not set averages boys and girls`() {
        val boy = GrowthReference.zScore(Indicator.WEIGHT, 9.0, 365, "male")!!
        val girl = GrowthReference.zScore(Indicator.WEIGHT, 9.0, 365, "female")!!
        assertEquals((boy + girl) / 2, GrowthReference.zScore(Indicator.WEIGHT, 9.0, 365, "unspecified")!!, 1e-9)
    }

    @Test
    fun `age in days is taken at the measurement date`() {
        assertEquals(30, GrowthReference.ageDaysAt("2024-01-01", "2024-01-31"))
        assertNull(GrowthReference.ageDaysAt("2024-02-01", "2024-01-31"))
    }
}
