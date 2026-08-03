package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.DoctorRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorPatientManagementUiState(
    val todayCount: Int = 0,
    val pendingCount: Int = 0,
    val isLoading: Boolean = false
)

/** Quick-access hub (Stitch "Patient Main Page") — a secondary doctor home screen: a grid of
 *  shortcuts (Schedule / Patient Info / Registration / Manage Slots) plus a small daily-stats
 *  strip. Distinct from the Patient Directory (that one is the searchable patient list); this is
 *  a navigation launchpad. */
class DoctorPatientManagementViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorPatientManagementUiState())
    val uiState: StateFlow<DoctorPatientManagementUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val appointments = doctorRepo.getAppointmentsForDoctor(doctorId).getOrDefault(emptyList())
            val today = java.time.LocalDate.now()
            val todayCount = appointments.count { appt ->
                val d = com.lullabyai.app.util.TimeUtils.localDate(appt.scheduledAt)
                d != null && runCatching { java.time.LocalDate.parse(d) }.getOrNull() == today
            }
            val pendingCount = appointments.count { it.status == "pending" }
            _uiState.value = DoctorPatientManagementUiState(todayCount, pendingCount, false)
        }
    }
}
