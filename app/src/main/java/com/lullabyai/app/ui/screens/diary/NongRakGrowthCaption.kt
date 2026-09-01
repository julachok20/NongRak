package com.lullabyai.app.ui.screens.diary

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.lullabyai.app.data.remote.AiProxyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Writes the sentence under a Then-and-Now pair.
 *
 * Both photos go up as ONE side-by-side image rather than as two requests: the model is being asked
 * about the difference between them, and a difference is not something anyone can see one picture
 * at a time.
 */
object NongRakGrowthCaption {

    private const val TAG = "NongRakGrowthCaption"

    /** Each half of the pair before tiling. Big enough to read a face, small enough to send. */
    private const val CELL_WIDTH = 512

    /** Two or three warm sentences. Longer stops being a caption and becomes an essay. */
    private const val MAX_CHARS = 180

    /**
     * The rule the whole feature stands on.
     *
     * Shown two photos of the same child months apart, a model reaches unprompted for the
     * vocabulary of comparison - bigger, rounder, thinner, healthier. A parent does not want a
     * verdict on their child's body, and several of those words land as an accusation about
     * feeding. So the prompt forbids the entire category rather than trying to steer tone inside it.
     */
    private const val TONE_RULES_TH =
        "ห้ามพูดถึงน้ำหนัก รูปร่าง ขนาดตัว ผิวพรรณ หรือสุขภาพ ห้ามเปรียบเทียบว่าดีขึ้นหรือแย่ลง " +
            "ให้พูดถึงการเติบโต พัฒนาการ ความสุข และช่วงเวลาที่ผ่านมาเท่านั้น " +
            "น้ำเสียงอบอุ่น เป็นบวก เหมือนเพื่อนที่ดีใจกับพ่อแม่"

    /**
     * Describes what changed between [earlyUrl] and [recentUrl], warmly.
     *
     * Returns a failure rather than a placeholder when the model or the network is unavailable -
     * the screen then shows the pair with no caption, which is honest, instead of a generic line
     * pretending to be about this particular child.
     */
    suspend fun describe(
        earlyUrl: String,
        recentUrl: String,
        earlyDateText: String,
        recentDateText: String,
        childName: String?,
        isThai: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sheet = sideBySide(earlyUrl, recentUrl)
                ?: error(if (isThai) "โหลดรูปไม่สำเร็จ" else "could not load the photos")

            val jpeg = ByteArrayOutputStream().use { out ->
                sheet.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            sheet.recycle()

            val who = childName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ลูกน้อย" else "the child")
            val prompt = buildString {
                append("ภาพนี้มีสองรูปเรียงกัน ซ้ายคือ \"$earlyDateText\" ขวาคือ \"$recentDateText\" ")
                append("เป็นเด็กคนเดียวกันชื่อ $who ")
                append("เขียนข้อความอบอุ่น 2-3 ประโยค เล่าว่าช่วงเวลาที่ผ่านมา $who เติบโตและเปลี่ยนไปอย่างไร ")
                append("$TONE_RULES_TH ")
                append("ตอบเป็นภาษาไทย ไม่เกิน $MAX_CHARS ตัวอักษร ตอบเฉพาะข้อความ ห้ามใส่เครื่องหมายคำพูด")
            }

            val result = AiProxyClient.generate(
                prompt = prompt,
                useSearch = false,
                maxTokens = 220,
                system = "คุณช่วยผู้ปกครองเขียนคำบรรยายความทรงจำของลูก ด้วยน้ำเสียงที่อบอุ่นและให้กำลังใจเสมอ",
                imageBase64 = Base64.encodeToString(jpeg, Base64.NO_WRAP),
                imageMimeType = "image/jpeg"
            )

            result.text
                ?.trim()
                ?.trim('"', '“', '”')
                ?.take(MAX_CHARS)
                ?.takeIf { it.isNotBlank() }
                ?: error(if (isThai) "AI ไม่ได้ตอบกลับ" else "the model returned nothing")
        }
    }

    /**
     * The two photos in one frame, left = then, right = now.
     *
     * Letterboxed onto equal cells rather than stretched: a squashed face is exactly the artefact
     * that would provoke a remark about the shape of a child's head.
     */
    private fun sideBySide(earlyUrl: String, recentUrl: String): Bitmap? {
        val early = fetchBitmap(earlyUrl) ?: return null
        val recent = fetchBitmap(recentUrl) ?: run {
            early.recycle()
            return null
        }

        val cellHeight = maxOf(
            CELL_WIDTH * early.height / early.width,
            CELL_WIDTH * recent.height / recent.width
        ).coerceAtLeast(1)

        val sheet = Bitmap.createBitmap(CELL_WIDTH * 2, cellHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

        listOf(early to 0, recent to CELL_WIDTH).forEach { (bitmap, offsetX) ->
            val scale = minOf(
                CELL_WIDTH.toFloat() / bitmap.width,
                cellHeight.toFloat() / bitmap.height
            )
            val drawWidth = (bitmap.width * scale).toInt()
            val drawHeight = (bitmap.height * scale).toInt()
            val left = offsetX + (CELL_WIDTH - drawWidth) / 2
            val top = (cellHeight - drawHeight) / 2
            canvas.drawBitmap(
                bitmap, null, Rect(left, top, left + drawWidth, top + drawHeight), paint
            )
            bitmap.recycle()
        }
        return sheet
    }

    private fun fetchBitmap(url: String): Bitmap? = runCatching {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    }.onFailure {
        Log.w(TAG, "Could not read one of the photos: ${it.message}")
    }.getOrNull()
}
