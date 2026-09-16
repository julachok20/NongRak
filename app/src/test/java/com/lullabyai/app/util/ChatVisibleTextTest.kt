package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Missing info is asked in chat; nothing the model asks may disappear with the old popup. */
class ChatVisibleTextTest {

    @Test
    fun `an action block is stripped from the bubble`() {
        val raw = "กดยืนยันด้านล่างนะคะ\n" +
            "<AGENT_ACTION>{\"type\":\"log_milk\",\"confirmText\":\"นม 120ml\",\"params\":{\"volumeMl\":\"120\"}}</AGENT_ACTION>"
        assertEquals("กดยืนยันด้านล่างนะคะ", chatVisibleText(raw))
    }

    @Test
    fun `an old need-info block becomes a question in the chat`() {
        val raw = "ค่ะ คุณแม่\n<NEED_INFO>{\"question\":\"น้องกีก้าหนักกี่กิโลคะ?\",\"fieldLabel\":\"น้ำหนัก\"}</NEED_INFO>"
        assertEquals("ค่ะ คุณแม่\n\nน้องกีก้าหนักกี่กิโลคะ?", chatVisibleText(raw))
    }

    @Test
    fun `a question with no other text still shows`() {
        val raw = "<NEED_INFO>{\"question\":\"หมายถึงลูกคนไหนคะ?\"}</NEED_INFO>"
        assertEquals("หมายถึงลูกคนไหนคะ?", chatVisibleText(raw))
    }
}
