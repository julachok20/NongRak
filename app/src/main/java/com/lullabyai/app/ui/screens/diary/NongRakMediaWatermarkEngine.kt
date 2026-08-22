package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.google.common.collect.ImmutableList
import com.lullabyai.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Burns the NongRak licence badge into exported media, and appends the signature outro to video.
 *
 * The video path really re-encodes. The previous implementation byte-copied the source file,
 * reported progress 0.2 -> 0.8 -> 1.0 and returned "success", so every exported .mp4 carried the
 * copyright claim in the UI and no watermark in the file. Nothing about that was visible in-app.
 *
 * Both paths draw through the SAME [drawWatermarkBadge], so a still and a frame of video cannot
 * drift apart.
 */
object NongRakMediaWatermarkEngine {
    private const val TAG = "NongRakWatermark"

    /** How long the closing brand card runs for. */
    private const val OUTRO_DURATION_US = 2_000_000L

    /** Image inputs carry no frame rate of their own; Transformer needs one to encode them. */
    private const val OUTRO_FRAME_RATE = 30

    private const val BRAND_URL = "https://nongrak-ai.vercel.app/"

    // -----------------------------------------------------------------------------------------
    // Images
    // -----------------------------------------------------------------------------------------

    /** Burns the official NongRak AI licence watermark badge (with logo) into an image. */
    suspend fun burnWatermarkToImage(
        context: Context,
        imageUri: Uri,
        title: String?,
        dateText: String?,
        isThai: Boolean = true
    ): Uri = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: return@withContext imageUri
        inputStream?.close()

