package com.lullabyai.app.data.calendar

import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Action
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.AppItem
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Link
import com.lullabyai.app.data.calendar.CalendarSyncPlanner.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two-way calendar sync decides whether to delete activities in a schedule shared by co-parents and
 * caregivers. The cases that matter most are the ones that could delete something that should stay.
 */
class CalendarSyncPlannerTest {

    private val child = "child-1"
    private val base = Snapshot("Park", 1_000_000_000L, 1_003_600_000L, allDay = false)

    private fun link(id: String, eventId: Long, snap: Snapshot = base, childId: String = child) =
        Link(childId, id, eventId, snap)

    private fun plan(apps: List<AppItem>, links: List<Link>, events: Map<Long, Snapshot>) =
        CalendarSyncPlanner.plan(child, apps, links, events)

    @Test
    fun `unlinked activity gets an event`() {
        val p = plan(listOf(AppItem("a", base)), emptyList(), emptyMap())
        assertEquals(listOf(Action.CreateEvent("a", base)), p.actions)
    }

    @Test
    fun `nothing changed means nothing to do`() {
        val p = plan(listOf(AppItem("a", base)), listOf(link("a", 7)), mapOf(7L to base))
        assertTrue(p.actions.isEmpty())
    }

    @Test
    fun `edit in the app is pushed to the calendar`() {
        val moved = base.copy(
            startMillis = base.startMillis + 3_600_000,
            endMillis = base.endMillis + 3_600_000
        )
        val p = plan(listOf(AppItem("a", moved)), listOf(link("a", 7)), mapOf(7L to base))
        assertEquals(listOf(Action.UpdateEvent("a", 7, moved)), p.actions)
    }

    @Test
    fun `edit in the calendar is pulled into the app`() {
        val renamed = base.copy(title = "Zoo")
        val p = plan(listOf(AppItem("a", base)), listOf(link("a", 7)), mapOf(7L to renamed))
        assertEquals(listOf(Action.UpdateActivity("a", 7, renamed)), p.actions)
    }

    @Test
    fun `when both sides changed the app wins`() {
        val appSide = base.copy(title = "Beach")
        val calendarSide = base.copy(title = "Zoo")
        val p = plan(listOf(AppItem("a", appSide)), listOf(link("a", 7)), mapOf(7L to calendarSide))
        assertEquals(listOf(Action.UpdateEvent("a", 7, appSide)), p.actions)
    }

    @Test
    fun `event deleted in the calendar deletes the activity`() {
        val p = plan(listOf(AppItem("a", base)), listOf(link("a", 7)), emptyMap())
        assertEquals(listOf(Action.DeleteActivity("a", 7)), p.actions)
        assertFalse(p.bulkLoss)
    }

    @Test
    fun `activity deleted in the app deletes its event`() {
        val p = plan(emptyList(), listOf(link("a", 7)), mapOf(7L to base))
        assertEquals(listOf(Action.DeleteEvent("a", 7)), p.actions)
    }

    @Test
    fun `gone on both sides just drops the link`() {
        val p = plan(emptyList(), listOf(link("a", 7)), emptyMap())
        assertEquals(listOf(Action.Unlink("a")), p.actions)
    }

    @Test
    fun `provider rounding under a minute is not an edit`() {
        val rounded = base.copy(startMillis = base.startMillis + 30_000)
        val p = plan(listOf(AppItem("a", base)), listOf(link("a", 7)), mapOf(7L to rounded))
        assertTrue(p.actions.isEmpty())
    }

    @Test
    fun `another child's links are never touched`() {
        // That child's activities were not loaded, so their absence must not read as deletion.
        val p = plan(emptyList(), listOf(link("x", 9, childId = "child-2")), emptyMap())
        assertTrue(p.actions.isEmpty())
    }

    @Test
    fun `most events vanishing at once deletes nothing`() {
        val ids = (1..6).map { "a$it" }
        val links = ids.mapIndexed { i, id -> link(id, i.toLong()) }
        val apps = ids.map { AppItem(it, base) }
        // Only one of six events still exists: an account removal, not six deliberate deletions.
        val p = plan(apps, links, mapOf(0L to base))
        assertTrue(p.bulkLoss)
        assertTrue(p.actions.none { it is Action.DeleteActivity })
        assertEquals(5, p.actions.count { it is Action.Unlink })
    }

    @Test
    fun `a single deletion among several links is honoured`() {
        val ids = (1..6).map { "a$it" }
        val links = ids.mapIndexed { i, id -> link(id, i.toLong()) }
        val apps = ids.map { AppItem(it, base) }
        val events = (1L..5L).associateWith { base } // event 0 was deleted by the parent
        val p = plan(apps, links, events)
        assertFalse(p.bulkLoss)
        assertEquals(listOf(Action.DeleteActivity("a1", 0)), p.actions)
    }

    @Test
    fun `a few deletions below the bulk threshold are honoured`() {
        // Three links, two deleted: ordinary, and below the sample size needed to suspect a loss.
        val links = listOf(link("a", 1), link("b", 2), link("c", 3))
        val apps = listOf(AppItem("a", base), AppItem("b", base), AppItem("c", base))
        val p = plan(apps, links, mapOf(3L to base))
        assertFalse(p.bulkLoss)
        assertEquals(2, p.actions.count { it is Action.DeleteActivity })
    }
}
