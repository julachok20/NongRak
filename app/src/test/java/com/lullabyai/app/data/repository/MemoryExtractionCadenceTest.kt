package com.lullabyai.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the assistant stops to learn something from a conversation.
 *
 * Worth pinning because the failure is invisible: get the boundary wrong and extraction simply
 * never runs. Nothing crashes, nothing logs, and the memory screen just stays empty for weeks
 * while everyone assumes the feature works.
 */
class MemoryExtractionCadenceTest {

    /** A greeting and a reply are not a conversation; nothing in them is worth keeping. */
    @Test
    fun `the first turns do not trigger extraction`() {
        assertFalse(ChatRepository.shouldExtractMemory(0))
        assertFalse(ChatRepository.shouldExtractMemory(1))
        assertFalse(ChatRepository.shouldExtractMemory(2))
    }

    @Test
    fun `the third turn triggers it`() {
        assertTrue(ChatRepository.shouldExtractMemory(3))
    }

    @Test
    fun `it then repeats every third turn, and only those`() {
        assertFalse(ChatRepository.shouldExtractMemory(4))
        assertFalse(ChatRepository.shouldExtractMemory(5))
        assertTrue(ChatRepository.shouldExtractMemory(6))
        assertFalse(ChatRepository.shouldExtractMemory(7))
        assertTrue(ChatRepository.shouldExtractMemory(9))
    }

    /** A long conversation must not start extracting on every single message. */
    @Test
    fun `a long conversation still extracts only one turn in three`() {
        val firing = (1..60).count { ChatRepository.shouldExtractMemory(it) }
        assertTrue("expected 20 firings in 60 turns, got $firing", firing == 20)
    }

    /** Defensive: a negative count is nonsense, and nonsense must not spend a model call. */
    @Test
    fun `a negative turn count never fires`() {
        assertFalse(ChatRepository.shouldExtractMemory(-1))
        assertFalse(ChatRepository.shouldExtractMemory(-3))
    }
}
