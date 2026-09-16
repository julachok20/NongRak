package com.lullabyai.app.data.infoask

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.util.HealthBookFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InfoAskCatalogTest {

    private val today = LocalDate.of(2026, 8, 31)

    private fun child(
        birthdate: String = "2025-01-01",
        deliveryType: String? = "natural",
        placeOfBirth: String? = "รพ.dummy",
        gestationalWeeks: Int? = 38,
        birthWeightG: Double? = 3200.0,
        birthLengthCm: Double? = 50.0,
        birthHeadCm: Double? = 34.0,
        bloodGroup: String? = "O",
        activityLevel: String = "moderate",
        noKnownAllergies: Boolean = true
    ) = Child(
        id = "c1",
        userId = "u1",
        name = "Fifa",
        birthdate = birthdate,
        deliveryType = deliveryType,
        placeOfBirth = placeOfBirth,
        gestationalWeeks = gestationalWeeks,
        birthWeightG = birthWeightG,
        birthLengthCm = birthLengthCm,
        birthHeadCm = birthHeadCm,
        bloodGroup = bloodGroup,
        activityLevel = activityLevel,
        noKnownAllergies = noKnownAllergies
    )

    private fun profile(
        name: String? = "แม่ทดสอบ",
        relationship: String? = "mother",
        phone: String? = "0812345678",
        address: String? = "Bangkok"
    ) = Profile(
        id = "u1",
        fullName = name,
        relationship = relationship,
        phone = phone,
        address = address
    )

    private fun snap(
        child: Child = child(),
        profile: Profile? = profile(),
        sleepLogs: List<SleepLog> = emptyList(),
        shownToday: Set<String> = emptySet(),
        answeredEver: Set<String> = emptySet()
    ) = InfoAskSnapshot(
        child = child,
        profile = profile,
        sleepLogs = sleepLogs,
        shownToday = shownToday,
        answeredEver = answeredEver,
        today = today
    )

    @Test
    fun `openAll returns every gap mixed across topics`() {
        val prompts = InfoAskCatalog.openAll(
            snap(child = child(deliveryType = null, placeOfBirth = null)),
            seed = 1L
        )
        val ids = prompts.map { it.fieldId }
        assertTrue(ids.contains("delivery_type"))
        assertTrue(ids.contains("place_of_birth"))
        assertTrue(ids.contains("sleep_last_night"))
        assertTrue(ids.contains("play_today"))
        assertTrue(ids.contains("feeding_type"))
        assertFalse("catch-all stays off while structured gaps exist", ids.contains("open_notes"))
        assertTrue(prompts.any { it.questionTh.contains("Fifa") })
        val firstFour = ids.take(4).toSet()
        assertTrue(
            "topics interleave — first four are not only Health Book birth fields",
            firstFour.any { it in setOf("sleep_last_night", "typical_bedtime", "nap_today", "play_today", "activity_level", "feeding_type") }
        )
    }

    @Test
    fun `different seeds shuffle to a different order`() {
        val s = snap(child = child(deliveryType = null, placeOfBirth = null))
        val a = InfoAskCatalog.openAll(s, seed = 1L).map { it.fieldId }
        val b = InfoAskCatalog.openAll(s, seed = 99L).map { it.fieldId }
        assertEquals(a.toSet(), b.toSet())
        assertTrue("day-to-day order should change", a != b)
    }

    @Test
    fun `user subject is included when guardian name is missing`() {
        val prompts = InfoAskCatalog.openAll(
            snap(child = child(), profile = profile(name = null), answeredEver = setOf("activity_level")),
            seed = 1L
        )
        val nameQ = prompts.find { it.fieldId == "guardian_name" }
        assertEquals(InfoAskSubject.USER, nameQ?.subject)
    }

    @Test
    fun `skips filled one-shots and shown-today fields`() {
        val prompts = InfoAskCatalog.openAll(
            snap(
                shownToday = setOf("sleep_last_night", "typical_bedtime", "nap_today", "play_today", "feeding_type", "recent_facility", "mood_after_sleep"),
                answeredEver = setOf("activity_level")
            ),
            seed = 1L
        )
        assertEquals(listOf("open_notes"), prompts.map { it.fieldId })
    }

    @Test
    fun `sleep last night is skipped when a main log exists today`() {
        val log = SleepLog(childId = "c1", sleepType = "main", startTime = "2026-08-31")
        assertFalse(InfoAskCatalog.isOpen("sleep_last_night", snap(sleepLogs = listOf(log))))
        assertTrue(InfoAskCatalog.isOpen("sleep_last_night", snap()))
    }

    @Test
    fun `activity_level is skipped once answered`() {
        assertTrue(InfoAskCatalog.isOpen("activity_level", snap()))
        assertFalse(InfoAskCatalog.isOpen("activity_level", snap(answeredEver = setOf("activity_level"))))
    }

    @Test
    fun `allergies prompt is open when neither allergies nor noKnownAllergies set`() {
        val c = child(noKnownAllergies = false).copy(allergies = null)
        assertTrue(InfoAskCatalog.isOpen("allergies", snap(child = c)))
        assertFalse(InfoAskCatalog.isOpen("allergies", snap(child = c.copy(noKnownAllergies = true))))
        assertFalse(InfoAskCatalog.isOpen("allergies", snap(child = c.copy(allergies = "eggs"))))
    }

    @Test
    fun `child row count sees gaps without any logs and ignores shown-today`() {
        val complete = InfoAskCatalog.openChildRowCount(
            child = child(noKnownAllergies = true),
            answeredEver = setOf("activity_level"),
            today = today
        )
        assertEquals("a fully filled row owes nothing", 0, complete)

        val gappy = child(bloodGroup = null, placeOfBirth = null, noKnownAllergies = false)
        assertEquals(
            "blood group, place of birth, allergies and the never-answered activity level",
            4,
            InfoAskCatalog.openChildRowCount(gappy, today = today)
        )
    }

    @Test
    fun `child row count never counts fields that need logs`() {
        val ids = InfoAskCatalog.CHILD_ROW_FIELDS
        assertFalse(ids.contains("sleep_last_night"))
        assertFalse(ids.contains("feeding_type"))
        assertFalse(ids.contains("guardian_name"))
    }

    @Test
    fun `banner names both child and parent when mixed`() {
        val mixed = listOf(
            InfoAskPrompt("sleep_last_night", InfoAskSubject.CHILD, InfoAskKind.CHIPS, "q", "q"),
            InfoAskPrompt("guardian_name", InfoAskSubject.USER, InfoAskKind.TEXT, "q", "q")
        )
        assertEquals("Fifa และคุณ", InfoAskCatalog.bannerSubjectName(mixed, "Fifa", true))
        assertEquals("Fifa and you", InfoAskCatalog.bannerSubjectName(mixed, "Fifa", false))
    }

    @Test
    fun `birth weight under 20 is kilograms`() {
        assertEquals(3200.0, InfoAskCatalog.parseBirthWeightG("3.2")!!, 0.01)
        assertEquals(3200.0, InfoAskCatalog.parseBirthWeightG("3200")!!, 0.01)
        assertNull(InfoAskCatalog.parseBirthWeightG("0"))
        assertNull(InfoAskCatalog.parseBirthWeightG("abc"))
    }
}

