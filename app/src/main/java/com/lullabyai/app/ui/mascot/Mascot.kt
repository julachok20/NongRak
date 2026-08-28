package com.lullabyai.app.ui.mascot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = (2 * PI).toFloat()
private val MouthDark = Color(0xFF49212C)
private val Tongue = Color(0xFFD1697A)

/**
 * A bone-deformed mascot.
 *
 * The artwork is a single flat PNG; the movement comes from skinning a vertex grid over it and
 * handing the result to `Canvas.drawBitmapMesh`, which is the same technique a runtime like Rive
 * uses. That is what lets a wing swing while the belly stays put, without the art being split into
 * layers.
 *
 * [mood] is the resting behaviour when it loops, or a one-shot to play when it does not — pass
 * [MascotMood.REPLY] the moment an answer arrives and it plays once, then falls back to [resting].
 */
@Composable
fun Mascot(
    art: MascotArt,
    mood: MascotMood,
    modifier: Modifier = Modifier,
    resting: MascotMood = MascotMood.IDLE,
    /** Flaps the mouth. Android TTS exposes only start/done, so this is a cartoon flap, not lip-sync. */
    speaking: Boolean = false,
    /** Draws blinking eyes and a mouth over the printed ones. */
    animateFace: Boolean = true,
    /**
     * Worn items, drawn over the body in order — shirt first, then anything on top of it.
     *
     * Each is a full-canvas PNG registered to the same art as [art] and transparent everywhere
     * else, so it goes through the SAME deformed vertex grid as the body. That is the whole trick:
     * a sleeve swings with the arm bone and a belly print squashes with a bounce for free, with no
     * per-garment rigging and no anchor points to keep in sync.
     */
    garments: List<Int> = emptyList(),
    onTap: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Decoded unscaled and in software config: drawBitmapMesh cannot read a hardware bitmap, and
    // density scaling would resample the art differently on every phone.
    val bitmap = remember(art) {
        BitmapFactory.decodeResource(
            context.resources, art.art,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
        )
    }

    // Same decode rules as the body: software config (drawBitmapMesh cannot read a hardware
    // bitmap) and unscaled, so a garment lines up with the art on every density.
    val garmentBitmaps = remember(garments) {
        garments.mapNotNull { res ->
            runCatching {
                BitmapFactory.decodeResource(
                    context.resources, res,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    }
                )
            }.getOrNull()
        }
    }

    val mesh = remember(art) { SkinnedMesh(art.bones) }
    val vertices = remember(mesh) { mesh.newVertexBuffer() }
    val angles = remember { FloatArray(3) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
    }

    var pose by remember { mutableStateOf(MascotPose()) }
    /** A one-shot waiting to play; it outranks the looping mood until it finishes. */
    var oneShot by remember { mutableStateOf<MascotMood?>(null) }

    LaunchedEffect(mood) { if (!mood.loops) oneShot = mood }

    LaunchedEffect(art) {
        var startedAt = 0L
        var playing: MascotMood? = null
        while (true) {
            withFrameNanos { now ->
                val target = oneShot ?: if (mood.loops) mood else resting
                // Restarting the clock on every change is what stops a mood from beginning
                // halfway through its curve.
                if (target !== playing) {
                    playing = target
                    startedAt = now
                }
                val elapsedMs = (now - startedAt) / 1_000_000f
                val duration = target.durationMs.toFloat()
                pose = if (target.loops) {
                    target.poseAt((elapsedMs % duration) / duration)
                } else {
                    val t = (elapsedMs / duration).coerceAtMost(1f)
                    if (t >= 1f) oneShot = null
                    target.poseAt(t)
                }
            }
        }
    }

    // The face runs on its own clock: blinking that lined up with the body's rhythm would read as
    // mechanical, and a mouth flap tied to the mood would stop mid-sentence when the mood changed.
    var blinking by remember { mutableStateOf(false) }
    var mouthPhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animateFace) {
        if (!animateFace) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blinking = true
            delay(110)
            blinking = false
            if (Random.nextInt(4) == 0) {          // an occasional double blink
                delay(120); blinking = true; delay(100); blinking = false
            }
        }
    }

    LaunchedEffect(speaking, animateFace) {
        if (!speaking || !animateFace) {
            mouthPhase = 0f
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> mouthPhase = (now - start) / 1_000_000_000f }
        }
    }

    val tappable = if (onTap == null) modifier else modifier.pointerInput(art) {
        detectTapGestures {
            oneShot = MascotMood.TAP
            onTap()
        }
    }

    Canvas(tappable) {
        if (bitmap == null) return@Canvas
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        angles[MascotArt.ARM] = Math.toRadians(pose.arm.toDouble()).toFloat()
        angles[MascotArt.ARM_FAR] = Math.toRadians(pose.armFar.toDouble()).toFloat()
        angles[MascotArt.TRAIL] = Math.toRadians(pose.trail.toDouble()).toFloat()
        mesh.pose(angles, w, h, vertices)

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            val saved = native.save()
            // Whole-body motion happens around the mascot's centre, on top of the bone
            // deformation — the two are independent, which is why a wave can happen mid-bounce.
            native.translate(w / 2f + pose.dx * w, h / 2f + pose.dy * h)
            native.rotate(pose.rot)
            native.scale(pose.sx, pose.sy)
            native.translate(-w / 2f, -h / 2f)
            native.drawBitmapMesh(bitmap, mesh.cols, mesh.rows, vertices, 0, null, 0, paint)
            // The same vertex buffer, so clothing deforms with the body rather than sliding over it.
            garmentBitmaps.forEach { garment ->
                native.drawBitmapMesh(garment, mesh.cols, mesh.rows, vertices, 0, null, 0, paint)
            }
            native.restoreToCount(saved)
        }

        if (!animateFace) return@Canvas

        // Drawn after the mesh and inside the same body transform, so the face rides along with a
        // bounce or a tilt. It sits where bone weights are ~0, which is why it stays aligned while
        // a limb is swinging.
        withTransform({
            translate(pose.dx * w, pose.dy * h)
            rotate(pose.rot, pivot = Offset(w / 2f, h / 2f))
            scale(pose.sx, pose.sy, pivot = Offset(w / 2f, h / 2f))
        }) {
            val face = art.face
            val skin = Color(face.skin)

            // Two sines at unrelated speeds, so the flap never looks metronomic.
            val open = if (!speaking) 0f else {
                val a = (sin(TWO_PI * 3.1f * mouthPhase) + 1f) / 2f
                val b = (sin(TWO_PI * 5.7f * mouthPhase) + 1f) / 2f
                (0.12f + 0.88f * (0.7f * a + 0.3f * b)).coerceIn(0f, 1f)
            }

            if (speaking) {
                // Cover the printed mouth generously before drawing the moving one.
                drawOval(
                    color = skin,
                    topLeft = Offset(
                        (face.mouthX - face.mouthRadiusX * 1.5f) * w,
                        (face.mouthY - face.mouthRadiusY * 2.2f) * h
                    ),
                    size = Size(face.mouthRadiusX * 3f * w, face.mouthRadiusY * 4.4f * h)
                )
                val halfH = (0.004f + face.mouthRadiusY * 2.2f * open) * h
                val halfW = face.mouthRadiusX * w * (1f - 0.15f * open)
                drawOval(
                    color = MouthDark,
                    topLeft = Offset(face.mouthX * w - halfW, face.mouthY * h - halfH),
                    size = Size(halfW * 2f, halfH * 2f)
                )
                if (open > 0.4f) {
                    drawOval(
                        color = Tongue,
                        topLeft = Offset(face.mouthX * w - halfW * 0.55f, face.mouthY * h + halfH * 0.05f),
                        size = Size(halfW * 1.1f, halfH * 0.8f)
                    )
                }
            }

            if (blinking) {
                listOf(
                    face.eyeLeftX to face.eyeLeftY,
                    face.eyeRightX to face.eyeRightY
                ).forEach { (cx, cy) ->
                    val rx = face.eyeRadiusX * w * 1.15f
                    val ry = face.eyeRadiusY * h * 1.15f
                    drawOval(
                        color = skin,
                        topLeft = Offset(cx * w - rx, cy * h - ry),
                        size = Size(rx * 2f, ry * 2f)
                    )
                    drawArc(                       // the closed lid
                        color = MouthDark,
                        startAngle = 200f, sweepAngle = 140f, useCenter = false,
                        topLeft = Offset(cx * w - rx * 0.85f, cy * h - ry * 0.35f),
                        size = Size(rx * 1.7f, ry * 0.7f),
                        style = Stroke(width = (h * 0.010f).coerceAtLeast(1f))
                    )
                }
            }
        }
    }
}
