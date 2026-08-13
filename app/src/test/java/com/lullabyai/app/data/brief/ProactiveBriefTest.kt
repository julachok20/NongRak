package com.lullabyai.app.data.brief

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * The wording is the only part of the brief that runs without a network round trip, and it is
 * the part a parent actually reads. These pin the unit boundaries and the past/future symmetry.
 */
class ProactiveBriefTest {

    private val now: Instant = Instant.parse("2026-08-12T09:00:00Z")

    @Test
    fun `relativeThai picks the right unit`() {
        assertEquals("ไม่ถึงนาที", relativeThai(now, now.plusSeconds(30)))
        assertEquals("30 นาที", relativeThai(now, now.plus(Duration.ofMinutes(30))))
        assertEquals("1 ชั่วโมง", relativeThai(now, now.plus(Duration.ofMinutes(60))))
        assertEquals("3 ชั่วโมง", relativeThai(now, now.plus(Duration.ofHours(3))))
        assertEquals("1 วัน", relativeThai(now, now.plus(Duration.ofHours(24))))
        assertEquals("2 วัน", relativeThai(now, now.plus(Duration.ofDays(2))))
    }

    @Test
    fun `relativeThai reads the same in both directions`() {
        val ahead = relativeThai(now, now.plus(Duration.ofMinutes(45)))
        val behind = relativeThai(now, now.minus(Duration.ofMinutes(45)))
        assertEquals(ahead, behind)
        assertEquals("45 นาที", ahead)
    }

    @Test
    fun `dayPhrase names today and tomorrow`() {
        assertEquals("วันนี้", dayPhrase(0))
        assertEquals("พรุ่งนี้", dayPhrase(1))
        assertEquals("อีก 5 วัน", dayPhrase(5))
    }

    @Test
    fun `greeting is used when nothing is pending`() {
        assertEquals(BriefKind.NONE, ProactiveBrief.GREETING.kind)
        assertEquals("สวัสดีค่ะ", ProactiveBrief.GREETING.headline)
    }
}
