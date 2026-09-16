package com.lullabyai.app.data.routine

import com.lullabyai.app.data.routine.CareRoutinePlan.Entry
import com.lullabyai.app.data.routine.CareRoutinePlan.Log
import com.lullabyai.app.data.routine.CareRoutinePlan.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The routine screen tells a caregiver what to do now. Getting "now" wrong is the whole risk, so
 * the boundaries between one item and the next are what these tests pin down.
 */
class CareRoutinePlanTest {

    private fun at(h: Int, m: Int = 0) = h * 60 + m

    private val day = listOf(
        Entry("wake", at(7, 15), "ตื่นนอน & วัดอุณหภูมิ", "health"),
        Entry("bottle", at(8, 30), "นมแม่ขวด & วิตามิน", "feeding"),
        Entry("tummy", at(10, 0), "Tummy time", "activity"),
        Entry("solid", at(11, 45), "อาหารเสริม", "solid"),
        Entry("nap", at(13, 0), "นอนกลางวัน Nap 2", "sleep"),
        Entry("bath", at(16, 30), "อาบน้ำ", "hygiene"),
        Entry("night", at(19, 30), "นมมื้อดึก & เข้านอน", "night")
    )

    @Test
    fun `unstarted day is all upcoming with no current item`() {
        val d = CareRoutinePlan.build(day, emptyList(), at(6))
        assertEquals(List(7) { Status.UPCOMING }, d.slots.map { it.status })
        assertNull(d.current)
        assertEquals("wake", d.next?.entry?.id)
        assertEquals(0, d.percent)
    }

    @Test
    fun `the item whose slot contains now is current`() {
        val d = CareRoutinePlan.build(day, emptyList(), at(14))
        assertEquals("nap", d.current?.entry?.id)
        assertEquals("bath", d.next?.entry?.id)
    }

    @Test
    fun `an item becomes current exactly at its own time`() {
        val d = CareRoutinePlan.build(day, emptyList(), at(13, 0))
        assertEquals("nap", d.current?.entry?.id)
    }

    @Test
    fun `a passed slot with nothing recorded is missed`() {
        val d = CareRoutinePlan.build(day, emptyList(), at(14))
        assertEquals(Status.MISSED, d.slots.first { it.entry.id == "wake" }.status)
        assertEquals(Status.MISSED, d.slots.first { it.entry.id == "solid" }.status)
    }

    @Test
    fun `the last item of the day stays current until midnight`() {
        val d = CareRoutinePlan.build(day, emptyList(), at(23, 45))
        assertEquals("night", d.current?.entry?.id)
        assertNull(d.next)
    }

    @Test
    fun `ticked items are done whatever the time`() {
        val logs = listOf(Log("wake", done = true), Log("bottle", done = true))
        val d = CareRoutinePlan.build(day, logs, at(9))
        assertEquals(Status.DONE, d.slots.first { it.entry.id == "wake" }.status)
        assertEquals(Status.DONE, d.slots.first { it.entry.id == "bottle" }.status)
        assertEquals(2, d.done)
        assertEquals(28, d.percent) // 2 of 7
    }

    @Test
    fun `a skipped item is neither done nor missed`() {
        val d = CareRoutinePlan.build(day, listOf(Log("solid", done = false, note = "หลับยาว")), at(14))
        val slot = d.slots.first { it.entry.id == "solid" }
        assertEquals(Status.SKIPPED, slot.status)
        assertEquals("หลับยาว", slot.log?.note)
        assertEquals(0, d.done)
        assertEquals(1, d.skipped)
    }

    @Test
    fun `a done item never counts as the current one`() {
        val d = CareRoutinePlan.build(day, listOf(Log("nap", done = true)), at(14))
        assertNull(d.current)
        assertEquals("bath", d.next?.entry?.id)
    }

    @Test
    fun `items are ordered by time whatever order they arrive in`() {
        val d = CareRoutinePlan.build(day.reversed(), emptyList(), at(6))
        assertEquals(listOf("wake", "bottle", "tummy", "solid", "nap", "bath", "night"), d.slots.map { it.entry.id })
    }

    @Test
    fun `a child with no routine yet reports an empty day`() {
        val d = CareRoutinePlan.build(emptyList(), emptyList(), at(12))
        assertEquals(0, d.total)
        assertEquals(0, d.percent)
        assertNull(d.current)
        assertNull(d.next)
    }

    @Test
    fun `a fully ticked day is a hundred percent`() {
        val logs = day.map { Log(it.id, done = true) }
        val d = CareRoutinePlan.build(day, logs, at(20))
        assertEquals(100, d.percent)
        assertNull(d.current)
    }

    @Test
    fun `stored times parse with or without seconds`() {
        assertEquals(at(7, 15), CareRoutinePlan.parseMinuteOfDay("07:15"))
        assertEquals(at(7, 15), CareRoutinePlan.parseMinuteOfDay("07:15:00"))
        assertNull(CareRoutinePlan.parseMinuteOfDay("not a time"))
        assertNull(CareRoutinePlan.parseMinuteOfDay("25:00"))
        assertNull(CareRoutinePlan.parseMinuteOfDay(null))
    }

    @Test
    fun `minutes format back to a clock time`() {
        assertEquals("07:15", CareRoutinePlan.formatMinuteOfDay(at(7, 15)))
        assertEquals("00:00", CareRoutinePlan.formatMinuteOfDay(0))
        assertEquals("23:59", CareRoutinePlan.formatMinuteOfDay(at(23, 59)))
    }
}
