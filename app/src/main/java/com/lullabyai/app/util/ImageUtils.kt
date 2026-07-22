package com.lullabyai.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtils {
    /**
     * Reads [uri], downscales so the longest side is <= [maxDim], and returns JPEG bytes — small
     * enough to base64 into the vision request without huge payloads. Null on failure.
     */
    fun downscaleToJpeg(context: Context, uri: Uri, maxDim: Int = 1024, quality: Int = 80): ByteArray? = runCatching {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scaled = if (longest > maxDim) {
            val s = maxDim.toFloat() / longest
            Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt().coerceAtLeast(1), (bmp.height * s).toInt().coerceAtLeast(1), true)
        } else bmp
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** Reads [uri]'s raw bytes as-is — for non-image documents (PDF) where downscaling doesn't
     *  apply. Null on failure. Caller is responsible for keeping the file small (Claude/Gemini
     *  both cap request payload size) — no size check here, deliberately kept simple. */
    fun readBytes(context: Context, uri: Uri): ByteArray? =
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
}
