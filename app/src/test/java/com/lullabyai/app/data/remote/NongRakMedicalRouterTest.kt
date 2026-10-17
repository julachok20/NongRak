package com.lullabyai.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The routing decision decides whether a parent's question is answered by a medical model with a
 * "not a diagnosis" warning, or by the general parenting chain with none. Both failure directions
 * matter: a missed medical question loses the warning, and a false positive sends nap-schedule
 * questions to a clinical model.
 */
class NongRakMedicalRouterTest {

    @Test
    fun `thai symptom questions are medical`() {
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("ลูกมีไข้ 3 วันแล้วควรทำอย่างไร"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("ลูกมีผื่นขึ้นหลังทานอาหาร"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("ลูกท้องเสียตั้งแต่เมื่อคืน"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("ให้ยาลดไข้ขนาดเท่าไหร่"))
    }

    @Test
    fun `english symptom questions are medical`() {
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("My baby has a fever of 39"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("There is a rash on his tummy"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("How much paracetamol for a 2 year old?"))
    }

    @Test
    fun `case does not affect english detection`() {
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("FEVER and COUGH"))
        assertTrue(NongRakMedicalRouter.isMedicalQuestion("Vomiting since last night"))
    }

    @Test
    fun `ordinary parenting questions are not medical`() {
        assertFalse(NongRakMedicalRouter.isMedicalQuestion("ลูกควรนอนกี่ชั่วโมงต่อวัน"))
        assertFalse(NongRakMedicalRouter.isMedicalQuestion("How do I start tummy time?"))
        assertFalse(NongRakMedicalRouter.isMedicalQuestion("What is a good bedtime routine?"))
    }

    @Test
    fun `emergency phrases are flagged`() {
        assertTrue(NongRakMedicalRouter.isEmergency("ลูกชักและตัวเขียว"))
        assertTrue(NongRakMedicalRouter.isEmergency("My baby is not breathing properly"))
        assertTrue(NongRakMedicalRouter.isEmergency("She swallowed a battery"))
        assertTrue(NongRakMedicalRouter.isEmergency("ลูกหมดสติ"))
    }

    @Test
    fun `routine illness is medical but not an emergency`() {
        val q = "ลูกมีน้ำมูกและไอเล็กน้อย"
        assertTrue(NongRakMedicalRouter.isMedicalQuestion(q))
        assertFalse(NongRakMedicalRouter.isEmergency(q))
    }

    @Test
    fun `every emergency phrase also reads as medical`() {
        // An emergency that did not register as medical would skip MedGemma and lose the
        // emergency warning entirely - the worst possible combination.
        listOf(
            "ลูกชัก", "ลูกสำลัก", "ลูกหมดสติ",
            "baby is choking", "he had a seizure", "unconscious and limp"
        ).forEach { phrase ->
            assertTrue(
                "emergency phrase not detected as medical: $phrase",
                NongRakMedicalRouter.isMedicalQuestion(phrase)
            )
        }
    }

    @Test
    fun `warnings name the model and refuse to diagnose`() {
        val th = NongRakMedicalRouter.standardWarning(isThai = true)
        assertTrue(th.contains("MedGemma"))
        assertTrue(th.contains("ไม่ใช่การวินิจฉัย"))

        val en = NongRakMedicalRouter.standardWarning(isThai = false)
        assertTrue(en.contains("MedGemma"))
        assertTrue(en.lowercase().contains("not a diagnosis"))
    }

    @Test
    fun `emergency warning carries the thai emergency number`() {
        assertTrue(NongRakMedicalRouter.emergencyWarning(isThai = true).contains("1669"))
        assertTrue(NongRakMedicalRouter.emergencyWarning(isThai = false).contains("1669"))
    }

    // --- Community symptom threads -------------------------------------------------------------

    @Test
    fun `a fever under three months is urgent at any temperature`() {
        // The case the AI assist toggle was argued over: a reassuring answer here buys the delay.
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("ลูก 2 เดือน มีไข้ 38.0", ageMonths = 2))
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("2 month old has a fever", ageMonths = 2))
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("ตัวร้อนตั้งแต่เมื่อคืน", ageMonths = 0))
    }

    @Test
    fun `an unknown age with a fever is treated as urgent`() {
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("ลูกมีไข้", ageMonths = null))
    }

    @Test
    fun `an older child with a plain fever is not forced to emergency`() {
        // Otherwise every teething toddler gets a 1669 banner and parents stop reading it.
        assertFalse(NongRakMedicalRouter.isEmergencyForAge("ลูก 18 เดือน มีไข้ต่ำๆ", ageMonths = 18))
        assertFalse(NongRakMedicalRouter.isEmergencyForAge("toddler has a mild fever", ageMonths = 18))
    }

    @Test
    fun `age never downgrades a real emergency`() {
        // isEmergencyForAge may only ever add to isEmergency, never subtract from it.
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("ลูกชักเกร็ง", ageMonths = 36))
        assertTrue(NongRakMedicalRouter.isEmergencyForAge("my child is unresponsive", ageMonths = 60))
    }

    @Test
    fun `the newly added infant red flags are recognised`() {
        listOf("ซี่โครงบุ๋ม", "กระหม่อมบุ๋ม", "อาเจียนพุ่ง", "ถ่ายเป็นเลือด", "ผื่นจ้ำเลือด")
            .forEach { assertTrue(it, NongRakMedicalRouter.isEmergency("น้องมีอาการ $it")) }

        listOf("projectile vomiting", "blood in stool", "non-blanching rash", "chest indrawing")
            .forEach { assertTrue(it, NongRakMedicalRouter.isEmergency("baby has $it")) }
    }

    @Test
    fun `every added red flag also counts as a medical question`() {
        // The gap that bit before: emergency-only terms read as non-medical and skipped the router.
        listOf("กระหม่อมโป่ง", "ตัวอ่อนปวกเปียก", "floppy", "refusing to feed")
            .forEach { assertTrue(it, NongRakMedicalRouter.isMedicalQuestion("น้อง $it")) }
    }
}
