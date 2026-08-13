package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.CareLog
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class CaregiverUpdateUiState(
    val childId: String? = null,
    val childName: String = "",
    /** Which grid tile is armed. Saving needs a kind, so this doubles as the submit guard. */
    val selectedKind: String? = null,
    val note: String = "",
    val mood: String? = null,
    val abnormal: String = "",
    /** Everything logged for the child today, by anyone — the daily summary reads this. */
    val today: List<CareLog> = emptyList(),
    /** Owners and co-owners of this child: who the carer is allowed to message. */
    val parents: List<ChildAccessMember> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val savedOk: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean get() = selectedKind != null && !saving

    /** kind -> how many times today, for the summary sheet. */
    val summary: List<Pair<String, Int>>
        get() = today.groupingBy { it.kind }.eachCount().entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /** Flagged concerns come first in the summary — that is the whole reason a parent opens it. */
    val concerns: List<CareLog> get() = today.filter { !it.abnormal.isNullOrBlank() }
}

class CaregiverUpdateViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaregiverUpdateUiState())
    val uiState: StateFlow<CaregiverUpdateUiState> = _uiState.asStateFlow()

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
            val parents = childRepo.listAccessMembers(id).getOrDefault(emptyList())
                .filter { AccessRoleLabels.isFullAccess(it.role) && it.userId != me }
            _uiState.update { it.copy(parents = parents) }

            refreshToday(id)
        }
    }

    private suspend fun refreshToday(childId: String) {
        val (from, to) = localDayBounds()
        val logs = childRepo.listCareLogs(childId, from, to).getOrDefault(emptyList())
        _uiState.update { it.copy(loading = false, today = logs) }
    }

    fun selectKind(kind: String) = _uiState.update {
        // Tapping the armed tile again disarms it, so a mis-tap costs one tap to undo.
        it.copy(selectedKind = if (it.selectedKind == kind) null else kind, savedOk = false)
    }

    fun setNote(v: String) = _uiState.update { it.copy(note = v) }
    fun setMood(v: String) = _uiState.update { it.copy(mood = if (it.mood == v) null else v) }
    fun setAbnormal(v: String) = _uiState.update { it.copy(abnormal = v) }

    fun clearForm() = _uiState.update {
        it.copy(selectedKind = null, note = "", mood = null, abnormal = "", savedOk = false)
    }

    fun submit() {
        val s = _uiState.value
        val childId = s.childId ?: return
        val kind = s.selectedKind ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedOk = false) }
            childRepo.addCareLog(
                childId = childId,
                kind = kind,
                note = s.note,
                mood = s.mood,
                abnormal = s.abnormal
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        saving = false, savedOk = true, selectedKind = null,
                        note = "", mood = null, abnormal = ""
                    )
                }
                refreshToday(childId)
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    /** The local calendar day as UTC instants, so "today" means the carer's today. */
    private fun localDayBounds(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toString()
        val end = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toString()
        return start to end
    }
}

/**
 * Just enough of my own membership to pick a destination: a carer goes to the daily-update
 * screen, everyone else to the history. Deliberately separate from [ActivityHistoryViewModel]
 * so the Child-info screen does not pull a whole activity feed to decide one button.
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
            // An owner who also cares for the child still wants the parent view; the daily-update
            // screen is for people whose job here IS the daily log.
            _amCaregiver.value = mine.isCaregiver && !AccessRoleLabels.isFullAccess(mine.role)
        }
    }
}

/** The quick-record grid from Stitch "Child Update & Caregiver Chat". */
object CareKinds {

    data class Kind(val kind: String, val emoji: String, val th: String, val en: String)

    /** Order matches the Stitch grid. */
    val ALL = listOf(
        Kind("feed", "🍼", "ป้อนนม", "Feeding"),
        Kind("nap", "😴", "นอนหลับ", "Sleep"),
        Kind("diaper", "🧷", "เปลี่ยนผ้าอ้อม", "Diaper"),
        Kind("play", "🧸", "เล่น", "Play"),
        Kind("bath", "🛁", "อาบน้ำ", "Bath"),
        Kind("meal", "🥣", "ทานอาหาร", "Meal"),
        Kind("exercise", "⚽", "ออกกำลังกาย", "Exercise"),
        Kind("read", "📚", "อ่านหนังสือ", "Reading"),
        Kind("other", "➕", "อื่นๆ", "Other")
    )

    fun label(kind: String, isThai: Boolean): String =
        ALL.find { it.kind == kind }?.let { if (isThai) it.th else it.en } ?: kind

    fun emoji(kind: String): String = ALL.find { it.kind == kind }?.emoji ?: "•"

    /** mood key, Thai, English — matches "วันนี้น้องเป็นอย่างไรบ้าง?" in the design. */
    val MOODS = listOf(
        Triple("good", "อารมณ์ดี", "Cheerful"),
        Triple("fussy", "งอแงเล็กน้อย", "A little fussy"),
        Triple("unwell", "ไม่สบาย", "Unwell")
    )

    fun moodLabel(mood: String?, isThai: Boolean): String =
        MOODS.find { it.first == mood }?.let { if (isThai) it.second else it.third } ?: ""
}
