package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.model.BudgetLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budget bar is the one number on the Finance screen a parent acts on, so its arithmetic is
 * pinned down here. The overspent case matters most: it has to report the overshoot without the
 * bar itself running past its track.
 */
class BudgetLineTest {

    @Test
    fun `no budget set reports no progress and no overspend`() {
        val line = BudgetLine(category = null, budgetSatang = 0, spentSatang = 450_00)
        assertFalse(line.hasBudget)
        assertEquals(0f, line.progress, 0.0001f)
        assertFalse(line.overBudget)
    }

    @Test
    fun `partial spend is the plain ratio`() {
        val line = BudgetLine(budgetSatang = 15_000_00, spentSatang = 12_450_00)
        assertEquals(0.83f, line.progress, 0.001f)
        assertFalse(line.overBudget)
        assertEquals(2_550_00, line.remainingSatang)
    }

    @Test
    fun `overspending clamps the bar but still reports the overshoot`() {
        val line = BudgetLine(budgetSatang = 10_000_00, spentSatang = 12_000_00)
        // Clamped: a bar drawn past its track reads as a rendering bug, not as overspending.
        assertEquals(1f, line.progress, 0.0001f)
        assertTrue(line.overBudget)
        // The negative remainder is what the screen renders as "เกินงบ 2,000 ฿".
        assertEquals(-2_000_00, line.remainingSatang)
    }

    @Test
    fun `spending exactly the budget is not over budget`() {
        val line = BudgetLine(budgetSatang = 5_000_00, spentSatang = 5_000_00)
        assertEquals(1f, line.progress, 0.0001f)
        assertFalse(line.overBudget)
        assertEquals(0, line.remainingSatang)
    }
}
