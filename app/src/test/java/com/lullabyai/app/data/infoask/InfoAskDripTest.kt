package com.lullabyai.app.data.infoask

import org.junit.Assert.assertEquals
import org.junit.Test

class InfoAskDripTest {

    private fun prompt(id: String) = InfoAskPrompt(id, InfoAskSubject.CHILD, InfoAskKind.TEXT, "q", "q")

    private fun open(n: Int) = (1..n).map { prompt("f$it") }

    @Test
    fun `first hour introduces at most three and they stay when unanswered`() {
        val now = 1_000_000L
        val (first, drip1) = InfoAskCatalog.drip(open(10), InfoAskCatalog.AskDrip(), now)
        assertEquals(listOf("f1", "f2", "f3"), first.map { it.fieldId })
        val (again, drip2) = InfoAskCatalog.drip(open(10), drip1, now + 60_000)
        assertEquals(listOf("f1", "f2", "f3"), again.map { it.fieldId })
        assertEquals(drip1.stacked.map { it.fieldId }, drip2.stacked.map { it.fieldId })
    }

    @Test
    fun `next hour can add three more until the stack hits twelve`() {
        var drip = InfoAskCatalog.AskDrip()
        var last = emptyList<InfoAskPrompt>()
        repeat(5) { hour ->
            val result = InfoAskCatalog.drip(open(20), drip, hour * InfoAskCatalog.HOUR_MS)
            last = result.first
            drip = result.second
        }
        assertEquals(12, last.size)
        assertEquals(12, drip.addedToday.size)
        val (stuck, drip2) = InfoAskCatalog.drip(open(20), drip, 5 * InfoAskCatalog.HOUR_MS)
        assertEquals(12, stuck.size)
        assertEquals(drip.stacked.map { it.fieldId }, drip2.stacked.map { it.fieldId })
    }

    @Test
    fun `answering in the same hour does not refill the three-per-hour cap`() {
        val t0 = 0L
        val (_, drip1) = InfoAskCatalog.drip(open(20), InfoAskCatalog.AskDrip(), t0)
        val remaining = open(20).filter { it.fieldId != "f1" }
        val (sameHour, _) = InfoAskCatalog.drip(remaining, drip1, t0 + 60_000)
        assertEquals(listOf("f2", "f3"), sameHour.map { it.fieldId })
    }

    @Test
    fun `answering frees a slot but the daily add cap still holds`() {
        val t0 = 0L
        val (first, drip1) = InfoAskCatalog.drip(open(20), InfoAskCatalog.AskDrip(), t0)
        assertEquals(3, first.size)
        val afterAnswer = drip1.copy(stacked = drip1.stacked.drop(1))
        val remaining = open(20).filter { it.fieldId != "f1" }
        val (second, drip2) = InfoAskCatalog.drip(remaining, afterAnswer, t0 + InfoAskCatalog.HOUR_MS)
        assertEquals(listOf("f2", "f3", "f4", "f5", "f6"), second.map { it.fieldId })
        assertEquals(setOf("f1", "f2", "f3", "f4", "f5", "f6"), drip2.addedToday)
    }
}
