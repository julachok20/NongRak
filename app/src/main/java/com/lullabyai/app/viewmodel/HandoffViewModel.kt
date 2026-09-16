package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.handoff.*
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.offline.OfflineCache
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HandoffRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.data.repository.RoutineRepository
import com.lullabyai.app.data.repository.SleepRepository
import com.lullabyai.app.data.routine.CareRoutineDuty
import com.lullabyai.app.data.routine.CareRoutinePlan
import com.lullabyai.app.util.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

data class HandoffState(
    val childId: String = "",
    val childName: String = "",
    val userId: String = "",
    val people: List<ChildAccessMember> = emptyList(),
    val handoffs: List<CareHandoff> = emptyList(),
    val draft: HandoffDraft = HandoffDraft(),
    val busy: Boolean = false,
    val error: String? = null,
    val context: HandoffContext? = null,
    val contexts: Map<String, HandoffContext> = emptyMap(),
    val returnNotes: Map<String, String> = emptyMap(),
    /** Result of the most recent "send to every caregiver", so the screen can say who got it. */
    val lastBroadcast: HandoffBroadcastResult? = null,
    val child: Child? = null,
    val allergies: List<Allergy> = emptyList(),
    val chronic: List<ChronicCondition> = emptyList(),
    val healthRecords: List<com.lullabyai.app.data.model.HealthRecord> = emptyList(),
    val latestTempC: Double? = null,
    val wakeIso: String? = null,
    val mood: String? = null,
    val day: CareRoutinePlan.Day = CareRoutinePlan.build(emptyList(), emptyList(), 0),
    val onDuty: CareRoutineDuty.OnDuty? = null,
    val profile: Profile? = null,
    val doctorName: String? = null,
    val lastRecordAt: String? = null,
    val canEdit: Boolean = false,
    val date: String = "",
    val milkMlToday: Int = 0,
    val urineToday: Int = 0,
    val stoolToday: Int = 0,
    val parentPeerId: String? = null,
    val parentPeerName: String? = null
)

class HandoffViewModel : ViewModel() {
    private val repo = HandoffRepository()
    private val children = ChildRepository()
    private val routines = RoutineRepository()
    private val sleep = SleepRepository()
    private val health = HealthRepository()
    private val visits = DoctorVisitRepository()
    private val auth = AuthRepository()
    private val nutrition = NutritionRepository()
    private val _state = MutableStateFlow(HandoffState())
    val state = _state.asStateFlow()
    private val draftMutex = Mutex()
    private fun key(s: HandoffState) = "handoff_draft_${s.userId}_${s.childId}"

