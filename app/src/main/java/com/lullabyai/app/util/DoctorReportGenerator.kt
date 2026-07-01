package com.lullabyai.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the official "ข้อมูลเด็ก" child-data form (3 sections per the supplied template) as a
 * multi-page A4 PDF, populated with the child's real data. Labels/section titles are fixed Thai
 * (this is a Thai official document); the caller supplies only values. Android's PdfDocument renders
 * Thai via the device system font. Layout is block-based: every piece of content is measured first so
 * pages break cleanly and the navy header band shows the correct "หน้า X / N".
 */
object DoctorReportGenerator {

    // -------- input model (values only; the generator owns all labels) --------
    data class Vac(val ageGroup: String, val name: String, val dateGiven: String, val place: String, val note: String)
    data class Growth(val date: String, val weight: String, val height: String, val status: String, val note: String)
    /** One development domain row. Order is fixed by the generator (see DEV_DOMAINS). */
    data class DevStatus(val normal: Boolean, val monitor: Boolean, val note: String)

    data class ChildData(
        val documentRef: String,
        val recordDate: String,
        // §1 ข้อมูลเด็ก
        val childCode: String, val nickname: String, val birthdate: String,
        val age: String, val gender: String, val idNumber: String,
        val unitClass: String, val recorder: String, val updatedDate: String,
        // §2 วัคซีนที่ได้รับ
        val vaccines: List<Vac>,
        // §3 พัฒนาการ (exactly 5 rows, in DEV_DOMAINS order)
        val development: List<DevStatus>, val devObservation: String,
        // §4 โภชนาการ
        val regularFood: String, val allergyAvoid: String, val eatingProblem: String,
        val growth: List<Growth>, val nutritionAdvice: String,
        // §5 ประวัติสุขภาพ
        val chronicDisease: String, val drugFoodAllergy: String, val medications: String,
        val majorIllness: String, val surgeryHospital: String, val followupDoctor: String,
        val healthHistoryDetail: String, val currentCare: String,
        // §6 สรุปและข้อเสนอแนะ
        val overallSummary: String, val recommendations: String
    )

    val DEV_DOMAINS = listOf(
        "กล้ามเนื้อมัดใหญ่",
        "กล้ามเนื้อมัดเล็กและการใช้มือ",
        "ภาษาและการสื่อสาร",
        "สังคม อารมณ์ และพฤติกรรม",
        "การเรียนรู้และการช่วยเหลือตนเอง"
    )

    // -------- geometry --------
    private const val PAGE_W = 595f   // A4 @ 72dpi
    private const val PAGE_H = 842f
    private const val MARGIN = 45f
    private const val BAND_H = 96f
    private const val CONTENT_TOP = BAND_H + 22f
    private const val CONTENT_BOTTOM = PAGE_H - 46f
    private val CONTENT_W get() = PAGE_W - MARGIN * 2

    // -------- colors --------
    private val NAVY = Color.rgb(22, 38, 60)
    private val SECTION_BG = Color.rgb(236, 238, 242)
    private val BOX_BORDER = Color.rgb(150, 162, 178)
    private val GRID = Color.rgb(200, 202, 214)
    private val LABEL = Color.rgb(90, 96, 108)
    private val TEXT = Color.rgb(26, 28, 30)
    private val FOOT = Color.rgb(120, 124, 132)
    private val WHITE = Color.WHITE

    // -------- paints --------
    private fun p(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
        textSize = size; this.color = color; isAntiAlias = true
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** A measured, drawable block. height includes its own internal spacing; gapAfter is space below.
     *  keepWithNext forces the following block onto the same page (avoids orphaned section bars / headers). */
    private class Block(val height: Float, val gapAfter: Float, val keepWithNext: Boolean = false, val draw: (android.graphics.Canvas, Float) -> Unit)

    fun generate(context: Context, data: ChildData): File {
        val titleP = p(22f, WHITE, true)
        val subP = p(9f, Color.rgb(205, 214, 228))
        val metaP = p(9f, Color.rgb(205, 214, 228))
        val secP = p(13f, TEXT, true)
        val labelP = p(9f, LABEL)
        val valueP = p(10.5f, TEXT)
        val headP = p(9f, TEXT, true)
        val cellP = p(9.5f, TEXT)
        val footP = p(8f, FOOT)
        val boxStroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = BOX_BORDER; isAntiAlias = true }
        val gridStroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = GRID; isAntiAlias = true }
        val secFill = Paint().apply { style = Paint.Style.FILL; color = SECTION_BG; isAntiAlias = true }
        val headFill = Paint().apply { style = Paint.Style.FILL; color = SECTION_BG; isAntiAlias = true }
        val bandFill = Paint().apply { style = Paint.Style.FILL; color = NAVY }
        val footLine = Paint().apply { strokeWidth = 1f; color = Color.rgb(210, 214, 222) }

