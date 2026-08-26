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
 * Draws one sheet of the NongRak health book onto a [Canvas], at A4 proportions.
 *
 * ONE renderer, deliberately. The PDF export draws onto a `PdfDocument` page and the in-app book
 * draws onto a `Bitmap` — same code, same coordinates — so "the printed book looks like the app"
 * is a structural property here, not something that has to be maintained by hand in two layouts.
 *
 * Coordinates are in PostScript points against a 595×842 page; callers that want a bigger raster
 * scale the canvas rather than passing a different size, so nothing here needs to know about dpi.
 */
object HealthBookPageRenderer {

    const val PAGE_W = 595f
    const val PAGE_H = 842f

    // Palette sampled directly from the printed form artwork, so a page rendered here and a page
    // photocopied from the paper book are the same blue.
    private const val DEEP = "#003CB8"      // table header bars, section numbers
    private const val WAVE = "#0240C3"      // footer band
    private const val MID = "#427CE4"       // section pills
    private const val SKY = "#F3F6FC"       // page tint
    private const val CARD = "#FBFCFE"
    private const val BORDER = "#DCE6FA"
    private const val ROW_W = "#FEFEFE"
    private const val ROW_B = "#F7F9FE"
    private const val ROW_P = "#FEF8FA"
    private const val INK = "#1B2559"
    private const val MUTED = "#7A88A8"
    private const val PINK = "#F4899E"
    private const val PINK_BG = "#FDE6EC"
    private const val GREEN = "#3FA894"
    private const val GREEN_BG = "#EAF7F4"
    private const val PURPLE = "#8B7BC7"
    private const val PURPLE_BG = "#F1EEFA"

    private const val M = 24f               // outer margin
    private const val FOOTER_H = 46f

    /** Decoded once per process: eight pages each re-decoding the mascot is eight needless copies. */
    private val bitmapCache = mutableMapOf<Int, Bitmap?>()

    fun draw(c: Canvas, ctx: Context, book: HealthBook, index: Int) {
        when (val page = book.pages.getOrNull(index) ?: return) {
            is FormPage.Cover -> drawCover(c, ctx, page)
            is FormPage.Instructions -> drawInstructions(c, ctx, page)
            is FormPage.BasicInfo -> drawBasicInfo(c, ctx, page)
            is FormPage.Immunization -> drawImmunization(c, ctx, page)
            is FormPage.Illness -> drawIllness(c, ctx, page)
            is FormPage.Lifestyle -> drawLifestyle(c, ctx, page)
            is FormPage.Growth -> drawGrowth(c, ctx, page)
            is FormPage.Closing -> drawClosing(c, ctx, page)
        }
    }

    // =========================================================================================
    // Pages
    // =========================================================================================

    private fun drawCover(c: Canvas, ctx: Context, p: FormPage.Cover) {
        c.drawColor(Color.WHITE)
        verticalTint(c, 0f, PAGE_H - FOOTER_H)
        clouds(c)

        // Thin inset keyline — the printed cover has a card edge, not a full bleed.
        c.drawRoundRect(RectF(12f, 12f, PAGE_W - 12f, PAGE_H - 12f), 16f, 16f, stroke(BORDER, 1f))

        image(c, ctx, R.drawable.book_mark, RectF(150f, 55f, 210f, 115f))
        c.drawText("NongRak", 220f, 105f, paint(40f, DEEP, bold = true))
        c.drawText("Caring for your little one, every day", 222f, 128f, paint(11f, MID))

        centered(c, "Health Child Record", 205f, paint(34f, DEEP, bold = true))
        c.drawLine(150f, 240f, 270f, 240f, stroke(BORDER, 1f))
        c.drawLine(325f, 240f, 445f, 240f, stroke(BORDER, 1f))
        heart(c, PAGE_W / 2f, 240f, 7f, PINK)
        centered(c, "สมุดบันทึกสุขภาพเด็ก", 272f, paint(24f, DEEP, bold = true))

        image(c, ctx, R.drawable.book_mascot_greeting, RectF(178f, 300f, 418f, 600f))
        heart(c, 148f, 355f, 15f, PINK)
        star(c, 100f, 425f, 13f, "#FFC94D")
        star(c, 435f, 352f, 12f, "#79B4F5")

        val boxTop = PAGE_H - 260f
        val box = RectF(M + 26f, boxTop, PAGE_W - M - 26f, boxTop + 128f)
        card(c, box, "#FFFFFF", BORDER, 14f)

        var y = boxTop + 30f
        listOf(
            "ชื่อเด็ก (Child's Name)" to p.childName,
            "ชื่อเล่น (Nickname)" to p.nickname,
            "วันเดือนปีเกิด (Date of Birth)" to p.birthdate,
            "โรงพยาบาล / สถานพยาบาล (Healthcare Center)" to p.healthcareCenter
        ).forEach { (label, value) ->
            dottedField(c, box.left + 20f, y, box.right - 20f, label, value, 9f)
            y += 28f
        }

        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", null)
    }

    private fun drawInstructions(c: Canvas, ctx: Context, p: FormPage.Instructions) {
        c.drawColor(Color.WHITE)
        verticalTint(c, 0f, 420f)
        clouds(c)

        image(c, ctx, R.drawable.book_mark, RectF(158f, 32f, 198f, 72f))
        c.drawText("NongRak", 204f, 66f, paint(28f, DEEP, bold = true))
        centered(c, "Health Child Record", 118f, paint(26f, DEEP, bold = true))
        centered(c, "สมุดบันทึกสุขภาพเด็ก", 146f, paint(18f, MID, bold = true))
        image(c, ctx, R.drawable.book_mascot_happy, RectF(468f, 40f, 578f, 178f))

        val pill = RectF(PAGE_W / 2f - 132f, 160f, PAGE_W / 2f + 132f, 190f)
        c.drawRoundRect(pill, 15f, 15f, paint(0f, DEEP))
        centered(c, "คำแนะนำการใช้สมุดบันทึกสุขภาพเด็ก", 180f, paint(11.5f, "#FFFFFF", bold = true))

        centered(c, "สมุดเล่มนี้จัดทำขึ้นเพื่อใช้บันทึกข้อมูลสุขภาพและพัฒนาการของลูกน้อยอย่างต่อเนื่อง", 210f, paint(9.5f, MID))
        centered(c, "เพื่อให้คุณพ่อคุณแม่และบุคลากรทางการแพทย์สามารถดูแลลูกน้อยได้อย่างเหมาะสมและทันเวลา", 226f, paint(9.5f, MID))

        val colW = (PAGE_W - M * 2 - 16f) / 2f
        val left = RectF(M, 248f, M + colW, 660f)
        val right = RectF(M + colW + 16f, 248f, PAGE_W - M, 470f)

        card(c, left, CARD, BORDER, 14f)
        c.drawText("วิธีการใช้สมุดเล่มนี้", left.left + 18f, left.top + 28f, paint(13f, DEEP, bold = true))
        var y = left.top + 54f
        p.steps.forEachIndexed { i, (title, body) ->
            c.drawCircle(left.left + 26f, y + 2f, 9f, paint(0f, "#E8EFFC"))
            centeredAt(c, "${i + 1}", left.left + 26f, y + 5.5f, paint(9f, DEEP, bold = true))
            c.drawText(title, left.left + 44f, y + 4f, paint(10f, INK, bold = true))
            var by = y + 19f
            wrap(body, paint(8.5f, MUTED), left.width() - 62f).take(2).forEach {
                c.drawText(it, left.left + 44f, by, paint(8.5f, MUTED))
                by += 12f
            }
            y = by + 12f
            if (i < p.steps.lastIndex) {
                c.drawLine(left.left + 44f, y - 8f, left.right - 18f, y - 8f, stroke("#EEF2FA", 1f))
            }
        }

        card(c, right, "#FEF9FB", "#FBDDE5", 14f)
        c.drawText("สัญลักษณ์ที่ใช้ในสมุด", right.left + 18f, right.top + 26f, paint(12.5f, PINK, bold = true))
        var sy = right.top + 44f
        p.symbols.forEach { (name, desc) ->
            val row = RectF(right.left + 14f, sy, right.right - 14f, sy + 30f)
            c.drawRoundRect(row, 8f, 8f, paint(0f, "#FFFFFF"))
            c.drawText(name, row.left + 34f, row.centerY() + 3.5f, paint(9f, DEEP, bold = true))
            c.drawText(fit(desc, paint(8f, MUTED), 128f), row.left + 122f, row.centerY() + 3.5f, paint(8f, MUTED))
            heart(c, row.left + 18f, row.centerY(), 6f, PINK)
            sy += 34f
        }

        val notes = RectF(right.left, 488f, right.right, 660f)
        card(c, notes, CARD, BORDER, 14f)
        c.drawText("หมายเหตุสำคัญ", notes.left + 18f, notes.top + 26f, paint(12.5f, DEEP, bold = true))
        var ny = notes.top + 48f
        p.importantNotes.forEach { note ->
            c.drawCircle(notes.left + 22f, ny - 3f, 2.5f, paint(0f, DEEP))
            // Narrower than the card: the mascot sits over the card's lower-right corner, as it
            // does on the printed sheet, and full-width bullets would run underneath it.
            wrap(note, paint(8.5f, MUTED), notes.width() - 128f).take(2).forEach {
                c.drawText(it, notes.left + 32f, ny, paint(8.5f, MUTED))
                ny += 12f
            }
            ny += 10f
        }
        image(c, ctx, R.drawable.book_mascot_greeting, RectF(470f, 560f, 578f, 690f))

        val thanks = RectF(M + 30f, 690f, PAGE_W - M - 30f, 760f)
        card(c, thanks, "#FFFFFF", BORDER, 14f)
        heart(c, thanks.left + 44f, thanks.centerY(), 17f, PINK)
        centered(c, "ขอบคุณที่ให้ความสำคัญกับสุขภาพของลูกน้อย", thanks.top + 32f, paint(15f, DEEP, bold = true))
        centered(c, "เราจะเคียงข้างคุณในทุกช่วงเวลาของการเติบโต", thanks.top + 54f, paint(10f, MUTED))

        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", null)
    }

