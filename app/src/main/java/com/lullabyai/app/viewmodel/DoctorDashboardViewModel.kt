package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorDashboardUiState(
    val profile: Profile? = null,
    val doctorProfile: DoctorProfile? = null,
    val todayAppointments: List<Appointment> = emptyList(),
    val childrenById: Map<String, Child> = emptyMap(),
    val recentPatientIds: List<String> = emptyList(),
    val patientCount: Int = 0,
    val isLoading: Boolean = false,
    /** Clinic hours and/or bookable slots are configured — hide first-run nudge. */
    val scheduleConfigured: Boolean = false,
    /** Legacy flag: still used for empty-today-only checks in UI; prefer [scheduleConfigured]. */
    val needsInitialScheduleSetup: Boolean = false,
    /** Pending parent booking requests awaiting doctor approval. */
    val pendingRegistrationCount: Int = 0
)

/** Doctor's home screen — greeting, today's appointment queue/timeline, and a recent-patients
 *  preview. Mirrors ChildViewModel's single-uiState-per-screen shape. */
class DoctorDashboardViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val authRepo = AuthRepository()
    private val childRepo = ChildRepository()

    private val _uiState = MutableStateFlow(DoctorDashboardUiState())
    val uiState: StateFlow<DoctorDashboardUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val profile = authRepo.getProfile().getOrNull()
            val doctorProfile = doctorRepo.getDoctorProfile(doctorId).getOrNull()
            val appointments = doctorRepo.getAppointmentsForDoctor(doctorId).getOrDefault(emptyList())
            val today = java.time.LocalDate.now()
            val todayAppointments = appointments.filter { appt ->
                val d = com.lullabyai.app.util.TimeUtils.localDate(appt.scheduledAt)
                d != null && runCatching { java.time.LocalDate.parse(d) }.getOrNull() == today
            }.sortedBy { it.scheduledAt ?: "" }
            val patientIds = doctorRepo.getPatientChildIds(doctorId).getOrDefault(emptyList())
            val recentIds = patientIds.takeLast(2).reversed()
            val neededIds = (todayAppointments.map { it.childId } + recentIds).distinct()
            val childMap = neededIds.mapNotNull { id -> childRepo.getChildById(id).getOrNull()?.let { id to it } }.toMap()
            val todayStr = today.toString()
            val slotsFromToday = doctorRepo.getSlots(doctorId, todayStr).getOrDefault(emptyList())
            val hasOpenSlot = slotsFromToday.any { it.status == "open" }
            val weekly = doctorRepo.getAvailability(doctorId).getOrDefault(emptyList())
            val hasWeeklyHours = weekly.any { !it.isDayOff && !it.startTime.isNullOrBlank() && !it.endTime.isNullOrBlank() }
            val scheduleConfigured = hasOpenSlot || hasWeeklyHours
            val nudgeDismissed = SettingsManager(NongRakApp.instance).isDoctorScheduleNudgeDismissed()
            val pendingRegistrationCount = doctorRepo.pendingBookingsForDoctor(doctorId).getOrDefault(emptyList()).size
            _uiState.value = DoctorDashboardUiState(
                profile = profile,
                doctorProfile = doctorProfile,
                todayAppointments = todayAppointments,
                childrenById = childMap,
                recentPatientIds = recentIds,
                patientCount = patientIds.size,
                isLoading = false,
                scheduleConfigured = scheduleConfigured,
                needsInitialScheduleSetup = !scheduleConfigured && !nudgeDismissed,
                pendingRegistrationCount = pendingRegistrationCount
            )
        }
    }
}
