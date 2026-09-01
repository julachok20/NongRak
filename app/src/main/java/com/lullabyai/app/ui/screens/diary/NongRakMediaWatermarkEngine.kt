package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
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
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Everything the studio can change about one clip.
 *
 * Deliberately a value object rather than a pile of parameters: the studio dialog holds exactly
 * this and nothing else, so "what did the parent choose" and "what gets rendered" cannot drift.
 */
data class NongRakStudioEdit(
    val trimStartMs: Long = 0L,
    /** null means "to the end of the clip". */
    val trimEndMs: Long? = null,
    val speed: Float = 1f,
    val effect: NongRakVideoEffect = NongRakVideoEffect.NONE,
    val filter: NongRakVideoFilter = NongRakVideoFilter.NONE,
    val mascot: NongRakMascotSticker = NongRakMascotSticker.NONE,
    val mascotPosition: MascotPosition = MascotPosition.BOTTOM_LEFT,
    /**
     * Where the companion stands, as a fraction of the frame, measured at its centre.
     *
     * Free coordinates rather than four corners, because a corner is only the right answer when
     * the child happens not to be standing in it.
     */
    val mascotX: Float = 0.22f,
    val mascotY: Float = 0.74f,
    /** Where the caption sits, same convention. */
    val captionX: Float = 0.5f,
    val captionY: Float = 0.84f,
    /** null keeps the source framing. */
    val aspectRatio: StudioAspectRatio? = null,
    val captionText: String? = null,
    val stampStyle: NongRakStampStyle? = null,
    val keepOriginalAudio: Boolean = true
) {
    /**
     * True when rendering would actually produce a different file.
     *
     * Re-encoding a clip to apply nothing costs a minute of the parent's time and a generation of
     * quality, so the renderer returns the source untouched instead.
     */
    val changesAnything: Boolean
        get() = trimStartMs > 0L ||
            trimEndMs != null ||
            speed != 1f ||
            effect != NongRakVideoEffect.NONE ||
            filter != NongRakVideoFilter.NONE ||
            mascot != NongRakMascotSticker.NONE ||
            aspectRatio != null ||
            !captionText.isNullOrBlank() ||
            !keepOriginalAudio
}

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
        /**
         * False for clips the studio already branded at creation time.
         *
         * Stamping a second badge over the first is not a cosmetic problem: the two are different
         * marks, drawn in the same corner, and they overlap into something that looks like damage.
         */
        watermark: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): Uri {
        return try {
            renderVideo(
                context, videoUri, title, dateText, isThai,
                aspectRatio, mascotResId, mascotPosition, watermark, onProgress
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
        watermark: Boolean,
        onProgress: (Float) -> Unit
    ): Uri {
        onProgress(0.05f)

        val (srcWidth, srcHeight) = videoSize(context, videoUri)

        // Full-frame transparent overlays rather than badge-sized bitmaps with anchor maths: the
        // same drawWatermarkBadge call then produces pixel-identical results for a photo and for a
        // frame of video, which is the entire point of having one drawing routine.
        // Typed as the supertype OverlayEffect actually takes; a list inferred as
        // MutableList<BitmapOverlay> does not satisfy List<TextureOverlay> from Kotlin.
        val overlays = mutableListOf<TextureOverlay>()

        if (watermark) {
            val watermarkBitmap = withContext(Dispatchers.Default) {
                transparentFrame(srcWidth, srcHeight) { canvas ->
                    drawWatermarkBadge(context, canvas, srcWidth, srcHeight, title, dateText, isThai)
                }
            }
            overlays.add(BitmapOverlay.createStaticBitmapOverlay(watermarkBitmap))
        }

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

    // -----------------------------------------------------------------------------------------
    // Recap reel
    // -----------------------------------------------------------------------------------------

    /** One memory on its way into the reel. */
    data class RecapSource(
        val url: String,
        val isVideo: Boolean,
        val title: String?,
        val dateText: String
    )

    /** How long each still holds. Long enough to look at, short enough that eight is not a chore. */
    private const val STILL_DURATION_US = 2_400_000L

    /** How much of each video is used. A reel is made of glimpses. */
    private const val CLIP_TAKE_MS = 3_000L

    /** Everything is normalised to this; a sequence of mixed sizes is refused by the muxer. */
    private const val RECAP_WIDTH = 1080
    private const val RECAP_HEIGHT = 1920

    /**
     * Assembles several memories into one real video.
     *
     * The screen showing this used to display a single existing memory - and, when the child had
     * none, a stock photograph of somebody else's baby - behind a play button that animated a
     * counter rather than playing anything. Nothing was ever assembled. This does the assembling.
     *
     * Each clip carries its own date, so a reel watched a year later still says when.
     */
    @OptIn(UnstableApi::class)
    suspend fun buildRecap(
        context: Context,
        sources: List<RecapSource>,
        isThai: Boolean,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> = runCatching {
        require(sources.isNotEmpty()) {
            if (isThai) "ยังไม่มีความทรงจำให้รวมเป็นคลิป" else "there are no memories to assemble"
        }

        onProgress(0.02f)

        // Pulled local first: signed URLs expire, and Transformer's image path will not fetch one.
        val local = withContext(Dispatchers.IO) {
            sources.mapIndexedNotNull { index, source ->
                runCatching {
                    val dir = File(context.cacheDir, "recap").apply { mkdirs() }
                    val suffix = if (source.isVideo) "mp4" else "jpg"
                    val file = File(dir, "recap_${System.currentTimeMillis()}_$index.$suffix")
                    URL(source.url).openStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                    source to file
                }.onFailure {
                    Log.w(TAG, "Skipping a memory the reel could not download: ${it.message}")
                }.getOrNull()
            }
        }

        require(local.isNotEmpty()) {
            if (isThai) "ดาวน์โหลดความทรงจำไม่สำเร็จ" else "none of the memories could be downloaded"
        }

        onProgress(0.15f)

        val items = local.map { (source, file) ->
            val overlays = mutableListOf<TextureOverlay>()

            withContext(Dispatchers.Default) {
                captionFrame(
                    RECAP_WIDTH,
                    RECAP_HEIGHT,
                    listOfNotNull(source.title?.takeIf { it.isNotBlank() }, source.dateText)
                        .joinToString(" • "),
                    NongRakStampStyle.PASTEL_CLOUD,
                    centreX = 0.5f,
                    centreY = 0.88f
                )
            }.let { overlays.add(BitmapOverlay.createStaticBitmapOverlay(it)) }

            withContext(Dispatchers.Default) {
                brandWatermarkFrame(context, RECAP_WIDTH, RECAP_HEIGHT)
            }?.let { overlays.add(BitmapOverlay.createStaticBitmapOverlay(it)) }

            val effects = Effects(
                emptyList(),
                listOf(
                    OverlayEffect(ImmutableList.copyOf(overlays)),
                    Presentation.createForWidthAndHeight(
                        RECAP_WIDTH, RECAP_HEIGHT, Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            )

            if (source.isVideo) {
                EditedMediaItem.Builder(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0)
                                .setEndPositionMs(CLIP_TAKE_MS)
                                .build()
                        )
                        .build()
                ).setEffects(effects).build()
            } else {
                EditedMediaItem.Builder(
                    MediaItem.Builder()
                        .setUri(Uri.fromFile(file))
                        // Without an explicit MIME the item is probed as video, finds no track and
                        // fails rather than being treated as a picture.
                        .setMimeType(MimeTypes.IMAGE_JPEG)
                        .build()
                )
                    .setDurationUs(STILL_DURATION_US)
                    .setFrameRate(OUTRO_FRAME_RATE)
                    .setEffects(effects)
                    .build()
            }
        }

        val outro = buildOutroItem(context, RECAP_WIDTH, RECAP_HEIGHT, isThai)
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "nongrak_recap_${System.currentTimeMillis()}.mp4")

        val sequenceBuilder = EditedMediaItemSequence.Builder()
        items.forEach { sequenceBuilder.addItem(it) }
        sequenceBuilder.addItem(outro)

        val composition = Composition.Builder(ImmutableList.of(sequenceBuilder.build()))
            // Stills carry no audio. Without a forced track the muxer drops audio for the whole
            // reel, silencing the video memories inside it.
            .experimentalSetForceAudioTrack(true)
            .build()

        awaitExport(context, composition, exportFile.absolutePath, onProgress)

        onProgress(1f)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
    }

    // -----------------------------------------------------------------------------------------
    // Studio edits
    // -----------------------------------------------------------------------------------------

    /**
     * Renders the studio's choices into a new clip.
     *
     * Before this existed the studio was decoration: every call site discarded the chosen effect
     * and showed a success toast, so a parent picked ละอองดาวนำโชค, tapped บันทึก, and kept the
     * original file. Anything the studio offers has to change the video or not be offered.
     *
     * Returns the source URI unchanged when [edit] asks for nothing. On failure the reason names
     * what the encoder actually reported, so the dialog can say more than "ไม่สำเร็จ".
     *
     * No watermark is burned here — [prepareWatermarkedVideoForExport] owns that and runs at share
     * time, so a clip edited twice does not end up wearing two badges.
     */
    suspend fun renderStudioEdit(
        context: Context,
        videoUri: Uri,
        edit: NongRakStudioEdit,
        onProgress: (Float) -> Unit = {}
    ): Result<Uri> {
        if (!edit.changesAnything) return Result.success(videoUri)
        return try {
            Result.success(renderStudio(context, videoUri, edit, onProgress))
        } catch (e: Exception) {
            Log.e(TAG, "Studio render failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun renderStudio(
        context: Context,
        videoUri: Uri,
        edit: NongRakStudioEdit,
        onProgress: (Float) -> Unit
    ): Uri {
        onProgress(0.05f)

        val (srcWidth, srcHeight) = videoSize(context, videoUri)

        val overlays = mutableListOf<TextureOverlay>()

        // Particles are drawn once and animated by moving the whole layer. A per-frame particle
        // system would mean regenerating a full-frame bitmap 30 times a second on the CPU while
        // the encoder is already saturating the device.
        particleFrame(edit.effect, srcWidth, srcHeight)?.let {
            overlays.add(DriftingOverlay(it))
        }

        edit.mascot.art?.let { art ->
            // The rigged companion, one bitmap per step of its idle loop, cycled by presentation
            // time. A single still would have been cheaper and would also have been the thing the
            // parent already said they did not want.
            val frames = withContext(Dispatchers.Default) {
                NongRakMascotFrames.render(context, art, srcWidth, srcHeight, edit)
            }
            if (frames.isNotEmpty()) overlays.add(LoopingOverlay(frames))
        }

        if (!edit.captionText.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                captionFrame(
                    srcWidth, srcHeight, edit.captionText, edit.stampStyle,
                    edit.captionX, edit.captionY
                )
            }.let { overlays.add(BitmapOverlay.createStaticBitmapOverlay(it)) }
        }

        // The licence badge is burned in HERE, at the moment the memory is made, rather than when
        // it is shared. A clip that went through the studio is already the finished article, and
        // stamping again on the way out gave it two badges.
        withContext(Dispatchers.Default) {
            brandWatermarkFrame(context, srcWidth, srcHeight)
        }?.let { overlays.add(BitmapOverlay.createStaticBitmapOverlay(it)) }

        val videoEffects = buildList<Effect> {
            // Speed first: it rewrites presentation timestamps, and the overlays below are
            // anchored to those timestamps. Applying it last would animate the sparkles against
            // the original clock rather than the new one.
            if (edit.speed != 1f) add(SpeedChangeEffect(edit.speed))
            addAll(colorGradeFor(edit.filter))
            if (overlays.isNotEmpty()) add(OverlayEffect(ImmutableList.copyOf(overlays)))
            // After the overlays so the decoration is reframed with the picture instead of being
            // cropped off by the new aspect ratio.
            edit.aspectRatio?.let {
                add(
                    Presentation.createForAspectRatio(
                        it.ratioWidth / it.ratioHeight, Presentation.LAYOUT_SCALE_TO_FIT
                    )
                )
            }
        }

        // Sonic keeps the pitch sane while matching the video's new duration. Without it the audio
        // track keeps the original length and drifts out of sync by the end of the clip.
        val audioProcessors: List<AudioProcessor> =
            if (edit.speed != 1f) listOf(SonicAudioProcessor().apply { setSpeed(edit.speed) })
            else emptyList()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(edit.trimStartMs)
                    .apply { edit.trimEndMs?.let { setEndPositionMs(it) } }
                    .build()
            )
            .build()

        val item = EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(!edit.keepOriginalAudio)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()

        onProgress(0.15f)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "nongrak_studio_${System.currentTimeMillis()}.mp4")

        val composition = Composition.Builder(
            ImmutableList.of(EditedMediaItemSequence.Builder(item).build())
        ).build()

        awaitExport(context, composition, exportFile.absolutePath, onProgress)

        onProgress(1.0f)
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", exportFile
        )
    }

    /**
     * Plays a pre-rendered loop, one bitmap per step, chosen by presentation time.
     *
     * The frames are full-frame layers already positioned, so there are no overlay settings to get
     * wrong and the mascot cannot drift away from where the preview showed it.
     */
    @OptIn(UnstableApi::class)
    private class LoopingOverlay(private val frames: List<Bitmap>) : BitmapOverlay() {
        override fun getBitmap(presentationTimeUs: Long): Bitmap {
            val step = (presentationTimeUs / FRAME_INTERVAL_US).toInt()
            // floorMod, not %: a negative timestamp from a seek would index backwards off the list.
            return frames[((step % frames.size) + frames.size) % frames.size]
        }

        private companion object {
            /** 12fps. The idle loop is a slow breath; more steps is memory spent on nothing. */
            const val FRAME_INTERVAL_US = 1_000_000L / 12
        }
    }

    /**
     * The horizontal NongRak logo, bottom-right, sized to the frame.
     *
     * Returns null rather than substituting anything if the asset will not decode: a memory
     * exported without a badge is a small loss, a memory exported with a grey box is a defaced one.
     */
    private fun brandWatermarkFrame(context: Context, width: Int, height: Int): Bitmap? {
        val logo = runCatching {
            BitmapFactory.decodeResource(
                context.resources,
                R.drawable.nongrak_logo_horizontal,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inScaled = false
                }
            )
        }.getOrNull() ?: return null

        val targetWidth = width * 0.30f
        val targetHeight = targetWidth * (logo.height.toFloat() / logo.width)
        val margin = width * 0.04f
        val left = width - targetWidth - margin
        val top = height - targetHeight - margin

        return transparentFrame(width, height) { canvas ->
            canvas.drawBitmap(
                logo,
                null,
                RectF(left, top, left + targetWidth, top + targetHeight),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    isFilterBitmap = true
                    // Slightly transparent so the badge reads as a mark on the memory rather than
                    // as part of what was filmed.
                    alpha = 235
                }
            )
        }
    }

    /**
     * A whole-frame decoration layer that breathes and drifts.
     *
     * The same bitmap every frame; only its alpha and position move. That is enough to read as
     * "animated sparkle" and costs one texture upload for the entire export.
     */
    @OptIn(UnstableApi::class)
    private class DriftingOverlay(private val layer: Bitmap) : BitmapOverlay() {

        override fun getBitmap(presentationTimeUs: Long): Bitmap = layer

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
            val phase = (presentationTimeUs / DRIFT_CYCLE_US) * TWO_PI
            return StaticOverlaySettings.Builder()
                .setAlphaScale(0.55f + 0.35f * sin(phase))
                .setBackgroundFrameAnchor(0f, DRIFT_TRAVEL * sin(phase * 0.5f))
                .build()
        }

        private companion object {
            const val TWO_PI = (2.0 * PI).toFloat()

            /** One rise-and-fall every four seconds — slow enough not to read as a glitch. */
            const val DRIFT_CYCLE_US = 4_000_000f

            /** Fraction of the frame height the layer travels, in normalised device coordinates. */
            const val DRIFT_TRAVEL = 0.06f
        }
    }

    /**
     * The chosen colour treatment, as GL operations.
     *
     * Deliberately gentle. A parent is grading their child's face, not a music video: skin that
     * has been pushed two stops warm reads as illness, and every one of these is applied over
     * whatever the phone's own camera processing already did.
     */
    @OptIn(UnstableApi::class)
    private fun colorGradeFor(filter: NongRakVideoFilter): List<Effect> = when (filter) {
        NongRakVideoFilter.NONE -> emptyList()
        NongRakVideoFilter.VINTAGE_FILM -> listOf(
            RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.02f).setBlueScale(0.86f).build(),
            Contrast(0.18f)
        )
        NongRakVideoFilter.RAINBOW_AURA -> listOf(
            HslAdjustment.Builder().adjustSaturation(28f).build()
        )
        NongRakVideoFilter.GOLDEN_HOUR -> listOf(
            RgbAdjustment.Builder().setRedScale(1.10f).setGreenScale(1.04f).setBlueScale(0.88f).build(),
            Contrast(0.08f)
        )
        NongRakVideoFilter.SOFT_PASTEL -> listOf(
            HslAdjustment.Builder().adjustSaturation(-18f).build(),
            Contrast(-0.12f)
        )
    }

    /**
     * The particle layer for a preset, or null when the preset is colour-only.
     *
     * Seeded from the preset id rather than the clock: re-rendering the same edit twice has to
     * produce the same video, or a parent who taps บันทึก again gets a different result for no
     * reason they can see.
     */
    internal fun particleFrame(effect: NongRakVideoEffect, width: Int, height: Int): Bitmap? {
        val spec = when (effect) {
            NongRakVideoEffect.STARLIGHT -> ParticleSpec(ParticleShape.STAR, Color.parseColor("#FFD54F"), 42)
            NongRakVideoEffect.BUBBLES -> ParticleSpec(ParticleShape.CIRCLE, Color.parseColor("#81D4FA"), 30)
            NongRakVideoEffect.BLOSSOM -> ParticleSpec(ParticleShape.PETAL, Color.parseColor("#F48FB1"), 34)
            NongRakVideoEffect.GOLDEN_CROWN -> ParticleSpec(ParticleShape.STAR, Color.parseColor("#FFB300"), 20)
            NongRakVideoEffect.TOY_KINGDOM -> ParticleSpec(ParticleShape.BLOCK, Color.parseColor("#FF8A65"), 26)
            else -> return null
        }

        val random = Random(effect.id.hashCode())
        val unit = width.coerceAtMost(height) / 100f

        return transparentFrame(width, height) { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.color }
            repeat(spec.count) {
                val cx = random.nextFloat() * width
                val cy = random.nextFloat() * height
                val r = unit * (0.8f + random.nextFloat() * 1.8f)
                paint.alpha = 90 + random.nextInt(120)
                when (spec.shape) {
                    ParticleShape.CIRCLE -> {
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = unit * 0.28f
                        canvas.drawCircle(cx, cy, r * 1.6f, paint)
                        paint.style = Paint.Style.FILL
                    }
                    ParticleShape.STAR -> drawFourPointStar(canvas, cx, cy, r, paint)
                    ParticleShape.PETAL -> canvas.drawOval(
                        RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.55f), paint
                    )
                    ParticleShape.BLOCK -> canvas.drawRoundRect(
                        RectF(cx - r, cy - r, cx + r, cy + r), r * 0.3f, r * 0.3f, paint
                    )
                }
            }
        }
    }

    /** A four-point sparkle: two tapered spikes crossed, which reads as light rather than as a dot. */
    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy - r * 2.4f)
            quadTo(cx, cy, cx + r * 2.4f, cy)
            quadTo(cx, cy, cx, cy + r * 2.4f)
            quadTo(cx, cy, cx - r * 2.4f, cy)
            quadTo(cx, cy, cx, cy - r * 2.4f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private enum class ParticleShape { STAR, CIRCLE, PETAL, BLOCK }

    private data class ParticleSpec(val shape: ParticleShape, val color: Int, val count: Int)

    /** The parent's caption, on a stamp-coloured pill above the licence badge. */
    internal fun captionFrame(
        width: Int,
        height: Int,
        text: String,
        stampStyle: NongRakStampStyle?,
        centreX: Float = 0.5f,
        centreY: Float = 0.84f
    ): Bitmap {
        val scale = width / 1080f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Long captions are trimmed rather than wrapped: two lines of text over a baby's face is
        // worse than a shortened sentence, and the full text is already saved on the memory.
        val maxTextWidth = width * 0.82f
        val shown = if (textPaint.measureText(text) <= maxTextWidth) text else {
            var cut = text
            while (cut.length > 1 && textPaint.measureText("$cut…") > maxTextWidth) {
                cut = cut.dropLast(1)
            }
            "$cut…"
        }

        val padH = 34f * scale
        val padV = 22f * scale
        val textWidth = textPaint.measureText(shown)
        val boxWidth = textWidth + padH * 2
        val boxHeight = textPaint.textSize + padV * 2
        // Placed by its centre, then held inside the frame: a parent dragging near an edge means
        // "as far over as it goes", not "half off the picture".
        val left = (centreX * width - boxWidth / 2f).coerceIn(0f, (width - boxWidth).coerceAtLeast(0f))
        val top = (centreY * height - boxHeight / 2f).coerceIn(0f, (height - boxHeight).coerceAtLeast(0f))

        val pillColor = (stampStyle ?: NongRakStampStyle.PASTEL_CLOUD).badgeColor
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                235,
                (pillColor.red * 255).toInt(),
                (pillColor.green * 255).toInt(),
                (pillColor.blue * 255).toInt()
            )
        }

        return transparentFrame(width, height) { canvas ->
            val rect = RectF(left, top, left + boxWidth, top + boxHeight)
            canvas.drawRoundRect(rect, boxHeight / 2f, boxHeight / 2f, bgPaint)
            canvas.drawText(shown, width / 2f, top + padV + textPaint.textSize * 0.8f, textPaint)
        }
    }
}
