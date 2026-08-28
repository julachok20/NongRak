package com.lullabyai.app.ui.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Keeps the app's idea of a level identical to the database's.
 *
 * Postgres owns what level a companion IS (`mascot_level`), but the closet needs to know how far
 * the next one is to draw a progress bar, and asking the server per frame is absurd. Two formulas
 * for one number is exactly the kind of drift that ends with a bar at 90% and a locked item, so
 * this pins them together.
 */
class MascotWardrobeTest {

    /** Verbatim port of Postgres: floor(sqrt(greatest(xp,0) / 25)) + 1, floored at 1. */
    private fun postgresMascotLevel(xp: Int): Int =
        maxOf(1, floor(sqrt(maxOf(xp, 0) / 25.0)).toInt() + 1)

    @Test
    fun `xpForLevel is the exact threshold postgres uses`() {
        for (level in 1..25) {
            val threshold = MascotWardrobe.xpForLevel(level)

            assertEquals(
                "xp $threshold should be exactly level $level",
                level,
                postgresMascotLevel(threshold),
            )

            // And one xp short must still be the level below — otherwise the app would show an
            // item as unlocked a moment before the server agrees.
            if (level > 1) {
                assertEquals(
                    "xp ${threshold - 1} should still be level ${level - 1}",
                    level - 1,
                    postgresMascotLevel(threshold - 1),
                )
            }
        }
    }

    @Test
    fun `every xp value agrees with postgres`() {
        // Walk the range a real bond can reach and check the app never disagrees about which
        // level an xp total sits in.
        for (xp in 0..5_000) {
            val level = postgresMascotLevel(xp)
            assertTrue(
                "xp $xp is level $level, but xpForLevel($level) = ${MascotWardrobe.xpForLevel(level)} is above it",
                MascotWardrobe.xpForLevel(level) <= xp,
            )
            assertTrue(
                "xp $xp is level $level, but xpForLevel(${level + 1}) = ${MascotWardrobe.xpForLevel(level + 1)} is not above it",
                MascotWardrobe.xpForLevel(level + 1) > xp,
            )
        }
    }

    @Test
    fun `progress runs zero to one across a level and never escapes`() {
        for (level in 1..10) {
            val floorXp = MascotWardrobe.xpForLevel(level)
            val ceilXp = MascotWardrobe.xpForLevel(level + 1)

            assertEquals(0f, MascotWardrobe.progressWithinLevel(floorXp, level), 0.0001f)
            assertTrue(MascotWardrobe.progressWithinLevel(ceilXp - 1, level) < 1f)

            // Clamped, so a stale level from a screen that has not refreshed cannot draw a bar
            // off the end of its track.
            assertEquals(1f, MascotWardrobe.progressWithinLevel(ceilXp + 9_999, level), 0.0001f)
            assertEquals(0f, MascotWardrobe.progressWithinLevel(0, level), 0.0001f)
        }
    }

    @Test
    fun `garments drop anything not earned or not drawn`() {
        // The catalogue is empty until the garment art exists, so every id is unknown — which is
        // the case that must not crash: a save naming an item this build does not have.
        val worn = mapOf("shirt" to "tee_that_does_not_exist")

        for (skin in MascotArt.entries) {
            assertTrue(
                "unknown item should resolve to nothing for $skin",
                MascotWardrobe.garmentsFor(skin, worn, level = 99).isEmpty(),
            )
        }
        assertTrue(MascotWardrobe.garmentsFor(MascotArt.NONGRAK, emptyMap(), level = 1).isEmpty())
    }

    @Test
    fun `companion unlocks are gated by the best bond, not their own`() {
        // Gating a companion behind its OWN level would lock every one of them at 1 forever,
        // because an unraised companion is always level 1.
        assertTrue(MascotWardrobe.skinUnlocked(MascotArt.NONGRAK, bestLevel = 1))
        assertTrue(!MascotWardrobe.skinUnlocked(MascotArt.BUNNY, bestLevel = 1))
        assertTrue(MascotWardrobe.skinUnlocked(MascotArt.BUNNY, bestLevel = 2))
        assertTrue(!MascotWardrobe.skinUnlocked(MascotArt.PINGU, bestLevel = 3))
        assertTrue(MascotWardrobe.skinUnlocked(MascotArt.PINGU, bestLevel = 4))

        // The first companion must never be locked, or a new carer has none at all.
        assertEquals(1, MascotWardrobe.skinRequiredLevel(MascotArt.NONGRAK))
    }
}
