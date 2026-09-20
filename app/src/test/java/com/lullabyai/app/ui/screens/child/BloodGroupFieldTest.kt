package com.lullabyai.app.ui.screens.child

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * children.blood_group is one string ("O+"); the edit page shows it as an ABO chip row plus an
 * Rh toggle. Splitting and rejoining it has to be lossless, or opening the page and saving it
 * without touching anything would quietly change a medical field.
 */
class BloodGroupFieldTest {

    @Test
    fun `round trips every value the picker can produce`() {
        for (abo in listOf("A", "B", "AB", "O")) {
            for (rh in listOf("+", "-")) {
                val stored = abo + rh
                assertEquals(stored, composeBloodGroup(bloodAbo(stored), bloodRhPositive(stored)))
            }
        }
    }

    @Test
    fun `reads the parts out of a stored value`() {
        assertEquals("AB", bloodAbo("AB-"))
        assertFalse(bloodRhPositive("AB-"))
        assertEquals("O", bloodAbo("O+"))
        assertTrue(bloodRhPositive("O+"))
    }

    @Test
    fun `tolerates the shapes older rows actually hold`() {
        assertEquals("A", bloodAbo(" a+ "))   // whitespace and lower case
        assertEquals("O", bloodAbo("O"))      // group recorded without an Rh
        assertTrue(bloodRhPositive("O"))      // unknown Rh reads as positive
    }

    @Test
    fun `refuses values that are not a blood group`() {
        assertNull(bloodAbo(null))
        assertNull(bloodAbo(""))
        assertNull(bloodAbo("unknown"))       // written by the Info Ask "not sure" chip
        assertNull(bloodAbo("C+"))
    }

    @Test
    fun `an unset group stays unset rather than becoming a bare sign`() {
        assertNull(composeBloodGroup(null, true))
        assertNull(composeBloodGroup(null, false))
    }
}
