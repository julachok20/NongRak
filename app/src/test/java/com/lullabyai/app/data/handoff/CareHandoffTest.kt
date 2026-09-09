package com.lullabyai.app.data.handoff

import org.junit.Assert.*
import org.junit.Test

class CareHandoffTest {
    private val handoff=CareHandoff("h","child","parent","carer")
    @Test fun sendingDoesNotAcknowledgeCare() {
        assertEquals("pending",handoff.status)
        assertNull(handoff.acceptedAt)
        assertEquals(listOf("accept","decline"),handoffActions(handoff,"carer"))
        assertEquals(listOf("cancel"),handoffActions(handoff,"parent"))
    }
    @Test fun strangerHasNoActionsInAnyState() {
        listOf("pending","active","declined","cancelled","completed","closed").forEach {
            assertTrue(handoffActions(handoff.copy(status=it),"stranger").isEmpty())
        }
    }
    @Test fun returnNeedsSenderAcknowledgment() {
        assertEquals(listOf("complete"),handoffActions(handoff.copy(status="active"),"carer"))
        assertTrue(handoffActions(handoff.copy(status="active"),"parent").isEmpty())
        assertEquals(listOf("acknowledge"),handoffActions(handoff.copy(status="completed"),"parent"))
        assertTrue(handoffActions(handoff.copy(status="completed"),"carer").isEmpty())
        assertTrue(handoffActions(handoff.copy(status="closed"),"parent").isEmpty())
    }
}
