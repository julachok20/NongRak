package com.lullabyai.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The extractor reads whatever the model actually returned, not what it was told to return.
 *
 * Same failure class as the "{...}" notifications that reached production: a model wraps its answer
 * in a fence or a sentence, a strict parser throws, and the feature silently stops working. Here the
 * cost is a family quietly losing its memory, with nothing on screen to show it.
 */
class AssistantMemoryExtractTest {

    private val one =
        """[{"key":"nap_window","value":"มีนางีบบ่ายประมาณ 13:00","source":"said","confidence":0.8}]"""

    @Test
    fun `a bare array passes through`() {
        assertEquals(one, AssistantMemoryRepository.extractArray(one))
    }

    @Test
    fun `a json fence is stripped`() {
        assertEquals(one, AssistantMemoryRepository.extractArray("```json\n$one\n```"))
    }

    @Test
    fun `a plain fence is stripped`() {
        assertEquals(one, AssistantMemoryRepository.extractArray("```\n$one\n```"))
    }

    @Test
    fun `prose around the array is discarded`() {
        val wrapped = "Here is what I found:\n$one\nLet me know if you need more."
        assertEquals(one, AssistantMemoryRepository.extractArray(wrapped))
    }

    /** The model was told to answer [] when there is nothing worth keeping, and often does. */
    @Test
    fun `an empty array survives`() {
        assertEquals("[]", AssistantMemoryRepository.extractArray("[]"))
        assertEquals("[]", AssistantMemoryRepository.extractArray("```json\n[]\n```"))
    }

    /**
     * Anything unparseable becomes an empty array rather than an exception. Losing one extraction
     * is acceptable; crashing the conversation that produced it is not.
     */
    @Test
    fun `junk degrades to an empty array, never a throw`() {
        assertEquals("[]", AssistantMemoryRepository.extractArray("I could not do that."))
        assertEquals("[]", AssistantMemoryRepository.extractArray(""))
        assertEquals("[]", AssistantMemoryRepository.extractArray("{\"key\":\"x\"}"))
    }

    /** A nested array must not be truncated at the first closing bracket. */
    @Test
    fun `nested brackets keep the whole array`() {
        val nested = """[{"key":"likes","value":"[banana, rice]","source":"said"}]"""
        assertEquals(nested, AssistantMemoryRepository.extractArray("answer: $nested"))
    }
}
