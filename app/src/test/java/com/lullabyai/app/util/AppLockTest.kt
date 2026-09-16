package com.lullabyai.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    @Test
    fun `short trips to the background do not relock`() {
        assertFalse(AppLock.shouldRelock(backgroundedAt = 1_000L, now = 1_000L + AppLock.RELOCK_AFTER_MS - 1))
    }

    @Test
    fun `a minute or more in the background relocks`() {
        assertTrue(AppLock.shouldRelock(backgroundedAt = 1_000L, now = 1_000L + AppLock.RELOCK_AFTER_MS))
    }

    @Test
    fun `never backgrounded never relocks`() {
        assertFalse(AppLock.shouldRelock(backgroundedAt = null, now = Long.MAX_VALUE))
    }
}
