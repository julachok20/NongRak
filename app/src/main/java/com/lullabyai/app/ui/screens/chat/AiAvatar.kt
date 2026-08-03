package com.lullabyai.app.ui.screens.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.lullabyai.app.R
import com.lullabyai.app.ui.theme.Primary
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * NongRak mascot avatar that reads as ALIVE: the mouth opens and closes while the AI speaks, the
 * eyes blink at random intervals, and the whole mascot breathes gently when silent.
 *
 * How: the mascot artwork stays [R.drawable.nongrak_mascot_hero] (always the same pose — the
 * animation is drawn on top of the art at fixed fractional coordinates, so swapping poses would
 * misalign it) and a Canvas paints over the printed mouth/eyes.
 *
 * Android TTS exposes only start/done, never phonemes, so the mouth runs a two-sine loop while
 * [isSpeaking] — deliberately a stylised cartoon flap, not lip-sync.
 *
 * ponytail: the constants below are the calibration knobs, measured from the 512x512 PNG
 * (mouth x 270..309 y 252..266; left eye ~(225,232); right eye ~(347,224)). Re-measure them if the
 * mascot art is ever re-cropped or replaced.
 */
private const val MOUTH_CX = 0.565f
private const val MOUTH_CY = 0.506f
private const val MOUTH_HALF_W = 0.042f
private const val MOUTH_MAX_HALF_H = 0.032f
private const val MOUTH_PATCH_RX = 0.060f
private const val MOUTH_PATCH_RY = 0.034f
private const val EYE_L_CX = 0.440f
private const val EYE_L_CY = 0.452f
private const val EYE_R_CX = 0.678f
private const val EYE_R_CY = 0.438f
private const val EYE_RX = 0.052f
private const val EYE_RY = 0.050f

private val FaceCream = Color(0xFFFBF6F1)
private val MouthDark = Color(0xFF49212C)
private val Tongue = Color(0xFFD1697A)

@Composable
fun AiAvatarView(isSpeaking: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "avatar")

    // Breathing: slow when idle, a touch quicker while talking.
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (isSpeaking) 1600 else 2600), RepeatMode.Reverse),
        label = "breath"
    )
    // Free-running phase for the mouth loop.
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )

    // Two sines at unrelated speeds so the flap doesn't look metronomic.
    val openAmount = if (!isSpeaking) 0f else {
        val a = (sin(2 * PI * 3.1 * phase).toFloat() + 1f) / 2f
        val b = (sin(2 * PI * 5.7 * phase).toFloat() + 1f) / 2f
        (0.12f + 0.88f * (0.7f * a + 0.3f * b)).coerceIn(0f, 1f)
    }

    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(Random.nextLong(2200, 5200))
            blinking = true
            kotlinx.coroutines.delay(110)
            blinking = false
            if (Random.nextInt(4) == 0) {           // occasional double blink
                kotlinx.coroutines.delay(120)
                blinking = true
                kotlinx.coroutines.delay(100)
                blinking = false
            }
        }
    }

    val scale = if (isSpeaking) 1f + 0.035f * breath else 1f + 0.018f * breath
    val ringAlpha = if (isSpeaking) 0.22f + 0.18f * breath else 0.10f + 0.06f * breath

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(Primary.copy(alpha = ringAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxSize(0.92f)
                .clip(CircleShape)
                .background(Color.White)
        )
        // Art + overlay share one square box, so the fractional coordinates above line up
        // regardless of the size the caller asks for.
        Box(Modifier.fillMaxSize(0.88f).aspectRatio(1f), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.nongrak_mascot_hero),
                contentDescription = "NongRak AI",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Paint out the printed mouth, then draw our own.
                drawOval(
                    color = FaceCream,
                    topLeft = Offset((MOUTH_CX - MOUTH_PATCH_RX) * w, (MOUTH_CY - MOUTH_PATCH_RY) * h),
                    size = Size(2 * MOUTH_PATCH_RX * w, 2 * MOUTH_PATCH_RY * h)
                )
                if (openAmount < 0.08f) {
                    // Closed: a smiling arc.
                    val rx = MOUTH_HALF_W * w
                    val ry = MOUTH_MAX_HALF_H * h * 0.55f
                    drawArc(
                        color = MouthDark,
                        startAngle = 20f, sweepAngle = 140f, useCenter = false,
                        topLeft = Offset(MOUTH_CX * w - rx, MOUTH_CY * h - ry),
                        size = Size(2 * rx, 2 * ry),
                        style = Stroke(width = (h * 0.011f).coerceAtLeast(1f))
                    )
                } else {
                    val halfH = (0.006f + MOUTH_MAX_HALF_H * openAmount) * h
                    val halfW = MOUTH_HALF_W * w * (1f - 0.15f * openAmount)   // narrows as it opens
                    drawOval(
                        color = MouthDark,
                        topLeft = Offset(MOUTH_CX * w - halfW, MOUTH_CY * h - halfH),
                        size = Size(2 * halfW, 2 * halfH)
                    )
                    if (openAmount > 0.4f) {
                        drawOval(
                            color = Tongue,
                            topLeft = Offset(MOUTH_CX * w - halfW * 0.55f, MOUTH_CY * h + halfH * 0.05f),
                            size = Size(halfW * 1.1f, halfH * 0.8f)
                        )
                    }
                }

                if (blinking) {
                    listOf(EYE_L_CX to EYE_L_CY, EYE_R_CX to EYE_R_CY).forEach { (cx, cy) ->
                        val rx = EYE_RX * w
                        val ry = EYE_RY * h
                        drawOval(
                            color = FaceCream,
                            topLeft = Offset(cx * w - rx, cy * h - ry),
                            size = Size(2 * rx, 2 * ry)
                        )
                        drawArc(   // closed eyelid line
                            color = MouthDark,
                            startAngle = 200f, sweepAngle = 140f, useCenter = false,
                            topLeft = Offset(cx * w - rx * 0.85f, cy * h - ry * 0.35f),
                            size = Size(rx * 1.7f, ry * 0.7f),
                            style = Stroke(width = (h * 0.012f).coerceAtLeast(1f))
                        )
                    }
                }
            }
        }
    }
}
