package com.lullabyai.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reactions on a community post.
 *
 * The wire values are checked by a database constraint, so a typo here is a write that fails in
 * production rather than at compile time. These pin them, and pin the two product decisions:
 * four faces, and no angry one.
 */
class ReactionTest {

    @Test
    fun `wire values match the database check constraint exactly`() {
        // community_post_likes_reaction_check: reaction in ('like', 'love', 'laugh', 'sad')
        assertEquals(
            listOf("like", "love", "laugh", "sad"),
            Reaction.entries.map { it.wire }
        )
    }

    @Test
    fun `every wire value round-trips`() {
        Reaction.entries.forEach { reaction ->
            assertEquals(reaction, Reaction.fromWire(reaction.wire))
        }
    }

    @Test
    fun `an unknown reaction reads as like rather than throwing`() {
        // A newer server adding a face must not crash an older app.
        assertEquals(Reaction.LIKE, Reaction.fromWire("angry"))
        assertEquals(Reaction.LIKE, Reaction.fromWire(null))
        assertEquals(Reaction.LIKE, Reaction.fromWire(""))
    }

    @Test
    fun `wire parsing tolerates case and padding`() {
        assertEquals(Reaction.LOVE, Reaction.fromWire(" LOVE "))
        assertEquals(Reaction.LAUGH, Reaction.fromWire("Laugh"))
    }

    @Test
    fun `there is no angry reaction`() {
        // Deliberate: a one-tap way to express anger at a parent asking about their child at 2am
        // is not a feature worth having.
        assertFalse(Reaction.entries.any { it.wire == "angry" })
        assertEquals(4, Reaction.entries.size)
    }

    @Test
    fun `every reaction has an emoji and both labels`() {
        Reaction.entries.forEach { reaction ->
            assertTrue(reaction.name, reaction.emoji.isNotBlank())
            assertTrue(reaction.name, reaction.label(isThai = true).isNotBlank())
            assertTrue(reaction.name, reaction.label(isThai = false).isNotBlank())
        }
    }
}
