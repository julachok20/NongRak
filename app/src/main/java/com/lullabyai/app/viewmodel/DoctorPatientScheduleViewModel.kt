package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.resolveChildForDoctorView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorPatientScheduleUiState(
    val appointments: List<Appointment> = emptyList(),
    val childrenById: Map<String, Child> = emptyMap(),
    /** Clinic-assigned HN per child, for this doctor only. Absent until the doctor assigns one. */
    val hnByChildId: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false
)

/** Doctor's full appointment schedule, with skip / reschedule / complete actions — every
 *  transition routes through DoctorRepository.updateAppointmentStatus, then reloads. */
class DoctorPatientScheduleViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val linkRepo = PediaConnectLinkRepository()

    private val _uiState = MutableStateFlow(DoctorPatientScheduleUiState())
    val uiState: StateFlow<DoctorPatientScheduleUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val appointments = doctorRepo.getAppointmentsForDoctor(doctorId).getOrDefault(emptyList())
            val childMap = appointments.map { it.childId }.distinct()
                .mapNotNull { id ->
                    val notes = appointments.firstOrNull { it.childId == id }?.notes
                    resolveChildForDoctorView(id, notes)?.let { id to it }
                }.toMap()
            val hnMap = linkRepo.hnByChildForDoctor(doctorId).getOrDefault(emptyMap())
            _uiState.value = DoctorPatientScheduleUiState(appointments, childMap, hnMap, false)
        }
    }

    fun skip(appointmentId: String, reason: String?) {
        viewModelScope.launch {
            doctorRepo.updateAppointmentStatus(appointmentId, "skipped", notes = reason)
            load()
        }
    }

    fun reschedule(appointmentId: String, newScheduledAt: String, notes: String?) {
        viewModelScope.launch {
            doctorRepo.updateAppointmentStatus(appointmentId, "rescheduled", scheduledAt = newScheduledAt, notes = notes)
            load()
        }
    }

    fun complete(appointmentId: String) {
        viewModelScope.launch {
            doctorRepo.updateAppointmentStatus(appointmentId, "completed")
            load()
        }
    }

    fun confirm(appointmentId: String) {
        viewModelScope.launch {
            doctorRepo.updateAppointmentStatus(appointmentId, "confirmed")
            load()
        }
    }
}
