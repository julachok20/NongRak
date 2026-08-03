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
 * Professional multi-sound sleep library. [streamUrl] tracks are Mixkit ambient loops
 * (mixkit.co/license/ — free commercial use). Empty URL = always-on procedural synth so the
 * player never hard-fails offline. Verified titles were re-checked historically for RAIN/WOMB/
 * DEEP_SLEEP/RELAX; newer tracks prefer procedural fallbacks when a Mixkit ID is uncertain.
 */
enum class NoiseTrack(
    val labelTh: String,
    val labelEn: String,
    val streamUrl: String,
    val iconHint: String = "waves"
) {
    RAIN("เสียงฝน", "Rain", "https://assets.mixkit.co/active_storage/sfx/1225/1225-preview.mp3", "rainy"),
    WOMB("ในครรภ์", "In Womb", "https://assets.mixkit.co/active_storage/sfx/496/496-preview.mp3", "favorite"),
    DEEP_SLEEP("หลับสนิท", "Deep Sleep", "https://assets.mixkit.co/active_storage/sfx/3126/3126-preview.mp3", "water_drop"),
    RELAX("คลื่นทะเล", "Ocean", "https://assets.mixkit.co/active_storage/sfx/1196/1196-preview.mp3", "waves"),
    FAN("พัดลม", "Fan", "", "mode_fan"),
    WIND("สายลม", "Soft Wind", "", "air"),
    FIRE("เตาผิง", "Fireplace", "", "local_fire_department"),
    BIRDS("นกยามเช้า", "Birds", "", "flutter"),
    THUNDER("ฝนฟ้าคะนอง", "Thunder Rain", "", "thunderstorm"),
    LULLABY("เพลงกล่อมเด็ก", "Lullaby", "", "music_note")
}

