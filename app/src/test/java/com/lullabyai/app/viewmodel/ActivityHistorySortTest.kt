package com.lullabyai.app.viewmodel

import com.lullabyai.app.data.model.ChildActivityEntry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The filter + sort + grouping in [ActivityHistoryUiState] is the only branching logic on the
 * history screen, so it is the piece worth pinning down.
 */
class ActivityHistorySortTest {

    private fun entry(
        id: String,
        kind: String,
        category: String,
        at: String,
        author: String? = null,
        detail: String? = null
    ) = ChildActivityEntry(
        sourceId = id, kind = kind, category = category,
        occurredAt = at, detail = detail, authorName = author
    )

    // Server order: newest first.
    private val feed = listOf(
        entry("1", "growth", "growth", "2026-08-12T09:00:00+00:00", "Jan", "10.7 kg"),
        entry("2", "care_feed", "care", "2026-08-12T07:00:00+00:00", "Nanny"),
        entry("3", "vaccine", "medical", "2026-08-11T10:00:00+00:00", "Dr Somchai", "MMR")
    )

    private fun state(
        sort: ActivitySort = ActivitySort.NEWEST,
        category: String = ActivityHistoryUiState.ALL,
        query: String = ""
    ) = ActivityHistoryUiState(entries = feed, sort = sort, category = category, query = query)

    @Test
    fun `newest keeps the server order`() {
        assertEquals(listOf("1", "2", "3"), state().visible.map { it.sourceId })
    }

    @Test
    fun `oldest reverses it`() {
        assertEquals(
            listOf("3", "2", "1"),
            state(sort = ActivitySort.OLDEST).visible.map { it.sourceId }
        )
    }

    @Test
    fun `by type groups each kind together`() {
        val ordered = state(sort = ActivitySort.KIND).visible.map { it.kind }
        assertEquals(listOf("care_feed", "growth", "vaccine"), ordered)
    }

    @Test
    fun `by person sorts on the author name`() {
        assertEquals(
            listOf("Dr Somchai", "Jan", "Nanny"),
            state(sort = ActivitySort.AUTHOR).visible.map { it.authorName }
        )
    }

    @Test
    fun `category chip narrows to one category`() {
        assertEquals(listOf("2"), state(category = "care").visible.map { it.sourceId })
    }

    @Test
    fun `search matches detail and author, case-insensitively`() {
        assertEquals(listOf("3"), state(query = "mmr").visible.map { it.sourceId })
        assertEquals(listOf("2"), state(query = "nann").visible.map { it.sourceId })
    }

    @Test
    fun `group key follows the sort`() {
        assertEquals("growth", state(sort = ActivitySort.KIND).groupKeyOf(feed[0]))
        assertEquals("Jan", state(sort = ActivitySort.AUTHOR).groupKeyOf(feed[0]))
        // Time sorts group by the LOCAL day — the whole reason groupKeyOf exists rather than a
        // substring of the raw UTC timestamp. Shape check only, since the zone varies by machine.
        assertEquals(10, state().groupKeyOf(feed[0]).length)
    }

    @Test
    fun `only owners and co-owners may chat`() {
        assertEquals(true, ActivityHistoryUiState(myRole = "owner").canChat)
        assertEquals(true, ActivityHistoryUiState(myRole = "co_owner").canChat)
        assertEquals(false, ActivityHistoryUiState(myRole = "nanny").canChat)
        assertEquals(false, ActivityHistoryUiState(myRole = "doctor").canChat)
        assertEquals(false, ActivityHistoryUiState(myRole = null).canChat)
    }
}
