package com.lullabyai.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Renders every sheet of the health book and writes it out as a PNG.
 *
 * Two jobs. It fails if a page throws or comes out blank — the cheap regression guard on layout
 * code that has no other test. And the PNGs it leaves behind are how the form gets checked against
 * the paper original by eye, which is the only way "looks like the form" can actually be verified.
 */
class HealthBookRenderTest {

    private fun sampleBook(): HealthBook {
        fun rows(n: Int, cols: Int, fill: (Int, Int) -> String) =
            List(n) { r -> List(cols) { c -> fill(r, c) } }

        return HealthBook(
            childName = "น้องทดสอบ ใจดี",
            ageText = "18 เดือน",
            birthdate = "2024-02-10",
            bloodGroup = "O",
            generatedAt = "2026-08-26",
            pages = listOf(
                FormPage.Cover(
                    childName = "น้องทดสอบ ใจดี",
                    nickname = "น้องทดสอบ",
                    birthdate = "2024-02-10",
                    healthcareCenter = "โรงพยาบาลตัวอย่าง"
                ),
                FormPage.Instructions(
                    steps = List(5) { "หัวข้อที่ ${it + 1}" to "คำอธิบายตัวอย่างสำหรับขั้นตอนที่ ${it + 1} ของการใช้สมุดเล่มนี้" },
                    symbols = List(5) { "สัญลักษณ์ ${it + 1}" to "คำอธิบายสัญลักษณ์" },
                    importantNotes = List(3) { "หมายเหตุสำคัญข้อที่ ${it + 1} สำหรับการใช้งานสมุดบันทึกสุขภาพเด็ก" }
                ),
                FormPage.BasicInfo(
                    child = listOf(
                        "ชื่อ-นามสกุล (Child's Name)" to "น้องทดสอบ ใจดี",
                        "ชื่อเล่น (Nickname)" to "น้องทดสอบ"
                    ),
                    gender = "female",
                    birth = listOf(
                        "วันเดือนปีเกิด (Date of Birth)" to "2024-02-10",
                        "อายุ (Age)" to "18",
                        "กรุ๊ปเลือด (Blood Type)" to "O",
                        "โรคประจำตัว (Chronic Disease)" to "",
                        "การแพ้ยา/อาหาร (Allergy)" to "ไข่ขาว, นมวัว",
                        "สถานที่คลอด (Place of Birth)" to "โรงพยาบาลตัวอย่าง",
                        "อายุครรภ์ (Gestational Age)" to "39",
                        "น้ำหนักแรกเกิด (Birth Weight)" to "3100",
                        "ส่วนสูงแรกเกิด (Birth Length)" to "50",
                        "เส้นรอบศีรษะแรกเกิด (Head Circumference)" to "34"
                    ),
                    deliveryType = "normal",
                    guardian = listOf(
                        "ชื่อบิดา (Father's Name)" to "",
                        "ชื่อมารดา (Mother's Name)" to "",
                        "ผู้ปกครอง (Guardian)" to "",
                        "ความสัมพันธ์ (Relation)" to "",
                        "ที่อยู่ (Address)" to "",
                        "โทรศัพท์ (Phone)" to "",
                        "อีเมล (Email)" to ""
                    ),
                    healthcare = listOf(
                        "ชื่อโรงพยาบาล/สถานพยาบาล (Hospital/Clinic Name)" to "โรงพยาบาลตัวอย่าง",
                        "ที่อยู่ (Address)" to "",
                        "โทรศัพท์ (Phone)" to "",
                        "แพทย์ประจำตัว (Primary Doctor)" to "นพ.ตัวอย่าง"
                    ),
                    feeding = listOf(
                        "นมแม่อย่างเดียว (Breastmilk Only)" to false,
                        "นมผสม (Formula)" to false,
                        "ทั้งนมแม่และนมผสม (Breastmilk & Formula)" to true,
                        "อื่น ๆ (Others)" to false
                    ),
                    sleepHours = "10.5",
                    sleepWindow = "20:30 ถึง 07:00",
                    development = listOf(
                        "ยิ้ม (Smiling)" to true,
                        "พลิกคว่ำ/หงาย (Turning over)" to true,
                        "นั่ง (Sitting)" to true,
                        "คลาน (Crawling)" to true,
                        "ยืน (Standing)" to true,
                        "เดิน (Walking)" to true,
                        "พูด (Talking)" to false,
                        "อื่น ๆ (Others)" to false
                    ),
                    notes = listOf("ตัวอย่างหมายเหตุบรรทัดที่หนึ่ง", "ตัวอย่างหมายเหตุบรรทัดที่สอง")
                ),
                FormPage.Immunization(
                    rows = rows(8, 6) { r, c ->
                        if (r > 3) "" else when (c) {
                            0 -> "${(r + 1) * 2} เดือน"
                            1 -> "DTP-HB-Hib"
                            2 -> "${r + 1}"
                            3 -> "2024-0${r + 4}-15"
                            4 -> "โรงพยาบาลตัวอย่าง"
                            else -> ""
                        }
                    },
                    part = 1, parts = 2,
                    reactions = listOf("DTP: มีไข้ต่ำ ๆ หลังฉีด 1 วัน"),
                    healthcareNotes = listOf("นัดครั้งถัดไป 2026-09-20")
                ),
                FormPage.Illness(
                    rows = rows(9, 6) { r, c ->
                        if (r > 2) "" else when (c) {
                            0 -> "2026-0${r + 5}-11"
                            1 -> "ไข้หวัด · ตัวร้อน, ไอ, น้ำมูกไหล"
                            2 -> "ยาลดไข้พาราเซตามอล ทุก 6 ชั่วโมง และพักผ่อนให้เพียงพอ"
                            3 -> "โรงพยาบาลตัวอย่าง"
                            4 -> "นพ.ตัวอย่าง"
                            else -> "หายแล้ว"
                        }
                    },
                    part = 1, parts = 1,
                    allergies = listOf("ไข่ขาว (เล็กน้อย)", "นมวัว (ปานกลาง)"),
                    familyHistory = listOf("ภูมิแพ้อากาศ — ฝั่งมารดา")
                ),
                FormPage.Lifestyle(
                    nutrition = rows(6, 6) { r, c ->
                        if (r > 2) "" else when (c) {
                            0 -> "2026-08-2${r + 1}"
                            1 -> "ข้าวต้มหมู, ไข่ตุ๋น"
                            2 -> "กล้วยน้ำว้า"
                            3 -> "ฟักทอง, กล้วย"
                            4 -> "${450 + r * 30}"
                            else -> "${620 + r * 25} kcal"
                        }
                    },
                    exercise = rows(4, 4) { _, _ -> "" },
                    oral = rows(4, 4) { _, _ -> "" },
                    part = 1, parts = 1,
                    notes = emptyList()
                ),
                FormPage.Growth(
                    rows = rows(6, 6) { r, c ->
                        if (r > 3) "" else when (c) {
                            0 -> "2026-0${r + 3}-01"
                            1 -> "${12 + r * 2}"
                            2 -> "${9.4 + r * 0.6}"
                            3 -> "${74.0 + r * 2.5}"
                            else -> ""
                        }
                    },
                    points = listOf(
                        GrowthPoint(3.0, 6.2, 60.0, null),
                        GrowthPoint(9.0, 8.4, 70.5, null),
                        GrowthPoint(15.0, 9.9, 77.0, null),
                        GrowthPoint(21.0, 11.2, 83.0, null)
                    ),
                    part = 1, parts = 1,
                    notes = listOf("วัดที่คลินิกเด็กดี")
                ),
                FormPage.Closing(
                    notes = listOf("เริ่มบันทึกตั้งแต่แรกเกิด"),
                    primaryDoctor = "นพ.ตัวอย่าง",
                    healthcareCenter = "โรงพยาบาลตัวอย่าง",
                    phone = "02-000-0000",
                    firstUsed = "2024-02-12",
                    lastUpdated = "2026-08-26"
                )
            )
        )
    }

