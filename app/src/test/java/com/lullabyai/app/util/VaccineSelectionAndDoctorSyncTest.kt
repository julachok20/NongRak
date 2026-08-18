package com.lullabyai.app.util

import com.lullabyai.app.data.model.VaccineRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaccineSelectionAndDoctorSyncTest {

    @Test
    fun testVaccineSimilarityExactAndAliasMatching() {
        val childVaccines = listOf(
            VaccineRecord(id = "rec-1", childId = "c1", vaccineName = "BCG", status = "completed", ageGroup = "แรกเกิด"),
            VaccineRecord(id = "rec-2", childId = "c1", vaccineName = "HBV เข็มที่ 1", status = "upcoming", ageGroup = "แรกเกิด"),
            VaccineRecord(id = "rec-3", childId = "c1", vaccineName = "DTP-HB-Hib เข็มที่ 1", status = "upcoming", ageGroup = "2 เดือน"),
            VaccineRecord(id = "rec-4", childId = "c1", vaccineName = "MMR เข็มที่ 1", status = "upcoming", ageGroup = "9 เดือน"),
            VaccineRecord(id = "rec-5", childId = "c1", vaccineName = "ไข้หวัดใหญ่", status = "upcoming", ageGroup = "6 เดือน"),
            VaccineRecord(id = "rec-6", childId = "c1", vaccineName = "JE เข็มที่ 1", status = "upcoming", ageGroup = "1 ปี")
        )

        // 1. Exact match
        val matchBcg = VaccineSimilarityMatcher.findSimilarVaccine("BCG", childVaccines)
        assertNotNull(matchBcg)
        assertEquals("rec-1", matchBcg?.matchedRecord?.id)
        assertEquals(1.0f, matchBcg!!.similarityScore, 0.01f)

        // 2. Thai alias match for BCG ("บีซีจี" or "วัคซีนบีซีจี")
        val matchThaiBcg = VaccineSimilarityMatcher.findSimilarVaccine("บีซีจี", childVaccines)
        assertNotNull(matchThaiBcg)
        assertEquals("rec-1", matchThaiBcg?.matchedRecord?.id)

        // 3. Thai alias match for MMR ("หัด-คางทูม-หัดเยอรมัน" or "หัดเยอรมัน")
        val matchMmr = VaccineSimilarityMatcher.findSimilarVaccine("หัดเยอรมัน", childVaccines)
        assertNotNull(matchMmr)
        assertEquals("rec-4", matchMmr?.matchedRecord?.id)

        // 4. English alias match for Flu / Influenza
        val matchFlu = VaccineSimilarityMatcher.findSimilarVaccine("Flu vaccine", childVaccines)
        assertNotNull(matchFlu)
        assertEquals("rec-5", matchFlu?.matchedRecord?.id)

        // 5. Thai alias match for Hepatitis B ("ตับอักเสบบี")
        val matchHepB = VaccineSimilarityMatcher.findSimilarVaccine("ตับอักเสบบี", childVaccines)
        assertNotNull(matchHepB)
        assertEquals("rec-2", matchHepB?.matchedRecord?.id)

        // 6. Typo / similarity match ("Rotavrus" when Rota not in list returns null, but similar string)
        val noMatchDengue = VaccineSimilarityMatcher.findSimilarVaccine("ไข้เลือดออก", childVaccines)
        assertNull(noMatchDengue) // Dengue is not in child's book list -> returns null -> treats as new vaccine
    }

    @Test
    fun testAppointmentBookingMetaVaccineEncodingAndParsing() {
        // Test encoding with existing vaccine from book
        val encodedFromBook = AppointmentBookingMeta.encodeReason(
            type = AppointmentBookingType.VACCINE,
            detail = "นัดฉีดตามกำหนดช่วง 2 เดือน",
            vaccineName = "DTP-HB-Hib เข็มที่ 1",
            vaccineRecordId = "rec-3"
        )
        assertTrue(encodedFromBook.contains("[type:vaccine]"))
        assertTrue(encodedFromBook.contains("[vac_id:rec-3]"))
        assertTrue(encodedFromBook.contains("[vac_name:DTP-HB-Hib เข็มที่ 1]"))

        // Test parsing
        val parsedFromBook = AppointmentBookingMeta.parseVaccine(encodedFromBook, null)
        assertEquals("DTP-HB-Hib เข็มที่ 1", parsedFromBook.vaccineName)
        assertEquals("rec-3", parsedFromBook.vaccineRecordId)
        assertTrue(parsedFromBook.isFromBook)

        // Test display reason stripping tags cleanly
        val display = AppointmentBookingMeta.displayReason(encodedFromBook, isThai = true)
        assertEquals("วัคซีน: DTP-HB-Hib เข็มที่ 1: นัดฉีดตามกำหนดช่วง 2 เดือน", display)

        // Test encoding with new custom vaccine (no record ID)
        val encodedNewVac = AppointmentBookingMeta.encodeReason(
            type = AppointmentBookingType.VACCINE,
            detail = "ต้องการฉีดป้องกันเสริม",
            vaccineName = "วัคซีนอีสุกอีใส (Varicella)",
            vaccineRecordId = null
        )
        val parsedNewVac = AppointmentBookingMeta.parseVaccine(encodedNewVac, null)
        assertEquals("วัคซีนอีสุกอีใส (Varicella)", parsedNewVac.vaccineName)
        assertNull(parsedNewVac.vaccineRecordId)
        assertEquals(false, parsedNewVac.isFromBook)
    }
}
