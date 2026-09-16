package com.lullabyai.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Frames cut out of a video so each one can be screened like a photo before the video is published.
 *
 * A video is never sent whole: the edge function screens images, and a whole clip would not fit.
 * An empty result means the file could NOT be read — callers must treat that as "cannot check",
 * never as "safe".
 */
object VideoFrames {
    const val MAX_FRAMES = 5

    /** Evenly spaced frames (the first one always included), longest side <= [maxDim], as JPEG bytes. */
    fun extract(context: Context, uri: Uri, frames: Int = MAX_FRAMES, maxDim: Int = 512, quality: Int = 70): List<ByteArray> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val count = frames.coerceIn(1, MAX_FRAMES)
            (0 until count).mapNotNull { index ->
                val atMs = if (durationMs <= 0L) 0L else durationMs * index / count
                val bitmap = retriever.getFrameAtTime(atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                bitmap?.let { jpeg(it, maxDim, quality) }
            }
        } catch (e: Exception) {
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** First frame, for the feed card to show while the clip itself stays a link. */
    fun thumbnail(context: Context, uri: Uri, maxDim: Int = 1024, quality: Int = 80): ByteArray? =
        extract(context, uri, frames = 1, maxDim = maxDim, quality = quality).firstOrNull()

    private fun jpeg(bitmap: Bitmap, maxDim: Int, quality: Int): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > maxDim) {
            val ratio = maxDim.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }
}
