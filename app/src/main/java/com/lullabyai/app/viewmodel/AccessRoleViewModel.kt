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

/**
 * Capability vocabulary `child_can()` / `valid_child_capabilities()` understand.
 * Stitch Permission Settings Detail is the 14 editor toggles in [SECTIONS];
 * [CANCEL_APPOINTMENTS] stays in the allowlist so older grants are not wiped on save.
 */
object AccessCapabilities {
    const val VIEW_HEALTH = "view_health"
    const val LOG_SYMPTOMS = "log_symptoms"
    const val EDIT_GROWTH = "edit_growth"
    const val VIEW_APPOINTMENTS = "view_appointments"
    const val BOOK_DOCTOR = "book_doctor"
    const val CANCEL_APPOINTMENTS = "cancel_appointments"
    const val VIEW_DOCUMENTS = "view_documents"
    const val UPLOAD_DOCUMENTS = "upload_documents"
    const val EDIT_PROFILE = "edit_profile"
    const val VIEW_ACTIVITIES = "view_activities"
    const val LOG_ACTIVITIES = "log_activities"
    const val LOG_MEDICATIONS = "log_medications"
    const val LOG_FEEDING = "log_feeding"
    const val VIEW_NUTRITION = "view_nutrition"
    const val LOG_SLEEP = "log_sleep"

    /** New daily-care keys. Missing on a row means "never saved the new grid" — treat as granted. */
    val DAILY_CARE = setOf(
        VIEW_ACTIVITIES, LOG_ACTIVITIES, LOG_MEDICATIONS, LOG_FEEDING, VIEW_NUTRITION, LOG_SLEEP
    )

    data class ToggleSpec(val key: String, val titleTh: String, val titleEn: String)
    data class SectionSpec(
        val id: String,
        val titleTh: String,
        val titleEn: String,
        val items: List<ToggleSpec>
    )

    val SECTIONS: List<SectionSpec> = listOf(
        SectionSpec(
            id = "health",
            titleTh = "ข้อมูลสุขภาพและพัฒนาการ",
            titleEn = "Health & Development",
            items = listOf(
                ToggleSpec(VIEW_HEALTH, "ดูประวัติสุขภาพและการเจริญเติบโต", "View Health & Growth History"),
                ToggleSpec(LOG_SYMPTOMS, "บันทึกอาการป่วยและอุณหภูมิ", "Log Symptoms & Temperature"),
                ToggleSpec(EDIT_GROWTH, "แก้ไขข้อมูลเติบโตและพัฒนาการ", "Edit Growth & Milestone Data")
            )
        ),
        SectionSpec(
            id = "visits",
            titleTh = "การพบแพทย์และกิจกรรมการดูแล",
            titleEn = "Doctor Visits & Care Activities",
            items = listOf(
                ToggleSpec(VIEW_APPOINTMENTS, "ดูประวัติการพบแพทย์", "View Doctor Visit History"),
                ToggleSpec(BOOK_DOCTOR, "บันทึกการพบแพทย์และการวินิจฉัย", "Log Doctor Visits & Clinical Notes"),
                ToggleSpec(VIEW_ACTIVITIES, "ดูบันทึกกิจกรรมประจำวัน", "View Daily Activity Records"),
                ToggleSpec(LOG_ACTIVITIES, "บันทึกกิจกรรมและกิจวัตร", "Log Activities & Routines")
            )
        ),
        SectionSpec(
            id = "meds",
            titleTh = "ยาและโภชนาการ",
            titleEn = "Medications & Nutrition",
            items = listOf(
                ToggleSpec(LOG_MEDICATIONS, "จัดการตารางยาและบันทึกการให้ยา", "Manage & Log Medications"),
                ToggleSpec(LOG_FEEDING, "บันทึกการกินนมและอาหาร", "Log Feeding & Meals"),
                ToggleSpec(VIEW_NUTRITION, "ดูข้อมูลโภชนาการ", "View Nutrition Details"),
                ToggleSpec(LOG_SLEEP, "บันทึกการนอนหลับ", "Log Sleep Sessions")
            )
        ),
        SectionSpec(
            id = "docs",
            titleTh = "เอกสารและข้อมูลโปรไฟล์",
            titleEn = "Documents & Child Profile",
            items = listOf(
                ToggleSpec(VIEW_DOCUMENTS, "ดูคลังเอกสาร", "View Document Vault"),
                ToggleSpec(UPLOAD_DOCUMENTS, "อัปโหลดเอกสารใหม่", "Upload Documents"),
                ToggleSpec(EDIT_PROFILE, "แก้ไขโปรไฟล์ลูก", "Edit Child Profile")
            )
        )
    )

