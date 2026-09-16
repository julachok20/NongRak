package com.lullabyai.app.ui.screens.child

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChildHubAgeLabelTest {

    @Test
    fun `hero age includes leftover weeks`() {
        val dob = LocalDate.now().minusMonths(7).minusDays(10).toString()
        val label = hubAgeLabel(dob, true)
        assertTrue(label.contains("เดือน"))
        assertTrue(label.contains("สัปดาห์"))
    }

    @Test
    fun `header pill uses months only`() {
        val dob = LocalDate.of(2025, 9, 13).toString()
        val label = hubAgeMonthsOnly(dob, true)
        assertEquals("12 เดือน", label)
    }
}
