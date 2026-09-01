package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.lullabyai.app.data.remote.AiProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Picks the part of a clip worth keeping, and asks the model to name it.
 *
 * The cut is decided ON THE PHONE, from the clip's own sound and movement. Only a contact sheet of
 * the chosen moment leaves the device, and only to write a caption - a video of someone's child is
 * not a thing to upload wholesale for a convenience feature.
 *
 * Nothing here applies itself. [suggest] returns a proposal the editor pushes onto the undo stack,
 * so a parent sees what changed and can take it back in one tap.
 */
object NongRakAutoEditor {

    private const val TAG = "NongRakAutoEditor"

    /** One second per bucket. Finer than this and a single laugh smears across the whole clip. */
    internal const val BUCKET_MS = 1000L

    /** Keep roughly this much of the clip, so the tool actually cuts something. */
    private const val TARGET_FRACTION = 0.7f
    private const val MAX_WINDOW_MS = 15_000L
    private const val MIN_WINDOW_MS = 3_000L

    /** How many frames go into the contact sheet the model reads. */
    private const val SHEET_FRAMES = 8
    private const val SHEET_COLUMNS = 4
    private const val SHEET_CELL_WIDTH = 256

    /** Longer than a stamp badge can carry without becoming a paragraph over a child's face. */
    private const val MAX_CAPTION_CHARS = 42

    /**
     * A proposed edit, plus the reason it was proposed.
     *
     * The reason is shown to the parent. "AI ปรับให้แล้ว" with no explanation is indistinguishable
     * from the app having done something at random.
     */
    data class Suggestion(
        val edit: NongRakStudioEdit,
        val reasonTh: String,
        val reasonEn: String,
        val captionFromAi: Boolean
    )

