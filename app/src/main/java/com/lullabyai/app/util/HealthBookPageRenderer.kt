package com.lullabyai.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.lullabyai.app.R

/**
 * Draws one sheet of the NongRak health book onto a [Canvas].
 *
 * The sheet itself IS the designed artwork from `Health-Form/` — bundled as `form_*.webp` and
 * drawn full-bleed. Nothing here redraws the layout, the mascots, the wave or the ruling; this
 * file only writes the child's data onto the lines the design already provides. Redrawing the
 * design in code is exactly how it drifts away from the original, so the design stays an image.
 *
 * All coordinates are in the artwork's own pixel space (1054 × 1492), measured off the source
 * files, and the canvas is scaled to the output size once. A coordinate here can therefore be
 * checked directly against the PNG in the design folder.
 *
 * ONE renderer, deliberately: the PDF export draws onto a `PdfDocument` page and the in-app book
 * draws onto a `Bitmap`, same code, so the printed copy and the screen cannot disagree.
 */
object HealthBookPageRenderer {

    const val PAGE_W = 595f
    const val PAGE_H = 842f

    /** The artwork's coordinate space. Every constant below is in these units. */
    private const val FW = 1054f
    private const val FH = 1492f

    private const val INK = "#17307A"       // filled-in values
    private const val INK_SOFT = "#33477F"  // table cell text

    /** Decoded once per process: eight sheets each decoding a background is eight needless copies. */
    private val bitmapCache = mutableMapOf<Int, Bitmap?>()

    fun draw(c: Canvas, ctx: Context, book: HealthBook, index: Int) {
        val page = book.pages.getOrNull(index) ?: return
        c.save()
        c.scale(PAGE_W / FW, PAGE_H / FH)
        background(c, ctx, backgroundFor(page))
        when (page) {
            is FormPage.Cover -> cover(c, page)
            is FormPage.Instructions -> Unit          // wholly static artwork
            is FormPage.BasicInfo -> basicInfo(c, page)
            is FormPage.Immunization -> immunization(c, page)
            is FormPage.Illness -> illness(c, page)
            is FormPage.Lifestyle -> lifestyle(c, page)
            is FormPage.Growth -> growth(c, page)
            is FormPage.Closing -> closing(c, page)
        }
        c.restore()
    }

    private fun backgroundFor(page: FormPage) = when (page) {
        is FormPage.Cover -> R.drawable.form_cover
        is FormPage.Instructions -> R.drawable.form_intro
        is FormPage.BasicInfo -> R.drawable.form_info
        is FormPage.Immunization -> R.drawable.form_vaccine
        is FormPage.Illness -> R.drawable.form_illness
        is FormPage.Lifestyle -> R.drawable.form_lifestyle
        is FormPage.Growth -> R.drawable.form_growth
        is FormPage.Closing -> R.drawable.form_end
    }

    // =========================================================================================
    // Sheets
    // =========================================================================================

    private fun cover(c: Canvas, p: FormPage.Cover) {
        value(c, 345f, 1149f, 930f, p.childName, 20f)
        value(c, 315f, 1201f, 930f, p.nickname, 20f)
        value(c, 405f, 1254f, 930f, thaiDate(p.birthdate), 20f)
        // โรงพยาบาล/สถานพยาบาล is left for hand-filling — see the note in [basicInfo].
    }

