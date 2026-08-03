package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppointmentDetailsUiState(
    val loading: Boolean = false,
    val appointment: Appointment? = null,
    val profile: Profile? = null,
    val doctorProfile: DoctorProfile? = null,
    val photoUrl: String? = null,
    val cancelling: Boolean = false,
    val queueDelayed: Boolean = false,
    val error: String? = null
)

/** Backs AppointmentDetailsScreen — read view of one appointments row plus its doctor's profile,
 *  and the cancel action (updateAppointmentStatus, same status-transition function the doctor
 *  side uses for confirm/complete/etc). */
class AppointmentDetailsViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val _ui = MutableStateFlow(AppointmentDetailsUiState())
    val ui: StateFlow<AppointmentDetailsUiState> = _ui

    fun load(appointmentId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            repo.getAppointment(appointmentId)
                .onSuccess { appt ->
                    val doctorProfile = repo.getDoctorProfile(appt.doctorId).getOrNull()
                    val parentProfile = repo.searchDoctors(null).getOrDefault(emptyList())
                        .firstOrNull { it.first.id == appt.doctorId }?.first
                    val photo = StorageHelper.signedAvatarUrl(doctorProfile?.clinicPhotoUrl)
                    val delayed = repo.isAppointmentDelayedInQueue(appt)
                    _ui.value = _ui.value.copy(
                        loading = false, appointment = appt, profile = parentProfile,
                        doctorProfile = doctorProfile, photoUrl = photo, queueDelayed = delayed
                    )
                }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    fun cancel(appointmentId: String, reason: String, onDone: () -> Unit) {
        val trimmed = reason.trim()
        if (trimmed.isBlank() || _ui.value.cancelling) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(cancelling = true)
            val notes = cancelNotes(_ui.value.appointment?.notes, trimmed)
            repo.updateAppointmentStatus(appointmentId, status = "cancelled", notes = notes)
                .onSuccess { _ui.value = _ui.value.copy(cancelling = false, appointment = it); onDone() }
                .onFailure { _ui.value = _ui.value.copy(cancelling = false, error = it.message) }
        }
    }

    private fun cancelNotes(existing: String?, reason: String): String {
        val line = "Cancelled by parent: $reason"
        return if (existing.isNullOrBlank()) line else "${existing.trim()}\n\n$line"
    }
}
