package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChatMode
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChatSession
import com.lullabyai.app.data.model.UiChatMessage
import com.lullabyai.app.data.repository.AiRepository
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ChatRepository
import com.lullabyai.app.util.ChildMention
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
    /**
     * The answer while it is still arriving from Spark, tags held back. Null when nothing is
     * streaming. Kept out of [messages] so the call screen only ever speaks finished replies.
     */
    val streamingText: String? = null,
    val error: String? = null,
    /** Set for one feed, so the companion's "อิ่มแล้ว" banner shows once and then clears. */
    val bondFeed: com.lullabyai.app.data.repository.BondFeed? = null,
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
    val wasteInsight: String? = null,
    val monthlyReport: String? = null,
    val diseaseList: List<RiskDisease> = emptyList(),
    val preventionList: List<PreventionTip> = emptyList(),
    val isRefreshing: Boolean = false,
    val analysisChildId: String? = null,  // which child the AI analyses below belong to (multi-child)
    val parentRelationship: String? = null, // profiles.relationship — how the chatbot addresses the user
    /** profiles.full_name, for the one greeting. Null on accounts that never filled it in. */
    val parentName: String? = null,
    val lastProvider: String = "",   // which AI model answered the latest message (debug indicator)
    val mode: ChatMode = ChatMode.NORMAL,  // persona/speech style: normal, เล่านิทาน, คุยกับเด็ก
    /** Mirrors the Auto Record setting; the screen pushes it in so the ViewModel needs no Context. */
    val autoRecord: Boolean = false
)

class ChatViewModel : ViewModel() {
    private val repo = ChatRepository()
    private val aiRepo = AiRepository()
    private val authRepo = AuthRepository()
    /** Last family roster sent with a chat turn — used by need-info follow-ups. */
    private var lastFamily: List<Child> = emptyList()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    private fun welcomeMessage(
        relationship: String?,
        isThai: Boolean = true,
        name: String? = null
    ) = UiChatMessage(
        id = "welcome",
        role = "model",
        content = RelationshipLabels.chatWelcome(relationship, isThai, name)
    )

    fun loadSessions(isThai: Boolean = true) {
        viewModelScope.launch {
            val profile = authRepo.getProfile().getOrNull()
            val relationship = profile?.relationship
            // Prefer the real name; username is a fallback, and neither being set is normal.
            val name = profile?.fullName?.takeIf { it.isNotBlank() }
                ?: profile?.username?.takeIf { it.isNotBlank() }
            if (relationship != null || name != null) {
                _uiState.value = _uiState.value.copy(
                    parentRelationship = relationship ?: _uiState.value.parentRelationship,
                    parentName = name ?: _uiState.value.parentName
                )
            }
            repo.getSessions().onSuccess { sessions ->
                _uiState.value = _uiState.value.copy(sessions = sessions)
                // Auto-open most recent, or create a new one if none
                if (_uiState.value.currentSessionId == null) {
                    if (sessions.isNotEmpty()) openSession(sessions.first().id)
                    else newChat(isThai)
                }
            }
        }
    }

