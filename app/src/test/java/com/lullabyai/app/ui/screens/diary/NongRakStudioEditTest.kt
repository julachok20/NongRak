package com.lullabyai.app.ui.screens.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the one decision that can silently cost a parent their edit.
 *
 * [NongRakStudioEdit.changesAnything] is what stops the renderer re-encoding a clip to apply
 * nothing - a minute of waiting and a generation of quality for no change. Get it wrong in the
 * other direction and a real edit is skipped, บันทึก reports success, and the original file comes
 * back. That was the exact bug this whole feature exists to fix.
 */
class NongRakStudioEditTest {

    @Test
    fun `an untouched edit changes nothing`() {
        assertFalse(NongRakStudioEdit().changesAnything)
    }

    @Test
    fun `a caption counts as a change`() {
        val seeded = NongRakStudioEdit(
            captionText = "ก้าวแรก",
            stampStyle = NongRakStampStyle.PASTEL_CLOUD
        )
        assertTrue(seeded.changesAnything)
    }

    @Test
    fun `a blank caption is not a change`() {
        assertFalse(NongRakStudioEdit(captionText = "   ").changesAnything)
    }

    @Test
    fun `every control the editor offers registers as a change`() {
        val cases = listOf(
            "trim start" to NongRakStudioEdit(trimStartMs = 500),
            "trim end" to NongRakStudioEdit(trimEndMs = 4_000),
            "speed" to NongRakStudioEdit(speed = 2f),
            "effect" to NongRakStudioEdit(effect = NongRakVideoEffect.STARLIGHT),
            "filter" to NongRakStudioEdit(filter = NongRakVideoFilter.VINTAGE_FILM),
            "mascot" to NongRakStudioEdit(mascot = NongRakMascotSticker.NONGRAK),
            "aspect ratio" to NongRakStudioEdit(aspectRatio = StudioAspectRatio.SQUARE_1_1),
            "mute" to NongRakStudioEdit(keepOriginalAudio = false)
        )
        cases.forEach { (name, edit) ->
            assertTrue("$name should count as a change", edit.changesAnything)
        }
    }

    @Test
    fun `a filter and an effect compose instead of replacing each other`() {
        // They shared one field until the tool row split them, so choosing a tone silently threw
        // away the sparkles. The whole point of the split is that both survive.
        val both = NongRakStudioEdit(
            effect = NongRakVideoEffect.BLOSSOM,
            filter = NongRakVideoFilter.VINTAGE_FILM
        )
        assertEquals(NongRakVideoEffect.BLOSSOM, both.effect)
        assertEquals(NongRakVideoFilter.VINTAGE_FILM, both.filter)
        assertTrue(both.changesAnything)
    }

    @Test
    fun `choosing the original effect and normal speed is still no change`() {
        val neutral = NongRakStudioEdit(
            effect = NongRakVideoEffect.NONE,
            mascot = NongRakMascotSticker.NONE,
            speed = 1f,
            aspectRatio = null
        )
        assertFalse(neutral.changesAnything)
    }
}