    private fun basicInfo(c: Canvas, p: FormPage.BasicInfo) {
        // ── ข้อมูลเด็ก ───────────────────────────────────────────────────────────────────
        value(c, 292f, 280f, 650f, p.child[0].second, 17f)
        value(c, 228f, 312f, 650f, p.child[1].second, 17f)
        if (p.gender == "male") tick(c, 222f, 334f, 16f)
        if (p.gender == "female") tick(c, 378f, 334f, 16f)
        value(c, 295f, 374f, 505f, thaiDate(p.birth[0].second), 17f)
        value(c, 165f, 406f, 285f, p.birth[1].second, 17f)
        value(c, 260f, 437f, 410f, p.birth[2].second, 17f)
        value(c, 315f, 468f, 575f, p.birth[3].second, 17f)
        value(c, 292f, 499f, 575f, p.birth[4].second, 17f)

        // ── ข้อมูลการคลอด / ข้อมูลผู้ปกครอง / โรงพยาบาล ───────────────────────────────────
        // All three are left blank on purpose. The app records none of it: birth measurements and
        // guardian contacts are never collected, and the healthcare centre came from the paused
        // Pedia Connect system. The artwork already prints the dotted lines, so a parent fills
        // them in by hand — which is what a blank line in a paper book is for.

        // ── ข้อมูลสุขภาพเบื้องต้น ────────────────────────────────────────────────────────
        p.feeding.forEachIndexed { i, (_, on) -> if (on) tick(c, 77f, 1216f + i * 21f) }
        p.development.forEachIndexed { i, (_, on) -> if (on) tick(c, 392f, 1216f + i * 21f) }
        value(c, 190f, 1349f, 298f, p.sleepHours, 15f)
        val window = p.sleepWindow.split("ถึง").map { it.trim() }
        value(c, 170f, 1390f, 230f, window.getOrElse(0) { "" }, 15f)
        value(c, 253f, 1390f, 328f, window.getOrElse(1) { "" }, 15f)

        ruled(c, 700f, 1185f, 975f, 28f, 7, p.notes, 15f)
    }

    private fun immunization(c: Canvas, p: FormPage.Immunization) {
        table(c, VACCINE, p.rows, 2, dateColumns = setOf(3))
        ruled(c, 345f, 1197f, 500f, 0f, 1, p.reactions.take(1), 13f)
        ruled(c, 50f, 1225f, 500f, 28f, 2, p.reactions.drop(1), 13f)
        ruled(c, 50f, 1315f, 500f, 28f, 2, p.healthcareNotes, 13f)
        partMark(c, p.part, p.parts)
    }

    private fun illness(c: Canvas, p: FormPage.Illness) {
        table(c, ILLNESS, p.rows, 2, dateColumns = setOf(0))
        ruled(c, 300f, 1242f, 520f, 0f, 1, p.allergies.take(1), 13f)
        ruled(c, 50f, 1270f, 520f, 28f, 2, p.allergies.drop(1), 13f)
        ruled(c, 50f, 1360f, 520f, 28f, 2, p.familyHistory, 13f)
        partMark(c, p.part, p.parts)
    }

    private fun lifestyle(c: Canvas, p: FormPage.Lifestyle) {
        table(c, NUTRITION, p.nutrition, 2, dateColumns = setOf(0))
        table(c, EXERCISE, p.exercise, 1)
        table(c, ORAL, p.oral, 1, checkColumns = setOf(1, 2))
        partMark(c, p.part, p.parts)
    }

    private fun growth(c: Canvas, p: FormPage.Growth) {
        table(c, GROWTH, p.rows, 1, dateColumns = setOf(0))

        // The artwork already draws the axes, gridlines and reference band; only the child's own
        // measurements are added, so nothing here can contradict the printed scale.
        trend(c, WEIGHT_PLOT, p.points.mapNotNull { pt -> pt.weightKg?.let { pt.ageMonths to it } }, "#E8607F")
        trend(c, HEIGHT_PLOT, p.points.mapNotNull { pt -> pt.heightCm?.let { pt.ageMonths to it } }, "#2E9E88")
        trend(c, HEAD_PLOT, p.points.mapNotNull { pt -> pt.headCm?.let { pt.ageMonths to it } }, "#7E6BC4")

        ruled(c, 60f, 1300f, 520f, 30f, 4, p.notes, 13f)
        partMark(c, p.part, p.parts)
    }

    private fun closing(c: Canvas, p: FormPage.Closing) {
        ruled(c, 70f, 605f, 985f, 38f, 4, p.notes, 15f)
        // สำหรับบุคลากรทางการแพทย์ stays blank: doctor and facility came from Pedia Connect.
        value(c, 800f, 1303f, 985f, thaiDate(p.firstUsed), 16f)
        value(c, 800f, 1344f, 985f, thaiDate(p.lastUpdated), 16f)
    }

