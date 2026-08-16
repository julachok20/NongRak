package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChildAccessActivity
import com.lullabyai.app.data.model.ChildAccessInvite
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildRole
import com.lullabyai.app.data.model.InviteCandidate
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessPermissionUiState(
    val childId: String? = null,
    val childName: String = "",
    val members: List<ChildAccessMember> = emptyList(),
    /** The signed-in user, so the screen can tell "me" from everyone else in the list. */
    val myUserId: String? = null,
    val myRole: String? = null,
    val activity: List<ChildAccessActivity> = emptyList(),
    /**
     * The roles this child's family actually has — the starter three plus anything they renamed or
     * created in Manage Roles. An invite offers these, not a list fixed in the app.
     */
    val roles: List<ChildRole> = emptyList(),
    val selectedMember: ChildAccessMember? = null,
    val invite: ChildAccessInvite? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val inviteCreated: Boolean = false,
    val savedOk: Boolean = false,
    /** Matches for the invite name box. Empty until the query is long enough to be a real search. */
    val candidates: List<InviteCandidate> = emptyList(),
    val searching: Boolean = false,
    val transferring: Boolean = false,
    val transferredOk: Boolean = false
) {
    /** Inviting people is an owner/co-owner action; everyone else sees the list read-only. */
    val canInvite: Boolean get() = AccessRoleLabels.isFullAccess(myRole)
    val isOwner: Boolean get() = myRole == "owner"
}

class AccessPermissionViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessPermissionUiState())
    val uiState: StateFlow<AccessPermissionUiState> = _uiState.asStateFlow()

    /** Held so each keystroke cancels the previous lookup instead of racing it. */
    private var searchJob: Job? = null

    fun load(childId: String? = null, childName: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            val name = childName
                ?: childRepo.getSelectedChild().getOrNull()?.name
                ?: ""
            _uiState.update { it.copy(loading = true, error = null, childId = id, childName = name) }
            val members = childRepo.listAccessMembers(id).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val activity = childRepo.listAccessActivity(id).getOrDefault(emptyList())
            // Empty falls back to the starter roles in the UI, which are always valid.
            val roles = childRepo.listChildRoles(id).getOrDefault(emptyList())
            val me = runCatching { supabase.auth.currentUserOrNull()?.id }.getOrNull()
            _uiState.update {
                it.copy(
                    loading = false,
                    members = members,
                    activity = activity,
                    roles = roles,
                    myUserId = me,
                    myRole = members.find { m -> m.userId == me }?.role,
                    selectedMember = it.selectedMember?.let { sel ->
                        members.find { m -> m.userId == sel.userId }
                    }
                )
            }
        }
    }

    fun selectMember(userId: String) {
        val m = _uiState.value.members.find { it.userId == userId }
        _uiState.update { it.copy(selectedMember = m, savedOk = false, error = null) }
    }

    fun createInvite(
        role: String,
        inviteType: String,
        inviteeName: String? = null,
        inviteeEmail: String? = null
    ) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, inviteCreated = false) }
            childRepo.generateAccessInvite(
                childId = childId,
                role = role,
                inviteType = inviteType,
                inviteeEmail = inviteeEmail?.ifBlank { null },
                inviteeName = inviteeName?.ifBlank { null }
            ).onSuccess { invite ->
                _uiState.update {
                    it.copy(saving = false, invite = invite, inviteCreated = true)
                }
                load(childId, _uiState.value.childName)
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    fun regenerateQr(role: String = "co_owner") {
        createInvite(role = role, inviteType = "qr")
    }

    /**
     * Looks up people matching what has been typed into the invite name box.
     *
     * Debounced, and silent under 3 characters — the RPC returns nothing there anyway, but not
     * asking at all keeps a people-search off the wire on every keystroke. Failures clear the
     * list rather than surfacing an error: a non-owner simply never sees suggestions.
     */
    fun searchCandidates(query: String) {
        searchJob?.cancel()
        val childId = _uiState.value.childId
        if (childId == null || query.trim().length < 3) {
            _uiState.update { it.copy(candidates = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(searching = true) }
            val found = childRepo.searchInviteCandidates(childId, query.trim()).getOrDefault(emptyList())
            _uiState.update { it.copy(candidates = found, searching = false) }
        }
    }

    fun clearCandidates() = _uiState.update { it.copy(candidates = emptyList()) }

    fun clearInviteFlag() {
        _uiState.update { it.copy(inviteCreated = false) }
    }

    fun savePermissions(member: ChildAccessMember) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedOk = false) }
            childRepo.updateMemberPermissions(
                childId = childId,
                userId = member.userId,
                role = member.role,
                canViewHealth = member.canViewHealth,
                canLogSymptoms = member.canLogSymptoms,
                canEditGrowth = member.canEditGrowth,
                canViewAppointments = member.canViewAppointments,
                canBookDoctor = member.canBookDoctor,
                canCancelAppointments = member.canCancelAppointments,
                canViewDocuments = member.canViewDocuments,
                canUploadDocuments = member.canUploadDocuments,
                canEditProfile = member.canEditProfile
            ).onSuccess {
                _uiState.update { it.copy(saving = false, savedOk = true, selectedMember = member) }
                load(childId, _uiState.value.childName)
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    /**
     * Flips the "helps care for this child" tick. Optimistic: the row updates immediately and
     * [load] reconciles, because a checkbox that waits on a round trip reads as broken.
     */
    fun setCaregiver(userId: String, value: Boolean) {
        val childId = _uiState.value.childId ?: return
        _uiState.update { s ->
            s.copy(members = s.members.map { if (it.userId == userId) it.copy(isCaregiver = value) else it })
        }
        viewModelScope.launch {
            childRepo.setMemberCaregiver(childId, userId, value)
                .onSuccess { load(childId, _uiState.value.childName) }
                .onFailure { e ->
                    // Put the tick back where it was — the server refused.
                    _uiState.update { s ->
                        s.copy(
                            error = e.message,
                            members = s.members.map {
                                if (it.userId == userId) it.copy(isCaregiver = !value) else it
                            }
                        )
                    }
                }
        }
    }

    fun removeMember(userId: String) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.removeAccessMember(childId, userId)
                .onSuccess {
                    _uiState.update { it.copy(saving = false, selectedMember = null) }
                    load(childId, _uiState.value.childName)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(saving = false, error = e.message) }
                }
        }
    }

    /** Transfers ownership of the current child to [targetUserId]. */
    fun transferOwnership(targetUserId: String, onComplete: ((Boolean) -> Unit)? = null) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(transferring = true, error = null, transferredOk = false) }
            childRepo.transferChildOwnership(childId, targetUserId)
                .onSuccess {
                    _uiState.update { it.copy(transferring = false, transferredOk = true) }
                    load(childId, _uiState.value.childName)
                    onComplete?.invoke(true)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(transferring = false, error = e.message) }
                    onComplete?.invoke(false)
                }
        }
    }
}

