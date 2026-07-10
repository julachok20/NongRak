package com.lullabyai.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread
import kotlin.math.sin
import kotlin.random.Random

/**
 * [streamUrl] tracks are real ambient recordings from Mixkit (mixkit.co/license/ — free for
 * commercial use, no attribution or API key required, direct-linkable). The first pick (2026-07-04)
 * matched tracks by category name alone ("white-noise", "heartbeat", "ocean") without checking
 * actual titles — Mixkit's "white-noise" category turned out to be all sci-fi/glitch/radio-static
 * effects, "heartbeat" was almost entirely horror-movie jump-scare stingers, and the "ocean" pick
 * was a generic "wind, cold interior" track, not waves. Re-picked 2026-07-04 by pulling each
 * candidate's real <h2 class="item-grid-card__title"> text before choosing, not just its category.
 */
enum class NoiseTrack(val labelTh: String, val labelEn: String, val streamUrl: String) {
    RAIN("เสียงฝน", "Rain", "https://assets.mixkit.co/active_storage/sfx/1225/1225-preview.mp3"), // "Forest rain loop"
    WOMB("ในครรภ์", "In Womb", "https://assets.mixkit.co/active_storage/sfx/496/496-preview.mp3"), // "Inside a beating heart"
    DEEP_SLEEP("หลับสนิท", "Deep Sleep", "https://assets.mixkit.co/active_storage/sfx/3126/3126-preview.mp3"), // "Water flowing ambience loop"
    RELAX("ผ่อนคลาย", "Relax", "https://assets.mixkit.co/active_storage/sfx/1196/1196-preview.mp3"), // "Sea waves loop"
    // Deliberately no streamUrl: a real Mixkit lullaby track exists ("Baby toy music box lullaby")
    // but its direct asset URL isn't recoverable without a browser (the download link isn't in the
    // static page), and guessing a numeric sfx ID is exactly the mistake the note above already
    // warns about. Synthesized instead — see LULLABY branch in startProceduralFallback() — so this
    // track is always a real melody (a public-domain nursery tune), never a network guess.
    LULLABY("เพลงกล่อมเด็ก", "Lullaby", "")
}

/**
 * Streams real ambient recordings ([NoiseTrack.streamUrl]) and loops them. If the stream can't be
 * reached (offline, blocked network, dead link) it automatically falls back to the original
 * procedurally-synthesized noise so sleep sounds still work without internet — a baby-sleep
 * feature that hard-fails with no signal would be worse than a rougher on-device substitute.
 */
class WhiteNoisePlayer {
    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var genThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var usingFallback = false

    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds
    private val _selectedTrack = MutableStateFlow(NoiseTrack.RAIN)
    val selectedTrack: StateFlow<NoiseTrack> = _selectedTrack

    /** Auto-stops after this many seconds (mockup showed a 30-minute sleep-timer style duration). */
    var durationSeconds: Int = 30 * 60

    fun selectTrack(t: NoiseTrack) {
        _selectedTrack.value = t
        if (running) {
            stopInternal()
            play()
        }
    }

