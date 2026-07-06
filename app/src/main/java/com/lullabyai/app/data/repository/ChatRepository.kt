package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.ChatMessage
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.util.RelationshipLabels
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * System-level persona for the one-off "AI analysis" text cards (dashboard/child summary/health/
 * disease risk/growth). Distinct from the default chat persona: the default's "ให้กำลังใจ" warm
 * framing kept winning out over an inline no-greeting instruction buried in the prompt text, so
 * the no-greeting rule is stated here at the SYSTEM level instead, where models weight it higher.
 */
/**
 * The old version of this hardcoded "always respond in Thai unless the user typed English" —
 * fine for the chat screen (where there's real user text to detect a language from) but these
 * analysis calls have no free-text user input, so that escape hatch never fired: switching the
 * app to English never changed the AI's output language. [isThai] now drives it explicitly.
 */
private fun analysisSystemPrompt(isThai: Boolean): String = if (isThai)
    "คุณคือ \"น้องรัก\" (NongRak) ผู้ช่วย AI สำหรับพ่อแม่ในการเลี้ยงดูเด็กแรกเกิดถึง 3 ปี " +
        "ตอบเป็นภาษาไทยเสมอ ห้ามใช้สัญลักษณ์พิเศษหรือ markdown ห้ามใช้ emoji " +
        "ใช้ภาษาที่เป็นธรรมชาติ อบอุ่น และเข้าใจง่าย นี่คือข้อความสรุป/วิเคราะห์ ไม่ใช่บทสนทนา " +
        "ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ (เช่น สวัสดี, สวัสดีค่ะ, สวัสดีพ่อแม่น้อง) และห้ามเอ่ยชื่อเด็กเป็นประโยคแรก " +
        "ให้เริ่มประโยคแรกด้วยเนื้อหาสาระโดยตรงทันที"
