package com.lullabyai.app.util

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Best-effort on-device cry / loud-sound heuristic using [AudioRecord] RMS energy.
 *
 * This is **not** a clinical cry classifier — phones vary wildly in mic gain, and we do not
 * run an ML model. Heuristic rules:
 * - Sustained elevated RMS (~≥ ~55–65 dBFS-ish) for ≥ ~800ms → `loud`
 * - Higher energy with a mid-band emphasis (approx. 300–3000 Hz via simple high-pass residual)
 *   for ≥ ~1.2s → `cry`
 * - Low-frequency periodic rumble bursts → `snore_like` (very rough)
 *
 * Emits at most one event every [cooldownMs] to avoid spam. Parent must grant RECORD_AUDIO.
 */
class CrySoundDetector(
    private val cooldownMs: Long = 12_000L
) {
    data class LiveLevel(
        val dbApprox: Float = 0f,
        val label: String = "quiet", // quiet | ambient | loud | cry_like
        val monitoring: Boolean = false
    )

    data class DetectedEvent(
        val type: String, // cry | loud | snore_like
        val peakDb: Float,
        val intensity: Float,
        val durationMs: Int
    )

    private val sampleRate = 16_000
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    @Volatile private var running = false

    private val _level = MutableStateFlow(LiveLevel())
    val level: StateFlow<LiveLevel> = _level

    private val _lastEvent = MutableStateFlow<DetectedEvent?>(null)
    val lastEvent: StateFlow<DetectedEvent?> = _lastEvent

    var onEvent: ((DetectedEvent) -> Unit)? = null

    fun start(): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val bufSize = (minBuf * 2).coerceAtLeast(sampleRate / 2)
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
        } catch (_: SecurityException) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }
        record = rec
        running = true
        rec.startRecording()
        worker = thread(start = true, name = "cry-sound-detector") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val buf = ShortArray(bufSize / 2)
            var loudMs = 0
            var cryMs = 0
            var rumbleMs = 0
            var lastEmit = 0L
            var hpPrev = 0f
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sumSq = 0.0
                var hpSumSq = 0.0
                var lowSumSq = 0.0
                for (i in 0 until n) {
                    val s = buf[i] / 32768f
                    sumSq += s * s
                    // One-pole high-pass residual ≈ mid/high energy (cry-ish).
                    val hp = s - hpPrev
                    hpPrev = hpPrev * 0.95f + s * 0.05f
                    hpSumSq += hp * hp
                    lowSumSq += hpPrev * hpPrev
                }
                val rms = sqrt(sumSq / n).toFloat().coerceAtLeast(1e-6f)
                val db = (20f * log10(rms)).coerceIn(-80f, 0f)
                // Map roughly to a "room dB" display (absolute calibration is device-dependent).
                val displayDb = (db + 90f).coerceIn(0f, 100f)
                val hpRms = sqrt(hpSumSq / n).toFloat()
                val lowRms = sqrt(lowSumSq / n).toFloat()
                val frameMs = (n * 1000f / sampleRate).toInt().coerceAtLeast(1)

                val elevated = displayDb >= 58f
                val cryish = elevated && hpRms > rms * 0.35f && displayDb >= 62f
                val rumble = displayDb in 48f..62f && lowRms > hpRms * 1.4f

                loudMs = if (elevated) loudMs + frameMs else 0
                cryMs = if (cryish) cryMs + frameMs else 0
                rumbleMs = if (rumble) rumbleMs + frameMs else 0

                val label = when {
                    cryMs >= 400 -> "cry_like"
                    loudMs >= 300 -> "loud"
                    displayDb >= 42f -> "ambient"
                    else -> "quiet"
                }
                _level.value = LiveLevel(dbApprox = displayDb, label = label, monitoring = true)

                val now = System.currentTimeMillis()
                if (now - lastEmit < cooldownMs) continue
                val event = when {
                    cryMs >= 1200 -> DetectedEvent("cry", displayDb, (displayDb / 100f).coerceIn(0f, 1f), cryMs)
                    loudMs >= 800 -> DetectedEvent("loud", displayDb, (displayDb / 100f).coerceIn(0f, 1f), loudMs)
                    rumbleMs >= 2500 -> DetectedEvent("snore_like", displayDb, (displayDb / 100f).coerceIn(0f, 1f), rumbleMs)
                    else -> null
                }
                if (event != null) {
                    lastEmit = now
                    loudMs = 0; cryMs = 0; rumbleMs = 0
                    _lastEvent.value = event
                    onEvent?.invoke(event)
                }
            }
            runCatching { rec.stop(); rec.release() }
            _level.value = LiveLevel(monitoring = false)
        }
        return true
    }

    fun stop() {
        running = false
        worker?.join(400)
        worker = null
        record = null
        _level.value = LiveLevel(monitoring = false)
    }

    fun release() = stop()
}
