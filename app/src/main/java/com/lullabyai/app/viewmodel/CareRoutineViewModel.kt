package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HandoffRepository
import com.lullabyai.app.data.repository.RoutineRepository
import com.lullabyai.app.data.routine.CareRoutine
import com.lullabyai.app.data.routine.CareRoutineDuty
import com.lullabyai.app.data.routine.CareRoutinePlan
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

data class CareRoutineUiState(
    val childId: String = "",
    val childName: String = "",
    /** The child row, for the age line and the age-based routine advice. */
    val child: Child? = null,
    val date: String = "",
    val routines: List<CareRoutine> = emptyList(),
    val day: CareRoutinePlan.Day = CareRoutinePlan.build(emptyList(), emptyList(), 0),
    /** Only parents (or an item's creator) may change the routine itself. */
    val canEdit: Boolean = false,
    /** Everyone ticked as a caregiver for this child — the people expected to follow it today. */
    val caregivers: List<String> = emptyList(),
    /** Whoever is holding the child now, from the open handover or today's ticks. */
    val onDuty: CareRoutineDuty.OnDuty? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null
)

/**
 * Drives the routine timeline: the child's repeating day, today's ticks, and who is allowed to
 * change which of the two.
 */
class CareRoutineViewModel(
    private val repo: RoutineRepository = RoutineRepository(),
    private val childRepo: ChildRepository = ChildRepository(),
    private val handoffRepo: HandoffRepository = HandoffRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CareRoutineUiState())
    val uiState: StateFlow<CareRoutineUiState> = _uiState.asStateFlow()

    /** Kept out of the state: only needed to put a name on each tick and on the duty row. */
    private var members: List<ChildAccessMember> = emptyList()
    private var shift: CareRoutineDuty.Shift? = null

    fun load(childId: String) {
        if (childId.isBlank()) return
        _uiState.update { it.copy(childId = childId, loading = true) }
        viewModelScope.launch {
            val child = childRepo.getChildById(childId).getOrNull()
            val members = childRepo.listAccessMembers(childId).getOrNull().orEmpty()
            this@CareRoutineViewModel.members = members
            this@CareRoutineViewModel.shift = openShift(childId)
            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            val mine = members.firstOrNull { it.userId == me }
            _uiState.update {
                it.copy(
                    child = child,
                    childName = child?.name.orEmpty(),
                    // Unknown membership (the list failed to load) is treated as "may edit" only
                    // when there is no row to judge by; the server refuses anyway, and the refusal
                    // is reported rather than silently swallowed.
                    canEdit = mine == null || AccessRoleLabels.isFullAccess(mine.role),
                    caregivers = members.filter { m -> m.isCaregiver }.map { m -> m.displayName }
                )
            }
            refresh()
        }
    }

    /** Re-reads the plan and today's ticks. Called on open and after every write. */
    fun refresh() {
        val childId = _uiState.value.childId
        if (childId.isBlank()) return
        viewModelScope.launch {
            val date = repo.today()
            val routines = repo.listRoutines(childId).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val logs = repo.listLogs(childId, date).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val entries = routines.mapNotNull { r ->
                CareRoutinePlan.parseMinuteOfDay(r.timeOfDay)?.let { minute ->
                    CareRoutinePlan.Entry(r.id, minute, r.title, r.category, r.detail, r.prepNote)
                }
            }
            val planLogs = logs.map { log ->
                val minute = minuteOf(log.recordedAt)
                CareRoutinePlan.Log(
                    routineId = log.routineId,
                    done = log.status == "done",
                    note = log.note,
                    // "บันทึกโดย พี่เลี้ยงใจ" — an unknown actor (a member who has since been
                    // removed) is left blank rather than shown as a raw user id.
                    actorName = members.firstOrNull { it.userId == log.actorId }?.displayName.orEmpty(),
                    recordedMinute = minute
                )
            }
            val onDuty = CareRoutineDuty.pick(
                people = members.map {
                    CareRoutineDuty.Person(it.userId, it.displayName, it.role, it.avatarUrl)
                },
                caregiverIds = members.filter { it.isCaregiver }.map { it.userId }.toSet(),
                shift = shift,
                ticks = logs.map { CareRoutineDuty.Tick(it.actorId.orEmpty(), minuteOf(it.recordedAt)) }
            )
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            _uiState.update {
                it.copy(
                    date = date,
                    routines = routines,
                    day = CareRoutinePlan.build(entries, planLogs, now),
                    onDuty = onDuty,
                    loading = false,
                    error = null
                )
            }
        }
    }

    /**
     * The handover that is open right now, which is what makes someone "ผู้ดูแลปัจจุบัน" rather
     * than merely one of the people with access. A failure here costs the duty row, not the screen.
     */
    private suspend fun openShift(childId: String): CareRoutineDuty.Shift? {
        val handoffs = runCatching { handoffRepo.list(childId) }.getOrNull().orEmpty()
        val open = handoffs.firstOrNull { it.status == "active" } ?: return null
        return CareRoutineDuty.Shift(
            userId = open.recipientId,
            startMinute = minuteOf(open.acceptedAt ?: open.createdAt),
            endMinute = minuteOf(open.expectedEnd)
        )
    }

    /** Local minute-of-day for a stored timestamp, or null when it is absent or unreadable. */
    private fun minuteOf(timestamp: String?): Int? = runCatching {
        java.time.OffsetDateTime.parse(timestamp).toLocalTime().let { it.hour * 60 + it.minute }
    }.getOrNull()

    fun tick(routineId: String, done: Boolean, note: String = "") = write {
        repo.setLog(_uiState.value.childId, routineId, _uiState.value.date, done, note).getOrThrow()
    }

    fun undo(routineId: String) = write {
        repo.clearLog(routineId, _uiState.value.date).getOrThrow()
    }

    fun addRoutine(
        timeOfDay: String,
        category: String,
        title: String,
        detail: String,
        prepNote: String = ""
    ) = write {
        repo.addRoutine(_uiState.value.childId, timeOfDay, category, title, detail, prepNote).getOrThrow()
    }

    fun updateRoutine(
        id: String,
        timeOfDay: String,
        category: String,
        title: String,
        detail: String,
        prepNote: String = ""
    ) = write {
        repo.updateRoutine(id, timeOfDay, category, title, detail, prepNote).getOrThrow()
    }

    fun removeRoutine(id: String) = write { repo.deactivateRoutine(id).getOrThrow() }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun write(block: suspend () -> Unit) {
        if (_uiState.value.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _uiState.update { it.copy(saving = false) }
                refresh()
            } catch (e: CancellationException) {
                _uiState.update { it.copy(saving = false) }
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = e.message ?: "routine_write_failed") }
            }
        }
    }
}
