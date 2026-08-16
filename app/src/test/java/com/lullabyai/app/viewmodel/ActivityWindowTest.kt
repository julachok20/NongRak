package com.lullabyai.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A schedule points forward: something already done belongs in the care log, not the calendar.
 * The interesting part is the boundary, so `now` is injected rather than read from the clock.
 */
class ActivityWindowTest {

    private val now: OffsetDateTime =
        OffsetDateTime.of(2026, 11, 14, 10, 0, 0, 0, ZoneOffset.ofHours(7))

    @Test
    fun `a future window is accepted`() {
        assertNull(validateActivityWindow(now.plusHours(1), now.plusHours(3), now))
    }

    @Test
    fun `a start in the past is refused`() {
        assertEquals(
            "past_start",
            validateActivityWindow(now.minusMinutes(1), now.plusHours(1), now)
        )
    }

    @Test
    fun `yesterday is refused even with a sensible duration`() {
        assertEquals(
            "past_start",
            validateActivityWindow(now.minusDays(1), now.minusDays(1).plusHours(2), now)
        )
    }

    @Test
    fun `an end before the start is refused`() {
        assertEquals(
            "end_before_start",
            validateActivityWindow(now.plusHours(3), now.plusHours(2), now)
        )
    }

    @Test
    fun `a zero-length window is refused`() {
        val at = now.plusHours(2)
        assertEquals("end_before_start", validateActivityWindow(at, at, now))
    }

    @Test
    fun `no end time is fine — many entries have no duration`() {
        assertNull(validateActivityWindow(now.plusHours(1), null, now))
    }

    @Test
    fun `the past check compares instants, not wall clocks`() {
        // 11:30 in Tokyo (+09:00) is 09:30 in Bangkok (+07:00) — earlier as an instant than `now`,
        // so it must be refused even though its local hour reads later.
        val earlierInstantLaterWallClock =
            OffsetDateTime.of(2026, 11, 14, 11, 30, 0, 0, ZoneOffset.ofHours(9))
        assertEquals("past_start", validateActivityWindow(earlierInstantLaterWallClock, null, now))
    }
}