    @Test
    fun everyPageRendersAndIsNotBlank() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Dumping eight A4 sheets is ~700 lines of base64 per run, so it is opt-in:
        //   adb shell am instrument -e dumpPages true ...
        // and the reassembled JPEGs are what gets compared against Health-Form/ by eye.
        val dumpPages = InstrumentationRegistry.getArguments().getString("dumpPages") == "true"
        val book = sampleBook()
        // Writing the sheets out is a convenience for eyeballing them against the paper form, not
        // the contract. Storage can be absent (this AVD mounts no emulated volume), so a failure to
        // write is reported and skipped — the blank-page assertions below still run either way.
        val test = InstrumentationRegistry.getInstrumentation().context
        val outDir = listOfNotNull(
            ctx.getExternalFilesDir(null), ctx.externalCacheDir, test.cacheDir, ctx.cacheDir
        ).also { roots ->
            println("BOOK_RENDER_ROOTS=" + roots.joinToString { it.absolutePath })
        }.firstNotNullOfOrNull { root ->
            runCatching {
                File(root, "book-render").apply { deleteRecursively() }
                    .takeIf { it.mkdirs() || it.isDirectory }
            }.onFailure { println("BOOK_RENDER_SKIP ${root.absolutePath}: ${it.message}") }
                .getOrNull()
        }
        println("BOOK_RENDER_DIR=${outDir?.absolutePath ?: "<none writable>"}")

        val s = 2f
        book.pages.indices.forEach { i ->
            val bmp = Bitmap.createBitmap(
                (HealthBookPageRenderer.PAGE_W * s).toInt(),
                (HealthBookPageRenderer.PAGE_H * s).toInt(),
                Bitmap.Config.ARGB_8888
            )
            Canvas(bmp).apply {
                scale(s, s)
                HealthBookPageRenderer.draw(this, ctx, book, i)
            }

            // A page that drew nothing still produces a bitmap and still writes a file, so only
            // sampling the pixels tells a blank sheet apart from a rendered one.
            val distinct = mutableSetOf<Int>()
            var y = 0
            while (y < bmp.height && distinct.size < 12) {
                var x = 0
                while (x < bmp.width) {
                    distinct += bmp.getPixel(x, y)
                    x += 37
                }
                y += 41
            }
            assertTrue("page $i rendered blank (${distinct.size} distinct colours)", distinct.size > 6)

            if (outDir != null) {
                runCatching {
                    FileOutputStream(File(outDir, "health_book_%02d.png".format(i))).use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }.onFailure { println("BOOK_RENDER_WRITE_FAIL $i: ${it.message}") }
            }
            if (dumpPages) {
                // Streamed over logcat rather than written to a file: device storage is unreliable
                // across emulator images. Chunked, because logcat truncates long lines.
                val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 72, it) }
                val b64 = Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP)
                b64.chunked(2000).forEachIndexed { seq, part ->
                    Log.i("NRBOOK", "P$i:$seq:$part")
                }
                Log.i("NRBOOK", "P$i:END:${b64.length}")
            }
            bmp.recycle()
        }

        println("BOOK_RENDER_WROTE=${outDir?.listFiles()?.size ?: 0} of ${book.pages.size}")
    }
}