    // =========================================================================================
    // Table geometry, measured off the artwork
    // =========================================================================================

    /** Column edges, header bottom and row height — all read from the source PNG's own ruling. */
    private class Grid(
        val xs: FloatArray,
        val top: Float,
        val rowH: Float,
        val rows: Int
    )

    private val VACCINE = Grid(
        floatArrayOf(27f, 176f, 367f, 512f, 672f, 850f, 1027f), top = 459f, rowH = 77.4f, rows = 8
    )
    private val ILLNESS = Grid(
        floatArrayOf(34f, 170f, 367f, 570f, 730f, 873f, 1020f), top = 497f, rowH = 70.0f, rows = 9
    )
    private val NUTRITION = Grid(
        floatArrayOf(54f, 201f, 366f, 524f, 687f, 843f, 1004f), top = 509f, rowH = 47.5f, rows = 6
    )
    private val EXERCISE = Grid(
        floatArrayOf(50f, 155f, 297f, 404f, 503f), top = 982f, rowH = 48.8f, rows = 4
    )
    private val ORAL = Grid(
        floatArrayOf(537f, 646f, 764f, 901f, 1009f), top = 982f, rowH = 48.8f, rows = 4
    )
    private val GROWTH = Grid(
        floatArrayOf(50f, 189f, 317f, 489f, 662f, 836f, 1000f), top = 497f, rowH = 45.0f, rows = 6
    )

    /** Plot rectangles for the three growth charts: x spans 0–36 months, y the printed value range. */
    private class Plot(
        val x0: Float, val x1: Float,
        val yTop: Float, val yBottom: Float,
        val vMax: Float, val vMin: Float
    )

    private val WEIGHT_PLOT = Plot(78f, 321f, 934f, 1124f, vMax = 16f, vMin = 0f)
    private val HEIGHT_PLOT = Plot(397f, 640f, 934f, 1124f, vMax = 110f, vMin = 30f)
    private val HEAD_PLOT = Plot(716f, 959f, 934f, 1124f, vMax = 60f, vMin = 20f)

    // =========================================================================================
    // Overlay primitives
    // =========================================================================================