    private fun drawBasicInfo(c: Canvas, ctx: Context, p: FormPage.BasicInfo) {
        pageChrome(c, ctx, "1", "ข้อมูลเบื้องต้น", "(Basic Information)", R.drawable.book_mascot_greeting)

        // --- Child information -----------------------------------------------------------
        val childCard = RectF(M, 118f, PAGE_W - M, 300f)
        card(c, childCard, CARD, BORDER, 14f)
        sectionPill(c, childCard.left + 16f, childCard.top - 12f, "ข้อมูลเด็ก (Child's Information)", MID)

        val photo = RectF(childCard.right - 160f, childCard.top + 26f, childCard.right - 20f, childCard.top + 150f)
        c.drawRoundRect(photo, 12f, 12f, paint(0f, "#EFF4FD"))
        c.drawRoundRect(photo, 12f, 12f, dashed(BORDER))
        centeredAt(c, "ติดรูปเด็ก", photo.centerX(), photo.centerY() + 22f, paint(8.5f, MID))
        centeredAt(c, "(Photo)", photo.centerX(), photo.centerY() + 34f, paint(7.5f, MUTED))
        c.drawRoundRect(
            RectF(photo.centerX() - 16f, photo.centerY() - 24f, photo.centerX() + 16f, photo.centerY() - 2f),
            5f, 5f, paint(0f, "#B9CCEC")
        )

        var y = childCard.top + 34f
        val fieldRight = photo.left - 18f
        p.child.forEach { (label, value) ->
            dottedField(c, childCard.left + 20f, y, fieldRight, label, value, 8.5f)
            y += 20f
        }
        // Gender is a tick pair on paper, not a written word.
        c.drawText("เพศ (Gender)", childCard.left + 20f, y, paint(8.5f, INK))
        checkbox(c, childCard.left + 118f, y - 7f, p.gender == "male", "ชาย (Male)")
        checkbox(c, childCard.left + 210f, y - 7f, p.gender == "female", "หญิง (Female)")
        y += 20f
        p.birth.take(5).forEach { (label, value) ->
            dottedField(c, childCard.left + 20f, y, fieldRight, label, value, 8.5f)
            y += 20f
        }

        // --- Birth + guardian, side by side ----------------------------------------------
        val halfW = (PAGE_W - M * 2 - 14f) / 2f
        val birthCard = RectF(M, 322f, M + halfW, 500f)
        val guardCard = RectF(M + halfW + 14f, 322f, PAGE_W - M, 500f)

        card(c, birthCard, CARD, BORDER, 14f)
        sectionPill(c, birthCard.left + 14f, birthCard.top - 12f, "ข้อมูลการคลอด (Birth Information)", MID)
        var by = birthCard.top + 32f
        dottedField(c, birthCard.left + 16f, by, birthCard.right - 16f, "สถานที่คลอด (Place of Birth)", p.birth[5].second, 8f)
        by += 20f
        c.drawText("วิธีคลอด (Delivery Type)", birthCard.left + 16f, by, paint(8f, INK))
        by += 16f
        checkbox(c, birthCard.left + 18f, by - 7f, p.deliveryType == "normal", "คลอดธรรมชาติ (Normal)")
        checkbox(c, birthCard.left + 150f, by - 7f, p.deliveryType == "c_section", "ผ่าตัดคลอด (C-Section)")
        by += 22f
        listOf(
            "อายุครรภ์ (Gestational Age)" to p.birth[6].second,
            "น้ำหนักแรกเกิด (Birth Weight)" to p.birth[7].second,
            "ส่วนสูงแรกเกิด (Birth Length)" to p.birth[8].second,
            "เส้นรอบศีรษะแรกเกิด (Head Circ.)" to p.birth[9].second
        ).forEach { (l, v) ->
            dottedField(c, birthCard.left + 16f, by, birthCard.right - 16f, l, v, 8f)
            by += 20f
        }

        card(c, guardCard, "#FEF9FB", "#FBDDE5", 14f)
        sectionPill(c, guardCard.left + 14f, guardCard.top - 12f, "ข้อมูลผู้ปกครอง (Parent/Guardian)", PINK)
        var gy = guardCard.top + 32f
        p.guardian.forEach { (l, v) ->
            dottedField(c, guardCard.left + 16f, gy, guardCard.right - 16f, l, v, 8f)
            gy += 20f
        }

        // --- Healthcare centre ------------------------------------------------------------
        val hc = RectF(M, 516f, PAGE_W - M, 598f)
        card(c, hc, CARD, BORDER, 14f)
        sectionPill(c, hc.left + 16f, hc.top - 12f, "โรงพยาบาล/สถานพยาบาล (Healthcare Center)", MID)
        // Four rows at 16pt: the card was tightened to make room below, and the last line
        // (Primary Doctor) fell outside it at the old spacing.
        var hy = hc.top + 30f
        p.healthcare.forEach { (l, v) ->
            dottedField(c, hc.left + 20f, hy, hc.right - 150f, l, v, 8f)
            hy += 16f
        }
        hospital(c, hc.right - 118f, hc.top + 26f, 96f, 52f)

        // --- Basic health ------------------------------------------------------------------
        val bh = RectF(M, 612f, M + halfW + 60f, PAGE_H - FOOTER_H - 12f)
        val nt = RectF(bh.right + 14f, 612f, PAGE_W - M, PAGE_H - FOOTER_H - 12f)

        card(c, bh, CARD, BORDER, 14f)
        sectionPill(c, bh.left + 16f, bh.top - 12f, "ข้อมูลสุขภาพเบื้องต้น (Basic Health)", MID)
        val colX = bh.left + 18f
        val col2X = bh.left + bh.width() / 2f + 6f
        c.drawText("การให้นม (Feeding)", colX, bh.top + 34f, paint(8.5f, MID, bold = true))
        var fy = bh.top + 50f
        p.feeding.forEach { (label, on) ->
            checkbox(c, colX, fy - 7f, on, label, 7.5f)
            fy += 15f
        }
        c.drawText("การนอน (Sleeping)", colX, fy + 10f, paint(8.5f, MID, bold = true))
        dottedField(c, colX, fy + 26f, col2X - 12f, "นอนวันละประมาณ (ชม.)", p.sleepHours, 7.5f)
        dottedField(c, colX, fy + 42f, col2X - 12f, "เวลานอนกลางคืน", p.sleepWindow, 7.5f)

        c.drawText("พัฒนาการเบื้องต้น (Development)", col2X, bh.top + 34f, paint(8.5f, "#E9A93C", bold = true))
        var dy = bh.top + 50f
        p.development.forEach { (label, on) ->
            checkbox(c, col2X, dy - 7f, on, label, 7.5f)
            dy += 15f
        }

        card(c, nt, "#FBF9FE", "#E5DFF6", 14f)
        sectionPill(c, nt.left + 14f, nt.top - 12f, "หมายเหตุ (Notes)", PURPLE)
        ruledLines(c, nt.left + 14f, nt.top + 34f, nt.right - 14f, 9, 15f, p.notes)
        image(c, ctx, R.drawable.book_mascot_caring, RectF(nt.right - 92f, nt.bottom - 108f, nt.right - 6f, nt.bottom - 6f))

        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", null)
    }