        // ---- text helpers ----
        fun wrap(text: String, paint: Paint, maxW: Float): List<String> {
            if (text.isBlank()) return listOf("")
            val out = ArrayList<String>(); var cur = StringBuilder()
            for (ch in text) {
                if (ch == '\n') { out.add(cur.toString()); cur = StringBuilder(); continue }
                cur.append(ch)
                if (paint.measureText(cur.toString()) > maxW) {
                    cur.deleteCharAt(cur.length - 1); out.add(cur.toString()); cur = StringBuilder().append(ch)
                }
            }
            out.add(cur.toString()); return out
        }
        fun fit(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var t = text
            while (t.isNotEmpty() && paint.measureText("$t…") > maxW) t = t.dropLast(1)
            return "$t…"
        }
        fun valueOrDash(v: String) = v.ifBlank { "-" }

        // ---- block builders ----
        val blocks = ArrayList<Block>()

        fun sectionBar(title: String) {
            blocks.add(Block(28f, 12f, keepWithNext = true) { c, top ->
                c.drawRoundRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + 26f), 6f, 6f, secFill)
                c.drawText(title, MARGIN + 12f, top + 18f, secP)
            })
        }

        // A row of up to 3 labeled boxed fields.
        fun fieldRow(vararg fields: Pair<String, String>) {
            val gap = 16f
            val cols = fields.size
            val cw = (CONTENT_W - gap * (cols - 1)) / cols
            blocks.add(Block(46f, 10f) { c, top ->
                fields.forEachIndexed { i, (label, value) ->
                    val x = MARGIN + i * (cw + gap)
                    c.drawText(label, x, top + 9f, labelP)
                    val boxTop = top + 14f
                    c.drawRoundRect(RectF(x, boxTop, x + cw, boxTop + 30f), 3f, 3f, boxStroke)
                    if (value.isNotBlank()) c.drawText(fit(value, valueP, cw - 12f), x + 8f, boxTop + 20f, valueP)
                }
            })
        }

        // A bordered text area with a label above; height grows with the (wrapped) content.
        fun textArea(label: String, value: String, minLines: Int = 3) {
            val innerW = CONTENT_W - 16f
            val lines = wrap(value, cellP, innerW)
            val rows = maxOf(minLines, lines.size)
            val boxH = rows * 15f + 14f
            blocks.add(Block(14f + boxH, 14f) { c, top ->
                c.drawText(label, MARGIN, top + 9f, labelP)
                val boxTop = top + 14f
                c.drawRoundRect(RectF(MARGIN, boxTop, MARGIN + CONTENT_W, boxTop + boxH), 4f, 4f, boxStroke)
                if (value.isNotBlank()) {
                    var ly = boxTop + 18f
                    lines.forEach { ln -> c.drawText(ln, MARGIN + 8f, ly, cellP); ly += 15f }
                }
            })
        }

        // Generic data table. colW must sum to CONTENT_W. Each row paginates independently; the
        // header is emitted as its own block so a clean break never orphans it badly.
        fun table(headers: List<String>, colW: List<Float>, rows: List<List<String>>, centerCols: Set<Int> = emptySet()) {
            val rowH = 30f
            // header block
            blocks.add(Block(rowH, 0f, keepWithNext = true) { c, top ->
                c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + rowH), headFill)
                var x = MARGIN
                headers.forEachIndexed { i, h ->
                    c.drawRect(RectF(x, top, x + colW[i], top + rowH), gridStroke)
                    c.drawText(fit(h, headP, colW[i] - 10f), x + 6f, top + 19f, headP)
                    x += colW[i]
                }
            })
            val drawRows = rows.ifEmpty { List(3) { headers.map { "" } } }  // a few blank rows if no data
            drawRows.forEach { row ->
                blocks.add(Block(rowH, 0f) { c, top ->
                    var x = MARGIN
                    row.forEachIndexed { i, cell ->
                        c.drawRect(RectF(x, top, x + colW[i], top + rowH), gridStroke)
                        if (cell.isNotBlank()) {
                            if (i in centerCols) {
                                val tw = cellP.measureText(cell)
                                c.drawText(cell, x + (colW[i] - tw) / 2f, top + 19f, cellP)
                            } else c.drawText(fit(cell, cellP, colW[i] - 10f), x + 6f, top + 19f, cellP)
                        }
                        x += colW[i]
                    }
                })
            }
            blocks.add(Block(0f, 14f) { _, _ -> })  // spacer after table
        }

        // Development table with two checkbox columns.
        fun devTable() {
            val rowH = 30f
            val cw = listOf(220f, 70f, 90f, CONTENT_W - 220f - 70f - 90f)
            val headers = listOf("ด้านพัฒนาการ", "ปกติ", "ควรติดตาม", "หมายเหตุ")
            blocks.add(Block(rowH, 0f, keepWithNext = true) { c, top ->
                c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + rowH), headFill)
                var x = MARGIN
                headers.forEachIndexed { i, h ->
                    c.drawRect(RectF(x, top, x + cw[i], top + rowH), gridStroke)
                    c.drawText(h, x + 6f, top + 19f, headP); x += cw[i]
                }
            })
            DEV_DOMAINS.forEachIndexed { idx, domain ->
                val st = data.development.getOrNull(idx) ?: DevStatus(false, false, "")
                blocks.add(Block(rowH, 0f) { c, top ->
                    var x = MARGIN
                    // domain
                    c.drawRect(RectF(x, top, x + cw[0], top + rowH), gridStroke)
                    c.drawText(fit(domain, cellP, cw[0] - 10f), x + 6f, top + 19f, cellP); x += cw[0]
                    // ปกติ checkbox
                    c.drawRect(RectF(x, top, x + cw[1], top + rowH), gridStroke)
                    checkbox(c, x + cw[1] / 2f - 7f, top + rowH / 2f - 7f, st.normal, boxStroke, cellP); x += cw[1]
                    // ควรติดตาม checkbox
                    c.drawRect(RectF(x, top, x + cw[2], top + rowH), gridStroke)
                    checkbox(c, x + cw[2] / 2f - 7f, top + rowH / 2f - 7f, st.monitor, boxStroke, cellP); x += cw[2]
                    // note
                    c.drawRect(RectF(x, top, x + cw[3], top + rowH), gridStroke)
                    if (st.note.isNotBlank()) c.drawText(fit(st.note, cellP, cw[3] - 10f), x + 6f, top + 19f, cellP)
                })
            }
            blocks.add(Block(0f, 14f) { _, _ -> })
        }

        // ================= build the document content =================
        sectionBar("1. ข้อมูลเด็ก")
        fieldRow("รหัสเด็ก" to data.childCode, "ชื่อเล่น" to data.nickname, "วันเดือนปีเกิด" to data.birthdate)
        fieldRow("อายุ" to data.age, "เพศ" to data.gender, "เลขประจำตัว/เลขสูติบัตร" to data.idNumber)
        fieldRow("หน่วยงาน/ห้องเรียน" to data.unitClass, "ผู้บันทึก" to data.recorder, "วันที่ปรับปรุงข้อมูล" to data.updatedDate)
        blocks.add(Block(8f, 0f) { _, _ -> })

        sectionBar("2. วัคซีนที่ได้รับ")
        table(
            listOf("ช่วงอายุ", "วัคซีนที่ได้รับ", "วันที่ได้รับ", "สถานที่/หน่วยบริการ", "หมายเหตุ"),
            listOf(80f, 150f, 90f, 110f, CONTENT_W - 80f - 150f - 90f - 110f),
            data.vaccines.map { listOf(it.ageGroup, it.name, it.dateGiven, it.place, it.note) }
        )

        sectionBar("3. พัฒนาการ")
        devTable()
        textArea("ข้อสังเกตด้านพัฒนาการ/สิ่งที่ควรติดตาม", data.devObservation)

        sectionBar("4. โภชนาการ")
        fieldRow("อาหารที่รับประทานเป็นประจำ" to data.regularFood, "อาหารที่แพ้หรือควรหลีกเลี่ยง" to data.allergyAvoid, "ปัญหาการกิน/ข้อจำกัด" to data.eatingProblem)
        blocks.add(Block(6f, 0f) { _, _ -> })
        table(
            listOf("วันที่", "น้ำหนัก", "ส่วนสูง", "ภาวะโภชนาการ", "หมายเหตุ"),
            listOf(90f, 80f, 80f, 140f, CONTENT_W - 90f - 80f - 80f - 140f),
            data.growth.map { listOf(it.date, it.weight, it.height, it.status, it.note) }
        )
        textArea("คำแนะนำด้านโภชนาการเพิ่มเติม", data.nutritionAdvice)

        sectionBar("5. ประวัติสุขภาพ")
        fieldRow("โรคประจำตัว" to data.chronicDisease, "ประวัติแพ้ยา/อาหาร" to data.drugFoodAllergy)
        fieldRow("ยาหรือการดูแลที่ใช้อยู่" to data.medications, "ประวัติเจ็บป่วยสำคัญ" to data.majorIllness)
        fieldRow("ประวัติผ่าตัด/นอนโรงพยาบาล" to data.surgeryHospital, "แพทย์/หน่วยบริการที่ติดตาม" to data.followupDoctor)
        blocks.add(Block(6f, 0f) { _, _ -> })
        textArea("รายละเอียดประวัติสุขภาพ", data.healthHistoryDetail, minLines = 4)
        textArea("การดูแลสุขภาพปัจจุบัน หรือคำแนะนำจากแพทย์/ผู้ดูแล", data.currentCare)

        sectionBar("6. สรุปและข้อเสนอแนะ")
        textArea("สรุปภาพรวม/ประเด็นสำคัญที่ควรติดตาม", data.overallSummary, minLines = 4)
        textArea("ข้อเสนอแนะสำหรับผู้ดูแล/ครู/เจ้าหน้าที่", data.recommendations)

        // ================= paginate =================
        val pages = ArrayList<ArrayList<Pair<Float, Block>>>()
        var current = ArrayList<Pair<Float, Block>>()
        var y = CONTENT_TOP
        blocks.forEachIndexed { i, b ->
            // A keepWithNext block must fit together with the following block, or break before it.
            val needed = if (b.keepWithNext && i + 1 < blocks.size) b.height + b.gapAfter + blocks[i + 1].height else b.height
            if (y + needed > CONTENT_BOTTOM && current.isNotEmpty()) {
                pages.add(current); current = ArrayList(); y = CONTENT_TOP
            }
            current.add(y to b)
            y += b.height + b.gapAfter
        }
        if (current.isNotEmpty()) pages.add(current)
        val total = pages.size

        // ================= draw =================
        val doc = PdfDocument()
        pages.forEachIndexed { idx, pageBlocks ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), idx + 1).create())
            val c = page.canvas
            // header band
            c.drawRect(RectF(0f, 0f, PAGE_W, BAND_H), bandFill)
            c.drawText("ข้อมูลเด็ก", MARGIN, 44f, titleP)
            c.drawText("เอกสารข้อมูลเด็กสำหรับบันทึกสุขภาพ พัฒนาการ โภชนาการ และวัคซีน", MARGIN, 66f, subP)
            val pageStr = "หน้า ${idx + 1} / $total"
            c.drawText(pageStr, PAGE_W - MARGIN - metaP.measureText(pageStr), 44f, metaP)
            val refStr = "เลขที่เอกสาร: ${data.documentRef}"
            c.drawText(refStr, PAGE_W - MARGIN - metaP.measureText(refStr), 66f, metaP)
            // content
            pageBlocks.forEach { (top, b) -> b.draw(c, top) }
            // footer
            c.drawLine(MARGIN, CONTENT_BOTTOM + 16f, PAGE_W - MARGIN, CONTENT_BOTTOM + 16f, footLine)
            c.drawText("ข้อมูลนี้เป็นข้อมูลส่วนบุคคล ควรเก็บรักษาอย่างปลอดภัย", MARGIN, CONTENT_BOTTOM + 30f, footP)
            val rd = "วันที่บันทึก: ${data.recordDate}"
            c.drawText(rd, PAGE_W - MARGIN - footP.measureText(rd), CONTENT_BOTTOM + 30f, footP)
            doc.finishPage(page)
        }

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "child_data_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    /** Draws a 14×14 form checkbox; a navy tick (two strokes, not a font glyph) when checked. */
    private fun checkbox(c: android.graphics.Canvas, x: Float, y: Float, checked: Boolean, stroke: Paint, @Suppress("UNUSED_PARAMETER") textPaint: Paint) {
        c.drawRoundRect(RectF(x, y, x + 14f, y + 14f), 2f, 2f, stroke)
        if (checked) {
            val tick = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1.8f; color = NAVY; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            // tick shape inside the 14×14 box
            c.drawLine(x + 3f, y + 7.5f, x + 6f, y + 10.5f, tick)
            c.drawLine(x + 6f, y + 10.5f, x + 11f, y + 4f, tick)
        }
    }
}
