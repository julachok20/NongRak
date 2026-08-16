package com.lullabyai.app.ui.mascot

import androidx.annotation.DrawableRes
import com.lullabyai.app.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A bone: a pivot, a direction, and how far its influence reaches.
 *
 * Everything is in normalised image space (0..1) so one rig fits the artwork at any size, and so
 * the numbers stay readable — 0.74 is "three quarters across", which can be checked against the
 * picture by eye.
 */
data class Bone(
    val name: String,
    val pivotX: Float,
    val pivotY: Float,
    val tipX: Float,
    val tipY: Float,
    /** Radius, normalised, beyond which this bone stops moving pixels. */
    val falloff: Float
)

/**
 * Where a mascot's printed face sits, so a moving mouth and blinking eyes can be painted over it.
 *
 * Measured from the artwork by finding the near-black eye blobs and the saturated red of an open
 * mouth, then normalised — the numbers are checkable against the picture by eye, and must be
 * re-measured if the art is ever recropped.
 */
data class Face(
    val eyeLeftX: Float, val eyeLeftY: Float,
    val eyeRightX: Float, val eyeRightY: Float,
    val eyeRadiusX: Float, val eyeRadiusY: Float,
    val mouthX: Float, val mouthY: Float,
    val mouthRadiusX: Float, val mouthRadiusY: Float,
    /** The colour surrounding the mouth, used to paint out the printed one before redrawing it. */
    val skin: Long
)

/**
 * The four mascots and where their movable parts are.
 *
 * A pivot deliberately sits slightly *inside* the body rather than exactly at the joint: rotating
 * about the visible seam tears the shoulder open, while rotating about a point just inside it lets
 * the surrounding pixels stretch and keeps the silhouette whole.
 */
enum class MascotArt(@DrawableRes val art: Int, val bones: List<Bone>, val face: Face) {

    PINGU(
        R.drawable.mascot_pingu,
        listOf(
            // Verified against rendered poses: the raised wing swings without dragging the belly.
            Bone("arm", 0.74f, 0.56f, 0.92f, 0.42f, 0.26f),
            Bone("armFar", 0.22f, 0.60f, 0.09f, 0.72f, 0.20f),
            Bone("trail", 0.60f, 0.58f, 0.60f, 0.80f, 0.16f)   // hanging scarf end
        ),
        // Measured: both eyes and the beak opening found cleanly.
        Face(0.407f, 0.373f, 0.612f, 0.394f, 0.036f, 0.040f, 0.502f, 0.443f, 0.030f, 0.014f, 0xFFF6BE2C)   // beak yellow, not white
    ),

    BUNNY(
        R.drawable.mascot_bunny,
        listOf(
            Bone("arm", 0.68f, 0.56f, 0.80f, 0.44f, 0.16f),
            Bone("armFar", 0.33f, 0.62f, 0.24f, 0.70f, 0.14f), // the paw holding the carrot
            Bone("trail", 0.53f, 0.28f, 0.68f, 0.14f, 0.13f)   // the long ear, which whips
        ),
        // Eyes measured; the mouth is estimated — detection kept locking onto the carrot, which is
        // the same red. Worth checking on a device before trusting it.
        Face(0.426f, 0.495f, 0.623f, 0.457f, 0.038f, 0.040f, 0.520f, 0.565f, 0.030f, 0.018f, 0xFFFFFFFF)
    ),

    COCO(
        R.drawable.mascot_coco,
        listOf(
            Bone("arm", 0.30f, 0.55f, 0.20f, 0.42f, 0.16f),    // raised paw
            Bone("armFar", 0.70f, 0.66f, 0.80f, 0.74f, 0.14f),
            Bone("trail", 0.72f, 0.30f, 0.86f, 0.55f, 0.17f)   // floppy ear
        ),
        // Left eye and mouth measured; the right eye is mirrored, because detection picked his
        // nose — also near-black, and larger.
        Face(0.388f, 0.332f, 0.610f, 0.345f, 0.045f, 0.048f, 0.480f, 0.491f, 0.036f, 0.022f, 0xFFFFFFFF)
    ),