    fun newChat(isThai: Boolean = true) {
        viewModelScope.launch {
            repo.createSession(if (isThai) "แชทใหม่" else "New Chat").onSuccess { session ->
                val sessions = listOf(session) + _uiState.value.sessions
                _uiState.value = _uiState.value.copy(
                    sessions = sessions,
                    currentSessionId = session.id,
                    messages = listOf(welcomeMessage(_uiState.value.parentRelationship, isThai, _uiState.value.parentName))
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

    fun openSession(sessionId: String, isThai: Boolean = true) {
        _uiState.value = _uiState.value.copy(currentSessionId = sessionId, isLoading = true)
        viewModelScope.launch {
            repo.getMessages(sessionId).onSuccess { msgs ->
                val welcome = welcomeMessage(_uiState.value.parentRelationship, isThai, _uiState.value.parentName)
                val ui = if (msgs.isEmpty()) listOf(welcome)
                else msgs.map { UiChatMessage(it.id, it.role, it.content, medical = it.medical) }
                _uiState.value = _uiState.value.copy(messages = ui, isLoading = false)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    messages = listOf(welcomeMessage(_uiState.value.parentRelationship, isThai, _uiState.value.parentName))
                )
            }
        }
    }

    fun deleteChat(sessionId: String, isThai: Boolean = true) {
        viewModelScope.launch {
            repo.deleteSession(sessionId).onSuccess {
                val remaining = _uiState.value.sessions.filterNot { it.id == sessionId }
                _uiState.value = _uiState.value.copy(sessions = remaining)
                if (_uiState.value.currentSessionId == sessionId) {
                    if (remaining.isNotEmpty()) openSession(remaining.first().id, isThai)
                    else newChat(isThai)
                }
            }
        }
    }

    /** Story / talk-to-child mode. Changes the persona sent with each message (see [ChatMode]) and
     *  the TTS speech style the chat + call screens use. */
    fun setMode(mode: ChatMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    /**
     * The chosen companion's voice, built in the UI layer from its MascotId.
     *
     * Held here rather than passed at every call site: the screen sends messages from six places
     * (the composer, story buttons, quick actions), and a persona threaded through each would go
     * stale the first time someone added a seventh.
     */
    private var activePersona: String? = null

    /** Which companion is on screen, so feeding the child can feed the right one. */
    private var activeMascot: String? = "nongrak"

    fun setPersona(persona: String?) {
        activePersona = persona
    }

    fun setCompanion(mascotKey: String, persona: String?) {
        activeMascot = mascotKey
        activePersona = persona
    }

    /** Cleared once the banner has been shown, so it appears for one feed and not on every recompose. */
    fun clearBondFeed() {
        _uiState.value = _uiState.value.copy(bondFeed = null)
    }

    /**
     * Feeding the child feeds the companion.
     *
     * Failure is swallowed on purpose: the meal is the thing that mattered and it is already
     * saved. A bond that could fail a care record would be a toy breaking a tool.
     */
    private suspend fun feedCompanion(
        xp: Int = 10,
        /** Talking builds the bond quietly; only a meal is worth interrupting the screen for. */
        announce: Boolean = true
    ) {
        val mascot = activeMascot ?: return
        com.lullabyai.app.data.repository.MascotBondRepository().feed(mascot, xp)
            .onSuccess { if (it.fed && announce) _uiState.value = _uiState.value.copy(bondFeed = it) }
    }

    /** Empty unless the reply will be spoken aloud — normal chat sends exactly what it always did. */
    private fun spokenPrefix(spoken: Boolean, isThai: Boolean): String {
        if (!spoken) return ""
        return if (isThai)
            "[คำสั่งการตอบ: ข้อความนี้มาจากการโทรคุยด้วยเสียง " +
                "คุณยังเป็นผู้ช่วยที่ช่วยตรวจสอบข้อมูลและบันทึกข้อมูลของลูกเหมือนเดิมทุกอย่าง " +
                "ถ้าผู้ใช้เล่าหรือขอให้บันทึกอะไร ให้สร้างบล็อก AGENT_ACTION ตามปกติเสมอ " +
                "ความสั้นนี้ใช้กับ 'คำที่พูดออกเสียง' เท่านั้น ไม่ได้ยกเลิกความสามารถใด ๆ " +
                "ส่วนที่พูด: สั้นมาก 1-2 ประโยค คุยแบบเพื่อนคุยกับเพื่อน ใช้คำพูดธรรมดา " +
                "ไม่ต้องมีหัวข้อหรือรายการยาว ๆ " +
                "ถ้าเรื่องนั้นต้องอธิบายยาว ให้ตอบใจความสำคัญก่อนแล้วถามกลับสั้น ๆ ว่าอยากฟังต่อไหม] "
        else
            "[Reply instruction: this came from a voice call. You are still the assistant that " +
                "checks and records the child's information — if the parent reports or asks to " +
                "record anything, still emit the AGENT_ACTION block exactly as usual. This " +
                "brevity applies ONLY to the words that get spoken; it removes no capability. " +
                "Spoken part: at most 1-2 short sentences, one friend talking to another, plain " +
                "words, no headings or long lists. If it genuinely needs a long answer, give the " +
                "key point and ask briefly whether they want the rest.] "
    }

    /** Empty unless the account has several children and this message named none of them. */
    private fun ambiguousChildPrefix(ambiguous: Boolean, isThai: Boolean): String {
        if (!ambiguous) return ""
        return if (isThai)
            "[หมายเหตุ: บัญชีนี้มีลูกมากกว่าหนึ่งคน และข้อความนี้ไม่ได้ระบุชื่อลูก " +
                "ถ้าคำตอบขึ้นอยู่กับเด็กคนใดคนหนึ่ง ให้ถามสั้น ๆ ก่อนว่าหมายถึงคนไหน " +
                "ห้ามตอบเจาะจงเด็กคนใดคนหนึ่งราวกับว่ารู้อยู่แล้ว " +
                "ถ้าเป็นคำถามทั่วไปที่ไม่ขึ้นกับตัวเด็ก ก็ตอบได้เลยตามปกติ] "
        else
            "[Note: this account has more than one child and the message named none of them. " +
                "If the answer depends on a specific child, ask briefly which one first, and do " +
                "not answer about a particular child as though it were obvious. If the question " +
                "does not depend on a specific child, just answer normally.] "
    }

    fun sendMessage(
        content: String,
        child: Child?,
        imageBytes: ByteArray? = null,
        imageUri: String? = null,
        imageMime: String? = null,
        persona: String? = null,
        isThai: Boolean = true,
        /**
         * This message came from a voice call, so the reply is going to be SPOKEN.
         *
         * A paragraph that reads well on screen is unbearable read aloud: the parent waits through
         * it, cannot skim it, and has usually stopped listening by sentence three. Same mechanism
         * as [ChatMode.promptPrefix] — an instruction on the outgoing text, since the system prompt
         * is fixed in ChatRepository and the bubble keeps the parent's raw wording.
         */
        spoken: Boolean = false,
        /**
         * Several children on the account and this message named none of them.
         *
         * The selected child still goes along as context, but the reply must not state a
         * child-specific answer as if the right one were obvious — with three children a
         * confident "the next vaccine is..." about the selected one is wrong two times in three.
         */
        childAmbiguous: Boolean = false,
        /** The parent's voice as a WAV, sent instead of a transcript. See ChatRepository. */
        audioBytes: ByteArray? = null,
        audioMime: String? = null,
        /** Every child on the account. Empty = [child] alone. */
        family: List<Child> = emptyList()
    ) {
        val sessionId = _uiState.value.currentSessionId ?: return
        val roster = family.ifEmpty { listOfNotNull(child) }
        lastFamily = roster
        val targetChild = ChildMention.resolve(content, roster, fallback = child) ?: child
        val bubbleText = content.ifBlank {
            if (audioBytes != null) (if (isThai) "[ข้อความเสียง]" else "[Voice message]") else content
        }
        val userMsg = UiChatMessage(id = java.util.UUID.randomUUID().toString(), role = "user", content = bubbleText, imageUri = imageUri)
        // Persona instruction rides along with the text (the system prompt is fixed inside
        // ChatRepository); the bubble above keeps the parent's raw wording. Empty for NORMAL, so
        // normal chat sends exactly what it always did.
        val many = roster.size > 1
        val outgoing = spokenPrefix(spoken, isThai) +
            ambiguousChildPrefix(childAmbiguous || (many && ChildMention.isAmbiguous(content, roster, child)), isThai) +
            _uiState.value.mode.promptPrefix(isThai) + content
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.filterNot { it.id == "welcome" } + userMsg,
            isLoading = true
        )
        viewModelScope.launch {
            repo.sendMessage(
                sessionId, outgoing, targetChild, imageBytes, imageMime,
                audioBytes, audioMime,
                _uiState.value.parentRelationship, persona ?: activePersona, isThai,
                family = roster,
                mascotKey = activeMascot,
                // Saved, titled and searched without the "[หมายเหตุ…]" notes built into `outgoing`.
                displayText = content,
                onPartial = { partial ->
                    _uiState.value = _uiState.value.copy(
                        streamingText = com.lullabyai.app.util.visibleWhileStreaming(partial).ifBlank { null }
                    )
                }
            )
                .onSuccess { reply ->
                    // The AI may append an <AGENT_ACTION> block (see RelationshipLabels.chatSystemPrompt)
                    // — it becomes the confirmation card; the visible bubble stays plain text.
                    val (_, action, _) = com.lullabyai.app.util.parseAgentAction(reply.text)
                    val aiMsg = UiChatMessage(
                        id = java.util.UUID.randomUUID().toString(), role = "model",
                        content = com.lullabyai.app.util.chatVisibleText(reply.text),
                        // Signed command cards replace the old unsigned AGENT_ACTION card for the same answer.
                        agentAction = if (reply.commands.isEmpty()) action else null,
                        medical = reply.medical,
                        commands = reply.commands
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + aiMsg,
                        isLoading = false,
                        streamingText = null,
                        lastProvider = com.lullabyai.app.data.remote.ClaudeClient.lastProvider
                    )
                    // Auto Record mode: run the proposed write straight away instead of waiting
                    // for a tap. The card still appears and still reports the outcome, so the
                    // parent can see what was logged and undo it from the record screens.
                    // With several children, only auto-write when this message named one —
                    // otherwise the Child Info tab would silently record against the wrong baby.
                    val actionChild = ChildMention.resolve(
                        content, roster, fallback = if (roster.size <= 1) child else null
                    )
                    val childId = actionChild?.id
                    if (action != null && childId != null &&
                        _uiState.value.autoRecord && isAutoRecordable(action)
                    ) {
                        confirmAgentAction(aiMsg.id, childId)
                    }
                    // Auto Record covers adding only; the command center has nothing else in phase 1,
                    // and edits/deletes will always wait for a tap.
                    if (_uiState.value.autoRecord) {
                        reply.commands.filter { it.command.startsWith("add_") }
                            .forEach { confirmCommand(aiMsg.id, it.ticket) }
                    }
                    // Time spent talking is time spent together. The RPC's cooldown paces this, so
                    // a long conversation builds the bond steadily rather than per message.
                    feedCompanion(xp = 4, announce = false)
                    // Refresh session list (title/updated order may change)
                    repo.getSessions().onSuccess { _uiState.value = _uiState.value.copy(sessions = it) }
                }
                .onFailure {
                    val errMsg = UiChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        role = "model",
                        content = if (isThai) "ขออภัย เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง" else "Sorry, an error occurred. Please try again."
                    )
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + errMsg,
                        isLoading = false,
                        streamingText = null
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
            // A meal for the child is a meal for the companion.
            if (ok) {
                val t = normalizeType(action.type)
                if ("food" in t || "meal" in t || "milk" in t || "formula" in t) feedCompanion()
            }
            if (!ok) android.util.Log.w("AgentAction", "type=${action.type} params=${action.params} confirmText='${action.confirmText}' returned false (unmatched type or missing required param)")
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == messageId) it.copy(actionStatus = if (ok) "confirmed" else "failed") else it
                }
            )
        }
    }

