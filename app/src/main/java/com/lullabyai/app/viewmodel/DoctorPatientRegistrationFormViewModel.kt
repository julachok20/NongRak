package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One row of the Stitch "ประวัติวัคซีน" list on the doctor-filled registration form. */
data class WalkInVaccineEntry(val name: String, val dateGiven: String)

data class DoctorPatientRegistrationFormUiState(
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

/**
 * Backs the doctor-authored registration form (Stitch "Patient Registration with Appointment
 * Details"). A walk-in has no parent account, so registering does three writes in order:
 *
 *  1. `children` row owned by THIS doctor (RLS `insert own children` requires user_id = auth.uid()),
 *  2. Pedia Connect intake + guardian contact (`register_pediaconnect_intake` RPC),
 *  3. an approved `pediaconnect_doctor_links` row plus a **confirmed** appointment — the doctor is
 *     both requester and approver here, so there is nothing left to approve.
 *
 * When the parent later registers the same child in the app, the two records stay separate: this
 * child row belongs to the clinic, and the parent's own `children` row remains their SSOT.
 */
class DoctorPatientRegistrationFormViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val pediaRepo = PediaConnectRepository()
    private val linkRepo = PediaConnectLinkRepository()
    private val doctorRepo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorPatientRegistrationFormUiState())
    val uiState: StateFlow<DoctorPatientRegistrationFormUiState> = _uiState

    fun register(
        fullName: String,
        nickname: String,
        birthdate: String,
        gender: String,
        bloodGroup: String,
        weightKg: String,
        heightCm: String,
        allergies: String,
        chronicConditions: String,
        vaccines: List<WalkInVaccineEntry>,
        visitReason: String,
        appointmentDate: String,
        guardianName: String,
        guardianRelationship: String,
        guardianPhone: String,
        guardianEmail: String,
        isThai: Boolean
    ) {
        val doctorId = supabase.auth.currentUserOrNull()?.id
        if (doctorId == null) {
            _uiState.value = _uiState.value.copy(
                error = if (isThai) "กรุณาเข้าสู่ระบบใหม่" else "Please sign in again"
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = DoctorPatientRegistrationFormUiState(isSaving = true)

            // vaccine_records inserts are gated on child_can(...), which a clinic-created child may
            // not satisfy, so the doses the doctor typed are preserved in notes rather than risking
            // a silent drop. The parent's own vaccine book stays the SSOT.
            val notes = buildString {
                nickname.trim().takeIf { it.isNotBlank() }?.let { appendLine(if (isThai) "ชื่อเล่น: $it" else "Nickname: $it") }
                chronicConditions.trim().takeIf { it.isNotBlank() }?.let { appendLine(if (isThai) "โรคประจำตัว: $it" else "Chronic: $it") }
                if (vaccines.isNotEmpty()) {
                    appendLine(if (isThai) "ประวัติวัคซีน:" else "Vaccine history:")
                    vaccines.forEach { v ->
                        appendLine("- ${v.name}" + v.dateGiven.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty())
                    }
                }
                appendLine(if (isThai) "ลงทะเบียนโดยแพทย์ (walk-in)" else "Registered by doctor (walk-in)")
            }.trim()

            val child = childRepo.createChild(
                Child(
                    userId = doctorId,
                    name = fullName.trim(),
                    birthdate = birthdate.trim(),
                    gender = gender,
                    bloodGroup = bloodGroup.takeIf { it.isNotBlank() },
                    allergies = allergies.trim().takeIf { it.isNotBlank() },
                    notes = notes.takeIf { it.isNotBlank() },
                    weightKg = weightKg.toDoubleOrNull(),
                    heightCm = heightCm.toDoubleOrNull()
                )
            ).getOrElse { e ->
                _uiState.value = DoctorPatientRegistrationFormUiState(
                    error = (if (isThai) "สร้างข้อมูลผู้ป่วยไม่สำเร็จ: " else "Could not create the patient: ") + e.message
                )
                return@launch
            }

            pediaRepo.registerIntake(
                childId = child.id,
                name = fullName.trim(),
                birthdate = birthdate.trim(),
                gender = gender,
                bloodGroup = bloodGroup.takeIf { it.isNotBlank() },
                allergies = allergies.trim().takeIf { it.isNotBlank() },
                notes = notes.takeIf { it.isNotBlank() },
                mainGuardianUserId = null,
                mainGuardianName = guardianName.trim(),
                mainGuardianRelationship = guardianRelationship,
                mainGuardianPhone = guardianPhone.trim(),
                mainGuardianEmail = guardianEmail.trim()
            ).onFailure { e ->
                _uiState.value = DoctorPatientRegistrationFormUiState(
                    error = (if (isThai) "บันทึกข้อมูลลงทะเบียนไม่สำเร็จ: " else "Could not save the intake: ") + e.message
                )
                return@launch
            }

            // The doctor registered this patient themselves, so the link is approved immediately.
            linkRepo.register(doctorId, child.id, visitReason.trim().takeIf { it.isNotBlank() })
                .onSuccess { link -> linkRepo.approve(link.id, doctorId) }

            val scheduledAt = appointmentDate.trim().takeIf { it.isNotBlank() }?.let { date ->
                // The form captures a date only; 09:00 local is a placeholder the doctor can move
                // with เลื่อนนัด on ตารางนัดหมาย.
                runCatching {
                    java.time.LocalDate.parse(date)
                        .atTime(9, 0)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toString()
                }.getOrNull()
            }
            val appointment = doctorRepo.requestAppointment(
                doctorId = doctorId,
                childId = child.id,
                slotId = null,
                reason = visitReason.trim().takeIf { it.isNotBlank() }
            ).getOrNull()
            if (appointment != null) {
                doctorRepo.updateAppointmentStatus(appointment.id, "confirmed", scheduledAt = scheduledAt)
            }

            _uiState.value = DoctorPatientRegistrationFormUiState(saved = true)
        }
    }
}
