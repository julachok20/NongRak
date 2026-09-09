package com.lullabyai.app.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One particle's offset from centre, when it starts, and how big it gets. */
private data class Particle(val dx: Int, val delay: Float, val scale: Float)

// Hand-placed rather than random: a fixed spread reads as a deliberate little burst, whereas
// re-randomising on every tap makes repeated petting look noisy.
private val HEARTS = listOf(
    Particle(dx = -30, delay = 0.00f, scale = 1.00f),
    Particle(dx = 6, delay = 0.10f, scale = 1.25f),
    Particle(dx = 34, delay = 0.05f, scale = 0.90f),
    Particle(dx = -12, delay = 0.22f, scale = 0.75f),
    Particle(dx = 22, delay = 0.30f, scale = 0.70f)
)

private val SPARKLES = listOf(
    Particle(dx = -40, delay = 0.00f, scale = 0.9f),
    Particle(dx = 38, delay = 0.35f, scale = 1.1f),
    Particle(dx = 2, delay = 0.65f, scale = 0.7f)
)

private val ZZZ = listOf(
    Particle(dx = 26, delay = 0.00f, scale = 1.00f),
    Particle(dx = 38, delay = 0.33f, scale = 0.85f),
    Particle(dx = 48, delay = 0.66f, scale = 0.70f)
)

/**
 * The little flourishes around the companion: hearts when it is petted, and an ambient loop that
 * matches its mood — sparkles when it is happy, drifting "z"s when it is asleep.
 *
 * Drawn outside the mascot's own transform so the particles do not inherit its sway and squash;
 * they should look like they are in the air around it, not painted onto it.
 */
@Composable
fun ParticleLayer(
    burstKey: Long,
    ambient: CompanionEmotion,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        HeartBurst(burstKey)
        when (ambient) {
            CompanionEmotion.HAPPY -> AmbientLoop(SPARKLES, 2600) { progress, particle ->
                Icon(
                    Icons.Filled.AutoAwesome, null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier
                        .offset(x = particle.dx.dp, y = (-46 * progress).dp)
                        .alpha(fadeInOut(progress))
                        .scale(particle.scale * (0.6f + progress * 0.5f))
                )
            }
            CompanionEmotion.RESTING -> AmbientLoop(ZZZ, 3400) { progress, particle ->
                Text(
                    "z",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalChatColors.current.primary,
                    modifier = Modifier
                        .offset(x = (particle.dx + progress * 14).dp, y = (-52 * progress).dp)
                        .alpha(fadeInOut(progress))
                        .scale(particle.scale)
                )
            }
            else -> Unit
        }
    }
}

/** Ramp up over the first fifth of the flight, hold, then fade out over the last third. */
private fun fadeInOut(progress: Float): Float = when {
    progress < 0.2f -> progress / 0.2f
    progress > 0.65f -> ((1f - progress) / 0.35f).coerceAtLeast(0f)
    else -> 1f
}

@Composable
private fun HeartBurst(burstKey: Long) {
    // Starts finished, so nothing renders until the first pet.
    val flight = remember { Animatable(1f) }

    LaunchedEffect(burstKey) {
        if (burstKey == 0L) return@LaunchedEffect
        flight.snapTo(0f)
        flight.animateTo(1f, tween(1000, easing = FastOutSlowInEasing))
    }

    if (flight.value >= 1f) return

    Box(contentAlignment = Alignment.TopCenter) {
        HEARTS.forEach { heart ->
            // Each heart runs the same flight, offset in time, and finishes before the burst ends.
            val progress = ((flight.value - heart.delay) / 0.7f).coerceIn(0f, 1f)
            if (progress <= 0f) return@forEach
            Icon(
                Icons.Filled.Favorite, null,
                tint = Color(0xFFEC4899),
                modifier = Modifier
                    .offset(x = heart.dx.dp, y = (-70 * progress).dp)
                    .alpha(1f - progress)
                    .scale(heart.scale * (0.5f + progress * 0.7f))
            )
        }
    }
}

@Composable
private fun AmbientLoop(
    particles: List<Particle>,
    durationMillis: Int,
    content: @Composable (progress: Float, particle: Particle) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "ambient")
    val cycle by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle"
    )

    Box(contentAlignment = Alignment.TopCenter) {
        particles.forEach { particle ->
            // Wrapping keeps the three staggered without needing three separate transitions.
            val progress = (cycle + particle.delay) % 1f
            content(progress, particle)
        }
    }
}
