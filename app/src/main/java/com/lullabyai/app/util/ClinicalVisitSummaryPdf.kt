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

/** One-page visit summary for Stitch 41 "พิมพ์ใบสรุป" — current write-up fields only. */
object ClinicalVisitSummaryPdf {

    data class VisitData(
        val childName: String,
        val hn: String,
        val visitReason: String?,
        val symptoms: String,
        val diagnosis: String,
        val treatmentPlan: String,
        val doctorAdvice: String,
        val weightKg: String,
        val heightCm: String,
        val temperatureC: String,
        val followUpDate: String?,
        val prescriptions: List<Pair<String, String>>
    )

    fun generate(context: Context, data: VisitData, isThai: Boolean): File {
        val pageW = 595
        val pageH = 842
        val margin = 48f
        val brand = Color.rgb(0x37, 0x57, 0xAE)
        val text = Color.rgb(30, 32, 38)
        val label = Color.rgb(100, 106, 122)

        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
            textSize = size
            this.color = color
            isAntiAlias = true
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val titleP = paint(18f, brand, true)
        val metaP = paint(10f, label)
        val sectionP = paint(12f, brand, true)
        val bodyP = paint(11f, text)

        fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val out = ArrayList<String>()
            var cur = StringBuilder()
            for (ch in text) {
                if (ch == '\n') {
                    out.add(cur.toString())
                    cur = StringBuilder()
                    continue
                }
                cur.append(ch)
                if (paint.measureText(cur.toString()) > maxW) {
                    cur.deleteCharAt(cur.length - 1)
                    out.add(cur.toString())
                    cur = StringBuilder().append(ch)
                }
            }
            out.add(cur.toString())
            return out
        }

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        val maxW = pageW - margin * 2
        var y = margin

        val title = if (isThai) "ใบสรุปการตรวจ" else "Visit Summary"
        canvas.drawText(title, margin, y + 20f, titleP)
        y += 36f

        val dateStr = SimpleDateFormat("d MMM yyyy HH:mm", if (isThai) Locale("th", "TH") else Locale.US)
            .format(Date())
        canvas.drawText(dateStr, margin, y, metaP)
        y += 22f

        canvas.drawText("${data.childName}  ·  HN: ${data.hn}", margin, y, bodyP)
        y += 18f
        data.visitReason?.takeIf { it.isNotBlank() }?.let {
            canvas.drawText(it, margin, y, metaP)
            y += 20f
        }

        fun section(label: String, value: String) {
            canvas.drawText(label, margin, y, sectionP)
            y += 16f
            for (line in wrap(value.ifBlank { "-" }, bodyP, maxW)) {
                canvas.drawText(line, margin, y, bodyP)
                y += 15f
            }
            y += 10f
        }

        val vitals = buildString {
            if (data.weightKg.isNotBlank()) append(if (isThai) "น้ำหนัก ${data.weightKg} kg" else "Weight ${data.weightKg} kg")
            if (data.heightCm.isNotBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(if (isThai) "ส่วนสูง ${data.heightCm} cm" else "Height ${data.heightCm} cm")
            }
            if (data.temperatureC.isNotBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(if (isThai) "อุณหภูมิ ${data.temperatureC} °C" else "Temp ${data.temperatureC} °C")
            }
        }

        section(if (isThai) "อาการ / การตรวจ" else "Symptoms / Exam", data.symptoms)
        section(if (isThai) "การวินิจฉัย" else "Diagnosis", data.diagnosis)
        if (vitals.isNotBlank()) section(if (isThai) "สัญญาณชีพ" else "Vitals", vitals)
        val plan = buildString {
            append(data.treatmentPlan.trim())
            data.prescriptions.forEach { (name, howTo) ->
                if (isNotEmpty()) append("\n")
                append("• $name")
                if (howTo.isNotBlank()) append(" — $howTo")
            }
        }
        section(if (isThai) "แผนการรักษา / ยา" else "Treatment / Rx", plan)
        if (data.doctorAdvice.isNotBlank()) {
            section(if (isThai) "คำแนะนำ" else "Advice", data.doctorAdvice)
        }
        data.followUpDate?.takeIf { it.isNotBlank() }?.let {
            section(if (isThai) "นัดครั้งถัดไป" else "Follow-up", it)
        }

        doc.finishPage(page)
        val out = File(context.cacheDir, "visit_summary_${System.currentTimeMillis()}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
