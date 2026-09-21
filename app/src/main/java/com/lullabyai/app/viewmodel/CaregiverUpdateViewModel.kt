package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.CareLog
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.remote.CareAlertClient
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HandoffRepository
import com.lullabyai.app.data.settings.ChildSelection
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * บันทึกกิจกรรมด่วนสำหรับผู้ดูแล — the caregiver's one-screen log.
 *
 * One save can carry several things at once: a mood, any number of activities, a note, a photo and
 * a voice note. Each activity becomes its own care_logs row so the parent's history reads them one
 * by one; the note and the attachments ride on the first row rather than being copied onto every
 * row, which would show the same photo three times.
 */
data class CaregiverUpdateUiState(
    val childId: String? = null,
    val childName: String = "",
    val childBirthdate: String? = null,
    val childAvatarUrl: String? = null,
    val myUserId: String? = null,
    val myName: String = "",

    val mood: String? = null,
    val selectedKinds: Set<String> = emptySet(),
    /** What "อื่นๆ" actually was. Folded into the note, since the kind stays "other". */
    val otherLabel: String = "",
    val note: String = "",
    val photo: ByteArray? = null,
    val voice: ByteArray? = null,
    val voiceSeconds: Int = 0,
    val recording: Boolean = false,
    /** แจ้งเตือนด่วนไปยังคุณแม่ทันที. Switched on by itself when "ตัวอุ่นๆ ซึม" is picked. */
    val urgent: Boolean = false,

    /** Everything logged for the child today, by anyone. */
    val today: List<CareLog> = emptyList(),
    /** record id -> the parents (by name) who have seen it. */
    val seenBy: Map<String, List<String>> = emptyMap(),
    /** Owners and co-owners of this child: who the carer reports to and may message. */
    val parents: List<ChildAccessMember> = emptyList(),
    val unreadByPeer: Map<String, Int> = emptyMap(),
    /** The open handover's hours when it is mine, as minutes of the day. */
    val shiftStart: Int? = null,
    val shiftEnd: Int? = null,

    val loading: Boolean = false,
    val saving: Boolean = false,
    /** Set after a save; says what was saved and whether the LINE alert went out. */
    val savedMessage: String? = null,
    val error: String? = null
) {
    /** "อื่นๆ" is not a record until it has been named. */
    private val otherReady: Boolean get() = "other" !in selectedKinds || otherLabel.isNotBlank()

    private val hasLooseContent: Boolean
        get() = note.isNotBlank() || photo != null || voice != null || mood != null

    /** How many care_logs rows a save will write — the "บันทึกที่เลือก N รายการ" count. */
    val pendingCount: Int
        get() = if (selectedKinds.isNotEmpty()) selectedKinds.size else if (hasLooseContent) 1 else 0

    val canSubmit: Boolean get() = pendingCount > 0 && otherReady && !saving && !recording

    val totalUnread: Int get() = unreadByPeer.values.sum()

    /** The newest mood anyone recorded today — the header's "อารมณ์ดี ร่าเริง". */
    val latestMood: CareLog? get() = today.firstOrNull { !it.mood.isNullOrBlank() }
}

