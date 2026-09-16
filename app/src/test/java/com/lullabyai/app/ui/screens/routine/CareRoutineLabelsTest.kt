package com.lullabyai.app.ui.screens.routine

import com.lullabyai.app.data.routine.CareRoutinePlan
import org.junit.Assert.assertEquals
import org.junit.Test

/** The header chip, the timeline's footers and the in-progress button label. */
class CareRoutineLabelsTest {

    @Test
    fun `today's date reads as today in the header chip`() {
        val today = java.time.LocalDate.now().toString()
        val day = java.time.LocalDate.now()
        val expected = "วันนี้, ${day.dayOfMonth} " + listOf(
            "ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.",
            "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."
        )[day.monthValue - 1]

        assertEquals(expected, routineDateLabel(today, isThai = true))
    }

    @Test
    fun `another day drops the today prefix`() {
        assertEquals("24 พ.ค.", routineDateLabel("2020-05-24", isThai = true))
        assertEquals("24 May", routineDateLabel("2020-05-24", isThai = false))
    }

    @Test
    fun `an unreadable date leaves the chip off rather than showing junk`() {
        assertEquals("", routineDateLabel("", isThai = true))
        assertEquals("", routineDateLabel("not-a-date", isThai = true))
    }

    @Test
    fun `times carry the Thai hour marker`() {
        assertEquals("07:15 น.", timeLabel(7 * 60 + 15, isThai = true))
        assertEquals("07:15", timeLabel(7 * 60 + 15, isThai = false))
    }

    @Test
    fun `the day splits into the design's four periods`() {
        assertEquals("ช่วงเช้า", dayPeriodLabel(7 * 60, isThai = true))
        assertEquals("ช่วงบ่าย", dayPeriodLabel(13 * 60, isThai = true))
        assertEquals("ช่วงเย็น", dayPeriodLabel(16 * 60 + 30, isThai = true))
        assertEquals("ช่วงค่ำ", dayPeriodLabel(19 * 60 + 30, isThai = true))
    }

    @Test
    fun `a tick is credited to whoever recorded it, with the time`() {
        val log = CareRoutinePlan.Log(
            routineId = "r1",
            done = true,
            actorName = "พี่เลี้ยงใจ",
            recordedMinute = 8 * 60 + 45
        )

        assertEquals("บันทึกโดย พี่เลี้ยงใจ (08:45 น.)", recordedByLabel(log, isThai = true))
    }

    @Test
    fun `an actor who has left the circle is not shown as a blank name`() {
        val log = CareRoutinePlan.Log(routineId = "r1", done = true, recordedMinute = 8 * 60 + 45)

        assertEquals("บันทึกเมื่อ (08:45 น.)", recordedByLabel(log, isThai = true))
        assertEquals("บันทึกแล้ว", recordedByLabel(CareRoutinePlan.Log("r1", true), isThai = true))
    }

    @Test
    fun `an upcoming slot counts down, and a passed one says nothing`() {
        assertEquals("อีก 3 ชม. 15 นาที", untilLabel(16 * 60 + 30, 13 * 60 + 15, isThai = true))
        assertEquals("อีก 20 นาที", untilLabel(13 * 60 + 20, 13 * 60, isThai = true))
        assertEquals("อีก 2 ชม.", untilLabel(15 * 60, 13 * 60, isThai = true))
        assertEquals("", untilLabel(12 * 60, 13 * 60, isThai = true))
    }

    @Test
    fun `the prep line is prefixed by what the item needs it for`() {
        assertEquals(
            "เตรียม: สบู่สูตรอ่อนโยน & ผ้าเช็ดตัวอุ่น",
            prepLabel("hygiene", "สบู่สูตรอ่อนโยน & ผ้าเช็ดตัวอุ่น", isThai = true)
        )
        assertEquals(
            "เป้าหมาย: หลับต่อเนื่อง 6-8 ชม.",
            prepLabel("night", "หลับต่อเนื่อง 6-8 ชม.", isThai = true)
        )
        assertEquals("Goal: 1.5 - 2 hrs", prepLabel("sleep", "1.5 - 2 hrs", isThai = false))
    }

    @Test
    fun `an item with nothing to prepare adds no prefix of its own`() {
        assertEquals("", prepLabel("hygiene", "", isThai = true))
        assertEquals("", prepLabel("sleep", "   ", isThai = true))
    }

    @Test
    fun `finishing a nap records waking, not a generic tick`() {
        assertEquals("บันทึกเวลาตื่นนอน", currentActionLabel("sleep", isThai = true))
        assertEquals("บันทึกเวลาตื่นนอน", currentActionLabel("night", isThai = true))
        assertEquals("บันทึกว่าทำแล้ว", currentActionLabel("feeding", isThai = true))
    }
}
