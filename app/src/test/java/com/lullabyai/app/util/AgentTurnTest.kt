package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reply can ask for more than one thing. The previous parser took the first block and dropped the
 * rest silently, so "log the feed and remind me at six" half happened — the kind of failure that
 * leaves no trace and is therefore never reported.
 */
class AgentTurnTest {

    @Test
    fun `two actions in one reply are both kept`() {
        val raw = """
            บันทึกให้แล้วนะคะ
            <AGENT_ACTION>{"type":"log_milk","confirmText":"นม 120ml","params":{"volumeMl":"120"}}</AGENT_ACTION>
            <AGENT_ACTION>{"type":"add_schedule","confirmText":"นัด 18:00","params":{"title":"อาบน้ำ"}}</AGENT_ACTION>
        """.trimIndent()

        val turn = parseAgentTurn(raw)

        assertEquals(2, turn.actions.size)
        assertEquals("log_milk", turn.actions[0].type)
        assertEquals("add_schedule", turn.actions[1].type)
        assertEquals("บันทึกให้แล้วนะคะ", turn.displayText)
    }

    @Test
    fun `a query is separated from actions and marks the turn unfinished`() {
        val raw = """
            ขอดูข้อมูลก่อนนะคะ
            <AI_QUERY>{"tool":"query_sleep","params":{"days":"7"}}</AI_QUERY>
        """.trimIndent()

        val turn = parseAgentTurn(raw)

        assertEquals(1, turn.queries.size)
        assertEquals("query_sleep", turn.queries[0].tool)
        assertEquals("7", turn.queries[0].params["days"])
        assertTrue(turn.wantsMoreWork)
        assertTrue(turn.actions.isEmpty())
    }

    @Test
    fun `several queries can be requested at once`() {
        val raw = """
            <AI_QUERY>{"tool":"query_sleep","params":{"days":"7"}}</AI_QUERY>
            <AI_QUERY>{"tool":"query_feeds","params":{"days":"7"}}</AI_QUERY>
        """.trimIndent()

        assertEquals(
            listOf("query_sleep", "query_feeds"),
            parseAgentTurn(raw).queries.map { it.tool }
        )
    }

    @Test
    fun `a reply with no blocks is left alone`() {
        val turn = parseAgentTurn("สวัสดีค่ะ วันนี้เป็นอย่างไรบ้าง")

        assertEquals("สวัสดีค่ะ วันนี้เป็นอย่างไรบ้าง", turn.displayText)
        assertTrue(turn.actions.isEmpty())
        assertTrue(turn.queries.isEmpty())
        assertNull(turn.needInfo)
        assertFalse(turn.wantsMoreWork)
    }

    @Test
    fun `malformed json is dropped without taking the reply with it`() {
        // A half-written block must not cost the parent the sentence they were reading.
        val raw = "บันทึกแล้วค่ะ\n<AGENT_ACTION>{\"type\":</AGENT_ACTION>"
        val turn = parseAgentTurn(raw)

        assertTrue(turn.actions.isEmpty())
        assertEquals("บันทึกแล้วค่ะ", turn.displayText)
    }

    @Test
    fun `the old single-action entry point still returns the first action`() {
        val raw = """
            ok
            <AGENT_ACTION>{"type":"log_milk","confirmText":"a","params":{}}</AGENT_ACTION>
            <AGENT_ACTION>{"type":"log_food","confirmText":"b","params":{}}</AGENT_ACTION>
        """.trimIndent()

        val (text, action, needInfo) = parseAgentAction(raw)

        assertEquals("ok", text)
        assertEquals("log_milk", action?.type)
        assertNull(needInfo)
    }
}