class CaregiverUpdateViewModel(
    private val childRepo: ChildRepository = ChildRepository(),
    private val handoffRepo: HandoffRepository = HandoffRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaregiverUpdateUiState())
    val uiState: StateFlow<CaregiverUpdateUiState> = _uiState.asStateFlow()

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val selected = childRepo.getSelectedChild().getOrNull()
            val id = childId ?: ChildSelection.id.value ?: selected?.id ?: run {
                _uiState.update { it.copy(error = "no_child") }
                return@launch
            }
            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            _uiState.update {
                it.copy(
                    loading = true, error = null, childId = id, myUserId = me,
                    childName = selected?.name.orEmpty(),
                    childBirthdate = selected?.birthdate,
                    childAvatarUrl = StorageHelper.signedAvatarUrl(selected?.avatarUrl)
                )
            }

            val members = childRepo.listAccessMembers(id).getOrDefault(emptyList())
            val parents = members.filter { AccessRoleLabels.isFullAccess(it.role) && it.userId != me }
            val myName = members.find { it.userId == me }?.displayName.orEmpty()
            _uiState.update { it.copy(parents = parents, myName = myName) }

            // The shift line only shows when the open handover is actually to me.
            runCatching { handoffRepo.list(id) }.getOrNull().orEmpty()
                .firstOrNull { it.status == "active" && it.recipientId == me }
                ?.let { h ->
                    _uiState.update {
                        it.copy(shiftStart = minuteOf(h.acceptedAt ?: h.createdAt), shiftEnd = minuteOf(h.expectedEnd))
                    }
                }

            refreshToday(id)
        }
    }

    private suspend fun refreshToday(childId: String) {
        val (from, to) = localDayBounds()
        val logs = childRepo.listCareLogs(childId, from, to).getOrDefault(emptyList())
        val unread = childRepo.careUnreadByPeer(childId).getOrDefault(emptyMap())
        val parentNames = _uiState.value.parents.associate { it.userId to it.displayName.orEmpty() }
        val seen = childRepo.recordReads(childId, logs.map { it.id }).getOrDefault(emptyList())
            .filter { it.readerId in parentNames }
            .groupBy({ it.recordId }, { parentNames[it.readerId].orEmpty() })
        _uiState.update { it.copy(loading = false, today = logs, unreadByPeer = unread, seenBy = seen) }
    }

    fun refresh() {
        val id = _uiState.value.childId ?: return
        viewModelScope.launch { refreshToday(id) }
    }

    fun setMood(key: String) = _uiState.update {
        val picked = if (it.mood == key) null else key
        // Picking "ตัวอุ่นๆ ซึม" arms the alert, because that is what the design promises; the
        // caregiver can still switch it off before saving.
        it.copy(mood = picked, urgent = if (picked == CareKinds.MOOD_UNWELL) true else it.urgent, savedMessage = null)
    }

    fun toggleKind(kind: String) = _uiState.update {
        val next = if (kind in it.selectedKinds) it.selectedKinds - kind else it.selectedKinds + kind
        it.copy(selectedKinds = next, savedMessage = null)
    }

    fun setOtherLabel(v: String) = _uiState.update { it.copy(otherLabel = v.take(80)) }
    fun setNote(v: String) = _uiState.update { it.copy(note = v.take(1000)) }
    fun setUrgent(v: Boolean) = _uiState.update { it.copy(urgent = v) }
    fun setPhoto(bytes: ByteArray?) = _uiState.update { it.copy(photo = bytes) }
    fun setRecording(v: Boolean) = _uiState.update { it.copy(recording = v) }
    fun setVoice(bytes: ByteArray?, seconds: Int) =
        _uiState.update { it.copy(voice = bytes, voiceSeconds = if (bytes == null) 0 else seconds, recording = false) }
    fun reportError(message: String) = _uiState.update { it.copy(error = message, recording = false) }
    fun dismissSaved() = _uiState.update { it.copy(savedMessage = null) }

    fun submit(isThai: Boolean) {
        val s = _uiState.value
        val childId = s.childId ?: return
        if (!s.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedMessage = null) }
            try {
                // Upload first: a row pointing at a file that failed to upload would show a broken
                // photo to the parent, so a failed upload stops the save with the reason.
                val photoPath = s.photo?.let {
                    childRepo.uploadCareMedia(childId, it, "jpg", "image/jpeg").getOrElse { e ->
                        error("อัปโหลดรูปไม่สำเร็จ: ${e.message}")
                    }
                }
                val voicePath = s.voice?.let {
                    childRepo.uploadCareMedia(childId, it, "m4a", "audio/mp4").getOrElse { e ->
                        error("อัปโหลดเสียงไม่สำเร็จ: ${e.message}")
                    }
                }

                val note = listOfNotNull(
                    s.otherLabel.trim().takeIf { "other" in s.selectedKinds && it.isNotEmpty() },
                    s.note.trim().takeIf { it.isNotEmpty() }
                ).joinToString(" — ").ifBlank { null }
                val abnormal = if (s.mood == CareKinds.MOOD_UNWELL) CareKinds.moodLabel(s.mood, true) else null

                // Kinds in the grid's order, so the history lists them the way they were picked.
                val kinds = CareKinds.ALL.map { it.kind }.filter { it in s.selectedKinds }
                    .ifEmpty {
                        listOf(if (s.mood != null && note == null && photoPath == null && voicePath == null) "mood" else "note")
                    }

                kinds.forEachIndexed { i, kind ->
                    val first = i == 0
                    childRepo.addCareLog(
                        childId = childId,
                        kind = kind,
                        note = if (first) note else null,
                        mood = s.mood,
                        abnormal = if (first) abnormal else null,
                        photoPath = if (first) photoPath else null,
                        voicePath = if (first) voicePath else null,
                        voiceSeconds = if (first && voicePath != null) s.voiceSeconds else null
                    ).getOrThrow()
                }

                val alertLine = if (s.urgent) alertOutcome(childId, s, kinds, note, isThai) else null
                _uiState.update {
                    it.copy(
                        saving = false,
                        mood = null, selectedKinds = emptySet(), otherLabel = "", note = "",
                        photo = null, voice = null, voiceSeconds = 0, urgent = false,
                        savedMessage = listOfNotNull(
                            if (isThai) "บันทึก ${kinds.size} รายการเรียบร้อย"
                            else "Saved ${kinds.size} ${if (kinds.size == 1) "entry" else "entries"}",
                            alertLine
                        ).joinToString(" • ")
                    )
                }
                refreshToday(childId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(saving = false, error = e.message ?: e.toString()) }
            }
        }
    }

    /**
     * Sends the LINE alert and turns the answer into one line the caregiver can trust. The save has
     * already succeeded by now, so an alert that could not go out is reported, never thrown.
     */
    private suspend fun alertOutcome(
        childId: String,
        s: CaregiverUpdateUiState,
        kinds: List<String>,
        note: String?,
        isThai: Boolean
    ): String {
        val summary = buildString {
            s.mood?.let { append(CareKinds.moodLabel(it, true)).append(". ") }
            val labels = kinds.filter { it != "note" && it != "mood" }.map { CareKinds.label(it, true) }
            if (labels.isNotEmpty()) append(labels.joinToString(", ")).append(". ")
            note?.let { append(it) }
            if (s.myName.isNotBlank()) append("\n— ").append(s.myName)
        }.trim().ifBlank { "มีบันทึกใหม่จากผู้ดูแล" }
        val outcome = CareAlertClient.notifyParents(childId, summary).getOrElse { e ->
            return if (isThai) "แจ้งเตือน LINE ไม่สำเร็จ (${e.message})" else "LINE alert failed (${e.message})"
        }
        return when {
            outcome.sent -> if (isThai) "แจ้งเตือนคุณแม่ทาง LINE แล้ว" else "Parent alerted on LINE"
            outcome.reason == "daily_limit" -> if (isThai) "วันนี้แจ้งเตือนครบจำนวนแล้ว (5 ครั้ง/วัน)" else "Daily alert limit reached"
            outcome.reason == "no_linked_parent" -> if (isThai) "คุณแม่ยังไม่ได้เชื่อม LINE จึงแจ้งเตือนไม่ได้" else "No parent has linked LINE"
            else -> if (isThai) "ไม่ได้ส่งแจ้งเตือน (${outcome.reason})" else "Alert not sent (${outcome.reason})"
        }
    }

    private fun minuteOf(timestamp: String?): Int? = runCatching {
        OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
            .let { it.hour * 60 + it.minute }
    }.getOrNull()

    /** The local calendar day as UTC instants, so "today" means the carer's today. */
    private fun localDayBounds(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toString()
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toString()
        return start to end
    }
}

