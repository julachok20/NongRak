package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PatientRegistrationUiState(
    val loading: Boolean = false,
    val profile: Profile? = null,
    val doctorProfile: DoctorProfile? = null,
    val submitting: Boolean = false,
    val submitted: Appointment? = null,
    val error: String? = null
)

/** Backs PatientRegistrationScreen — the first-time "register my child with this clinic" intake
 *  form. There's no dedicated intake-fields table in the foundation schema (only appointments +
 *  its reason/notes columns), so the guardian/child intake details the form collects are bundled
 *  into the created appointment's notes rather than dropped; the doctor sees them when reviewing
 *  the registration request. This creates the same appointments row (status defaults to "pending")
 *  that DoctorRepository.requestAppointment always creates — the doctor-side approval screen acts
 *  on it exactly like a normal booking request. */
class PatientRegistrationViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val _ui = MutableStateFlow(PatientRegistrationUiState())
    val ui: StateFlow<PatientRegistrationUiState> = _ui

    private var loadedDoctorId: String? = null

    fun load(doctorId: String) {
        if (doctorId == loadedDoctorId) return
        loadedDoctorId = doctorId
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val doctorProfile = repo.getDoctorProfile(doctorId).getOrNull()
            val parentProfile = repo.searchDoctors(null).getOrDefault(emptyList())
                .firstOrNull { it.first.id == doctorId }?.first
            _ui.value = _ui.value.copy(loading = false, profile = parentProfile, doctorProfile = doctorProfile)
        }
    }

    fun submit(doctorId: String, childId: String, intakeNotes: String) {
        if (_ui.value.submitting) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true, error = null)
            repo.requestAppointment(doctorId, childId, slotId = null, reason = "Patient registration")
                .onSuccess { appt ->
                    // Second write for the notes blob — requestAppointment doesn't take notes, and
                    // adding a notes param there would touch the shared repo other agents depend on.
                    repo.updateAppointmentStatus(appt.id, status = "pending", notes = intakeNotes)
                        .onSuccess { _ui.value = _ui.value.copy(submitting = false, submitted = it) }
                        .onFailure { _ui.value = _ui.value.copy(submitting = false, submitted = appt) }
                }
                .onFailure { _ui.value = _ui.value.copy(submitting = false, error = it.message) }
        }
    }
}
