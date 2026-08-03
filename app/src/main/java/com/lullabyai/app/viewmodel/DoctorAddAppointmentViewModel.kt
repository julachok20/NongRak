package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorAddAppointmentUiState(
    val allPatients: List<Child> = emptyList(),
    val selectedPatient: Child? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

/** Doctor manually books/confirms an appointment for an existing patient. There's no global
 *  "search every child in the system" API exposed to a doctor account (RLS scopes `children` to
 *  the owning parent + synced members) — so the patient picker searches within the doctor's own
 *  patient list (same set as the Patient Directory), which covers the realistic "add a follow-up
 *  slot for someone I already see" case. */
class DoctorAddAppointmentViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val childRepo = ChildRepository()

    private val _uiState = MutableStateFlow(DoctorAddAppointmentUiState())
    val uiState: StateFlow<DoctorAddAppointmentUiState> = _uiState

    fun loadPatients() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            val ids = doctorRepo.getPatientChildIds(doctorId).getOrDefault(emptyList())
            val patients = ids.mapNotNull { id -> childRepo.getChildById(id).getOrNull() }
            _uiState.value = _uiState.value.copy(allPatients = patients)
        }
    }

    fun selectPatient(child: Child?) {
        _uiState.value = _uiState.value.copy(selectedPatient = child)
    }

    fun submit(reason: String, scheduledAtIso: String) {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        val child = _uiState.value.selectedPatient ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val created = doctorRepo.requestAppointment(doctorId, child.id, null, reason.ifBlank { null })
            val id = created.getOrNull()?.id
            if (id != null) {
                doctorRepo.updateAppointmentStatus(id, "confirmed", scheduledAt = scheduledAtIso)
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } else {
                _uiState.value = _uiState.value.copy(isSaving = false, error = created.exceptionOrNull()?.message)
            }
        }
    }
}
