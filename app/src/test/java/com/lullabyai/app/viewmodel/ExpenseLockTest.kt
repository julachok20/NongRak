package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.model.ChildExpense
import com.lullabyai.app.ui.screens.expense.isLocked
import com.lullabyai.app.ui.screens.expense.minutesLeft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The 30-minute freeze is enforced by a Postgres trigger; this covers the client's reading of it,
 * which decides whether the checkbox is tappable. The risk here is timestamp parsing, not policy —
 * the server sends an offset timestamp and the device is rarely in UTC.
 */
class ExpenseLockTest {

    private fun expense(usedAt: String?, used: Boolean = true) =
        ChildExpense(id = "e1", alreadyUsed = used, usedAt = usedAt)

    private fun agoMinutes(m: Long): String =
        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(m).toString()

    @Test
    fun `never ticked is never locked`() {
        assertFalse(expense(null, used = false).isLocked())
        assertNull(expense(null, used = false).minutesLeft())
    }

    @Test
    fun `just ticked is editable with the full window left`() {
        val e = expense(agoMinutes(0))
        assertFalse(e.isLocked())
        // Truncation toward zero can land this on 29 the instant after stamping.
        assertTrue(e.minutesLeft()!! in 29..30)
    }

    @Test
    fun `inside the window stays editable and counts down`() {
        val e = expense(agoMinutes(25))
        assertFalse(e.isLocked())
        assertTrue(e.minutesLeft()!! in 4..5)
    }

    @Test
    fun `past the window is locked`() {
        val e = expense(agoMinutes(31))
        assertTrue(e.isLocked())
        assertNull(e.minutesLeft())
    }

    @Test
    fun `a non-UTC offset is read as an instant, not as local time`() {
        // The same moment as 20 minutes ago, written with Thailand's +07:00 offset. Reading this
        // as local wall-clock time would shift it by seven hours and freeze a row that is still
        // well inside its window.
        val bangkok = OffsetDateTime.now(ZoneOffset.ofHours(7)).minusMinutes(20)
        val e = expense(bangkok.toString())
        assertFalse(e.isLocked())
        assertTrue(e.minutesLeft()!! in 9..10)
    }

    @Test
    fun `unparseable timestamp is treated as unlocked rather than crashing`() {
        assertFalse(expense("not-a-date").isLocked())
        assertNull(expense("not-a-date").minutesLeft())
    }
}
