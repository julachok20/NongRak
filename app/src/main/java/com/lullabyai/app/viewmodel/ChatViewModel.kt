package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.UiChatMessage
import com.lullabyai.app.data.repository.AiRepository
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ChatRepository
import com.lullabyai.app.util.RelationshipLabels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RiskDisease(val name: String, val level: String, val percent: Int, val description: String)
data class PreventionTip(val title: String, val detail: String)

data class ChatUiState(
    val sessions: List<ChatSession> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<UiChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val dashboardSummary: String? = null,
    val childInfoSummary: String? = null,
    val childInfoCardSummary: String? = null,
    val healthAnalysis: String? = null,
    val diseaseRiskAnalysis: String? = null,
    val growthInsight: String? = null,
    val weeklyHealthReport: String? = null,
    val sleepInsight: String? = null,
    val quickTip: String? = null,
    val trendInsights: String? = null,
    val nutritionInsight: String? = null,
    val sleepActivityInsight: String? = null,
    val eliminationInsight: String? = null,
    val monthlyReport: String? = null,
    val diseaseList: List<RiskDisease> = emptyList(),
    val preventionList: List<PreventionTip> = emptyList(),
    val isRefreshing: Boolean = false,
    val analysisChildId: String? = null,  // which child the AI analyses below belong to (multi-child)
    val parentRelationship: String? = null, // profiles.relationship — how the chatbot addresses the user
    val lastProvider: String = ""   // which AI model answered the latest message (debug indicator)
)

