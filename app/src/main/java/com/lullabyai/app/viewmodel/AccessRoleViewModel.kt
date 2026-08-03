package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.ChildRole
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccessRoleUiState(
    val childId: String? = null,
    val roles: List<ChildRole> = emptyList(),
    val members: List<ChildAccessMember> = emptyList(),
    val isOwner: Boolean = false,
    val editing: ChildRole? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val savedOk: Boolean = false
)

class AccessRoleViewModel(
    private val childRepo: ChildRepository = ChildRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessRoleUiState())
    val uiState: StateFlow<AccessRoleUiState> = _uiState.asStateFlow()

    fun load(childId: String? = null) {
        viewModelScope.launch {
            val id = childId ?: ChildSelection.id.value
                ?: childRepo.getSelectedChild().getOrNull()?.id
                ?: run {
                    _uiState.update { it.copy(error = "no_child") }
                    return@launch
                }
            _uiState.update { it.copy(loading = true, error = null, childId = id) }
            val roles = childRepo.listChildRoles(id).getOrElse { e ->
                _uiState.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val members = childRepo.listAccessMembers(id).getOrDefault(emptyList())
            val uid = supabase.auth.currentUserOrNull()?.id
            val childOwnerId = childRepo.getSelectedChild().getOrNull()?.userId
            val isOwner = uid != null && (
                members.any { it.userId == uid && it.role == "owner" }
                    || childOwnerId == uid
                )
            _uiState.update {
                it.copy(loading = false, roles = roles, members = members, isOwner = isOwner)
            }
        }
    }

    /** Opens the editor. Passing null starts a blank custom role. */
    fun edit(role: ChildRole?) {
        _uiState.update { it.copy(editing = role ?: ChildRole(), savedOk = false, error = null) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(editing = null, savedOk = false, error = null) }
    }

    fun toggleCapability(cap: String) {
        val current = _uiState.value.editing ?: return
        val next = if (cap in current.capabilities) current.capabilities - cap
                   else current.capabilities + cap
        _uiState.update { it.copy(editing = current.copy(capabilities = next)) }
    }

    fun updateDraft(nameTh: String? = null, nameEn: String? = null, key: String? = null) {
        val current = _uiState.value.editing ?: return
        _uiState.update {
            it.copy(
                editing = current.copy(
                    nameTh = nameTh ?: current.nameTh,
                    nameEn = nameEn ?: current.nameEn,
                    key = key ?: current.key
                )
            )
        }
    }

    fun saveRole() {
        val childId = _uiState.value.childId ?: return
        val draft = _uiState.value.editing ?: return
        val baseKey = draft.baseRoleKey?.takeIf { it.isNotBlank() }
            ?: draft.key.takeIf { draft.isSystem && it.isNotBlank() }

        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedOk = false) }

            val result = if (baseKey != null) {
                childRepo.upsertChildRoleOverride(
                    childId = childId,
                    baseRoleKey = baseKey,
                    nameTh = draft.nameTh.ifBlank { draft.nameEn },
                    nameEn = draft.nameEn.ifBlank { draft.nameTh },
                    capabilities = draft.capabilities
                )
            } else {
                val key = draft.key.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_")
                if (key.length < 2) {
                    _uiState.update { it.copy(saving = false, error = "invalid_key") }
                    return@launch
                }
                childRepo.upsertChildRole(
                    childId = childId,
                    key = key,
                    nameTh = draft.nameTh.ifBlank { draft.nameEn },
                    nameEn = draft.nameEn.ifBlank { draft.nameTh },
                    capabilities = draft.capabilities
                )
            }

            result.onSuccess {
                _uiState.update { it.copy(saving = false, savedOk = true, editing = null) }
                load(childId)
            }.onFailure { e ->
                _uiState.update { it.copy(saving = false, error = e.message) }
            }
        }
    }

    /** Removes a custom role, or resets a built-in role back to the global default for this child. */
    fun deleteRole(roleId: String) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null) }
            childRepo.deleteChildRole(roleId)
                .onSuccess {
                    _uiState.update { it.copy(saving = false, editing = null) }
                    load(childId)
                }
                .onFailure { e -> _uiState.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun assignRole(userId: String, roleId: String) {
        val childId = _uiState.value.childId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, error = null, savedOk = false) }
            childRepo.assignChildRole(childId, userId, roleId)
                .onSuccess {
                    _uiState.update { it.copy(saving = false, savedOk = true) }
                    load(childId)
                }
                .onFailure { e -> _uiState.update { it.copy(saving = false, error = e.message) } }
        }
    }

    fun clearFlags() {
        _uiState.update { it.copy(savedOk = false, error = null) }
    }
}

/** The nine capabilities `child_can()` understands, with bilingual labels for the role editor. */
object AccessCapabilities {
    val ALL = listOf(
        "view_health", "log_symptoms", "edit_growth",
        "view_appointments", "book_doctor", "cancel_appointments",
        "view_documents", "upload_documents", "edit_profile"
    )

    fun label(cap: String, isThai: Boolean): String = when (cap) {
        "view_health" -> if (isThai) "ดูข้อมูลสุขภาพ" else "View health data"
        "log_symptoms" -> if (isThai) "บันทึกอาการ" else "Log symptoms"
        "edit_growth" -> if (isThai) "แก้ไขการเจริญเติบโต" else "Edit growth records"
        "view_appointments" -> if (isThai) "ดูนัดหมาย" else "View appointments"
        "book_doctor" -> if (isThai) "นัดพบแพทย์" else "Book appointments"
        "cancel_appointments" -> if (isThai) "ยกเลิกนัดหมาย" else "Cancel appointments"
        "view_documents" -> if (isThai) "ดูเอกสาร" else "View documents"
        "upload_documents" -> if (isThai) "อัปโหลดเอกสาร" else "Upload documents"
        "edit_profile" -> if (isThai) "แก้ไขโปรไฟล์ลูก" else "Edit child profile"
        else -> cap
    }
}
