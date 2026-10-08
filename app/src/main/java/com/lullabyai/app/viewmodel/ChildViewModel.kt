package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.ChildFavorite
import com.lullabyai.app.data.model.FavoriteCategory
import com.lullabyai.app.data.model.FavoriteSource
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.AiTask
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.repository.AiRepository
import com.lullabyai.app.data.repository.AddFavoriteResult
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.util.DoctorReportGenerator
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class ChildUiState(
    // Starts true (not false) so the empty-state "please add a child" gate on Dashboard/Child Info/
    // Statistics doesn't flash for a frame before loadChild() has even had a chance to run — that
    // was showing the wrong prompt to users who already have a child, right as each screen opened.
    val isLoading: Boolean = true,
    val children: List<Child> = emptyList(),   // all children on this account (for the switcher)
    val child: Child? = null,
    val vaccines: List<VaccineRecord> = emptyList(),
    val growthRecords: List<GrowthRecord> = emptyList(),
    val healthRecords: List<HealthRecord> = emptyList(),
    /** Latest symptom-log date per health_record_id — feeds Batch Q #9 auto-resolve. */
    val symptomActivityByRecord: Map<String, String> = emptyMap(),
    /** Latest symptom write timestamp per episode — feeds F3 10-min edit lock. */
    val symptomUpdateByRecord: Map<String, String> = emptyMap(),
    // Stitch AIInsightCard text for HEALTH-UX owned list screens
    val healthHistoryInsight: String? = null,
    val healthHistoryInsightLoading: Boolean = false,
    val sicknessFollowUpInsight: String? = null,
    val sicknessFollowUpInsightLoading: Boolean = false,
    val medicationHistoryInsight: String? = null,
    val medicationHistoryInsightLoading: Boolean = false,
    val doctorVisitHistoryInsight: String? = null,
    val doctorVisitHistoryInsightLoading: Boolean = false,
    val symptomLogPhotoSignedUrl: String? = null,
    val allergies: List<Allergy> = emptyList(),
    val chronicConditions: List<ChronicCondition> = emptyList(),
    val milestones: List<DevelopmentMilestone> = emptyList(),
    val aiTasks: List<AiTask> = emptyList(),
    val tasksLoading: Boolean = false,
    val avatarUploading: Boolean = false,
    val reportLoading: Boolean = false,   // generating the doctor-export PDF
    val medicalDocuments: List<com.lullabyai.app.data.model.MedicalDocument> = emptyList(),
    val docUploading: Boolean = false,
    val medicationSchedules: List<com.lullabyai.app.data.model.MedicationSchedule> = emptyList(),
    val medicationDoses: List<com.lullabyai.app.data.model.MedicationDose> = emptyList(),
    val medicationSaving: Boolean = false,
    val sleepLogs: List<com.lullabyai.app.data.model.SleepLog> = emptyList(),
    val sleepSchedules: List<com.lullabyai.app.data.model.SleepSchedule> = emptyList(),
    val activeSleepSession: com.lullabyai.app.data.model.SleepLog? = null,
    val sleepSaving: Boolean = false,
    /** Short-lived signed URL for the child photo (private bucket). Use this to display the avatar. */
    val childAvatarSignedUrl: String? = null,
    /**
     * สิ่งที่น้องชอบ. Empty for a caretaker whose parent turned sharing off — the database returns
     * no rows, so there is nothing here to hide.
     */
    val favorites: List<ChildFavorite> = emptyList(),
    /** list_child_activity_feed — every record for this child, newest first, with its author. */
    val activityFeed: List<com.lullabyai.app.data.model.ChildActivityEntry> = emptyList(),
    val favoritesSaving: Boolean = false,
    // --- Health-record detail screen (#7) ---
    val openHealthRecordId: String? = null,         // record currently shown on the detail screen
    val healthDetailAdvice: String? = null,         // one-shot AI advice for the open record
    val healthDetailAdviceLoading: Boolean = false,
    val healthPhotoSignedUrl: String? = null,       // signed URL for the open record's photo
    val healthPhotoUploading: Boolean = false,
    val healthDetailSaving: Boolean = false,
    val wasteRecords: List<com.lullabyai.app.data.model.WasteRecord> = emptyList(),
    // --- Sickness day-tracking (symptom logs under an open health-record "episode") ---
    val symptomLogs: List<com.lullabyai.app.data.model.SymptomLog> = emptyList(),
    val symptomLogSaving: Boolean = false,
    // --- Doctor visits ("บันทึกการหาหมอ") ---
    val doctorVisits: List<DoctorVisit> = emptyList(),
    val doctorVisitsLoading: Boolean = false,
    val openDoctorVisitId: String? = null,        // visit currently shown on VisitDetailsScreen / edited on RecordVisitScreen
    val doctorVisitSaving: Boolean = false,
    val visitDocuments: List<com.lullabyai.app.data.model.MedicalDocument> = emptyList(),
    val visitDocUploading: Boolean = false,
    // SymptomPickerScreen -> RecordVisitScreen hand-off, same pattern as openHealthRecordId.
    val selectedVisitSymptoms: List<String>? = null,
    // --- Sync child (multi-parent access) ---
    val syncSearching: Boolean = false,
    val syncSearchResults: List<com.lullabyai.app.data.model.ChildSyncMatch> = emptyList(),
    val syncRequestSent: Boolean = false,
    val syncError: String? = null,
    val inviteCode: String? = null,
    val inviteCodeChildName: String? = null,   // which child the shown code belongs to — each child gets its own code
    val pendingSyncRequests: List<com.lullabyai.app.data.model.ChildSyncRequest> = emptyList(),
    /**
     * Requests the owner has already answered on this device. [loadPendingSyncRequests] re-reads
     * from the server, so without this a row that is still `pending` server-side — a denial the RPC
     * refused, or a duplicate the requester filed — reappears on the dashboard card immediately
     * after Approve/Deny. Once answered, it stays gone; a failure is reported through `syncError`
     * instead of by silently putting the card back.
     */
    val handledSyncIds: Set<String> = emptySet(),
    /** One-shot popup when a doctor sends vaccine verification to this account. */
    val doctorVaccineInboxPopup: DoctorVaccineInboxItem? = null,
    // --- Home info-ask (replaces the daily-question popup) ------------------------------------

    val dailyQuestion: String? = null,
    val infoAsks: List<com.lullabyai.app.data.infoask.InfoAskPrompt> = emptyList(),
    /**
     * The child [infoAsks] was built for. Questions are per child, and the answers are written to
     * whichever child is selected now — so a leftover list from the previous child would file one
     * child's answer under another. Anything that uses [infoAsks] must check this first.
     */
    val infoAskChildId: String? = null,
    val infoAskSaving: Boolean = false,
    /**
     * Open questions per child id, for the red badge in the child switcher. Counted from each
     * child's own row, so switching child is not needed to learn that they owe answers.
     */
    val childQuestionCounts: Map<String, Int> = emptyMap(),
    // --- Stitch "AI Suggestions & Guidance" (#11) ---
    val aiGuidance: AiGuidance? = null,
    val aiGuidanceLoading: Boolean = false,
    /** Active child: creator or member role `owner` — may delete child data. */
    val isChildOwner: Boolean = false,
    /** Child ids this account may delete (for multi-child switcher). */
    val ownedChildIds: Set<String> = emptySet(),
    /** Child eligible for ownership reclaim on re-registration/login. */
    val reclaimableChild: com.lullabyai.app.data.model.ReclaimableChild? = null,
    val requireRegistrationPrompt: String? = null,
    val error: String? = null
)

/** Parent inbox item — doctor pushed vaccine verification (not a parent-initiated request). */
data class DoctorVaccineInboxItem(
    val requestId: String,
    val childId: String,
    val childName: String,
    val vaccineName: String,
    val message: String?
)

/** One suggestion card on the AI Suggestions & Guidance screen.
 *  [iconKey] is one of nutrition | social | motor | sleep | health — the screen maps it to an icon. */
data class AiGuidanceCard(
    val iconKey: String,
    val title: String,
    val category: String,
    val body: String
)

/** Parsed payload for the AI Suggestions & Guidance screen. Not persisted — regenerated per visit. */
data class AiGuidance(
    val headline: String,
    val summary: String,
    val cards: List<AiGuidanceCard>,
    val tip: String?,
    val insight: String?
)

class ChildViewModel : ViewModel() {
    companion object {
        const val OWNER_ONLY_DELETE_ERROR = "owner_only_delete"
    }

    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val doctorVisitRepo = com.lullabyai.app.data.repository.DoctorVisitRepository()
    private val medicationRepo = com.lullabyai.app.data.repository.MedicationRepository()
    private val sleepRepo = com.lullabyai.app.data.repository.SleepRepository()
    private val nutritionRepo = com.lullabyai.app.data.repository.NutritionRepository()
    private val authRepo = com.lullabyai.app.data.repository.AuthRepository()
    private val aiRepo = AiRepository()
    private val chatRepo = com.lullabyai.app.data.repository.ChatRepository()
    private val notifRepo = com.lullabyai.app.data.repository.NotificationRepository()
    private val doctorRepo = com.lullabyai.app.data.repository.DoctorRepository()

    private val _uiState = MutableStateFlow(ChildUiState())
    val uiState: StateFlow<ChildUiState> = _uiState
    private var lastInfoAskEvalMs: Long = 0L

    init { loadChild() }

    fun isGuest(): Boolean = _uiState.value.child?.userId == "guest" || _uiState.value.child?.id == "guest_child_local"

    fun promptRegistration(message: String? = null) {
        _uiState.value = _uiState.value.copy(
            requireRegistrationPrompt = message ?: "กรุณาสมัครสมาชิกหรือเข้าสู่ระบบเพื่อบันทึกข้อมูลและใช้งานฟีเจอร์นี้"
        )
    }

    fun dismissRegistrationPrompt() {
        _uiState.value = _uiState.value.copy(requireRegistrationPrompt = null)
    }

    fun initGuestChild() {
        val today = java.time.LocalDate.now()
        val birthDate = today.minusMonths(3).toString()
        val guestChild = Child(
            id = "guest_child_local",
            userId = "guest",
            name = "Test",
            birthdate = birthDate,
            gender = "unspecified",
            noKnownAllergies = true,
            allergies = "ไม่มีประวัติการแพ้"
        )
        val guestOwnerFlags = setOf(guestChild.id)
        val sampleMilestones = childRepo.defaultMilestones(guestChild.id)
        val birth = today.minusMonths(3)
        val sampleVaccines = com.lullabyai.app.data.VaccineSetupCatalog.SCHEDULE.map { dose ->
            val due = birth.plusMonths(dose.ageMonths.toLong())
            val status = when {
                due.isBefore(today) -> "overdue"
                due.isBefore(today.plusMonths(2)) -> "appointment"
                else -> "upcoming"
            }
            VaccineRecord(
                id = "guest_vac_${dose.dbName}_${dose.ageMonths}",
                childId = guestChild.id,
                vaccineName = dose.dbName,
                description = dose.description,
                ageGroup = dose.ageGroup,
                ageOrder = dose.ageMonths,
                scheduledDate = due.toString(),
                dateGiven = null,
                status = status
            )
        }

        _uiState.value = _uiState.value.copy(
            children = listOf(guestChild),
            child = guestChild,
            childAvatarSignedUrl = null,
            isLoading = false,
            error = null,
            ownedChildIds = guestOwnerFlags,
            isChildOwner = true,
            vaccines = sampleVaccines,
            growthRecords = emptyList(),
            healthRecords = emptyList(),
            symptomActivityByRecord = emptyMap(),
            symptomUpdateByRecord = emptyMap(),
            allergies = emptyList(),
            chronicConditions = emptyList(),
            milestones = sampleMilestones,
            medicalDocuments = emptyList(),
            sleepLogs = emptyList(),
            sleepSchedules = emptyList(),
            doctorVisits = emptyList(),
            dailyQuestion = null,
            infoAsks = emptyList(),
            pendingSyncRequests = emptyList(),
            doctorVaccineInboxPopup = null
        )
        com.lullabyai.app.data.settings.ChildSelection.set(guestChild.id)
    }

    fun loadChild() {
        if (_uiState.value.child?.userId == "guest" && supabase.auth.currentUserOrNull() == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = null)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val childrenResult = childRepo.getChildren()
            // A fetch failure (network blip, transient RLS/auth hiccup) must NOT be treated as "this
            // account has zero children" — that was collapsing into the same empty state as a
            // genuinely childless account, so the "please add a child" prompt kept firing for users
            // who already had one, on every screen re-visit. Keep whatever children list we already
            // had and surface the real error instead of wiping it to empty.
            if (childrenResult.isFailure) {
                if (_uiState.value.child?.userId == "guest") {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                    return@launch
                }
                _uiState.value = _uiState.value.copy(isLoading = false, error = childrenResult.exceptionOrNull()?.message)
                return@launch
            }
            val children = childrenResult.getOrDefault(emptyList())
            childRepo.getSelectedChild()
                .onSuccess { child ->
                    val ownerFlags = resolveOwnerFlags(children)
                    if (child == null) {
                        _uiState.value = clearedChildDataState(
                            children = children,
                            ownedChildIds = ownerFlags,
                            isLoading = false
                        )
                        return@launch
                    }
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(child.avatarUrl)
                    val favorites = childRepo.childFavorites(child.id).getOrDefault(emptyList())
                    _uiState.value = _uiState.value.copy(
                        children = children,
                        child = child,
                        childAvatarSignedUrl = signed,
                        favorites = favorites,
                        isLoading = false,
                        error = null,
                        ownedChildIds = ownerFlags,
                        isChildOwner = child.id in ownerFlags
                    )
                    loadAllData(child.id)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            checkReclaimable()
        }
    }

    /** Checks if any child has previous owner email matching the signed-in user's email. */
    fun checkReclaimable() {
        viewModelScope.launch {
            val reclaimable = childRepo.checkReclaimableChildren().getOrDefault(emptyList()).firstOrNull()
            _uiState.update { it.copy(reclaimableChild = reclaimable) }
        }
    }