else
    "You are \"NongRak\", an AI assistant for parents raising a child aged 0-3. " +
        "Always respond in English, even though the reference data below is in Thai. Never use special symbols or markdown, never use emoji. " +
        "Use natural, warm, easy-to-understand language. This is a summary/analysis, not a conversation. " +
        "Never open with a greeting or preamble (e.g. \"Hello\", \"Hi there\") and never start the first sentence with the child's name. " +
        "Start the first sentence directly with the substance of the analysis."

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
    suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
        child: Child?,
        imageBytes: ByteArray? = null,
        imageMime: String? = null,
        relationship: String? = null
    ): Result<String> = runCatching {
        // Retrieve prior history (memory) BEFORE adding the new message
        val history = runCatching { getMessages(sessionId).getOrDefault(emptyList()) }.getOrDefault(emptyList())

        val childContext = buildChildContext(child, relationship)

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
            system = RelationshipLabels.chatSystemPrompt(relationship),
            jsonMode = false,
            imageBase64 = imageB64, imageMimeType = imageMime ?: "image/jpeg"
        )

        val response = aiText

        // Persist the CLEANED text only — the raw response can carry an <AGENT_ACTION>/<NEED_INFO>
        // sentinel block (see ChatViewModel.sendMessage), and saving that verbatim would show the
        // literal tag/JSON as chat text the next time this session's history is reopened.
        runCatching {
            val (displayText, _, _) = com.lullabyai.app.util.parseAgentAction(response)
            saveMessage(sessionId, "model", displayText)
            if (history.none { it.role == "user" }) {
                val title = userMessage.take(30) + if (userMessage.length > 30) "..." else ""
                renameSession(sessionId, title)
            }
        }

        response
    }

    /** Builds a rich context string from the child profile + medical history (synced from onboarding). */
    private suspend fun buildChildContext(child: Child?, relationship: String? = null): String {
        if (child == null) {
            if (relationship.isNullOrBlank()) return ""
            return buildString {
                append("ข้อมูลผู้ใช้:\n")
                append("- ความสัมพันธ์กับเด็ก: ${RelationshipLabels.thaiRelationshipLabel(relationship)}\n")
                append("- ให้เรียกผู้ใช้ว่า \"${RelationshipLabels.thaiAddress(relationship)}\"\n")
            }
        }
        val activity = when (child.activityLevel) {
            "low" -> "น้อย"; "high" -> "มาก"; else -> "ปานกลาง"
        }
        val medical = runCatching {
            supabase.from("medical_history").select {
                filter { eq("child_id", child.id) }
                limit(1)
            }.decodeList<com.lullabyai.app.data.model.MedicalHistory>().firstOrNull()
        }.getOrNull()

        // สถิติ (Statistics) snapshot — without this, chat could only see the static profile fields
        // above and had no way to answer questions about actual logged sleep/growth/vaccine/milestone
        // data, even though that's exactly what ข้อมูลลูก/สถิติ show in the app itself.
        val statsBlock = runCatching { buildStatsSummary(child.id) }.getOrNull().orEmpty()

        return buildString {
            append("ข้อมูลผู้ใช้และเด็ก (ใช้ข้อมูลนี้ในการวิเคราะห์และให้คำแนะนำ):\n")
            if (!relationship.isNullOrBlank()) {
                append("- ผู้ใช้: ${RelationshipLabels.thaiAddress(relationship)} (${RelationshipLabels.thaiRelationshipLabel(relationship)})\n")
            }
            append("- ชื่อเด็ก: ${child.name}\n")
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
            if (statsBlock.isNotBlank()) append(statsBlock)
        }
    }

    /** Compact real-data summary — same tables ข้อมูลลูก/สถิติ show, not fabricated. Kept to counts/
     *  percentages (not full per-record dumps) to keep this cheap on every chat turn. */
    private suspend fun buildStatsSummary(childId: String): String {
        val sleepLogs = SleepRepository().getLogs(childId).getOrDefault(emptyList())
        val growth = HealthRepository().getGrowthRecords(childId).getOrDefault(emptyList())
        val vaccines = HealthRepository().getVaccineRecords(childId).getOrDefault(emptyList())
        val milestones = ChildRepository().getMilestones(childId).getOrDefault(emptyList())
        val nutritionRepo = NutritionRepository()
        val todayFood = nutritionRepo.getFoodLogs(childId)
        val todayExercise = nutritionRepo.getExerciseLogs(childId)

        return buildString {
            append("\nสถิติล่าสุด (จากบันทึกจริงในแอป):\n")
            val weekAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7))
            val recentSleep = sleepLogs.filter { log ->
                com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)?.isAfter(weekAgo) == true
            }
            if (recentSleep.isNotEmpty()) {
                val totalMin = recentSleep.sumOf { log ->
                    val start = com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
                    val end = log.endTime?.let { com.lullabyai.app.util.TimeUtils.parseInstant(it) } ?: java.time.Instant.now()
                    if (start != null) java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
                }
                append("- การนอน 7 วันล่าสุด: ${recentSleep.size} ครั้ง รวม ${totalMin / 60} ชม. ${totalMin % 60} นาที\n")
            } else append("- การนอน: ยังไม่มีบันทึกในช่วง 7 วันล่าสุด\n")

            growth.maxByOrNull { it.recordedAt ?: "" }?.let { g ->
                append("- บันทึกการเจริญเติบโตล่าสุด: ${g.recordedAt ?: "-"}" +
                    (g.weightKg?.let { " น้ำหนัก $it กก." } ?: "") + (g.heightCm?.let { " ส่วนสูง $it ซม." } ?: "") + "\n")
            }

            if (vaccines.isNotEmpty()) {
                val done = vaccines.count { it.status == "completed" }
                append("- วัคซีน: ได้รับแล้ว $done จาก ${vaccines.size} รายการตามตาราง\n")
            }

            if (milestones.isNotEmpty()) {
                val achieved = milestones.count { it.isAchieved }
                append("- พัฒนาการ: ทำได้แล้ว $achieved จาก ${milestones.size} ทักษะที่บันทึกไว้\n")
            }

            append("- อาหารวันนี้: ${todayFood.size} รายการ" + (todayFood.sumOf { it.calories ?: 0 }.let { if (it > 0) " รวม $it แคลอรี่" else "" }) + "\n")
            append("- กิจกรรม/ออกกำลังกายวันนี้: ${todayExercise.size} รายการ" +
                (todayExercise.sumOf { it.durationMin ?: 0 }.let { if (it > 0) " รวม $it นาที" else "" }) + "\n")
        }
    }

    // ---- One-off AI analyses (no session needed) ----

    /** Dashboard insight — a 3-sentence summary of the child and their health risk. */
    suspend fun generateDashboardSummary(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปภาพรวมเกี่ยวกับเด็กและความเสี่ยงด้านสุขภาพ " +
            "ภายใน 3 ประโยคเท่านั้น ใช้ภาษาที่เป็นธรรมชาติ ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ ตอบเนื้อหาโดยตรงทันที ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ เช่น \"สวัสดี\" หรือชื่อเด็ก"
        val cacheKey = "dashboard_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "dashboard") }
    }

    /** Child Summary (ข้อมูลลูก ภาพรวม) — factual, instruction-style summary, under 7 sentences. */
    suspend fun generateChildSummary(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปข้อมูลเกี่ยวกับเด็ก ความยาวไม่เกิน 7 ประโยค " +
            "เขียนในลักษณะข้อมูลเชิงข้อเท็จจริงและคำแนะนำ ไม่ต้องใช้น้ำเสียงเหมือนมนุษย์พูดคุย " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ ตอบเนื้อหาโดยตรงทันที ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ เช่น \"สวัสดี\" หรือชื่อเด็ก"
        val cacheKey = "child_summary_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "child_summary") }
    }

    /** Child Health page (ข้อมูลลูก สุขภาพ) — health check, under 5 sentences. */
    suspend fun generateHealthAnalysis(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น ตรวจสอบและประเมินสุขภาพของเด็ก ความยาวไม่เกิน 5 ประโยค " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ ตอบเนื้อหาโดยตรงทันที ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ เช่น \"สวัสดี\" หรือชื่อเด็ก"
        val cacheKey = "health_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "health_analysis") }
    }

    /** Disease Risk page (ข้อมูลลูก ความเสี่ยงโรค) — risk summary, under 5 sentences. */
    suspend fun generateDiseaseRiskAnalysis(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nจากข้อมูลข้างต้น สรุปความเสี่ยงโรคของเด็กสำหรับเด็กวัยนี้ในประเทศไทย ความยาวไม่เกิน 5 ประโยค " +
            "ห้ามใช้ emoji และห้ามใช้สัญลักษณ์พิเศษใดๆ ตอบเนื้อหาโดยตรงทันที ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ เช่น \"สวัสดี\" หรือชื่อเด็ก"
        val cacheKey = "disease_risk_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 2048, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "disease_risk") }
    }

    /** Returns a pipe-format list of likely/at-risk diseases: name|level|percent|description per line. */
    suspend fun generateDiseaseList(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) + """

จากข้อมูลเด็กข้างต้น ประเมินโรคที่พบบ่อยหรือมีความเสี่ยงสำหรับเด็กวัยนี้ในประเทศไทย 5 รายการ
ตอบกลับในรูปแบบบรรทัดละ 1 โรค คั่นด้วยเครื่องหมาย | ตามนี้เท่านั้น:
ชื่อโรค|ระดับ|เปอร์เซ็นต์|คำอธิบายสั้นๆ
โดย "ระดับ" ใช้คำใดคำหนึ่ง: normal, attention, low, high
"เปอร์เซ็นต์" เป็นตัวเลข 0-100 (ไม่ต้องมีเครื่องหมาย %)
ห้ามมีหัวข้อหรือข้อความอื่นนอกเหนือจากนี้
""".trim()
        val cacheKey = "disease_list_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096, system = analysisSystemPrompt(isThai))
            .also { searchCache.save(cacheKey, it, "disease_list") }
    }

    /** Returns a pipe-format list of prevention tips: title|detail per line. */
    suspend fun generatePrevention(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) + """

ให้คำแนะนำการป้องกันโรคและดูแลสุขภาพสำหรับเด็กคนนี้ 3 ข้อ
ตอบกลับบรรทัดละ 1 ข้อ คั่นด้วย | ตามนี้:
หัวข้อสั้นๆ|รายละเอียดสั้นๆ
ห้ามมีข้อความอื่น
""".trim()
        val cacheKey = "prevention_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096, system = analysisSystemPrompt(isThai))
            .also { searchCache.save(cacheKey, it, "prevention") }
    }

    suspend fun generateGrowthInsight(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nวิเคราะห์การเจริญเติบโตของเด็กคนนี้จากข้อมูลข้างต้น " +
            "ให้คำแนะนำเรื่องโภชนาการและการเจริญเติบโตอย่างละเอียด ตอบครบถ้วนไม่ตัดทอน " +
            "ตอบเนื้อหาโดยตรงทันที ห้ามขึ้นต้นด้วยคำทักทายหรือคำนำใดๆ เช่น \"สวัสดี\" หรือชื่อเด็ก"
        val cacheKey = "growth_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = true, maxTokens = 4096, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "growth_insight") }
    }

    /**
     * Weekly Health Report (ความเสี่ยงโรค > ดูรายงานเชิงลึก) — a longer-form narrative explaining
     * WHY the child's current risk picture looks the way it does, grounded only in real data the
     * app has (child profile, medical history, allergies). There is no stored week-over-week risk
     * history to chart, so this stays an honest explanatory report rather than a fabricated trend.
     */
    suspend fun generateWeeklyHealthReport(child: Child, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nเขียนรายงานสุขภาพเชิงลึกประจำสัปดาห์สำหรับเด็กคนนี้ อธิบายว่าทำไมภาพรวมความเสี่ยงด้านสุขภาพในตอนนี้จึงเป็นเช่นนี้ " +
            "โดยอ้างอิงจากข้อมูลจริงข้างต้นเท่านั้น (อายุ น้ำหนัก ส่วนสูง การนอน ระดับกิจกรรม ประวัติแพ้ ประวัติทางการแพทย์) " +
            "ห้ามอ้างถึงข้อมูลย้อนหลังที่ไม่มีอยู่จริง เช่น สัปดาห์ที่แล้วหรือแนวโน้มในอดีต ให้เขียนเป็นย่อหน้า 4-6 ประโยค " +
            "ปิดท้ายด้วยข้อแนะนำที่นำไปปฏิบัติได้ 1-2 ข้อ"
        val cacheKey = "weekly_report_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 2048, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "weekly_report") }
    }

    /**
     * Sleep insight (การนอน hub AI card) — [sleepSummary] is a caller-built string of the child's
     * actual recent sleep logs (already loaded client-side), so the AI reasons only over real data
     * and doesn't invent a "regression" or trend that isn't actually there.
     */
    suspend fun generateSleepInsight(child: Child, sleepSummary: String, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nข้อมูลการนอนล่าสุดของเด็กคนนี้:\n$sleepSummary" +
            "\n\nวิเคราะห์รูปแบบการนอนของเด็กคนนี้โดยอ้างอิงจากข้อมูลข้างต้นเท่านั้น ห้ามสมมติแนวโน้มหรือปัญหาที่ไม่ปรากฏในข้อมูลจริง " +
            "หากข้อมูลไม่พอจะสรุปแนวโน้มได้ ให้บอกว่าควรบันทึกเพิ่มเติมกี่วันจึงจะวิเคราะห์ได้แม่นยำขึ้น เขียน 2-3 ประโยค"
        val cacheKey = "sleep_insight_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 1024, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "sleep_insight") }
    }

    /** Quick Log Hub tip — one short, actionable sentence grounded in [activitySummary]
     *  (caller-built from the child's actual most-recent logs, not a fabricated routine). */
    suspend fun generateQuickTip(child: Child, activitySummary: String, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nกิจกรรมล่าสุดของเด็กคนนี้:\n$activitySummary" +
            "\n\nให้คำแนะนำสั้นๆ 1 ประโยคที่ทำได้ทันที โดยอ้างอิงจากกิจกรรมล่าสุดข้างต้นเท่านั้น ห้ามสมมติเวลาหรือเหตุการณ์ที่ไม่ได้ระบุไว้"
        val cacheKey = "quick_tip_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 512, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "quick_tip") }
    }

    /** Smart Trends insights — 2-3 short sentences grounded in [trendSummary] (caller-built from
     *  real sleep/food logs over the last week). Must say data is insufficient rather than invent
     *  a trend — there's no long-term stored history to genuinely support "vs last week" claims. */
    suspend fun generateTrendInsights(child: Child, trendSummary: String, forceRefresh: Boolean = false, isThai: Boolean = true): Result<String> = runCatching {
        val prompt = buildChildContext(child) +
            "\n\nข้อมูลกิจกรรมช่วง 7 วันที่ผ่านมาของเด็กคนนี้:\n$trendSummary" +
            "\n\nสรุปแนวโน้มที่สังเกตได้จากข้อมูลข้างต้นเท่านั้น 2-3 ประโยค ห้ามสมมติตัวเลขหรือช่วงเวลาที่ไม่ปรากฏในข้อมูลจริง " +
            "หากข้อมูลไม่พอจะสรุปแนวโน้มได้ ให้บอกตรงๆ ว่าควรบันทึกเพิ่มเติมเท่าไรจึงจะวิเคราะห์ได้แม่นยำขึ้น"
        val cacheKey = "trend_insights_${child.id}_${langTag(isThai)}"
        val cached = if (!forceRefresh) searchCache.get(cacheKey) else null
        cached ?: ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 1024, system = analysisSystemPrompt(isThai), jsonMode = false)
            .also { searchCache.save(cacheKey, it, "trend_insights") }
    }

    private fun langTag(isThai: Boolean) = if (isThai) "th" else "en"

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
