package com.lullabyai.app.data.infoask

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bug this pins down: questions built for child A stayed on screen after switching to child B,
 * and answering them wrote A's question into B's record.
 */
class InfoAskReloadTest {

    @Test
    fun `a different child always reloads, even with questions already on screen`() {
        assertTrue(InfoAskCatalog.needsReload(selectedChildId = "child-b", loadedForChildId = "child-a", loaded = 3))
    }

    @Test
    fun `the same child with questions loaded does not reload`() {
        assertFalse(InfoAskCatalog.needsReload(selectedChildId = "child-a", loadedForChildId = "child-a", loaded = 3))
    }

    @Test
    fun `nothing loaded yet reloads, and no child selected does nothing`() {
        assertTrue(InfoAskCatalog.needsReload(selectedChildId = "child-a", loadedForChildId = null, loaded = 0))
        // Same child, zero open questions: reloading is harmless and keeps the count fresh.
        assertTrue(InfoAskCatalog.needsReload(selectedChildId = "child-a", loadedForChildId = "child-a", loaded = 0))
        assertFalse(InfoAskCatalog.needsReload(selectedChildId = null, loadedForChildId = "child-a", loaded = 2))
    }
}
