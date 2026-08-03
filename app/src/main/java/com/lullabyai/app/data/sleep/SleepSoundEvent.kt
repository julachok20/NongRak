package com.lullabyai.app.data.sleep

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-device cry/loud-sound detection event persisted to `sleep_sound_events`.
 * Heuristic only — not a medical cry classifier.
 */
@Serializable
data class SleepSoundEvent(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("sleep_log_id") val sleepLogId: String? = null,
    /** cry | loud | snore_like */
    @SerialName("event_type") val eventType: String = "cry",
    val intensity: Float? = null,
    @SerialName("peak_db") val peakDb: Float? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("detected_at") val detectedAt: String = "",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)
