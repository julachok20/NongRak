package com.lullabyai.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Settings › Export My Data as a readable A4 PDF. Lays out the plain-text report from
 * UserDataExport.toReport across as many pages as it needs; "========" lines become headings.
 */
object DataExportPdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val LINE_H = 15f

    fun write(context: Context, report: String): File {
        val brand = Color.rgb(0x37, 0x57, 0xAE)
        val body = Paint().apply { textSize = 10.5f; color = Color.rgb(30, 32, 38); isAntiAlias = true }
        val heading = Paint().apply {
            textSize = 13f; color = brand; isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val footer = Paint().apply { textSize = 9f; color = Color.rgb(100, 106, 122); isAntiAlias = true }
        val maxW = PAGE_W - MARGIN * 2

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            val next = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
            next.canvas.drawText("NongRak AI • $pageNo", MARGIN, PAGE_H - 24f, footer)
            page = next
            y = MARGIN
        }

        fun draw(text: String, paint: Paint, extraBefore: Float = 0f) {
            if (y + extraBefore + LINE_H > PAGE_H - MARGIN) newPage() else y += extraBefore
            y += LINE_H
            page?.canvas?.drawText(text, MARGIN, y, paint)
        }

        try {
            newPage()
            report.lines().forEachIndexed { index, line ->
                val trimmed = line.trim()
                val isHeading = trimmed.startsWith("======== ") && trimmed.endsWith(" ========")
                when {
                    index == 0 -> draw(line, heading)
                    isHeading -> draw(trimmed.removeSurrounding("======== ", " ========"), heading, extraBefore = 8f)
                    line.isBlank() -> y += LINE_H / 2
                    else -> wrap(line, body, maxW).forEach { draw(it, body) }
                }
            }
            page?.let { doc.finishPage(it) }

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, "nongrak_my_data_${System.currentTimeMillis()}.pdf")
            FileOutputStream(out).use { doc.writeTo(it) }
            return out
        } finally {
            doc.close()
        }
    }

    /** Breaks by measured width, indenting continuation lines. Character-based, so Thai (no spaces) wraps too. */
    private fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
        val indent = text.takeWhile { it == ' ' } + "  "
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (ch in text) {
            cur.append(ch)
            if (paint.measureText(cur.toString()) > maxW) {
                cur.deleteCharAt(cur.length - 1)
                out.add(cur.toString())
                cur = StringBuilder(indent).append(ch)
            }
        }
        out.add(cur.toString())
        return out
    }
}