    val EDITOR = SECTIONS.flatMap { it.items.map { item -> item.key } }

    val ALL = EDITOR + CANCEL_APPOINTMENTS

    fun spec(cap: String): ToggleSpec? = SECTIONS.asSequence().flatMap { it.items.asSequence() }.find { it.key == cap }

    fun label(cap: String, isThai: Boolean): String {
        spec(cap)?.let { return if (isThai) it.titleTh else it.titleEn }
        return when (cap) {
            CANCEL_APPOINTMENTS -> if (isThai) "ยกเลิกนัดหมาย" else "Cancel appointments"
            else -> cap
        }
    }

    fun actionTitle(cap: String, isThai: Boolean): String = label(cap, isThai)

    fun roleBadge(role: String): String = when (role) {
        "nanny" -> "Nanny / Caretaker Role"
        "owner" -> "Owner Role"
        "co_owner", "member" -> "Co-owner Role"
        "doctor" -> "Doctor Role"
        "grandparent" -> "Grandparent Role"
        "relative" -> "Relative Role"
        "teacher" -> "Teacher / Daycare Role"
        "viewer" -> "View only Role"
        else -> "${AccessRoleLabels.title(role, isThai = false)} Role"
    }

    fun effectiveCaps(member: com.lullabyai.app.data.model.ChildAccessMember): Set<String> {
        val stored = member.capabilities.filter { it in ALL }.toMutableSet()
        if (stored.isEmpty()) {
            if (member.canViewHealth) stored += VIEW_HEALTH
            if (member.canLogSymptoms) stored += LOG_SYMPTOMS
            if (member.canEditGrowth) stored += EDIT_GROWTH
            if (member.canViewAppointments) stored += VIEW_APPOINTMENTS
            if (member.canBookDoctor) stored += BOOK_DOCTOR
            if (member.canCancelAppointments) stored += CANCEL_APPOINTMENTS
            if (member.canViewDocuments) stored += VIEW_DOCUMENTS
            if (member.canUploadDocuments) stored += UPLOAD_DOCUMENTS
            if (member.canEditProfile) stored += EDIT_PROFILE
        }
        // Rows saved before the Stitch daily-care toggles exist: keep daily care on until
        // the owner explicitly saves this grid (which writes the keys on or off).
        if (stored.none { it in DAILY_CARE }) stored += DAILY_CARE
        if (member.canCancelAppointments || CANCEL_APPOINTMENTS in member.capabilities) {
            stored += CANCEL_APPOINTMENTS
        }
        return stored
    }

    fun holds(member: com.lullabyai.app.data.model.ChildAccessMember, cap: String): Boolean {
        if (AccessRoleLabels.isFullAccess(member.role) || member.role == "owner") return true
        return cap in effectiveCaps(member)
    }

    fun accessSummary(caps: Set<String>, isThai: Boolean): String {
        val canWrite = caps.any {
            it.startsWith("log_") || it == EDIT_GROWTH || it == EDIT_PROFILE ||
                it == UPLOAD_DOCUMENTS || it == BOOK_DOCTOR
        }
        return when {
            !canWrite -> if (isThai) "อ่านบันทึกประจำวันเท่านั้น" else "View daily records only"
            LOG_ACTIVITIES in caps && BOOK_DOCTOR !in caps && EDIT_GROWTH !in caps ->
                if (isThai) "บันทึกกิจกรรมประจำวัน" else "Daily care logging"
            else -> if (isThai) "สิทธิ์กำหนดเอง" else "Custom access"
        }
    }
}
