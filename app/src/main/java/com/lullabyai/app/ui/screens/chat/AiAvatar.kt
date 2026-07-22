package com.lullabyai.app.ui.screens.chat

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.lullabyai.app.ui.theme.Primary
import java.util.Locale

/**
 * "AI Avatar" — no external art assets available, so this is a simple branded Compose-drawn
 * mascot (a pulsing circle with an animated "mouth" arc) rather than an illustrated character.
 * Swap the draw logic for real animation/Lottie art later if commissioned — the [isSpeaking]
 * contract (drives the mouth-open animation) stays the same either way.
 */
@Composable
fun AiAvatarView(isSpeaking: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar")
    val pulse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isSpeaking) 260 else 1400), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Primary.copy(alpha = 0.15f), radius = r, center = center)
        drawCircle(color = Primary, radius = r * (0.72f + 0.06f * pulse), center = center)
        val mouthHeight = if (isSpeaking) r * 0.35f * pulse else r * 0.05f
        drawArc(
            color = androidx.compose.ui.graphics.Color.White,
            startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(center.x - r * 0.4f, center.y - mouthHeight / 2f + r * 0.15f),
            size = androidx.compose.ui.geometry.Size(r * 0.8f, mouthHeight.coerceAtLeast(4f)),
            style = Stroke(width = r * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

/** Thin wrapper around Android's built-in TextToSpeech (no new dependency/account needed). */
@Composable
fun rememberAiVoice(): AiVoice {
    val context = LocalContext.current
    val voice = remember { AiVoice(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    return voice
}

class AiVoice(context: Context) {
    private var tts: TextToSpeech? = null
    var isSpeaking by mutableStateOf(false)
        private set

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("th", "TH") // falls back to device default if unavailable
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) { isSpeaking = false }
                    override fun onError(utteranceId: String?) { isSpeaking = false }
                })
            }
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nongrak_ai_reply")
    }

    fun stop() { tts?.stop(); isSpeaking = false }
    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
