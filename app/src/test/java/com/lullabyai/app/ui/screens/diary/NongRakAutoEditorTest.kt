package com.lullabyai.app.ui.screens.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cut is decided on the phone, so this is where a wrong answer actually lands on a parent.
 *
 * Everything tested here is pure: the Android side of the analyser reduces a clip to two float
 * arrays, and from there the choice of which seconds to keep is ordinary arithmetic that can be
 * checked without a device.
 */
class NongRakAutoEditorTest {

    @Test
    fun `picks the loudest run rather than the loudest instant`() {
        // One spike at the start, a sustained stretch at the end. A parent wants the stretch - the
        // spike is a door closing.
        val scores = floatArrayOf(1f, 0f, 0f, 0.7f, 0.8f, 0.9f)
        assertEquals(3, NongRakAutoEditor.pickWindow(scores, 3))
    }

    @Test
    fun `a window as long as the clip starts at the beginning`() {
        val scores = floatArrayOf(0.1f, 0.9f, 0.2f)
        assertEquals(0, NongRakAutoEditor.pickWindow(scores, 3))
        assertEquals(0, NongRakAutoEditor.pickWindow(scores, 9))
    }

    @Test
    fun `an empty clip does not crash the picker`() {
        assertEquals(0, NongRakAutoEditor.pickWindow(FloatArray(0), 3))
    }

    @Test
    fun `sound outweighs movement`() {
        // Bucket 0 is loud and still; bucket 1 is silent and shaky. The laugh should win, because
        // a pan across an empty room is not the memory.
        val combined = NongRakAutoEditor.combine(
            audio = floatArrayOf(1f, 0f),
            motion = floatArrayOf(0f, 1f)
        )
        assertTrue("a still laugh should outrank a silent pan", combined[0] > combined[1])
    }

    @Test
    fun `a silent clip falls back to movement alone`() {
        val motion = floatArrayOf(0.2f, 0.9f)
        assertTrue(NongRakAutoEditor.combine(audio = null, motion = motion).contentEquals(motion))
    }

    @Test
    fun `normalise scales to the clip's own peak`() {
        val scaled = NongRakAutoEditor.normalise(floatArrayOf(2f, 4f, 8f))
        assertEquals(1f, scaled[2], 0.0001f)
        assertEquals(0.5f, scaled[1], 0.0001f)
    }

    @Test
    fun `a flat signal normalises to zero rather than dividing by it`() {
        val scaled = NongRakAutoEditor.normalise(floatArrayOf(0f, 0f))
        assertTrue(scaled.all { it == 0f })
    }

    @Test
    fun `each corner of the mood square picks a different preset`() {
        val lively = NongRakAutoEditor.moodFor(audio = 0.9f, motion = 0.9f)
        val moment = NongRakAutoEditor.moodFor(audio = 0.9f, motion = 0.1f)
        val quietPlay = NongRakAutoEditor.moodFor(audio = 0.1f, motion = 0.9f)
        val calm = NongRakAutoEditor.moodFor(audio = 0.1f, motion = 0.1f)

        assertEquals(NongRakVideoEffect.STARLIGHT, lively.effect)
        assertEquals(NongRakVideoEffect.GOLDEN_CROWN, moment.effect)
        assertEquals(NongRakVideoEffect.BUBBLES, quietPlay.effect)
        assertEquals(NongRakVideoEffect.BLOSSOM, calm.effect)

        // The calm corner is the one that must never arrive with sparkles on it.
        assertEquals(NongRakVideoFilter.SOFT_PASTEL, calm.filter)
    }
}
