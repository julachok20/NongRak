package com.lullabyai.app.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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

    private data class Utterance(val text: String, val isThai: Boolean, val rate: Float, val pitch: Float)

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
    fun speak(text: String, isThai: Boolean, rate: Float = 1f, pitch: Float = 1f) {
        if (text.isBlank()) return
        val u = Utterance(text, isThai, rate, pitch)
        if (!ready) { pending = u; return }
        enqueue(u)
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
        runCatching { engine?.stop() }
        isSpeaking = false
    }

    fun shutdown() {
        pending = null
        isSpeaking = false
        runCatching { engine?.stop() }
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