/** Shared bilingual labels for access roles (Stitch). */
object AccessRoleLabels {
    /**
     * The starter roles every family gets, widest access first. Deliberately only three — anything
     * else is a role the family creates for itself in Manage Roles, rather than a long list of
     * guesses nobody picks. Mirrors the system rows in `child_roles`.
     *
     * Must stay a subset of the server's `valid_child_roles()`, which is the authority and rejects
     * anything outside it (and always rejects 'owner', which cannot be handed out). That function
     * still accepts more keys than these: 'doctor' is granted through the doctor-connect flow
     * rather than from this picker, and older members may hold 'member' or 'other'. [title] keeps
     * labels for every key, so an existing member never renders as a bare role string.
     */
    val ROLES = listOf("co_owner", "relative", "nanny")

    /**
     * Roles that hold every permission by definition. Their permission grid is read-only —
     * there is nothing meaningful to grant or revoke, and letting a co-owner edit another
     * co-owner's rights only creates a way to lock the family out of its own child record.
     */
    fun isFullAccess(role: String?): Boolean = role == "owner" || role == "co_owner" || role == "member"

    /**
     * Determines whether [myRole] can remove [targetRole].
     * - Owner can remove anyone except self (must transfer or delete child).
     * - Co-owner can remove non-co-owner roles (relatives, nannies, etc.), but NOT other co-owners or the owner.
     */
    fun canRemove(myRole: String?, targetRole: String?, isSelf: Boolean): Boolean {
        if (isSelf) return myRole != "owner"
        if (myRole == "owner") return targetRole != "owner"
        if (myRole == "co_owner" || myRole == "member") {
            return targetRole != "owner" && targetRole != "co_owner" && targetRole != "member"
        }
        return false
    }

