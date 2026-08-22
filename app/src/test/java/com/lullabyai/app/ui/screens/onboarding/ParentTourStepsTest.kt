package com.lullabyai.app.ui.screens.onboarding

import com.lullabyai.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nine-step tour failed because most steps pointed at nothing and none asked for an action.
 * These pin the shape that fixed it — a step with no [TourStepInfo.targetKey] is the old bug
 * coming back, since the shell can only draw a spotlight (and only pass a tap through) when a
 * step names a real anchor.
 */
class ParentTourStepsTest {

    @Test
    fun `tour is three steps`() {
        assertEquals(3, ParentTourSteps.steps.size)
    }

    @Test
    fun `every step spotlights a real anchor`() {
        ParentTourSteps.steps.forEachIndexed { i, s ->
            assertTrue("step $i has no targetKey — it would dim the screen and point at nothing", !s.targetKey.isNullOrBlank())
        }
    }

    @Test
    fun `every step runs on the dashboard so the anchors stay on screen`() {
        ParentTourSteps.steps.forEach { assertEquals(Screen.Dashboard.route, it.route) }
    }

    @Test
    fun `every step has copy in both languages`() {
        ParentTourSteps.steps.forEachIndexed { i, s ->
            assertTrue("step $i missing Thai copy", s.titleTh.isNotBlank() && s.bodyTh.isNotBlank())
            assertTrue("step $i missing English copy", s.titleEn.isNotBlank() && s.bodyEn.isNotBlank())
        }
    }
}
