package com.lullabyai.app.data

import com.lullabyai.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * บริการแนะนำ on the home page. The failure modes worth pinning: suggesting something useless for
 * the child's age (a postpartum check-in to a toddler's parent, solid-food tools for a newborn),
 * reshuffling under the user's finger, and repeating what is already on the page.
 */
class QuickActionCatalogRecommendTest {

    private fun pick(
        age: Int?,
        exclude: Set<String> = emptySet(),
        seed: Long = 42L,
        count: Int = 4
    ): List<String> = QuickActionCatalog.recommendForHome(age, exclude, seed, count).map { it.route }

    @Test
    fun `returns the requested number of distinct routes`() {
        val routes = pick(18)
        assertEquals(4, routes.size)
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `same seed gives the same pick so recomposition cannot reshuffle`() {
        assertEquals(pick(18, seed = 7L), pick(18, seed = 7L))
    }

    @Test
    fun `different seeds eventually give a different pick`() {
        val first = pick(18, seed = 1L)
        assertTrue((2L..30L).any { pick(18, seed = it) != first })
    }

    @Test
    fun `utilities are never recommended`() {
        val banned = setOf(
            Screen.Settings.route,
            Screen.Notifications.route,
            Screen.WidgetsIntro.route,
            Screen.PermissionAccess.route
        )
        (1L..60L).forEach { seed ->
            assertTrue(pick(18, seed = seed, count = 10).none { it in banned })
        }
    }

    @Test
    fun `postpartum check-in is not suggested for a toddler`() {
        (1L..80L).forEach { seed ->
            assertFalse(Screen.MentalHealthAssessment.route in pick(30, seed = seed, count = 10))
        }
    }

    @Test
    fun `solid food tools are not suggested for a newborn`() {
        (1L..80L).forEach { seed ->
            assertFalse(Screen.CalorieCalculator.route in pick(2, seed = seed, count = 10))
        }
    }

    @Test
    fun `unknown age filters nothing by age`() {
        // A blank birthdate must not be read as "newborn" - age-gated destinations stay possible.
        val seen = (1L..200L).flatMap { pick(null, seed = it, count = 10) }.toSet()
        assertTrue(Screen.MentalHealthAssessment.route in seen)
        assertTrue(Screen.CalorieCalculator.route in seen)
    }

    @Test
    fun `routes already on the page are skipped while alternatives exist`() {
        val onPage = setOf(
            Screen.VaccineBook.route,
            Screen.GrowthHistory.route,
            Screen.ChildDevelopment.route,
            Screen.ChildHealth.route
        )
        (1L..60L).forEach { seed ->
            assertTrue(pick(18, exclude = onPage, seed = seed).none { it in onPage })
        }
    }

    @Test
    fun `row is back-filled rather than left short`() {
        val everything = QuickActionCatalog.all.map { it.route }.toSet()
        assertEquals(4, pick(18, exclude = everything).size)
    }
}
