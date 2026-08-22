package com.lullabyai.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityDistanceTest {

    @Test
    fun `distance between same coordinate is zero`() {
        val dist = ActivityRecommender.calculateDistanceKm(13.7563, 100.5018, 13.7563, 100.5018)
        assertEquals(0.0, dist, 0.01)
    }

    @Test
    fun `distance calculation between Bangkok landmarks is accurate`() {
        // Siam Paragon (13.7460, 100.5349) to Safari World (13.8654, 100.7042) is ~23-25 km
        val dist = ActivityRecommender.calculateDistanceKm(13.7460, 100.5349, 13.8654, 100.7042)
        assertTrue("Distance should be between 20 and 26 km, got $dist", dist in 20.0..26.0)
    }

    @Test
    fun `distance calculation within 15-30 km range is correctly measured`() {
        // CentralWorld (13.7466, 100.5393) to Mega Bangna (13.6469, 100.6802) is ~18 km
        val dist = ActivityRecommender.calculateDistanceKm(13.7466, 100.5393, 13.6469, 100.6802)
        assertTrue("Distance should be within 15-22 km, got $dist", dist in 15.0..22.0)
    }
}
