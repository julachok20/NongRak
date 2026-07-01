package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.AiAnalysis
import com.lullabyai.app.data.model.AiTask
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AiRepository {

    private val activeTaskTarget = 5  // show 5 AI-recommended activities on the พัฒนาการ page
    private val expiryHours = 5L  // a completed activity stays 5h, then is archived & replaced by the next one

    // ---------------- AI TASKS ----------------

    suspend fun getActiveTasks(childId: String): List<AiTask> = runCatching {
        supabase.from("ai_tasks").select {
            filter { eq("child_id", childId); eq("status", "active") }
            order("created_at", Order.ASCENDING)
        }.decodeList<AiTask>()
    }.getOrDefault(emptyList())

    suspend fun getHistoryTasks(childId: String): List<AiTask> = runCatching {
        supabase.from("ai_tasks").select {
            filter { eq("child_id", childId); eq("status", "history") }
            order("completed_at", Order.DESCENDING)
        }.decodeList<AiTask>()
    }.getOrDefault(emptyList())

    suspend fun completeTask(taskId: String) {
        runCatching {
            supabase.from("ai_tasks").update(
                buildJsonObject {
                    put("is_completed", JsonPrimitive(true))
                    put("completed_at", JsonPrimitive(Instant.now().toString()))
                }
            ) { filter { eq("id", taskId) } }
        }
    }

    /** Deselect / un-mark a completed task. */
    suspend fun uncompleteTask(taskId: String) {
        runCatching {
            supabase.from("ai_tasks").update(
                buildJsonObject {
                    put("is_completed", JsonPrimitive(false))
                    put("completed_at", kotlinx.serialization.json.JsonNull)
                }
            ) { filter { eq("id", taskId) } }
        }
    }

    /** "Not available" button: hard-delete the task and generate a fresh replacement. */
    suspend fun replaceTask(taskId: String, child: Child, milestoneHints: List<String> = emptyList()) {
        runCatching { supabase.from("ai_tasks").delete { filter { eq("id", taskId) } } }
        val newTexts = generateTaskTexts(child, 1, milestoneHints)
        insertTasks(child.id, newTexts)
    }

    private suspend fun archiveTask(taskId: String) {
        runCatching {
            supabase.from("ai_tasks").update(
                buildJsonObject { put("status", JsonPrimitive("history")) }
            ) { filter { eq("id", taskId) } }
        }
    }

    private suspend fun insertTasks(childId: String, texts: List<String>) {
        if (texts.isEmpty()) return
        val rows = texts.map { AiTask(childId = childId, taskText = it) }
        runCatching { supabase.from("ai_tasks").insert(rows) }
    }

    /**
     * Keeps the task list fresh:
     *  - daily refresh: if no active task was created today, archive all and regenerate
     *  - 4h replacement: a completed activity older than [expiryHours] moves to history
     *  - refill: always keep [activeTaskTarget] active tasks (so finishing one brings the next)
     */
    suspend fun syncTasks(child: Child, milestoneHints: List<String> = emptyList()): List<AiTask> {
        val childId = child.id
        var tasks = getActiveTasks(childId)
        val today = LocalDate.now()

        val createdToday = tasks.any { t ->
            t.createdAt?.let { parseInstant(it)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today } ?: false
        }

        // New day → archive everything and regenerate a fresh set
        if (tasks.isNotEmpty() && !createdToday) {
            tasks.forEach { archiveTask(it.id) }
            tasks = emptyList()
        }

        // 4h-after-complete replacement
        val now = Instant.now()
        val expired = tasks.filter { t ->
            t.isCompleted && t.completedAt?.let { ts ->
                parseInstant(ts)?.let { ChronoUnit.HOURS.between(it, now) >= expiryHours } ?: false
            } ?: false
        }
        expired.forEach { archiveTask(it.id) }
        val remaining = tasks - expired.toSet()

        // Refill to target
        val needed = activeTaskTarget - remaining.size
        if (needed > 0) {
            val newTasks = generateTaskTexts(child, needed, milestoneHints)
            insertTasks(childId, newTasks)
        }

        return getActiveTasks(childId)
    }

    private suspend fun generateTaskTexts(child: Child, count: Int, milestoneHints: List<String> = emptyList()): List<String> {
        // Ground the activities in the child's official developmental milestones (CDC/Thai DSPM) that
        // are not yet achieved, so each activity helps the parent work toward a real milestone.
        val milestoneBlock = if (milestoneHints.isNotEmpty())
            "ออกแบบกิจกรรมให้ช่วยส่งเสริมพัฒนาการตามหมุดหมายมาตรฐาน (อ้างอิง CDC / DSPM กรมอนามัย) ที่ยังทำไม่ได้ ดังนี้:\n" +
            milestoneHints.take(count).joinToString("\n") { "- $it" } + "\n"
        else
            "อ้างอิงพัฒนาการตามวัยมาตรฐาน (CDC / DSPM กรมอนามัย) ของเด็กวัยนี้\n"
        val prompt = """
สร้างกิจกรรมส่งเสริมพัฒนาการประจำวันสำหรับเด็กแรกเกิดถึง 3 ปี ชื่อ ${child.name} อายุ ${ageText(child.birthdate)}
จำนวน $count กิจกรรม ที่พ่อแม่ทำกับลูกได้ง่าย ๆ ที่บ้าน เหมาะสมกับวัยของเด็ก
$milestoneBlock เน้นพัฒนาการด้านการเรียนรู้ ภาษาและการสื่อสาร การคิด สังคมและอารมณ์ และกล้ามเนื้อมัดเล็ก
ไม่ใช่กิจกรรมออกกำลังกายหรือการเคลื่อนไหวร่างกาย (กล้ามเนื้อมัดใหญ่) เพราะมีหน้าออกกำลังกายแยกอยู่แล้ว
ตอบเป็นภาษาไทย บรรทัดละ 1 กิจกรรม ในรูปแบบนี้เท่านั้น:
ชื่อกิจกรรมสั้นๆ | เป้าหมายสั้นๆ
ห้ามใส่เลขลำดับ ห้ามใส่ bullet ห้ามมีหัวข้ออื่น
""".trim()
        val raw = runCatching { ClaudeClient.generateContent(prompt, useSearch = false) }.getOrDefault("")
        val lines = raw.split("\n")
            .map { it.trim().removePrefix("-").removePrefix("•").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.contains("|") && it.length in 3..160 }
        return if (lines.size >= count) lines.take(count)
        else (lines + defaultTasks()).distinct().take(count)
    }

    private fun defaultTasks() = listOf(
        "อ่านนิทานให้ลูกฟัง | ฟังนิทาน 1 เล่มก่อนนอน",
        "ร้องเพลงพร้อมท่าทาง | ทำท่าตามเพลงได้ 1 เพลง",
        "เล่นต่อบล็อกไม้ | ต่อบล็อกได้ 3 ชิ้น",
        "เดินเล่นพูดคุยรอบบ้าน | ชี้และเรียกชื่อสิ่งของ 5 อย่าง"
    )

    // ---------------- AI ANALYSIS CACHE ----------------

    suspend fun getCachedAnalysis(childId: String, type: String): String? = runCatching {
        supabase.from("ai_analysis").select {
            filter { eq("child_id", childId); eq("analysis_type", type) }
            limit(1)
        }.decodeList<AiAnalysis>().firstOrNull()?.content
    }.getOrNull()

    private suspend fun saveAnalysis(childId: String, type: String, content: String) {
        runCatching {
            // Replace any existing cached analysis of this type (acts as upsert)
            supabase.from("ai_analysis").delete {
                filter { eq("child_id", childId); eq("analysis_type", type) }
            }
            supabase.from("ai_analysis").insert(
                buildJsonObject {
                    put("child_id", JsonPrimitive(childId))
                    put("analysis_type", JsonPrimitive(type))
                    put("content", JsonPrimitive(content))
                    put("updated_at", JsonPrimitive(Instant.now().toString()))
                }
            )
        }
    }

    /**
     * Returns cached analysis if present AND valid.
     * If the cache contains an old error message (quota exceeded, "ขออภัย", etc.)
     * it is discarded and fresh content is generated instead.
     */
    suspend fun getOrGenerate(childId: String, type: String, generate: suspend () -> String): String {
        getCachedAnalysis(childId, type)?.let { cached ->
            if (!isErrorResponse(cached)) return cached
            // Stale error cached — delete it and regenerate below
            runCatching {
                supabase.from("ai_analysis").delete {
                    filter { eq("child_id", childId); eq("analysis_type", type) }
                }
            }
        }
        val fresh = generate()
        if (!isErrorResponse(fresh)) saveAnalysis(childId, type, fresh)
        return fresh
    }

    /** Force regeneration (อัปเดตข้อมูล button). */
    suspend fun refresh(childId: String, type: String, generate: suspend () -> String): String {
        val fresh = generate()
        if (!isErrorResponse(fresh)) saveAnalysis(childId, type, fresh)
        return fresh
    }

    /**
     * Returns true when [text] looks like an AI error / quota message rather than
     * a real analysis result. Such cached values are discarded on the next read.
     */
    private fun isErrorResponse(text: String): Boolean {
        if (text.length < 30) return true          // too short to be a real analysis
        val lower = text.lowercase()
        return lower.contains("quota")             // "No Quota", "quota exceeded"
            || lower.contains("ขออภัย")            // Thai apology from fallback message
            || lower.contains("กรุณาลองใหม่")      // "please try again"
            || lower.contains("api key")
            || lower.contains("invalid")
            || lower.contains("error")
            || lower.contains("unable to")
            || lower.contains("cannot")
    }

    private fun parseInstant(s: String): Instant? = runCatching { Instant.parse(s) }
        .recoverCatching { java.time.OffsetDateTime.parse(s).toInstant() }
        .recoverCatching { java.time.LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant() }
        .getOrNull()

    private fun ageText(birthdate: String): String = try {
        val p = birthdate.split("-")
        val y = p[0].toInt(); val m = p[1].toInt()
        val now = java.util.Calendar.getInstance()
        var years = now.get(java.util.Calendar.YEAR) - y
        var months = now.get(java.util.Calendar.MONTH) + 1 - m
        if (months < 0) { years--; months += 12 }
        if (years > 0) "$years ปี $months เดือน" else "$months เดือน"
    } catch (e: Exception) { "ไม่ทราบอายุ" }
}
