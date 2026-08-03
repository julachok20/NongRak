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
    data class Vac(
        val ageGroup: String, val name: String, val dateGiven: String, val place: String, val note: String,
        // #10 provenance/certification. Raw flags, not text — this file owns all labels.
        val fromMophBook: Boolean = false,
        val certified: Boolean = false
    )
    data class Growth(val date: String, val weight: String, val height: String, val status: String, val note: String)
    /** One development domain row. Order is fixed by the generator (see DEV_DOMAINS). */
    data class DevStatus(val normal: Boolean, val monitor: Boolean, val note: String)
    data class MedDoc(val fileName: String, val uploadedDate: String)

    data class ChildData(
        val documentRef: String,
        val recordDate: String,
        // §1 ข้อมูลเด็ก
        val childCode: String, val nickname: String, val birthdate: String,
        val age: String, val gender: String, val idNumber: String,
        val nationality: String, val ethnicity: String, val religion: String,
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
        // §6 การนอนและการทานยา (added — was tracked in-app but never reached this export)
        val sleepSummary: String, val medicationSummary: String,
        // §7 สรุปและข้อเสนอแนะ
        val overallSummary: String, val recommendations: String,
        // §8 เอกสารแนบ (medical documents — optional category)
        val medicalDocs: List<MedDoc> = emptyList()
    )

    val ALL_CATEGORIES = setOf("basic", "vaccine", "milestones", "nutrition", "health", "ai", "documents")

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

    // -------- colors — NongRak brand blue (Primary = #3757AE in Color.kt) instead of generic
    // navy/gray, and an accent-bar + banded-row style instead of the old boxed-grid look --------
    private val BRAND = Color.rgb(0x37, 0x57, 0xAE)
    private val BRAND_TINT = Color.rgb(234, 238, 248)
    private val ROW_ALT = Color.rgb(245, 247, 251)
    private val BOX_BORDER = Color.rgb(210, 216, 230)
    private val HAIRLINE = Color.rgb(224, 228, 238)
    private val LABEL = Color.rgb(100, 106, 122)
    private val TEXT = Color.rgb(30, 32, 38)
    private val FOOT = Color.rgb(130, 134, 144)
    private val WHITE = Color.WHITE

    // -------- paints --------
    private fun p(size: Float, color: Int, bold: Boolean = false) = Paint().apply {
        textSize = size; this.color = color; isAntiAlias = true
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    /** A measured, drawable block. height includes its own internal spacing; gapAfter is space below.
     *  keepWithNext forces the following block onto the same page (avoids orphaned section bars / headers). */
    private class Block(val height: Float, val gapAfter: Float, val keepWithNext: Boolean = false, val draw: (android.graphics.Canvas, Float) -> Unit)

    /** categories: any of "basic","vaccine","milestones","nutrition","health","ai","documents" —
     *  gates which sections render (§7 summary/recommendations always renders; "ai" only gates
     *  whether the AI-generated paragraph appears within it). detailed=false skips the free-text
     *  observation/notes boxes but keeps all tables and headline fields. */
    fun generate(context: Context, data: ChildData, categories: Set<String> = ALL_CATEGORIES, detailed: Boolean = true): File {
        val titleP = p(23f, WHITE, true)
        val subP = p(9f, Color.rgb(220, 228, 245))
        val metaP = p(9f, Color.rgb(220, 228, 245))
        val secP = p(13f, BRAND, true)
        val labelP = p(9f, LABEL)
        val valueP = p(10.5f, TEXT)
        val headP = p(9f, BRAND, true)
        val cellP = p(9.5f, TEXT)
        val footP = p(8f, FOOT)
        val boxStroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = BOX_BORDER; isAntiAlias = true }
        val hairline = Paint().apply { strokeWidth = 1f; color = HAIRLINE; isAntiAlias = true }
        val secAccent = Paint().apply { style = Paint.Style.FILL; color = BRAND; isAntiAlias = true }
        val secTint = Paint().apply { style = Paint.Style.FILL; color = BRAND_TINT; isAntiAlias = true }
        val headFill = Paint().apply { style = Paint.Style.FILL; color = BRAND_TINT; isAntiAlias = true }
        val rowAltFill = Paint().apply { style = Paint.Style.FILL; color = ROW_ALT; isAntiAlias = true }
        val bandFill = Paint().apply { style = Paint.Style.FILL; color = BRAND }
        val footLine = Paint().apply { strokeWidth = 1f; color = HAIRLINE }

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

        // Left accent bar + light brand tint instead of a flat gray box — reads as a section
        // heading rather than a form-field label, and carries the app's brand color throughout.
        fun sectionBar(title: String) {
            blocks.add(Block(30f, 14f, keepWithNext = true) { c, top ->
                c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + 28f), secTint)
                c.drawRect(RectF(MARGIN, top, MARGIN + 4f, top + 28f), secAccent)
                c.drawText(title, MARGIN + 14f, top + 19f, secP)
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
        // header is emitted as its own block so a clean break never orphans it badly. Banded rows
        // (alternating tint) + a single bottom hairline per row reads cleaner on a scan than a full
        // grid of borders on every cell.
        fun table(headers: List<String>, colW: List<Float>, rows: List<List<String>>, centerCols: Set<Int> = emptySet()) {
            val rowH = 28f
            // header block
            blocks.add(Block(rowH, 0f, keepWithNext = true) { c, top ->
                c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + rowH), headFill)
                var x = MARGIN
                headers.forEachIndexed { i, h ->
                    c.drawText(fit(h, headP, colW[i] - 10f), x + 8f, top + 18f, headP)
                    x += colW[i]
                }
            })
            val drawRows = rows.ifEmpty { List(3) { headers.map { "" } } }  // a few blank rows if no data
            drawRows.forEachIndexed { rowIdx, row ->
                blocks.add(Block(rowH, 0f) { c, top ->
                    if (rowIdx % 2 == 1) c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + rowH), rowAltFill)
                    var x = MARGIN
                    row.forEachIndexed { i, cell ->
                        if (cell.isNotBlank()) {
                            if (i in centerCols) {
                                val tw = cellP.measureText(cell)
                                c.drawText(cell, x + (colW[i] - tw) / 2f, top + 18f, cellP)
                            } else c.drawText(fit(cell, cellP, colW[i] - 10f), x + 8f, top + 18f, cellP)
                        }
                        x += colW[i]
                    }
                    c.drawLine(MARGIN, top + rowH, MARGIN + CONTENT_W, top + rowH, hairline)
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
                headers.forEachIndexed { i, h -> c.drawText(h, x + 8f, top + 19f, headP); x += cw[i] }
            })
            DEV_DOMAINS.forEachIndexed { idx, domain ->
                val st = data.development.getOrNull(idx) ?: DevStatus(false, false, "")
                blocks.add(Block(rowH, 0f) { c, top ->
                    if (idx % 2 == 1) c.drawRect(RectF(MARGIN, top, MARGIN + CONTENT_W, top + rowH), rowAltFill)
                    var x = MARGIN
                    c.drawText(fit(domain, cellP, cw[0] - 10f), x + 8f, top + 19f, cellP); x += cw[0]
                    checkbox(c, x + cw[1] / 2f - 7f, top + rowH / 2f - 7f, st.normal, boxStroke, cellP); x += cw[1]
                    checkbox(c, x + cw[2] / 2f - 7f, top + rowH / 2f - 7f, st.monitor, boxStroke, cellP); x += cw[2]
                    if (st.note.isNotBlank()) c.drawText(fit(st.note, cellP, cw[3] - 10f), x + 8f, top + 19f, cellP)
                    c.drawLine(MARGIN, top + rowH, MARGIN + CONTENT_W, top + rowH, hairline)
                })
            }
            blocks.add(Block(0f, 14f) { _, _ -> })
        }

        // ================= build the document content (gated by selected categories) =================
        if ("basic" in categories) {
            sectionBar("1. ข้อมูลเด็ก")
            fieldRow("รหัสเด็ก" to data.childCode, "ชื่อเล่น" to data.nickname, "วันเดือนปีเกิด" to data.birthdate)
            fieldRow("อายุ" to data.age, "เพศ" to data.gender, "เลขประจำตัว/เลขสูติบัตร" to data.idNumber)
            fieldRow("สัญชาติ" to data.nationality, "เชื้อชาติ" to data.ethnicity, "ศาสนา" to data.religion)
            fieldRow("หน่วยงาน/ห้องเรียน" to data.unitClass, "ผู้บันทึก" to data.recorder, "วันที่ปรับปรุงข้อมูล" to data.updatedDate)
            blocks.add(Block(8f, 0f) { _, _ -> })
        }

        if ("vaccine" in categories) {
            sectionBar("2. วัคซีนที่ได้รับ")
            table(
                listOf("ช่วงอายุ", "วัคซีนที่ได้รับ", "วันที่ได้รับ", "สถานที่/หน่วยบริการ", "การรับรอง", "หมายเหตุ"),
                listOf(70f, 130f, 80f, 95f, 95f, CONTENT_W - 70f - 130f - 80f - 95f - 95f),
                data.vaccines.map {
                    // #10: a doctor reading the PDF needs to know whether a dose was typed in by the
                    // parent, transcribed from the ministry book, or backed by a real certificate.
                    val certification = listOfNotNull(
                        if (it.fromMophBook) "จากสมุดวัคซีน สธ." else null,
                        if (it.certified) "มีใบรับรอง" else null
                    ).joinToString(" • ").ifEmpty { "-" }
                    listOf(it.ageGroup, it.name, it.dateGiven, it.place, certification, it.note)
                }
            )
        }

        if ("milestones" in categories) {
            sectionBar("3. พัฒนาการ")
            devTable()
            if (detailed) textArea("ข้อสังเกตด้านพัฒนาการ/สิ่งที่ควรติดตาม", data.devObservation)
        }

        if ("nutrition" in categories) {
            sectionBar("4. โภชนาการและการเติบโต")
            fieldRow("อาหารที่รับประทานเป็นประจำ" to data.regularFood, "อาหารที่แพ้หรือควรหลีกเลี่ยง" to data.allergyAvoid, "ปัญหาการกิน/ข้อจำกัด" to data.eatingProblem)
            blocks.add(Block(6f, 0f) { _, _ -> })
            table(
                listOf("วันที่", "น้ำหนัก", "ส่วนสูง", "ภาวะโภชนาการ", "หมายเหตุ"),
                listOf(90f, 80f, 80f, 140f, CONTENT_W - 90f - 80f - 80f - 140f),
                data.growth.map { listOf(it.date, it.weight, it.height, it.status, it.note) }
            )
            if (detailed) textArea("คำแนะนำด้านโภชนาการเพิ่มเติม", data.nutritionAdvice)
        }

        if ("health" in categories) {
            sectionBar("5. ประวัติสุขภาพ")
            fieldRow("โรคประจำตัว" to data.chronicDisease, "ประวัติแพ้ยา/อาหาร" to data.drugFoodAllergy)
            fieldRow("ยาหรือการดูแลที่ใช้อยู่" to data.medications, "ประวัติเจ็บป่วยสำคัญ" to data.majorIllness)
            fieldRow("ประวัติผ่าตัด/นอนโรงพยาบาล" to data.surgeryHospital, "แพทย์/หน่วยบริการที่ติดตาม" to data.followupDoctor)
            blocks.add(Block(6f, 0f) { _, _ -> })
            if (detailed) {
                textArea("รายละเอียดประวัติสุขภาพ", data.healthHistoryDetail, minLines = 4)
                textArea("การดูแลสุขภาพปัจจุบัน หรือคำแนะนำจากแพทย์/ผู้ดูแล", data.currentCare)
            }

            // §6 — tracked in-app (sleep sessions, medication dose adherence); doctor visits
            // commonly ask about both directly, folded under the same "health" category.
            sectionBar("6. การนอนและการทานยา")
            if (detailed) {
                textArea("สรุปการนอน", data.sleepSummary)
                textArea("ยาที่ใช้ประจำและความสม่ำเสมอในการทาน", data.medicationSummary)
            }
        }

        if ("documents" in categories && data.medicalDocs.isNotEmpty()) {
            sectionBar("8. เอกสารแนบทางการแพทย์")
            table(
                listOf("ชื่อไฟล์", "วันที่อัปโหลด"),
                listOf(CONTENT_W - 150f, 150f),
                data.medicalDocs.map { listOf(it.fileName, it.uploadedDate) }
            )
        }

        // §7 always renders — a report needs a conclusion — but the AI paragraph only appears
        // when "ai" is a selected category; recommendations (concrete follow-ups) always show.
        sectionBar("7. สรุปและข้อเสนอแนะ")
        if ("ai" in categories) textArea("สรุปภาพรวม/ประเด็นสำคัญที่ควรติดตาม (วิเคราะห์โดย AI)", data.overallSummary, minLines = 4)
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
            c.drawText("เอกสารข้อมูลเด็กสำหรับบันทึกสุขภาพ พัฒนาการ โภชนาการ การนอน และวัคซีน", MARGIN, 66f, subP)
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

    /** Draws a 14×14 form checkbox; a brand-colored tick (two strokes, not a font glyph) when checked. */
    private fun checkbox(c: android.graphics.Canvas, x: Float, y: Float, checked: Boolean, stroke: Paint, @Suppress("UNUSED_PARAMETER") textPaint: Paint) {
        c.drawRoundRect(RectF(x, y, x + 14f, y + 14f), 2f, 2f, stroke)
        if (checked) {
            val tick = Paint().apply {
                style = Paint.Style.STROKE; strokeWidth = 1.8f; color = BRAND; isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            // tick shape inside the 14×14 box
            c.drawLine(x + 3f, y + 7.5f, x + 6f, y + 10.5f, tick)
            c.drawLine(x + 6f, y + 10.5f, x + 11f, y + 4f, tick)
        }
    }
}
