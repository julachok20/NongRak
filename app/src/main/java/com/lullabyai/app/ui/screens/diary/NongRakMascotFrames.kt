package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.lullabyai.app.ui.mascot.MascotArt
import com.lullabyai.app.ui.mascot.MascotMood
import com.lullabyai.app.ui.mascot.SkinnedMesh

/**
 * Renders the rigged companion into video frames.
 *
 * The mascot in an exported clip used to be a flat PNG pasted into a corner. The app already owns a
 * companion that breathes and swings an arm - the one on the chat screen - and this drives THAT
 * rig, off-screen, one bitmap per step of its idle loop.
 *
 * Deliberately the same [SkinnedMesh] and the same [MascotMood.poseAt] the on-screen mascot uses. A
 * second animation engine written for video would drift from the one the parent watched while
 * choosing, and this project has already paid for a duplicate mascot implementation once.
 */
internal object NongRakMascotFrames {

    private const val TAG = "NongRakMascotFrames"

    /** Steps around the idle loop. At 12fps playback this is two seconds before it repeats. */
    private const val FRAME_COUNT = 24

    /** The companion's size as a fraction of the frame's shorter edge. */
    private const val SIZE_FRACTION = 0.28f

    /**
     * One full-frame transparent layer per step, with the companion already in position.
     *
     * Full-frame rather than a small bitmap plus overlay settings: those settings position in
     * normalised device coordinates that flip and scale differently from the preview's, and a
     * mascot landing somewhere other than where it was dragged is worse than one costing a few
     * more megabytes to composite.
     *
     * Returns an empty list if the artwork will not decode - the caller then simply omits it.
     */
    fun render(
        context: Context,
        art: MascotArt,
        frameWidth: Int,
        frameHeight: Int,
        edit: NongRakStudioEdit
    ): List<Bitmap> {
        val source = runCatching {
            BitmapFactory.decodeResource(
                context.resources, art.art,
                BitmapFactory.Options().apply {
                    // drawBitmapMesh cannot read a hardware bitmap, and density scaling would
                    // resample the art differently on every phone.
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
            )
        }.getOrNull() ?: run {
            Log.w(TAG, "Mascot art ${art.name} would not decode; exporting without a companion")
            return emptyList()
        }

        val side = minOf(frameWidth, frameHeight) * SIZE_FRACTION
        val mesh = SkinnedMesh(art.bones)
        val vertices = mesh.newVertexBuffer()
        val angles = FloatArray(3)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        // Where the companion's centre sits, clamped so a drag to the edge parks it against that
        // edge rather than half outside the picture.
        val left = (edit.mascotX * frameWidth - side / 2f)
            .coerceIn(0f, (frameWidth - side).coerceAtLeast(0f))
        val top = (edit.mascotY * frameHeight - side / 2f)
            .coerceIn(0f, (frameHeight - side).coerceAtLeast(0f))

        return (0 until FRAME_COUNT).map { step ->
            val pose = MascotMood.IDLE.poseAt(step.toFloat() / FRAME_COUNT)

            angles[MascotArt.ARM] = Math.toRadians(pose.arm.toDouble()).toFloat()
            angles[MascotArt.ARM_FAR] = Math.toRadians(pose.armFar.toDouble()).toFloat()
            angles[MascotArt.TRAIL] = Math.toRadians(pose.trail.toDouble()).toFloat()
            mesh.pose(angles, side, side, vertices)

            val layer = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            Canvas(layer).apply {
                val saved = save()
                translate(left, top)
                // Whole-body motion around the mascot's centre, on top of the bone deformation -
                // the same order the on-screen mascot uses, so a wave can happen mid-bounce.
                translate(side / 2f + pose.dx * side, side / 2f + pose.dy * side)
                rotate(pose.rot)
                scale(pose.sx, pose.sy)
                translate(-side / 2f, -side / 2f)
                drawBitmapMesh(source, mesh.cols, mesh.rows, vertices, 0, null, 0, paint)
                restoreToCount(saved)
            }
            layer
        }
    }
}
