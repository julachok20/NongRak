package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pantry's safety rules. Most cases here are the same question: what does the screen say when
 * nobody has told it anything? The answer must never be the reassuring one.
 */
class PantrySafetyTest {

    private val today: LocalDate = LocalDate.parse("2026-09-25")

    @Test fun `days left counts from today, and an unset date stays unknown`() {
        assertEquals(3L, PantrySafety.daysLeft("2026-09-28", today))
        assertEquals(0L, PantrySafety.daysLeft("2026-09-25", today))
        assertEquals(-2L, PantrySafety.daysLeft("2026-09-23", today))
        assertNull(PantrySafety.daysLeft(null, today))
        assertNull(PantrySafety.daysLeft("not a date", today))
    }

    @Test fun `freshness buckets match the design's three-day window`() {
        assertEquals(PantrySafety.Freshness.EXPIRED, PantrySafety.freshness("2026-09-24", today))
        assertEquals(PantrySafety.Freshness.URGENT, PantrySafety.freshness("2026-09-25", today))
        assertEquals(PantrySafety.Freshness.URGENT, PantrySafety.freshness("2026-09-26", today))
        assertEquals(PantrySafety.Freshness.SOON, PantrySafety.freshness("2026-09-28", today))
        assertEquals(PantrySafety.Freshness.FINE, PantrySafety.freshness("2026-10-05", today))
        assertEquals(PantrySafety.Freshness.UNKNOWN, PantrySafety.freshness(null, today))
    }

    @Test fun `an ingredient with no expiry date is never called urgent, and never called fresh`() {
        assertFalse(PantrySafety.needsCookingSoon(null, today))
        assertEquals(PantrySafety.Freshness.UNKNOWN, PantrySafety.freshness(null, today))
        assertTrue(PantrySafety.needsCookingSoon("2026-09-22", today))   // already gone
        assertTrue(PantrySafety.needsCookingSoon("2026-09-28", today))
        assertFalse(PantrySafety.needsCookingSoon("2026-10-01", today))
    }

    @Test fun `a food nobody tested on this child is untested, not safe`() {
        assertEquals(PantrySafety.TrialState.UNTESTED, PantrySafety.trialState(null, null, today))
        assertEquals(PantrySafety.TrialState.UNTESTED, PantrySafety.trialState("", null, today))
    }

    @Test fun `the watch runs for four days, then asks the parent to decide`() {
        assertEquals(PantrySafety.TrialState.WATCHING, PantrySafety.trialState("testing", "2026-09-24", today))
        assertEquals(PantrySafety.TrialState.DUE_TO_DECIDE, PantrySafety.trialState("testing", "2026-09-22", today))
        assertEquals(PantrySafety.TrialState.SAFE, PantrySafety.trialState("safe", "2026-09-01", today))
        assertEquals(PantrySafety.TrialState.ALLERGIC, PantrySafety.trialState("allergic", "2026-09-01", today))
    }

    @Test fun `the day counter starts at one and the label stops at the last watch day`() {
        assertEquals(1, PantrySafety.watchDay("2026-09-25", today))
        assertEquals(2, PantrySafety.watchDay("2026-09-24", today))
        assertEquals(1, PantrySafety.watchDay("2026-09-30", today))      // a future start is still day 1
        assertEquals("ทดสอบวันที่ 2/4", PantrySafety.watchLabel("testing", "2026-09-24", isThai = true, today = today))
        assertEquals("Day 4 of 4", PantrySafety.watchLabel("testing", "2026-09-01", isThai = false, today = today))
        assertNull(PantrySafety.watchLabel("safe", "2026-09-24", isThai = true, today = today))
    }
}
