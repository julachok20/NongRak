package com.lullabyai.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.lullabyai.app.data.remote.AiProxyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val THAI = Locale("th", "TH")
private const val UTTERANCE_ID = "nongrak_ai_reply"

/**
 * Platform [TextToSpeech] wrapper — no new dependency, works offline, free.
 *
 * [isSpeaking] is what drives the avatar's mouth (see AiAvatar.kt); Android TTS gives no phoneme
 * data, only start/done, so the mouth animates on a loop while this is true.
 *
 * Thai voice: [thaiVoiceAvailable] is false when the device has no Thai voice data installed. In
 * that case Thai text is NOT spoken at all — an English engine reading Thai produces noise, which
 * is worse than silence — and the UI shows a "install Thai voice in Settings" hint instead.
 */
class TtsSpeaker(context: Context) {

    var isSpeaking by mutableStateOf(false)
        private set

    /** false → device has no Thai voice data; Thai replies are skipped (see class doc). */
    var thaiVoiceAvailable by mutableStateOf(true)
        private set

    /** true once Gemini has actually spoken — the missing-Thai-voice warning is moot then. */
    var cloudVoiceReady by mutableStateOf(false)
        private set

    private data class Utterance(val text: String, val isThai: Boolean, val rate: Float, val pitch: Float)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var speechJob: Job? = null
    private var track: AudioTrack? = null

    private var engine: TextToSpeech? = null
    private var ready = false
    // TextToSpeech init is async: a reply arriving in the first ~second would otherwise be dropped
    // silently, which reads as "the voice is broken".
    private var pending: Utterance? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            val e = engine
            if (status != TextToSpeech.SUCCESS || e == null) return@TextToSpeech
            thaiVoiceAvailable = runCatching { e.isLanguageAvailable(THAI) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { isSpeaking = true }
                override fun onDone(utteranceId: String?) { isSpeaking = false }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { isSpeaking = false }
                @Deprecated("platform abstract override", ReplaceWith("onError(utteranceId, errorCode)"))
                override fun onError(utteranceId: String?) { isSpeaking = false }
                override fun onError(utteranceId: String?, errorCode: Int) { isSpeaking = false }
            })
            ready = true
            pending?.let { pending = null; enqueue(it) }
        }
    }

    /**
     * Speaks [text] in Thai or English per the caller's `LocalStrings.isThai`.
     * [rate]/[pitch] carry the mode's speech style (slower for storytelling, brighter for a child).
     */
    fun speak(text: String, isThai: Boolean, rate: Float = 1f, pitch: Float = 1f, style: String? = null) {
        if (text.isBlank()) return
        val u = Utterance(text, isThai, rate, pitch)
        stop()
        // Gemini first — it speaks Thai properly and does not care whether the device has Thai
        // voice data installed. The platform engine stays as the offline/failure path.
        speechJob = scope.launch {
            val speech = AiProxyClient.synthesize(text, style = style)
            if (speech == null || speech.pcm.isEmpty()) {
                cloudVoiceReady = false
                if (!ready) { pending = u; return@launch }
                enqueue(u)
                return@launch
            }
            cloudVoiceReady = true
            isSpeaking = true
            runCatching { play(speech, u.rate) }
            isSpeaking = false
        }
    }

    /** Streams the PCM straight out; [speed] carries the mode's pacing (storytelling is slower). */
    private suspend fun play(speech: AiProxyClient.Speech, speed: Float) = withContext(Dispatchers.IO) {
        val minBuffer = AudioTrack.getMinBufferSize(
            speech.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(8192)
        val audio = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(speech.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audio
        try {
            if (speed != 1f) {
                runCatching { audio.playbackParams = audio.playbackParams.setSpeed(speed) }
            }
            audio.play()
            var offset = 0
            while (offset < speech.pcm.size && isActive) {
                val written = audio.write(speech.pcm, offset, minOf(minBuffer, speech.pcm.size - offset))
                if (written <= 0) break
                offset += written
            }
            // write() returns once the data is queued, not once it is heard — wait for the tail
            // so isSpeaking (and the avatar's mouth) stays true until the voice actually stops.
            val totalFrames = speech.pcm.size / 2
            while (isActive && audio.playbackHeadPosition < totalFrames) delay(40)
        } finally {
            runCatching { audio.stop() }
            runCatching { audio.release() }
            if (track === audio) track = null
        }
    }

    private fun enqueue(u: Utterance) {
        val e = engine ?: return
        if (u.isThai && !thaiVoiceAvailable) return  // see class doc — silence beats gibberish
        e.language = if (u.isThai) THAI else Locale.US
        e.setSpeechRate(u.rate)
        e.setPitch(u.pitch)
        val ok = e.speak(u.text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (ok != TextToSpeech.SUCCESS) isSpeaking = false
    }

    fun stop() {
        pending = null
        speechJob?.cancel()
        speechJob = null
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { engine?.stop() }
        isSpeaking = false
    }

    fun shutdown() {
        stop()
        scope.cancel()
        runCatching { track?.release() }
        track = null
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }
}

/** Owns one engine per screen and releases it on leave — an un-shutdown engine leaks the service. */
@Composable
fun rememberTtsSpeaker(): TtsSpeaker {
    val context = LocalContext.current
    val speaker = remember { TtsSpeaker(context) }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    return speaker
}
