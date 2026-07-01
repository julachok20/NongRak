package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.UiChatMessage
import com.lullabyai.app.data.repository.AiRepository
import com.lullabyai.app.data.repository.ChatRepository
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
    val healthAnalysis: String? = null,
    val diseaseRiskAnalysis: String? = null,
    val growthInsight: String? = null,
    val diseaseList: List<RiskDisease> = emptyList(),
    val preventionList: List<PreventionTip> = emptyList(),
    val isRefreshing: Boolean = false,
    val analysisChildId: String? = null,  // which child the AI analyses below belong to (multi-child)
    val lastProvider: String = ""   // which AI model answered the latest message (debug indicator)
)

class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()
    private val aiRepo = AiRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val welcomeMessage = UiChatMessage(
        id = "welcome",
        role = "model",
        content = "สวัสดีค่ะ! 👋\nมีอะไรให้ช่วยเกี่ยวกับลูกของคุณบ้างคะ?"
    )

    fun loadSessions() {
        viewModelScope.launch {
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
                    messages = listOf(welcomeMessage)
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
                val ui = if (msgs.isEmpty()) listOf(welcomeMessage)
                else msgs.map { UiChatMessage(it.id, it.role, it.content) }
                _uiState.value = _uiState.value.copy(messages = ui, isLoading = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, messages = listOf(welcomeMessage))
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
            repo.sendMessage(sessionId, content, child, imageBytes, imageMime)
                .onSuccess { response ->
                    val aiMsg = UiChatMessage(id = java.util.UUID.randomUUID().toString(), role = "model", content = response)
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
     * Multi-child guard: if the child changed, immediately clear ALL cached AI analyses so the
     * previous child's insights never show for the new one. Loaders below also drop stale results
     * (a slow generation that finishes after the user switched again).
     */
    private fun ensureAnalysisChild(childId: String) {
        if (_uiState.value.analysisChildId != childId) {
            _uiState.value = _uiState.value.copy(
                analysisChildId = childId,
                dashboardSummary = null, childInfoSummary = null, healthAnalysis = null,
                diseaseRiskAnalysis = null, growthInsight = null,
                diseaseList = emptyList(), preventionList = emptyList()
            )
        }
    }
    private fun isCurrent(childId: String) = _uiState.value.analysisChildId == childId

    // Dashboard insight (3-sentence child + risk summary)
    fun loadDashboardSummary(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "dashboard") {
                repo.generateDashboardSummary(child).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(dashboardSummary = text)
        }
    }

    // Child Summary page (factual, instruction-style, < 7 sentences)
    fun loadChildInfoSummary(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "child_summary") {
                repo.generateChildSummary(child).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(childInfoSummary = text)
        }
    }

    // Loads from DB cache; generates + saves only if missing.
    fun loadHealthAnalysis(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "health") {
                repo.generateHealthAnalysis(child).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(healthAnalysis = text)
        }
    }

    fun loadDiseaseRisk(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val prediction = aiRepo.getOrGenerate(child.id, "disease_risk") {
                repo.generateDiseaseRiskAnalysis(child).getOrDefault("กำลังวิเคราะห์...")
            }
            val listRaw = aiRepo.getOrGenerate(child.id, "risk_diseases") {
                repo.generateDiseaseList(child).getOrDefault("")
            }
            val prevRaw = aiRepo.getOrGenerate(child.id, "prevention") {
                repo.generatePrevention(child).getOrDefault("")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(
                diseaseRiskAnalysis = prediction,
                diseaseList = parseDiseases(listRaw),
                preventionList = parsePrevention(prevRaw)
            )
        }
    }

    fun loadGrowthInsight(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "growth") {
                repo.generateGrowthInsight(child).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(growthInsight = text)
        }
    }

    /** อัปเดตข้อมูล button: force-regenerate all cached analyses. */
    fun refreshAllAnalysis(child: Child) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            // forceRefresh = true bypasses search_cache so the AI is actually called fresh
            val prediction = aiRepo.refresh(child.id, "disease_risk") { repo.generateDiseaseRiskAnalysis(child, forceRefresh = true).getOrDefault("") }
            val listRaw = aiRepo.refresh(child.id, "risk_diseases") { repo.generateDiseaseList(child, forceRefresh = true).getOrDefault("") }
            val prevRaw = aiRepo.refresh(child.id, "prevention") { repo.generatePrevention(child, forceRefresh = true).getOrDefault("") }
            aiRepo.refresh(child.id, "health") { repo.generateHealthAnalysis(child, forceRefresh = true).getOrDefault("") }
            aiRepo.refresh(child.id, "growth") { repo.generateGrowthInsight(child, forceRefresh = true).getOrDefault("") }
            val dash = aiRepo.refresh(child.id, "dashboard") { repo.generateDashboardSummary(child, forceRefresh = true).getOrDefault("") }
            val childSum = aiRepo.refresh(child.id, "child_summary") { repo.generateChildSummary(child, forceRefresh = true).getOrDefault("") }
            val parsedDiseases = parseDiseases(listRaw)
            // Only apply if the user hasn't switched to a different child mid-refresh.
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(
                dashboardSummary = dash, childInfoSummary = childSum,
                diseaseRiskAnalysis = prediction,
                diseaseList = parsedDiseases,
                preventionList = parsePrevention(prevRaw),
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
