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

    /**
     * The leftover days are the point: the hero label above spells them out as weeks, and the
     * header pill must drop them. The date is relative for the same reason the one above it is.
     * This test used to hardcode 2025-09-13 and assert "12 เดือน", so it turned red the day that
     * child reached 13 months and stayed red from 2026-10-13 onward.
     */
    @Test
    fun `header pill uses months only, ignoring leftover days`() {
        val dob = LocalDate.now().minusMonths(12).minusDays(10).toString()
        val label = hubAgeMonthsOnly(dob, true)
        assertEquals("12 เดือน", label)
    }

    @Test
    fun `an unparseable birthdate gives an empty label, never a crash`() {
        assertEquals("", hubAgeMonthsOnly(null, true))
        assertEquals("", hubAgeMonthsOnly("", true))
        assertEquals("", hubAgeMonthsOnly("not-a-date", true))
    }
}