class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()
    private val aiRepo = AiRepository()
    private val authRepo = AuthRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private fun welcomeMessage(relationship: String?) = UiChatMessage(
        id = "welcome",
        role = "model",
        content = RelationshipLabels.chatWelcome(relationship)
    )

    fun loadSessions() {
        viewModelScope.launch {
            val relationship = authRepo.getProfile().getOrNull()?.relationship
            if (relationship != null) {
                _uiState.value = _uiState.value.copy(parentRelationship = relationship)
            }
            repo.getSessions().onSuccess { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
                // Auto-open most recent, or create a new one if none
                if (_uiState.value.currentSessionId == null) {
                    if (sessions.isNotEmpty()) openSession(sessions.first().id)
                    else newChat()
                }
            }
        }
    }

    fun newChat() {
        viewModelScope.launch {
            repo.createSession().onSuccess { session ->
                val sessions = listOf(session) + _uiState.value.sessions
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    currentSessionId = session.id,
                    messages = listOf(welcomeMessage(_uiState.value.parentRelationship))
                )
            }
        }
    }

    /** Rename a chat session (updates the title shown in the history drawer). */
    fun renameSession(sessionId: String, newTitle: String) {
        val title = newTitle.trim().ifBlank { return }
        viewModelScope.launch {
            repo.renameSession(sessionId, title).onSuccess {
                _uiState.value = _uiState.value.copy(
                    sessions = _uiState.value.sessions.map { if (it.id == sessionId) it.copy(title = title) else it }
                )
            }
        }
    }

    fun openSession(sessionId: String) {
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId, isLoading = true)
        viewModelScope.launch {
            repo.getMessages(sessionId).onSuccess { msgs ->
                val welcome = welcomeMessage(_uiState.value.parentRelationship)
                val ui = if (msgs.isEmpty()) listOf(welcome)
                else msgs.map { UiChatMessage(it.id, it.role, it.content) }
                _uiState.value = _uiState.value.copy(messages = ui, isLoading = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    messages = listOf(welcomeMessage(_uiState.value.parentRelationship))
                )
            }
        }
    }

    fun deleteChat(sessionId: String) {
        viewModelScope.launch {
            repo.deleteSession(sessionId).onSuccess {
                val remaining = _uiState.value.sessions.filterNot { it.id == sessionId }
                _uiState.value = _uiState.value.copy(sessions = remaining)
                if (_uiState.value.currentSessionId == sessionId) {
                    if (remaining.isNotEmpty()) openSession(remaining.first().id)
                    else newChat()
                }
            }
        }
    }

    fun sendMessage(content: String, child: Child?, imageBytes: ByteArray? = null, imageUri: String? = null, imageMime: String? = null) {
        val sessionId = _uiState.value.currentSessionId ?: return
        val userMsg = UiChatMessage(id = java.util.UUID.randomUUID().toString(), role = "user", content = content, imageUri = imageUri)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filterNot { it.id == "welcome" } + userMsg,
            isLoading = true
        )
        viewModelScope.launch {
            repo.sendMessage(sessionId, content, child, imageBytes, imageMime, _uiState.value.parentRelationship)
                .onSuccess { response ->
                    // The AI may append an <AGENT_ACTION> or <NEED_INFO> block (see
                    // RelationshipLabels.chatSystemPrompt) — strip it into structured data for the
                    // confirmation card / info popup; the visible bubble stays plain text.
                    val (displayText, action, needInfo) = com.lullabyai.app.util.parseAgentAction(response)
                    val aiMsg = UiChatMessage(
                        id = java.util.UUID.randomUUID().toString(), role = "model", content = displayText,
                        agentAction = action, needInfo = needInfo
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + aiMsg,
                        isLoading = false,
                        lastProvider = com.lullabyai.app.data.remote.ClaudeClient.lastProvider
                    )
                    // Refresh session list (title/updated order may change)
                    repo.getSessions().onSuccess { _uiState.value = _uiState.value.copy(sessions = it) }
                }
                .onFailure {
                    val errMsg = UiChatMessage(id = java.util.UUID.randomUUID().toString(), role = "model", content = "ขออภัย เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง")
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + errMsg,
                        isLoading = false
                    )
                }
        }
    }

    /**
     * Executes the [messageId] message's pending AgentAction — the ONLY path that actually writes
     * data on the AI's behalf, and only ever runs when the user taps Confirm on the chat card.
     * Scoped to additive/low-risk repository calls: logging sleep/food/exercise/health/milk/waste,
     * updating growth numbers, marking the next pending medicine dose taken, marking a vaccine
     * completed, adding an allergy or chronic condition, navigating to a screen, and deleting the
     * single most-recent log of a type.
     * Not supported yet (out of scope): editing an arbitrary existing record, adding/changing a
     * medication SCHEDULE (only marking an existing dose taken), or account/settings changes.
     */
    fun confirmAgentAction(messageId: String, childId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        val action = msg.agentAction ?: return
        viewModelScope.launch {
            val ok = runCatching { dispatchAgentAction(action, childId) }
                .onFailure { android.util.Log.e("AgentAction", "type=${action.type} params=${action.params} confirmText='${action.confirmText}' threw", it) }
                .getOrDefault(false)
            if (!ok) android.util.Log.w("AgentAction", "type=${action.type} params=${action.params} confirmText='${action.confirmText}' returned false (unmatched type or missing required param)")
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == messageId) it.copy(actionStatus = if (ok) "confirmed" else "failed") else it
                }
            )
        }
    }

    fun cancelAgentAction(messageId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == messageId) it.copy(actionStatus = "cancelled") else it }
        )
    }

    /** Popup "Submit" — sends the answer as a normal chat message so the AI picks up straight from
     *  where it left off (and can even log it via AgentAction itself, e.g. weight -> update_growth). */
    fun answerNeedInfo(messageId: String, answer: String, child: Child?) {
        dismissNeedInfo(messageId)
        if (answer.isNotBlank()) sendMessage(answer, child)
    }

    /** Popup "Skip" — no cooldown by design (see NeedInfoRequest); just hides this instance. */
    fun dismissNeedInfo(messageId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == messageId) it.copy(needInfoAnswered = true) else it }
        )
    }

    // Models don't always reproduce a literal string exactly — "Log Sleep" / "log-sleep" instead
    // of "log_sleep" would otherwise silently fall through to the `else -> false` branch with no
    // trace of why.
    private fun normalizeType(type: String) = type.trim().lowercase().replace(Regex("[ -]"), "_")

    // Case-insensitivity alone wasn't enough: even after fixing the type-matching (confirmed via
    // Supabase's API logs — no writes were ever attempted), fresh test messages STILL produced zero
    // writes. The remaining gap is param *key* naming — the system prompt asks for camelCase
    // ("startTime") but the model reliably drifts to snake_case ("start_time") regardless, and every
    // required-param lookup below does `?: return false`, which bails out before any repository call
    // even runs. Stripping delimiters (underscore/hyphen/space) from both sides before comparing
    // makes the lookup tolerant of whichever convention the model actually used.
    private fun canonicalKey(k: String) = k.lowercase().filter { it.isLetterOrDigit() }
    private fun Map<String, String>.getCi(key: String): String? {
        val target = canonicalKey(key)
        return entries.firstOrNull { canonicalKey(it.key) == target }?.value
    }
    /** Same lookup, but logs exactly which required param was missing and what keys were actually
     *  present — so if this still silently fails, the next check tells us precisely why instead of
     *  another blind guess. */
    private fun Map<String, String>.requireCi(key: String, type: String): String? =
        getCi(key) ?: run { android.util.Log.w("AgentAction", "type='$type' missing required param '$key' — had keys: ${keys}"); null }

    // Checked via Supabase's own API logs: zero POST attempts ever reached sleep_logs/food_logs/
    // exercise_logs/health_records/growth_records, meaning the old exact `when("log_sleep" -> ...)`
    // matching fell through to `else -> false` before any repository call — the model just isn't
    // reproducing the literal type string reliably, no matter how precisely the prompt spells it
    // out. Keyword matching on the normalized type is far more forgiving of whatever wording an
    // LLM actually free-generates. "delete"/"remove" is checked first so e.g. "delete_sleep_log"
    // (contains both "delete" and "sleep") routes to deletion, not to logging a new sleep entry.
    private suspend fun dispatchAgentAction(action: com.lullabyai.app.data.model.AgentAction, childId: String): Boolean {
        val p = action.params
        val t = normalizeType(action.type)
        return when {
            "delete" in t || "remove" in t -> {
                val logType = p.getCi("logType")?.lowercase() ?: when {
                    "sleep" in t -> "sleep"
                    "food" in t || "meal" in t -> "food"
                    "exercise" in t || "activity" in t -> "exercise"
                    else -> null
                }
                when (logType) {
                    "sleep" -> com.lullabyai.app.data.repository.SleepRepository().getLogs(childId).getOrNull()
                        ?.maxByOrNull { it.startTime }?.let { com.lullabyai.app.data.repository.SleepRepository().deleteLog(it.id).isSuccess } ?: false
                    "food" -> com.lullabyai.app.data.repository.NutritionRepository().getFoodLogs(childId)
                        .maxByOrNull { it.createdAt ?: "" }?.let { com.lullabyai.app.data.repository.NutritionRepository().deleteFood(it.id).isSuccess } ?: false
                    "exercise" -> com.lullabyai.app.data.repository.NutritionRepository().getExerciseLogs(childId)
                        .maxByOrNull { it.createdAt ?: "" }?.let { com.lullabyai.app.data.repository.NutritionRepository().deleteExercise(it.id).isSuccess } ?: false
                    else -> false
                }
            }
            "sleep" in t -> {
                val today = java.time.LocalDate.now().toString()
                val start = p.requireCi("startTime", t) ?: return false
                val end = p.requireCi("endTime", t) ?: return false
                val endDate = if (end < start) java.time.LocalDate.now().plusDays(1).toString() else today
                com.lullabyai.app.data.repository.SleepRepository()
                    .addManualLog(childId, p.getCi("sleepType") ?: "main", "${today}T$start:00", "${endDate}T$end:00")
                    .isSuccess
            }
            "growth" in t || "weight" in t || "height" in t -> {
                val weight = p.getCi("weightKg")?.toDoubleOrNull()
                val height = p.getCi("heightCm")?.toDoubleOrNull()
                if (weight == null && height == null) {
                    android.util.Log.w("AgentAction", "type='$t' had neither weightKg nor heightCm — keys: ${p.keys}")
                    return false
                }
                com.lullabyai.app.data.repository.HealthRepository().addGrowthRecord(
                    com.lullabyai.app.data.model.GrowthRecord(
                        childId = childId, weightKg = weight, heightCm = height,
                        recordedAt = java.time.Instant.now().toString()
                    )
                ).isSuccess
            }
            "food" in t || "meal" in t -> {
                com.lullabyai.app.data.repository.NutritionRepository()
                    .addFood(childId, p.getCi("mealType") ?: "other", p.requireCi("itemName", t) ?: action.confirmText.ifBlank { return false }, p.getCi("calories")?.toIntOrNull())
                    .isSuccess
            }
            "exercise" in t || "activity" in t -> {
                com.lullabyai.app.data.repository.NutritionRepository()
                    .addExercise(childId, p.requireCi("activityName", t) ?: action.confirmText.ifBlank { return false }, p.getCi("durationMin")?.toIntOrNull(), null)
                    .isSuccess
            }
            "medicine" in t || "medication" in t || "dose" in t -> {
                // No specific dose ID from the model — take the soonest-due pending dose, same
                // "best-guess most-relevant record" approach as delete_most_recent above.
                val doses = com.lullabyai.app.data.repository.MedicationRepository().getDoses(childId)
                    .getOrNull()?.filter { it.status == "pending" } ?: emptyList()
                val target = doses.minByOrNull { it.scheduledAt } ?: return false
                com.lullabyai.app.data.repository.MedicationRepository().markDoseTaken(target.id).isSuccess
            }
            "vaccine" in t -> {
                val name = p.requireCi("vaccineName", t) ?: action.confirmText.ifBlank { return false }
                val records = com.lullabyai.app.data.repository.HealthRepository().getVaccineRecords(childId).getOrNull() ?: emptyList()
                val target = records.filter { it.status != "completed" }
                    .firstOrNull { it.vaccineName.contains(name, ignoreCase = true) || name.contains(it.vaccineName, ignoreCase = true) }
                    ?: return false
                com.lullabyai.app.data.repository.HealthRepository()
                    .updateVaccineStatus(target.id, "completed", java.time.LocalDate.now().toString()).isSuccess
            }
            "milk" in t || "formula" in t -> {
                val volume = p.getCi("volumeMl")?.toIntOrNull() ?: return false
                com.lullabyai.app.data.repository.NutritionRepository()
                    .addMilkLog(childId, p.getCi("milkType") ?: "breast", volume, p.getCi("side"), p.getCi("notes")).isSuccess
            }
            "waste" in t || "diaper" in t || "poop" in t || "pee" in t -> {
                com.lullabyai.app.data.repository.HealthRepository().addWasteRecord(
                    childId, p.getCi("wasteType") ?: "poop", p.getCi("consistency"), p.getCi("color"),
                    p.getCi("amount") ?: "ปกติ", p.getCi("notes")
                ).isSuccess
            }
            "allergy" in t -> {
                com.lullabyai.app.data.repository.HealthRepository().addAllergy(
                    childId, p.requireCi("name", t) ?: action.confirmText.ifBlank { return false },
                    p.getCi("allergyType") ?: "other", p.getCi("severity") ?: "mild", p.getCi("symptoms")
                ).isSuccess
            }
            "chronic" in t || "condition" in t -> {
                com.lullabyai.app.data.repository.HealthRepository().addChronicCondition(
                    childId, p.requireCi("name", t) ?: action.confirmText.ifBlank { return false }, p.getCi("notes")
                ).isSuccess
            }
            // Navigation is UI-only — actually fired in AIChatScreen's confirm handler (it needs
            // NavController, which this ViewModel deliberately doesn't hold). This branch only marks
            // the card "confirmed" for the same tap.
            "navigate" in t -> true
            "health" in t || "symptom" in t || "record" in t || "log" in t -> {
                // Observed via logcat: the model sometimes emits params={} entirely for log_health
                // (confirmed live — 3 identical failures) despite the type matching correctly.
                // confirmText is the one field it reliably fills in (it's shown directly in the
                // chat UI), so it's a much safer fallback than failing the whole action.
                com.lullabyai.app.data.repository.HealthRepository().addHealthRecord(
                    com.lullabyai.app.data.model.HealthRecord(
                        childId = childId, eventType = "log", title = p.requireCi("title", t) ?: action.confirmText.ifBlank { return false },
                        description = p.getCi("description"), severity = "mild",
                        temperature = p.getCi("temperature")?.toDoubleOrNull(),
                        recordedAt = java.time.LocalDate.now().toString()
                    )
                ).isSuccess
            }
            else -> {
                android.util.Log.w("AgentAction", "no keyword match for normalized type '$t' (raw: '${action.type}')")
                false
            }
        }
    }

    /**
     * Multi-child guard: if the child changed, immediately clear ALL cached AI analyses so the
     * previous child's insights never show for the new one. Loaders below also drop stale results
     * (a slow generation that finishes after the user switched again).
     */
    private fun ensureAnalysisChild(childId: String) {
        if (_uiState.value.analysisChildId != childId) {
            _uiState.value = _uiState.value.copy(
                analysisChildId = childId,
                dashboardSummary = null, childInfoSummary = null, healthAnalysis = null,
                diseaseRiskAnalysis = null, growthInsight = null, weeklyHealthReport = null, sleepInsight = null,
                quickTip = null, trendInsights = null, nutritionInsight = null, sleepActivityInsight = null, eliminationInsight = null, monthlyReport = null,
                diseaseList = emptyList(), preventionList = emptyList()
            )
        }
    }
    private fun isCurrent(childId: String) = _uiState.value.analysisChildId == childId

    // The outer aiRepo cache (Supabase ai_analysis table) is keyed only by (childId, type) — without
    // a language suffix, switching the app's language would still return yesterday's cached Thai
    // text on an English-selected screen (or vice versa), same class of bug as the sleep_insight
    // permanent-cache issue fixed earlier. langTag() keeps the two languages in separate cache rows.
    private fun langTag(isThai: Boolean) = if (isThai) "th" else "en"

    // Dashboard insight (3-sentence child + risk summary)
    fun loadDashboardSummary(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "dashboard_${langTag(isThai)}") {
                repo.generateDashboardSummary(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(dashboardSummary = text)
        }
    }

    // Child Summary page (factual, instruction-style, < 7 sentences)
    fun loadChildInfoSummary(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "child_summary_${langTag(isThai)}") {
                repo.generateChildSummary(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(childInfoSummary = text)
        }
    }

    // Short AI card on ข้อมูลลูก ภาพรวม (Child Info overview) — 1-2 sentences, separate from the
    // fuller childInfoSummary used by the health-check and monthly-summary pages.
    fun loadChildInfoCardSummary(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "child_summary_card_${langTag(isThai)}") {
                repo.generateChildInfoCardSummary(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(childInfoCardSummary = text)
        }
    }

    // Loads from DB cache; generates + saves only if missing.
    fun loadHealthAnalysis(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "health_${langTag(isThai)}") {
                repo.generateHealthAnalysis(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(healthAnalysis = text)
        }
    }

    fun loadDiseaseRisk(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val prediction = aiRepo.getOrGenerate(child.id, "disease_risk_${langTag(isThai)}") {
                repo.generateDiseaseRiskAnalysis(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            val listRaw = aiRepo.getOrGenerate(child.id, "risk_diseases_${langTag(isThai)}") {
                repo.generateDiseaseList(child, isThai = isThai).getOrDefault("")
            }
            val prevRaw = aiRepo.getOrGenerate(child.id, "prevention_${langTag(isThai)}") {
                repo.generatePrevention(child, isThai = isThai).getOrDefault("")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(
                diseaseRiskAnalysis = prediction,
                diseaseList = parseDiseases(listRaw),
                preventionList = parsePrevention(prevRaw)
            )
        }
    }

    fun loadGrowthInsight(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "growth_${langTag(isThai)}") {
                repo.generateGrowthInsight(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(growthInsight = text)
        }
    }

    fun loadWeeklyHealthReport(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "weekly_report_${langTag(isThai)}") {
                repo.generateWeeklyHealthReport(child, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(weeklyHealthReport = text)
        }
    }

    /** [logs] should be the caller's already-loaded sleep logs (ChildViewModel.uiState.sleepLogs) —
     *  built into a plain-text summary here so ChatRepository doesn't need to know the SleepLog model. */
    fun loadSleepInsight(child: Child, logs: List<com.lullabyai.app.data.model.SleepLog>, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        val recent = logs.filter { it.endTime != null }.sortedByDescending { it.startTime }.take(14)
        val summary = if (recent.isEmpty()) "ยังไม่มีบันทึกการนอนที่เสร็จสมบูรณ์" else recent.joinToString("\n") { log ->
            val start = com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
            val end = log.endTime?.let { com.lullabyai.app.util.TimeUtils.parseInstant(it) }
            val minutes = if (start != null && end != null) java.time.Duration.between(start, end).toMinutes() else null
            "${if (log.sleepType == "nap") "งีบ" else "นอนหลัก"} " +
                (com.lullabyai.app.util.TimeUtils.localTime(log.startTime) ?: "") +
                (minutes?.let { " (${it / 60}ชม.${it % 60}นาที)" } ?: "")
        }
        viewModelScope.launch {
            // Sleep logs change many times a day (unlike growth/health), so this always
            // regenerates from the current logs rather than returning a stale cached row —
            // getOrGenerate would otherwise keep returning whatever was first generated
            // (often when there was little/no data yet) forever, since nothing else refreshes it.
            val text = aiRepo.refresh(child.id, "sleep_insight_${langTag(isThai)}") {
                repo.generateSleepInsight(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(sleepInsight = text)
        }
    }

    /** [activitySummary] is a caller-built plain-text summary of the child's most recent logs
     *  across sleep/food/health/development — built by the screen since it already merges those
     *  for display, so ChatViewModel doesn't need to depend on every log model. */
    fun loadQuickTip(child: Child, activitySummary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "quick_tip_${langTag(isThai)}") {
                repo.generateQuickTip(child, activitySummary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(quickTip = text)
        }
    }

    /** [trendSummary] is a caller-built plain-text summary of the last 7 days of sleep/food logs. */
    fun loadTrendInsights(child: Child, trendSummary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "trend_insights_${langTag(isThai)}") {
                repo.generateTrendInsights(child, trendSummary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(trendInsights = text)
        }
    }

    /** [summary] is a caller-built plain-text summary of the last 7 days of food/milk logs. */
    fun loadNutritionInsight(child: Child, summary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "nutrition_insight_${langTag(isThai)}") {
                repo.generateNutritionInsight(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(nutritionInsight = text)
        }
    }

    /** [summary] is a caller-built plain-text summary of the last 7 days of sleep/exercise logs. */
    fun loadSleepActivityInsight(child: Child, summary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "sleep_activity_insight_${langTag(isThai)}") {
                repo.generateSleepActivityInsight(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(sleepActivityInsight = text)
        }
    }

    /** [summary] is a caller-built plain-text summary of recent waste records + illness history. */
    fun loadEliminationInsight(child: Child, summary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "elimination_insight_${langTag(isThai)}") {
                repo.generateEliminationInsight(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(eliminationInsight = text)
        }
    }

    /** [summary] is a caller-built plain-text summary of real 30-day aggregates across sleep,
     *  nutrition, activity, development, and elimination logs. */
    fun loadMonthlyReport(child: Child, summary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "monthly_report_${langTag(isThai)}") {
                repo.generateMonthlyReport(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(monthlyReport = text)
        }
    }

    /** อัปเดตข้อมูล button: force-regenerate all cached analyses. */
    fun refreshAllAnalysis(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val tag = langTag(isThai)
            // forceRefresh = true bypasses search_cache so the AI is actually called fresh
            val prediction = aiRepo.refresh(child.id, "disease_risk_$tag") { repo.generateDiseaseRiskAnalysis(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val listRaw = aiRepo.refresh(child.id, "risk_diseases_$tag") { repo.generateDiseaseList(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val prevRaw = aiRepo.refresh(child.id, "prevention_$tag") { repo.generatePrevention(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            aiRepo.refresh(child.id, "health_$tag") { repo.generateHealthAnalysis(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            aiRepo.refresh(child.id, "growth_$tag") { repo.generateGrowthInsight(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val dash = aiRepo.refresh(child.id, "dashboard_$tag") { repo.generateDashboardSummary(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val childSum = aiRepo.refresh(child.id, "child_summary_$tag") { repo.generateChildSummary(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val weeklyReport = aiRepo.refresh(child.id, "weekly_report_$tag") { repo.generateWeeklyHealthReport(child, forceRefresh = true, isThai = isThai).getOrDefault("") }
            val parsedDiseases = parseDiseases(listRaw)
            // Only apply if the user hasn't switched to a different child mid-refresh.
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(
                dashboardSummary = dash, childInfoSummary = childSum,
                diseaseRiskAnalysis = prediction,
                diseaseList = parsedDiseases,
                preventionList = parsePrevention(prevRaw),
                weeklyHealthReport = weeklyReport,
                isRefreshing = false
            ) else _uiState.value = _uiState.value.copy(isRefreshing = false)
            // Notifications: normal on refresh, warning if a high-risk disease is found
            val notif = com.lullabyai.app.data.repository.NotificationRepository()
            notif.notify(
                com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                "อัปเดตผลวิเคราะห์ AI แล้ว",
                "ผลวิเคราะห์สุขภาพและความเสี่ยงโรคล่าสุดพร้อมแล้ว"
            )
            val high = parsedDiseases.filter { it.level == "high" || it.level == "attention" || it.percent >= 50 }
            if (high.isNotEmpty()) {
                notif.notify(
                    com.lullabyai.app.data.repository.NotifCategory.WARNING,
                    "พบความเสี่ยงที่ควรเฝ้าระวัง",
                    high.joinToString(", ") { it.name }
                )
            }
        }
    }

    private fun parseDiseases(raw: String): List<RiskDisease> =
        raw.split("\n").mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size >= 4) {
                val pct = parts[2].filter { it.isDigit() }.toIntOrNull() ?: 0
                RiskDisease(parts[0], parts[1].lowercase(), pct, parts[3])
            } else null
        }

    private fun parsePrevention(raw: String): List<PreventionTip> =
        raw.split("\n").mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size >= 2 && parts[0].isNotBlank()) PreventionTip(parts[0], parts[1]) else null
        }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /** Wipe all chat state when the user signs out so sessions never bleed into the next account. */
    fun clearForSignOut() { _uiState.value = ChatUiState() }
}
