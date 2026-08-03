package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChildSyncMatch
import com.lullabyai.app.data.model.ChildSyncRequest
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Both halves of "เชื่อมต่อข้อมูลลูก" (Sync Child Profile):
 *
 *  - **Requester side** — search by name+birthdate, redeem a 6-char access code, or scan a share QR.
 *    Every path only ever creates a *pending* request; nothing is granted client-side.
 *  - **Owner side** — [startWatching] opens a Supabase Realtime subscription so a scan that happens
 *    while the app is open raises [AccessRequestUiState.popup] (approve/deny) immediately. Requests
 *    that arrive while the app is closed are picked up by [loadPending] on the next launch and shown
 *    in the pending list instead. No FCM involved.
 */
data class AccessRequestUiState(
    // --- requester side ---
    val role: String = ChildRepository.DEFAULT_REQUEST_ROLE,
    val childQuery: String = "",
    val birthdate: String = "",
    val accessCode: String = "",
    val searching: Boolean = false,
    val searchResults: List<ChildSyncMatch> = emptyList(),
    val searchRan: Boolean = false,
    val selectedMatchId: String? = null,
    val submitting: Boolean = false,
    val requestSent: Boolean = false,
    val error: String? = null,
    // --- owner side ---
    val pending: List<ChildSyncRequest> = emptyList(),
    val childNames: Map<String, String> = emptyMap(),
    val popup: ChildSyncRequest? = null,
    val resolving: Boolean = false
) {
    val canSubmit: Boolean
        get() = !submitting && (accessCode.isNotBlank() || selectedMatchId != null)

    fun nameFor(childId: String): String = childNames[childId] ?: ""
}

class AccessRequestViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessRequestUiState())
    val uiState: StateFlow<AccessRequestUiState> = _uiState.asStateFlow()

    private var watchJob: Job? = null

    // ---- requester side ----------------------------------------------------------------------

    fun setRole(role: String) = _uiState.update { it.copy(role = role, error = null) }

    fun setChildQuery(value: String) =
        _uiState.update { it.copy(childQuery = value, selectedMatchId = null, searchRan = false, error = null) }

    fun setBirthdate(value: String) =
        _uiState.update { it.copy(birthdate = value, selectedMatchId = null, searchRan = false, error = null) }

    /** Access codes are 6 chars, letters+digits, upper-cased as the user types (Stitch). */
    fun setAccessCode(value: String) = _uiState.update {
        it.copy(accessCode = value.filter { c -> c.isLetterOrDigit() }.uppercase().take(6), error = null)
    }

    fun selectMatch(childId: String) = _uiState.update { it.copy(selectedMatchId = childId, error = null) }

    /**
     * Exact name + birthdate lookup. Both are required — `search_child_for_sync` matches on both, so
     * a name alone would let anyone enumerate other families' children.
     */
    fun search() {
        val s = _uiState.value
        if (s.childQuery.isBlank() || s.birthdate.isBlank()) {
            _uiState.update { it.copy(error = ERR_NEED_NAME_AND_BIRTHDATE) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(searching = true, error = null, searchResults = emptyList()) }
            childRepo.searchChildForSync(s.childQuery.trim(), s.birthdate.trim())
                .onSuccess { matches ->
                    _uiState.update {
                        it.copy(
                            searching = false,
                            searchRan = true,
                            searchResults = matches,
                            selectedMatchId = matches.singleOrNull()?.childId
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(searching = false, searchRan = true, error = safeError(e)) }
                }
        }
    }

    /** The Stitch "ยืนยันการเชื่อมต่อ" button: access code wins if filled, otherwise the picked match. */
    fun submit() {
        val s = _uiState.value
        when {
            s.accessCode.isNotBlank() -> sendRequest { childRepo.requestSyncByCode(s.accessCode, s.role) }
            s.selectedMatchId != null -> sendRequest { childRepo.requestSyncBySearch(s.selectedMatchId, s.role) }
            else -> _uiState.update { it.copy(error = ERR_NOTHING_TO_SUBMIT) }
        }
    }

    /**
     * A scanned share QR. Fires straight away — the whole point of the scan is that the owner's phone
     * decides, so there is nothing for the scanner to confirm first. Still only a pending request.
     */
    fun submitScannedQr(rawPayload: String) {
        val role = _uiState.value.role
        sendRequest { childRepo.requestSyncByQr(rawPayload, role) }
    }

    private fun sendRequest(call: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _uiState.update { it.copy(submitting = true, error = null, requestSent = false) }
            call()
                .onSuccess { _uiState.update { it.copy(submitting = false, requestSent = true) } }
                .onFailure { e -> _uiState.update { it.copy(submitting = false, error = safeError(e)) } }
        }
    }

    fun clearRequestSent() = _uiState.update {
        it.copy(requestSent = false, accessCode = "", selectedMatchId = null, searchResults = emptyList(), searchRan = false)
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    // ---- owner side -------------------------------------------------------------------------

    /** Pending requests + the child names needed to render them, both RLS-scoped to this user. */
    fun loadPending(raisePopup: Boolean = false) {
        viewModelScope.launch {
            val pending = childRepo.getPendingSyncRequests().getOrElse {
                _uiState.update { st -> st.copy(error = safeError(it)) }
                return@launch
            }
            val names = childRepo.getChildren().getOrDefault(emptyList()).associate { it.id to it.name }
            _uiState.update {
                it.copy(
                    pending = pending,
                    childNames = names,
                    // Keep an already-open popup on screen; only auto-raise for a genuinely new arrival.
                    popup = it.popup?.let { open -> pending.find { p -> p.id == open.id } }
                        ?: pending.firstOrNull().takeIf { _ -> raisePopup }
                )
            }
        }
    }

    /**
     * Opens the Realtime subscription for the signed-in owner. Idempotent — calling it again while a
     * subscription is live is a no-op, so every access screen can call it on entry.
     *
     * Also polls every 4s as a fallback when Realtime tables are not in the `supabase_realtime`
     * publication (logcat: "Unable to subscribe to changes with given parameters").
     */
    fun startWatching() {
        if (watchJob?.isActive == true) return
        val ownerId = supabase.auth.currentUserOrNull()?.id ?: return
        loadPending()
        watchJob = viewModelScope.launch {
            launch {
                while (true) {
                    delay(4_000)
                    loadPending(raisePopup = true)
                }
            }
            runCatching {
                childRepo.watchPendingSyncRequests(ownerId).collect { loadPending(raisePopup = true) }
            }
        }
    }

    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
    }

    fun showPopup(request: ChildSyncRequest) = _uiState.update { it.copy(popup = request, error = null) }

    fun dismissPopup() = _uiState.update { it.copy(popup = null) }

    /** approve = true grants access via `confirm_child_sync_request`; false marks it rejected. */
    fun resolve(requestId: String, approve: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(resolving = true, error = null) }
            childRepo.confirmSyncRequest(requestId, approve)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            resolving = false,
                            popup = null,
                            pending = it.pending.filterNot { p -> p.id == requestId }
                        )
                    }
                    loadPending()
                }
                .onFailure { e ->
                    // Someone else (or another device) already resolved it — drop it either way.
                    _uiState.update {
                        it.copy(resolving = false, popup = null, error = safeError(e))
                    }
                    loadPending()
                }
        }
    }

    override fun onCleared() {
        stopWatching()
        super.onCleared()
    }

    companion object {
        const val ERR_NEED_NAME_AND_BIRTHDATE = "need_name_and_birthdate"
        const val ERR_NOTHING_TO_SUBMIT = "nothing_to_submit"
        const val ERR_INVALID_CODE = "invalid_code"
        const val ERR_ALREADY_MEMBER = "already_member"
        const val ERR_NOT_FOUND = "not_found"
        const val ERR_ALREADY_RESOLVED = "already_resolved"
        const val ERR_GENERIC = "generic"

        /**
         * Maps a backend failure to a stable key. Raw throwable messages are never surfaced: they
         * can carry the child id, the requester's user id, or the invite token, none of which the
         * scanner is entitled to see. [AccessRequestStrings] turns the key into user-facing text.
         */
        fun safeError(e: Throwable): String {
            val raw = (e.message ?: "").lowercase()
            return when {
                "invalid or expired code" in raw || "empty_code" in raw -> ERR_INVALID_CODE
                "already a member" in raw -> ERR_ALREADY_MEMBER
                "child not found" in raw || "request not found" in raw -> ERR_NOT_FOUND
                "already resolved" in raw -> ERR_ALREADY_RESOLVED
                "not authorized" in raw -> ERR_NOT_FOUND
                else -> ERR_GENERIC
            }
        }
    }
}

