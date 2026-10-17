package com.lullabyai.app.data

import com.lullabyai.app.data.model.RecordEdit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Turns one record_edits row into the lines a parent reads: "น้ำหนักตัว 11.8 กก. → 12.0 กก. (+0.2 กก.)".
 *
 * record_edits keeps whole rows, so this decides what is worth showing. Bookkeeping columns (ids,
 * timestamps of the write itself, storage paths) are never shown — a parent asked what changed about
 * their child, not which row id moved. Anything not in [FIELDS] is skipped rather than printed as a
 * raw column name.
 */
object RecordDiff {

    data class Change(
        val field: String,
        val label: String,
        val before: String,
        val after: String,
        /** Signed difference with its unit, for numeric fields only: "+0.2 กก.". */
        val delta: String? = null,
        /** True when the new value is empty — history then says this was reverted to none. */
        val cleared: Boolean = false
    )

    private enum class Kind { NUMBER_1DP, INTEGER, TIME, DATE, TEXT }

    private data class Field(val th: String, val en: String, val kind: Kind, val unitTh: String = "", val unitEn: String = "")

    /** Display order, labels and formatting for the columns a parent recognises. */
    private val FIELDS: LinkedHashMap<String, Field> = linkedMapOf(
        "weight_kg" to Field("น้ำหนักตัว", "Weight", Kind.NUMBER_1DP, "กก.", "kg"),
        "height_cm" to Field("ส่วนสูง", "Height", Kind.NUMBER_1DP, "ซม.", "cm"),
        "volume_ml" to Field("ปริมาณ", "Volume", Kind.INTEGER, "มล.", "ml"),
        "duration_min" to Field("ระยะเวลาเข้าเต้า", "Time at the breast", Kind.INTEGER, "นาที", "min"),
        "temperature" to Field("อุณหภูมิ", "Temperature", Kind.NUMBER_1DP, "°C", "°C"),
        "start_time" to Field("เวลาเข้านอน", "Fell asleep", Kind.TIME),
        "end_time" to Field("เวลาตื่นนอน", "Woke up", Kind.TIME),
        "taken_at" to Field("เวลาให้ยา", "Given at", Kind.TIME),
        "logged_at" to Field("เวลาที่บันทึก", "Logged at", Kind.TIME),
        "recorded_at" to Field("เวลาที่บันทึก", "Recorded at", Kind.TIME),
        "log_date" to Field("วันที่", "Date", Kind.DATE),
        "date_given" to Field("วันที่ฉีด", "Date given", Kind.DATE),
        "vaccine_name" to Field("วัคซีน", "Vaccine", Kind.TEXT),
        "item_name" to Field("รายการอาหาร", "Food", Kind.TEXT),
        "meal_type" to Field("มื้อ", "Meal", Kind.TEXT),
        "milk_type" to Field("ชนิดนม", "Milk", Kind.TEXT),
        "side" to Field("ข้าง", "Side", Kind.TEXT),
        "waste_type" to Field("ประเภท", "Type", Kind.TEXT),
        "consistency" to Field("ลักษณะ", "Consistency", Kind.TEXT),
        "color" to Field("สี", "Colour", Kind.TEXT),
        "amount" to Field("ปริมาณ", "Amount", Kind.TEXT),
        "kind" to Field("กิจกรรม", "Activity", Kind.TEXT),
        "mood" to Field("อารมณ์", "Mood", Kind.TEXT),
        "feeling" to Field("อาการ", "Feeling", Kind.TEXT),
        "pain_level" to Field("ระดับความเจ็บ", "Pain level", Kind.INTEGER),
        "quality_stars" to Field("คุณภาพการนอน", "Sleep quality", Kind.INTEGER, "ดาว", "stars"),
        "status" to Field("สถานะ", "Status", Kind.TEXT),
        "abnormal" to Field("สิ่งผิดปกติ", "Concern", Kind.TEXT),
        "place" to Field("สถานที่", "Place", Kind.TEXT),
        "note" to Field("บันทึก", "Note", Kind.TEXT),
        "notes" to Field("บันทึก", "Note", Kind.TEXT)
    )