/**
 * Just enough of my own membership to pick a destination: a carer goes to the quick record,
 * everyone else to the history. Deliberately separate from [ActivityHistoryViewModel] so the
 * Child-info screen does not pull a whole activity feed to decide one button.
 */
class CareRoleViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _amCaregiver = MutableStateFlow(false)
    /** True when I hold the caregiver tick and do NOT hold full access. */
    val amCaregiver: StateFlow<Boolean> = _amCaregiver.asStateFlow()

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id ?: return@launch
            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull() ?: return@launch
            val mine = childRepo.listAccessMembers(id).getOrNull()
                ?.find { it.userId == me } ?: return@launch
            // An owner who also cares for the child still wants the parent view.
            _amCaregiver.value = mine.isCaregiver && !AccessRoleLabels.isFullAccess(mine.role)
        }
    }
}

/** The quick-record choices from Stitch "บันทึกกิจกรรมด่วนสำหรับผู้ดูแล". */
object CareKinds {

    data class Kind(val kind: String, val emoji: String, val th: String, val en: String)

    /** "ตอนนี้น้องกำลังทำอะไรอยู่?" — order matches the design. */
    val ALL = listOf(
        Kind("play", "🧸", "เล่นของเล่นเสริมทักษะ", "Skill-building play"),
        Kind("nap", "🛌", "นอนหลับพักผ่อน", "Sleeping"),
        Kind("feed", "🍼", "ดื่มนมแม่ / นมผง", "Milk"),
        Kind("meal", "🥣", "ทานอาหารว่าง / BLW", "Snack / BLW"),
        Kind("diaper", "🚼", "เปลี่ยนผ้าอ้อม / อาบน้ำ", "Nappy / bath"),
        Kind("motor", "🚶", "เกาะยืน / ฝึกคลาน", "Standing / crawling"),
        Kind("other", "✨", "อื่นๆ (ระบุเองได้)", "Other")
    )

