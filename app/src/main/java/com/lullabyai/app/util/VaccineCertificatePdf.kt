package com.lullabyai.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Auto-generated ใบรับรองวัคซีน for doctor-initiated certificate send (Stitch 46). */
object VaccineCertificatePdf {

    data class CertificateData(
        val childName: String,
        val hn: String,
        val vaccineName: String,
        val doseLabel: String?,
        val dateGiven: String,
        val lotNumber: String?,
        val injectionSite: String?,
        val providerName: String,
        val clinicName: String?
    )

    fun generate(context: Context, data: CertificateData, isThai: Boolean): File {
        val pageW = 595
        val pageH = 842
        val margin = 48f
        val brand = Color.rgb(0x1e, 0x40, 0xa1)
        val text = Color.rgb(25, 28, 30)
        val label = Color.rgb(100, 106, 122)

        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
            textSize = size
            this.color = color
            isAntiAlias = true
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titleP = paint(22f, brand, true)
        val subP = paint(11f, label)
        val sectionP = paint(12f, brand, true)
        val bodyP = paint(11f, text)
        val bodyBoldP = paint(11f, text, true)

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        var y = margin + 20f

        val title = if (isThai) "ใบรับรองการฉีดวัคซีน" else "Vaccine Administration Certificate"
        canvas.drawText(title, margin, y, titleP)
        y += 28f
        canvas.drawText("NongRak AI", margin, y, subP)
        y += 36f

        canvas.drawLine(margin, y, pageW - margin, y, paint(1f, Color.LTGRAY))
        y += 24f

        fun row(lbl: String, value: String) {
            canvas.drawText(lbl, margin, y, sectionP)
            canvas.drawText(value, margin + 160f, y, bodyBoldP)
            y += 22f
        }

        row(if (isThai) "ชื่อผู้รับวัคซีน" else "Patient", data.childName)
        row(if (isThai) "HN" else "HN", data.hn)
        row(if (isThai) "ชื่อวัคซีน" else "Vaccine", data.vaccineName)
        data.doseLabel?.let { row(if (isThai) "ครั้งที่" else "Dose", it) }
        row(if (isThai) "วันที่ฉีด" else "Date given", formatDate(data.dateGiven, isThai))
        data.lotNumber?.takeIf { it.isNotBlank() }?.let { row("Lot", it) }
        data.injectionSite?.takeIf { it.isNotBlank() }?.let {
            row(if (isThai) "ตำแหน่งฉีด" else "Site", it)
        }
        row(if (isThai) "ผู้ให้บริการ" else "Provider", data.providerName)
        data.clinicName?.takeIf { it.isNotBlank() }?.let {
            row(if (isThai) "สถานพยาบาล" else "Facility", it)
        }

        y += 16f
        val stamp = SimpleDateFormat(if (isThai) "d MMM yyyy HH:mm" else "MMM d, yyyy HH:mm", Locale.getDefault())
            .format(Date())
        canvas.drawText(
            if (isThai) "ออกใบรับรองเมื่อ $stamp" else "Issued $stamp",
            margin, y, subP
        )

        doc.finishPage(page)
        val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(dir, "vaccine_cert_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun formatDate(iso: String, isThai: Boolean): String = runCatching {
        val d = java.time.LocalDate.parse(iso.take(10))
        if (isThai) "${d.dayOfMonth}/${d.monthValue}/${d.year + 543}"
        else d.toString()
    }.getOrDefault(iso)
}
