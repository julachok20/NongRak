package com.lullabyai.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The ‹ › arrows must move one unit of whatever view is showing. They previously stepped a month in
 * every view, which made Day and Week useless for reaching tomorrow or next week.
 */
class ScheduleStepTest {

    private val day = LocalDate.of(2026, 11, 14)   // a Saturday

    @Test
    fun `day view steps a single day`() {
        assertEquals(LocalDate.of(2026, 11, 15), ScheduleView.DAY.step(day, 1))
        assertEquals(LocalDate.of(2026, 11, 13), ScheduleView.DAY.step(day, -1))
    }

    @Test
    fun `week view steps seven days`() {
        assertEquals(LocalDate.of(2026, 11, 21), ScheduleView.WEEK.step(day, 1))
        assertEquals(LocalDate.of(2026, 11, 7), ScheduleView.WEEK.step(day, -1))
    }

    @Test
    fun `month view steps a month`() {
        assertEquals(LocalDate.of(2026, 12, 14), ScheduleView.MONTH.step(day, 1))
        assertEquals(LocalDate.of(2026, 10, 14), ScheduleView.MONTH.step(day, -1))
    }

    @Test
    fun `stepping a day crosses the month boundary`() {
        val monthEnd = LocalDate.of(2026, 11, 30)
        assertEquals(LocalDate.of(2026, 12, 1), ScheduleView.DAY.step(monthEnd, 1))
    }

    @Test
    fun `stepping a week crosses the year boundary`() {
        val nearNewYear = LocalDate.of(2026, 12, 28)
        assertEquals(LocalDate.of(2027, 1, 4), ScheduleView.WEEK.step(nearNewYear, 1))
    }

    @Test
    fun `stepping a month clamps to the shorter month rather than overflowing`() {
        // 31 Jan + 1 month is 28 Feb, not 3 March — java.time clamps, and the calendar relies on it.
        val jan31 = LocalDate.of(2027, 1, 31)
        assertEquals(LocalDate.of(2027, 2, 28), ScheduleView.MONTH.step(jan31, 1))
    }
}
