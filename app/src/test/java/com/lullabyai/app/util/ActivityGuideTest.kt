package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The activity goal and the eat-and-move balance. The rule that matters most: a shortfall is never
 * answered by less food, so no combination of inputs may produce an "eat less" verdict.
 */
class ActivityGuideTest {

    @Test fun `the goal follows the WHO split at one year`() {
        assertEquals(30, ActivityGuide.dailyMinutesGoal(3))
        assertEquals(30, ActivityGuide.dailyMinutesGoal(11))
        assertEquals(180, ActivityGuide.dailyMinutesGoal(12))
        assertEquals(180, ActivityGuide.dailyMinutesGoal(40))
    }

    @Test fun `progress moves from nothing to met`() {
        assertEquals(ActivityGuide.Progress.NONE_YET, ActivityGuide.progress(0, 180))
        assertEquals(ActivityGuide.Progress.STARTED, ActivityGuide.progress(40, 180))
        assertEquals(ActivityGuide.Progress.ALMOST, ActivityGuide.progress(120, 180))
        assertEquals(ActivityGuide.Progress.MET, ActivityGuide.progress(180, 180))
        assertEquals(ActivityGuide.Progress.MET, ActivityGuide.progress(200, 180))
    }

    @Test fun `balance reads both sides and never asks for less food`() {
        assertEquals(ActivityGuide.Balance.BOTH_GOOD, ActivityGuide.balance(950, 1000, 170, 180))
        assertEquals(ActivityGuide.Balance.MOVE_MORE, ActivityGuide.balance(950, 1000, 30, 180))
        assertEquals(ActivityGuide.Balance.EAT_MORE, ActivityGuide.balance(400, 1000, 170, 180))
        assertEquals(ActivityGuide.Balance.EARLY, ActivityGuide.balance(300, 1000, 20, 180))
        // Eating well above target is still BOTH_GOOD, never a verdict to cut back.
        assertEquals(ActivityGuide.Balance.BOTH_GOOD, ActivityGuide.balance(1600, 1000, 180, 180))
    }

    @Test fun `balance stays quiet without targets or without logs`() {
        assertEquals(ActivityGuide.Balance.NO_DATA, ActivityGuide.balance(900, 0, 60, 180))
        assertEquals(ActivityGuide.Balance.NO_DATA, ActivityGuide.balance(900, 1000, 60, 0))
        assertEquals(ActivityGuide.Balance.NO_DATA, ActivityGuide.balance(0, 1000, 0, 180))
    }

    @Test fun `the daily average needs something to average`() {
        assertEquals(210, ActivityGuide.dailyAverage(1050, 5))
        assertNull(ActivityGuide.dailyAverage(0, 5))
        assertNull(ActivityGuide.dailyAverage(500, 0))
    }

    @Test fun `the goal is explained in words, not just a number`() {
        assertTrue(ActivityGuide.goalLabel(6, isThai = true).contains("30"))
        assertTrue(ActivityGuide.goalLabel(18, isThai = false).contains("180"))
    }
}
