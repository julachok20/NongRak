package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hydration card's numbers. The one that matters most is water before six months: it must be a
 * hard zero with a warning, never a goal a parent could try to meet.
 */
class HydrationGuideTest {

    @Test fun `no plain water before six months, and the screen is told to say so`() {
        assertEquals(0, HydrationGuide.waterTargetMl(2))
        assertEquals(0, HydrationGuide.waterTargetMl(5))
        assertTrue(HydrationGuide.waterNotAdvised(5))
        assertFalse(HydrationGuide.waterNotAdvised(6))
    }

    @Test fun `water grows with age and then stops`() {
        assertEquals(120, HydrationGuide.waterTargetMl(6))
        assertEquals(120, HydrationGuide.waterTargetMl(11))
        assertEquals(240, HydrationGuide.waterTargetMl(12))
        assertEquals(480, HydrationGuide.waterTargetMl(24))
        assertEquals(960, HydrationGuide.waterTargetMl(48))
        assertEquals(960, HydrationGuide.waterTargetMl(96))   // capped, not linear forever
    }

    @Test fun `milk volume comes from the child's own energy figure`() {
        // 0-5 months: the whole 460 kcal of the DRI comes from milk.
        assertEquals((460 / 0.67).toInt(), HydrationGuide.milkTargetMl(3, 460))
        // 6-11 months: solids take a share, so the milk figure is lower than the energy alone implies.
        assertEquals((700 * 0.6 / 0.67).toInt(), HydrationGuide.milkTargetMl(8, 700))
    }

    @Test fun `past a year there is no millilitre target to fail against`() {
        assertNull(HydrationGuide.milkTargetMl(12, 1050))
        assertNull(HydrationGuide.milkTargetMl(30, 1050))
        assertNull(HydrationGuide.milkTargetMl(3, null))
        assertNull(HydrationGuide.milkTargetMl(3, 0))
    }

    @Test fun `balance says nothing when there is no target`() {
        assertEquals(HydrationGuide.Balance.UNKNOWN, HydrationGuide.balance(500, null))
        assertEquals(HydrationGuide.Balance.UNKNOWN, HydrationGuide.balance(500, 0))
        assertEquals(HydrationGuide.Balance.NONE_YET, HydrationGuide.balance(0, 600))
        assertEquals(HydrationGuide.Balance.LOW, HydrationGuide.balance(300, 600))
        assertEquals(HydrationGuide.Balance.GOOD, HydrationGuide.balance(480, 600))
        assertEquals(HydrationGuide.Balance.OVER, HydrationGuide.balance(800, 600))
    }

    @Test fun `millilitres convert to the ounces on the bottle`() {
        assertEquals(5.0, HydrationGuide.toOunces(148), 0.05)
        assertEquals(0.0, HydrationGuide.toOunces(0), 0.001)
    }
}
