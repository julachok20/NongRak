package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildActivityEntry
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

data class ActivityHistoryUiState(
    val childId: String? = null,
    val childName: String = "",
    val entries: List<ChildActivityEntry> = emptyList(),
    /**
     * Who the viewer can open a conversation with: everyone holding the "helps care for this
     * child" tick, plus the other full-access parents, minus themselves.
     */
    val caregivers: List<ChildAccessMember> = emptyList(),
    /** Conversations for this child, so the design's preview card has something to show. */
    val conversations: List<com.lullabyai.app.data.model.CareConversation> = emptyList(),
    val myUserId: String? = null,
    val myRole: String? = null,
    val category: String = ALL,
    val query: String = "",
    val sort: ActivitySort = ActivitySort.NEWEST,
    val loading: Boolean = false,
    val error: String? = null
) {
    /**
     * Only an owner or co-owner may open a caregiver chat. Everyone else sees the same history
     * with no way to start a conversation from it — the user's "Exclude chat link button
     * except owner/co-owner".
     */
    val canChat: Boolean get() = AccessRoleLabels.isFullAccess(myRole)

    /**
     * The one conversation the preview card shows: whatever is unread first, else the most
     * recent. Anything more would duplicate the Care Circle hub sitting one tap away.
     */
    val topConversation: com.lullabyai.app.data.model.CareConversation?
        get() = conversations.firstOrNull { it.unread > 0 }
            ?: conversations.firstOrNull { it.hasHistory }

    /** What the list actually renders, after the chip, the search box and the sort. */
    val visible: List<ChildActivityEntry>
        get() {
            val filtered = entries.filter { e ->
                (category == ALL || e.category == category) &&
                    (query.isBlank() || listOfNotNull(e.detail, e.note, e.authorName)
                        .any { it.contains(query, ignoreCase = true) })
            }
            return when (sort) {
                // The server already returns newest-first, so this is the no-op case.
                ActivitySort.NEWEST -> filtered
                ActivitySort.OLDEST -> filtered.sortedBy { it.occurredAt }
                // Kind first, time second, so each type reads as its own run instead of the
                // eight-way interleave that made the list feel like noise.
                ActivitySort.KIND -> filtered.sortedWith(
                    compareBy<ChildActivityEntry> { it.kind }.thenByDescending { it.occurredAt }
                )
                ActivitySort.AUTHOR -> filtered.sortedWith(
                    compareBy<ChildActivityEntry> { it.authorName.orEmpty() }
                        .thenByDescending { it.occurredAt }
                )
            }
        }

    /**
     * The heading each row sits under. Time-based sorts group by day; the others group by what
     * you sorted on, because a date header over a kind-sorted list explains nothing.
     */
    fun groupKeyOf(entry: ChildActivityEntry): String = when (sort) {
        // Local date, not the raw UTC prefix — otherwise an evening entry in Bangkok files
        // itself under the following day.
        ActivitySort.NEWEST, ActivitySort.OLDEST -> TimeUtils.localDate(entry.occurredAt).orEmpty()
        ActivitySort.KIND -> entry.kind
        ActivitySort.AUTHOR -> entry.authorName.orEmpty()
    }

    companion object { const val ALL = "all" }
}

/** How the history is ordered. Time is the default; the rest exist to tame a busy log. */
enum class ActivitySort { NEWEST, OLDEST, KIND, AUTHOR }

class ActivityHistoryViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityHistoryUiState())
    val uiState: StateFlow<ActivityHistoryUiState> = _uiState.asStateFlow()

    /** The live care-log subscription; held so re-entering the screen does not stack channels. */
    private var watchJob: Job? = null

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            val name = childRepo.getSelectedChild().getOrNull()?.name ?: ""
            _uiState.update { it.copy(loading = true, error = null, childId = id, childName = name) }

            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            val members = childRepo.listAccessMembers(id).getOrDefault(emptyList())
            val convos = childRepo.careConversations(id).getOrDefault(emptyList())
            val entries = childRepo.listActivityFeed(id).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    entries = entries,
                    // Never offer to chat with yourself.
                    // Who this viewer may open a conversation with: caregivers, plus the other
                    // parents — an owner and a co-owner need to talk to each other too.
                    caregivers = members.filter { m ->
                        m.userId != me &&
                            (m.isCaregiver || AccessRoleLabels.isFullAccess(m.role))
                    },
                    conversations = convos,
                    myUserId = me,
                    myRole = members.find { m -> m.userId == me }?.role
                )
            }
            // Started here, not from the screen: the subscription needs the child id that this
            // load just resolved, so sequencing it anywhere else invites a silent no-op.
            startWatching()
        }
    }

    /**
     * Keeps the feed live. A caregiver's save reaches the parent's screen the moment it lands
     * rather than on the next manual open — that immediacy is the point of the feature.
     *
     * ponytail: reloads the feed instead of splicing the changed row in. The realtime payload
     * carries no author name, so a splice would read "ผู้ดูแล" until the next refresh anyway.
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

    fun setQuery(value: String) = _uiState.update { it.copy(query = value) }

    fun setSort(value: ActivitySort) = _uiState.update { it.copy(sort = value) }
}

/** Bilingual titles for feed rows. The server sends units only; the noun is chosen here. */
object ActivityKindLabels {
    val CATEGORIES = listOf(
        ActivityHistoryUiState.ALL to ("ทั้งหมด" to "All"),
        "growth" to ("การเติบโต" to "Growth"),
        "medical" to ("การแพทย์" to "Medical"),
        "nutrition" to ("โภชนาการ" to "Nutrition"),
        "daily" to ("ประจำวัน" to "Daily"),
        // Everything the caregiver logs from the quick-record grid.
        "care" to ("ผู้ดูแล" to "Care")
    )

    fun sortLabel(sort: ActivitySort, isThai: Boolean): String = when (sort) {
        ActivitySort.NEWEST -> if (isThai) "ใหม่สุด" else "Newest"
        ActivitySort.OLDEST -> if (isThai) "เก่าสุด" else "Oldest"
        ActivitySort.KIND -> if (isThai) "ตามประเภท" else "By type"
        ActivitySort.AUTHOR -> if (isThai) "ตามผู้บันทึก" else "By person"
    }

    fun categoryLabel(key: String, isThai: Boolean): String =
        CATEGORIES.find { it.first == key }?.second
            ?.let { if (isThai) it.first else it.second } ?: key

    fun title(kind: String, isThai: Boolean): String = when {
        // Caregiver quick-records arrive as "care_<kind>"; their label lives with the grid.
        kind.startsWith("care_") -> CareKinds.label(kind.removePrefix("care_"), isThai)
        else -> titleOfRecord(kind, isThai)
    }

    private fun titleOfRecord(kind: String, isThai: Boolean): String = when (kind) {
        "growth" -> if (isThai) "อัปเดตการเติบโต" else "Growth update"
        "vaccine" -> if (isThai) "บันทึกการรับวัคซีน" else "Vaccine recorded"
        "milk" -> if (isThai) "บันทึกการดื่มนม" else "Milk recorded"
        "food" -> if (isThai) "บันทึกมื้ออาหาร" else "Meal recorded"
        "waste" -> if (isThai) "บันทึกการขับถ่าย" else "Diaper recorded"
        "sleep" -> if (isThai) "บันทึกการนอน" else "Sleep recorded"
        "symptom" -> if (isThai) "บันทึกอาการ" else "Symptom logged"
        "medicine" -> if (isThai) "บันทึกการให้ยา" else "Medicine given"
        else -> kind
    }
}
