package com.lullabyai.app.data

import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.model.WasteRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed's whole job is ordering rows that were never written to be ordered together, so that
 * is what is pinned here: six tables, four timestamp spellings, one list.
 */
class ChildRecordFeedTest {

    @Test
    fun `a date-only stamp becomes midnight so it sorts against datetimes on the same day`() {
        assertEquals("2026-09-20T00:00:00", ChildRecordFeed.normalise("2026-09-20"))
        assertEquals("2026-09-20T14:30:00", ChildRecordFeed.normalise("2026-09-20T14:30:00"))
        // Postgres hands back a space instead of the T often enough to matter.
        assertEquals("2026-09-20T14:30:00", ChildRecordFeed.normalise("2026-09-20 14:30:00"))
        // A fractional part would otherwise make strings of different lengths compare wrongly.
        assertEquals("2026-09-20T14:30:00", ChildRecordFeed.normalise("2026-09-20T14:30:00.123456Z"))
        assertNull(ChildRecordFeed.normalise(null))
        assertNull(ChildRecordFeed.normalise("   "))
    }

    @Test
    fun `orders every source together, newest first`() {
        val feed = ChildRecordFeed.build(
            growth = listOf(GrowthRecord(weightKg = 8.2, recordedAt = "2026-09-18")),
            health = listOf(HealthRecord(title = "ไข้", temperature = 38.1, recordedAt = "2026-09-20T09:00:00")),
            sleep = listOf(SleepLog(sleepType = "nap", startTime = "2026-09-20T13:00:00")),
            waste = listOf(WasteRecord(wasteType = "stool", recordedAt = "2026-09-19T08:00:00")),
            visits = listOf(DoctorVisit(visitDatetime = "2026-09-17T10:00:00", doctorName = "หมอเอ")),
            vaccines = listOf(VaccineRecord(vaccineName = "BCG", dateGiven = "2026-09-16"))
        )
        assertEquals(
            listOf(
                ChildRecordFeed.Kind.SLEEP,    // 09-20 13:00
                ChildRecordFeed.Kind.HEALTH,   // 09-20 09:00
                ChildRecordFeed.Kind.WASTE,    // 09-19
                ChildRecordFeed.Kind.GROWTH,   // 09-18
                ChildRecordFeed.Kind.VISIT,    // 09-17
                ChildRecordFeed.Kind.VACCINE   // 09-16
            ),
            feed.map { it.kind }
        )
    }

    @Test
    fun `a same-day date-only row sorts below that day's timed rows, not above them`() {
        val feed = ChildRecordFeed.build(
            growth = listOf(GrowthRecord(weightKg = 8.0, recordedAt = "2026-09-20")),
            sleep = listOf(SleepLog(startTime = "2026-09-20T07:00:00"))
        )
        assertEquals(ChildRecordFeed.Kind.SLEEP, feed.first().kind)
        assertEquals(ChildRecordFeed.Kind.GROWTH, feed.last().kind)
    }

    @Test
    fun `rows with no usable timestamp are dropped rather than sorted to the top`() {
        val feed = ChildRecordFeed.build(
            growth = listOf(GrowthRecord(weightKg = 8.0, recordedAt = null)),
            health = listOf(HealthRecord(title = "ok", recordedAt = "2026-09-20T09:00:00"))
        )
        assertEquals(1, feed.size)
        assertEquals(ChildRecordFeed.Kind.HEALTH, feed.single().kind)
    }

    @Test
    fun `a scheduled vaccine is a plan, so only given doses appear`() {
        val feed = ChildRecordFeed.build(
            vaccines = listOf(
                VaccineRecord(vaccineName = "HepB", dateGiven = null, scheduledDate = "2026-12-01"),
                VaccineRecord(vaccineName = "BCG", dateGiven = "2026-09-16")
            )
        )
        assertEquals(1, feed.size)
        assertEquals("BCG", feed.single().detail)
    }

    @Test
    fun `recent caps the inline list and leaves the rest for see-all`() {
        val many = (1..9).map { GrowthRecord(weightKg = it.toDouble(), recordedAt = "2026-09-%02d".format(it)) }
        val feed = ChildRecordFeed.build(growth = many)
        assertEquals(9, feed.size)
        val shown = ChildRecordFeed.recent(feed)
        assertEquals(5, shown.size)
        assertTrue("newest first", shown.first().at > shown.last().at)
        assertEquals("2026-09-09", shown.first().date)
    }
}