    fun dismissReclaim() {
        _uiState.update { it.copy(reclaimableChild = null) }
    }

    /** Reclaims ownership of [childId], demotes interim owner back to old role, and restores caller as owner. */
    fun reclaimOwnership(childId: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            childRepo.reclaimChildOwnership(childId)
                .onSuccess {
                    _uiState.update { it.copy(reclaimableChild = null) }
                    loadChild()
                    onComplete?.invoke(true)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                    onComplete?.invoke(false)
                }
        }
    }

    /** Wipe per-child cached data so stale vaccines/notifications from a prior session can't show
     *  when this account currently has zero children (or the selected child was removed). */
    private fun clearedChildDataState(
        children: List<Child>,
        ownedChildIds: Set<String>,
        isLoading: Boolean
    ) = _uiState.value.copy(
        children = children,
        child = null,
        childAvatarSignedUrl = null,
        isLoading = isLoading,
        error = null,
        ownedChildIds = ownedChildIds,
        isChildOwner = false,
        vaccines = emptyList(),
        growthRecords = emptyList(),
        healthRecords = emptyList(),
        symptomActivityByRecord = emptyMap(),
        symptomUpdateByRecord = emptyMap(),
        allergies = emptyList(),
        chronicConditions = emptyList(),
        milestones = emptyList(),
        medicalDocuments = emptyList(),
        sleepLogs = emptyList(),
        sleepSchedules = emptyList(),
        doctorVisits = emptyList(),
        dailyQuestion = null,
        infoAsks = emptyList(),
        pendingSyncRequests = emptyList(),
        doctorVaccineInboxPopup = null
    )

    // --- Sync child (multi-parent access) ---------------------------------------------------

    fun searchChildForSync(name: String, birthdate: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(syncSearching = true, syncError = null, syncSearchResults = emptyList())
            childRepo.searchChildForSync(name, birthdate)
                .onSuccess { _uiState.value = _uiState.value.copy(syncSearching = false, syncSearchResults = it) }
                .onFailure { _uiState.value = _uiState.value.copy(syncSearching = false, syncError = AccessRequestViewModel.safeError(it)) }
        }
    }

    fun requestSyncBySearch(childId: String, role: String? = null) {
        viewModelScope.launch {
            childRepo.requestSyncBySearch(childId, role)
                .onSuccess { _uiState.value = _uiState.value.copy(syncRequestSent = true, syncError = null) }
                .onFailure { _uiState.value = _uiState.value.copy(syncError = AccessRequestViewModel.safeError(it)) }
        }
    }

    fun requestSyncByCode(code: String, role: String? = null, method: String = "code") {
        viewModelScope.launch {
            childRepo.requestSyncByCode(code, role = role, method = method)
                .onSuccess { _uiState.value = _uiState.value.copy(syncRequestSent = true, syncError = null) }
                .onFailure { _uiState.value = _uiState.value.copy(syncError = AccessRequestViewModel.safeError(it)) }
        }
    }

    fun generateInviteCode(childId: String) {
        val childName = _uiState.value.children.find { it.id == childId }?.name
            ?: _uiState.value.child?.name
        viewModelScope.launch {
            childRepo.generateInviteCode(childId)
                .onSuccess { _uiState.value = _uiState.value.copy(inviteCode = it, inviteCodeChildName = childName) }
                .onFailure { _uiState.value = _uiState.value.copy(syncError = AccessRequestViewModel.safeError(it)) }
        }
    }

    fun clearSyncState() {
        _uiState.value = _uiState.value.copy(
            syncSearching = false, syncSearchResults = emptyList(), syncRequestSent = false,
            syncError = null, inviteCode = null, inviteCodeChildName = null
        )
    }

    /** Dismisses the owner-side sync error banner. Does not un-handle the request. */
    fun clearSyncError() {
        _uiState.value = _uiState.value.copy(syncError = null)
    }

    fun loadPendingSyncRequests(context: android.content.Context) {
        viewModelScope.launch {
            val snoozed = runCatching {
                com.lullabyai.app.data.settings.SettingsManager(context).snoozedSyncRequestIds()
            }.getOrDefault(emptySet())
            childRepo.getPendingSyncRequests()
                .onSuccess { fresh ->
                    // Hidden if answered this session OR snoozed on disk. The disk half is what
                    // survives a refresh: the row can still be `pending` server-side after the
                    // owner answers, and re-showing it every reload is the bug being fixed.
                    val hidden = _uiState.value.handledSyncIds + snoozed
                    _uiState.value = _uiState.value.copy(
                        pendingSyncRequests = fresh.filterNot { it.id in hidden }
                    )
                }
        }
    }

    /**
     * The optimistic removal below hides the row before the server has agreed to anything, so a
     * failed RPC MUST be reported. This previously had only an `onSuccess` branch: when
     * `confirm_child_sync_request` raised ('not authorized', 'already resolved'), the row vanished
     * from the list while staying `pending` in Postgres, and AccessRequestViewModel's 4s poll
     * re-raised the popup seconds later — the "pressed Deny, it keeps coming back" report.
     */
    fun confirmSyncRequest(context: android.content.Context, requestId: String, approve: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                pendingSyncRequests = _uiState.value.pendingSyncRequests.filterNot { it.id == requestId },
                handledSyncIds = _uiState.value.handledSyncIds + requestId,
                syncError = null
            )
            // Snooze before the call, not after: the point is to stop the card returning whatever
            // the server says, including when the RPC refuses. The error banner is what tells the
            // owner a refusal happened; the two-hour expiry is what stops a genuinely unresolved
            // request from being lost.
            runCatching {
                com.lullabyai.app.data.settings.SettingsManager(context).snoozeSyncRequest(requestId)
            }
            childRepo.confirmSyncRequest(requestId, approve)
                .onSuccess { loadPendingSyncRequests(context) }
                .onFailure { e ->
                    // safeError, never e.message: raw RPC text can carry the child id, the
                    // requester's user id or the invite token, none of which belong on screen.
                    // Re-read after, so the row returns to the list honestly rather than the owner
                    // learning of the failure only when the popup comes back.
                    _uiState.value = _uiState.value.copy(
                        syncError = AccessRequestViewModel.safeError(e)
                    )
                    loadPendingSyncRequests(context)
                }
        }
    }

    // --- Realtime sync (co-parent devices + post-approval membership) -----------------------

    private var childDataWatchJob: Job? = null
    private var membershipWatchJob: Job? = null

    /** Watch for newly granted membership (QR/code sync approved) and reload the children list. */
    fun startMembershipWatch() {
        if (membershipWatchJob?.isActive == true) return
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        membershipWatchJob = viewModelScope.launch {
            launch {
                while (true) {
                    delay(5_000)
                    loadChild()
                }
            }
            runCatching {
                childRepo.watchMembershipGranted(uid).collect { loadChild() }
            }
        }
    }

    /** Reload shared child data whenever another device writes to Supabase for this child. */
    fun startChildDataWatch(childId: String) {
        if (childDataWatchJob?.isActive == true && watchedChildId == childId) return
        childDataWatchJob?.cancel()
        watchedChildId = childId
        childDataWatchJob = viewModelScope.launch {
            childRepo.watchChildDataChanges(childId).collect { loadAllData(childId) }
        }
    }

    fun stopRealtimeSync() {
        childDataWatchJob?.cancel()
        membershipWatchJob?.cancel()
        watchedChildId = null
    }

    private var watchedChildId: String? = null

    private suspend fun resolveOwnerFlags(children: List<Child>): Set<String> = coroutineScope {
        children.map { child ->
            async {
                if (childRepo.isChildOwner(child.id).getOrDefault(false)) child.id else null
            }
        }.mapNotNull { it.await() }.toSet()
    }

    private suspend fun ensureChildOwner(childId: String? = _uiState.value.child?.id): Boolean {
        val id = childId?.takeIf { it.isNotBlank() } ?: return false
        if (id in _uiState.value.ownedChildIds) return true
        if (!childRepo.isChildOwner(id).getOrDefault(false)) {
            _uiState.value = _uiState.value.copy(error = OWNER_ONLY_DELETE_ERROR)
            return false
        }
        val owned = _uiState.value.ownedChildIds + id
        _uiState.value = _uiState.value.copy(
            ownedChildIds = owned,
            isChildOwner = _uiState.value.child?.id == id
        )
        return true
    }

    /** Owner or member with `book_doctor` capability may open Pedia Connect / book appointments. */
    suspend fun canAccessDoctorConnect(childId: String?): Boolean {
        val id = childId?.takeIf { it.isNotBlank() }
            ?: childRepo.getSelectedChild().getOrNull()?.id
            ?: return false
        val uid = supabase.auth.currentUserOrNull()?.id ?: return false
        val child = _uiState.value.children.find { it.id == id }
            ?: childRepo.getChildById(id).getOrNull()
            ?: return false
        if (child.userId == uid) return true
        val member = childRepo.getMyMembership(id).getOrNull() ?: return false
        return AccessCapabilities.holds(member, AccessCapabilities.BOOK_DOCTOR) ||
            child.userId == uid
    }

    override fun onCleared() {
        stopRealtimeSync()
        super.onCleared()
    }

    /** Doctor → parent vaccine verification deliveries (popup on home). */
    fun loadDoctorVaccineInbox(context: android.content.Context) {
        // Temporarily cancelled/disabled per user request
    }

    fun dismissDoctorVaccineInbox(context: android.content.Context, openVaccineBook: Boolean = false) {
        val popup = _uiState.value.doctorVaccineInboxPopup ?: return
        viewModelScope.launch {
            com.lullabyai.app.data.settings.SettingsManager(context).dismissDoctorVaccineInboxId(popup.requestId)
            _uiState.value = _uiState.value.copy(doctorVaccineInboxPopup = null)
            if (openVaccineBook) {
                // Vaccine list reload happens when parent opens the book screen.
            }
        }
    }

    // --- Home info-ask (typed catalog; replaces the daily-question popup) ---------------------

    /** Surfaces every currently open gap on Home / Child Info as one mixed form. */
    fun loadDailyQuestion(context: android.content.Context, isThai: Boolean) = loadInfoAsk(context, isThai)

    fun loadInfoAsk(context: android.content.Context, isThai: Boolean) {
        if (isGuest()) return
        val state = _uiState.value
        val now = System.currentTimeMillis()
        if (!com.lullabyai.app.data.infoask.InfoAskCatalog.needsReload(
                selectedChildId = state.child?.id,
                loadedForChildId = state.infoAskChildId,
                loaded = state.infoAsks.size,
                lastEvalMs = lastInfoAskEvalMs,
                nowMs = now
            )
        ) return
        viewModelScope.launch { loadInfoAskInternal(context, isThai) }
    }

    /**
     * Counts the open questions for every child at once, so the switcher can flag the children the
     * parent is not currently looking at. Reads the loaded child rows and local answer history
     * only — no round trip per child.
     */
    fun loadChildQuestionBadges(context: android.content.Context) {
        if (isGuest()) return
        val children = _uiState.value.children
        if (children.isEmpty()) return
        viewModelScope.launch {
            val settings = com.lullabyai.app.data.settings.SettingsManager(context)
            val counts = children.associate { child ->
                child.id to com.lullabyai.app.data.infoask.InfoAskCatalog.openChildRowCount(
                    child = child,
                    answeredEver = settings.infoAskAnsweredFieldIds(child.id)
                )
            }
            _uiState.value = _uiState.value.copy(childQuestionCounts = counts)
        }
    }

    private suspend fun loadInfoAskInternal(context: android.content.Context, isThai: Boolean) {
        val child = _uiState.value.child ?: return
        val settings = com.lullabyai.app.data.settings.SettingsManager(context)
        val from = java.time.OffsetDateTime.now().minusDays(7)
        val to = java.time.OffsetDateTime.now().plusDays(1)
        val snapshot = com.lullabyai.app.data.infoask.InfoAskSnapshot(
            child = child,
            profile = authRepo.getProfile().getOrNull(),
            authEmail = supabase.auth.currentUserOrNull()?.email,
            sleepLogs = _uiState.value.sleepLogs.ifEmpty { sleepRepo.getLogs(child.id).getOrDefault(emptyList()) },
            sleepSchedules = _uiState.value.sleepSchedules.ifEmpty { sleepRepo.getSchedules(child.id).getOrDefault(emptyList()) },
            milkLogs = nutritionRepo.getMilkLogs(child.id),
            activities = childRepo.listActivities(child.id, from.toString(), to.toString()).getOrDefault(emptyList()),
            visits = _uiState.value.doctorVisits.ifEmpty { doctorVisitRepo.getVisits(child.id).getOrDefault(emptyList()) },
            shownToday = settings.infoAskShownFieldIdsToday(child.id),
            answeredEver = settings.infoAskAnsweredFieldIds(child.id)
        )
        val catalog = com.lullabyai.app.data.infoask.InfoAskCatalog
        lastInfoAskEvalMs = System.currentTimeMillis()
        var prompts = catalog.openAll(snapshot)
        if (prompts.isEmpty()) {
            _uiState.value = _uiState.value.copy(infoAsks = emptyList(), infoAskChildId = child.id, dailyQuestion = null)
            return
        }
        val dripped = catalog.drip(prompts, settings.infoAskDrip(child.id), lastInfoAskEvalMs)
        prompts = dripped.first
        settings.saveInfoAskDrip(child.id, dripped.second)
        if (prompts.isEmpty()) {
            _uiState.value = _uiState.value.copy(infoAsks = emptyList(), infoAskChildId = child.id, dailyQuestion = null)
            return
        }
        if (prompts.size == 1 && prompts.first().fieldId == "open_notes") {
            chatRepo.generateDailyQuestion(child, isThai).getOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
                prompts = listOf(prompts.first().copy(questionTh = q, questionEn = q))
            }
        }
        _uiState.value = _uiState.value.copy(infoAsks = prompts, infoAskChildId = child.id, dailyQuestion = null)
    }

    /**
     * Writes every filled answer into the repo that owns that field. Unfilled questions stay open
     * so the banner can come back the same day. Structured fields never dump into notes; only
     * `open_notes` (and nap=no) append a dated Q/A line.
     */
    fun answerInfoAsk(
        context: android.content.Context,
        answers: List<com.lullabyai.app.data.infoask.InfoAskAnswer>,
        isThai: Boolean = true,
        onDone: () -> Unit = {}
    ) {
        val prompts = _uiState.value.infoAsks
        if (prompts.isEmpty()) return
        // The questions on screen must belong to the child they will be saved against. If the child
        // was switched underneath them, drop the answers and re-ask rather than file them wrongly.
        val selectedChildId = _uiState.value.child?.id
        if (selectedChildId == null || _uiState.value.infoAskChildId != selectedChildId) {
            _uiState.value = _uiState.value.copy(infoAsks = emptyList(), infoAskChildId = null, dailyQuestion = null)
            viewModelScope.launch { loadInfoAskInternal(context, isThai) }
            return
        }
        val catalog = com.lullabyai.app.data.infoask.InfoAskCatalog
        val byId = prompts.associateBy { it.fieldId }
        val filled = answers.filter { a -> byId[a.fieldId]?.let { catalog.isFilled(it, a) } == true }
        if (filled.isEmpty()) return
        if (isGuest()) { promptRegistration(); onDone(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(infoAskSaving = true, error = null)
            val settings = com.lullabyai.app.data.settings.SettingsManager(context)
            var firstError: String? = null
            for (answer in filled) {
                val prompt = byId[answer.fieldId] ?: continue
                val result = runCatching { writeInfoAskAnswer(settings, prompt, answer) }
                if (result.isFailure) {
                    firstError = result.exceptionOrNull()?.message
                    break
                }
            }
            if (firstError != null) {
                _uiState.value = _uiState.value.copy(infoAskSaving = false, error = firstError)
                return@launch
            }
            _uiState.value = _uiState.value.copy(infoAsks = emptyList(), infoAskChildId = null, dailyQuestion = null, infoAskSaving = false)
            loadChild()
            loadInfoAskInternal(context, isThai)
            // The switcher badge counts answers, so it has to drop the moment one is written.
            loadChildQuestionBadges(context)
            onDone()
        }
    }

    private suspend fun writeInfoAskAnswer(
        settings: com.lullabyai.app.data.settings.SettingsManager,
        prompt: com.lullabyai.app.data.infoask.InfoAskPrompt,
        answer: com.lullabyai.app.data.infoask.InfoAskAnswer
    ) {
        val child = _uiState.value.child ?: return
        val chip = answer.chipId?.takeIf { it.isNotBlank() }
        val value = answer.text.trim()
        val catalog = com.lullabyai.app.data.infoask.InfoAskCatalog
        when (prompt.fieldId) {
            "birthdate" -> patchChild(child.id, mapOf("birthdate" to value), child.copy(birthdate = value))
            "delivery_type" -> patchChild(child.id, mapOf("delivery_type" to chip), child.copy(deliveryType = chip))
            "place_of_birth" -> patchChild(child.id, mapOf("place_of_birth" to value), child.copy(placeOfBirth = value))
            "gestational_weeks" -> {
                val weeks = catalog.parsePositiveNumber(value)?.toInt() ?: error("invalid")
                patchChild(child.id, mapOf("gestational_weeks" to weeks), child.copy(gestationalWeeks = weeks))
            }
            "birth_weight_g" -> {
                val g = catalog.parseBirthWeightG(value) ?: error("invalid")
                patchChild(child.id, mapOf("birth_weight_g" to g), child.copy(birthWeightG = g))
            }
            "birth_length_cm" -> {
                val n = catalog.parsePositiveNumber(value) ?: error("invalid")
                patchChild(child.id, mapOf("birth_length_cm" to n), child.copy(birthLengthCm = n))
            }
            "birth_head_cm" -> {
                val n = catalog.parsePositiveNumber(value) ?: error("invalid")
                patchChild(child.id, mapOf("birth_head_cm" to n), child.copy(birthHeadCm = n))
            }
            "blood_group" -> {
                val stored = if (chip == "unknown") "unknown" else chip
                patchChild(child.id, mapOf("blood_group" to stored), child.copy(bloodGroup = stored))
            }
            "allergies" -> {
                val isNone = value.contains("ไม่มี") || value.contains("ไม่แพ้") || value.contains("none", ignoreCase = true)
                if (isNone) {
                    patchChild(child.id, mapOf("no_known_allergies" to true), child.copy(noKnownAllergies = true))
                } else {
                    val name = value.ifBlank { chip ?: "" }
                    if (name.isNotBlank()) {
                        healthRepo.addAllergy(child.id, name, "food", "mild", symptoms = null)
                        patchChild(child.id, mapOf("allergies" to name), child.copy(allergies = name))
                        refreshHealthRecords(child.id)
                    }
                }
            }
            // Favourites are rows in their own table, like allergies above — the answer becomes one
            // item tagged info_ask, so the parent can see later which ones the banner collected.
            "favorite_toy" -> addFavoriteFromAsk(child.id, FavoriteCategory.TOY, value)
            "favorite_food" -> addFavoriteFromAsk(child.id, FavoriteCategory.FOOD, value)
            "favorite_sound" -> addFavoriteFromAsk(child.id, FavoriteCategory.SOUND, value)
            "favorite_activity" -> addFavoriteFromAsk(child.id, FavoriteCategory.ACTIVITY, value)
            "favorite_soothing" -> addFavoriteFromAsk(child.id, FavoriteCategory.SOOTHING, value)
            "guardian_name" -> authRepo.updateProfile(null, value, null).getOrThrow()
            "guardian_relation" -> authRepo.updateProfile(null, null, null, relationship = chip).getOrThrow()
            "guardian_phone" -> {
                val phone = com.lullabyai.app.data.repository.ThaiPhone.normalize(value) ?: value
                authRepo.updateProfile(null, null, null, phone = phone).getOrThrow()
            }
            "guardian_address" -> authRepo.updateProfile(null, null, null, address = value).getOrThrow()
            "sleep_last_night" -> {
                val stars = chip!!.toIntOrNull()?.coerceIn(1, 5) ?: 3
                val end = java.time.LocalDateTime.now().withHour(7).withMinute(0).withSecond(0).withNano(0)
                val start = end.minusHours(10)
                sleepRepo.addManualLog(child.id, "main", start.toString(), end.toString(), qualityStars = stars).getOrThrow()
                loadSleep()
            }
            "typical_bedtime" -> {
                val start = chip ?: value
                sleepRepo.createSchedule(child.id, "นอนหลัก", "main", start, "06:00", "ALL").getOrThrow()
            }
            "nap_today" -> {
                if (chip == "yes") {
                    val start = java.time.LocalDateTime.now().minusHours(2).withSecond(0).withNano(0)
                    val end = start.plusMinutes(90)
                    sleepRepo.addManualLog(child.id, "nap", start.toString(), end.toString()).getOrThrow()
                    loadSleep()
                } else {
                    appendNotes(child, prompt.question(true), "ไม่ได้นอนกลางวัน")
                }
            }
            "activity_level" -> {
                patchChild(child.id, mapOf("activity_level" to chip), child.copy(activityLevel = chip ?: "moderate"))
                settings.recordInfoAskFieldAnswered(child.id, "activity_level")
            }
            "play_today" -> {
                val title = prompt.chips.find { it.id == chip }?.labelTh ?: "กิจกรรม"
                val now = java.time.OffsetDateTime.now()
                childRepo.addActivity(
                    childId = child.id,
                    title = title,
                    category = "family",
                    startsAt = now.toString(),
                    allDay = true
                ).getOrThrow()
            }
            "feeding_type" -> {
                when (chip) {
                    "both" -> {
                        nutritionRepo.addMilkLog(child.id, "breast", 0, null, "จากแบบถามข้อมูล").getOrThrow()
                        nutritionRepo.addMilkLog(child.id, "formula", 0, null, "จากแบบถามข้อมูล").getOrThrow()
                    }
                    else -> nutritionRepo.addMilkLog(child.id, chip ?: "formula", 0, null, "จากแบบถามข้อมูล").getOrThrow()
                }
            }
            "recent_facility" -> {
                val whenIso = com.lullabyai.app.util.TimeUtils.toUtcIso(java.time.LocalDateTime.now())
                doctorVisitRepo.saveVisit(
                    id = null, childId = child.id, visitType = "completed", visitDatetime = whenIso,
                    facilityName = value, doctorName = null, doctorSpecialty = null, reason = null,
                    symptoms = null, diagnosis = null, treatmentPlan = null, doctorAdvice = null,
                    weightKg = null, heightCm = null, temperatureC = null, bloodPressure = null,
                    nextAppointmentDate = null, preparationNotes = null, reminders = null,
                    status = "completed"
                ).getOrThrow()
                loadDoctorVisits()
            }
            "mood_after_sleep" -> {
                val latest = _uiState.value.sleepLogs.firstOrNull()
                if (latest != null && latest.id.isNotBlank()) {
                    sleepRepo.updateMood(latest.id, chip!!).getOrThrow()
                } else {
                    val start = java.time.LocalDateTime.now().minusHours(10)
                    val end = java.time.LocalDateTime.now()
                    sleepRepo.addManualLog(child.id, "main", start.toString(), end.toString(), mood = chip).getOrThrow()
                }
                loadSleep()
            }
            "open_notes" -> appendNotes(child, prompt.question(true), value)
            else -> appendNotes(child, prompt.question(true), value.ifBlank { chip ?: "" })
        }
    }

    private suspend fun patchChild(childId: String, updates: Map<String, Any?>, local: com.lullabyai.app.data.model.Child) {
        childRepo.updateChild(childId, updates).getOrThrow()
        _uiState.value = _uiState.value.copy(
            child = local,
            children = _uiState.value.children.map { if (it.id == local.id) local else it }
        )
    }

    private suspend fun appendNotes(child: com.lullabyai.app.data.model.Child, question: String, answer: String) {
        val entry = "[${java.time.LocalDate.now()}] Q: $question A: $answer"
        val newNotes = (child.notes?.takeIf { it.isNotBlank() }?.plus("\n") ?: "") + entry
        childRepo.updateChildNotes(child.id, newNotes).getOrThrow()
        _uiState.value = _uiState.value.copy(child = child.copy(notes = newNotes))
    }

    fun dismissDailyQuestion(context: android.content.Context? = null) = dismissInfoAsk(context)

    /** Hide the form for this visit. Unanswered questions stay on the stack and come back. */
    fun dismissInfoAsk(context: android.content.Context? = null) {
        _uiState.value = _uiState.value.copy(infoAsks = emptyList(), dailyQuestion = null, infoAskSaving = false)
    }

    /**
     * Builds a doctor-export PDF for the active child (profile, growth, allergies, vaccines, health
     * records, milestones, and an AI summary of recent parent concerns) and returns the File so the
     * screen can share it. Off the main thread; uses already-loaded uiState plus a couple of fetches.
     */
    fun exportDoctorReport(
        context: android.content.Context, isThai: Boolean,
        categories: Set<String> = DoctorReportGenerator.ALL_CATEGORIES,
        periodMonths: Int? = null, detailed: Boolean = true,
        onResult: (java.io.File?, String?) -> Unit
    ) {
        val st = _uiState.value
        val child = st.child ?: run { onResult(null, "no child selected"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reportLoading = true)
            val result = runCatching {
                val med = childRepo.getMedicalHistory(child.id).getOrNull()
                val concerns = runCatching {
                    val sessions = chatRepo.getSessions().getOrDefault(emptyList())
                    val msgs = sessions.firstOrNull()?.let { chatRepo.getMessages(it.id).getOrDefault(emptyList()) } ?: emptyList()
                    msgs.filter { it.role == "user" }.takeLast(12).joinToString("\n") { it.content }
                }.getOrDefault("")

                val achieved = st.milestones.count { it.isAchieved }
                val pending = st.milestones.filter { !it.isAchieved }

                // AI summary for the doctor
                val aiPrompt = buildString {
                    append(if (isThai) "สรุปภาพรวมสุขภาพและพัฒนาการของเด็กแบบกระชับเป็นภาษาไทยสำหรับแพทย์ 3-5 ประโยค จากข้อมูลนี้ และระบุข้อกังวลของผู้ปกครองหากมี\n"
                           else "Summarize this child's health and development concisely for a doctor in 3-5 sentences, noting parent concerns if any.\n")
                    val genderForAi = when (child.gender.lowercase()) { "male","boy","m","ชาย" -> "ชาย"; "female","girl","f","หญิง" -> "หญิง"; else -> "ไม่ระบุ" }
                    append("ชื่อ ${child.name}, อายุ ${calculateAge(isThai)}, เพศ $genderForAi\n")
                    append("ส่วนสูง ${child.heightCm ?: "-"} ซม., น้ำหนัก ${child.weightKg ?: "-"} กก.\n")
                    append("แพ้: ${st.allergies.joinToString(", ") { it.name }.ifBlank { "ไม่มีข้อมูล" }}\n")
                    append("โรคประจำตัว: ${med?.underlyingConditions ?: "ไม่มีข้อมูล"}\n")
                    append("พัฒนาการ: ทำได้ $achieved/${st.milestones.size}\n")
                    if (concerns.isNotBlank()) append("ข้อกังวลของผู้ปกครองจากการสนทนา:\n$concerns\n")
                    append(if (isThai) "ตอบเป็นย่อหน้าเดียว ไม่ใช้สัญลักษณ์พิเศษ" else "Reply as one paragraph, no special symbols.")
                }
                val aiSummary = runCatching {
                    com.lullabyai.app.data.remote.ClaudeClient.generateContent(aiPrompt, useSearch = false, maxTokens = 800)
                }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("ขออภัย") }
                    ?: (if (isThai) "ไม่สามารถสร้างบทสรุป AI ได้ในขณะนี้" else "AI summary unavailable.")

                // ----- Map the child's data onto the official "ข้อมูลเด็ก" form (Thai, 6 sections) -----
                fun d10(s: String?) = com.lullabyai.app.util.TimeUtils.localDate(s) ?: ""   // ISO → local yyyy-MM-dd
                val today = java.time.LocalDate.now()
                val reportId = "NR-" + child.id.replace("-", "").take(8).uppercase() + "-" + today.toString().replace("-", "")
                val recordDate = "%02d/%02d/%04d".format(today.dayOfMonth, today.monthValue, today.year + 543)
                val genderTh = when (child.gender.lowercase()) {
                    "male", "boy", "m", "ชาย" -> "ชาย"
                    "female", "girl", "f", "หญิง" -> "หญิง"
                    else -> "ไม่ระบุ"
                }

                // Child age in whole months (for development "due" checks).
                val birth = runCatching { java.time.LocalDate.parse(child.birthdate) }.getOrNull()
                val ageMonths = birth?.let { val pp = java.time.Period.between(it, today); pp.years * 12 + pp.months } ?: Int.MAX_VALUE

                // Period filter (null = all-time). Applied to date-stamped rows (vaccines, growth);
                // ponytail: "Custom" range from the export screen currently maps to all-time too —
                // add a real date-range picker if users need a specific arbitrary window.
                val periodCutoff = periodMonths?.let { today.minusMonths(it.toLong()) }
                fun inPeriod(dateStr: String?): Boolean {
                    if (periodCutoff == null) return true
                    val d = runCatching { java.time.LocalDate.parse(dateStr) }.getOrNull() ?: return true
                    return !d.isBefore(periodCutoff)
                }

                // §2 vaccines actually received (completed) — the export screen labels this
                // category "Vaccination Book (ALL)": deliberately exempt from the period filter,
                // unlike growth/nutrition rows below. A parent needs the full vaccination history
                // in every export, not just whatever happened in the last 3-6 months.
                val vacRows = st.vaccines.filter { it.status == "completed" }
                    .sortedBy { it.ageOrder ?: 0 }
                    .map {
                        DoctorReportGenerator.Vac(
                            it.ageGroup ?: "", it.vaccineName, d10(it.dateGiven), "",
                            it.notes ?: it.description ?: "",
                            fromMophBook = it.source == "moph_book",
                            certified = !it.certificateUrl.isNullOrBlank()
                        )
                    }

                // §3 development — one status row per official domain (mapped from milestone categories).
                val domainCats = listOf("gross_motor", "fine_motor", "communication", "social", "self_help")
                val devStatuses = domainCats.map { cat ->
                    val inDomain = st.milestones.filter { it.category == cat }
                    val due = inDomain.filter { (it.ageMonthsExpected ?: Int.MAX_VALUE) <= ageMonths }
                    val pendingDue = due.filter { !it.isAchieved }
                    val hasData = inDomain.isNotEmpty()
                    val monitor = pendingDue.isNotEmpty()
                    DoctorReportGenerator.DevStatus(
                        normal = hasData && !monitor,
                        monitor = monitor,
                        note = if (monitor) "ควรติดตาม ${pendingDue.size} รายการ" else ""
                    )
                }
                val devObservation = pending.filter { (it.ageMonthsExpected ?: Int.MAX_VALUE) <= ageMonths }
                    .take(8).joinToString("\n") { "• ${it.milestoneName}" }

                // §4 growth history.
                val growthRows = st.growthRecords.filter { inPeriod(it.recordedAt) }.map {
                    DoctorReportGenerator.Growth(d10(it.recordedAt), it.weightKg?.let { w -> "$w" } ?: "", it.heightCm?.let { h -> "$h" } ?: "", "", it.notes ?: "")
                }
                val foodAllergies = st.allergies.filter { it.type == "food" }.joinToString(", ") { it.name }
                val allAllergies = st.allergies.joinToString(", ") {
                    it.name + (it.symptoms?.takeIf { sm -> sm.isNotBlank() }?.let { sm -> " ($sm)" } ?: "")
                }

                // §5 health-history detail = recent health records.
                val healthDetail = st.healthRecords.take(10).joinToString("\n") {
                    "${d10(it.recordedAt)}  ${it.title}" + (it.temperature?.let { t -> "  ${t}°C" } ?: "") +
                        (it.description?.takeIf { dsc -> dsc.isNotBlank() }?.let { dsc -> " — $dsc" } ?: "")
                }

                // §6 sleep + medication — tracked in-app (SleepTracker, Medication History) but
                // never reached this export before; fetched fresh rather than relying on whichever
                // screens happen to have already populated ChildUiState this session.
                val sleepLogs = com.lullabyai.app.data.repository.SleepRepository().getLogs(child.id).getOrDefault(emptyList())
                val weekAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7))
                val recentSleep = sleepLogs.filter { log ->
                    com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)?.isAfter(weekAgo) == true && log.endTime != null
                }
                val sleepSummary = if (recentSleep.isEmpty()) {
                    if (isThai) "ยังไม่มีบันทึกการนอนในช่วง 7 วันล่าสุด" else "No completed sleep sessions in the last 7 days"
                } else {
                    val totalMin = recentSleep.sumOf { log ->
                        val start = com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
                        val end = log.endTime?.let { com.lullabyai.app.util.TimeUtils.parseInstant(it) }
                        if (start != null && end != null) java.time.Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
                    }
                    val daysWithSleep = recentSleep.mapNotNull { com.lullabyai.app.util.TimeUtils.parseInstant(it.startTime)?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() }.distinct().size
                    if (isThai) "7 วันล่าสุด: นอน ${recentSleep.size} ครั้ง รวม ${totalMin / 60} ชม. ${totalMin % 60} นาที (มีบันทึก $daysWithSleep จาก 7 วัน) เฉลี่ย ${(totalMin / 7 / 60)} ชม./วัน"
                    else "Last 7 days: ${recentSleep.size} sessions, ${totalMin / 60}h ${totalMin % 60}m total (logged on $daysWithSleep of 7 days), avg ${(totalMin / 7 / 60)}h/day"
                }

                val medSchedules = com.lullabyai.app.data.repository.MedicationRepository().getSchedules(child.id).getOrDefault(emptyList())
                val medDoses = com.lullabyai.app.data.repository.MedicationRepository().getDoses(child.id).getOrDefault(emptyList())
                val medicationSummary = if (medSchedules.isEmpty()) {
                    if (isThai) "ไม่มีตารางการทานยาที่บันทึกไว้" else "No medication schedules on record"
                } else buildString {
                    medSchedules.forEach { sch ->
                        val schDoses = medDoses.filter { it.scheduleId == sch.id }
                        val taken = schDoses.count { it.status == "taken" }
                        val skipped = schDoses.count { it.status == "skipped" }
                        val total = schDoses.size
                        append(if (isThai) "${sch.medicineName} (${sch.dosage ?: ""} ${sch.dosageUnit})" else sch.medicineName)
                        if (total > 0) append(if (isThai) " — ทานแล้ว $taken/$total ครั้ง ข้าม $skipped ครั้ง" else " — taken $taken/$total, skipped $skipped")
                        append("\n")
                    }
                }.trim()

                // §7 recommendations = overdue vaccines + pending milestones (concrete follow-ups).
                val overdueVac = st.vaccines.filter { it.status == "overdue" }
                val recommendations = buildString {
                    if (overdueVac.isNotEmpty()) append("วัคซีนที่เลยกำหนด: " + overdueVac.joinToString(", ") { it.vaccineName } + "\n")
                    val devFollow = devStatuses.withIndex().filter { it.value.monitor }
                        .joinToString(", ") { DoctorReportGenerator.DEV_DOMAINS[it.index] }
                    if (devFollow.isNotBlank()) append("ด้านพัฒนาการที่ควรติดตาม: $devFollow")
                }.trim()

                val formData = DoctorReportGenerator.ChildData(
                    documentRef = reportId, recordDate = recordDate,
                    // §1
                    childCode = child.id.replace("-", "").take(8).uppercase(),
                    nickname = child.name, birthdate = child.birthdate, age = calculateAge(true),
                    gender = genderTh, idNumber = "",
                    nationality = child.nationality ?: "", ethnicity = child.ethnicity ?: "", religion = child.religion ?: "",
                    unitClass = "",
                    recorder = "ผู้ปกครอง", updatedDate = d10(child.updatedAt).ifBlank { today.toString() },
                    // §2
                    vaccines = vacRows,
                    // §3
                    development = devStatuses, devObservation = devObservation,
                    // §4
                    regularFood = "", allergyAvoid = foodAllergies, eatingProblem = "",
                    growth = growthRows, nutritionAdvice = "",
                    // §5
                    chronicDisease = med?.underlyingConditions ?: "",
                    drugFoodAllergy = allAllergies,
                    medications = med?.medicationDetails ?: "",
                    majorIllness = "", surgeryHospital = "",
                    followupDoctor = "",
                    healthHistoryDetail = healthDetail, currentCare = "",
                    // §6
                    sleepSummary = sleepSummary, medicationSummary = medicationSummary,
                    // §7
                    overallSummary = aiSummary, recommendations = recommendations,
                    // §8 — filename + upload date only (the files themselves aren't embedded).
                    medicalDocs = st.medicalDocuments.map { DoctorReportGenerator.MedDoc(it.fileName, d10(it.createdAt)) }
                )
                val isDemoUser = isGuest() || child.userId == "guest" || child.id == "guest_child_local"
                DoctorReportGenerator.generate(context, formData, categories, detailed, isDemo = isDemoUser)
            }
            result.exceptionOrNull()?.let { android.util.Log.e("DoctorReport", "export failed", it) }
            _uiState.value = _uiState.value.copy(reportLoading = false)
            onResult(result.getOrNull(), result.exceptionOrNull()?.message)
        }
    }

    /** Delete a child (after the user confirms). Falls back to another child, or none if it was the last. */
    fun deleteChild(childId: String) {
        viewModelScope.launch {
            if (!ensureChildOwner(childId)) return@launch
            childRepo.deleteChild(childId)
            // If the deleted child was active, clear the selection so the next load picks another.
            if (com.lullabyai.app.data.settings.ChildSelection.current() == childId) {
                com.lullabyai.app.data.settings.ChildSelection.set(null)
            }
            vaccineSeededChildId = null
            milestonesEnsuredChildId = null
            _uiState.value = _uiState.value.copy(
                child = null, vaccines = emptyList(), growthRecords = emptyList(),
                healthRecords = emptyList(), symptomActivityByRecord = emptyMap(), symptomUpdateByRecord = emptyMap(),
                healthHistoryInsight = null, sicknessFollowUpInsight = null,
                medicationHistoryInsight = null, doctorVisitHistoryInsight = null,
                allergies = emptyList(), milestones = emptyList(),
                aiTasks = emptyList(), childAvatarSignedUrl = null
            )
            loadChild()
        }
    }

    /** Switch the active child (multi-child). Persists the selection and reloads everything for it. */
    fun selectChild(childId: String) {
        if (childId == _uiState.value.child?.id) return
        com.lullabyai.app.data.settings.ChildSelection.set(childId)
        // Reset per-child guards so the new child seeds its milestones/vaccines and reloads cleanly.
        vaccineSeededChildId = null
        milestonesEnsuredChildId = null
        // Clear the previous child's data so stale info never shows during the switch.
        _uiState.value = _uiState.value.copy(
            child = null, vaccines = emptyList(), growthRecords = emptyList(),
            healthRecords = emptyList(), symptomActivityByRecord = emptyMap(), symptomUpdateByRecord = emptyMap(),
            healthHistoryInsight = null, sicknessFollowUpInsight = null,
            medicationHistoryInsight = null, doctorVisitHistoryInsight = null,
            allergies = emptyList(), milestones = emptyList(),
            aiTasks = emptyList(), childAvatarSignedUrl = null,
            infoAsks = emptyList(), infoAskChildId = null, dailyQuestion = null,
            isChildOwner = childId in _uiState.value.ownedChildIds
        )
        loadChild()
    }

    private fun loadAllData(childId: String) {
        if (isGuest() || childId == "guest_child_local") return
        viewModelScope.launch {
            // Recompute statuses against today's date first so the recommended vaccine stays current
            healthRepo.refreshVaccineStatuses(childId)
            coroutineScope {
                val vaccinesDef = async { healthRepo.getVaccineRecords(childId).getOrNull() }
                val growthDef = async { healthRepo.getGrowthRecords(childId).getOrNull() }
                val healthDef = async {
                    healthRepo.getHealthRecords(childId).getOrNull()?.let { records ->
                        Triple(
                            records,
                            healthRepo.getLatestSymptomActivityByRecord(childId).getOrDefault(emptyMap()),
                            healthRepo.getLatestSymptomUpdateByRecord(childId).getOrDefault(emptyMap())
                        )
                    }
                }
                val milestonesDef = async { childRepo.getMilestones(childId).getOrNull() }
                val allergiesDef = async { healthRepo.getAllergies(childId).getOrNull() }
                val chronicDef = async { healthRepo.getChronicConditions(childId).getOrNull() }

                val vaccines = vaccinesDef.await()
                val growth = growthDef.await()
                val healthBundle = healthDef.await()
                val milestones = milestonesDef.await()
                val allergyList = allergiesDef.await()
                val chronic = chronicDef.await()

                val cur = _uiState.value.child
                val allergySummary = allergyList
                    ?.map { it.name }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.joinToString(", ")
                    ?.ifBlank { null }
                val childWithAllergies = if (cur != null && allergyList != null) {
                    cur.copy(allergies = allergySummary)
                } else {
                    cur
                }

                _uiState.value = _uiState.value.copy(
                    vaccines = vaccines ?: _uiState.value.vaccines,
                    growthRecords = growth ?: _uiState.value.growthRecords,
                    healthRecords = healthBundle?.first ?: _uiState.value.healthRecords,
                    symptomActivityByRecord = healthBundle?.second ?: _uiState.value.symptomActivityByRecord,
                    symptomUpdateByRecord = healthBundle?.third ?: _uiState.value.symptomUpdateByRecord,
                    milestones = milestones ?: _uiState.value.milestones,
                    allergies = allergyList ?: _uiState.value.allergies,
                    chronicConditions = chronic ?: _uiState.value.chronicConditions,
                    child = childWithAllergies
                )

                growth?.let { healChildVitalsFromGrowth(childId, it) }
                if (cur != null && allergyList != null && cur.allergies != allergySummary) {
                    childRepo.syncAllergySummary(childId, allergyList.map { it.name })
                }
            }
        }
    }

    private fun reloadAllergies() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.getAllergies(childId).onSuccess { list ->
                val summary = list.map { it.name }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifBlank { null }
                val cur = _uiState.value.child
                _uiState.value = _uiState.value.copy(
                    allergies = list,
                    child = cur?.copy(allergies = summary)
                )
            }
        }
    }

    /** If growth history has a newer reading than the children-row cache, push it onto the child
     *  so Summary / Health / Edit Child / Nutrition all show the same vitals as Growth History.
     *  When every reading was deleted, the cache is cleared so Hub shows none. */
    private suspend fun healChildVitalsFromGrowth(
        childId: String,
        records: List<GrowthRecord>,
        clearMissing: Boolean = false
    ) {
        val cur = _uiState.value.child ?: return
        if (cur.id != childId) return
        val latestWeight = records.filter { it.weightKg != null }.maxByOrNull { it.recordedAt ?: "" }?.weightKg
        val latestHeight = records.filter { it.heightCm != null }.maxByOrNull { it.recordedAt ?: "" }?.heightCm
        val newWeight = when {
            clearMissing -> latestWeight
            cur.weightKg == null -> null
            else -> latestWeight ?: cur.weightKg
        }
        val newHeight = when {
            clearMissing -> latestHeight
            cur.heightCm == null -> null
            else -> latestHeight ?: cur.heightCm
        }
        if (newWeight == cur.weightKg && newHeight == cur.heightCm) return
        childRepo.updateVitals(childId, newWeight, newHeight, clearMissing = clearMissing)
        _uiState.value = _uiState.value.copy(
            child = cur.copy(weightKg = newWeight, heightCm = newHeight)
        )
    }

    // Loaded on-demand (not part of the eager child-load cascade) — avoids an extra network call
    // for accounts that never open the waste-record screen.
    fun loadWasteRecords() {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            healthRepo.getWasteRecords(childId).onSuccess { _uiState.value = _uiState.value.copy(wasteRecords = it) }
        }
    }

    fun addWasteRecord(wasteType: String, consistency: String?, color: String?, amount: String, notes: String?) {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            healthRepo.addWasteRecord(childId, wasteType, consistency, color, amount, notes).onSuccess { loadWasteRecords() }
        }
    }

    fun addAllergy(name: String, type: String, severity: String, symptoms: String?) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addAllergy(childId, name, type, severity, symptoms).onSuccess {
                reloadAllergies()
                // Adding a real allergy after previously confirming "none" — clear that flag.
                if (_uiState.value.child?.noKnownAllergies == true) setNoKnownAllergies(false)
            }
        }
    }

    /** Confirms (or un-confirms) "no known allergies" for the active child — distinct from simply
     *  having zero allergy rows, so the missing-info nudge stops re-asking once this is set true. */
    fun setNoKnownAllergies(value: Boolean) {
        if (isGuest()) { promptRegistration(); return }
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            childRepo.setNoKnownAllergies(childId, value).onSuccess {
                _uiState.value = _uiState.value.let { s ->
                    s.copy(
                        child = s.child?.copy(noKnownAllergies = value),
                        children = s.children.map { if (it.id == childId) it.copy(noKnownAllergies = value) else it }
                    )
                }
            }
        }
    }

    fun updateAllergy(id: String, name: String, type: String, severity: String, symptoms: String?) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            healthRepo.updateAllergy(id, name, type, severity, symptoms).onSuccess { reloadAllergies() }
        }
    }

    fun deleteAllergy(id: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            healthRepo.deleteAllergy(id).onSuccess { reloadAllergies() }
        }
    }

    private fun reloadChronicConditions() {
        if (isGuest()) return
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.getChronicConditions(childId).onSuccess { _uiState.value = _uiState.value.copy(chronicConditions = it) }
        }
    }

    /** Public refresh for Chronic Registry screen after its own CRUD (#14). */
    fun refreshChronicConditionsFromRegistry() = reloadChronicConditions()

    fun addChronicCondition(name: String, notes: String?) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addChronicCondition(childId, name, notes).onSuccess { reloadChronicConditions() }
        }
    }

    fun deleteChronicCondition(id: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            healthRepo.deleteChronicCondition(id).onSuccess { reloadChronicConditions() }
        }
    }

    /** Sync AI daily tasks: daily refresh, 5h-after-complete replacement, refill to 4. */
    fun loadTasks(child: Child) {
        if (isGuest()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tasksLoading = true)
            // Ground the AI activities in the child's not-yet-achieved, age-appropriate official milestones.
            val ageM = getAgeMonths()
            val hints = _uiState.value.milestones
                .filter { !it.isAchieved && (it.ageMonthsExpected ?: 0) <= ageM }
                .sortedBy { it.ageMonthsExpected ?: 0 }
                .map { it.milestoneName }
            val tasks = aiRepo.syncTasks(child, hints)
            _uiState.value = _uiState.value.copy(aiTasks = tasks, tasksLoading = false)
        }
    }

    /** Toggle a task's done status (select / deselect). */
    fun toggleTask(taskId: String, currentlyCompleted: Boolean) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            if (currentlyCompleted) aiRepo.uncompleteTask(taskId) else aiRepo.completeTask(taskId)
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(aiTasks = aiRepo.getActiveTasks(childId))
        }
    }

    /** "Not available" button — delete the task and generate a replacement. */
    fun notAvailableTask(taskId: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            val ageM = getAgeMonths()
            val hints = _uiState.value.milestones
                .filter { !it.isAchieved && (it.ageMonthsExpected ?: 0) <= ageM }
                .sortedBy { it.ageMonthsExpected ?: 0 }
                .map { it.milestoneName }
            aiRepo.replaceTask(taskId, child, hints)
            _uiState.value = _uiState.value.copy(aiTasks = aiRepo.getActiveTasks(child.id))
        }
    }

    fun loadTaskHistory(onLoaded: (List<AiTask>) -> Unit) {
        if (isGuest()) { onLoaded(emptyList()); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            onLoaded(aiRepo.getHistoryTasks(childId))
        }
    }

    /** Upload a new child photo (raw image bytes), then update the child in state. */
    fun uploadChildAvatar(bytes: ByteArray) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            _uiState.value = _uiState.value.copy(avatarUploading = true, error = null)
            childRepo.uploadChildAvatar(current.id, bytes)
                .onSuccess { signedUrl ->
                    _uiState.value = _uiState.value.copy(
                        childAvatarSignedUrl = signedUrl,
                        avatarUploading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        avatarUploading = false,
                        error = "อัปโหลดรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    /**
     * Saves the whole แก้ไขข้อมูลลูก page in one write.
     *
     * Takes a [Child] rather than a parameter per field: the edit page owns two dozen of them now,
     * and a positional list that long is a bug waiting for the day two String? arguments get
     * swapped. Growth history still gets its own row when the vitals actually moved, because the
     * columns on `children` are only a cache of the latest reading.
     */
    fun saveChildProfile(updated: Child, onDone: () -> Unit) {
        if (isGuest()) { promptRegistration(); onDone(); return }
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            val vitalsChanged = updated.weightKg != current.weightKg || updated.heightCm != current.heightCm
            childRepo.updateChild(updated)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(child = updated)
                    if (vitalsChanged) {
                        childRepo.updateVitals(
                            updated.id, updated.weightKg, updated.heightCm, clearMissing = true
                        )
                        if (updated.weightKg != null || updated.heightCm != null) {
                            addGrowthRecord(
                                GrowthRecord(
                                    weightKg = updated.weightKg,
                                    heightCm = updated.heightCm,
                                    recordedAt = java.time.LocalDate.now().toString(),
                                    recordType = "profile_edit"
                                )
                            )
                        }
                    }
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "อัปเดตข้อมูลลูกแล้ว",
                        "ข้อมูลของ ${updated.name} ได้รับการบันทึกเรียบร้อย"
                    )
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    private suspend fun addFavoriteFromAsk(childId: String, category: String, value: String) {
        if (value.isBlank()) return
        childRepo.addChildFavorite(childId, category, value, FavoriteSource.INFO_ASK).getOrThrow()
    }

    /**
     * Records something the AI noticed in the child's activity.
     *
     * Fire-and-forget on purpose: this runs off the back of an analysis the parent did not ask
     * for, and a failed write is not worth interrupting them over. The unique constraint means
     * re-reading the same activity re-adds nothing.
     */
    fun noteFavoriteFromAi(category: String, label: String) {
        if (isGuest() || label.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            childRepo.addChildFavorite(current.id, category, label, FavoriteSource.AI)
                .onSuccess { result ->
                    val added = (result as? AddFavoriteResult.Added)?.favorite ?: return@onSuccess
                    _uiState.value = _uiState.value.copy(favorites = _uiState.value.favorites + added)
                }
        }
    }

    /**
     * Loads the server's merged record feed for the selected child. Cleared first, so a switch of
     * child never shows the previous child's history while the new one loads.
     */
    fun loadActivityFeed() {
        val child = _uiState.value.child ?: return
        _uiState.value = _uiState.value.copy(activityFeed = emptyList())
        viewModelScope.launch {
            childRepo.listActivityFeed(child.id, limit = 80)
                .onSuccess { rows ->
                    if (_uiState.value.child?.id == child.id) _uiState.value = _uiState.value.copy(activityFeed = rows)
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    private var favoriteScanRunning = false

    /**
     * Reads what the family wrote this past while and adds the favourites it states outright.
     *
     * Runs on its own when the child page opens, at most once a day per child, and only for a
     * parent — a caregiver's app would spend an AI call on rows the database refuses to save. Every
     * failure is swallowed on purpose: nobody asked for this scan, so it must never put an error on
     * their screen. FavoriteExtractor is what makes it safe to add without asking: nothing reaches
     * the profile unless its words are in the note it came from, and each item is tagged AI with a
     * one-tap remove.
     */
    fun scanFavoritesFromActivity(context: android.content.Context) {
        if (isGuest() || favoriteScanRunning) return
        val child = _uiState.value.child ?: return
        favoriteScanRunning = true
        viewModelScope.launch {
            try {
                val settings = com.lullabyai.app.data.settings.SettingsManager(context)
                if (settings.favoriteScanDoneToday(child.id)) return@launch
                if (childRepo.isChildParent(child.id).getOrDefault(false) != true) return@launch
                // Marked before the call: a failure today should wait for tomorrow, not retry on
                // every visit to the page.
                settings.markFavoriteScanDone(child.id)

                val today = java.time.LocalDate.now()
                val feed = childRepo.listActivityFeed(child.id, limit = 80).getOrDefault(emptyList())
                val activities = childRepo.listActivities(
                    child.id, today.minusDays(60).toString(), today.plusDays(1).toString()
                ).getOrDefault(emptyList())
                val lines = com.lullabyai.app.data.FavoriteExtractor.sourceLines(feed, activities)
                if (lines.isEmpty()) return@launch          // nothing written in words; no call

                val existing = _uiState.value.favorites
                val reply = com.lullabyai.app.data.remote.AiProxyClient.generate(
                    prompt = com.lullabyai.app.data.FavoriteExtractor.prompt(child.name, lines, existing),
                    useSearch = false,
                    maxTokens = 600,
                    system = com.lullabyai.app.data.FavoriteExtractor.SYSTEM,
                    jsonMode = true
                )
                com.lullabyai.app.data.FavoriteExtractor.parse(reply.text, lines, existing)
                    .forEach { noteFavoriteFromAi(it.category, it.label) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Unasked-for background work: a failed scan is a day with nothing new, not an error.
            } finally {
                favoriteScanRunning = false
            }
        }
    }

    /** Picks one of the four bundled photos instead of uploading one. */
    fun pickAvatarPreset(presetId: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            _uiState.value = _uiState.value.copy(avatarUploading = true, error = null)
            childRepo.setChildAvatarPreset(current.id, presetId)
                .onSuccess {
                    val stored = com.lullabyai.app.data.model.ChildAvatarPreset.storedValue(presetId)
                    _uiState.value = _uiState.value.copy(
                        child = current.copy(avatarUrl = stored),
                        childAvatarSignedUrl =
                            com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(stored),
                        avatarUploading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        avatarUploading = false,
                        error = "เปลี่ยนรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    /**
     * Records one favourite. [source] says who noticed it — the parent, the AI reading activity,
     * or the daily question banner — and a repeat of something already recorded is quietly kept
     * as the one row that is already there.
     */
    fun addFavorite(category: String, label: String, source: String = FavoriteSource.PARENT) {
        if (isGuest()) { promptRegistration(); return }
        if (label.isBlank()) return
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            _uiState.value = _uiState.value.copy(favoritesSaving = true, error = null)
            childRepo.addChildFavorite(current.id, category, label, source)
                .onSuccess { result ->
                    val added = (result as? AddFavoriteResult.Added)?.favorite
                    _uiState.value = _uiState.value.copy(
                        favorites = if (added != null) _uiState.value.favorites + added else _uiState.value.favorites,
                        favoritesSaving = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(favoritesSaving = false, error = it.message)
                }
        }
    }

    fun removeFavorite(favoriteId: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            childRepo.deleteChildFavorite(favoriteId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        favorites = _uiState.value.favorites.filterNot { it.id == favoriteId }
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    /** แชร์ให้ผู้ดูแลเห็น. The database is what actually hides them; this only records the choice. */
    fun setFavoritesShared(shared: Boolean) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            childRepo.setFavoritesShared(current.id, shared)
                .onSuccess { _uiState.value = _uiState.value.copy(child = current.copy(favoritesShared = shared)) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateMilestone(milestone: DevelopmentMilestone) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            childRepo.updateMilestone(milestone).onSuccess { loadChild() }
        }
    }

    private var milestonesEnsuredChildId: String? = null
    fun ensureDefaultMilestones() {
        if (isGuest()) return
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            if (milestonesEnsuredChildId == childId) return@launch
            milestonesEnsuredChildId = childId
            childRepo.ensureDefaultMilestones(childId)
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Toggle a milestone achieved/not, then refresh the list. */
    fun setMilestoneAchieved(id: String, achieved: Boolean) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            childRepo.setMilestoneAchieved(id, achieved)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Save (or clear) the reason a milestone is skipped, then refresh the list. */
    fun setMilestoneSkipReason(id: String, reason: String?) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            childRepo.setMilestoneSkipReason(id, reason)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Load the current child's uploaded medical documents (for the Edit Child screen). */
    fun loadMedicalDocuments() {
        if (isGuest()) return
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
        }
    }

    // Tracks the in-flight Document Vault upload so the user can cancel it explicitly instead
    // of just watching the FAB spin with no way out.
    private var docUploadJob: Job? = null

    /** Upload a medical document for a specific child (e.g. Pedia Connect registration before that child is selected globally). */
    suspend fun uploadMedicalDocumentForChild(
        childId: String,
        name: String,
        mime: String?,
        bytes: ByteArray,
        category: String = "other"
    ): Result<Unit> = childRepo.uploadMedicalDocument(childId, name, mime, bytes, category = category)

    /** Upload a medical document (PDF/JPG/PNG) for the current child to the private bucket.
     *  [category] = identity | certs | lab | other (Document Vault chips). */
    fun uploadMedicalDocument(name: String, mime: String?, bytes: ByteArray, category: String = "other") {
        docUploadJob?.cancel()
        docUploadJob = viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(docUploading = true)
            childRepo.uploadMedicalDocument(childId, name, mime, bytes, category = category)
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
            _uiState.value = _uiState.value.copy(docUploading = false)
        }
    }

    /** Cancel button on the Document Vault upload FAB — aborts an in-flight upload. */
    fun cancelMedicalDocumentUpload() {
        docUploadJob?.cancel()
        docUploadJob = null
        _uiState.value = _uiState.value.copy(docUploading = false)
    }

    /** Signed URL for open/view, or onFailure null. */
    fun openMedicalDocument(filePath: String, onResult: (String?) -> Unit) {
        signMedicalDocUrl(filePath, onResult)
    }

    /** Open a stored ใบรับรองวัคซีน (private medical-docs path on the vaccine row). */
    fun openVaccineCertificate(filePath: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            onResult(healthRepo.signedVaccineCertificateUrl(filePath).getOrNull())
        }
    }

    /** Download bytes from private storage (caller writes to Downloads / shares). */
    fun downloadMedicalDocumentBytes(filePath: String, onResult: (ByteArray?) -> Unit) {
        viewModelScope.launch { onResult(childRepo.downloadMedicalDocumentBytes(filePath).getOrNull()) }
    }

    /** Remove an uploaded medical document for the current child. */
    fun deleteMedicalDocument(id: String, filePath: String) {
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            childRepo.deleteMedicalDocument(id, filePath)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
        }
    }

    /** Load the current child's medication schedules + individual dose timeline. */
    fun loadMedications() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            medicationRepo.getSchedules(childId).onSuccess { _uiState.value = _uiState.value.copy(medicationSchedules = it) }
            medicationRepo.getDoses(childId).onSuccess { _uiState.value = _uiState.value.copy(medicationDoses = it) }
        }
    }

    fun addMedicationSchedule(
        medicineName: String, dosage: Double?, dosageUnit: String, frequency: String,
        scheduleType: String, startDate: String?, endDate: String?, numDoses: Int?, notes: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(medicationSaving = true)
            medicationRepo.createSchedule(childId, medicineName, dosage, dosageUnit, frequency, scheduleType, startDate, endDate, numDoses, notes)
            loadMedications()
            _uiState.value = _uiState.value.copy(medicationSaving = false)
            onDone()
        }
    }

    fun markDoseTaken(id: String) {
        viewModelScope.launch { medicationRepo.markDoseTaken(id); loadMedications() }
    }

    fun skipDose(id: String) {
        viewModelScope.launch { medicationRepo.skipDose(id); loadMedications() }
    }

    fun deleteMedicationSchedule(id: String) {
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            medicationRepo.deleteSchedule(id)
            loadMedications()
        }
    }

    /** Load the current child's sleep sessions + recurring schedules. */
    fun loadSleep() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            sleepRepo.getLogs(childId).onSuccess { logs ->
                _uiState.value = _uiState.value.copy(sleepLogs = logs, activeSleepSession = logs.firstOrNull { it.endTime == null })
            }
            sleepRepo.getSchedules(childId).onSuccess { _uiState.value = _uiState.value.copy(sleepSchedules = it) }
        }
    }

    /** One-tap sleep tracking: starts a session if none is active, otherwise ends the active one. */
    fun toggleSleepSession(sleepType: String = "main", mood: String? = null) {
        if (isGuest()) { promptRegistration(); return }
        if (_uiState.value.sleepSaving) return
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            val active = _uiState.value.activeSleepSession
            if (active != null) sleepRepo.endSession(active.id, mood)
            else sleepRepo.startSession(childId, sleepType)
            loadSleep()
            _uiState.value = _uiState.value.copy(sleepSaving = false)
        }
    }

    fun addManualSleepLog(sleepType: String, start: String, end: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            sleepRepo.addManualLog(childId, sleepType, start, end)
            loadSleep()
            _uiState.value = _uiState.value.copy(sleepSaving = false)
        }
    }

    fun deleteSleepLog(id: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            sleepRepo.deleteLog(id)
            loadSleep()
        }
    }

    fun addSleepSchedule(label: String, sleepType: String, startTime: String, endTime: String, daysOfWeek: String, onDone: () -> Unit = {}) {
        if (isGuest()) { promptRegistration(); onDone(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            sleepRepo.createSchedule(childId, label, sleepType, startTime, endTime, daysOfWeek)
            loadSleep()
            _uiState.value.sleepSchedules.maxByOrNull { it.createdAt ?: "" }
                ?.let { com.lullabyai.app.util.SleepReminderScheduler.scheduleNext(it) }
            _uiState.value = _uiState.value.copy(sleepSaving = false)
            onDone()
        }
    }

    fun updateSleepSchedule(id: String, label: String, sleepType: String, startTime: String, endTime: String, daysOfWeek: String, onDone: () -> Unit = {}) {
        if (isGuest()) { promptRegistration(); onDone(); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            sleepRepo.updateSchedule(id, label, sleepType, startTime, endTime, daysOfWeek)
            loadSleep()
            com.lullabyai.app.util.SleepReminderScheduler.cancel(id)
            _uiState.value.sleepSchedules.find { it.id == id }
                ?.let { com.lullabyai.app.util.SleepReminderScheduler.scheduleNext(it) }
            _uiState.value = _uiState.value.copy(sleepSaving = false)
            onDone()
        }
    }

    fun deleteSleepSchedule(id: String) {
        if (isGuest()) { promptRegistration(); return }
        com.lullabyai.app.util.SleepReminderScheduler.cancel(id)
        viewModelScope.launch {
            if (!ensureChildOwner()) return@launch
            sleepRepo.deleteSchedule(id)
            loadSleep()
        }
    }

    /** Rough age-based recommended total daily sleep (hours) for 0-3y, per standard pediatric guidance. */
    fun sleepTargetHours(): Int =
        com.lullabyai.app.util.SleepTarget.forBirthdate(_uiState.value.child?.birthdate)

    /** Rough age-based recommended bedtime clock time ("HH:mm" start, end), paired with
     *  [sleepTargetHours] — younger children get an earlier bedtime, per standard pediatric
     *  guidance. Feeds the "does this time work?" recommendation prompt on SleepScheduleScreen. */
    fun recommendedBedtime(): Pair<String, String> {
        val ageMonths = com.lullabyai.app.data.VaccineSetupCatalog.ageMonthsFromBirthdate(_uiState.value.child?.birthdate ?: "")
        val start = when (ageMonths) {
            in 0..3 -> "19:00"
            in 4..11 -> "19:00"
            in 12..23 -> "19:30"
            else -> "20:00"
        }
        val startMin = start.split(":").let { it[0].toInt() * 60 + it[1].toInt() }
        val endMin = (startMin + sleepTargetHours() * 60) % (24 * 60)
        val end = "%02d:%02d".format(endMin / 60, endMin % 60)
        return start to end
    }

    /** Post-hoc mood tag (mockup's mood grid isn't gated behind ending a session) — applies to
     *  today's active session if one is running, else the most recent completed log today. */
    fun logMoodForToday(mood: String) {
        if (isGuest()) { promptRegistration(); return }
        val logs = _uiState.value.sleepLogs
        val today = java.time.LocalDate.now()
        val target = _uiState.value.activeSleepSession
            ?: logs.filter { log ->
                com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime)
                    ?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() == today
            }.maxByOrNull { it.startTime }
            ?: return
        viewModelScope.launch { sleepRepo.updateMood(target.id, mood); loadSleep() }
    }

    fun addGrowthRecord(record: GrowthRecord) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val toSave = record.copy(childId = childId)
            healthRepo.addGrowthRecord(toSave)
                .onSuccess {
                    healthRepo.getGrowthRecords(childId).onSuccess {
                        _uiState.value = _uiState.value.copy(growthRecords = it)
                    }
                    val cur = _uiState.value.child
                    if (cur != null && (toSave.weightKg != null || toSave.heightCm != null)) {
                        _uiState.value = _uiState.value.copy(
                            child = cur.copy(
                                weightKg = toSave.weightKg ?: cur.weightKg,
                                heightCm = toSave.heightCm ?: cur.heightCm
                            )
                        )
                    }
                }
                .onFailure { com.lullabyai.app.data.offline.PendingWriteQueue.enqueueGrowthRecord(toSave) }
        }
    }

    fun deleteGrowthRecord(id: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            if (!ensureChildOwner(childId)) return@launch
            healthRepo.deleteGrowthRecord(id).onSuccess {
                healthRepo.getGrowthRecords(childId).onSuccess { records ->
                    _uiState.value = _uiState.value.copy(growthRecords = records)
                    healChildVitalsFromGrowth(childId, records, clearMissing = true)
                }
            }
        }
    }

    /** Reloads health records + per-episode last symptom activity (Batch Q #9) + F3 timestamps. */
    private suspend fun refreshHealthRecords(childId: String) {
        healthRepo.getHealthRecords(childId).onSuccess { records ->
            val activity = healthRepo.getLatestSymptomActivityByRecord(childId).getOrDefault(emptyMap())
            val updates = healthRepo.getLatestSymptomUpdateByRecord(childId).getOrDefault(emptyMap())
            _uiState.value = _uiState.value.copy(
                healthRecords = records,
                symptomActivityByRecord = activity,
                symptomUpdateByRecord = updates
            )
        }
    }

    /** F3 helper — lock clocks from last symptom write for this episode. */
    fun isHealthRecordEditLocked(record: HealthRecord): Boolean =
        com.lullabyai.app.util.HealthRecordRules.isEditLocked(
            record, _uiState.value.symptomUpdateByRecord[record.id] ?: record.symptomsUpdatedAt
        )

    fun addHealthRecord(record: HealthRecord, photoBytes: ByteArray? = null, onDone: (() -> Unit)? = null) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addHealthRecord(record.copy(childId = childId)).onSuccess { saved ->
                if (photoBytes != null) {
                    healthRepo.uploadHealthPhoto(childId, saved.id, photoBytes).onSuccess { path ->
                        healthRepo.updateHealthRecord(
                            saved.id, saved.title, saved.description,
                            saved.additionalSymptoms ?: emptyList(), path, saved.temperature
                        )
                    }
                }
                refreshHealthRecords(childId)
                val temp = record.temperature
                if (temp != null && temp >= 37.5) {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.WARNING,
                        "พบอุณหภูมิสูง",
                        "บันทึกอุณหภูมิ $temp°C — หากมีไข้สูงต่อเนื่องควรปรึกษาแพทย์"
                    )
                } else {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "บันทึกสุขภาพใหม่",
                        record.title
                    )
                }
                onDone?.invoke()
            }
        }
    }

    /** Remember which record the detail screen should open (the app passes selection via VM state,
     *  not nav args — same pattern as RecipeDetail/ExerciseInstruction). */
    fun selectHealthRecord(recordId: String) {
        val changed = _uiState.value.openHealthRecordId != recordId
        _uiState.value = _uiState.value.copy(
            openHealthRecordId = recordId,
            sicknessFollowUpInsight = if (changed) null else _uiState.value.sicknessFollowUpInsight,
            sicknessFollowUpInsightLoading = if (changed) false else _uiState.value.sicknessFollowUpInsightLoading
        )
    }

    /**
     * Opens the health-record detail (#7): signs the attached photo (if any) and generates a
     * short, one-shot AI care suggestion grounded in this record's temperature + symptoms + notes.
     */
    fun loadHealthDetail(recordId: String, isThai: Boolean) {
        val record = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return
        viewModelScope.launch {
            val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(record.photoUrl)
            _uiState.value = _uiState.value.copy(
                healthPhotoSignedUrl = signed,
                healthDetailAdvice = null,
                healthDetailAdviceLoading = true
            )
            val child = _uiState.value.child
            val temp = record.temperature?.let { "${it}°C" } ?: (if (isThai) "ไม่ได้บันทึก" else "not recorded")
            val symptoms = (listOf(record.title) + (record.additionalSymptoms ?: emptyList()))
                .filter { it.isNotBlank() }.joinToString(", ")
            val notes = record.description?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่มี" else "none")
            val prompt = if (isThai)
                "เด็กอายุ ${child?.let { calculateAge(true) } ?: "-"} มีบันทึกสุขภาพดังนี้ " +
                "อุณหภูมิร่างกาย $temp อาการที่พบ $symptoms บันทึกเพิ่มเติมจากผู้ปกครอง: $notes " +
                "ช่วยให้คำแนะนำการดูแลเบื้องต้นสำหรับคุณพ่อคุณแม่ สั้นกระชับ 3-4 ประโยค " +
                "บอกสัญญาณที่ควรพาไปพบแพทย์ด้วย ใช้ภาษาพูดที่อบอุ่นเป็นกันเอง ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "A child aged ${child?.let { calculateAge(false) } ?: "-"} has this health log: " +
                "body temperature $temp, symptoms $symptoms, parent notes: $notes. " +
                "Give brief first-care guidance for the parent in 3-4 sentences, including warning " +
                "signs that warrant seeing a doctor. Warm, plain language, no symbols or emoji."
            val advice = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 500)
            _uiState.value = _uiState.value.copy(healthDetailAdvice = advice, healthDetailAdviceLoading = false)
        }
    }

    /** Upload/replace the photo on a health record, persist its path, and refresh the list + preview.
     *  Blocked after the Batch Q #8 10-minute edit lock. */
    fun uploadHealthPhoto(recordId: String, bytes: ByteArray) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val record = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return@launch
            if (isHealthRecordEditLocked(record)) {
                _uiState.value = _uiState.value.copy(
                    error = "บันทึกนี้ล็อกแล้ว — แก้ไขได้ภายใน 10 นาทีหลังอัปเดตอาการ / This record is locked (10-min window from last symptom update)"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(healthPhotoUploading = true, error = null)
            healthRepo.uploadHealthPhoto(childId, recordId, bytes)
                .onSuccess { path ->
                    healthRepo.updateHealthRecord(
                        recordId, record.title, record.description,
                        record.additionalSymptoms ?: emptyList(), path, record.temperature
                    )
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(path)
                    refreshHealthRecords(childId)
                    _uiState.value = _uiState.value.copy(healthPhotoSignedUrl = signed, healthPhotoUploading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        healthPhotoUploading = false,
                        error = "อัปโหลดรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    /** Save edits to a health record from the detail screen, then refresh the list.
     *  No-ops (with error) once Batch Q #8's 10-minute lock applies. */
    fun saveHealthRecord(
        recordId: String, title: String, description: String?,
        additionalSymptoms: List<String>, temperature: Double?, onDone: () -> Unit
    ) {
        if (isGuest()) { promptRegistration(); onDone(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val current = _uiState.value.healthRecords.firstOrNull { it.id == recordId }
            if (current != null && isHealthRecordEditLocked(current)) {
                _uiState.value = _uiState.value.copy(
                    error = "บันทึกนี้ล็อกแล้ว — แก้ไขได้ภายใน 10 นาทีหลังอัปเดตอาการ / This record is locked (10-min window from last symptom update)"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(healthDetailSaving = true)
            healthRepo.updateHealthRecord(
                recordId, title, description, additionalSymptoms, current?.photoUrl, temperature
            ).onSuccess {
                refreshHealthRecords(childId)
            }
            _uiState.value = _uiState.value.copy(healthDetailSaving = false)
            onDone()
        }
    }

    /** Toggle a health record between "monitoring" and "recovered" — feeds HealthHistoryScreen's badge.
     *  Allowed even after the edit lock (status is not a content edit). */
    fun toggleHealthRecordStatus(recordId: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val current = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return@launch
            val next = if (current.status == "recovered") "monitoring" else "recovered"
            healthRepo.updateHealthStatus(recordId, next).onSuccess {
                refreshHealthRecords(childId)
            }
        }
    }

    /** Batch Q #8 — tick / untick "unused". Always allowed after the 10-min content lock. */
    fun setHealthRecordUnused(recordId: String, unused: Boolean) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val current = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return@launch
            val next = when {
                unused -> "unused"
                current.status == "unused" -> "monitoring"
                else -> current.status
            }
            if (next == current.status) return@launch
            healthRepo.updateHealthStatus(recordId, next).onSuccess {
                refreshHealthRecords(childId)
            }
        }
    }

    fun loadSymptomLogs(healthRecordId: String) {
        viewModelScope.launch {
            healthRepo.getSymptomLogs(healthRecordId).onSuccess { logs ->
                _uiState.value = _uiState.value.copy(symptomLogs = logs, symptomLogPhotoSignedUrl = null)
            }
        }
    }

    /**
     * Upsert a day-log (multi-day + editable current symptom). Blocked by F3 once the
     * episode's last symptom write is older than 10 minutes — except when editing a log
     * that is itself still inside its own 10-min window.
     */
    fun saveSymptomLog(
        healthRecordId: String,
        logDate: String,
        symptoms: Map<String, String>,
        temperature: Double?,
        feeling: String?,
        dayStatus: String,
        notes: String?,
        painLevel: Int?,
        photoUrl: String?,
        existingId: String?,
        temperatureUnit: String = "C",
        onDone: () -> Unit
    ) {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            // F3: editing a prior entry locks 10 min after *that* entry's last write.
            // Creating a brand-new timeline entry is always allowed (existingId == null).
            val existing = existingId?.let { id -> _uiState.value.symptomLogs.firstOrNull { it.id == id } }
            if (existing != null && com.lullabyai.app.util.HealthRecordRules.isSymptomLogEditLocked(existing)) {
                _uiState.value = _uiState.value.copy(
                    error = "รายการนี้ล็อกแล้ว — แก้ไขได้ภายใน 10 นาทีหลังอัปเดต / This entry is locked (10-min window)",
                    symptomLogSaving = false
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(symptomLogSaving = true, error = null)
            healthRepo.upsertSymptomLog(
                healthRecordId, childId, logDate, symptoms, temperature, feeling,
                dayStatus, notes, painLevel, photoUrl, existingId, temperatureUnit
            ).onSuccess {
                healthRepo.getSymptomLogs(healthRecordId).onSuccess { logs ->
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(it.photoUrl)
                    _uiState.value = _uiState.value.copy(symptomLogs = logs, symptomLogPhotoSignedUrl = signed)
                }
                refreshHealthRecords(childId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
            _uiState.value = _uiState.value.copy(symptomLogSaving = false)
            onDone()
        }
    }

    fun addSymptomLog(
        healthRecordId: String, symptoms: Map<String, String>, temperature: Double?,
        feeling: String?, dayStatus: String, notes: String?, onDone: () -> Unit
    ) {
        saveSymptomLog(
            healthRecordId, java.time.LocalDate.now().toString(), symptoms, temperature,
            feeling, dayStatus, notes, null, null, null, onDone = onDone
        )
    }

    fun uploadSymptomLogPhoto(bytes: ByteArray, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val key = "${System.currentTimeMillis()}"
            healthRepo.uploadSymptomLogPhoto(childId, key, bytes).onSuccess { path ->
                val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(path)
                _uiState.value = _uiState.value.copy(symptomLogPhotoSignedUrl = signed)
                onReady(path)
            }
        }
    }

    fun loadHealthHistoryInsight(isThai: Boolean) {
        val child = _uiState.value.child ?: return
        if (_uiState.value.healthHistoryInsight != null || _uiState.value.healthHistoryInsightLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(healthHistoryInsightLoading = true)
            val recent = _uiState.value.healthRecords.take(8).joinToString("; ") {
                "${it.title}(${it.status})"
            }.ifBlank { if (isThai) "ยังไม่มีบันทึก" else "no records yet" }
            val prompt = if (isThai)
                "เด็กชื่อ ${child.name} มีประวัติสุขภาพล่าสุด: $recent สรุปแนวโน้มสุขภาพสั้นๆ 2-3 ประโยค เป็นภาษาพูดอบอุ่น ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "Child ${child.name} recent health history: $recent. Give a warm 2-3 sentence health trend summary for the parent. No symbols or emoji."
            val text = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 400)
            _uiState.value = _uiState.value.copy(healthHistoryInsight = text, healthHistoryInsightLoading = false)
        }
    }

    fun loadSicknessFollowUpInsight(isThai: Boolean) {
        val child = _uiState.value.child ?: return
        val record = _uiState.value.healthRecords.firstOrNull { it.id == _uiState.value.openHealthRecordId } ?: return
        if (_uiState.value.sicknessFollowUpInsight != null || _uiState.value.sicknessFollowUpInsightLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sicknessFollowUpInsightLoading = true)
            val logs = _uiState.value.symptomLogs.take(5).joinToString("; ") { log ->
                val sym = log.symptoms.filterValues { it != "none" }.entries.joinToString(",") { "${it.key}:${it.value}" }
                "${log.logDate}:$sym"
            }.ifBlank { if (isThai) "ยังไม่มีบันทึกรายวัน" else "no day logs yet" }
            val prompt = if (isThai)
                "เด็กชื่อ ${child.name} กำลังติดตามอาการ \"${record.title}\" บันทึกรายวัน: $logs สรุปแนวโน้มการหายป่วยสั้นๆ 2-3 ประโยค อบอุ่น ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "Child ${child.name} is tracking \"${record.title}\". Day logs: $logs. Brief 2-3 sentence recovery trend for the parent. Warm, no symbols or emoji."
            val text = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 400)
            _uiState.value = _uiState.value.copy(sicknessFollowUpInsight = text, sicknessFollowUpInsightLoading = false)
        }
    }

    fun loadMedicationHistoryInsight(isThai: Boolean) {
        val child = _uiState.value.child ?: return
        if (_uiState.value.medicationHistoryInsight != null || _uiState.value.medicationHistoryInsightLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(medicationHistoryInsightLoading = true)
            val meds = _uiState.value.medicationSchedules.take(6).joinToString(", ") { it.medicineName }
                .ifBlank { if (isThai) "ยังไม่มีตารางยา" else "no schedules yet" }
            val prompt = if (isThai)
                "เด็กชื่อ ${child.name} มียาในตาราง: $meds ให้คำแนะนำสั้นๆ 2 ประโยคเรื่องการให้ยาอย่างสม่ำเสมอ อบอุ่น ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "Child ${child.name} medication schedules: $meds. Two short sentences of adherence tips for the parent. Warm, no symbols or emoji."
            val text = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 300)
            _uiState.value = _uiState.value.copy(medicationHistoryInsight = text, medicationHistoryInsightLoading = false)
        }
    }

    fun loadDoctorVisitHistoryInsight(isThai: Boolean) {
        val child = _uiState.value.child ?: return
        if (_uiState.value.doctorVisitHistoryInsight != null || _uiState.value.doctorVisitHistoryInsightLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(doctorVisitHistoryInsightLoading = true)
            val visits = _uiState.value.doctorVisits.take(5).joinToString("; ") {
                listOfNotNull(it.visitDatetime.take(10).ifBlank { null }, it.facilityName, it.diagnosis ?: it.reason).joinToString(" ")
            }.ifBlank { if (isThai) "ยังไม่มีประวัติพบแพทย์" else "no visits yet" }
            val prompt = if (isThai)
                "เด็กชื่อ ${child.name} ประวัติพบแพทย์: $visits สรุปสั้นๆ 2 ประโยคสำหรับผู้ปกครอง อบอุ่น ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "Child ${child.name} doctor visits: $visits. Two short parent-facing summary sentences. Warm, no symbols or emoji."
            val text = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 300)
            _uiState.value = _uiState.value.copy(doctorVisitHistoryInsight = text, doctorVisitHistoryInsightLoading = false)
        }
    }

    /**
     * #11 — content for the Stitch "AI Suggestions & Guidance" screen. Reuses the app's existing
     * AI chain (ClaudeClient.generateContent, same as every other insight on this VM); the only
     * difference is that this one asks for a line-delimited reply so the three suggestion cards,
     * the tip and the weekly insight can be split out. Parsing is deliberately lenient — a missing
     * or malformed line just drops that piece instead of failing the whole screen.
     */
    fun loadAiGuidance(isThai: Boolean, force: Boolean = false) {
        val child = _uiState.value.child ?: return
        if (!force && (_uiState.value.aiGuidance != null || _uiState.value.aiGuidanceLoading)) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiGuidanceLoading = true)
            val st = _uiState.value
            val months = getAgeMonths()
            val devPercent = if (st.milestones.isNotEmpty())
                st.milestones.count { it.isAchieved } * 100 / st.milestones.size else 0
            val facts = listOfNotNull(
                "age_months=$months",
                child.weightKg?.let { "weight_kg=$it" },
                child.heightCm?.let { "height_cm=$it" },
                "milestones_achieved_percent=$devPercent",
                avgSleepHours()?.let { "avg_sleep_hours=%.1f".format(it) },
                st.allergies.takeIf { it.isNotEmpty() }?.let { "allergies=" + it.joinToString(",") { a -> a.name } },
                st.chronicConditions.takeIf { it.isNotEmpty() }?.let { "chronic=" + it.joinToString(",") { c -> c.name } },
                st.healthRecords.take(5).takeIf { it.isNotEmpty() }
                    ?.let { "recent_health=" + it.joinToString(",") { r -> "${r.title}(${r.status})" } }
            ).joinToString("; ")

            val format = """
                HEADLINE|<warm short headline>
                SUMMARY|<2-3 sentences about this week>
                CARD|<one of: nutrition, social, motor, sleep, health>|<short title>|<category label>|<1-2 sentence suggestion>
                CARD|...
                CARD|...
                TIP|<one short practical tip>
                INSIGHT|<one short behavioural insight>
            """.trimIndent()
            val prompt = if (isThai)
                "ข้อมูลเด็กชื่อ ${child.name}: $facts\n" +
                "เขียนคำแนะนำสำหรับผู้ปกครองเป็นภาษาไทย น้ำเสียงอบอุ่นให้กำลังใจ ห้ามใช้อิโมจิหรือสัญลักษณ์ " +
                "ต้องมี CARD สามบรรทัดเท่านั้น อ้างอิงข้อมูลจริงด้านบน อย่าแต่งตัวเลขขึ้นมาใหม่ " +
                "ตอบตามรูปแบบนี้เท่านั้น บรรทัดละหนึ่งรายการ ไม่ต้องมีข้อความอื่น:\n$format"
            else
                "Child ${child.name}: $facts\n" +
                "Write parent-facing guidance in English, warm and encouraging, no emoji or symbols. " +
                "Exactly three CARD lines. Ground everything in the facts above; do not invent numbers. " +
                "Reply in this format only, one item per line, nothing else:\n$format"

            val text = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 900)
            _uiState.value = _uiState.value.copy(
                aiGuidance = text?.let { parseAiGuidance(it, isThai) },
                aiGuidanceLoading = false
            )
        }
    }

    /** Mean nightly sleep (hours) over completed "main" sessions in the last 7 days. Null = no data. */
    fun avgSleepHours(): Double? {
        val cutoff = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS)
        val durations = _uiState.value.sleepLogs.mapNotNull { log ->
            if (log.sleepType != "main") return@mapNotNull null
            val start = com.lullabyai.app.util.TimeUtils.parseInstant(log.startTime) ?: return@mapNotNull null
            val end = com.lullabyai.app.util.TimeUtils.parseInstant(log.endTime) ?: return@mapNotNull null
            if (end.isBefore(cutoff)) return@mapNotNull null
            val minutes = java.time.temporal.ChronoUnit.MINUTES.between(start, end)
            if (minutes <= 0) null else minutes / 60.0
        }
        return if (durations.isEmpty()) null else durations.average()
    }

    private fun parseAiGuidance(raw: String, isThai: Boolean): AiGuidance {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        fun field(tag: String) = lines.firstOrNull { it.startsWith("$tag|", true) }
            ?.substringAfter('|')?.trim()?.takeIf { it.isNotBlank() }
        val cards = lines.filter { it.startsWith("CARD|", true) }.mapNotNull { line ->
            val p = line.split('|').drop(1).map { it.trim() }
            if (p.size < 4) null
            else AiGuidanceCard(iconKey = p[0].lowercase(), title = p[1], category = p[2], body = p[3])
        }
        // If the model ignored the format entirely, still show its prose rather than an empty screen.
        val fallback = lines.filterNot { it.contains('|') }.joinToString(" ")
        return AiGuidance(
            headline = field("HEADLINE") ?: (if (isThai) "คำแนะนำสำหรับวันนี้" else "Guidance for today"),
            summary = field("SUMMARY") ?: fallback,
            cards = cards,
            tip = field("TIP"),
            insight = field("INSIGHT")
        )
    }

    // --- Doctor visits ("บันทึกการหาหมอ") -------------------------------------------------------

    fun loadDoctorVisits() {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(doctorVisitsLoading = true)
            doctorVisitRepo.getVisits(childId).onSuccess { _uiState.value = _uiState.value.copy(doctorVisits = it) }
            _uiState.value = _uiState.value.copy(doctorVisitsLoading = false)
        }
    }

    /** Remember which visit VisitDetailsScreen/RecordVisitScreen(edit mode) should open — same
     *  VM-state pattern as selectHealthRecord (no nav-arg round trip). */
    fun selectDoctorVisit(visitId: String?) {
        _uiState.value = _uiState.value.copy(openDoctorVisitId = visitId)
    }

    /** Save a future appointment (RecordAppointmentScreen). */
    fun addAppointment(
        visitDatetime: String, facilityName: String?, doctorName: String?, reason: String?,
        reminders: List<String>, preparationNotes: String?, onDone: () -> Unit
    ) {
        if (isGuest()) { promptRegistration(); onDone(); return }
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(doctorVisitSaving = true)
            doctorVisitRepo.saveVisit(
                id = null, childId = childId, visitType = "scheduled", visitDatetime = visitDatetime,
                facilityName = facilityName, doctorName = doctorName, doctorSpecialty = null, reason = reason,
                symptoms = null, diagnosis = null, treatmentPlan = null, doctorAdvice = null,
                weightKg = null, heightCm = null, temperatureC = null, bloodPressure = null,
                nextAppointmentDate = null, preparationNotes = preparationNotes, reminders = reminders,
                status = "scheduled"
            ).onSuccess {
                loadDoctorVisits()
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "นัดหมายแพทย์ใหม่",
                    facilityName?.let { fn -> if (doctorName != null) "$fn • $doctorName" else fn } ?: (doctorName ?: "")
                )
            }
            _uiState.value = _uiState.value.copy(doctorVisitSaving = false)
            onDone()
        }
    }

    /**
     * Insert (visitId == null) or update (visitId != null) a completed visit log
     * (RecordVisitScreen). Also used to silently create a draft row the first time the user
     * attaches a photo before tapping the final Save button — [onSaved] always receives the row's
     * id so the caller can keep attaching / finally navigate away.
     */
    fun saveDoctorVisit(
        visitId: String?, visitDatetime: String, facilityName: String?, doctorName: String?,
        weightKg: Double?, heightCm: Double?, temperatureC: Double?, bloodPressure: String?,
        symptoms: List<String>, reason: String?, diagnosis: String?, treatmentPlan: String?,
        nextAppointmentDate: String?, onSaved: (String) -> Unit
    ) {
        if (isGuest()) { promptRegistration(); onSaved("guest"); return }
        val childId = _uiState.value.child?.id ?: return
        val status = if (!nextAppointmentDate.isNullOrBlank()) "follow_up" else "completed"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(doctorVisitSaving = true)
            doctorVisitRepo.saveVisit(
                id = visitId, childId = childId, visitType = "completed", visitDatetime = visitDatetime,
                facilityName = facilityName, doctorName = doctorName, doctorSpecialty = null, reason = reason,
                symptoms = symptoms.ifEmpty { null }, diagnosis = diagnosis, treatmentPlan = treatmentPlan, doctorAdvice = null,
                weightKg = weightKg, heightCm = heightCm, temperatureC = temperatureC, bloodPressure = bloodPressure,
                nextAppointmentDate = nextAppointmentDate?.takeIf { it.isNotBlank() }, preparationNotes = null,
                reminders = null, status = status
            ).onSuccess { visit ->
                if (weightKg != null || heightCm != null) {
                    addGrowthRecord(
                        GrowthRecord(weightKg = weightKg, heightCm = heightCm, recordedAt = visitDatetime.take(10), recordType = "doctor_visit")
                    )
                }
                loadDoctorVisits()
                _uiState.value = _uiState.value.copy(openDoctorVisitId = visit.id)
                onSaved(visit.id)
            }
            _uiState.value = _uiState.value.copy(doctorVisitSaving = false)
        }
    }

    fun loadVisitDocuments(visitId: String) {
        if (isGuest()) return
        viewModelScope.launch {
            childRepo.getVisitDocuments(visitId).onSuccess { _uiState.value = _uiState.value.copy(visitDocuments = it) }
        }
    }

    /** Attach a photo/PDF (prescription, doctor's note, x-ray) to a doctor visit — same private
     *  'medical-docs' bucket as the general document vault, just tagged with doctor_visit_id. */
    fun uploadVisitDocument(visitId: String, name: String, mime: String?, bytes: ByteArray) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(visitDocUploading = true)
            childRepo.uploadMedicalDocument(childId, name, mime, bytes, visitId)
            childRepo.getVisitDocuments(visitId).onSuccess { _uiState.value = _uiState.value.copy(visitDocuments = it) }
            _uiState.value = _uiState.value.copy(visitDocUploading = false)
        }
    }

    /** SymptomPickerScreen -> RecordVisitScreen result hand-off, same VM-state pattern as
     *  openHealthRecordId (no nav-arg round trip). RecordVisitScreen consumes + clears it. */
    fun setSelectedVisitSymptoms(symptoms: List<String>) {
        _uiState.value = _uiState.value.copy(selectedVisitSymptoms = symptoms)
    }

    fun clearSelectedVisitSymptoms() {
        _uiState.value = _uiState.value.copy(selectedVisitSymptoms = null)
    }

    /** Signs a private 'medical-docs' bucket path for one-off viewing (VisitDetailsScreen opens
     *  the result via the system image/PDF viewer, same private-bucket pattern as child avatars). */
    fun signMedicalDocUrl(filePath: String, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(childRepo.signedMedicalDocUrl(filePath).getOrNull()) }
    }

    // Maps a Thai age-group label to its month order so an added vaccine sorts into the right category.
    private fun ageOrderForGroup(group: String): Int = when (group.trim()) {
        "แรกเกิด" -> 0
        "1 เดือน" -> 1
        "2 เดือน" -> 2
        "4 เดือน" -> 4
        "6 เดือน" -> 6
        "9 เดือน" -> 9
        "1 ปี" -> 12
        "1 ปี 6 เดือน" -> 18
        "2 ปี" -> 24
        "2 ปี 6 เดือน" -> 30
        "3 ปี" -> 36
        else -> 999  // "อื่นๆ" / custom → sorts last
    }

    fun addVaccine(name: String, ageGroup: String, scheduledDate: String?, description: String?, place: String? = null) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val rec = VaccineRecord(
                childId = childId, vaccineName = name, description = description, place = place,
                ageGroup = ageGroup, ageOrder = ageOrderForGroup(ageGroup), scheduledDate = scheduledDate,
                status = if (scheduledDate != null) "appointment" else "upcoming"
            )
            healthRepo.addVaccineRecord(rec).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess { _uiState.value = _uiState.value.copy(vaccines = it) }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "เพิ่มวัคซีนใหม่",
                    "$name${if (scheduledDate != null) " • นัดวันที่ $scheduledDate" else ""}"
                )
            }
        }
    }

    /**
     * Safety net: if the child has no vaccine records yet (e.g. created before the schedule
     * was auto-seeded at onboarding), seed the full Thai EPI 0–3y schedule and reload.
     */
    // Guards against the seeding race: the screen's LaunchedEffect can fire ensureVaccineSchedule()
    // several times in quick succession (recompositions), and two coroutines could each read an
    // empty table before either insert lands — producing 2–4× duplicate schedules. We serialize on
    // a Mutex AND remember which child we've already ensured this session.
    private val vaccineSeedMutex = kotlinx.coroutines.sync.Mutex()
    private var vaccineSeededChildId: String? = null

    fun ensureVaccineSchedule() {
        if (isGuest()) return
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            vaccineSeedMutex.withLock {
                if (vaccineSeededChildId != child.id) {
                    val existing = healthRepo.getVaccineRecords(child.id).getOrDefault(emptyList())
                    if (existing.isEmpty()) {
                        healthRepo.insertDefaultVaccines(child.id, child.birthdate)
                    }
                    vaccineSeededChildId = child.id
                }
            }
            healthRepo.refreshVaccineStatuses(child.id)
            healthRepo.getVaccineRecords(child.id).onSuccess {
                _uiState.value = _uiState.value.copy(vaccines = it)
            }
        }
    }

    /** Edit / reschedule a vaccine (name, appointment date, description, place). */
    fun updateVaccineSchedule(record: VaccineRecord, name: String, scheduledDate: String?, description: String?, place: String?) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.updateVaccineSchedule(record.id, name, scheduledDate, description, place).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "อัปเดตกำหนดการวัคซีน",
                    "$name${if (scheduledDate != null) " • นัดวันที่ $scheduledDate" else ""}"
                )
            }
        }
    }

    /** Mark a vaccine as skipped with a reason (like skipping a development milestone). */
    fun skipVaccine(record: VaccineRecord, reason: String) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.skipVaccine(record.id, reason.ifBlank { null }).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
            }
        }
    }

    fun uploadVaccineCertificate(
        record: VaccineRecord,
        bytes: ByteArray,
        ext: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        if (isGuest()) { promptRegistration(); onDone(false); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch onDone(false)
            healthRepo.uploadVaccineCertificate(childId, record.id, bytes, ext)
                .onSuccess { path ->
                    healthRepo.registerVaccineCertificateInVault(
                        childId = childId,
                        filePath = path,
                        vaccineName = record.vaccineName,
                        fileSizeBytes = bytes.size
                    )
                    healthRepo.updateVaccineRecord(record.copy(certificateUrl = path))
                        .onSuccess {
                            healthRepo.getVaccineRecords(childId).onSuccess { list ->
                                _uiState.value = _uiState.value.copy(vaccines = list)
                            }
                            onDone(true)
                        }
                        .onFailure { onDone(false) }
                }
                .onFailure { onDone(false) }
        }
    }

    fun deleteVaccine(record: VaccineRecord) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            if (!ensureChildOwner(childId)) return@launch
            healthRepo.deleteVaccineRecord(record.id).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                    "ลบรายการวัคซีน",
                    "ลบ ${record.vaccineName} ออกจากสมุดวัคซีนแล้ว"
                )
            }
        }
    }

    fun applyScannedVaccines(names: Set<String>, onDone: (Int) -> Unit = {}) {
        if (isGuest()) { promptRegistration(); onDone(0); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch onDone(0)
            val today = java.time.LocalDate.now().toString()
            var applied = 0
            _uiState.value.vaccines.filter { it.vaccineName in names }.forEach { rec ->
                healthRepo.updateVaccineRecord(
                    rec.copy(
                        status = "completed",
                        dateGiven = rec.dateGiven ?: today,
                        source = "moph_book"
                    )
                ).onSuccess { applied++ }
            }
            healthRepo.getVaccineRecords(childId).onSuccess {
                _uiState.value = _uiState.value.copy(vaccines = it)
            }
            onDone(applied)
        }
    }

    fun toggleVaccine(record: VaccineRecord) {
        if (isGuest()) { promptRegistration(); return }
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val nowComplete = record.status != "completed"
            val today = java.time.LocalDate.now().toString()
            healthRepo.updateVaccineStatus(
                record.id,
                if (nowComplete) "completed" else "upcoming",
                if (nowComplete) today else null
            ).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess { _uiState.value = _uiState.value.copy(vaccines = it) }
                if (nowComplete) {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "บันทึกการฉีดวัคซีน",
                        "${record.vaccineName} เสร็จสิ้นแล้ว"
                    )
                }
            }
        }
    }

    fun calculateAge(isThai: Boolean = true): String {
        val child = _uiState.value.child ?: return ""
        return try {
            val parts = child.birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            var years = now.get(java.util.Calendar.YEAR) - year
            var months = now.get(java.util.Calendar.MONTH) + 1 - month
            if (months < 0) { years--; months += 12 }
            if (isThai) {
                if (years > 0) "$years ปี $months เดือน" else "$months เดือน"
            } else {
                if (years > 0) "$years y $months mo" else "$months mo"
            }
        } catch (e: Exception) { "" }
    }

    fun getAgeMonths(): Int {
        val child = _uiState.value.child ?: return 0
        return try {
            val parts = child.birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            (now.get(java.util.Calendar.YEAR) - year) * 12 + (now.get(java.util.Calendar.MONTH) + 1 - month)
        } catch (e: Exception) { 0 }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