    fun play() {
        if (running) return
        running = true
        usingFallback = false
        startTicker()
        if (_selectedTrack.value.streamUrl.isBlank()) { startProceduralFallback(); return }
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setOnPreparedListener { it.start(); _isPlaying.value = true }
            setOnErrorListener { _, _, _ -> startProceduralFallback(); true }
        }
        mediaPlayer = player
        runCatching {
            player.setDataSource(_selectedTrack.value.streamUrl)
            player.prepareAsync()
        }.onFailure { startProceduralFallback() }
    }

    /** Real internet track unreachable — synthesize the same track live instead of failing silently. */
    private fun startProceduralFallback() {
        if (usingFallback) return
        usingFallback = true
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        _isPlaying.value = true

        val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = (minBuf * 2).coerceAtLeast(4096)
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = player
        player.play()

        genThread = thread(start = true, name = "sleep-noise-gen") {
            var lowPassState = 0f
            var highPassPrevIn = 0f
            var highPassPrevOut = 0f
            var phase = 0.0
            // "Ah! vous dirai-je, Maman" (Twinkle Twinkle Little Star) opening phrase — 18th-century
            // French folk melody, public domain. Rests (0f) are silent beats between phrases.
            val melody = floatArrayOf(
                261.63f, 261.63f, 392.00f, 392.00f, 440.00f, 440.00f, 392.00f, 0f,
                349.23f, 349.23f, 329.63f, 329.63f, 293.66f, 293.66f, 261.63f, 0f
            )
            val noteSamples = (sampleRate * 0.55).toInt()
            var melodyIndex = 0
            var melodySamplePos = 0
            var melodyPhase = 0.0
            val chunk = ShortArray(bufSize / 2)
            while (running && usingFallback) {
                val current = _selectedTrack.value
                for (i in chunk.indices) {
                    val white = Random.nextFloat() * 2f - 1f
                    val sample: Float = when (current) {
                        NoiseTrack.RAIN -> {
                            // One-pole high-pass: cuts rumble, keeps hiss — reads as rainfall.
                            val hp = 0.95f * (highPassPrevOut + white - highPassPrevIn)
                            highPassPrevIn = white; highPassPrevOut = hp
                            hp * 0.5f
                        }
                        NoiseTrack.WOMB -> {
                            // Low-pass rumble + slow amplitude pulse ~= heartbeat/whoosh.
                            lowPassState += 0.05f * (white - lowPassState)
                            val heartbeat = (sin(phase) * 0.5 + 0.5).toFloat()
                            lowPassState * (0.4f + 0.6f * heartbeat)
                        }
                        NoiseTrack.DEEP_SLEEP -> {
                            lowPassState += 0.03f * (white - lowPassState)
                            lowPassState * 0.6f
                        }
                        NoiseTrack.RELAX -> {
                            lowPassState += 0.08f * (white - lowPassState)
                            lowPassState * 0.5f
                        }
                        NoiseTrack.LULLABY -> {
                            val freq = melody[melodyIndex]
                            val note = if (freq <= 0f) 0f else {
                                val raw = sin(melodyPhase).toFloat()
                                // Soft attack/release envelope per note so notes don't click.
                                val t = melodySamplePos.toFloat() / noteSamples
                                val env = when { t < 0.1f -> t / 0.1f; t > 0.8f -> ((1f - t) / 0.2f).coerceAtLeast(0f); else -> 1f }
                                raw * env * 0.35f
                            }
                            melodyPhase += 2 * Math.PI * freq / sampleRate
                            if (melodyPhase > 2 * Math.PI) melodyPhase -= 2 * Math.PI
                            melodySamplePos++
                            if (melodySamplePos >= noteSamples) {
                                melodySamplePos = 0
                                melodyIndex = (melodyIndex + 1) % melody.size
                            }
                            note
                        }
                    }
                    chunk[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.3f).toInt().toShort()
                    phase += 2 * Math.PI * 1.2 / sampleRate
                    if (phase > 2 * Math.PI) phase -= 2 * Math.PI
                }
                player.write(chunk, 0, chunk.size)
            }
            runCatching { player.stop(); player.release() }
        }
    }

    private fun startTicker() {
        stopTicker()
        val r = object : Runnable {
            override fun run() {
                if (!running) return
                _elapsedSeconds.value += 1
                if (_elapsedSeconds.value >= durationSeconds) {
                    pause()
                    return
                }
                handler.postDelayed(this, 1000)
            }
        }
        tickRunnable = r
        handler.postDelayed(r, 1000)
    }

    private fun stopTicker() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun stopInternal() {
        running = false
        usingFallback = false
        stopTicker()
        runCatching { mediaPlayer?.stop(); mediaPlayer?.release() }
        mediaPlayer = null
        genThread?.join(200)
        genThread = null
        audioTrack = null
    }

    /** There's nothing to "resume" mid-position for the fallback synth; a real stream just re-buffers
     *  from the start on the next play() since these are short looping ambience tracks, not narration.
     *  Fully releases the player (rather than MediaPlayer.pause()) so play() never leaks a stale instance. */
    fun pause() {
        stopInternal()
        _isPlaying.value = false
    }

    fun release() {
        stopInternal()
        _isPlaying.value = false
        _elapsedSeconds.value = 0
    }
}
