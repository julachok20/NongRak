package com.lullabyai.app.ui.screens.settings

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordAgeLabelTest {
    private val now = Instant.parse("2026-09-17T10:00:00Z")

    @Test
    fun `counts whole days and says today for under a day`() {
        assertEquals("เปลี่ยนล่าสุดเมื่อวันนี้", passwordAgeLabel(now.minus(Duration.ofHours(23)), now, true))
        assertEquals("เปลี่ยนล่าสุดเมื่อ 45 วันที่แล้ว", passwordAgeLabel(now.minus(Duration.ofDays(45)), now, true))
        assertEquals("Last changed 1 day ago", passwordAgeLabel(now.minus(Duration.ofDays(1)), now, false))
    }

    @Test
    fun `a future timestamp from clock skew reads as today`() {
        assertEquals("Last changed today", passwordAgeLabel(now.plusSeconds(120), now, false))
    }
}
