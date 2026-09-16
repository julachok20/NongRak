package com.lullabyai.app.data.routine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One repeating item in a child's day ("08:30 นมแม่ขวด & วิตามิน").
 *
 * [timeOfDay] is a Postgres `time`, so it arrives as "08:30:00"; the UI works in minutes since
 * midnight through [CareRoutinePlan.parseMinuteOfDay].
 */
@Serializable
data class CareRoutine(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("time_of_day") val timeOfDay: String = "",
    val category: String = "other",
    val title: String = "",
    val detail: String = "",
    /**
     * "สบู่สูตรอ่อนโยน & ผ้าเช็ดตัวอุ่น" — what to have ready before the item starts, or what it
     * aims at. Read before the item; [detail] is read once it has begun.
     */
    @SerialName("prep_note") val prepNote: String = "",
    val active: Boolean = true,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** That an item happened (or was deliberately skipped) on one date, and who recorded it. */
@Serializable
data class CareRoutineLog(
    val id: String = "",
    @SerialName("routine_id") val routineId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("on_date") val onDate: String = "",
    /** "done" or "skipped". */
    val status: String = "done",
    val note: String = "",
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null
)

/** The categories the timeline colours and labels, matching the Stitch routine design. */
object CareRoutineCategories {
    data class Category(val key: String, val th: String, val en: String)

    val ALL = listOf(
        Category("health", "สุขภาพ & ตื่นนอน", "Health & waking"),
        Category("feeding", "การกินนม", "Milk"),
        Category("solid", "อาหารเสริม", "Solid food"),
        Category("activity", "กิจกรรมพัฒนาการ", "Development"),
        Category("sleep", "การนอนหลับ", "Sleep"),
        Category("hygiene", "สุขอนามัย", "Hygiene"),
        Category("night", "การนอนกลางคืน", "Bedtime"),
        Category("other", "อื่นๆ", "Other")
    )

    fun label(key: String, isThai: Boolean): String {
        val c = ALL.firstOrNull { it.key == key } ?: ALL.last()
        return if (isThai) c.th else c.en
    }
}
