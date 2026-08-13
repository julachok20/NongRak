package com.lullabyai.app.ui.screens.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Where one eye sits on a mascot's greeting pose, as a fraction of the image, plus the colour of
 * the skin/fur immediately around it.
 *
 * A blink is drawn as an eyelid in that surrounding colour closing over the eye — the same trick
 * [AiAvatarView] uses for น้องรัก, generalised so every companion can blink. [lid] therefore has
 * to match the face it covers: โคโค่'s right eye sits on the caramel patch, not on white fur.
 */
data class EyeSpec(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val lid: Color
)

// Measured off the 512x512 greeting artwork (น้องรัก's values come from AiAvatar.kt, which was
// calibrated against its hero pose). Re-measure when the 1024x1024 art lands — the fractions
// survive a resolution change, but not a re-pose.
private val NONGRAK_LID = Color(0xFFFBF6F1)

private val MASCOT_EYES: Map<MascotId, List<EyeSpec>> = mapOf(
    MascotId.NONGRAK to listOf(
        EyeSpec(0.440f, 0.452f, 0.052f, 0.050f, NONGRAK_LID),
        EyeSpec(0.678f, 0.438f, 0.052f, 0.050f, NONGRAK_LID)
    ),
    MascotId.COCO to listOf(
        EyeSpec(0.453f, 0.410f, 0.053f, 0.061f, Color(0xFFF5E9E4)),
        EyeSpec(0.674f, 0.430f, 0.053f, 0.061f, Color(0xFFE7AE7D))  // sits on the caramel patch
    ),
    MascotId.BUNNY to listOf(
        EyeSpec(0.455f, 0.512f, 0.049f, 0.057f, Color(0xFFFFFFFD)),
        EyeSpec(0.645f, 0.504f, 0.049f, 0.057f, Color(0xFFFEF3EF))
    ),
    MascotId.PINGU to listOf(
        EyeSpec(0.445f, 0.342f, 0.045f, 0.053f, Color(0xFFF9ECEA)),
        EyeSpec(0.621f, 0.348f, 0.045f, 0.053f, Color(0xFFF9ECEA))
    )
)

/** Eye positions for this mascot, or empty if none were measured. */
fun MascotId.eyes(): List<EyeSpec> = MASCOT_EYES[this].orEmpty()

/**
 * A companion that behaves like a pet: it breathes, drifts, sways, blinks on its own, and reacts
 * when you touch it.
 *
 * The blink only draws on the greeting pose, because that is the pose the eye coordinates were
 * measured from — the other poses already have their expression baked into the art (happy has
 * closed crescents, resting is asleep), so painting a lid onto them would fight the drawing.
 */
@Composable
fun PetMascot(
    mascot: MascotId,
    emotion: CompanionEmotion,
    modifier: Modifier = Modifier,
    onPet: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val hop = remember { Animatable(0f) }
    val blink = remember { Animatable(0f) }
    // Negative stretches tall and thin (rising), positive squashes wide and flat (landing).
    val squash = remember { Animatable(0f) }
    var pettedAt by remember { mutableStateOf(0L) }

    // A touch makes it jump and grin for a moment before settling back to whatever it was doing.
    val shownEmotion = if (pettedAt > 0L) CompanionEmotion.HAPPY else emotion

    LaunchedEffect(pettedAt) {
        if (pettedAt == 0L) return@LaunchedEffect
        delay(1100)
        pettedAt = 0L
    }

    // Idle life. Three overlapping cycles with different periods so the motion never looks looped.
    val transition = rememberInfiniteTransition(label = "pet")
    val drift by transition.pingPong(0f, -7f, 2100, "drift")
    val breathe by transition.pingPong(1f, 1.035f, 1300, "breathe")
    val sway by transition.pingPong(-1.6f, 1.6f, 1850, "sway")

    LaunchedEffect(mascot) {
        while (true) {
            delay(Random.nextLong(2400, 5600))
            blink.animateTo(1f, tween(90))
            blink.animateTo(0f, tween(110))
            // Real blinks come in pairs often enough that always-single reads as robotic.
            if (Random.nextInt(100) < 30) {
                delay(140)
                blink.animateTo(1f, tween(80))
                blink.animateTo(0f, tween(100))
            }
        }
    }

    val interaction = remember { MutableInteractionSource() }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = (drift + hop.value) * density
                    // Squash and stretch preserve volume: what one axis loses, the other gains.
                    scaleX = breathe * (1f + squash.value * 0.12f)
                    scaleY = breathe * (1f - squash.value * 0.12f)
                    rotationZ = sway
                    // Anchor at the feet so a squash flattens onto the ground, not through it.
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clickable(interactionSource = interaction, indication = null) {
                    pettedAt = System.currentTimeMillis()
                    scope.launch {
                        hop.animateTo(-16f, tween(150, easing = FastOutSlowInEasing))
                        hop.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                    // Runs alongside the hop, not after it: stretch on the way up, squash on
                    // landing, then wobble back to rest.
                    scope.launch {
                        squash.animateTo(-0.7f, tween(120, easing = FastOutSlowInEasing))
                        squash.animateTo(0.9f, tween(150, easing = FastOutSlowInEasing))
                        squash.animateTo(
                            0f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                        )
                    }
                    onPet()
                }
        ) {
            // Cross-fade instead of a hard swap — poses differ enough that cutting between them
            // reads as a glitch rather than a change of mood.
            Crossfade(targetState = shownEmotion, animationSpec = tween(260), label = "pose") { pose ->
                Image(
                    painter = painterResource(mascot.poseFor(pose)),
                    contentDescription = mascot.thaiName,
                    contentScale = ContentScale.Crop,
                    // The artwork still carries its own painted background, so it is zoomed to
                    // push that border outside the frame and read as a portrait rather than a
                    // pasted square. Drop this once the transparent art lands.
                    modifier = Modifier.fillMaxSize().scale(1.14f)
                )
            }
            if (shownEmotion == CompanionEmotion.GREETING && blink.value > 0.01f) {
                val eyes = mascot.eyes()
                Canvas(Modifier.fillMaxSize()) {
                    eyes.forEach { eye ->
                        val halfW = size.width * eye.rx
                        val halfH = size.height * eye.ry
                        drawOval(
                            color = eye.lid,
                            topLeft = Offset(size.width * eye.cx - halfW, size.height * eye.cy - halfH),
                            size = Size(halfW * 2f, halfH * 2f * blink.value)
                        )
                    }
                }
            }
        }
        ParticleLayer(burstKey = pettedAt, ambient = shownEmotion)
    }
}

/** Ping-pong tween — the shape every idle animation on this screen uses. */
@Composable
private fun InfiniteTransition.pingPong(from: Float, to: Float, durationMillis: Int, label: String) =
    animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(
            tween(durationMillis, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = label
    )
