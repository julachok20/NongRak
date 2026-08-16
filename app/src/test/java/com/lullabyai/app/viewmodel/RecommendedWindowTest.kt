package com.lullabyai.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * What the two time pickers open on when a suggestion is scheduled. The value only has to be
 * sensible — the parent may change both — but it must never open on a moment that is already gone,
 * because that turns one tap into an error message.
 */
class RecommendedWindowTest {

    private val zone: ZoneId = ZoneId.of("Asia/Bangkok")

    /** 14:00 in Bangkok. */
    private val now: OffsetDateTime =
        OffsetDateTime.of(2026, 11, 14, 14, 0, 0, 0, ZoneOffset.ofHours(7))

    private val today: LocalDate = LocalDate.of(2026, 11, 14)
    private val tomorrow: LocalDate = today.plusDays(1)

    @Test
    fun `the model's hour and length are used as given`() {
        val (start, end) = recommendedWindow(18, 3, tomorrow, now, zone)
        assertEquals(LocalTime.of(18, 0), start)
        assertEquals(LocalTime.of(21, 0), end)
    }

    @Test
    fun `no suggestion falls back to a two-hour late morning`() {
        val (start, end) = recommendedWindow(null, null, tomorrow, now, zone)
        assertEquals(LocalTime.of(10, 0), start)
        assertEquals(LocalTime.of(12, 0), end)
    }

    @Test
    fun `an hour already gone today slides to the next whole hour`() {
        // 10:00 has passed at 14:00, so opening the picker there would only produce an error.
        val (start, end) = recommendedWindow(10, 2, today, now, zone)
        assertEquals(LocalTime.of(15, 0), start)
        assertEquals(LocalTime.of(17, 0), end)
    }

    @Test
    fun `a later hour today is left alone`() {
        val (start, _) = recommendedWindow(18, 2, today, now, zone)
        assertEquals(LocalTime.of(18, 0), start)
    }

    @Test
    fun `the same hour tomorrow is not slid forward`() {
        val (start, _) = recommendedWindow(10, 2, tomorrow, now, zone)
        assertEquals(LocalTime.of(10, 0), start)
    }

    @Test
    fun `late at night the end never rolls into the next day`() {
        val lateEvening = OffsetDateTime.of(2026, 11, 14, 23, 40, 0, 0, ZoneOffset.ofHours(7))
        val (start, end) = recommendedWindow(21, 4, today, now = lateEvening, zone = zone)
        // No whole hour is left today, so the pair stays inside the day and the save refuses it
        // with a message — rather than wrapping to 00:xx, which has also passed.
        assertTrue(end.isAfter(start))
        assertTrue(end.isBefore(LocalTime.of(23, 59)))
    }

    @Test
    fun `an impossible hour from the model is pulled back into the day`() {
        val (start, end) = recommendedWindow(30, 99, tomorrow, now, zone)
        assertTrue(start.hour in 6..21)
        assertTrue(end.isAfter(start))
    }
}