    fun title(role: String, isThai: Boolean): String = when (role) {
        "owner" -> if (isThai) "เจ้าของ" else "Owner"
        "co_owner", "member" -> if (isThai) "ผู้ปกครองร่วม (Co-owner)" else "Co-owner"
        "doctor" -> if (isThai) "แพทย์ (Doctor)" else "Doctor"
        "nanny" -> if (isThai) "พี่เลี้ยงเด็ก (Nanny)" else "Nanny / Caretaker"
        "grandparent" -> if (isThai) "ปู่ ย่า ตา ยาย" else "Grandparent"
        "relative" -> if (isThai) "ญาติ" else "Relative"
        "teacher" -> if (isThai) "ครู / ศูนย์เด็กเล็ก" else "Teacher / Daycare"
        "viewer" -> if (isThai) "ดูอย่างเดียว" else "View only"
        "other" -> if (isThai) "อื่นๆ (Other)" else "Other"
        else -> role
    }

    fun subtitle(role: String, isThai: Boolean): String = when (role) {
        "co_owner", "member" -> if (isThai) "จัดการข้อมูลและเชิญผู้อื่นได้" else "Full co-parent control"
        "doctor" -> if (isThai) "ดูข้อมูลสุขภาพและอัปโหลดเอกสารตรวจ" else "View health & upload clinical docs"
        "nanny" -> if (isThai) "บันทึกอาการประจำวันและดูแลทั่วไป" else "Daily care logging & monitoring"
        "grandparent" -> if (isThai) "ดูข้อมูลและบันทึกอาการได้ แต่แก้ไขประวัติไม่ได้"
                         else "Can view and log, but not change the record"
        "relative" -> if (isThai) "ดูข้อมูลสุขภาพและตารางนัดหมาย"
                      else "View health info and appointments"
        "teacher" -> if (isThai) "ดูข้อมูลสุขภาพและบันทึกอาการที่สังเกตเห็น"
                     else "View health info and log what they observe"
        "viewer" -> if (isThai) "ดูข้อมูลสุขภาพอย่างเดียว สิทธิ์น้อยที่สุด"
                    else "Health info only — the most limited access"
        "other" -> if (isThai) "สิทธิ์เข้าถึงแบบกำหนดเอง" else "Custom limited access"
        else -> ""
    }

    /**
     * The badge on a member card in the Stitch design. Read off capabilities rather than the
     * role name, so a custom role or a hand-edited permission grid still describes itself
     * honestly instead of inheriting a label it no longer earns.
     */
    fun accessLevel(member: ChildAccessMember, isThai: Boolean): Triple<String, Boolean, Boolean> {
        val full = isFullAccess(member.role)
        val canLog = member.canLogSymptoms || member.canEditGrowth || member.canUploadDocuments
        val label = when {
            full -> if (isThai) "สิทธิ์จัดการทั้งหมด" else "Full access"
            canLog -> if (isThai) "บันทึกข้อมูลรายวัน" else "Can record"
            else -> if (isThai) "ดูข้อมูลเท่านั้น" else "View only"
        }
        return Triple(label, full, canLog)
    }

    fun activityLabel(eventType: String, isThai: Boolean): String = when (eventType) {
        "invite_created" -> if (isThai) "สร้างรหัสเชิญ" else "Invite created"
        "qr_generated" -> if (isThai) "สร้าง QR Code" else "QR generated"
        "access_requested" -> if (isThai) "มีคำขอเข้าถึง" else "Access requested"
        "access_approved" -> if (isThai) "อนุมัติการเข้าถึง" else "Access approved"
        "access_rejected" -> if (isThai) "ปฏิเสธการเข้าถึง" else "Access rejected"
        "permissions_updated" -> if (isThai) "อัปเดตสิทธิ์" else "Permissions updated"
        "member_removed" -> if (isThai) "ถอดสิทธิ์ผู้ใช้" else "Member removed"
        "caregiver_marked" -> if (isThai) "ตั้งเป็นผู้ช่วยดูแลเด็ก" else "Marked as caregiver"
        "caregiver_unmarked" -> if (isThai) "ยกเลิกผู้ช่วยดูแลเด็ก" else "Unmarked as caregiver"
        else -> eventType
    }
}
