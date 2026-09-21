package com.lullabyai.app.data

import com.lullabyai.app.data.model.ChildActivity
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.ChildFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the model says is only a proposal. These pin the checks that decide whether a proposal
 * reaches a child's profile — above all, that nothing gets in unless its words are in the note.
 */
class FavoriteExtractorTest {

    private val lines = listOf(
        "มื้อกลางวัน · กล้วยบด — ชอบมาก กินหมดถ้วย",   // 1
        "งอแงตอนเย็น สงบลงเมื่อเปิดเพลงกล่อมเด็ก",       // 2
        "ไปสวนสัตว์ — ตื่นเต้นกับยีราฟ"                    // 3
    )

    @Test
    fun `keeps a proposal whose label is in the line it cites`() {
        val out = FavoriteExtractor.parse(
            """{"items":[{"category":"food","label":"กล้วยบด","line":1}]}""", lines, emptyList()
        )
        assertEquals(listOf(FavoriteExtractor.Candidate("food", "กล้วยบด")), out)
    }

    @Test
    fun `drops a label the model invented`() {
        val out = FavoriteExtractor.parse(
            """{"items":[{"category":"food","label":"ไอศกรีมช็อกโกแลต","line":1}]}""", lines, emptyList()
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `drops a real phrase cited against the wrong line`() {
        // "เพลงกล่อมเด็ก" is on line 2, not 3 — a line number that does not back the claim fails.
        val out = FavoriteExtractor.parse(
            """{"items":[{"category":"sound","label":"เพลงกล่อมเด็ก","line":3}]}""", lines, emptyList()
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `drops unknown categories, missing lines and out-of-range line numbers`() {
        val out = FavoriteExtractor.parse(
            """{"items":[
                {"category":"medicine","label":"กล้วยบด","line":1},
                {"category":"food","label":"กล้วยบด"},
                {"category":"food","label":"กล้วยบด","line":9}
            ]}""",
            lines, emptyList()
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun `skips what is already recorded, ignoring case and spacing`() {
        val existing = listOf(ChildFavorite(category = "food", label = " กล้วยบด "))
        val out = FavoriteExtractor.parse(
            """{"items":[{"category":"food","label":"กล้วยบด","line":1},
                         {"category":"sound","label":"เพลงกล่อมเด็ก","line":2}]}""",
            lines, existing
        )
        assertEquals(listOf(FavoriteExtractor.Candidate("sound", "เพลงกล่อมเด็ก")), out)
    }

    @Test
    fun `survives a code fence, junk and an empty answer without throwing`() {
        val fenced = "```json\n{\"items\":[{\"category\":\"activity\",\"label\":\"สวนสัตว์\",\"line\":3}]}\n```"
        assertEquals(
            listOf(FavoriteExtractor.Candidate("activity", "สวนสัตว์")),
            FavoriteExtractor.parse(fenced, lines, emptyList())
        )
        assertTrue(FavoriteExtractor.parse("not json at all", lines, emptyList()).isEmpty())
        assertTrue(FavoriteExtractor.parse("", lines, emptyList()).isEmpty())
        assertTrue(FavoriteExtractor.parse(null, lines, emptyList()).isEmpty())
    }

    @Test
    fun `caps one run so a runaway answer cannot flood the profile`() {
        val many = (1..9).map { "ของเล่นชิ้นที่ $it" }
        val body = many.mapIndexed { i, _ -> """{"category":"toy","label":"ชิ้นที่ ${i + 1}","line":${i + 1}}""" }
        val out = FavoriteExtractor.parse("""{"items":[${body.joinToString(",")}]}""", many, emptyList())
        assertEquals(FavoriteExtractor.MAX_PER_RUN, out.size)
    }

    @Test
    fun `medical rows and prose-free rows never become source lines`() {
        val feed = listOf(
            ChildActivityEntry(kind = "medicine", category = "medical", note = "ยาน้ำรสส้ม ชอบมาก"),
            ChildActivityEntry(kind = "symptom", category = "medical", note = "ไข้ ชอบให้กอด"),
            ChildActivityEntry(kind = "milk", category = "nutrition", detail = "formula 120 ml", note = null),
            ChildActivityEntry(kind = "waste", category = "daily", note = "ปกติ"),
            ChildActivityEntry(kind = "food", category = "nutrition", detail = "lunch · กล้วยบด", note = "ชอบ"),
            ChildActivityEntry(kind = "care_mood", category = "care", note = "หัวเราะตอนเล่นจ๊ะเอ๋")
        )
        val activities = listOf(ChildActivity(title = "ไปสวนสัตว์", locationName = "เขาดิน", notes = null))
        assertEquals(
            listOf("lunch · กล้วยบด — ชอบ", "หัวเราะตอนเล่นจ๊ะเอ๋", "ไปสวนสัตว์ — เขาดิน"),
            FavoriteExtractor.sourceLines(feed, activities)
        )
    }
}
