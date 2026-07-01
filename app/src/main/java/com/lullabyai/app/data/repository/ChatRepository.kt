package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.ChatMessage
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.SearchCacheRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ChatRepository {

    private val searchCache = SearchCacheRepository()

    // ---- Sessions ----

    suspend fun getSessions(): Result<List<ChatSession>> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("chat_sessions").select {
            filter { eq("user_id", userId) }
            order("updated_at", Order.DESCENDING)
        }.decodeList<ChatSession>()
    }

    suspend fun createSession(title: String = "แชทใหม่"): Result<ChatSession> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        supabase.from("chat_sessions").insert(
            ChatSession(userId = userId, title = title)
        ) { select() }.decodeSingle<ChatSession>()
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        supabase.from("chat_sessions").delete { filter { eq("id", sessionId) } }
    }

    suspend fun renameSession(sessionId: String, title: String): Result<Unit> = runCatching {
        supabase.from("chat_sessions").update(mapOf("title" to title)) {
            filter { eq("id", sessionId) }
        }
    }

    // ---- Messages ----

    suspend fun getMessages(sessionId: String): Result<List<ChatMessage>> = runCatching {
        supabase.from("chat_messages").select {
            filter { eq("session_id", sessionId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<ChatMessage>()
    }

    private suspend fun saveMessage(sessionId: String, role: String, content: String) {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return
        // Insert an explicit JSON object so every column is always sent. (Using the data class
        // would drop fields equal to their default — e.g. role="user" — causing a NOT NULL error.)
        supabase.from("chat_messages").insert(
            buildJsonObject {
                put("user_id", userId)
                put("session_id", sessionId)
                put("role", role)
                put("content", content)
            }
        )
        // Bump session's updated_at so it sorts to the top — best-effort, must not break the insert
        runCatching {
            supabase.from("chat_sessions").update(mapOf("updated_at" to java.time.Instant.now().toString())) {
                filter { eq("id", sessionId) }
            }
        }
    }

    /**
     * RAG-style: builds the prompt from prior conversation history + full child profile context,
     * so the AI "remembers" everything said in this session AND knows the child's data.
     * Claude is called FIRST; DB persistence is best-effort so the AI still works if the DB fails.
     */
    suspend fun sendMessage(sessionId: String, userMessage: String, child: Child?, imageBytes: ByteArray? = null, imageMime: String? = null): Result<String> = runCatching {
        // Retrieve prior history (memory) BEFORE adding the new message
        val history = runCatching { getMessages(sessionId).getOrDefault(emptyList()) }.getOrDefault(emptyList())

        val childContext = buildChildContext(child)

        val conversation = history.joinToString("\n") { msg ->
            val who = if (msg.role == "user") "ผู้ใช้" else "ผู้ช่วย"
            "$who: ${msg.content}"
        }

        // RAG: pull grounding knowledge from the curated handbook/research corpus (pgvector). Best-effort
        // and optional — only chunks with decent similarity are used so off-topic questions are unaffected.
        // Skipped for image-only messages (no text to match on).
        val ragChunks = if (userMessage.isNotBlank())
            com.lullabyai.app.data.remote.ResearchRagClient.search(userMessage, k = 4).filter { it.similarity >= 0.40 }
        else emptyList()

        val prompt = buildString {
            if (childContext.isNotEmpty()) { appendLine(childContext); appendLine() }
            if (ragChunks.isNotEmpty()) {
                appendLine("ความรู้อ้างอิงจากคู่มือและงานวิจัยสุขภาพเด็ก (ใช้ประกอบการตอบเมื่อเกี่ยวข้อง อย่าแต่งข้อมูลเกินจากนี้):")
                ragChunks.forEach { appendLine("- ${it.content} (ที่มา: ${it.source})") }
                appendLine()
            }
            if (conversation.isNotEmpty()) {
                appendLine("ประวัติการสนทนา (จดจำบริบททั้งหมด):")
                appendLine(conversation)
                appendLine()
            }
            appendLine("ตอบข้อความล่าสุดของผู้ใช้ต่อไปนี้อย่างเป็นธรรมชาติ โดยใช้บริบทข้างต้น:")
            append(userMessage)
        }

        // Persist the user's message FIRST so it survives even if the AI call fails
        // (no response caching for chat — every message is its own conversation turn).
        runCatching { saveMessage(sessionId, "user", userMessage.ifBlank { if (imageBytes != null) "[ส่งรูปภาพ]" else "" }) }

        // Encode an attached image (downscaled by the caller) for the vision-capable AI path.
        val imageB64 = imageBytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

        // Call the AI (useSearch=false for chat: the system prompt already has the knowledge base).
        // maxTokens 4096: Thai is token-heavy, so a smaller limit truncates answers mid-sentence.
        val aiText = ClaudeClient.generateContent(
            prompt, useSearch = false, maxTokens = 4096,
            imageBase64 = imageB64, imageMimeType = imageMime ?: "image/jpeg"
        )

        val response = aiText

        // Persist the AI reply + set the session title from the first user message (best-effort)
        runCatching {
            saveMessage(sessionId, "model", response)
            if (history.none { it.role == "user" }) {
                val title = userMessage.take(30) + if (userMessage.length > 30) "..." else ""
                renameSession(sessionId, title)
            }
        }

        response
    }

    /** Builds a rich context string from the child profile + medical history (synced from onboarding). */
    private suspend fun buildChildContext(child: Child?): String {
        if (child == null) return ""
        val activity = when (child.activityLevel) {
            "low" -> "น้อย"; "high" -> "มาก"; else -> "ปานกลาง"
        }
        val medical = runCatching {
            supabase.from("medical_history").select {
                filter { eq("child_id", child.id) }
                limit(1)
            }.decodeList<com.lullabyai.app.data.model.MedicalHistory>().firstOrNull()
        }.getOrNull()

        return buildString {
            append("ข้อมูลเด็กของผู้ใช้ (ใช้ข้อมูลนี้ในการวิเคราะห์และให้คำแนะนำ):\n")
            append("- ชื่อ: ${child.name}\n")
            append("- อายุ: ${calculateAge(child.birthdate)} (วันเกิด ${child.birthdate})\n")
            append("- เพศ: ${if (child.gender == "male") "ชาย" else if (child.gender == "female") "หญิง" else "ไม่ระบุ"}\n")
            child.weightKg?.let { append("- น้ำหนัก: $it กก.\n") }
            child.heightCm?.let { append("- ส่วนสูง: $it ซม.\n") }
            child.sleepHours?.let { append("- นอนหลับเฉลี่ย: ${it.toInt()} ชม./วัน\n") }
            append("- ระดับกิจกรรม: $activity\n")
            if (child.allergies?.isNotBlank() == true) append("- ประวัติแพ้: ${child.allergies}\n")
            if (child.notes?.isNotBlank() == true) append("- หมายเหตุ: ${child.notes}\n")
            medical?.let { m ->
                if (m.hasTakenMedication) append("- เคยได้รับยา: ${m.medicationDetails ?: "เคย"}\n")
                if (m.underlyingConditions?.isNotBlank() == true) append("- โรคประจำตัว: ${m.underlyingConditions}\n")
                if (!m.vaccinesGiven.isNullOrEmpty()) append("- วัคซีนที่ได้รับ: ${m.vaccinesGiven.joinToString(", ")}\n")
            }
        }
    }

    // ---- One-off AI analyses (no session needed) ----

    /** Dashboard insight — a 3-sentence summary of the child and their health risk. */
    suspend fun generateDashboardSummary(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปภาพรวมเกี่ยวกับเด็กและความเสี่ยงด้านสุขภาพ " +
            "ภายใน 3 ประโยคเท่านั้น ใช้ภาษาที่เป็นธรรมชาติ ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ"
        val cacheKey = "dashboard_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048)
            .also { searchCache.save(cacheKey, it, "dashboard") }
    }

    /** Child Summary (ข้อมูลลูก ภาพรวม) — factual, instruction-style summary, under 7 sentences. */
    suspend fun generateChildSummary(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปข้อมูลเกี่ยวกับเด็ก ความยาวไม่เกิน 7 ประโยค " +
            "เขียนในลักษณะข้อมูลเชิงข้อเท็จจริงและคำแนะนำ ไม่ต้องใช้น้ำเสียงเหมือนมนุษย์พูดคุย " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ"
        val cacheKey = "child_summary_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048)
            .also { searchCache.save(cacheKey, it, "child_summary") }
    }

    /** Child Health page (ข้อมูลลูก สุขภาพ) — health check, under 5 sentences. */
    suspend fun generateHealthAnalysis(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น ตรวจสอบและประเมินสุขภาพของเด็ก ความยาวไม่เกิน 5 ประโยค " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ"
        val cacheKey = "health_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048)
            .also { searchCache.save(cacheKey, it, "health_analysis") }
    }

    /** Disease Risk page (ข้อมูลลูก ความเสี่ยงโรค) — risk summary, under 5 sentences. */
    suspend fun generateDiseaseRiskAnalysis(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปความเสี่ยงโรคของเด็กสำหรับเด็กวัยนี้ในประเทศไทย ความยาวไม่เกิน 5 ประโยค " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ"
        val cacheKey = "disease_risk_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 2048)
            .also { searchCache.save(cacheKey, it, "disease_risk") }
    }

    /** Returns a pipe-format list of likely/at-risk diseases: name|level|percent|description per line. */
    suspend fun generateDiseaseList(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) + """

จากข้อมูลเด็กข้างต้น ประเมินโรคที่พบบ่อยหรือมีความเสี่ยงสำหรับเด็กวัยนี้ในประเทศไทย 5 รายการ
ตอบกลับในรูปแบบบรรทัดละ 1 โรค คั่นด้วยเครื่องหมาย | ตามนี้เท่านั้น:
ชื่อโรค|ระดับ|เปอร์เซ็นต์|คำอธิบายสั้นๆ
โดย "ระดับ" ใช้คำใดคำหนึ่ง: normal, attention, low, high
"เปอร์เซ็นต์" เป็นตัวเลข 0-100 (ไม่ต้องมีเครื่องหมาย %)
ห้ามมีหัวข้อหรือข้อความอื่นนอกเหนือจากนี้
""".trim()
        val cacheKey = "disease_list_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096)
            .also { searchCache.save(cacheKey, it, "disease_list") }
    }

    /** Returns a pipe-format list of prevention tips: title|detail per line. */
    suspend fun generatePrevention(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) + """

ให้คำแนะนำการป้องกันโรคและดูแลสุขภาพสำหรับเด็กคนนี้ 3 ข้อ
ตอบกลับบรรทัดละ 1 ข้อ คั่นด้วย | ตามนี้:
หัวข้อสั้นๆ|รายละเอียดสั้นๆ
ห้ามมีข้อความอื่น
""".trim()
        val cacheKey = "prevention_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096)
            .also { searchCache.save(cacheKey, it, "prevention") }
    }

    suspend fun generateGrowthInsight(child: Child, forceRefresh: Boolean = false): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nวิเคราะห์การเจริญเติบโตของเด็กคนนี้จากข้อมูลข้างต้น " +
            "ให้คำแนะนำเรื่องโภชนาการและการเจริญเติบโตเป็นภาษาไทยอย่างละเอียด ตอบครบถ้วนไม่ตัดทอน"
        val cacheKey = "growth_${child.id}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096)
            .also { searchCache.save(cacheKey, it, "growth_insight") }
    }

    private fun calculateAge(birthdate: String): String {
        return try {
            val parts = birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            var years = now.get(java.util.Calendar.YEAR) - year
            var months = now.get(java.util.Calendar.MONTH) + 1 - month
            if (months < 0) { years--; months += 12 }
            if (years > 0) "$years ปี $months เดือน" else "$months เดือน"
        } catch (e: Exception) { "ไม่ทราบอายุ" }
    }
}
