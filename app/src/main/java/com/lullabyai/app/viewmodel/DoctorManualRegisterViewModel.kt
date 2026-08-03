package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.repository.intakeNameFromNotes
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorManualRegisterUiState(
    val pendingRows: List<PendingBookingRow> = emptyList(),
    val isLoading: Boolean = false,
    val isActing: Boolean = false,
    val error: String? = null
)

/** Backs [DoctorManualPatientRegisterScreen] — doctor assigns HN and approves pending bookings. */
class DoctorManualRegisterViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val pediaRepo = PediaConnectRepository()
    private val childRepo = ChildRepository()
    private val linkRepo = PediaConnectLinkRepository()

    private val _uiState = MutableStateFlow(DoctorManualRegisterUiState())
    val uiState: StateFlow<DoctorManualRegisterUiState> = _uiState

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
                    _uiState.value = DoctorManualRegisterUiState(pendingRows = rows, isLoading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
                }
        }
    }

    fun approveWithHn(appointmentId: String, hn: String, onDone: () -> Unit) {
        if (_uiState.value.isActing) return
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isActing = true, error = null)
            val appt = doctorRepo.getAppointment(appointmentId).getOrNull()
            doctorRepo.updateAppointmentStatus(appointmentId, status = "confirmed")
                .onSuccess {
                    appt?.childId?.let { childId ->
                        linkRepo.linkFor(doctorId, childId).getOrNull()?.let { link ->
                            if (!link.isApproved) linkRepo.approve(link.id, doctorId)
                        }
                        linkRepo.setHn(doctorId, childId, hn)
                    }
                    _uiState.value = _uiState.value.copy(isActing = false)
                    onDone()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isActing = false, error = it.message)
                }
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
