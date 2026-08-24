package com.lullabyai.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [HealthBook] as a real multi-page PDF.
 *
 * The second renderer over the SAME page structure the on-screen book uses. Neither builds its
 * own content: if a chapter changes, both change together, so the copy a parent prints and the
 * one they read in the app cannot disagree.
 */
object HealthBookPdf {

    // A4 at 72dpi, matching the other generators in this app so exports look like one family.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 44f

    private const val BLUE = "#2743C4"
    private const val INK = "#1B2559"
    private const val MUTED = "#6B7280"
    private const val ALERT = "#B26A00"

    suspend fun export(context: Context, book: HealthBook, isThai: Boolean): File =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()
            var pageNo = 1

            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create()).also { p ->
                drawCover(p.canvas, book, isThai)
                doc.finishPage(p)
            }

            doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create()).also { p ->
                drawContents(p.canvas, book, isThai)
                doc.finishPage(p)
            }

            book.pages.forEachIndexed { index, page ->
                val pdfPage = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo++).create()
                )
                drawPage(pdfPage.canvas, page, index + 1, book.pages.size, book, isThai)
                doc.finishPage(pdfPage)
            }

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = book.childName.replace(Regex("[^\\p{L}\\p{N}]+"), "_").take(40)
            val file = File(dir, "health_book_${safeName}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        }

    /** Hands the finished book to whatever the parent wants to do with it. */
    fun share(context: Context, file: File, childName: String, isThai: Boolean) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                if (isThai) "สมุดสุขภาพ $childName" else "$childName health book"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, if (isThai) "ส่งสมุดสุขภาพ" else "Share health book")
        )
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    private fun paint(size: Float, color: String, bold: Boolean = false) = Paint().apply {
        this.color = Color.parseColor(color)
        textSize = size
        strokeWidth = size
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun line(width: Float, color: String) = Paint().apply {
        this.color = Color.parseColor(color)
        strokeWidth = width
        isAntiAlias = true
    }

    private fun drawCover(c: Canvas, book: HealthBook, th: Boolean) {
        c.drawColor(Color.parseColor(BLUE))
        val pale = paint(12f, "#C3CDF5")

        c.drawText(
            if (th) "สมุดสุขภาพประจำตัว" else "PERSONAL HEALTH RECORD",
            MARGIN, 220f, pale
        )
        c.drawText(book.childName, MARGIN, 262f, paint(30f, "#FFFFFF", bold = true))
        c.drawText(book.ageText, MARGIN, 288f, pale)

        var y = 360f
        fun row(label: String, value: String) {
            c.drawText(label, MARGIN, y, pale)
            c.drawText(value, MARGIN + 180f, y, paint(12f, "#FFFFFF"))
            y += 22f
        }
        row(if (th) "วันเกิด" else "Date of birth", book.birthdate)
        row(if (th) "กรุ๊ปเลือด" else "Blood group", book.bloodGroup)
        row(if (th) "ออกเมื่อ" else "Issued", book.generatedAt)

        c.drawText(
            if (th) "ออกโดยแอปน้องรัก" else "Issued by the NongRak app",
            MARGIN, PAGE_H - 60f, pale
        )
    }

    private fun drawContents(c: Canvas, book: HealthBook, th: Boolean) {
        c.drawColor(Color.WHITE)
        var y = MARGIN + 30f
        c.drawText(if (th) "สารบัญ" else "Contents", MARGIN, y, paint(20f, INK, bold = true))
        y += 12f
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line(1f, BLUE))
        y += 26f

        // Chapters, not pages — a contents list containing "(cont.)" is a page index in disguise.
        book.pages.mapIndexed { i, p -> p.chapter to (i + 1) }
            .groupBy({ it.first }, { it.second })
            .map { (chapter, numbers) -> chapter to numbers.min() }
            .forEach { (chapter, page) ->
                c.drawText(chapter, MARGIN, y, paint(13f, INK))
                c.drawText("$page", PAGE_W - MARGIN - 20f, y, paint(12f, MUTED))
                y += 22f
            }

        c.drawText(
            if (th)
                "สมุดเล่มนี้สรุปจากข้อมูลที่บันทึกไว้ ไม่ใช่การวินิจฉัยทางการแพทย์"
            else
                "Compiled from recorded data. Not a medical diagnosis.",
            MARGIN, PAGE_H - 60f, paint(10f, MUTED)
        )
    }

    private fun drawPage(
        c: Canvas,
        page: BookPage,
        number: Int,
        total: Int,
        book: HealthBook,
        th: Boolean
    ) {
        c.drawColor(Color.WHITE)
        var y = MARGIN + 20f

        // Running head: the child's name on every page, so a single sheet separated from the rest
        // still identifies whose record it is.
        c.drawText(book.childName, MARGIN, MARGIN, paint(9f, MUTED))
        c.drawText(page.chapter.uppercase(), MARGIN, y, paint(9f, BLUE, bold = true))
        y += 20f
        c.drawText(page.title, MARGIN, y, paint(17f, INK, bold = true))
        y += 10f
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line(1f, BLUE))
        y += 22f

        val contentW = PAGE_W - MARGIN * 2

        page.blocks.forEach { block ->
            when (block) {
                is BookBlock.Heading -> {
                    y += 8f
                    c.drawText(block.text, MARGIN, y, paint(13f, INK, bold = true))
                    y += 18f
                }

                is BookBlock.Field -> {
                    c.drawText(block.label, MARGIN, y, paint(11f, MUTED))
                    val vp = paint(11f, if (block.alert) ALERT else INK, bold = block.alert)
                    c.drawText(fit(block.value, vp, contentW * 0.55f), MARGIN + contentW * 0.45f, y, vp)
                    y += 17f
                }

                is BookBlock.Paragraph -> {
                    val p = paint(10.5f, "#4B5563")
                    wrap(block.text, p, contentW).forEach {
                        c.drawText(it, MARGIN, y, p)
                        y += 14f
                    }
                    y += 4f
                }

                is BookBlock.Row -> {
                    val p = paint(10.5f, if (block.header) INK else "#4B5563", bold = block.header)
                    val colW = contentW / block.cells.size.coerceAtLeast(1)
                    block.cells.forEachIndexed { i, cell ->
                        c.drawText(fit(cell, p, colW - 6f), MARGIN + colW * i, y, p)
                    }
                    y += 15f
                }

                is BookBlock.Note -> {
                    c.drawText(block.text, MARGIN, y, paint(10.5f, "#059669"))
                    y += 16f
                }

                BookBlock.Divider -> {
                    y += 6f
                    c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line(0.6f, "#E5E7EB"))
                    y += 12f
                }
            }
        }

        c.drawText("$number / $total", PAGE_W / 2f - 14f, PAGE_H - MARGIN, paint(10f, MUTED))
    }

    /** Greedy wrap. Thai has no spaces, so a word-only split would never break a Thai sentence. */
    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        var line = StringBuilder()
        for (ch in text) {
            line.append(ch)
            if (p.measureText(line.toString()) > maxW) {
                val s = line.toString()
                val cut = s.lastIndexOf(' ')
                if (cut > 0) {
                    out += s.substring(0, cut)
                    line = StringBuilder(s.substring(cut + 1))
                } else {
                    // Mid-string break: the only option that works for Thai.
                    out += s.dropLast(1)
                    line = StringBuilder(s.takeLast(1))
                }
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    private fun fit(text: String, p: Paint, maxW: Float): String {
        if (p.measureText(text) <= maxW) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
