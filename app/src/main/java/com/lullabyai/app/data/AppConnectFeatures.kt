package com.lullabyai.app.data

/**
 * App Connect (health apps / wearables / child IoT) readiness gate.
 *
 * Flip to `true` only when all of the following are verified on a real device:
 * - Supabase `wearable_connections` + `device_readings` tables live with RLS
 * - Health Connect sync end-to-end (permissions → readings → AI context)
 * - IoT path is real hardware or clearly labelled manual-only (no fake seed snapshots)
 */
object AppConnectFeatures {
    const val IS_READY = false
}
