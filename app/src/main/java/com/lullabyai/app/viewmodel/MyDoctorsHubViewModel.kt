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

data class MyDoctorsHubUiState(
    val loading: Boolean = false,
    val doctors: List<Pair<Profile, DoctorProfile>> = emptyList(),
    val upcomingAppointments: List<Appointment> = emptyList()
)

/** Backs MyDoctorsHubScreen. "My doctors" = distinct doctors the parent's children have ANY
 *  appointment row with (any status) — there's no dedicated "favorite doctor" table, so this is
 *  derived the same way DoctorRepository.getPatientChildIds derives a doctor's patient list,
 *  just from the parent's side of the same appointments table. */
class MyDoctorsHubViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val _ui = MutableStateFlow(MyDoctorsHubUiState())
    val ui: StateFlow<MyDoctorsHubUiState> = _ui

    private var loadedForChildIds: List<String>? = null

    fun load(childIds: List<String>) {
        if (childIds.isEmpty() || childIds == loadedForChildIds) return
        loadedForChildIds = childIds
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val appointments = childIds.flatMap { repo.getAppointmentsForChild(it).getOrDefault(emptyList()) }
            val doctorIds = appointments.map { it.doctorId }.toSet()
            val allDoctors = repo.searchDoctors(null).getOrDefault(emptyList())
            val myDoctors = allDoctors.filter { (profile, _) -> profile.id in doctorIds }
            val upcoming = appointments
                .filter { it.status == "pending" || it.status == "confirmed" }
                .sortedByDescending { it.scheduledAt ?: it.createdAt }
                .take(5)
            _ui.value = MyDoctorsHubUiState(loading = false, doctors = myDoctors, upcomingAppointments = upcoming)
        }
    }
}