/**
 * Streams ambient recordings and loops them. Offline / dead-link → procedural synth per track.
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

    @Volatile private var generation = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds
    private val _selectedTrack = MutableStateFlow(NoiseTrack.RAIN)
    val selectedTrack: StateFlow<NoiseTrack> = _selectedTrack
    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume
    private val _durationSeconds = MutableStateFlow(30 * 60)
    val durationSecondsFlow: StateFlow<Int> = _durationSeconds

    /** Auto-stops after this many seconds (sleep timer). */
    var durationSeconds: Int
        get() = _durationSeconds.value
        set(value) { _durationSeconds.value = value.coerceAtLeast(60) }

    fun selectTrack(t: NoiseTrack) {
        _selectedTrack.value = t
        if (running) {
            stopInternal()
            play()
        }
    }

    fun nextTrack() {
        val all = NoiseTrack.entries
        val i = all.indexOf(_selectedTrack.value)
        selectTrack(all[(i + 1) % all.size])
    }

    fun previousTrack() {
        val all = NoiseTrack.entries
        val i = all.indexOf(_selectedTrack.value)
        selectTrack(all[(i - 1 + all.size) % all.size])
    }

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        _volume.value = clamped
        mediaPlayer?.setVolume(clamped, clamped)
        audioTrack?.setVolume(clamped)
    }

    fun setTimerMinutes(minutes: Int) {
        durationSeconds = minutes.coerceIn(5, 120) * 60
        if (_elapsedSeconds.value >= durationSeconds) pause()
    }

    fun play() {
        if (running) return
        running = true
        usingFallback = false
        _elapsedSeconds.value = 0
        val myGen = ++generation
        startTicker()
        if (_selectedTrack.value.streamUrl.isBlank()) { startProceduralFallback(myGen); return }
        val vol = _volume.value
        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            setVolume(vol, vol)
            setOnPreparedListener {
                if (myGen == generation && running) {
                    it.setVolume(_volume.value, _volume.value)
                    it.start()
                    _isPlaying.value = true
                }
            }
            setOnErrorListener { _, _, _ -> if (myGen == generation && running) startProceduralFallback(myGen); true }
        }
        mediaPlayer = player
        runCatching {
            player.setDataSource(_selectedTrack.value.streamUrl)
            player.prepareAsync()
        }.onFailure { if (myGen == generation && running) startProceduralFallback(myGen) }
    }

    private fun startProceduralFallback(gen: Int) {
        if (gen != generation || !running) return
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
        player.setVolume(_volume.value)
        player.play()

        genThread = thread(start = true, name = "sleep-noise-gen") {
            var lowPassState = 0f
            var highPassPrevIn = 0f
            var highPassPrevOut = 0f
            var phase = 0.0
            var cracklePhase = 0.0
            val melody = floatArrayOf(
                261.63f, 261.63f, 392.00f, 392.00f, 440.00f, 440.00f, 392.00f, 0f,
                349.23f, 349.23f, 329.63f, 329.63f, 293.66f, 293.66f, 261.63f, 0f
            )
            val noteSamples = (sampleRate * 0.55).toInt()
            var melodyIndex = 0
            var melodySamplePos = 0
            var melodyPhase = 0.0
            val chunk = ShortArray(bufSize / 2)
            while (running && usingFallback && gen == generation) {
                val current = _selectedTrack.value
                val gain = _volume.value
                for (i in chunk.indices) {
                    val white = Random.nextFloat() * 2f - 1f
                    val sample: Float = when (current) {
                        NoiseTrack.RAIN, NoiseTrack.THUNDER -> {
                            val hp = 0.95f * (highPassPrevOut + white - highPassPrevIn)
                            highPassPrevIn = white; highPassPrevOut = hp
                            var out = hp * 0.5f
                            if (current == NoiseTrack.THUNDER && Random.nextFloat() < 0.0008f) {
                                out += (Random.nextFloat() * 2f - 1f) * 0.7f
                            }
                            out
                        }
                        NoiseTrack.WOMB -> {
                            lowPassState += 0.05f * (white - lowPassState)
                            val heartbeat = (sin(phase) * 0.5 + 0.5).toFloat()
                            lowPassState * (0.4f + 0.6f * heartbeat)
                        }
                        NoiseTrack.DEEP_SLEEP -> {
                            lowPassState += 0.03f * (white - lowPassState)
                            lowPassState * 0.6f
                        }
                        NoiseTrack.RELAX, NoiseTrack.WIND -> {
                            lowPassState += 0.08f * (white - lowPassState)
                            val swell = (sin(phase * 0.15) * 0.3 + 0.7).toFloat()
                            lowPassState * 0.5f * swell
                        }
                        NoiseTrack.FAN -> {
                            // Narrow-band whir + soft broadband hiss.
                            val whir = sin(phase * 45).toFloat() * 0.15f
                            lowPassState += 0.12f * (white - lowPassState)
                            whir + lowPassState * 0.35f
                        }
                        NoiseTrack.FIRE -> {
                            lowPassState += 0.2f * (white - lowPassState)
                            val crackle = if (Random.nextFloat() < 0.01f) white * 0.8f else lowPassState * 0.4f
                            cracklePhase += 0.01
                            crackle * (0.7f + 0.3f * sin(cracklePhase).toFloat())
                        }
                        NoiseTrack.BIRDS -> {
                            // Occasional soft chirp tones over quiet bed.
                            lowPassState += 0.02f * (white - lowPassState)
                            val chirp = if (Random.nextFloat() < 0.002f) {
                                sin(phase * (800 + Random.nextFloat() * 1200)).toFloat() * 0.25f
                            } else 0f
                            lowPassState * 0.15f + chirp
                        }
                        NoiseTrack.LULLABY -> {
                            val freq = melody[melodyIndex]
                            val note = if (freq <= 0f) 0f else {
                                val raw = sin(melodyPhase).toFloat()
                                val t = melodySamplePos.toFloat() / noteSamples
                                val env = when {
                                    t < 0.1f -> t / 0.1f
                                    t > 0.8f -> ((1f - t) / 0.2f).coerceAtLeast(0f)
                                    else -> 1f
                                }
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
                    chunk[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE * 0.35f * gain).toInt().toShort()
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
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        genThread?.join(200)
        genThread = null
        audioTrack = null
    }

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
