package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.intakeNameFromNotes
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PendingBookingRow(
    val appointment: Appointment,
    val patientName: String?,
    val birthdate: String? = null,
    val gender: String? = null,
    val guardianName: String? = null,
    val guardianRelationship: String? = null,
    val intakeNotes: String? = null
)

data class DoctorPatientRegistrationApprovalUiState(
    val pendingBookings: List<PendingBookingRow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Prefix stored on `appointments.notes` when a doctor rejects a booking request. */
fun doctorRejectionNotes(reason: String): String {
    val trimmed = reason.trim()
    require(trimmed.isNotBlank()) { "Rejection reason is required" }
    return "Rejected: $trimmed"
}

/** Pending **booking requests** (`appointments.status = pending`) at this clinic — per-booking doctor approval. */
class DoctorPatientRegistrationApprovalViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val pediaRepo = PediaConnectRepository()
    private val childRepo = ChildRepository()

    private val _uiState = MutableStateFlow(DoctorPatientRegistrationApprovalUiState())
    val uiState: StateFlow<DoctorPatientRegistrationApprovalUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            doctorRepo.pendingBookingsForDoctor(doctorId)
                .onSuccess { list ->
                    val rows = list.map { appt ->
                        val intake = pediaRepo.patientRecordForChild(appt.childId).getOrNull()
                        val child = childRepo.getChildById(appt.childId).getOrNull()
                        val parent = intake?.id?.let { pediaRepo.mainParentForPatient(it).getOrNull() }
                        PendingBookingRow(
                            appointment = appt,
                            patientName = intake?.name ?: child?.name ?: intakeNameFromNotes(appt.notes),
                            birthdate = intake?.birthdate ?: child?.birthdate,
                            gender = intake?.gender ?: child?.gender,
                            guardianName = parent?.fullName ?: guardianFromNotes(appt.notes),
                            guardianRelationship = parent?.relationship,
                            intakeNotes = appt.notes
                        )
                    }
                    _uiState.value = DoctorPatientRegistrationApprovalUiState(pendingBookings = rows, isLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun approve(appointmentId: String) {
        if (_uiState.value.isLoading) return
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val appt = doctorRepo.getAppointment(appointmentId).getOrNull()
            doctorRepo.updateAppointmentStatus(appointmentId, status = "confirmed")
                .onSuccess {
                    appt?.childId?.let { childId ->
                        val link = com.lullabyai.app.data.repository.PediaConnectLinkRepository()
                            .linkFor(doctorId, childId).getOrNull()
                        if (link != null && !link.isApproved) {
                            com.lullabyai.app.data.repository.PediaConnectLinkRepository()
                                .approve(link.id, doctorId)
                        }
                    }
                    load()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun reject(appointmentId: String, reason: String) {
        if (_uiState.value.isLoading) return
        val notes = runCatching { doctorRejectionNotes(reason) }.getOrElse {
            _uiState.value = _uiState.value.copy(error = it.message)
            return
        }
        viewModelScope.launch {
            doctorRepo.updateAppointmentStatus(appointmentId, status = "cancelled", notes = notes)
                .onSuccess { load() }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }
}

private fun guardianFromNotes(notes: String?): String? {
    if (notes.isNullOrBlank()) return null
    return notes.lineSequence()
        .firstOrNull { it.contains("Guardian", ignoreCase = true) || it.contains("ผู้ปกครอง") }
        ?.substringAfter(":")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

data class DoctorPatientRegistrationDetailUiState(
    val appointment: Appointment? = null,
    val intake: com.lullabyai.app.data.model.PediaConnectPatientRecord? = null,
    val mainParent: com.lullabyai.app.data.model.PediaConnectPatientParentRecord? = null,
    val syncedChild: com.lullabyai.app.data.model.Child? = null,
    val vaccineRecords: List<VaccineRecord> = emptyList(),
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val actionDone: Boolean = false,
    val error: String? = null
)

class DoctorPatientRegistrationDetailViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val pediaRepo = PediaConnectRepository()
    private val childRepo = com.lullabyai.app.data.repository.ChildRepository()
    private val healthRepo = HealthRepository()
    private val linkRepo = com.lullabyai.app.data.repository.PediaConnectLinkRepository()

    private val _uiState = MutableStateFlow(DoctorPatientRegistrationDetailUiState())
    val uiState: StateFlow<DoctorPatientRegistrationDetailUiState> = _uiState

    fun load(appointmentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, actionDone = false)
            doctorRepo.getAppointment(appointmentId)
                .onSuccess { appt ->
                    val intake = pediaRepo.patientRecordForChild(appt.childId).getOrNull()
                    val parent = intake?.id?.let { pediaRepo.mainParentForPatient(it).getOrNull() }
                    val child = childRepo.getChildById(appt.childId).getOrNull()
                        ?: com.lullabyai.app.data.repository.resolveChildForDoctorView(appt.childId, appt.notes)
                    val vaccines = healthRepo.getVaccineRecords(appt.childId).getOrDefault(emptyList())
                    _uiState.value = DoctorPatientRegistrationDetailUiState(
                        appointment = appt,
                        intake = intake,
                        mainParent = parent,
                        syncedChild = child,
                        vaccineRecords = vaccines,
                        isLoading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    /**
     * Approve the booking, and record the clinic's HN for this child at the same time — approval is
     * the moment the practice actually takes the patient on, so it is where an HN gets issued.
     *
     * The HN write is deliberately secondary: if it fails (most likely the (doctor_id, hn) unique
     * index rejecting a number already in use) the approval still stands and the doctor is told,
     * rather than losing a valid approval over a typo.
     */
    fun approve(appointmentId: String, hn: String? = null) {
        if (_uiState.value.isActing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActing = true, error = null)
            doctorRepo.updateAppointmentStatus(appointmentId, status = "confirmed")
                .onSuccess {
                    var hnError: String? = null
                    val childId = _uiState.value.appointment?.childId
                    val doctorId = supabase.auth.currentUserOrNull()?.id
                    if (childId != null && doctorId != null) {
                        val link = linkRepo.linkFor(doctorId, childId).getOrNull()
                        if (link != null && !link.isApproved) {
                            linkRepo.approve(link.id, doctorId)
                        }
                    }
                    if (!hn.isNullOrBlank() && childId != null && doctorId != null) {
                        linkRepo.setHn(doctorId, childId, hn)
                            .onFailure { hnError = "บันทึกอนุมัติแล้ว แต่บันทึก HN ไม่สำเร็จ: ${it.message}" }
                    }
                    _uiState.value = _uiState.value.copy(isActing = false, actionDone = true, error = hnError)
                }
                .onFailure { _uiState.value = _uiState.value.copy(isActing = false, error = it.message) }
        }
    }

    fun reject(appointmentId: String, reason: String) {
        if (_uiState.value.isActing) return
        val notes = runCatching { doctorRejectionNotes(reason) }.getOrElse {
            _uiState.value = _uiState.value.copy(error = it.message)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActing = true, error = null)
            doctorRepo.updateAppointmentStatus(appointmentId, status = "cancelled", notes = notes)
                .onSuccess { _uiState.value = _uiState.value.copy(isActing = false, actionDone = true) }
                .onFailure { _uiState.value = _uiState.value.copy(isActing = false, error = it.message) }
        }
    }
}
