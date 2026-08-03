package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChildAccessActivity
import com.lullabyai.app.data.model.ChildAccessInvite
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessPermissionUiState(
    val childId: String? = null,
    val childName: String = "",
    val members: List<ChildAccessMember> = emptyList(),
    val activity: List<ChildAccessActivity> = emptyList(),
    val selectedMember: ChildAccessMember? = null,
    val invite: ChildAccessInvite? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val inviteCreated: Boolean = false,
    val savedOk: Boolean = false
)

class AccessPermissionViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessPermissionUiState())
    val uiState: StateFlow<AccessPermissionUiState> = _uiState.asStateFlow()

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
            _uiState.update {
                it.copy(
                    loading = false,
                    members = members,
                    activity = activity,
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
}

/** Shared bilingual labels for access roles (Stitch). */
object AccessRoleLabels {
    val ROLES = listOf("co_owner", "doctor", "nanny", "other")

    fun title(role: String, isThai: Boolean): String = when (role) {
        "owner" -> if (isThai) "เจ้าของ" else "Owner"
        "co_owner", "member" -> if (isThai) "ผู้ปกครองร่วม (Co-owner)" else "Co-owner"
        "doctor" -> if (isThai) "แพทย์ (Doctor)" else "Doctor"
        "nanny" -> if (isThai) "พี่เลี้ยงเด็ก (Nanny)" else "Nanny / Caretaker"
        "other" -> if (isThai) "อื่นๆ (Other)" else "Other"
        else -> role
    }

    fun subtitle(role: String, isThai: Boolean): String = when (role) {
        "co_owner", "member" -> if (isThai) "จัดการข้อมูลและเชิญผู้อื่นได้" else "Full co-parent control"
        "doctor" -> if (isThai) "ดูข้อมูลสุขภาพและอัปโหลดเอกสารตรวจ" else "View health & upload clinical docs"
        "nanny" -> if (isThai) "บันทึกอาการประจำวันและดูแลทั่วไป" else "Daily care logging & monitoring"
        "other" -> if (isThai) "สิทธิ์เข้าถึงแบบกำหนดเอง" else "Custom limited access"
        else -> ""
    }

    fun activityLabel(eventType: String, isThai: Boolean): String = when (eventType) {
        "invite_created" -> if (isThai) "สร้างรหัสเชิญ" else "Invite created"
        "qr_generated" -> if (isThai) "สร้าง QR Code" else "QR generated"
        "access_requested" -> if (isThai) "มีคำขอเข้าถึง" else "Access requested"
        "access_approved" -> if (isThai) "อนุมัติการเข้าถึง" else "Access approved"
        "access_rejected" -> if (isThai) "ปฏิเสธการเข้าถึง" else "Access rejected"
        "permissions_updated" -> if (isThai) "อัปเดตสิทธิ์" else "Permissions updated"
        "member_removed" -> if (isThai) "ถอดสิทธิ์ผู้ใช้" else "Member removed"
        else -> eventType
    }
}
