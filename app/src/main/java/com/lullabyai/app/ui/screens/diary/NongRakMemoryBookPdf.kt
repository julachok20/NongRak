package com.lullabyai.app.ui.screens.diary

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.lullabyai.app.data.model.DiaryMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * The memory book, as a real PDF.
 *
 * The button for this used to show "กำลังส่งออกสมุดภาพ PDF ของลูกน้อย…" and write nothing at all.
 * This writes the file.
 *
 * A4 at 72dpi, a cover then one page per memory, photos embedded with their text around them.
 * Videos appear as a title and a date - a PDF cannot hold a clip, and drawing a play button on
 * paper would be the same class of lie this replaces.
 */
object NongRakMemoryBookPdf {

    private const val TAG = "NongRakMemoryBookPdf"

    /** A4 at 72dpi, the unit PdfDocument works in. */
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    /** Beyond this the book stops - it is already long, and every page is a network fetch. */
    private const val MAX_PAGES = 40

    private val INK = Color.parseColor("#1E214D")
    private val MUTED = Color.parseColor("#6B6F8D")
    private val ACCENT = Color.parseColor("#3757AE")

    /**
     * Writes the book and returns it.
     *
     * Photos come from their signed URLs, so this needs the network. A memory whose image will not
     * download keeps its page and loses only its picture, rather than failing the whole book.
     */
    suspend fun build(
        context: Context,
        childName: String,
        memories: List<DiaryMemory>,
        signedUrls: Map<String, String>,
        isThai: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            require(memories.isNotEmpty()) {
                if (isThai) "ยังไม่มีความทรงจำให้ทำเป็นสมุดภาพ" else "there are no memories to bind"
            }

            val doc = PdfDocument()
            val ordered = memories.sortedBy { it.memoryDate }.take(MAX_PAGES)

            drawCover(doc, childName, ordered, isThai)
            ordered.forEachIndexed { index, memory ->
                val photo = memory.mediaPath
                    ?.takeIf { memory.mediaType == "photo" }
                    ?.let { signedUrls[it] }
                    ?.let { fetchBitmap(it) }
                drawMemoryPage(doc, memory, photo, index + 2, isThai)
                photo?.recycle()
            }

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "nongrak_memory_book_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    private fun drawCover(
        doc: PdfDocument,
        childName: String,
        memories: List<DiaryMemory>,
        isThai: Boolean
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)

        c.drawRect(0f, 0f, PAGE_W.toFloat(), 190f, Paint().apply { color = ACCENT })

        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText(
            if (isThai) "สมุดความทรงจำของ$childName" else "$childName's Memory Book",
            MARGIN, 106f, title
        )

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 13f
        }
        val span = listOfNotNull(memories.firstOrNull()?.memoryDate, memories.lastOrNull()?.memoryDate)
        if (span.size == 2) {
            c.drawText(
                "${formatMemoryDate(span[0], isThai)} – ${formatMemoryDate(span[1], isThai)}",
                MARGIN, 134f, sub
            )
        }

        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 14f
        }
        c.drawText(
            if (isThai) "รวม ${memories.size} ความทรงจำ" else "${memories.size} memories",
            MARGIN, 240f, body
        )

        c.drawText(
            "NongRak AI",
            MARGIN,
            PAGE_H - MARGIN,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 11f }
        )

        doc.finishPage(page)
    }

    private fun drawMemoryPage(
        doc: PdfDocument,
        memory: DiaryMemory,
        photo: Bitmap?,
        pageNumber: Int,
        isThai: Boolean
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        val c = page.canvas
        c.drawColor(Color.WHITE)
        var y = MARGIN + 20f

        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleText = memory.title?.takeIf { it.isNotBlank() }
            ?: (if (isThai) "ความทรงจำ" else "A memory")
        y = drawWrapped(c, titleText, MARGIN, y, PAGE_W - MARGIN * 2, heading, 24f)

        y += 6f
        c.drawText(
            formatMemoryDate(memory.memoryDate, isThai),
            MARGIN, y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 12f }
        )
        y += 22f

        if (photo != null) {
            val maxW = PAGE_W - MARGIN * 2
            val maxH = 380f
            val scale = minOf(maxW / photo.width, maxH / photo.height)
            val w = photo.width * scale
            val h = photo.height * scale
            c.drawBitmap(
                photo, null,
                RectF(MARGIN, y, MARGIN + w, y + h),
                Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
            )
            y += h + 22f
        } else if (memory.mediaType == "video") {
            // Says what it is, rather than drawing a play button that can never be pressed.
            c.drawText(
                if (isThai) "[ความทรงจำแบบวิดีโอ — เปิดดูได้ในแอป]"
                else "[Video memory — watch it in the app]",
                MARGIN, y,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 12f }
            )
            y += 24f
        }

        memory.description?.takeIf { it.isNotBlank() }?.let { text ->
            drawWrapped(
                c, text, MARGIN, y, PAGE_W - MARGIN * 2,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; textSize = 13f },
                19f
            )
        }

        val foot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MUTED; textSize = 10f }
        c.drawText("NongRak AI", MARGIN, PAGE_H - MARGIN, foot)
        c.drawText(
            "$pageNumber",
            PAGE_W - MARGIN,
            PAGE_H - MARGIN,
            Paint(foot).apply { textAlign = Paint.Align.RIGHT }
        )

        doc.finishPage(page)
    }

    /**
     * Wraps by measuring rather than by splitting on spaces.
     *
     * Thai does not put spaces between words, so a space-splitting wrapper produces one enormous
     * unbroken line and everything past the margin is lost. `breakText` counts what actually fits.
     */
    private fun drawWrapped(
        c: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
        lineHeight: Float
    ): Float {
        var y = startY
        var remaining = text
        while (remaining.isNotEmpty() && y < PAGE_H - MARGIN * 2) {
            val fit = paint.breakText(remaining, true, maxWidth, null)
            if (fit <= 0) break
            c.drawText(remaining.substring(0, fit), x, y, paint)
            remaining = remaining.substring(fit)
            y += lineHeight
        }
        return y
    }

    private fun fetchBitmap(url: String): Bitmap? = runCatching {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }
    }.onFailure {
        Log.w(TAG, "A memory's photo would not download; its page keeps the text: ${it.message}")
    }.getOrNull()
}