    private fun drawImmunization(c: Canvas, ctx: Context, p: FormPage.Immunization) {
        pageChrome(c, ctx, "02", "2. ประวัติการได้รับวัคซีน", "Immunization Record", R.drawable.book_mascot_hero)
        banner(
            c, "บันทึกการได้รับวัคซีนของเด็ก",
            listOf(
                "Record your child's vaccinations to help keep",
                "their health history organized and easy to review."
            ),
            partLabel(p.part, p.parts)
        )

        val top = 214f
        val bottom = 606f
        table(
            c,
            RectF(M, top, PAGE_W - M, bottom),
            headers = listOf(
                "อายุ" to "(Age)",
                "วัคซีน" to "(Vaccine)",
                "ครั้งที่ / เข็ม" to "(Dose)",
                "วันที่ได้รับ" to "(Date Given)",
                "ผู้ให้บริการ" to "(Provider)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.146f, .186f, .151f, .161f, .176f, .180f),
            rows = p.rows,
            maxLines = 2
        )

        val halfW = (PAGE_W - M * 2 - 14f) / 2f
        val notes = RectF(M, bottom + 22f, M + halfW, PAGE_H - FOOTER_H - 20f)
        val important = RectF(M + halfW + 14f, bottom + 22f, PAGE_W - M, PAGE_H - FOOTER_H - 20f)

        card(c, notes, CARD, BORDER, 14f)
        note(c, notes.left + 18f, notes.top + 18f)
        c.drawText("บันทึกเพิ่มเติม (Additional Notes)", notes.left + 40f, notes.top + 28f, paint(11f, DEEP, bold = true))
        c.drawText("ประวัติการแพ้วัคซีน / Vaccine Reactions:", notes.left + 16f, notes.top + 52f, paint(8f, INK))
        ruledLines(c, notes.left + 16f, notes.top + 68f, notes.right - 16f, 3, 14f, p.reactions)
        c.drawText("คำแนะนำจากบุคลากรทางการแพทย์ / Healthcare Notes:", notes.left + 16f, notes.top + 124f, paint(8f, INK))
        ruledLines(c, notes.left + 16f, notes.top + 140f, notes.right - 16f, 3, 14f, p.healthcareNotes)

        importantCard(c, ctx, important)
        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", "Caring for your little one, with all our heart")
    }

    private fun drawIllness(c: Canvas, ctx: Context, p: FormPage.Illness) {
        pageChrome(c, ctx, "03", "3. ประวัติการเจ็บป่วย", "Illness History", R.drawable.book_mascot_caring)
        banner(
            c, "บันทึกประวัติการเจ็บป่วยของเด็ก",
            listOf(
                "เพื่อช่วยในการติดตามสุขภาพของเด็ก และให้ข้อมูลแก่บุคลากรทางการแพทย์",
                "ในการดูแลรักษาได้อย่างเหมาะสม"
            ),
            partLabel(p.part, p.parts)
        )

        val top = 214f
        val bottom = 618f
        table(
            c,
            RectF(M, top, PAGE_W - M, bottom),
            headers = listOf(
                "วันที่ป่วย" to "(Date)",
                "อาการ / การวินิจฉัย" to "(Symptoms / Diagnosis)",
                "การรักษา / ยาที่ได้รับ" to "(Treatment / Medication)",
                "สถานพยาบาล" to "(Healthcare Center)",
                "แพทย์ผู้ดูแล" to "(Doctor)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.138f, .204f, .204f, .166f, .143f, .145f),
            rows = p.rows,
            maxLines = 2
        )

        val halfW = (PAGE_W - M * 2 - 14f) / 2f
        val notes = RectF(M, bottom + 20f, M + halfW, PAGE_H - FOOTER_H - 20f)
        val important = RectF(M + halfW + 14f, bottom + 20f, PAGE_W - M, PAGE_H - FOOTER_H - 20f)

        card(c, notes, CARD, BORDER, 14f)
        note(c, notes.left + 18f, notes.top + 18f)
        c.drawText("บันทึกเพิ่มเติม (Additional Notes)", notes.left + 40f, notes.top + 28f, paint(11f, DEEP, bold = true))
        c.drawText("ประวัติการแพ้ยา / Allergies:", notes.left + 16f, notes.top + 52f, paint(8f, INK))
        ruledLines(c, notes.left + 16f, notes.top + 68f, notes.right - 16f, 3, 14f, p.allergies)
        c.drawText("ประวัติครอบครัวที่เกี่ยวข้อง / Family History:", notes.left + 16f, notes.top + 124f, paint(8f, INK))
        ruledLines(c, notes.left + 16f, notes.top + 140f, notes.right - 16f, 3, 14f, p.familyHistory)

        importantCard(c, ctx, important)
        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", "Caring for your little one, with all our heart")
    }

    private fun drawLifestyle(c: Canvas, ctx: Context, p: FormPage.Lifestyle) {
        pageChrome(
            c, ctx, "04",
            "4. โภชนาการ ออกกำลังกาย สุขภาพช่องปาก",
            "Nutrition, Exercise & Oral Health",
            R.drawable.book_mascot_happy
        )
        banner(
            c, "บันทึกพฤติกรรมและสุขภาพที่ดีในแต่ละวัน",
            listOf(
                "การดูแลด้านโภชนาการ การออกกำลังกาย และสุขภาพช่องปาก",
                "ช่วยให้ลูกน้อยเติบโตแข็งแรงและมีพัฒนาการที่ดี"
            ),
            partLabel(p.part, p.parts)
        )

        val nutTop = 228f
        val nutBottom = 456f
        table(
            c,
            RectF(M, nutTop, PAGE_W - M, nutBottom),
            headers = listOf(
                "วันที่" to "(Date)",
                "อาหารหลัก" to "(Main Meals)",
                "อาหารว่าง" to "(Snack)",
                "ผักและผลไม้" to "(Vegetables & Fruits)",
                "น้ำดื่ม (ml)" to "(Water Intake)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.168f, .179f, .160f, .177f, .163f, .153f),
            rows = p.nutrition,
            maxLines = 2
        )
        sectionPill(c, M + 16f, nutTop - 26f, "โภชนาการ (Nutrition)", MID)

        val halfW = (PAGE_W - M * 2 - 14f) / 2f
        val exTop = 496f
        val exBottom = 644f
        val exercise = RectF(M, exTop, M + halfW, exBottom)
        val oral = RectF(M + halfW + 14f, exTop, PAGE_W - M, exBottom)

        table(
            c, exercise,
            headers = listOf(
                "วันที่" to "(Date)",
                "ประเภทกิจกรรม" to "(Activity Type)",
                "ระยะเวลา" to "(Duration)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.24f, .34f, .21f, .21f),
            rows = p.exercise,
            maxLines = 1,
            headerColor = GREEN,
            tintA = "#FFFFFF",
            tintB = GREEN_BG,
            tintC = "#FFFFFF"
        )
        sectionPill(c, exercise.left + 14f, exTop - 26f, "การออกกำลังกาย (Exercise)", GREEN)

        table(
            c, oral,
            headers = listOf(
                "วันที่" to "(Date)",
                "การแปรงฟัน" to "(Brushing)",
                "การใช้ไหมขัดฟัน" to "(Flossing)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.24f, .24f, .28f, .24f),
            rows = p.oral,
            maxLines = 1,
            headerColor = PURPLE,
            tintA = "#FFFFFF",
            tintB = PURPLE_BG,
            tintC = "#FFFFFF",
            checkColumns = setOf(1, 2)
        )
        sectionPill(c, oral.left + 14f, exTop - 26f, "สุขภาพช่องปาก (Oral Health)", PURPLE)

        val notes = RectF(M, 662f, M + halfW, PAGE_H - FOOTER_H - 20f)
        val important = RectF(M + halfW + 14f, 662f, PAGE_W - M, PAGE_H - FOOTER_H - 20f)
        card(c, notes, CARD, BORDER, 14f)
        note(c, notes.left + 18f, notes.top + 18f)
        c.drawText("บันทึกเพิ่มเติม (Additional Notes)", notes.left + 40f, notes.top + 28f, paint(11f, DEEP, bold = true))
        ruledLines(c, notes.left + 16f, notes.top + 50f, notes.right - 16f, 4, 15f, p.notes)

        importantCard(c, ctx, important)
        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", "Caring for your little one, with all our heart")
    }

    private fun drawGrowth(c: Canvas, ctx: Context, p: FormPage.Growth) {
        pageChrome(c, ctx, "05", "5. การเจริญเติบโต", "(Growth Record)", R.drawable.book_mascot_greeting)
        banner(
            c, "บันทึกการเจริญเติบโตของเด็ก",
            listOf(
                "ติดตามการเจริญเติบโตของลูกน้อยอย่างสม่ำเสมอ",
                "เพื่อให้มั่นใจว่าลูกเติบโตสมวัย แข็งแรง และมีพัฒนาการที่ดี"
            ),
            partLabel(p.part, p.parts)
        )

        val top = 232f
        val bottom = 426f
        table(
            c,
            RectF(M, top, PAGE_W - M, bottom),
            headers = listOf(
                "วันที่บันทึก" to "(Date)",
                "อายุ" to "(Age)",
                "น้ำหนัก" to "(กิโลกรัม / kg)",
                "ส่วนสูง / ความยาว" to "(เซนติเมตร / cm)",
                "รอบศีรษะ" to "(เซนติเมตร / cm)",
                "หมายเหตุ" to "(Remarks)"
            ),
            weights = listOf(.145f, .136f, .175f, .188f, .185f, .171f),
            rows = p.rows,
            maxLines = 1
        )
        sectionPill(c, M + 16f, top - 26f, "บันทึกการเจริญเติบโต (Growth Record)", MID)

        sectionPill(c, M + 16f, 448f, "กราฟติดตามการเจริญเติบโต (สำหรับติดตามแนวโน้ม)", MID)
        val chartTop = 476f
        val chartH = 176f
        val chartW = (PAGE_W - M * 2 - 24f) / 3f

        chart(
            c, RectF(M, chartTop, M + chartW, chartTop + chartH),
            "น้ำหนัก (Weight)", "กก.\n(kg)", PINK, "#FDEEF2",
            p.points.mapNotNull { pt -> pt.weightKg?.let { pt.ageMonths.toFloat() to it.toFloat() } },
            bandFor(isWeight = true), 0f, 18f, 3f
        )
        chart(
            c, RectF(M + chartW + 12f, chartTop, M + chartW * 2 + 12f, chartTop + chartH),
            "ส่วนสูง / ความยาว (Height)", "ซม.\n(cm)", GREEN, "#E9F6F3",
            p.points.mapNotNull { pt -> pt.heightCm?.let { pt.ageMonths.toFloat() to it.toFloat() } },
            bandFor(isWeight = false), 30f, 110f, 10f
        )
        // No head-circumference reference exists in GrowthReference, so this chart plots the
        // child's own measurements on a plain grid. Shading a band we have no data for would
        // invent a normal range on a medical chart.
        chart(
            c, RectF(M + chartW * 2 + 24f, chartTop, PAGE_W - M, chartTop + chartH),
            "รอบศีรษะ (Head Circumference)", "ซม.\n(cm)", PURPLE, "#F2EFFB",
            p.points.mapNotNull { pt -> pt.headCm?.let { pt.ageMonths.toFloat() to it.toFloat() } },
            null, 20f, 60f, 5f
        )

        star(c, M + 8f, 668f, 6f, "#F2B23C")
        c.drawText(
            "หมายเหตุ : กราฟนี้ใช้เพื่อติดตามแนวโน้มการเจริญเติบโต ควรประเมินร่วมกับเกณฑ์อ้างอิงของกรมอนามัยและคำแนะนำของบุคลากรทางการแพทย์",
            M + 18f, 671f, paint(7.2f, PINK, bold = true)
        )

        val halfW = (PAGE_W - M * 2 - 14f) / 2f
        val notes = RectF(M, 686f, M + halfW, PAGE_H - FOOTER_H - 20f)
        val important = RectF(M + halfW + 14f, 686f, PAGE_W - M, PAGE_H - FOOTER_H - 20f)
        card(c, notes, CARD, BORDER, 14f)
        note(c, notes.left + 18f, notes.top + 18f)
        c.drawText("บันทึกเพิ่มเติม (Additional Notes)", notes.left + 40f, notes.top + 28f, paint(11f, DEEP, bold = true))
        ruledLines(c, notes.left + 16f, notes.top + 48f, notes.right - 16f, 4, 14f, p.notes)

        card(c, important, "#FEF9FB", "#FBDDE5", 14f)
        shieldCheck(c, important.left + 26f, important.top + 26f, 13f)
        c.drawText("หมายเหตุสำคัญ", important.left + 46f, important.top + 30f, paint(11f, PINK, bold = true))
        c.drawText("การติดตามการเจริญเติบโตอย่างสม่ำเสมอ", important.left + 16f, important.top + 54f, paint(8.5f, INK))
        c.drawText("ช่วยให้ลูกน้อยเติบโตสมวัย แข็งแรง และมีพัฒนาการที่ดี", important.left + 16f, important.top + 68f, paint(8.5f, INK))
        c.drawText("Regular growth monitoring helps your child", important.left + 16f, important.top + 86f, paint(7.5f, MUTED))
        c.drawText("grow healthy and reach their full potential.", important.left + 16f, important.top + 97f, paint(7.5f, MUTED))

        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", "Caring for your little one, with all our heart")
    }

    private fun drawClosing(c: Canvas, ctx: Context, p: FormPage.Closing) {
        c.drawColor(Color.WHITE)
        verticalTint(c, 0f, 460f)
        clouds(c)

        image(c, ctx, R.drawable.book_mark, RectF(M + 4f, 26f, M + 46f, 68f))
        c.drawText("NongRak", M + 52f, 58f, paint(24f, DEEP, bold = true))
        c.drawText("Caring for your little one, every day", M + 54f, 74f, paint(7.5f, MID))
        image(c, ctx, R.drawable.book_mascot_caring, RectF(370f, 60f, 560f, 270f))

        c.drawText("ขอบคุณที่ให้เราดูแล", 90f, 130f, paint(28f, DEEP, bold = true))
        c.drawText("ลูกน้อยของคุณ", 90f, 168f, paint(28f, DEEP, bold = true))
        c.drawLine(100f, 188f, 190f, 188f, stroke(BORDER, 1f))
        heart(c, 205f, 188f, 6f, "#8FB6EE")
        c.drawLine(220f, 188f, 310f, 188f, stroke(BORDER, 1f))
        listOf(
            "สมุดบันทึกสุขภาพเด็กเล่มนี้",
            "จะเป็นเพื่อนร่วมทางในการดูแลลูกน้อย",
            "ให้เติบโตอย่างแข็งแรงและมีความสุข"
        ).forEachIndexed { i, line ->
            centeredIn(c, line, 60f, 350f, 212f + i * 18f, paint(10f, MUTED))
        }

        val notes = RectF(M, 288f, PAGE_W - M, 400f)
        card(c, notes, "#FFFFFF", BORDER, 14f)
        note(c, notes.left + 20f, notes.top + 22f)
        c.drawText("บันทึกเพิ่มเติม  /  Notes", notes.left + 44f, notes.top + 30f, paint(11f, DEEP, bold = true))
        ruledLines(c, notes.left + 18f, notes.top + 52f, notes.right - 18f, 4, 15f, p.notes)
        heart(c, notes.left - 2f, notes.bottom - 8f, 10f, PINK)

        val msg = RectF(M, 418f, PAGE_W - M, 594f)
        card(c, msg, "#FEF7F9", "#FBDDE5", 14f)
        c.drawCircle(msg.left + 32f, msg.top + 30f, 15f, paint(0f, PINK))
        c.drawText("ข้อความถึงคุณพ่อคุณแม่", msg.left + 56f, msg.top + 35f, paint(13f, PINK, bold = true))
        var my = msg.top + 62f
        listOf(
            listOf("การดูแลใส่ใจในทุกวัน คือของขวัญที่ดีที่สุดสำหรับลูกน้อย"),
            listOf("บันทึกข้อมูลสุขภาพของลูกน้อยอย่างสม่ำเสมอ", "เพื่อให้การดูแลรักษาเป็นไปอย่างต่อเนื่องและเหมาะสม"),
            listOf("อย่าลืมนำสมุดบันทึกสุขภาพเด็กมาใช้ในการตรวจทุกครั้ง", "และเก็บรักษาอย่างดี"),
            listOf("หากมีข้อสงสัยเกี่ยวกับสุขภาพของลูกน้อย", "ควรปรึกษาบุคลากรทางการแพทย์")
        ).forEach { lines ->
            heart(c, msg.left + 24f, my - 4f, 5f, PINK)
            lines.forEach { line ->
                c.drawText(line, msg.left + 38f, my, paint(8.5f, INK))
                my += 13f
            }
            my += 8f
        }

        val staff = RectF(M, 612f, PAGE_W - M, 720f)
        card(c, staff, "#F6FCFA", "#D8EFE9", 14f)
        c.drawCircle(staff.left + 30f, staff.top + 28f, 13f, paint(0f, GREEN))
        c.drawText("สำหรับบุคลากรทางการแพทย์", staff.left + 52f, staff.top + 32f, paint(11f, GREEN, bold = true))
        var sy = staff.top + 56f
        listOf(
            "แพทย์ผู้ดูแลหลัก (Primary Doctor)" to p.primaryDoctor,
            "สถานพยาบาล (Healthcare Center)" to p.healthcareCenter,
            "โทรศัพท์ (Phone)" to p.phone
        ).forEach { (l, v) ->
            dottedField(c, staff.left + 90f, sy, staff.right - 200f, l, v, 8f)
            sy += 18f
        }
        hospital(c, staff.right - 130f, staff.top + 26f, 108f, 62f)

        image(c, ctx, R.drawable.book_mascot_greeting, RectF(M, 726f, M + 84f, PAGE_H - FOOTER_H - 4f))
        val bubble = RectF(M + 92f, 730f, 360f, 790f)
        card(c, bubble, "#F3F7FE", BORDER, 10f)
        listOf(
            "NongRak ขอเป็นกำลังใจให้ทุกครอบครัว",
            "ในการเดินทางของการเลี้ยงดูลูกน้อย",
            "ให้มีสุขภาพดีและเติบโตอย่างมั่นใจในทุกช่วงวัย"
        ).forEachIndexed { i, line ->
            c.drawText(line, bubble.left + 14f, bubble.top + 20f + i * 14f, paint(8f, DEEP, bold = true))
        }
        dottedField(c, 378f, 752f, PAGE_W - M - 4f, "เริ่มใช้สมุดเล่มนี้ที่", p.firstUsed, 8f)
        dottedField(c, 378f, 778f, PAGE_W - M - 4f, "วันที่บันทึกล่าสุด", p.lastUpdated, 8f)

        footer(c, ctx, "ดูแลด้วยรัก ให้ลูกเติบโตอย่างมั่นใจ", "Caring for your little one, with all our heart")
    }

    // =========================================================================================
    // Shared chrome
    // =========================================================================================

    private fun pageChrome(
        c: Canvas,
        ctx: Context,
        badge: String,
        titleTh: String,
        titleEn: String,
        mascot: Int
    ) {
        c.drawColor(Color.WHITE)
        verticalTint(c, 0f, 300f)
        clouds(c)

        image(c, ctx, R.drawable.book_mark, RectF(M + 2f, 20f, M + 44f, 62f))
        c.drawText("NongRak", M + 50f, 48f, paint(21f, DEEP, bold = true))
        c.drawText("Caring for your little one, every day", M + 52f, 61f, paint(6.5f, MID))

        val cx = M + 208f
        c.drawCircle(cx, 44f, 27f, paint(0f, DEEP))
        centeredAt(c, badge, cx, 52f, paint(if (badge.length > 1) 19f else 22f, "#FFFFFF", bold = true))

        // Long headings wrap to a second line rather than shrinking into illegibility or running
        // under the mascot — chapter 4's title does not fit on one line at any readable size, and
        // the printed form sets it on two lines for exactly that reason.
        val maxTitleW = PAGE_W - (cx + 38f) - 122f
        var size = 17f
        val titlePaint = { sz: Float -> paint(sz, DEEP, bold = true) }
        // Shrink until the WHOLE heading fits in two lines. Sizing by total width instead would
        // leave a third line that then gets dropped, silently truncating the chapter name.
        while (size > 9f && wrap(titleTh, titlePaint(size), maxTitleW).size > 2) size -= 0.5f
        val lines = wrap(titleTh, titlePaint(size), maxTitleW).take(2)
        lines.forEachIndexed { i, line ->
            val y = if (lines.size == 1) 42f else 34f + i * 19f
            c.drawText(line, cx + 38f, y, titlePaint(size))
        }
        val enY = if (lines.size == 1) 62f else 70f
        c.drawText(fit(titleEn, paint(12f, MID), maxTitleW), cx + 38f, enY, paint(12f, MID))

        image(c, ctx, mascot, RectF(PAGE_W - M - 108f, 4f, PAGE_W - M + 4f, 122f))
    }

    private fun banner(c: Canvas, title: String, body: List<String>, partLabel: String?) {
        val r = RectF(M, 96f, PAGE_W - M - 118f, 176f)
        card(c, r, "#F6F9FE", BORDER, 14f)
        shieldHeart(c, r.left + 34f, r.centerY(), 19f)
        c.drawText(fit(title, paint(12.5f, DEEP, bold = true), r.width() - 80f), r.left + 64f, r.top + 32f, paint(12.5f, DEEP, bold = true))
        body.forEachIndexed { i, line ->
            c.drawText(fit(line, paint(8.5f, MID), r.width() - 80f), r.left + 64f, r.top + 50f + i * 14f, paint(8.5f, MID))
        }
        if (partLabel != null) {
            val w = paint(7.5f, "#FFFFFF", bold = true).measureText(partLabel) + 18f
            val chip = RectF(r.right - w - 12f, r.top + 10f, r.right - 12f, r.top + 26f)
            c.drawRoundRect(chip, 8f, 8f, paint(0f, MID))
            centeredAt(c, partLabel, chip.centerX(), chip.centerY() + 3f, paint(7.5f, "#FFFFFF", bold = true))
        }
    }

    /** Only shown when a section actually spans sheets — "แผ่นที่ 1/1" on every page is noise. */
    private fun partLabel(part: Int, parts: Int) = if (parts > 1) "แผ่นที่ $part / $parts" else null

    private fun importantCard(c: Canvas, ctx: Context, r: RectF) {
        card(c, r, "#FEF9FB", "#FBDDE5", 14f)
        shieldCheck(c, r.left + 28f, r.top + 28f, 14f)
        c.drawText("หมายเหตุสำคัญ", r.left + 50f, r.top + 32f, paint(11.5f, PINK, bold = true))
        val dots = paint(0f, PINK).apply { strokeWidth = 1.4f }
        var dx = r.left + 50f
        while (dx < r.right - 60f) {
            c.drawPoint(dx, r.top + 44f, dots)
            dx += 6f
        }
        c.drawText("ควรนำสมุดบันทึกสุขภาพเด็ก", r.left + 18f, r.top + 68f, paint(9.5f, INK, bold = true))
        c.drawText("มาใช้ประกอบการรับบริการทุกครั้ง", r.left + 18f, r.top + 83f, paint(9.5f, INK, bold = true))
        c.drawText("Please bring this health record", r.left + 18f, r.top + 102f, paint(7.5f, MUTED))
        c.drawText("when visiting your healthcare provider.", r.left + 18f, r.top + 113f, paint(7.5f, MUTED))
        image(c, ctx, R.drawable.book_mascot_happy, RectF(r.right - 86f, r.bottom - 104f, r.right - 4f, r.bottom - 2f))
    }

    private fun footer(c: Canvas, ctx: Context, thai: String, english: String?) {
        val top = PAGE_H - FOOTER_H
        val path = Path().apply {
            moveTo(0f, top + 14f)
            cubicTo(PAGE_W * 0.22f, top - 12f, PAGE_W * 0.42f, top + 22f, PAGE_W * 0.62f, top + 6f)
            cubicTo(PAGE_W * 0.80f, top - 6f, PAGE_W * 0.92f, top + 8f, PAGE_W, top + 2f)
            lineTo(PAGE_W, PAGE_H)
            lineTo(0f, PAGE_H)
            close()
        }
        c.drawPath(path, paint(0f, WAVE))

        val baseline = if (english == null) PAGE_H - 16f else PAGE_H - 22f
        centered(c, thai, baseline, paint(10f, "#FFFFFF", bold = true))
        if (english != null) centered(c, english, PAGE_H - 9f, paint(7.5f, "#C9D8FA"))
        image(c, ctx, R.drawable.book_mark_white, RectF(PAGE_W / 2f - 122f, baseline - 18f, PAGE_W / 2f - 96f, baseline + 6f))
        heart(c, PAGE_W / 2f + 106f, baseline - 5f, 7f, PINK)
    }

    // =========================================================================================
    // Primitives
    // =========================================================================================

    /**
     * A form table: dark heading bar with a bilingual caption, then fixed-height ruled rows.
     *
     * Row tints cycle white / pale blue / pale pink exactly as the printed form does. The tint is
     * decoration, never meaning — an empty row and a filled one differ only by their text.
     */
    private fun table(
        c: Canvas,
        r: RectF,
        headers: List<Pair<String, String>>,
        weights: List<Float>,
        rows: List<List<String>>,
        maxLines: Int,
        headerColor: String = DEEP,
        tintA: String = ROW_W,
        tintB: String = ROW_B,
        tintC: String = ROW_P,
        checkColumns: Set<Int> = emptySet()
    ) {
        val headerH = if (headers.any { it.second.isNotBlank() }) 34f else 24f
        val bodyTop = r.top + headerH
        val count = rows.size.coerceAtLeast(1)
        val rowH = (r.bottom - bodyTop) / count

        c.save()
        c.clipPath(Path().apply { addRoundRect(r, 10f, 10f, Path.Direction.CW) })

        c.drawRect(RectF(r.left, r.top, r.right, bodyTop), paint(0f, headerColor))
        rows.indices.forEach { i ->
            val tint = when (i % 3) {
                0 -> tintA
                1 -> tintB
                else -> tintC
            }
            c.drawRect(RectF(r.left, bodyTop + i * rowH, r.right, bodyTop + (i + 1) * rowH), paint(0f, tint))
        }

        val grid = stroke("#DCE6FA", 0.7f)
        var x = r.left
        val xs = weights.map { w -> x.also { x += r.width() * w } } + r.right
        xs.drop(1).dropLast(1).forEach { c.drawLine(it, bodyTop, it, r.bottom, grid) }
        (1 until rows.size).forEach { c.drawLine(r.left, bodyTop + it * rowH, r.right, bodyTop + it * rowH, grid) }

        headers.forEachIndexed { i, (th, en) ->
            if (i >= xs.size - 1) return@forEachIndexed
            val cx = (xs[i] + xs[i + 1]) / 2f
            val colW = xs[i + 1] - xs[i] - 6f
            if (en.isBlank()) {
                centeredAt(c, fit(th, paint(8.5f, "#FFFFFF", bold = true), colW), cx, r.top + headerH / 2f + 3.5f, paint(8.5f, "#FFFFFF", bold = true))
            } else {
                centeredAt(c, fit(th, paint(8.5f, "#FFFFFF", bold = true), colW), cx, r.top + 15f, paint(8.5f, "#FFFFFF", bold = true))
                centeredAt(c, fit(en, paint(6.8f, "#D6E2FA"), colW), cx, r.top + 26f, paint(6.8f, "#D6E2FA"))
            }
        }

        rows.forEachIndexed { ri, row ->
            val rowTop = bodyTop + ri * rowH
            row.forEachIndexed { ci, cell ->
                if (ci >= xs.size - 1) return@forEachIndexed
                if (ci in checkColumns) {
                    val cx = (xs[ci] + xs[ci + 1]) / 2f
                    val box = RectF(cx - 5f, rowTop + rowH / 2f - 5f, cx + 5f, rowTop + rowH / 2f + 5f)
                    c.drawRoundRect(box, 2f, 2f, paint(0f, "#FFFFFF"))
                    c.drawRoundRect(box, 2f, 2f, stroke("#A9B7D6", 0.9f))
                    if (cell.isNotBlank()) tick(c, box)
                    return@forEachIndexed
                }
                if (cell.isBlank()) return@forEachIndexed
                val cellW = xs[ci + 1] - xs[ci] - 10f
                val tp = paint(7.4f, INK)
                val lines = wrap(cell, tp, cellW)
                val shown = lines.take(maxLines)
                val startY = rowTop + rowH / 2f - (shown.size - 1) * 4.5f + 2.5f
                shown.forEachIndexed { li, line ->
                    // Only the final visible line gets an ellipsis, and only when text was dropped.
                    val text = if (li == maxLines - 1 && lines.size > maxLines) fit("$line…", tp, cellW) else line
                    c.drawText(text, xs[ci] + 5f, startY + li * 9f, tp)
                }
            }
        }

        c.restore()
        c.drawRoundRect(r, 10f, 10f, stroke("#C9D8F4", 1f))
    }

    /** A label followed by a dotted rule, with the recorded value written on the rule. */
    private fun dottedField(
        c: Canvas, x: Float, y: Float, right: Float,
        label: String, value: String, size: Float
    ) {
        val lp = paint(size, INK)
        c.drawText(label, x, y, lp)
        val startX = x + lp.measureText(label) + 6f
        if (startX >= right) return
        val dots = paint(0f, "#B9C6E2").apply { strokeWidth = 0.9f }
        var dx = startX
        while (dx < right) {
            c.drawPoint(dx, y + 2f, dots)
            dx += 3.4f
        }
        if (value.isNotBlank()) {
            val vp = paint(size, DEEP, bold = true)
            c.drawText(fit(value, vp, right - startX - 4f), startX + 2f, y - 1f, vp)
        }
    }

    private fun checkbox(c: Canvas, x: Float, y: Float, checked: Boolean, label: String, size: Float = 8f) {
        val box = RectF(x, y, x + 9f, y + 9f)
        c.drawRoundRect(box, 2f, 2f, paint(0f, "#FFFFFF"))
        c.drawRoundRect(box, 2f, 2f, stroke("#A9B7D6", 0.9f))
        if (checked) tick(c, box)
        c.drawText(label, x + 14f, y + 8f, paint(size, INK))
    }

    private fun tick(c: Canvas, box: RectF) {
        val p = paint(0f, DEEP).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.6f; strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(Path().apply {
            moveTo(box.left + 2f, box.centerY())
            lineTo(box.centerX() - 0.5f, box.bottom - 2.2f)
            lineTo(box.right - 1.6f, box.top + 2.2f)
        }, p)
    }

    /**
     * Ruled writing lines, with any recorded content written onto them.
     *
     * Lines keep going after the content runs out — that blank space is the part of a paper book
     * a parent writes in, and dropping it would turn the sheet back into a screen.
     */
    private fun ruledLines(
        c: Canvas, x: Float, y: Float, right: Float,
        count: Int, gap: Float, content: List<String>
    ) {
        val dots = paint(0f, "#C6D2E8").apply { strokeWidth = 0.9f }
        val tp = paint(8f, DEEP)
        val flat = content.flatMap { wrap(it, tp, right - x - 4f) }
        repeat(count) { i ->
            val ly = y + i * gap
            var dx = x
            while (dx < right) {
                c.drawPoint(dx, ly, dots)
                dx += 3.4f
            }
            flat.getOrNull(i)?.let { c.drawText(it, x + 2f, ly - 3f, tp) }
        }
    }

    /**
     * A trend chart: reference band (when one exists), grid, then the child's own measurements.
     *
     * The band comes from [GrowthReference], the same approximate table the growth screens use, so
     * the book cannot show a child as out of range while the app shows them inside it.
     */
    private fun chart(
        c: Canvas, r: RectF, title: String, axisLabel: String,
        accent: String, accentBg: String,
        points: List<Pair<Float, Float>>,
        band: ((Int) -> Pair<Double, Double>)?,
        yMin: Float, yMax: Float, yStep: Float
    ) {
        val pill = RectF(r.left, r.top, r.right, r.top + 22f)
        c.drawRoundRect(pill, 11f, 11f, paint(0f, accentBg))
        c.drawCircle(pill.left + 13f, pill.centerY(), 8f, paint(0f, accent))
        c.drawText(
            fit(title, paint(7.5f, accent, bold = true), pill.width() - 32f),
            pill.left + 26f, pill.centerY() + 3f, paint(7.5f, accent, bold = true)
        )

        val plot = RectF(r.left + 26f, r.top + 54f, r.right - 6f, r.bottom - 18f)
        c.drawRect(plot, paint(0f, "#FFFFFF"))

        axisLabel.split("\n").forEachIndexed { i, line ->
            c.drawText(line, r.left, r.top + 40f + i * 8f, paint(6f, MUTED))
        }

        fun yAt(v: Float) = plot.bottom - (v - yMin) / (yMax - yMin) * plot.height()
        fun xAt(m: Float) = plot.left + (m / 36f).coerceIn(0f, 1f) * plot.width()

        val grid = stroke("#EDF1F8", 0.6f)
        var v = yMin
        while (v <= yMax) {
            val gy = yAt(v)
            c.drawLine(plot.left, gy, plot.right, gy, grid)
            val tick = paint(5.5f, MUTED)
            c.drawText(trimNum(v), plot.left - 4f - tick.measureText(trimNum(v)), gy + 2.5f, tick)
            v += yStep
        }
        (0..36 step 3).forEach { m ->
            val gx = xAt(m.toFloat())
            c.drawLine(gx, plot.top, gx, plot.bottom, grid)
            centeredAt(c, "$m", gx, plot.bottom + 8f, paint(5.5f, MUTED))
        }

        if (band != null) {
            val upper = Path()
            val lower = Path()
            (0..36).forEach { m ->
                val (lo, hi) = band(m)
                val x = xAt(m.toFloat())
                if (m == 0) {
                    upper.moveTo(x, yAt(hi.toFloat()))
                    lower.moveTo(x, yAt(lo.toFloat()))
                } else {
                    upper.lineTo(x, yAt(hi.toFloat()))
                    lower.lineTo(x, yAt(lo.toFloat()))
                }
            }
            val fill = Path(upper)
            (36 downTo 0).forEach { m -> fill.lineTo(xAt(m.toFloat()), yAt(band(m).first.toFloat())) }
            fill.close()
            c.drawPath(fill, paint(0f, accentBg))
            val edge = stroke(accent, 0.8f).apply {
                alpha = 130
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
            }
            c.drawPath(upper, edge)
            c.drawPath(lower, edge)
        }

        if (points.isNotEmpty()) {
            val line = stroke(accent, 1.5f).apply {
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            }
            val path = Path()
            points.forEachIndexed { i, (m, value) ->
                val x = xAt(m)
                val yy = yAt(value.coerceIn(yMin, yMax))
                if (i == 0) path.moveTo(x, yy) else path.lineTo(x, yy)
            }
            c.drawPath(path, line)
            points.forEach { (m, value) ->
                val x = xAt(m)
                val yy = yAt(value.coerceIn(yMin, yMax))
                c.drawCircle(x, yy, 2.4f, paint(0f, "#FFFFFF"))
                c.drawCircle(x, yy, 2.4f, stroke(accent, 1.2f))
            }
        }

        c.drawRect(plot, stroke("#D9E2F3", 0.8f))
        centeredAt(c, "อายุ (เดือน) / Age (Months)", plot.centerX(), r.bottom - 2f, paint(5.8f, MUTED))
    }

    private fun bandFor(isWeight: Boolean): (Int) -> Pair<Double, Double> =
        { m -> GrowthReference.band(m, isWeight) }

    // --- small decorations -------------------------------------------------------------------

    private fun card(c: Canvas, r: RectF, fill: String, border: String, radius: Float) {
        c.drawRoundRect(r, radius, radius, paint(0f, fill))
        c.drawRoundRect(r, radius, radius, stroke(border, 1f))
    }

    private fun sectionPill(c: Canvas, x: Float, y: Float, text: String, color: String) {
        val tp = paint(8.5f, "#FFFFFF", bold = true)
        val w = tp.measureText(text) + 40f
        val r = RectF(x, y, x + w, y + 24f)
        c.drawRoundRect(r, 12f, 12f, paint(0f, color))
        c.drawCircle(r.left + 13f, r.centerY(), 8.5f, paint(0f, "#FFFFFF"))
        c.drawCircle(r.left + 13f, r.centerY(), 4f, paint(0f, color))
        c.drawText(text, r.left + 28f, r.centerY() + 3f, tp)
    }

    private fun verticalTint(c: Canvas, top: Float, bottom: Float) {
        val p = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, top, 0f, bottom,
                Color.parseColor(SKY), Color.WHITE,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        c.drawRect(RectF(0f, top, PAGE_W, bottom), p)
    }

    private fun clouds(c: Canvas) {
        val p = paint(0f, "#EDF3FC")
        c.drawOval(RectF(-40f, 150f, 110f, 210f), p)
        c.drawOval(RectF(PAGE_W - 90f, 220f, PAGE_W + 60f, 280f), p)
        c.drawOval(RectF(40f, 470f, 150f, 512f), p)
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, color: String) {
        c.drawPath(Path().apply {
            moveTo(cx, cy + s * 0.75f)
            cubicTo(cx - s * 1.5f, cy - s * 0.3f, cx - s * 0.5f, cy - s * 1.1f, cx, cy - s * 0.35f)
            cubicTo(cx + s * 0.5f, cy - s * 1.1f, cx + s * 1.5f, cy - s * 0.3f, cx, cy + s * 0.75f)
            close()
        }, paint(0f, color))
    }

    private fun star(c: Canvas, cx: Float, cy: Float, s: Float, color: String) {
        val path = Path()
        (0 until 10).forEach { i ->
            val rr = if (i % 2 == 0) s else s * 0.45f
            val a = Math.toRadians(-90.0 + i * 36.0)
            val x = cx + (rr * Math.cos(a)).toFloat()
            val y = cy + (rr * Math.sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, paint(0f, color))
    }

    private fun shieldHeart(c: Canvas, cx: Float, cy: Float, s: Float) {
        shield(c, cx, cy, s, "#DCE7FB", "#9FBCF0")
        heart(c, cx, cy + 1f, s * 0.42f, PINK)
    }

    private fun shieldCheck(c: Canvas, cx: Float, cy: Float, s: Float) {
        shield(c, cx, cy, s, PINK_BG, "#F5B6C4")
        c.drawPath(Path().apply {
            moveTo(cx - s * 0.36f, cy)
            lineTo(cx - s * 0.06f, cy + s * 0.3f)
            lineTo(cx + s * 0.4f, cy - s * 0.28f)
        }, stroke(PINK, 2f).apply { strokeCap = Paint.Cap.ROUND })
    }

    private fun shield(c: Canvas, cx: Float, cy: Float, s: Float, fill: String, strokeColor: String) {
        val path = Path().apply {
            moveTo(cx, cy - s)
            lineTo(cx + s * 0.82f, cy - s * 0.6f)
            lineTo(cx + s * 0.82f, cy + s * 0.2f)
            quadTo(cx + s * 0.7f, cy + s * 0.9f, cx, cy + s)
            quadTo(cx - s * 0.7f, cy + s * 0.9f, cx - s * 0.82f, cy + s * 0.2f)
            lineTo(cx - s * 0.82f, cy - s * 0.6f)
            close()
        }
        c.drawPath(path, paint(0f, fill))
        c.drawPath(path, stroke(strokeColor, 1.2f))
    }

    private fun note(c: Canvas, x: Float, y: Float) {
        val r = RectF(x - 8f, y - 9f, x + 8f, y + 9f)
        c.drawRoundRect(r, 3f, 3f, paint(0f, "#FFF6DA"))
        c.drawRoundRect(r, 3f, 3f, stroke("#E3C77A", 0.9f))
        val l = stroke("#C9A94F", 1f)
        (0..2).forEach { c.drawLine(x - 5f, y - 4f + it * 4f, x + 5f, y - 4f + it * 4f, l) }
    }

    private fun hospital(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val body = RectF(x + w * 0.18f, y + h * 0.28f, x + w * 0.82f, y + h)
        c.drawRect(body, paint(0f, "#E4EDFB"))
        c.drawRect(body, stroke("#B9CCEC", 1f))
        c.drawCircle(x + w * 0.5f, y + h * 0.22f, h * 0.17f, paint(0f, MID))
        val cross = stroke("#FFFFFF", 2.2f).apply { strokeCap = Paint.Cap.ROUND }
        c.drawLine(x + w * 0.5f - 4f, y + h * 0.22f, x + w * 0.5f + 4f, y + h * 0.22f, cross)
        c.drawLine(x + w * 0.5f, y + h * 0.22f - 4f, x + w * 0.5f, y + h * 0.22f + 4f, cross)
        val win = paint(0f, "#FFFFFF")
        (0..2).forEach { row ->
            (0..2).forEach { col ->
                c.drawRect(
                    RectF(
                        body.left + 6f + col * 12f, body.top + 8f + row * 12f,
                        body.left + 14f + col * 12f, body.top + 16f + row * 12f
                    ), win
                )
            }
        }
    }

    // --- text helpers -------------------------------------------------------------------------

    private fun paint(size: Float, color: String, bold: Boolean = false) = Paint().apply {
        this.color = Color.parseColor(color)
        textSize = size
        isAntiAlias = true
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun stroke(color: String, width: Float) = paint(0f, color).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun dashed(color: String) = stroke(color, 1f).apply {
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    private fun centered(c: Canvas, text: String, y: Float, p: Paint) =
        c.drawText(text, (PAGE_W - p.measureText(text)) / 2f, y, p)

    private fun centeredAt(c: Canvas, text: String, cx: Float, y: Float, p: Paint) =
        c.drawText(text, cx - p.measureText(text) / 2f, y, p)

    private fun centeredIn(c: Canvas, text: String, left: Float, right: Float, y: Float, p: Paint) =
        c.drawText(text, left + (right - left - p.measureText(text)) / 2f, y, p)

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

    private fun trimNum(v: Float) =
        if (v == v.toInt().toFloat()) "${v.toInt()}" else String.format(java.util.Locale.US, "%.1f", v)

    private fun image(c: Canvas, ctx: Context, resId: Int, dest: RectF) {
        val bmp = bitmapCache.getOrPut(resId) {
            runCatching { BitmapFactory.decodeResource(ctx.resources, resId) }.getOrNull()
        } ?: return

        // Fit inside the box without distortion: a squashed mascot is worse than a smaller one.
        val scale = minOf(dest.width() / bmp.width, dest.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val out = RectF(
            dest.centerX() - w / 2f, dest.centerY() - h / 2f,
            dest.centerX() + w / 2f, dest.centerY() + h / 2f
        )
        c.drawBitmap(bmp, Rect(0, 0, bmp.width, bmp.height), out, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