    /**
     * Analyses [videoUri] and proposes an edit built on top of [current].
     *
     * On top rather than replacing: a parent who already chose a mascot should not lose it because
     * they then asked for a suggestion.
     */
    suspend fun suggest(
        context: Context,
        videoUri: Uri,
        current: NongRakStudioEdit,
        childName: String?,
        isThai: Boolean
    ): Result<Suggestion> = withContext(Dispatchers.Default) {
        runCatching {
            val durationMs = durationOf(context, videoUri)
            require(durationMs > 0) { "clip has no readable duration" }

            val bucketCount = ((durationMs + BUCKET_MS - 1) / BUCKET_MS).toInt().coerceAtLeast(1)
            val audio = audioEnergy(context, videoUri, bucketCount)
            val motion = motionEnergy(context, videoUri, durationMs, bucketCount)
            val scores = combine(audio, motion)

            val windowMs = (durationMs * TARGET_FRACTION).toLong()
                .coerceIn(MIN_WINDOW_MS, MAX_WINDOW_MS)
                .coerceAtMost(durationMs)
            val windowBuckets = (windowMs / BUCKET_MS).toInt().coerceAtLeast(1)
            val startBucket = pickWindow(scores, windowBuckets)

            val trimStart = startBucket * BUCKET_MS
            val trimEnd = (trimStart + windowMs).coerceAtMost(durationMs)

            val meanAudio = audio?.meanOf(startBucket, windowBuckets) ?: 0f
            val meanMotion = motion.meanOf(startBucket, windowBuckets)
            val mood = moodFor(meanAudio, meanMotion)

            val caption = runCatching {
                captionFor(context, videoUri, trimStart, trimEnd, childName, mood)
            }.getOrNull()

            val trimmed = trimEnd - trimStart < durationMs - BUCKET_MS

            Suggestion(
                edit = current.copy(
                    trimStartMs = trimStart,
                    trimEndMs = trimEnd.takeIf { it < durationMs },
                    effect = mood.effect,
                    filter = mood.filter,
                    captionText = caption ?: current.captionText
                ),
                reasonTh = buildString {
                    if (trimmed) append("เลือกช่วง ${formatClock(trimStart)}–${formatClock(trimEnd)} ")
                    append(mood.reasonTh)
                    if (caption != null) append(" และเขียนข้อความให้แล้ว")
                },
                reasonEn = buildString {
                    if (trimmed) append("Kept ${formatClock(trimStart)}–${formatClock(trimEnd)}. ")
                    append(mood.reasonEn)
                    if (caption != null) append(" Caption written for you.")
                },
                captionFromAi = caption != null
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scoring - pure, and therefore the part worth testing
    // -----------------------------------------------------------------------------------------

    /**
     * The liveliest run of [windowBuckets] consecutive buckets.
     *
     * A plain sliding sum rather than anything cleverer: the signal is one sample per second over a
     * clip measured in seconds, and a smarter search would be fitting noise.
     */
    internal fun pickWindow(scores: FloatArray, windowBuckets: Int): Int {
        if (scores.isEmpty() || windowBuckets >= scores.size) return 0
        var best = 0
        var bestSum = Float.NEGATIVE_INFINITY
        for (start in 0..(scores.size - windowBuckets)) {
            var sum = 0f
            for (i in start until start + windowBuckets) sum += scores[i]
            if (sum > bestSum) {
                bestSum = sum
                best = start
            }
        }
        return best
    }

    /**
     * Sound counts for more than movement.
     *
     * A baby laughing while sitting still is the moment a parent wants; a shaky pan across an empty
     * room is not. Motion alone ranks those the wrong way round.
     */
    internal fun combine(audio: FloatArray?, motion: FloatArray): FloatArray {
        if (audio == null) return motion
        return FloatArray(motion.size) { i ->
            0.65f * audio.getOrElse(i) { 0f } + 0.35f * motion[i]
        }
    }

    /** What the chosen window sounds and looks like, mapped to a preset. */
    internal data class Mood(
        val effect: NongRakVideoEffect,
        val filter: NongRakVideoFilter,
        val reasonTh: String,
        val reasonEn: String,
        val promptHintTh: String
    )

    internal fun moodFor(audio: Float, motion: Float): Mood {
        val loud = audio >= 0.5f
        val busy = motion >= 0.5f
        return when {
            loud && busy -> Mood(
                NongRakVideoEffect.STARLIGHT, NongRakVideoFilter.NONE,
                "ช่วงที่สนุกและมีเสียงมากที่สุด ใส่ละอองดาวให้",
                "The liveliest, loudest stretch - added sparkles.",
                "ช่วงเวลาที่สนุกสนาน มีเสียงหัวเราะ"
            )
            loud && !busy -> Mood(
                NongRakVideoEffect.GOLDEN_CROWN, NongRakVideoFilter.GOLDEN_HOUR,
                "มีเสียงชัดแต่ภาพนิ่ง เหมือนช่วงเวลาสำคัญ ใส่โทนแสงทอง",
                "Clear sound over a still frame - a moment. Warmed the light.",
                "ช่วงเวลาสำคัญ เด็กกำลังส่งเสียงหรือพูด"
            )
            !loud && busy -> Mood(
                NongRakVideoEffect.BUBBLES, NongRakVideoFilter.NONE,
                "กำลังเล่นอยู่เงียบ ๆ ใส่ฟองสบู่ให้",
                "Playing quietly - added bubbles.",
                "ช่วงเวลาที่เด็กกำลังเล่นอย่างเงียบ ๆ"
            )
            else -> Mood(
                NongRakVideoEffect.BLOSSOM, NongRakVideoFilter.SOFT_PASTEL,
                "ช่วงที่สงบที่สุด ใส่โทนพาสเทลนุ่มตา",
                "The calmest stretch - softened the tone.",
                "ช่วงเวลาที่สงบ อบอุ่น"
            )
        }
    }

    /** Mean of [count] values from [from], guarding both ends. */
    internal fun FloatArray.meanOf(from: Int, count: Int): Float {
        if (isEmpty()) return 0f
        val end = (from + count).coerceAtMost(size)
        val start = from.coerceIn(0, (end - 1).coerceAtLeast(0))
        if (end <= start) return this[start]
        var sum = 0f
        for (i in start until end) sum += this[i]
        return sum / (end - start)
    }

    // -----------------------------------------------------------------------------------------
    // Signals
    // -----------------------------------------------------------------------------------------

    private fun durationOf(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * RMS loudness per bucket, normalised to 0..1, or null when the clip has no usable audio.
     *
     * Normalised against the clip's own peak rather than an absolute level: a nursery recorded at
     * arm's length and one recorded across a room share no absolute scale, and the only question
     * here is which second of THIS clip is the loud one.
     */
    private fun audioEnergy(context: Context, uri: Uri, bucketCount: Int): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(track)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val sums = DoubleArray(bucketCount)
            val counts = LongArray(bucketCount)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)
                        val size = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                if (outIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                        var sum = 0.0
                        var n = 0
                        while (shorts.hasRemaining()) {
                            val v = shorts.get().toDouble()
                            sum += v * v
                            n++
                        }
                        if (n > 0) {
                            val bucket = (info.presentationTimeUs / 1000 / BUCKET_MS)
                                .toInt().coerceIn(0, bucketCount - 1)
                            sums[bucket] += sum
                            counts[bucket] += n
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    // The decoder has nothing left and nothing more is coming. Without this the
                    // loop spins forever on a stream that ends without an EOS flag.
                    outputDone = true
                }
            }

            val rms = FloatArray(bucketCount) { i ->
                if (counts[i] == 0L) 0f else sqrt(sums[i] / counts[i]).toFloat()
            }
            normalise(rms)
        } catch (e: Exception) {
            // A clip whose audio will not decode still gets a motion-only suggestion.
            Log.w(TAG, "Audio analysis failed, falling back to motion only: ${e.message}")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * How much the picture changes per bucket, normalised to 0..1.
     *
     * Frames are crushed to 32x32 grey before comparison. At that size sensor noise and compression
     * shimmer average out, and what survives is somebody actually moving.
     */
    private fun motionEnergy(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bucketCount: Int
    ): FloatArray {
        val retriever = MediaMetadataRetriever()
        val scores = FloatArray(bucketCount)
        try {
            retriever.setDataSource(context, uri)
            var previous: IntArray? = null
            for (i in 0 until bucketCount) {
                val atMs = (i * BUCKET_MS + BUCKET_MS / 2).coerceAtMost(durationMs)
                val frame = retriever.getFrameAtTime(
                    atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue
                val grey = greyThumbnail(frame)
                frame.recycle()
                previous?.let { prev ->
                    var diff = 0L
                    for (p in grey.indices) diff += abs(grey[p] - prev[p]).toLong()
                    scores[i] = diff.toFloat() / grey.size
                }
                previous = grey
            }
        } catch (e: Exception) {
            Log.w(TAG, "Motion analysis failed, treating the clip as still: ${e.message}")
        } finally {
            runCatching { retriever.release() }
        }
        return normalise(scores)
    }

    private fun greyThumbnail(source: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(source, 32, 32, true)
        val pixels = IntArray(32 * 32)
        small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        small.recycle()
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            // Rec. 601 luma, integer-scaled: the ratio matters, the units do not.
            ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
    }

    /** Scales to 0..1 against the array's own peak. Returns zeros when nothing varies. */
    internal fun normalise(values: FloatArray): FloatArray {
        val max = values.maxOrNull() ?: 0f
        if (max <= 0f) return FloatArray(values.size)
        return FloatArray(values.size) { values[it] / max }
    }

    // -----------------------------------------------------------------------------------------
    // Caption
    // -----------------------------------------------------------------------------------------

    /**
     * Eight frames of the chosen window, tiled into ONE image, sent once.
     *
     * A contact sheet rather than eight calls: the model sees the whole moment in a single request,
     * and the proxy's existing single-image path carries it with no change.
     */
    private suspend fun captionFor(
        context: Context,
        uri: Uri,
        fromMs: Long,
        toMs: Long,
        childName: String?,
        mood: Mood
    ): String? {
        val sheet = contactSheet(context, uri, fromMs, toMs) ?: return null
        val jpeg = ByteArrayOutputStream().use { out ->
            sheet.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.toByteArray()
        }
        sheet.recycle()

        val who = childName?.takeIf { it.isNotBlank() }?.let { "ชื่อ $it" } ?: "เด็กเล็ก"
        val prompt = buildString {
            append("ภาพนี้คือ 8 เฟรมเรียงต่อกันจากคลิปวิดีโอสั้นของ$who ")
            append("บริบท: ${mood.promptHintTh} ")
            append("เขียนข้อความสั้น ๆ หนึ่งบรรทัดสำหรับติดบนวิดีโอความทรงจำนี้ ")
            append("ภาษาไทย ไม่เกิน $MAX_CAPTION_CHARS ตัวอักษร ")
            append("ห้ามใส่เครื่องหมายคำพูด ห้ามอธิบาย ตอบเฉพาะข้อความนั้น")
        }

        val result = AiProxyClient.generate(
            prompt = prompt,
            useSearch = false,
            maxTokens = 64,
            system = "คุณช่วยผู้ปกครองเขียนข้อความสั้น ๆ ที่อบอุ่นสำหรับคลิปความทรงจำของลูก",
            imageBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
            imageMimeType = "image/jpeg"
        )

        return result.text
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.trim()
            ?.trim('"', '“', '”', '\'')
            ?.take(MAX_CAPTION_CHARS)
            ?.takeIf { it.isNotBlank() }
    }

    private fun contactSheet(context: Context, uri: Uri, fromMs: Long, toMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frames = (0 until SHEET_FRAMES).mapNotNull { i ->
                val atMs = fromMs + (toMs - fromMs) * i / SHEET_FRAMES
                retriever.getFrameAtTime(
                    atMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
            }
            if (frames.isEmpty()) return null

            val first = frames.first()
            val cellHeight = (SHEET_CELL_WIDTH * first.height / first.width.toFloat()).toInt()
                .coerceAtLeast(1)
            val rows = (frames.size + SHEET_COLUMNS - 1) / SHEET_COLUMNS
            val sheet = Bitmap.createBitmap(
                SHEET_CELL_WIDTH * SHEET_COLUMNS,
                cellHeight * rows,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(sheet)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            frames.forEachIndexed { index, frame ->
                val left = (index % SHEET_COLUMNS) * SHEET_CELL_WIDTH
                val top = (index / SHEET_COLUMNS) * cellHeight
                canvas.drawBitmap(
                    frame,
                    null,
                    Rect(left, top, left + SHEET_CELL_WIDTH, top + cellHeight),
                    paint
                )
                frame.recycle()
            }
            sheet
        } catch (e: Exception) {
            Log.w(TAG, "Contact sheet failed, suggestion goes out without a caption: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
