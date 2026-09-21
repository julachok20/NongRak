package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.model.ChildActivityEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filtering and grouping in [ActivityHistoryUiState] is the branching logic on the history
 * screen. The sort menu this file used to cover is gone with the redesign; what it still pins is
 * everything the new screen keeps, plus the person chip and the kind-based chips it added.
 */
class ActivityHistoryFilterTest {

    private fun entry(
        id: String,
        kind: String,
        category: String,
        at: String,
        author: String? = null,
        authorId: String? = null,
        detail: String? = null
    ) = ChildActivityEntry(
        sourceId = id, kind = kind, category = category,
        occurredAt = at, detail = detail, authorName = author, authorId = authorId
    )

    // Server order: newest first.
    private val feed = listOf(
        entry("1", "growth", "growth", "2026-08-12T09:00:00+00:00", "Jan", "u-jan", "10.7 kg"),
        entry("2", "care_feed", "care", "2026-08-12T07:00:00+00:00", "Nanny", "u-nanny"),
        entry("3", "vaccine", "medical", "2026-08-11T10:00:00+00:00", "Dr Somchai", "u-dr", "MMR"),
        entry("4", "sleep", "daily", "2026-08-11T06:00:00+00:00", "Nanny", "u-nanny"),
        entry("5", "milk", "nutrition", "2026-08-11T05:00:00+00:00", "Jan", "u-jan", "formula 120 ml")
    )

    private fun state(
        category: String = ActivityHistoryUiState.ALL,
        authorId: String? = null,
        query: String = "",
        feedLimit: Int = ActivityHistoryUiState.PAGE
    ) = ActivityHistoryUiState(entries = feed, category = category, authorId = authorId, query = query, feedLimit = feedLimit)

    @Test
    fun `the list keeps the server's newest-first order`() {
        assertEquals(listOf("1", "2", "3", "4", "5"), state().visible.map { it.sourceId })
    }

    @Test
    fun `category chips narrow by the feed category`() {
        assertEquals(listOf("2"), state(category = "care").visible.map { it.sourceId })
        assertEquals(listOf("5"), state(category = "nutrition").visible.map { it.sourceId })
        assertEquals(listOf("3"), state(category = "medical").visible.map { it.sourceId })
    }

    @Test
    fun `growth, sleep and nappy chips narrow by kind, since those share a category with others`() {
        // "sleep" and "waste" both live in category "daily"; the chip must not show the other one.
        assertEquals(listOf("4"), state(category = "sleep").visible.map { it.sourceId })
        assertEquals(listOf("1"), state(category = "growth").visible.map { it.sourceId })
        assertTrue(state(category = "waste").visible.isEmpty())
    }

    @Test
    fun `the person chip shows only that person's entries`() {
        assertEquals(listOf("2", "4"), state(authorId = "u-nanny").visible.map { it.sourceId })
    }

    @Test
    fun `filters combine`() {
        assertEquals(listOf("4"), state(category = "sleep", authorId = "u-nanny").visible.map { it.sourceId })
        assertTrue(state(category = "care", authorId = "u-jan").visible.isEmpty())
    }

    @Test
    fun `search matches detail and author, case-insensitively`() {
        assertEquals(listOf("3"), state(query = "mmr").visible.map { it.sourceId })
        assertEquals(listOf("2", "4"), state(query = "nann").visible.map { it.sourceId })
    }

    @Test
    fun `the person chips list each author once, in order of first appearance`() {
        assertEquals(listOf("u-jan", "u-nanny", "u-dr"), state().authors.map { it.first })
    }

    @Test
    fun `entries group under their local day`() {
        // Shape check on the key, since the machine's zone decides the exact date.
        val days = state().days
        assertTrue(days.all { it.first.length == 10 })
        assertEquals(5, days.sumOf { it.second.size })
    }

    @Test
    fun `load more is offered only when a full page came back`() {
        assertFalse(state().canLoadMore)
        assertTrue(state(feedLimit = 5).canLoadMore)
    }

    @Test
    fun `only parents may chat and revert`() {
        assertEquals(true, ActivityHistoryUiState(myRole = "owner").canChat)
        assertEquals(true, ActivityHistoryUiState(myRole = "co_owner").isParent)
        assertEquals(false, ActivityHistoryUiState(myRole = "nanny").canChat)
        assertEquals(false, ActivityHistoryUiState(myRole = "doctor").isParent)
        assertEquals(false, ActivityHistoryUiState(myRole = null).canChat)
    }
}
