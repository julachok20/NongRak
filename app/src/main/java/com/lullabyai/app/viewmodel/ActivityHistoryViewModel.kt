package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.CareLog
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildActivityEntry
import com.lullabyai.app.data.model.RecordEdit
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.util.TimeUtils
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * ประวัติกิจกรรมและการแก้ไขข้อมูลลูก (Child Log & Edit Activity History).
 *
 * The feed is list_child_activity_feed; everything layered on it comes from its own table and is
 * matched by record id: what was changed afterwards (record_edits), who has seen it (record_reads),
 * and the photo or voice note a caregiver attached (care_logs).
 */
data class ActivityHistoryUiState(
    val childId: String? = null,
    val childName: String = "",
    val childBirthdate: String? = null,
    val childAvatarUrl: String? = null,
    val entries: List<ChildActivityEntry> = emptyList(),
    /** Every member of the child, for role badges and for who a parent can reply to. */
    val members: List<ChildAccessMember> = emptyList(),
    /** Conversations for this child, so the reply button can reach the right person. */
    val conversations: List<com.lullabyai.app.data.model.CareConversation> = emptyList(),
    val myUserId: String? = null,
    val myRole: String? = null,

    /** record id -> its edits, newest first. */
    val edits: Map<String, List<RecordEdit>> = emptyMap(),
    /** Records deleted in the loaded window, so a parent can bring one back. */
    val deletions: List<RecordEdit> = emptyList(),
    /** record id -> (reader name, read at) for parents who have seen it. */
    val seenBy: Map<String, List<Pair<String, String>>> = emptyMap(),
    /** care log id -> its photo and voice note. */
    val media: Map<String, CareLog> = emptyMap(),

    val category: String = ALL,
    val authorId: String? = null,
    val query: String = "",
    val feedLimit: Int = PAGE,
    val loadedAt: String? = null,
    val loading: Boolean = false,
    val reverting: Long? = null,
    val notice: String? = null,
    val error: String? = null
) {
    /** Owners, co-owners and members: who may reply, and who may revert. The database agrees. */
    val isParent: Boolean get() = AccessRoleLabels.isFullAccess(myRole)
    val canChat: Boolean get() = isParent

    val membersById: Map<String, ChildAccessMember> get() = members.associateBy { it.userId }

    /** "ผู้แก้ไข:" chips — everyone who wrote something in what is loaded, in order of first appearance. */
    val authors: List<Pair<String, String>>
        get() = entries.mapNotNull { e -> e.authorId?.let { it to e.authorName.orEmpty() } }.distinctBy { it.first }

    /** What the list renders, after the category chip, the person chip and the search box. */
    val visible: List<ChildActivityEntry>
        get() = entries.filter { e ->
            matches(category, e) &&
                (authorId == null || e.authorId == authorId) &&
                (query.isBlank() || listOfNotNull(e.detail, e.note, e.authorName, ActivityKindLabels.title(e.kind, true))
                    .any { it.contains(query, ignoreCase = true) })
        }

    /** Day headers, newest day first, using local dates so an evening entry does not jump a day. */
    val days: List<Pair<String, List<ChildActivityEntry>>>
        get() = visible.groupBy { TimeUtils.localDate(it.occurredAt).orEmpty() }.toList()

    val todayCount: Int
        get() {
            val today = java.time.LocalDate.now().toString()
            return entries.count { TimeUtils.localDate(it.occurredAt) == today }
        }

    /** A full page came back, so there may be older history to load. */
    val canLoadMore: Boolean get() = entries.size >= feedLimit

    companion object {
        const val ALL = "all"
        const val PAGE = 80

        /** The design's chips map onto the feed's category or kind; "care" keeps caregiver logs findable. */
        fun matches(key: String, e: ChildActivityEntry): Boolean = when (key) {
            ALL -> true
            "nutrition", "medical", "care" -> e.category == key
            "growth", "sleep", "waste" -> e.kind == key
            else -> true
        }
    }
}

class ActivityHistoryViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityHistoryUiState())
    val uiState: StateFlow<ActivityHistoryUiState> = _uiState.asStateFlow()

    /** The live care-log subscription; held so re-entering the screen does not stack channels. */
    private var watchJob: Job? = null

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val selected = childRepo.getSelectedChild().getOrNull()
            val id = childId ?: ChildSelection.id.value ?: selected?.id ?: run {
                _uiState.update { it.copy(error = "no_child") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    loading = true, error = null, childId = id,
                    childName = selected?.name.orEmpty(),
                    childBirthdate = selected?.birthdate,
                    childAvatarUrl = StorageHelper.signedAvatarUrl(selected?.avatarUrl)
                )
            }

            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            val members = childRepo.listAccessMembers(id).getOrDefault(emptyList())
            val convos = childRepo.careConversations(id).getOrDefault(emptyList())
            val entries = childRepo.listActivityFeed(id, _uiState.value.feedLimit).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val myRole = members.find { it.userId == me }?.role

            // Edits and deletions reach back as far as the oldest entry on screen.
            val since = entries.lastOrNull()?.occurredAt
                ?: Instant.now().minusSeconds(60L * 60 * 24 * 30).toString()
            val allEdits = childRepo.recordEdits(id, since).getOrDefault(emptyList())
            val ids = entries.map { it.sourceId }
            val idSet = ids.toSet()

            val parentNames = members.filter { AccessRoleLabels.isFullAccess(it.role) }
                .associate { it.userId to it.displayName }
            val seen = childRepo.recordReads(id, ids).getOrDefault(emptyList())
                .filter { it.readerId in parentNames && it.readerId != me }
                .groupBy({ it.recordId }, { parentNames[it.readerId].orEmpty() to it.readAt })

            val careIds = entries.filter { it.kind.startsWith("care_") }.map { it.sourceId }
            val media = childRepo.careLogMedia(id, careIds).getOrDefault(emptyList())
                .filter { it.photoPath != null || it.voicePath != null }
                .associateBy { it.id }

            _uiState.update {
                it.copy(
                    loading = false,
                    entries = entries,
                    members = members,
                    conversations = convos,
                    myUserId = me,
                    myRole = myRole,
                    edits = allEdits.filter { e -> e.action == "update" }.groupBy { e -> e.recordId },
                    deletions = allEdits.filter { e -> e.action == "delete" && e.recordId !in idSet }
                        .distinctBy { e -> e.recordId },
                    seenBy = seen,
                    media = media,
                    loadedAt = Instant.now().toString()
                )
            }

            // A parent opening the history has now seen what others wrote. Marked after the list is
            // on screen, and only for rows someone else authored — your own entry is not "read".
            if (AccessRoleLabels.isFullAccess(myRole)) {
                val othersRows = entries.filter { it.authorId != null && it.authorId != me }.map { it.sourceId }
                childRepo.markRecordsRead(id, othersRows)
            }
            startWatching()
        }
    }

    /**
     * Keeps the feed live, so a caregiver's save reaches the parent's screen when it lands.
     * Reloads rather than splicing: the realtime payload carries no author name.
     */
    fun startWatching() {
        if (watchJob?.isActive == true) return
        val id = _uiState.value.childId ?: return
        watchJob = viewModelScope.launch {
            runCatching { childRepo.watchCareLogs(id).collect { load(id) } }
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

    fun setCategory(value: String) = _uiState.update { it.copy(category = value) }
    fun setAuthor(userId: String?) = _uiState.update { it.copy(authorId = userId) }
    fun setQuery(value: String) = _uiState.update { it.copy(query = value) }
    fun dismissNotice() = _uiState.update { it.copy(notice = null, error = null) }

    /** โหลดประวัติย้อนหลัง — one more page of older history. */
    fun loadMore() {
        _uiState.update { it.copy(feedLimit = it.feedLimit + ActivityHistoryUiState.PAGE) }
        load(_uiState.value.childId)
    }

    /**
     * คืนค่าข้อมูลเดิม. The database decides whether the caller may; a refusal is shown with its reason
     * instead of pretending nothing happened.
     */
    fun revert(edit: RecordEdit, isThai: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(reverting = edit.id, error = null, notice = null) }
            childRepo.revertRecordEdit(edit.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            reverting = null,
                            notice = if (edit.action == "delete") (if (isThai) "กู้คืนรายการแล้ว" else "Entry restored")
                            else (if (isThai) "คืนค่าข้อมูลเดิมแล้ว" else "Previous values restored")
                        )
                    }
                    load(_uiState.value.childId)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(reverting = null, error = (if (isThai) "คืนค่าไม่สำเร็จ: " else "Could not restore: ") + e.message) }
                }
        }
    }
}

