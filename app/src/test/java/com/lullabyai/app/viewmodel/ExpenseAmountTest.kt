package com.lullabyai.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Money parsing is the only place this feature can silently corrupt data, so it is the piece
 * worth pinning down. Everything downstream stores integer satang.
 */
class ExpenseAmountTest {

    @Test
    fun `plain amounts convert to satang`() {
        assertEquals(45000L, ExpenseViewModel.parseSatang("450"))
        assertEquals(45050L, ExpenseViewModel.parseSatang("450.50"))
        assertEquals(1L, ExpenseViewModel.parseSatang("0.01"))
    }

    @Test
    fun `separators and currency marks are tolerated`() {
        assertEquals(125050L, ExpenseViewModel.parseSatang("1,250.50"))
        assertEquals(125050L, ExpenseViewModel.parseSatang(" ฿1,250.50 "))
    }

    @Test
    fun `half-satang input rounds rather than truncating`() {
        // 450.555 -> 45055.5 satang. Truncating would quietly lose money on every entry.
        assertEquals(45056L, ExpenseViewModel.parseSatang("450.555"))
    }

    @Test
    fun `non-amounts are refused instead of becoming zero`() {
        assertNull(ExpenseViewModel.parseSatang(""))
        assertNull(ExpenseViewModel.parseSatang("   "))
        assertNull(ExpenseViewModel.parseSatang("abc"))
        assertNull(ExpenseViewModel.parseSatang("0"))
        assertNull(ExpenseViewModel.parseSatang("-20"))
    }

    @Test
    fun `formatting is the inverse for display`() {
        assertEquals("1,250.50", ExpenseViewModel.formatBaht(125050))
        assertEquals("0.01", ExpenseViewModel.formatBaht(1))
        assertEquals("0.00", ExpenseViewModel.formatBaht(0))
    }

    @Test
    fun `a round trip through satang keeps the value`() {
        val satang = ExpenseViewModel.parseSatang("9,999.99")
        assertEquals(999999L, satang)
        assertEquals("9,999.99", ExpenseViewModel.formatBaht(satang!!))
    }
}
