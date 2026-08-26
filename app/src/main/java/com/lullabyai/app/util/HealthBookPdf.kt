package com.lullabyai.app.util

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Exports the health book as a real multi-page PDF.
 *
 * Thin on purpose: every page is drawn by [HealthBookPageRenderer], the same code the on-screen
 * book uses. This file only owns paper size, page order, and where the file lands — so a change
 * to the form's layout cannot reach the printed copy without also reaching the app.
 */
object HealthBookPdf {

    // A4 at 72dpi, matching the renderer's coordinate space one-to-one.
    private const val PAGE_W = 595
    private const val PAGE_H = 842

    suspend fun export(context: Context, book: HealthBook, isThai: Boolean): File =
        withContext(Dispatchers.IO) {
            val doc = PdfDocument()

            book.pages.indices.forEach { index ->
                val page = doc.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create()
                )
                HealthBookPageRenderer.draw(page.canvas, context, book, index)
                doc.finishPage(page)
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
}
