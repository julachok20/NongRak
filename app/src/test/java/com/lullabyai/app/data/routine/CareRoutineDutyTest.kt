package com.lullabyai.app.data.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareRoutineDutyTest {

    private val mum = CareRoutineDuty.Person("u-mum", "คุณแม่", "owner")
    private val nanny = CareRoutineDuty.Person("u-nanny", "พี่เลี้ยงใจ", "nanny")
    private val gran = CareRoutineDuty.Person("u-gran", "คุณยาย", "grandparent")
    private val people = listOf(mum, nanny, gran)

    @Test
    fun `an open handover names the person holding the child, with their shift`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = CareRoutineDuty.Shift(nanny.userId, startMinute = 8 * 60, endMinute = 18 * 60),
            ticks = listOf(CareRoutineDuty.Tick(mum.userId, 7 * 60 + 20))
        )

        assertEquals(nanny, duty?.person)
        assertEquals(8 * 60, duty?.shiftStart)
        assertEquals(18 * 60, duty?.shiftEnd)
        assertTrue(duty?.isOnDutyNow == true)
    }

    @Test
    fun `the open handover wins over whoever ticked last`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = CareRoutineDuty.Shift(nanny.userId, null, null),
            ticks = listOf(CareRoutineDuty.Tick(gran.userId, 12 * 60))
        )

        assertEquals(nanny, duty?.person)
        // The shift is theirs, so someone else's tick is not reported as their last one.
        assertNull(duty?.lastTickMinute)
    }

    @Test
    fun `without a handover, the caregiver who ticked most recently is the one there`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = null,
            ticks = listOf(
                CareRoutineDuty.Tick(gran.userId, 8 * 60),
                CareRoutineDuty.Tick(nanny.userId, 12 * 60 + 15)
            )
        )

        assertEquals(nanny, duty?.person)
        assertEquals(12 * 60 + 15, duty?.lastTickMinute)
        assertTrue(duty?.isOnDutyNow == true)
    }

    @Test
    fun `a parent's own tick does not make a caregiver of them`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId),
            shift = null,
            ticks = listOf(CareRoutineDuty.Tick(mum.userId, 13 * 60))
        )

        // Falls through to the only caregiver, who is listed but not known to be on duty.
        assertEquals(nanny, duty?.person)
        assertFalse(duty?.isOnDutyNow == true)
        assertNull(duty?.lastTickMinute)
    }

    @Test
    fun `a lone caregiver is shown without claiming they are on duty now`() {
        val duty = CareRoutineDuty.pick(people, setOf(nanny.userId), shift = null, ticks = emptyList())

        assertEquals(nanny, duty?.person)
        assertFalse(duty?.isOnDutyNow == true)
    }

    @Test
    fun `several idle caregivers name nobody rather than guess`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = null,
            ticks = emptyList()
        )

        assertNull(duty)
    }

    @Test
    fun `no caregivers at all leaves the row off the screen`() {
        assertNull(CareRoutineDuty.pick(people, emptySet(), shift = null, ticks = emptyList()))
    }

    @Test
    fun `a handover to someone no longer in the circle falls back to the ticks`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = CareRoutineDuty.Shift("u-removed", 8 * 60, 18 * 60),
            ticks = listOf(CareRoutineDuty.Tick(gran.userId, 9 * 60))
        )

        assertEquals(gran, duty?.person)
        assertEquals(9 * 60, duty?.lastTickMinute)
    }

    @Test
    fun `ticks with no recorded time never outrank a timed one`() {
        val duty = CareRoutineDuty.pick(
            people = people,
            caregiverIds = setOf(nanny.userId, gran.userId),
            shift = null,
            ticks = listOf(
                CareRoutineDuty.Tick(gran.userId, null),
                CareRoutineDuty.Tick(nanny.userId, 6 * 60)
            )
        )

        assertEquals(nanny, duty?.person)
    }
}
