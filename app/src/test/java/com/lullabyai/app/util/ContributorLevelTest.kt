package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile badge. It is a claim other parents read as authority, so these pin the two things
 * that matter: it is earned from real activity, and it never implies medical standing.
 */
class ContributorLevelTest {

    @Test
    fun `a new account gets no badge at all`() {
        assertEquals(0, ContributorLevel.level(posts = 0, likesReceived = 0, answersGiven = 0))
        assertNull(ContributorLevel.label(0, isThai = true))
        assertNull(ContributorLevel.label(0, isThai = false))
    }

    @Test
    fun `answering someone else counts double posting`() {
        // Helping is the behaviour worth encouraging, and it is harder to farm.
        val fromPosts = ContributorLevel.points(posts = 10, likesReceived = 0, answersGiven = 0)
        val fromAnswers = ContributorLevel.points(posts = 0, likesReceived = 0, answersGiven = 5)
        assertEquals(fromPosts, fromAnswers)
    }

    @Test
    fun `levels climb through the published thresholds`() {
        assertEquals(0, ContributorLevel.level(4))
        assertEquals(1, ContributorLevel.level(5))
        assertEquals(2, ContributorLevel.level(25))
        assertEquals(3, ContributorLevel.level(75))
        assertEquals(4, ContributorLevel.level(200))
        assertEquals(5, ContributorLevel.level(500))
        assertEquals(5, ContributorLevel.level(5000))
    }

    @Test
    fun `top contributor stays uncommon`() {
        assertFalse(ContributorLevel.isTopContributor(3))
        assertTrue(ContributorLevel.isTopContributor(4))
        assertTrue(ContributorLevel.isTopContributor(5))
    }

    @Test
    fun `every badge carries the not-a-doctor caveat`() {
        // The whole reason the badge is safe to show at all.
        val th = ContributorLevel.caveat(isThai = true)
        assertTrue(th, th.contains("ไม่ใช่การรับรองทางการแพทย์"))

        val en = ContributorLevel.caveat(isThai = false).lowercase()
        assertTrue(en, en.contains("not a medical qualification"))
    }

    @Test
    fun `no badge text ever claims medical standing`() {
        (1..ContributorLevel.MAX_LEVEL).forEach { level ->
            val th = ContributorLevel.label(level, isThai = true)
            val en = ContributorLevel.label(level, isThai = false)
            assertNotNull("level $level should have a label", th)
            listOf("แพทย์", "หมอ", "พยาบาล", "ผู้เชี่ยวชาญ").forEach { word ->
                assertFalse("level $level must not say $word", th.orEmpty().contains(word))
            }
            listOf("doctor", "nurse", "expert", "specialist", "verified").forEach { word ->
                assertFalse("level $level must not say $word", en.orEmpty().lowercase().contains(word))
            }
        }
    }

    @Test
    fun `the next level is reachable until the top`() {
        assertEquals(5, ContributorLevel.pointsToNextLevel(0))
        assertEquals(20, ContributorLevel.pointsToNextLevel(5))
        assertNull(ContributorLevel.pointsToNextLevel(500))
    }

    @Test
    fun `negative counts cannot drag a level below zero`() {
        // Defensive: counts arrive from the UI, and a bad subtraction upstream must not underflow.
        assertEquals(0, ContributorLevel.points(posts = -5, likesReceived = -5, answersGiven = -5))
        assertEquals(0, ContributorLevel.level(posts = -5, likesReceived = -5, answersGiven = -5))
    }
}
