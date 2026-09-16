package com.lullabyai.app.data.handoff

import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.routine.CareRoutineDuty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ParentHandoffBriefTest {

    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun `compact age is months and leftover weeks`() {
        assertEquals("7ด 1ส", ParentHandoffBrief.compactAge("2026-02-07", true, today))
        assertEquals("7m 1w", ParentHandoffBrief.compactAge("2026-02-07", false, today))
    }

    @Test
    fun `health chip stays well unless fever is recorded`() {
        assertEquals("สุขภาพดี ร่าเริง", ParentHandoffBrief.healthChip(36.6, "happy", true))
        assertEquals("มีไข้", ParentHandoffBrief.healthChip(38.2, "happy", true))
    }

    @Test
    fun `allergy line joins table rows and never invents none`() {
        val line = ParentHandoffBrief.allergyLine(
            childAllergies = "ไข่ขาว",
            noKnown = false,
            rows = listOf(Allergy(name = "อาหารทะเล", severity = "severe")),
            isThai = true
        )
        assertTrue(line!!.contains("ไข่ขาว"))
        assertTrue(line.contains("อาหารทะเล"))
        assertNull(ParentHandoffBrief.allergyLine(null, true, emptyList(), true))
    }

    @Test
    fun `fever item names the recorded temperature and does not invent a drug`() {
        val items = ParentHandoffBrief.medicalItems(
            childName = "Giga",
            feverC = 37.8,
            records = emptyList(),
            chronic = emptyList(),
            isThai = true
        )
        assertEquals(1, items.size)
        assertTrue(items[0].title.contains("37.8"))
        assertFalse(items[0].body.contains("Tempora", ignoreCase = true))
        assertFalse(items[0].body.contains("มล."))
    }

    @Test
    fun `diarrhea item only appears when a current record names it`() {
        val silent = ParentHandoffBrief.medicalItems("Giga", null, emptyList(), emptyList(), true)
        assertTrue(silent.isEmpty())
        val named = ParentHandoffBrief.medicalItems(
            "Giga", null,
            listOf(HealthRecord(title = "ท้องเสีย", description = "ถ่ายเหลว")),
            emptyList(), true
        )
        assertEquals(ParentHandoffBrief.MedicalItem.Kind.DIARRHEA, named.single().kind)
    }

    @Test
    fun `chronic condition uses the parent's prevention text`() {
        val items = ParentHandoffBrief.medicalItems(
            "Giga", null, emptyList(),
            listOf(ChronicCondition(name = "หอบหืด", prevention = "พกยาพ่น")),
            true
        )
        assertEquals("หอบหืด", items.single().title)
        assertEquals("พกยาพ่น", items.single().body)
    }

    @Test
    fun `on-duty caregiver is first and marked กำลังดูแล`() {
        val nanny = CareRoutineDuty.Person("n", "พี่เลี้ยงใจ", "nanny")
        val gran = CareRoutineDuty.Person("g", "คุณยายสมศรี", "grandparent")
        val cards = ParentHandoffBrief.caregiverCards(
            people = listOf(gran, nanny, CareRoutineDuty.Person("parent", "แม่", "owner")),
            caregiverIds = setOf("n", "g"),
            excludeUserId = "parent",
            onDuty = CareRoutineDuty.OnDuty(
                person = nanny,
                shiftStart = 8 * 60,
                shiftEnd = 18 * 60,
                isOnDutyNow = true
            ),
            isThai = true,
            roleTitle = { role, th -> if (role == "nanny") "พี่เลี้ยงใจ (Nanny)" else if (th) "ยาย" else "Grandparent" }
        )
        assertEquals("n", cards[0].userId)
        assertTrue(cards[0].onDuty)
        assertEquals("กำลังดูแล", cards[0].status)
        assertEquals("กะวันนี้ 08:00 - 18:00", cards[0].shift)
        assertEquals("พร้อมช่วยเสริม", cards[1].status)
        assertEquals("พี่ใจ", cards[0].avatarLabel)
        assertEquals(2, cards.size)
        assertTrue(cards.none { it.userId == "parent" })
    }

    @Test
    fun `wake time is the end of today's finished main sleep`() {
        val logs = listOf(
            SleepLog(sleepType = "main", startTime = "2026-09-13T21:00:00+07:00", endTime = "2026-09-14T07:15:00+07:00"),
            SleepLog(sleepType = "nap", startTime = "2026-09-14T13:00:00+07:00", endTime = "2026-09-14T14:00:00+07:00")
        )
        val iso = ParentHandoffBrief.wakeIso(logs, today)
        assertEquals("2026-09-14T07:15:00+07:00", iso)
    }

    @Test
    fun `only ticked caretakers appear on the parent picker`() {
        val nanny = CareRoutineDuty.Person("n", "พี่เลี้ยงใจ", "nanny")
        val relative = CareRoutineDuty.Person("r", "น้าสาว", "relative")
        val cards = ParentHandoffBrief.caregiverCards(
            people = listOf(nanny, relative, CareRoutineDuty.Person("parent", "แม่", "owner")),
            caregiverIds = setOf("n"),
            excludeUserId = "parent",
            onDuty = null,
            isThai = true,
            roleTitle = { role, _ -> role }
        )
        assertEquals(listOf("n"), cards.map { it.userId })
    }

    @Test
    fun `caregiver caution names allergens and never invents a drug`() {
        val line = ParentHandoffBrief.caregiverCaution(
            childAllergies = "ไข่ขาว",
            noKnown = false,
            rows = listOf(Allergy(name = "อาหารทะเล", severity = "severe")),
            isThai = true
        )
        assertTrue(line.contains("ไข่ขาว"))
        assertTrue(line.contains("อาหารทะเล"))
        assertTrue(line.contains("37.8"))
        assertFalse(line.contains("Tempora", ignoreCase = true))
        assertFalse(line.contains("มล."))
    }

    @Test
    fun `parent call label includes the relationship and the number`() {
        val pair = ParentHandoffBrief.parentCallLabel("แอน", "mother", "081-234", true)!!
        assertEquals("คุณแม่ (แอน)", pair.first)
        assertTrue(pair.second.contains("081-234"))
        assertNull(ParentHandoffBrief.parentCallLabel(null, "mother", null, true))
    }
}