/** Bilingual labels for the history. The server sends units only; the nouns are chosen here. */
object ActivityKindLabels {
    /** The design's chips: ทั้งหมด, การกิน/นม, การเติบโต, การนอน, การแพทย์, ขับถ่าย — plus ผู้ดูแล. */
    val CATEGORIES = listOf(
        ActivityHistoryUiState.ALL to ("ทั้งหมด" to "All"),
        "nutrition" to ("การกิน/นม" to "Food / milk"),
        "growth" to ("การเติบโต" to "Growth"),
        "sleep" to ("การนอน" to "Sleep"),
        "medical" to ("การแพทย์" to "Medical"),
        "waste" to ("ขับถ่าย" to "Nappies"),
        "care" to ("ผู้ดูแล" to "Caregiver")
    )

    fun categoryLabel(key: String, isThai: Boolean): String =
        CATEGORIES.find { it.first == key }?.second?.let { if (isThai) it.first else it.second } ?: key

    fun title(kind: String, isThai: Boolean): String = when {
        // Caregiver quick-records arrive as "care_<kind>"; their label lives with the grid.
        kind.startsWith("care_") -> CareKinds.label(kind.removePrefix("care_"), isThai)
        else -> when (kind) {
            "growth" -> if (isThai) "อัปเดตข้อมูลการเจริญเติบโต" else "Growth update"
            "vaccine" -> if (isThai) "บันทึกการรับวัคซีน" else "Vaccine recorded"
            "milk" -> if (isThai) "ดื่มนม" else "Milk"
            "food" -> if (isThai) "มื้ออาหาร" else "Meal"
            "waste" -> if (isThai) "ขับถ่าย / เปลี่ยนผ้าอ้อม" else "Nappy"
            "sleep" -> if (isThai) "การนอน" else "Sleep"
            "symptom" -> if (isThai) "บันทึกอาการ" else "Symptom logged"
            "medicine" -> if (isThai) "การให้ยา" else "Medicine given"
            else -> kind
        }
    }

    /** Which feed kind a record_edits table belongs to, for titling deleted entries. */
    fun kindOfTable(table: String): String = when (table) {
        "growth_records" -> "growth"
        "vaccine_records" -> "vaccine"
        "milk_logs" -> "milk"
        "food_logs" -> "food"
        "waste_records" -> "waste"
        "sleep_logs" -> "sleep"
        "health_symptom_logs" -> "symptom"
        "medication_doses" -> "medicine"
        "care_logs" -> "care_note"
        else -> table
    }

    /** Record page for this feed kind — tapping แก้ไขข้อมูล / แก้ไขเวลา opens it. */
    fun recordRoute(kind: String): String = when {
        kind.startsWith("care_") -> com.lullabyai.app.ui.navigation.Screen.CaregiverUpdate.route
        else -> when (kind) {
            "growth" -> com.lullabyai.app.ui.navigation.Screen.AddGrowthRecord.route
            "vaccine" -> com.lullabyai.app.ui.navigation.Screen.VaccineBook.route
            "milk" -> com.lullabyai.app.ui.navigation.Screen.MilkRecord.route
            "food" -> com.lullabyai.app.ui.navigation.Screen.EditFoodEntries.route
            "waste" -> com.lullabyai.app.ui.navigation.Screen.WasteRecord.route
            "sleep" -> com.lullabyai.app.ui.navigation.Screen.SleepTracker.route
            "symptom" -> com.lullabyai.app.ui.navigation.Screen.LogSicknessSymptoms.route
            "medicine" -> com.lullabyai.app.ui.navigation.Screen.AddMedicineSchedule.route
            else -> com.lullabyai.app.ui.navigation.Screen.QuickLogHub.route
        }
    }
}
