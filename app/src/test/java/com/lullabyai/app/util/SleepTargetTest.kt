package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * These bands are shown to a parent as "roughly how much a child this age sleeps", and the same
 * numbers drive the sleep goal elsewhere in the app. Worth pinning so the two cannot drift apart
 * again - they lived in one ViewModel and were about to be copied into onboarding.
 */
class SleepTargetTest {

    @Test
    fun `each age band returns its published figure`() {
        assertEquals(15, SleepTarget.forAgeMonths(0))
        assertEquals(15, SleepTarget.forAgeMonths(3))
        assertEquals(14, SleepTarget.forAgeMonths(4))
        assertEquals(14, SleepTarget.forAgeMonths(11))
        assertEquals(13, SleepTarget.forAgeMonths(12))
        assertEquals(13, SleepTarget.forAgeMonths(23))
        assertEquals(11, SleepTarget.forAgeMonths(24))
    }

    @Test
    fun `an age past the last band does not fall through to zero`() {
        assertEquals(11, SleepTarget.forAgeMonths(200))
    }

    /** A nonsense age must still produce a usable suggestion rather than break the step. */
    @Test
    fun `a negative age falls back to the oldest band`() {
        assertEquals(11, SleepTarget.forAgeMonths(-5))
    }

    @Test
    fun `a birthdate a few months ago lands in the infant band`() {
        val sixMonths = LocalDate.now().minusMonths(6).toString()
        assertEquals(14, SleepTarget.forBirthdate(sixMonths))
    }

    @Test
    fun `a toddler birthdate lands in the toddler band`() {
        val eighteenMonths = LocalDate.now().minusMonths(18).toString()
        assertEquals(13, SleepTarget.forBirthdate(eighteenMonths))
    }

    /**
     * A missing birthdate reads as 0 months, so this returns the newborn figure. That is only
     * acceptable because it is a SUGGESTION the parent has to accept - it is never written to a
     * child record on its own, which is the bug this change fixed.
     */
    @Test
    fun `a blank or null birthdate still returns a suggestion rather than throwing`() {
        assertEquals(15, SleepTarget.forBirthdate(null))
        assertEquals(15, SleepTarget.forBirthdate(""))
        assertEquals(15, SleepTarget.forBirthdate("not-a-date"))
    }
}
