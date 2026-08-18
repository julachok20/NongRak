package com.lullabyai.app.util

import com.lullabyai.app.data.model.SleepLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SleepCalculationTest {

    @Test
    fun `cross-midnight sleep attributes to wake up date`() {
        val recordDate = LocalDate.parse("2026-08-18")
        val startHour = 20
        val startMin = 0
        val endHour = 6
        val endMin = 30

        val startMinutesTotal = startHour * 60 + startMin
        val endMinutesTotal = endHour * 60 + endMin
        val isCrossMidnight = endMinutesTotal <= startMinutesTotal

        val actualStartDate = if (isCrossMidnight) recordDate.minusDays(1) else recordDate
        val actualEndDate = recordDate

        assertEquals(LocalDate.parse("2026-08-17"), actualStartDate)
        assertEquals(LocalDate.parse("2026-08-18"), actualEndDate)

        val startIso = "${actualStartDate}T%02d:%02d:00".format(startHour, startMin)
        val endIso = "${actualEndDate}T%02d:%02d:00".format(endHour, endMin)

        assertEquals("2026-08-17T20:00:00", startIso)
        assertEquals("2026-08-18T06:30:00", endIso)

        val diff = endMinutesTotal - startMinutesTotal
        val durationMinutes = if (diff <= 0) diff + 24 * 60 else diff
        assertEquals(10 * 60 + 30, durationMinutes) // 10h 30m
    }

    @Test
    fun `same day nap attributes to same date`() {
        val recordDate = LocalDate.parse("2026-08-18")
        val startHour = 13
        val startMin = 0
        val endHour = 15
        val endMin = 0

        val startMinutesTotal = startHour * 60 + startMin
        val endMinutesTotal = endHour * 60 + endMin
        val isCrossMidnight = endMinutesTotal <= startMinutesTotal

        val actualStartDate = if (isCrossMidnight) recordDate.minusDays(1) else recordDate
        val actualEndDate = recordDate

        assertEquals(LocalDate.parse("2026-08-18"), actualStartDate)
        assertEquals(LocalDate.parse("2026-08-18"), actualEndDate)

        val diff = endMinutesTotal - startMinutesTotal
        val durationMinutes = if (diff <= 0) diff + 24 * 60 else diff
        assertEquals(120, durationMinutes) // 2h
    }

    @Test
    fun `effective date helper extracts end time date for completed sleep`() {
        fun getEffectiveSleepDate(log: SleepLog): LocalDate? {
            val endLocalDate = log.endTime?.let { TimeUtils.parseInstant(it)?.atZone(ZoneId.of("UTC"))?.toLocalDate() }
            val startLocalDate = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.of("UTC"))?.toLocalDate()
            return endLocalDate ?: startLocalDate
        }

        val overnightLog = SleepLog(
            id = "test-1",
            childId = "child-1",
            sleepType = "main",
            startTime = "2026-08-17T13:00:00Z",
            endTime = "2026-08-18T23:30:00Z"
        )

        assertEquals(LocalDate.parse("2026-08-18"), getEffectiveSleepDate(overnightLog))
    }
}