    NONGRAK(
        R.drawable.mascot_nongrak,
        listOf(
            Bone("arm", 0.68f, 0.52f, 0.82f, 0.38f, 0.18f),
            Bone("armFar", 0.30f, 0.58f, 0.22f, 0.66f, 0.14f),
            Bone("trail", 0.34f, 0.60f, 0.20f, 0.84f, 0.18f)   // cape
        ),
        // Measured cleanly.
        Face(0.407f, 0.476f, 0.603f, 0.445f, 0.038f, 0.040f, 0.513f, 0.528f, 0.030f, 0.016f, 0xFFFDF7F2)
    );

    companion object {
        /** Index of the bone each mood drives; see [MascotMood]. */
        const val ARM = 0
        const val ARM_FAR = 1
        const val TRAIL = 2
    }
}

/**
 * Linear blend skinning over a vertex grid — the same thing Rive's mesh deformation does, and the
 * shape `Canvas.drawBitmapMesh` expects.
 *
 * Weights are computed once for a rig and reused every frame; only the cheap rotation per vertex
 * runs at 60fps. That is what keeps this affordable for something drawn continuously behind a chat.
 */
class SkinnedMesh(private val bones: List<Bone>, val cols: Int = 18, val rows: Int = 18) {

    private val count = (cols + 1) * (rows + 1)

    /** Rest positions in normalised space, laid out as x,y pairs. */
    private val rest = FloatArray(count * 2)

    /** weights[bone][vertex]; what is left over after all bones is held by the body. */
    private val weights = Array(bones.size) { FloatArray(count) }

    init {
        var v = 0
        for (row in 0..rows) {
            for (col in 0..cols) {
                val x = col.toFloat() / cols
                val y = row.toFloat() / rows
                rest[v * 2] = x
                rest[v * 2 + 1] = y

                var total = 0f
                for (b in bones.indices) {
                    val d = distanceToBone(x, y, bones[b])
                    // Squared falloff: a linear one lets a bone tug faintly at the whole body,
                    // which reads as the artwork wobbling rather than a limb moving.
                    val w = (1f - d / bones[b].falloff).coerceAtLeast(0f)
                    weights[b][v] = w * w
                    total += weights[b][v]
                }
                // Only normalise where bones overlap; elsewhere the remainder anchors the body.
                if (total > 1f) {
                    for (b in bones.indices) weights[b][v] /= total
                }
                v++
            }
        }
    }

    private fun distanceToBone(x: Float, y: Float, b: Bone): Float {
        val dx = b.tipX - b.pivotX
        val dy = b.tipY - b.pivotY
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq == 0f) 0f
        else (((x - b.pivotX) * dx + (y - b.pivotY) * dy) / lenSq).coerceIn(0f, 1f)
        return hypot(x - (b.pivotX + t * dx), y - (b.pivotY + t * dy))
    }

    /**
     * Writes posed vertices into [out] in the x,y order drawBitmapMesh wants.
     *
     * [anglesRad] is one angle per bone. [width]/[height] are the destination size in pixels, so
     * the same rig draws correctly at a 40dp avatar or a full-screen illustration.
     */
    fun pose(anglesRad: FloatArray, width: Float, height: Float, out: FloatArray) {
        for (v in 0 until count) {
            val x = rest[v * 2]
            val y = rest[v * 2 + 1]
            var outX = x
            var outY = y
            for (b in bones.indices) {
                val w = weights[b][v]
                if (w <= 0f) continue
                val angle = anglesRad.getOrElse(b) { 0f }
                if (angle == 0f) continue
                val px = bones[b].pivotX
                val py = bones[b].pivotY
                val c = cos(angle)
                val s = sin(angle)
                val rx = px + (x - px) * c - (y - py) * s
                val ry = py + (x - px) * s + (y - py) * c
                outX += w * (rx - x)
                outY += w * (ry - y)
            }
            out[v * 2] = outX * width
            out[v * 2 + 1] = outY * height
        }
    }

    /** A correctly sized buffer for [pose]. */
    fun newVertexBuffer() = FloatArray(count * 2)
}
