package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceLabelTest {

    @Test
    fun `our agent round-trips to a readable device name`() {
        val agent = DeviceLabel.userAgent(version = "1.0.3", manufacturer = "samsung", model = "SM-A546E", release = "16")
        assertEquals("NongRak/1.0.3 (samsung SM-A546E; Android 16)", agent)
        assertEquals("samsung SM-A546E • Android 16", DeviceLabel.parse(agent))
    }

    @Test
    fun `legacy and foreign agents are not guessed at`() {
        assertNull(DeviceLabel.parse(DeviceLabel.LEGACY_AGENT))
        assertNull(DeviceLabel.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))
        assertNull(DeviceLabel.parse(null))
    }
}