        val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        drawWatermarkBadge(
            context = context,
            canvas = canvas,
            canvasWidth = resultBitmap.width,
            canvasHeight = resultBitmap.height,
            title = title,
            dateText = dateText,
            isThai = isThai
        )

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "nongrak_memory_${System.currentTimeMillis()}.jpg")
        FileOutputStream(exportFile).use { out ->
            resultBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
    }

    // -----------------------------------------------------------------------------------------
    // Video
    // -----------------------------------------------------------------------------------------

    /**
     * Re-encodes [videoUri] with the licence badge burned into every frame, optionally reformatted
     * to [aspectRatio], and with the two-second brand outro appended.
     *
     * Returns the source URI unchanged if the export fails. Deliberate: a parent trying to share a
     * memory should still get their video out. The failure is named in the log rather than hidden.
     */
    suspend fun prepareWatermarkedVideoForExport(
        context: Context,
        videoUri: Uri,
        title: String?,
        dateText: String?,
        isThai: Boolean = true,
        aspectRatio: Float? = null,
        mascotResId: Int? = null,
        mascotPosition: MascotPosition? = null,
        onProgress: (Float) -> Unit = {}
    ): Uri {
        return try {
            renderVideo(
                context, videoUri, title, dateText, isThai,
                aspectRatio, mascotResId, mascotPosition, onProgress
            )
        } catch (e: Exception) {
            Log.e(TAG, "Watermark render failed, exporting original: ${e.message}", e)
            videoUri
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun renderVideo(
        context: Context,
        videoUri: Uri,
        title: String?,
        dateText: String?,
        isThai: Boolean,
        aspectRatio: Float?,
        mascotResId: Int?,
        mascotPosition: MascotPosition?,
        onProgress: (Float) -> Unit
    ): Uri {
        onProgress(0.05f)

        val (srcWidth, srcHeight) = videoSize(context, videoUri)

        // Full-frame transparent overlays rather than badge-sized bitmaps with anchor maths: the
        // same drawWatermarkBadge call then produces pixel-identical results for a photo and for a
        // frame of video, which is the entire point of having one drawing routine.
        val watermarkBitmap = withContext(Dispatchers.Default) {
            transparentFrame(srcWidth, srcHeight) { canvas ->
                drawWatermarkBadge(context, canvas, srcWidth, srcHeight, title, dateText, isThai)
            }
        }

        // Typed as the supertype OverlayEffect actually takes; a list inferred as
        // MutableList<BitmapOverlay> does not satisfy List<TextureOverlay> from Kotlin.
        val overlays = mutableListOf<TextureOverlay>(
            BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap)
        )

        if (mascotResId != null) {
            withContext(Dispatchers.Default) {
                mascotOverlayFrame(context, srcWidth, srcHeight, mascotResId, mascotPosition)
            }?.let { overlays.add(BitmapOverlay.createStaticBitmapOverlay(it)) }
        }

        val videoEffects = buildList {
            add(OverlayEffect(ImmutableList.copyOf(overlays)))
            // Applied AFTER the overlay so the badge is reframed together with the picture rather
            // than being cropped off by the new aspect ratio.
            if (aspectRatio != null) {
                add(Presentation.createForAspectRatio(aspectRatio, Presentation.LAYOUT_SCALE_TO_FIT))
            }
        }

        val mainItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        val outroItem = buildOutroItem(context, srcWidth, srcHeight, isThai)

        onProgress(0.15f)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "nongrak_video_${System.currentTimeMillis()}.mp4")

        val sequence = EditedMediaItemSequence.Builder(mainItem, outroItem).build()
        val composition = Composition.Builder(ImmutableList.of(sequence))
            // The outro is a still and carries no audio track. Without a forced track the muxer
            // drops audio for the whole composition, silencing the parent's actual video.
            .experimentalSetForceAudioTrack(true)
            .build()

        awaitExport(context, composition, exportFile.absolutePath, onProgress)

        onProgress(1.0f)
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", exportFile
        )
    }

    /**
     * Runs Transformer to completion.
     *
     * On the main thread because Transformer requires the calling thread to have a Looper and
     * posts its callbacks there; constructed from Dispatchers.IO it throws.
     */
    @OptIn(UnstableApi::class)
    private suspend fun awaitExport(
        context: Context,
        composition: Composition,
        outputPath: String,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                })
                .build()

            cont.invokeOnCancellation {
                // Leaving it running would keep an encoder and a MediaMuxer alive after the parent
                // has already closed the sheet.
                runCatching { transformer.cancel() }
            }

            onProgress(0.2f)
            transformer.start(composition, outputPath)
        }
    }

    /** The closing brand card, as a two-second still. */
    @OptIn(UnstableApi::class)
    private fun buildOutroItem(
        context: Context,
        width: Int,
        height: Int,
        isThai: Boolean
    ): EditedMediaItem {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outroFile = File(exportDir, "nongrak_outro_${System.currentTimeMillis()}.png")
        FileOutputStream(outroFile).use { out ->
            buildOutroBitmap(context, width, height, isThai)
                .compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // The MIME type must be set explicitly: without it Transformer probes the item as video,
        // finds no track and fails instead of treating it as an image.
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(outroFile))
            .setMimeType(MimeTypes.IMAGE_PNG)
            .build()

        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(OUTRO_DURATION_US)
            .setFrameRate(OUTRO_FRAME_RATE)
            .build()
    }

    // -----------------------------------------------------------------------------------------
    // Bitmap helpers
    // -----------------------------------------------------------------------------------------

    private fun videoSize(context: Context, uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            // A portrait phone video is stored landscape with rotation 90. Overlays are composited
            // AFTER rotation is applied, so a badge sized to the raw dimensions lands sideways.
            val swap = rotation == 90 || rotation == 270
            val width = (if (swap) h else w) ?: 1080
            val height = (if (swap) w else h) ?: 1920
            width to height
        } catch (e: Exception) {
            Log.w(TAG, "Could not read video size, assuming 1080x1920: ${e.message}")
            1080 to 1920
        } finally {
            runCatching { retriever.release() }
        }
    }

    private inline fun transparentFrame(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap))
        return bitmap
    }

    /** A full-frame transparent layer carrying just the mascot, in the chosen corner. */
    private fun mascotOverlayFrame(
        context: Context,
        width: Int,
        height: Int,
        mascotResId: Int,
        position: MascotPosition?
    ): Bitmap? {
        val mascot = runCatching {
            BitmapFactory.decodeResource(context.resources, mascotResId)
        }.getOrNull() ?: return null

        val target = width.coerceAtMost(height) * 0.22f
        val drawWidth = target
        val drawHeight = target * (mascot.height.toFloat() / mascot.width.toFloat())
        val margin = width * 0.05f

        // The badge owns the bottom-right corner and is not negotiable, so the mascot is kept out
        // of it: BOTTOM_RIGHT is deliberately not an option in MascotPosition either.
        val left = when (position) {
            MascotPosition.TOP_RIGHT -> width - drawWidth - margin
            MascotPosition.BOTTOM_CENTER -> (width - drawWidth) / 2f
            else -> margin
        }
        val top = when (position) {
            MascotPosition.TOP_LEFT, MascotPosition.TOP_RIGHT -> margin
            else -> height - drawHeight - margin * 2.4f
        }

        return transparentFrame(width, height) { canvas ->
            canvas.drawBitmap(
                mascot,
                null,
                RectF(left, top, left + drawWidth, top + drawHeight),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
        }
    }

    /** The two-second closing card: logo, brand, licence line, website. */
    private fun buildOutroBitmap(
        context: Context,
        width: Int,
        height: Int,
        isThai: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = (width.coerceAtLeast(height) / 1080f).coerceIn(0.6f, 2.5f)

        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    Color.parseColor("#FF0D0E29"), Color.parseColor("#FF2743C4"),
                    Shader.TileMode.CLAMP
                )
            }
        )

        val centerX = width / 2f
        val logoSize = width * 0.28f
        val logoTop = height / 2f - logoSize * 0.95f
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_nongrak_logo)
        }.getOrNull()?.let { logo ->
            canvas.drawBitmap(
                logo, null,
                RectF(centerX - logoSize / 2f, logoTop, centerX + logoSize / 2f, logoTop + logoSize),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("น้องรัก (NongRak AI)", centerX, logoTop + logoSize + 84f * scale, brandPaint)

        val licencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFD54F")
            textSize = 30f * scale
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val licence = if (isThai) "Official Family Archive • ลิขสิทธิ์แท้" else "Official NongRak Archive"
        canvas.drawText(licence, centerX, logoTop + logoSize + 140f * scale, licencePaint)

        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFC3CDF5")
            textSize = 28f * scale
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(BRAND_URL, centerX, logoTop + logoSize + 200f * scale, urlPaint)

        return bitmap
    }

    /** Generates a high-resolution watermarked preview / cover card bitmap. */
    fun createWatermarkedCoverBitmap(
        context: Context,
        baseBitmap: Bitmap,
        title: String?,
        dateText: String?,
        isThai: Boolean
    ): Bitmap {
        val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        drawWatermarkBadge(context, canvas, result.width, result.height, title, dateText, isThai)
        return result
    }

    /** Draws the official NongRak AI watermark and licence badge, with logo, onto a Canvas. */
    private fun drawWatermarkBadge(
        context: Context,
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        title: String?,
        dateText: String?,
        isThai: Boolean
    ) {
        val scale = (canvasWidth.coerceAtLeast(canvasHeight) / 1080f).coerceIn(0.6f, 2.5f)

        val padding = 32f * scale
        val badgeHeight = 76f * scale
        val badgeWidth = if (!title.isNullOrBlank()) 390f * scale else 270f * scale

        val left = canvasWidth - badgeWidth - padding
        val top = canvasHeight - badgeHeight - padding
        val right = canvasWidth - padding
        val bottom = canvasHeight - padding

        val badgeRect = RectF(left, top, right, bottom)
        val cornerRadius = 38f * scale

        // 1. Background dark capsule
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6020229")
            style = Paint.Style.FILL
            setShadowLayer(10f * scale, 0f, 4f * scale, Color.parseColor("#80000000"))
        }
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, bgPaint)

        // 2. Golden / iridescent border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
            shader = LinearGradient(
                left, top, right, bottom,
                Color.parseColor("#FFFFD54F"),
                Color.parseColor("#FF5270C9"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(badgeRect, cornerRadius, cornerRadius, borderPaint)

        // 3. Official NongRak logo
        val logoSize = badgeHeight - 16f * scale
        val logoLeft = left + 10f * scale
        val logoTop = top + 8f * scale
        val logoDestRect = RectF(logoLeft, logoTop, logoLeft + logoSize, logoTop + logoSize)

        try {
            val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_nongrak_logo)
            if (logoBitmap != null) {
                val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
                canvas.drawBitmap(logoBitmap, null, logoDestRect, logoPaint)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load logo bitmap: ${e.message}")
        }

        // 4. Brand text
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textStartX = logoLeft + logoSize + 10f * scale
        canvas.drawText("น้องรัก (NongRak AI)", textStartX, top + badgeHeight * 0.44f, brandPaint)

        // 5. Licence line
        val licensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFD54F")
            textSize = 10.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subText = if (!title.isNullOrBlank()) {
            "${title.take(18)} • ลิขสิทธิ์แท้"
        } else {
            if (isThai) "Official Family Archive • ลิขสิทธิ์แท้" else "Official NongRak Archive"
        }
        canvas.drawText(subText, textStartX, top + badgeHeight * 0.76f, licensePaint)

        // 6. Top-left milestone tag
        if (!title.isNullOrBlank() && !dateText.isNullOrBlank()) {
            val topBadgeWidth = 320f * scale
            val topBadgeHeight = 44f * scale
            val topBadgeRect = RectF(padding, padding, padding + topBadgeWidth, padding + topBadgeHeight)

            val topBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CC0D0E29")
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(topBadgeRect, 12f * scale, 12f * scale, topBgPaint)

            val topBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
                color = Color.parseColor("#66FFD54F")
            }
            canvas.drawRoundRect(topBadgeRect, 12f * scale, 12f * scale, topBorderPaint)

            val tagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 13f * scale
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("👶 $dateText", padding + 14f * scale, padding + 28f * scale, tagTextPaint)
        }
    }
}
