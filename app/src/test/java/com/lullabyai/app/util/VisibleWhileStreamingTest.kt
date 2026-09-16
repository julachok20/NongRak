package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The chat bubble fills while a reply streams; block tags and half-written JSON must never flash in it. */
class VisibleWhileStreamingTest {

    @Test
    fun `plain text passes through`() {
        assertEquals("ลูกนอนหลับดีค่ะ", visibleWhileStreaming("ลูกนอนหลับดีค่ะ"))
    }

    @Test
    fun `a block and everything after it is held back`() {
        val partial = "กดยืนยันด้านล่างนะคะ\n<AGENT_ACTION>{\"type\":\"log_milk\""
        assertEquals("กดยืนยันด้านล่างนะคะ", visibleWhileStreaming(partial))
    }

    @Test
    fun `an opener that has only half arrived is held back`() {
        assertEquals("ตอบแล้วค่ะ", visibleWhileStreaming("ตอบแล้วค่ะ <NEED_"))
    }

    @Test
    fun `a less-than sign shows once it cannot become a block`() {
        assertEquals("อุณหภูมิ", visibleWhileStreaming("อุณหภูมิ <"))
        assertEquals("อุณหภูมิ < 38 องศา", visibleWhileStreaming("อุณหภูมิ < 38 องศา"))
    }
}
