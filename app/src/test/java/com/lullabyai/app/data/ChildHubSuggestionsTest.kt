package com.lullabyai.app.data

import com.lullabyai.app.data.ChildHubSuggestions.Kind
import com.lullabyai.app.data.ChildHubSuggestions.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The strip speaks to a parent once per screen, so the ordering between competing nudges matters. */
class ChildHubSuggestionsTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 12)

    private fun signals(
        growth: LocalDate? = today.minusDays(3),
        vaccine: LocalDate? = null,
        illness: LocalDate? = null,
        sleep: LocalDate? = today,
        milestones: Int = 0,
        ageMonths: Int? = 7
    ) = Signals(today, growth, vaccine, illness, sleep, milestones, ageMonths)

    @Test
    fun `a late vaccine outranks everything`() {
        val s = signals(
            growth = null,
            vaccine = today.minusDays(5),
            illness = today,
            milestones = 3
        )
        assertEquals(ChildHubSuggestions.Suggestion(Kind.VACCINE_OVERDUE, 5), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a running illness outranks an upcoming shot`() {
        val s = signals(illness = today.minusDays(1), vaccine = today.plusDays(5))
        assertEquals(ChildHubSuggestions.Suggestion(Kind.ILLNESS_FOLLOW_UP, 1), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `an old illness is not followed up`() {
        val s = signals(illness = today.minusDays(10))
        assertNull(ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a shot inside the window is suggested with days remaining`() {
        val s = signals(vaccine = today.plusDays(9))
        assertEquals(ChildHubSuggestions.Suggestion(Kind.VACCINE_SOON, 9), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a distant shot is left alone`() {
        val s = signals(vaccine = today.plusMonths(3))
        assertNull(ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a child with no growth record is asked for a first one`() {
        val s = signals(growth = null)
        assertEquals(ChildHubSuggestions.Suggestion(Kind.GROWTH_NEVER), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `growth older than a month is stale`() {
        val s = signals(growth = today.minusDays(40))
        assertEquals(ChildHubSuggestions.Suggestion(Kind.GROWTH_STALE, 40), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `recent growth is not nagged about`() {
        val s = signals(growth = today.minusDays(29))
        assertNull(ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `pending milestones are suggested once the urgent things are clear`() {
        val s = signals(milestones = 2)
        assertEquals(ChildHubSuggestions.Suggestion(Kind.MILESTONE, 2), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a baby with no sleep logged today is nudged last`() {
        val s = signals(sleep = today.minusDays(1))
        assertEquals(ChildHubSuggestions.Suggestion(Kind.SLEEP_TODAY), ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `an older child is never nudged about sleep`() {
        val s = signals(sleep = today.minusDays(30), ageMonths = 36)
        assertNull(ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `unknown age never triggers the sleep nudge`() {
        val s = signals(sleep = null, ageMonths = null)
        assertNull(ChildHubSuggestions.suggest(s))
    }

    @Test
    fun `a quiet day suggests nothing at all`() {
        assertNull(ChildHubSuggestions.suggest(signals()))
    }
}
