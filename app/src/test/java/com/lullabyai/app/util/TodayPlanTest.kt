package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.MedicationDose
import com.lullabyai.app.data.model.VaccineRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The home card speaks for the app about a child's medicine. These pin the two things that make it
 * trustworthy: it never shows a line no row supports, and overdue is decided by the clock rather
 * than by whatever time the suite happens to run at.
 */
class TodayPlanTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 6, 15, 30)
    private val today: LocalDate = now.toLocalDate()

    private val mina = Child(id = "c1", name = "มีนา")
    private val pond = Child(id = "c2", name = "ปอนด์")

    private fun dose(hour: Int, minute: Int = 0, status: String = "pending", childId: String = "c1") =
        MedicationDose(
            id = "d-$childId-$hour-$minute-$status",
            scheduleId = "s1",
            childId = childId,
            // Built exactly the way MedicationRepository writes a row: a UTC instant, never a
            // naive local string. A test that invents its own timestamp shape proves nothing about
            // the rows the app actually reads.
            scheduledAt = TimeUtils.toUtcIso(LocalDateTime.of(today, java.time.LocalTime.of(hour, minute))),
            status = status
        )

    @Test
    fun `a pending dose whose time has passed is overdue`() {
        val items = TodayPlan.forChild(mina, listOf(dose(14)), emptyList(), emptyList(), now = now)
        assertEquals(listOf(TodayKind.MEDICINE_OVERDUE), items.map { it.kind })
        assertEquals("14:00", items.first().time)
    }

    @Test
    fun `a pending dose still to come today is due, not overdue`() {
        val items = TodayPlan.forChild(mina, listOf(dose(18)), emptyList(), emptyList(), now = now)
        assertEquals(listOf(TodayKind.MEDICINE_DUE), items.map { it.kind })
    }

    @Test
    fun `a taken dose is shown as done rather than outstanding`() {
        val items = TodayPlan.forChild(
            mina, listOf(dose(8, status = "taken")), emptyList(), emptyList(), now = now
        )
        assertEquals(listOf(TodayKind.DONE_MEDICINE), items.map { it.kind })
        assertTrue(TodayPlan.isClear(items))
    }

    /** Skipping is a decision the parent already made; nagging about it is the bug. */
    @Test
    fun `a skipped dose produces no line at all`() {
        val items = TodayPlan.forChild(
            mina, listOf(dose(8, status = "skipped")), emptyList(), emptyList(), now = now
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `a dose scheduled for another day is not today's problem`() {
        val yesterday = MedicationDose(
            id = "d-old", scheduleId = "s1", childId = "c1",
            scheduledAt = TimeUtils.toUtcIso(LocalDateTime.of(today.minusDays(1), java.time.LocalTime.of(14, 0))),
            status = "pending"
        )
        assertTrue(
            TodayPlan.forChild(mina, listOf(yesterday), emptyList(), emptyList(), now = now).isEmpty()
        )
    }

    @Test
    fun `an illness being tracked asks for today's entry only when today has none`() {
        val record = HealthRecord(
            id = "h1", childId = "c1", title = "ไข้", status = "monitoring",
            recordedAt = today.minusDays(2).toString()
        )

        val missing = TodayPlan.forChild(mina, emptyList(), emptyList(), listOf(record), now = now)
        assertEquals(listOf(TodayKind.SYMPTOM_LOG), missing.map { it.kind })
        assertEquals(3, missing.first().dayNumber)

        val logged = TodayPlan.forChild(
            mina, emptyList(), emptyList(), listOf(record),
            symptomActivityByRecord = mapOf("h1" to today.toString()), now = now
        )
        assertTrue(logged.isEmpty())
    }

    @Test
    fun `a recovered illness is not asked about`() {
        val record = HealthRecord(
            id = "h1", childId = "c1", title = "ไข้", status = "recovered",
            recordedAt = today.minusDays(2).toString()
        )
        assertTrue(
            TodayPlan.forChild(mina, emptyList(), emptyList(), listOf(record), now = now).isEmpty()
        )
    }

    @Test
    fun `only the nearest upcoming vaccine is shown, and past or completed ones never are`() {
        val vaccines = listOf(
            VaccineRecord(id = "v1", childId = "c1", vaccineName = "MMR", scheduledDate = today.plusDays(30).toString()),
            VaccineRecord(id = "v2", childId = "c1", vaccineName = "DTP", scheduledDate = today.plusDays(6).toString()),
            VaccineRecord(id = "v3", childId = "c1", vaccineName = "HepB", scheduledDate = today.minusDays(3).toString()),
            VaccineRecord(
                id = "v4", childId = "c1", vaccineName = "BCG",
                scheduledDate = today.plusDays(1).toString(), status = "completed"
            )
        )
        val items = TodayPlan.forChild(mina, emptyList(), vaccines, emptyList(), now = now)
        assertEquals(listOf(TodayKind.VACCINE), items.map { it.kind })
        assertEquals("DTP", items.first().label)
    }

    @Test
    fun `nothing logged means nothing claimed`() {
        val items = TodayPlan.forChild(mina, emptyList(), emptyList(), emptyList(), now = now)
        assertTrue(items.isEmpty())
        assertTrue(TodayPlan.isClear(items))
    }

    @Test
    fun `an outstanding item means the day is not clear`() {
        val items = TodayPlan.forChild(mina, listOf(dose(14)), emptyList(), emptyList(), now = now)
        assertFalse(TodayPlan.isClear(items))
    }

    /** The card is ครอบครัวของเรา, so a second child's overdue dose cannot hide behind a switcher. */
    @Test
    fun `the family view surfaces every child, worst first`() {
        val doses = listOf(
            dose(8, status = "taken", childId = "c1"),
            dose(13, childId = "c2")
        )
        val items = TodayPlan.forFamily(listOf(mina, pond), doses, emptyList(), emptyList(), now = now)
        assertEquals(TodayKind.MEDICINE_OVERDUE, items.first().kind)
        assertEquals("ปอนด์", items.first().childName)
        assertEquals(TodayKind.DONE_MEDICINE, items.last().kind)
    }

    /**
     * An offset-less timestamp is UTC, which is what TimeUtils says and what Postgres does. Reading
     * it as local wall-clock instead would move a 07:00 dose to 14:00 in Bangkok - the same row
     * landing in a different hour depending on which file parsed it.
     */
    @Test
    fun `an offset-less timestamp is read as UTC, not as local time`() {
        val naiveUtc = MedicationDose(
            id = "z", scheduleId = "s1", childId = "c1",
            scheduledAt = "${today}T07:00:00", status = "taken"
        )
        val items = TodayPlan.forChild(mina, listOf(naiveUtc), emptyList(), emptyList(), now = now)
        val expected = LocalDateTime.ofInstant(
            java.time.Instant.parse("${today}T07:00:00Z"), java.time.ZoneId.systemDefault()
        )
        assertEquals("%02d:%02d".format(expected.hour, expected.minute), items.first().time)
    }

    @Test
    fun `one child's rows never leak into another child's card`() {
        val items = TodayPlan.forChild(
            mina, listOf(dose(14, childId = "c2")), emptyList(), emptyList(), now = now
        )
        assertTrue(items.isEmpty())
    }
}