    /**
     * Kinds the grid no longer offers but that older rows carry, plus the two the screen writes when
     * nothing in the grid was picked. Kept so every row renders as words in the parent's history
     * instead of a raw key.
     */
    private val RETIRED = listOf(
        Kind("bath", "🛁", "อาบน้ำ", "Bath"),
        Kind("learn", "📚", "เรียนรู้", "Learning"),
        Kind("outdoor", "🌳", "กิจกรรมนอกบ้าน", "Outdoors"),
        Kind("exercise", "⚽", "ออกกำลังกาย", "Exercise"),
        Kind("read", "📚", "อ่านหนังสือ", "Reading"),
        Kind("mood", "💛", "อารมณ์ของน้อง", "Mood"),
        Kind("note", "📝", "บันทึกจากผู้ดูแล", "Caregiver note")
    )

    private fun find(kind: String): Kind? =
        ALL.find { it.kind == kind } ?: RETIRED.find { it.kind == kind }

    fun label(kind: String, isThai: Boolean): String =
        find(kind)?.let { if (isThai) it.th else it.en } ?: kind

    fun emoji(kind: String): String = find(kind)?.emoji ?: "•"

    const val MOOD_UNWELL = "unwell"

    /** "อารมณ์ตอนนี้น้องเป็นอย่างไร?" — key, emoji, Thai, English. */
    data class Mood(val key: String, val emoji: String, val th: String, val en: String)

    val MOODS = listOf(
        Mood("happy", "😊", "อารมณ์ดี ร่าเริง", "Happy and playful"),
        Mood("sleepy", "😴", "เริ่มง่วง ขยี้ตา", "Getting sleepy"),
        Mood("hungry", "🍼", "หิวนม/อาหาร", "Hungry"),
        Mood("fussy", "🥺", "งอแงเล็กน้อย", "A little fussy"),
        Mood(MOOD_UNWELL, "🤒", "ตัวอุ่นๆ ซึม", "Warm and listless")
    )

    /** Older rows used "good"; it still reads as words. */
    private val RETIRED_MOODS = listOf(Mood("good", "😊", "อารมณ์ดี", "Cheerful"))

    fun moodLabel(mood: String?, isThai: Boolean): String =
        (MOODS + RETIRED_MOODS).find { it.key == mood }?.let { if (isThai) it.th else it.en } ?: ""

    fun moodEmoji(mood: String?): String =
        (MOODS + RETIRED_MOODS).find { it.key == mood }?.emoji ?: ""
}