    private fun background(c: Canvas, ctx: Context, resId: Int) {
        val bmp = bitmapCache.getOrPut(resId) {
            runCatching {
                BitmapFactory.decodeResource(ctx.resources, resId, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565   // opaque page art: half the memory
                })
            }.getOrNull()
        }
        if (bmp == null) {
            // Never leave a blank sheet: without the artwork the data would print onto nothing.
            c.drawColor(Color.WHITE)
            return
        }
        c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), RectF(0f, 0f, FW, FH), FILTER)
    }

    /** Writes a recorded value onto a dotted line the artwork already drew. */
    private fun value(c: Canvas, x: Float, baseline: Float, right: Float, text: String, size: Float) {
        if (text.isBlank()) return
        val p = paint(size, INK, bold = true)
        c.drawText(fit(text, p, right - x), x, baseline, p)
    }

    /** Ticks a checkbox the artwork already drew, given its top-left corner. */
    private fun tick(c: Canvas, x: Float, y: Float, box: Float = 15f) {
        val p = paint(0f, INK).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        c.drawPath(Path().apply {
            moveTo(x + box * 0.18f, y + box * 0.52f)
            lineTo(x + box * 0.42f, y + box * 0.78f)
            lineTo(x + box * 0.86f, y + box * 0.20f)
        }, p)
    }

    /**
     * Writes onto the ruled lines the artwork prints, and stops when it runs out.
     *
     * Deliberately does not draw the lines: they are part of the design, and the blank ones left
     * over are the part of a paper book a parent writes in.
     */
    private fun ruled(
        c: Canvas, x: Float, firstBaseline: Float, right: Float,
        gap: Float, count: Int, content: List<String>, size: Float
    ) {
        if (content.isEmpty()) return
        val p = paint(size, INK)
        val flat = content.flatMap { wrap(it, p, right - x) }
        for (i in 0 until count) {
            val line = flat.getOrNull(i) ?: return
            c.drawText(line, x, firstBaseline + i * gap, p)
        }
    }

    private fun table(
        c: Canvas, g: Grid, rows: List<List<String>>, maxLines: Int,
        checkColumns: Set<Int> = emptySet(),
        dateColumns: Set<Int> = emptySet()
    ) {
        rows.take(g.rows).forEachIndexed { r, row ->
            val top = g.top + r * g.rowH
            val mid = top + g.rowH / 2f
            row.forEachIndexed { col, cell ->
                if (col >= g.xs.size - 1) return@forEachIndexed
                if (col in checkColumns) {
                    if (cell.isNotBlank()) tick(c, (g.xs[col] + g.xs[col + 1]) / 2f - 8f, mid - 8f, 16f)
                    return@forEachIndexed
                }
                if (cell.isBlank()) return@forEachIndexed
                val shownCell = if (col in dateColumns) thaiDate(cell) else cell
                val w = g.xs[col + 1] - g.xs[col] - 18f
                val p = paint(13.5f, INK_SOFT)
                val all = wrap(shownCell, p, w)
                val shown = all.take(maxLines)
                val y0 = mid - (shown.size - 1) * 8f + 5f
                shown.forEachIndexed { i, line ->
                    // Ellipsis only on the last visible line, and only if text was actually cut.
                    val t = if (i == maxLines - 1 && all.size > maxLines) fit("$line…", p, w) else line
                    c.drawText(t, g.xs[col] + 9f, y0 + i * 16f, p)
                }
            }
        }
    }

    /** The child's own measurements, drawn inside the chart the artwork already printed. */
    private fun trend(c: Canvas, plot: Plot, points: List<Pair<Double, Double>>, color: String) {
        if (points.isEmpty()) return
        fun x(months: Double) =
            plot.x0 + ((months / 36.0).coerceIn(0.0, 1.0)).toFloat() * (plot.x1 - plot.x0)
        fun y(v: Double) = plot.yBottom -
            ((v - plot.vMin) / (plot.vMax - plot.vMin)).coerceIn(0.0, 1.0).toFloat() *
            (plot.yBottom - plot.yTop)

        val line = paint(0f, color).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        points.forEachIndexed { i, (m, v) ->
            if (i == 0) path.moveTo(x(m), y(v)) else path.lineTo(x(m), y(v))
        }
        c.drawPath(path, line)
        points.forEach { (m, v) ->
            c.drawCircle(x(m), y(v), 4.4f, paint(0f, "#FFFFFF"))
            c.drawCircle(x(m), y(v), 4.4f, paint(0f, color).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.2f
            })
        }
    }

    /** Only marks continuation sheets — "แผ่นที่ 1/1" on a single sheet is noise. */
    private fun partMark(c: Canvas, part: Int, parts: Int) {
        if (parts <= 1) return
        c.drawText("แผ่นที่ $part / $parts", 44f, 1452f, paint(15f, "#C9D8FA"))
    }

    // =========================================================================================
    // Text helpers
    // =========================================================================================

    private val FILTER = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun paint(size: Float, color: String, bold: Boolean = false) = Paint().apply {
        this.color = Color.parseColor(color)
        textSize = size
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Greedy wrap. Thai has no spaces, so a word-only split would never break a Thai sentence. */
    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        if (text.isBlank()) return emptyList()
        if (p.measureText(text) <= maxW) return listOf(text)
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

    /** ISO dates read as foreign on a Thai form; the Buddhist year is what a parent expects. */
    private fun thaiDate(iso: String): String {
        if (iso.length < 10) return iso
        val d = runCatching { java.time.LocalDate.parse(iso.take(10)) }.getOrNull() ?: return iso
        return "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year + 543)
    }
}