    suspend fun load(childId: String) {
        if (childId.isBlank()) return
        val uid = repo.userId
        if (_state.value.childId != childId || _state.value.userId != uid) {
            _state.value = HandoffState(childId = childId, userId = uid)
            try {
                val draft = OfflineCache.get<HandoffDraft>(key(_state.value)) ?: HandoffDraft()
                val notes = OfflineCache.get<Map<String, String>>(key(_state.value) + "_return") ?: emptyMap()
                _state.update { it.copy(draft = draft, returnNotes = notes) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = "Draft: ${e.message}") }
            }
        }
        refresh()
    }

    suspend fun refresh() {
        val before = _state.value
        if (before.childId.isBlank() || before.busy) return
        try {
            val child = children.getChildById(before.childId).getOrThrow()
            val people = children.listAccessMembers(before.childId).getOrThrow()
            val list = repo.list(before.childId)
            val context = repo.context(before.childId)
            val expanded = before.contexts.keys.filter { id -> list.any { it.id == id } }
                .associateWith { repo.context(before.childId, it) }
            val date = routines.today()
            val routineRows = routines.listRoutines(before.childId).getOrDefault(emptyList())
            val logs = routines.listLogs(before.childId, date).getOrDefault(emptyList())
            val sleepLogs = sleep.getLogs(before.childId).getOrDefault(emptyList())
            val allergyRows = health.getAllergies(before.childId).getOrDefault(emptyList())
            val chronic = health.getChronicConditions(before.childId).getOrDefault(emptyList())
            val records = health.getHealthRecords(before.childId).getOrDefault(emptyList())
            val visit = visits.getVisits(before.childId).getOrDefault(emptyList())
                .firstOrNull { !it.doctorName.isNullOrBlank() }
            val profile = auth.getProfile().getOrNull()
            val milkLogs = nutrition.getMilkLogs(before.childId)
            val wasteRows = health.getWasteRecords(before.childId).getOrDefault(emptyList())
            val now = LocalTime.now().let { it.hour * 60 + it.minute }
            val entries = routineRows.mapNotNull { r ->
                CareRoutinePlan.parseMinuteOfDay(r.timeOfDay)?.let { minute ->
                    CareRoutinePlan.Entry(r.id, minute, r.title, r.category, r.detail, r.prepNote)
                }
            }
            val planLogs = logs.map { log ->
                CareRoutinePlan.Log(
                    routineId = log.routineId,
                    done = log.status == "done",
                    note = log.note,
                    actorName = people.firstOrNull { it.userId == log.actorId }?.displayName.orEmpty(),
                    recordedMinute = minuteOf(log.recordedAt)
                )
            }
            val open = list.firstOrNull { it.status == "active" }
            val shift = open?.let {
                CareRoutineDuty.Shift(
                    userId = it.recipientId,
                    startMinute = minuteOf(it.acceptedAt ?: it.createdAt),
                    endMinute = minuteOf(it.expectedEnd)
                )
            }
            val onDuty = CareRoutineDuty.pick(
                people = people.map { CareRoutineDuty.Person(it.userId, it.displayName, it.role, it.avatarUrl) },
                caregiverIds = people.filter { it.isCaregiver }.map { it.userId }.toSet(),
                shift = shift,
                ticks = logs.map { CareRoutineDuty.Tick(it.actorId.orEmpty(), minuteOf(it.recordedAt)) }
            )
            val lastRecord = listOfNotNull(
                logs.maxByOrNull { it.recordedAt.orEmpty() }?.recordedAt,
                sleepLogs.maxByOrNull { it.endTime ?: it.startTime }?.let { it.endTime ?: it.startTime },
                records.maxByOrNull { it.createdAt.orEmpty() }?.createdAt
            ).maxOrNull()
            val mine = people.firstOrNull { it.userId == before.userId }
            val parents = people.filter {
                AccessRoleLabels.isFullAccess(it.role) && it.userId != before.userId
            }
            val parentPeer = parents.firstOrNull { it.role == "owner" } ?: parents.firstOrNull()
            val waste = ParentHandoffBrief.todayWasteSplit(wasteRows, date)
            if (_state.value.childId == before.childId) _state.update {
                it.copy(
                    childName = child.name,
                    child = child,
                    people = people,
                    handoffs = list,
                    context = context,
                    contexts = expanded,
                    error = null,
                    allergies = allergyRows,
                    chronic = chronic,
                    healthRecords = records,
                    latestTempC = records.mapNotNull { rec -> rec.temperature }.firstOrNull(),
                    wakeIso = ParentHandoffBrief.wakeIso(sleepLogs, LocalDate.parse(date)),
                    mood = ParentHandoffBrief.latestMood(sleepLogs),
                    day = CareRoutinePlan.build(entries, planLogs, now),
                    onDuty = onDuty,
                    profile = profile,
                    doctorName = visit?.doctorName,
                    lastRecordAt = lastRecord,
                    canEdit = mine == null || AccessRoleLabels.isFullAccess(mine.role),
                    date = date,
                    milkMlToday = ParentHandoffBrief.todayMilkMl(milkLogs, date),
                    urineToday = waste.first,
                    stoolToday = waste.second,
                    parentPeerId = parentPeer?.userId,
                    parentPeerName = parentPeer?.displayName?.ifBlank { null }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Do not retain server records after a failed permission refresh.
            _state.update {
                it.copy(
                    handoffs = emptyList(),
                    people = emptyList(),
                    context = null,
                    contexts = emptyMap(),
                    error = e.message ?: "Request failed"
                )
            }
        }
    }

    private fun minuteOf(timestamp: String?): Int? = runCatching {
        java.time.OffsetDateTime.parse(timestamp).toLocalTime().let { it.hour * 60 + it.minute }
    }.getOrNull() ?: TimeUtils.localTime(timestamp)?.let { clock ->
        val p = clock.split(":")
        p[0].toInt() * 60 + p[1].toInt()
    }

    fun draft(value: HandoffDraft) {
        _state.update { it.copy(draft = value) }
        viewModelScope.launch {
            draftMutex.withLock {
                try {
                    val current = _state.value
                    OfflineCache.put(key(current), current.draft)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = "Draft was not saved: ${e.message}") }
                }
            }
        }
    }

    fun send() = mutate {
        val current = _state.value
        repo.send(current.childId, current.draft)
        draftMutex.withLock {
            val empty = HandoffDraft()
            OfflineCache.put(key(current), empty)
            _state.update { it.copy(draft = empty) }
        }
    }

    /** One briefing to every ticked caregiver; the draft is cleared only once the server accepted it. */
    fun broadcast() = mutate {
        val current = _state.value
        val result = repo.broadcast(current.childId, current.draft)
        draftMutex.withLock {
            val empty = HandoffDraft()
            OfflineCache.put(key(current), empty)
            _state.update { it.copy(draft = empty, lastBroadcast = result) }
        }
    }

    fun returnNote(id: String, note: String) {
        _state.update { it.copy(returnNotes = it.returnNotes + (id to note)) }
        viewModelScope.launch {
            draftMutex.withLock {
                try {
                    val current = _state.value
                    OfflineCache.put(key(current) + "_return", current.returnNotes)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(error = "Return draft was not saved: ${e.message}") }
                }
            }
        }
    }

    fun loadSummary(h: CareHandoff) {
        viewModelScope.launch {
            try {
                val context = repo.context(h.childId, h.id)
                _state.update { it.copy(contexts = it.contexts + (h.id to context)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(contexts = it.contexts - h.id, error = e.message) }
            }
        }
    }

    fun act(h: CareHandoff, action: String, note: String = "", index: Int? = null, done: Boolean? = null) =
        mutate { repo.act(h.id, action, note, index, done) }

    fun addTodayItem(timeOfDay: String, category: String, title: String, detail: String, prepNote: String = "") =
        mutate {
            routines.addRoutine(_state.value.childId, timeOfDay, category, title, detail, prepNote).getOrThrow()
        }

    fun tick(routineId: String, note: String = "") = mutate {
        val current = _state.value
        val mine = current.people.firstOrNull { it.userId == current.userId }
        val allowed = mine != null && (mine.isCaregiver || AccessRoleLabels.isFullAccess(mine.role))
        check(allowed) { "routine_tick_denied" }
        val date = current.date.ifBlank { routines.today() }
        routines.setLog(current.childId, routineId, date, done = true, note = note).getOrThrow()
    }

    private fun mutate(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false) }
                refresh()
            } catch (e: CancellationException) {
                _state.update { it.copy(busy = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Request failed") }
            }
        }
    }
}