    private val TIME_FIELDS = setOf("start_time", "end_time", "taken_at", "logged_at", "recorded_at")

    fun changes(edit: RecordEdit, isThai: Boolean, zone: ZoneId = ZoneId.systemDefault()): List<Change> {
        val before = edit.oldRow ?: return emptyList()
        val after = edit.newRow ?: return emptyList()      // a delete has nothing to compare against
        return FIELDS.mapNotNull { (key, field) ->
            val a = before[key]
            val b = after[key]
            if (sameValue(a, b)) return@mapNotNull null
            val afterText = render(b, field, isThai, zone)
            val cleared = text(b) == null
            Change(
                field = key,
                label = if (isThai) field.th else field.en,
                before = render(a, field, isThai, zone),
                after = afterText,
                delta = if (cleared) null else delta(a, b, field, isThai),
                cleared = cleared
            )
        }
    }

    fun emptyValue(isThai: Boolean) = if (isThai) "ไม่มี" else "None"

    /** True when every visible change wiped a value — history then says it was reverted to none. */
    fun isClearedToNone(changes: List<Change>): Boolean =
        changes.isNotEmpty() && changes.all { it.cleared }

    /** True when the edit only moved times — the history then says "แก้ไขเวลา" instead of "แก้ไขข้อมูล". */
    fun isTimeOnly(changes: List<Change>): Boolean =
        changes.isNotEmpty() && changes.all { it.field in TIME_FIELDS } && !isClearedToNone(changes)

    /** "1 ชั่วโมง 52 นาที" for a sleep row that has both ends, else null. */
    fun sleepDuration(row: JsonObject?, isThai: Boolean): String? {
        val start = row?.get("start_time")?.let(::text)?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        val end = row?.get("end_time")?.let(::text)?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
        if (start == null || end == null || !end.isAfter(start)) return null
        val mins = Duration.between(start, end).toMinutes()
        val h = mins / 60
        val m = mins % 60
        return if (isThai) listOfNotNull(if (h > 0) "$h ชั่วโมง" else null, "$m นาที").joinToString(" ")
        else listOfNotNull(if (h > 0) "${h}h" else null, "${m}m").joinToString(" ")
    }

    private fun text(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull

    /** numeric(5,2) comes back as "12.00" on one side and 12.0 on the other; compare as numbers. */
    private fun sameValue(a: JsonElement?, b: JsonElement?): Boolean {
        val x = text(a)
        val y = text(b)
        if (x == y) return true
        val nx = x?.toDoubleOrNull()
        val ny = y?.toDoubleOrNull()
        return nx != null && ny != null && nx == ny
    }

    private fun render(e: JsonElement?, f: Field, isThai: Boolean, zone: ZoneId): String {
        val raw = text(e) ?: return emptyValue(isThai)
        val unit = if (isThai) f.unitTh else f.unitEn
        fun withUnit(v: String) = if (unit.isEmpty()) v else "$v $unit"
        return when (f.kind) {
            Kind.NUMBER_1DP -> raw.toDoubleOrNull()?.let { withUnit("%.1f".format(it)) } ?: raw
            Kind.INTEGER -> raw.toDoubleOrNull()?.let { withUnit(it.toLong().toString()) } ?: raw
            Kind.TIME -> runCatching {
                val t = OffsetDateTime.parse(raw).atZoneSameInstant(zone)
                "%02d:%02d".format(t.hour, t.minute) + if (isThai) " น." else ""
            }.getOrDefault(raw)
            Kind.DATE, Kind.TEXT -> raw
        }
    }

    private fun delta(a: JsonElement?, b: JsonElement?, f: Field, isThai: Boolean): String? {
        if (f.kind != Kind.NUMBER_1DP && f.kind != Kind.INTEGER) return null
        val x = text(a)?.toDoubleOrNull() ?: return null
        val y = text(b)?.toDoubleOrNull() ?: return null
        val d = y - x
        val unit = if (isThai) f.unitTh else f.unitEn
        val num = if (f.kind == Kind.INTEGER) d.toLong().toString() else "%.1f".format(d)
        val signed = if (d > 0) "+$num" else num
        return if (unit.isEmpty()) signed else "$signed $unit"
    }
}
