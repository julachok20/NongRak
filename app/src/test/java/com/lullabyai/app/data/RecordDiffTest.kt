package com.lullabyai.app.data

import com.lullabyai.app.data.model.RecordEdit
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * What a parent is told changed. The traps pinned here: numeric columns that come back as "12.00"
 * on one side and 12.0 on the other, UTC timestamps that must read in Bangkok time, and bookkeeping
 * columns that must never surface as "what changed".
 */
class RecordDiffTest {

    private val bkk = ZoneId.of("Asia/Bangkok")

    private fun edit(before: Map<String, Any?>, after: Map<String, Any?>?) = RecordEdit(
        id = 1,
        oldRow = buildJsonObject { before.forEach { (k, v) -> put(k, prim(v)) } },
        newRow = after?.let { m -> buildJsonObject { m.forEach { (k, v) -> put(k, prim(v)) } } }
    )

    private fun prim(v: Any?) = when (v) {
        null -> JsonNull
        is Number -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    @Test
    fun `a weight correction reads as before, after and a signed difference`() {
        val c = RecordDiff.changes(edit(mapOf("weight_kg" to "11.80"), mapOf("weight_kg" to 12.0)), isThai = true, zone = bkk)
        assertEquals(1, c.size)
        assertEquals("น้ำหนักตัว", c[0].label)
        assertEquals("11.8 กก.", c[0].before)
        assertEquals("12.0 กก.", c[0].after)
        assertEquals("+0.2 กก.", c[0].delta)
    }

    @Test
    fun `the same number written two ways is not a change`() {
        val c = RecordDiff.changes(edit(mapOf("height_cm" to "40.00"), mapOf("height_cm" to 40)), isThai = true, zone = bkk)
        assertTrue(c.isEmpty())
    }

    @Test
    fun `times read in local time and a times-only edit is recognised`() {
        val c = RecordDiff.changes(
            edit(
                mapOf("end_time" to "2026-09-20T07:45:00+00:00", "notes" to "x"),
                mapOf("end_time" to "2026-09-20T08:00:00+00:00", "notes" to "x")
            ),
            isThai = true, zone = bkk
        )
        assertEquals(listOf("14:45 น." to "15:00 น."), c.map { it.before to it.after })
        assertNull("times have no delta", c[0].delta)
        assertTrue(RecordDiff.isTimeOnly(c))
    }

    @Test
    fun `bookkeeping columns never show up as changes`() {
        val c = RecordDiff.changes(
            edit(
                mapOf("id" to "a", "created_by" to "u1", "photo_path" to "p/1.jpg", "volume_ml" to 120),
                mapOf("id" to "a", "created_by" to "u2", "photo_path" to "p/2.jpg", "volume_ml" to 150)
            ),
            isThai = true, zone = bkk
        )
        assertEquals(listOf("volume_ml"), c.map { it.field })
        assertEquals("+30 มล.", c[0].delta)
        assertFalse(RecordDiff.isTimeOnly(c))
    }

    @Test
    fun `a value filled in from nothing shows a dash before it`() {
        val c = RecordDiff.changes(edit(mapOf("notes" to null), mapOf("notes" to "งอแงนิดหน่อย")), isThai = true, zone = bkk)
        assertEquals("ไม่มี", c.single().before)
        assertEquals("งอแงนิดหน่อย", c.single().after)
    }

    @Test
    fun `clearing a value reads as reverted to none`() {
        val c = RecordDiff.changes(edit(mapOf("weight_kg" to 12.0), mapOf("weight_kg" to null)), isThai = true, zone = bkk)
        assertEquals("12.0 กก.", c.single().before)
        assertEquals("ไม่มี", c.single().after)
        assertTrue(c.single().cleared)
        assertTrue(RecordDiff.isClearedToNone(c))
        assertNull(c.single().delta)
    }

    @Test
    fun `a delete has no after row and so no changes`() {
        assertTrue(RecordDiff.changes(edit(mapOf("weight_kg" to 12), null), isThai = true, zone = bkk).isEmpty())
    }

    @Test
    fun `sleep duration is worked out from the row's two ends`() {
        val row = buildJsonObject {
            put("start_time", "2026-09-20T06:08:00+00:00")
            put("end_time", "2026-09-20T08:00:00+00:00")
        }
        assertEquals("1 ชั่วโมง 52 นาที", RecordDiff.sleepDuration(row, isThai = true))
        assertNull(RecordDiff.sleepDuration(buildJsonObject { put("start_time", "2026-09-20T06:08:00+00:00") }, true))
    }
}