    /**
     * Runs one command-center card. The signed ticket goes back to ai-chat, which checks it and
     * writes with this person's own login; a refusal keeps its named reason for the card to show.
     */
    fun confirmCommand(messageId: String, ticket: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        if (msg.commandStatus[ticket] != null) return
        val command = msg.commands.find { it.ticket == ticket }?.command.orEmpty()
        setCommandStatus(messageId, ticket, "running")
        viewModelScope.launch {
            val result = com.lullabyai.app.data.remote.SparkChatClient.execute(ticket)
            val failure = result.exceptionOrNull()?.message
            if (failure != null) android.util.Log.w("AiCommand", "command=$command failed: $failure")
            setCommandStatus(messageId, ticket, if (failure == null) "done" else "failed:${failure.take(160)}")
        }
    }

    fun cancelCommand(messageId: String, ticket: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        if (msg.commandStatus[ticket] == null) setCommandStatus(messageId, ticket, "cancelled")
    }

    private fun setCommandStatus(messageId: String, ticket: String, status: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map {
                if (it.id == messageId) it.copy(commandStatus = it.commandStatus + (ticket to status)) else it
            }
        )
    }

    fun setAutoRecord(value: Boolean) {
        if (_uiState.value.autoRecord != value) _uiState.value = _uiState.value.copy(autoRecord = value)
    }

    /**
     * Which proposed actions may run unattended in Auto Record mode: additive logging only.
     *
     * Deletes always wait for a tap — an unattended delete loses data the parent may not know was
     * there. Navigation waits too: firing it without a tap would yank the screen out from under
     * someone mid-sentence, and it is resolved in the UI layer anyway.
     */
    private fun isAutoRecordable(action: com.lullabyai.app.data.model.AgentAction): Boolean {
        val t = normalizeType(action.type)
        return "delete" !in t && "remove" !in t && "navigate" !in t
    }

    fun cancelAgentAction(messageId: String) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages.map { if (it.id == messageId) it.copy(actionStatus = "cancelled") else it }
        )
    }

    // Models don't always reproduce a literal string exactly — "Log Sleep" / "log-sleep" instead
    // of "log_sleep" would otherwise silently fall through to the `else -> false` branch with no
    // trace of why.
    private fun normalizeType(type: String) = type.trim().lowercase().replace(Regex("[ -]"), "_")

    /**
     * A clock time, or null if this is not one.
     *
     * Models that are not told the current time answer with a template instead of a value —
     * `"[current_time - 3 minutes]"` was logged verbatim from a real reply. Treating that as
     * absent lets the caller fall back to now, rather than writing nonsense into a child's record
     * or failing the whole action.
     */
    private fun String?.asClockTime(): String? =
        this?.trim()
            ?.takeIf { Regex("^\\d{1,2}:\\d{2}$").matches(it) }
            ?.let { if (it.length == 4) "0$it" else it }

    private fun nowClockTime(): String =
        java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

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
                // "ลูกเพิ่งหลับ" is a complete instruction: the sleep has started and has no end
                // yet. Missing start means now, missing end means still sleeping — the schema
                // allows it, and demanding both made the commonest phrasing unrecordable.
                val start = p.getCi("startTime").asClockTime() ?: nowClockTime()
                val end = p.getCi("endTime").asClockTime()
                val endStamp = end?.let {
                    val date = if (it < start) java.time.LocalDate.now().plusDays(1).toString() else today
                    "${date}T$it:00"
                }
                com.lullabyai.app.data.repository.SleepRepository()
                    .addManualLog(childId, p.getCi("sleepType") ?: "main", "${today}T$start:00", endStamp)
                    .isSuccess
            }
            "health_book" in t || "birth" in t || "child_info" in t -> {
                val updates = mutableMapOf<String, Any?>()
                p.getCi("deliveryType")?.let { updates["delivery_type"] = it }
                p.getCi("placeOfBirth")?.let { updates["place_of_birth"] = it }
                p.getCi("gestationalWeeks")?.toIntOrNull()?.let { updates["gestational_weeks"] = it }
                p.getCi("birthWeightG")?.let { raw ->
                    val g = raw.toDoubleOrNull()
                    if (g != null) updates["birth_weight_g"] = if (g < 100) g * 1000.0 else g
                }
                p.getCi("birthLengthCm")?.toDoubleOrNull()?.let { updates["birth_length_cm"] = it }
                p.getCi("birthHeadCm")?.toDoubleOrNull()?.let { updates["birth_head_cm"] = it }
                p.getCi("bloodGroup")?.let { updates["blood_group"] = it }
                p.getCi("birthdate")?.let { updates["birthdate"] = it }
                p.getCi("allergies")?.let { updates["allergies"] = it }
                if (updates.isNotEmpty()) {
                    val ok = com.lullabyai.app.data.repository.ChildRepository().updateChild(childId, updates).isSuccess
                    if (ok) {
                        updates["birth_weight_g"]?.let { g ->
                            val kg = (g as Double) / 1000.0
                            com.lullabyai.app.data.repository.HealthRepository().addGrowthRecord(
                                com.lullabyai.app.data.model.GrowthRecord(
                                    childId = childId, weightKg = kg,
                                    recordedAt = java.time.Instant.now().toString()
                                )
                            )
                        }
                    }
                    ok
                } else false
            }
            "growth" in t || "weight" in t || "height" in t -> {
                val weight = p.getCi("weightKg")?.toDoubleOrNull()
                val height = p.getCi("heightCm")?.toDoubleOrNull()
                if (weight == null && height == null) {
                    android.util.Log.w("AgentAction", "type='$t' had neither weightKg nor heightCm — keys: ${p.keys}")
                    return false
                }
                val childUpdates = mutableMapOf<String, Any?>()
                if (weight != null) childUpdates["weight_kg"] = weight
                if (height != null) childUpdates["height_cm"] = height
                if (childUpdates.isNotEmpty()) {
                    com.lullabyai.app.data.repository.ChildRepository().updateChild(childId, childUpdates)
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
            // Cost Record. Money is parsed the same way the expense form parses it, so "120",
            // "120.50" and "฿120" all land as the same integer satang the rest of the app stores.
            "expense" in t || "cost" in t || "spend" in t -> {
                val satang = ExpenseViewModel.parseSatang(
                    p.getCi("amount") ?: p.getCi("amountBaht") ?: return false
                ) ?: return false
                com.lullabyai.app.data.repository.ChildRepository().addExpense(
                    childId = childId,
                    amountSatang = satang,
                    category = p.getCi("category") ?: "other",
                    spentOn = p.getCi("date") ?: java.time.LocalDate.now().toString(),
                    name = p.getCi("name") ?: action.confirmText.takeIf { it.isNotBlank() },
                    note = p.getCi("note")
                ).isSuccess
            }
            // A calendar entry, not a workout. This sits ABOVE the exercise branch on purpose:
            // that branch claims the bare word "activity", so without this "add an activity on
            // Saturday" would silently log a workout instead of a plan.
            "schedule" in t || "calendar" in t || "appointment" in t -> {
                val title = p.requireCi("title", t) ?: action.confirmText.ifBlank { return false }
                val zone = java.time.ZoneId.systemDefault()
                val startsAt = p.getCi("startsAt")
                    ?.let { runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull() }
                    ?: p.getCi("date")?.let { d ->
                        // A date with no time means "that day" — 10:00 is a plausible outing hour.
                        runCatching {
                            java.time.LocalDate.parse(d).atTime(10, 0).atZone(zone).toOffsetDateTime()
                        }.getOrNull()
                    }
                    ?: return false
                val endsAt = startsAt.plusHours(
                    p.getCi("durationHours")?.toLongOrNull()?.coerceIn(1L, 8L) ?: 1L
                )
                // The same forward-only rule the Add Activity screen enforces: the agent must not
                // become a way around a validation the UI applies.
                if (validateActivityWindow(startsAt, endsAt, java.time.OffsetDateTime.now()) != null) {
                    return false
                }
                com.lullabyai.app.data.repository.ChildRepository().addActivity(
                    childId = childId,
                    title = title,
                    category = p.getCi("category") ?: "family",
                    startsAt = startsAt.toString(),
                    endsAt = endsAt.toString(),
                    allDay = false,
                    locationName = p.getCi("place"),
                    notes = p.getCi("notes")
                ).isSuccess
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
                quickTip = null, trendInsights = null, nutritionInsight = null, sleepActivityInsight = null, eliminationInsight = null, wasteInsight = null, monthlyReport = null,
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

    /** The hub card's "เรียนรู้ใหม่": regenerate from the latest records instead of the cached text. */
    fun refreshChildInfoCardSummary(child: Child, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        _uiState.value = _uiState.value.copy(childInfoCardSummary = null)
        viewModelScope.launch {
            val text = aiRepo.refresh(child.id, "child_summary_card_${langTag(isThai)}") {
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
            // "who" in the key: insights written before WHO grounding must not be served again.
            val text = aiRepo.getOrGenerate(child.id, "growth_who_${langTag(isThai)}") {
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

    /** [summary] is a caller-built plain-text summary of recent waste/elimination log rows only. */
    fun loadWasteInsight(child: Child, summary: String, isThai: Boolean = true) {
        ensureAnalysisChild(child.id)
        viewModelScope.launch {
            val text = aiRepo.getOrGenerate(child.id, "waste_insight_${langTag(isThai)}") {
                repo.generateWasteInsight(child, summary, isThai = isThai).getOrDefault("กำลังวิเคราะห์...")
            }
            if (isCurrent(child.id)) _uiState.value = _uiState.value.copy(wasteInsight = text)
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