class HealthBookFieldsTest {

    @Test
    fun `mother relation fills mother line not father`() {
        val lines = HealthBookFields.guardianLines(
            Profile(fullName = "สมศรี", relationship = "mother", phone = "0811111111", address = "เชียงใหม่"),
            "mom@example.com"
        )
        assertEquals("สมศรี", lines.first { it.first.startsWith("ชื่อมารดา") }.second)
        assertEquals("", lines.first { it.first.startsWith("ชื่อบิดา") }.second)
        assertEquals("สมศรี", lines.first { it.first.startsWith("ผู้ปกครอง") }.second)
        assertEquals("มารดา", lines.first { it.first.startsWith("ความสัมพันธ์") }.second)
        assertEquals("เชียงใหม่", lines.first { it.first.startsWith("ที่อยู่") }.second)
        assertEquals("mom@example.com", lines.first { it.first.startsWith("อีเมล") }.second)
    }

    @Test
    fun `father relation fills father line`() {
        val lines = HealthBookFields.guardianLines(
            Profile(fullName = "สมชาย", relationship = "พ่อ"),
            null
        )
        assertEquals("สมชาย", lines.first { it.first.startsWith("ชื่อบิดา") }.second)
        assertEquals("", lines.first { it.first.startsWith("ชื่อมารดา") }.second)
    }

    @Test
    fun `delivery and birth weight print Thai book labels`() {
        assertEquals("ผ่าคลอด", HealthBookFields.delivery("cesarean"))
        assertEquals("38 สัปดาห์", HealthBookFields.gestational(38))
        assertTrue(HealthBookFields.birthWeight(3200.0).contains("3200"))
        assertEquals("", HealthBookFields.birthWeight(null))
    }
}