/** Bilingual copy for the error keys above, kept next to the keys so they can't drift apart. */
object AccessRequestStrings {
    fun error(key: String?, isThai: Boolean): String? = when (key) {
        null -> null
        AccessRequestViewModel.ERR_NEED_NAME_AND_BIRTHDATE ->
            if (isThai) "กรุณาระบุทั้งชื่อลูกและวันเกิดให้ตรงกัน" else "Enter both the child's name and birthdate"
        AccessRequestViewModel.ERR_NOTHING_TO_SUBMIT ->
            if (isThai) "กรอกรหัสการเข้าถึง สแกน QR หรือเลือกผลการค้นหาก่อน"
            else "Enter an access code, scan a QR, or pick a search result first"
        AccessRequestViewModel.ERR_INVALID_CODE ->
            if (isThai) "รหัสไม่ถูกต้อง ถูกใช้แล้ว หรือหมดอายุ — ขอรหัสใหม่จากเจ้าของบัญชี"
            else "That code is invalid, already used, or expired — ask the owner for a new one"
        AccessRequestViewModel.ERR_ALREADY_MEMBER ->
            if (isThai) "คุณมีสิทธิ์เข้าถึงข้อมูลลูกคนนี้อยู่แล้ว" else "You already have access to this child"
        AccessRequestViewModel.ERR_NOT_FOUND ->
            if (isThai) "ไม่พบคำขอหรือข้อมูลที่ตรงกัน" else "No matching request or record found"
        AccessRequestViewModel.ERR_ALREADY_RESOLVED ->
            if (isThai) "คำขอนี้ถูกดำเนินการไปแล้ว" else "This request was already handled"
        else ->
            if (isThai) "เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง" else "Something went wrong, please try again"
    }
}
