package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.CareMessage
import com.lullabyai.app.data.remote.AiProxyClient
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class CareChatUiState(
    val childId: String? = null,
    val childName: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val messages: List<CareMessage> = emptyList(),
    val draft: String = "",
    /**
     * What is being quoted, or null for an ordinary send. Usually a real message; a quote carried
     * in from ประวัติกิจกรรม is a stand-in with a blank [CareMessage.id], because the activity feed
     * is assembled from eight record tables and has no message row to point at. Both kinds draw
     * the same composer banner and the same bubble quote block — see [isActivityQuote].
     */
    val replyingTo: CareMessage? = null,
    /** Ids of withdrawn messages the reader expanded. Per-session, never persisted. */
    val revealed: Set<String> = emptySet(),
    /** null until checked; non-null means this user already accepted the notice. */
    val consentAt: String? = null,
    val consentChecked: Boolean = false,
    /** True when I am the carer here — only then does the AI read and record. */
    val amCaregiver: Boolean = false,
    /** The AI's follow-up when a message was too vague to record. Transient, never stored. */
    val aiQuestion: String? = null,
    /** What the AI just filed, so the carer sees it happened. */
    val aiRecorded: String? = null,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null
) {
    val needsConsent: Boolean get() = consentChecked && consentAt == null
}

/**
 * True for a quote that stands in for an activity-history entry rather than a real message.
 * Only a row that came back from the server carries an id, so a blank one can only be the
 * stand-in [CareChatViewModel.quoteActivity] built.
 */
val CareMessage.isActivityQuote: Boolean get() = id.isBlank()

class CareChatViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareChatUiState())
    val uiState: StateFlow<CareChatUiState> = _uiState.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Live thread subscription; held so re-opening the chat does not stack channels. */
    private var watchJob: Job? = null

    /** path -> signed URL. Signing costs a round trip, so each picture is signed once per view. */
    private val signedImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val imageUrls: StateFlow<Map<String, String>> = signedImages.asStateFlow()

    fun load(peerId: String, childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            val childName = childRepo.getSelectedChild().getOrNull()?.name ?: ""
            _uiState.update {
                it.copy(loading = true, error = null, childId = id, peerId = peerId, childName = childName)
            }

            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            val members = childRepo.listAccessMembers(id).getOrDefault(emptyList())
            val mine = members.find { m -> m.userId == me }
            val consent = childRepo.chatAiConsentAt(id).getOrNull()

            _uiState.update {
                it.copy(
                    peerName = members.find { m -> m.userId == peerId }?.displayName.orEmpty(),
                    amCaregiver = mine?.isCaregiver == true && !AccessRoleLabels.isFullAccess(mine.role),
                    consentAt = consent,
                    consentChecked = true
                )
            }
            refresh()
            // Needs the child id this load just resolved, so it starts here rather than in the UI.
            startWatching()
        }
    }

    /** ponytail: pull-based. Realtime subscriptions can come later if the delay ever matters. */
    fun refresh() {
        val s = _uiState.value
        val childId = s.childId ?: return
        viewModelScope.launch {
            val msgs = childRepo.listCareMessages(childId, s.peerId).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            _uiState.update { it.copy(loading = false, messages = msgs) }
            // Opening the thread is what "read" means, so it is marked here rather than on a
            // scroll gesture — a message you can see is a message you have received.
            if (msgs.any { !it.mine }) {
                childRepo.markCareMessagesRead(childId, s.peerId)
            }
            signMissingImages(msgs)
        }
    }

    /** Signs any picture path we have not signed yet, leaving already-signed ones alone. */
    private suspend fun signMissingImages(msgs: List<CareMessage>) {
        val pending = msgs.mapNotNull { it.imageUrl }
            .filter { it.isNotBlank() && it !in signedImages.value }
            .distinct()
        if (pending.isEmpty()) return
        val signed = pending.mapNotNull { path ->
            childRepo.signedChatImageUrl(path)?.let { path to it }
        }
        if (signed.isNotEmpty()) signedImages.update { it + signed }
    }

    /**
     * Keeps the thread live so a reply lands without the reader touching anything. Idempotent —
     * the screen can call it on every entry.
     */
    fun startWatching() {
        if (watchJob?.isActive == true) return
        val s = _uiState.value
        val childId = s.childId ?: return
        watchJob = viewModelScope.launch {
            runCatching { childRepo.watchCareMessages(childId, s.peerId).collect { refresh() } }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    override fun onCleared() {
        stopWatching()
        super.onCleared()
    }

    /**
     * Uploads a picture, then sends it as a message. The upload happens first on purpose: a
     * message row pointing at an object that failed to upload would render as a broken bubble
     * forever, and there is no way to repair it from the client.
     */
    fun sendImage(bytes: ByteArray, ext: String = "jpg") {
        val s = _uiState.value
        val childId = s.childId ?: return
        if (s.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null) }
            val path = childRepo.uploadChatImage(childId, bytes, ext).getOrElse { e ->
                _uiState.update { it.copy(sending = false, error = e.message) }
                return@launch
            }
            childRepo.sendCareMessage(
                childId = childId,
                peerId = s.peerId,
                body = s.draft.trim(),
                replyTo = s.replyingTo?.takeIf { !it.isActivityQuote }?.id,
                imageUrl = path,
                quoteText = s.replyingTo?.takeIf { it.isActivityQuote }?.body,
                quoteAuthor = s.replyingTo?.takeIf { it.isActivityQuote }?.senderName
            ).onSuccess {
                _uiState.update { it.copy(sending = false, draft = "", replyingTo = null) }
                refresh()
            }.onFailure { e ->
                _uiState.update { it.copy(sending = false, error = e.message) }
            }
        }
    }

    fun setDraft(v: String) = _uiState.update { it.copy(draft = v) }

    fun acceptPolicy() {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            childRepo.acceptChatAiPolicy(childId)
                .onSuccess {
                    val at = childRepo.chatAiConsentAt(childId).getOrNull()
                    _uiState.update { it.copy(consentAt = at) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun startReply(m: CareMessage) = _uiState.update { it.copy(replyingTo = m) }
    fun cancelReply() = _uiState.update { it.copy(replyingTo = null) }

    /**
     * Quote a ประวัติกิจกรรม entry. The author of that entry is whoever this conversation is with —
     * the history screen opens the chat with them — so the peer's name is the quote's attribution.
     */
    fun quoteActivity(subject: String) = _uiState.update {
        if (subject.isBlank()) it
        else it.copy(replyingTo = CareMessage(body = subject, senderName = it.peerName))
    }

    /** Expanding a withdrawn message is a per-reader choice, so it lives only in UI state. */
    fun toggleReveal(id: String) = _uiState.update {
        it.copy(revealed = if (id in it.revealed) it.revealed - id else it.revealed + id)
    }

    /**
     * Withdraws one of my own messages. The other side keeps a tombstone that still opens to the
     * original text — the screen warns about that before calling this.
     */
    fun unsend(messageId: String) {
        viewModelScope.launch {
            childRepo.unsendCareMessage(messageId)
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun dismissAiQuestion() = _uiState.update { it.copy(aiQuestion = null) }
    fun dismissAiRecorded() = _uiState.update { it.copy(aiRecorded = null) }

    fun send() {
        val s = _uiState.value
        val childId = s.childId ?: return
        val body = s.draft.trim()
        if (body.isEmpty() || s.sending) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null, draft = "") }
            childRepo.sendCareMessage(
                childId = childId,
                peerId = s.peerId,
                body = body,
                replyTo = s.replyingTo?.takeIf { !it.isActivityQuote }?.id,
                quoteText = s.replyingTo?.takeIf { it.isActivityQuote }?.body,
                quoteAuthor = s.replyingTo?.takeIf { it.isActivityQuote }?.senderName
            )
                .onSuccess {
                    _uiState.update { it.copy(sending = false, replyingTo = null) }
                    refresh()
                    // Only the carer's own words describe care worth filing. A parent asking
                    // "did she nap?" is a question, not a record.
                    if (s.amCaregiver) readAndRecord(childId, body)
                }
                .onFailure { e ->
                    // Give the text back rather than losing it.
                    _uiState.update { it.copy(sending = false, error = e.message, draft = body) }
                }
        }
    }

    /**
     * Has the AI read one caregiver message and, if it clearly describes care, file it as a
     * care log. When something essential is missing it asks instead of guessing — a wrong feed
     * volume in a child's record is worse than no entry at all.
     *
     * Failures are silent: the message is already delivered, and the chat must not look broken
     * because a model call did not come back.
     */
    private suspend fun readAndRecord(childId: String, message: String) {
        val result = AiProxyClient.generate(
            prompt = message,
            useSearch = false,
            maxTokens = 300,
            system = EXTRACT_SYSTEM,
            jsonMode = true
        )
        val text = result.text ?: return
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return

        fun str(key: String): String? =
            obj[key]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() && it != "null" }

        val kind = str("kind")
        if (kind != null && kind != "none") {
            childRepo.addCareLog(
                childId = childId,
                kind = kind,
                note = str("note"),
                mood = str("mood"),
                abnormal = str("abnormal")
            ).onSuccess {
                _uiState.update { it.copy(aiRecorded = CareKinds.label(kind, true)) }
            }
        }
        str("question")?.let { q -> _uiState.update { it.copy(aiQuestion = q) } }
    }

    private companion object {
        /**
         * Blunt on purpose: the model chooses between "record this" and "ask about this", and is
         * told to prefer asking. Everything it writes shows up in the parent's history under the
         * caregiver's name, so a confident wrong guess is the expensive failure.
         */
        const val EXTRACT_SYSTEM = """
คุณคือผู้ช่วยที่อ่านข้อความจากพี่เลี้ยงเด็กที่ส่งถึงผู้ปกครอง
หน้าที่: ดึงข้อมูลการดูแลเด็กออกมาเป็น JSON เท่านั้น ห้ามมีข้อความอื่น

รูปแบบ:
{"kind":"feed|nap|diaper|play|bath|meal|exercise|read|other|none","note":"...","mood":"good|fussy|unwell|null","abnormal":"...|null","question":"...|null"}

กฎ:
- kind = "none" ถ้าข้อความไม่ได้รายงานการดูแลเด็ก (เช่น ทักทาย ถามคำถาม นัดเวลา)
- note = สรุปสั้นๆ ของสิ่งที่ทำ พร้อมปริมาณ/เวลา ถ้ามีระบุไว้
- abnormal = อาการผิดปกติที่ควรให้ผู้ปกครองทราบ ถ้าไม่มีให้เป็น null
- question = คำถามภาษาไทยสั้นๆ ถ้าข้อมูลสำคัญขาดหาย (เช่น ปริมาณนม เวลา) ถ้าครบแล้วให้เป็น null
- ถ้าไม่แน่ใจ ให้ถามดีกว่าเดา
"""
    }
}
