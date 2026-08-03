package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.data.repository.resolveChildForDoctorView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

enum class PatientVisitKind { NEW, RETURNING, UPCOMING }

data class PatientDirectoryRow(
    val child: Child,
    val latestStatus: String,
    val visitKind: PatientVisitKind,
    val nextAppointmentAt: String?,
    val hn: String?
)

data class DoctorPatientDirectoryUiState(
    val rows: List<PatientDirectoryRow> = emptyList(),
    val isLoading: Boolean = false
)

/** Doctor's patient directory — children with appointments/links at this clinic. */
class DoctorPatientDirectoryViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val linkRepo = PediaConnectLinkRepository()

    private val _uiState = MutableStateFlow(DoctorPatientDirectoryUiState())
    val uiState: StateFlow<DoctorPatientDirectoryUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val childIds = doctorRepo.getPatientChildIds(doctorId).getOrDefault(emptyList())
            val appointments = doctorRepo.getAppointmentsForDoctor(doctorId).getOrDefault(emptyList())
            val hnMap = linkRepo.hnByChildForDoctor(doctorId).getOrDefault(emptyMap())
            val now = Instant.now()
            val rows = childIds.mapNotNull { id ->
                val mine = appointments.filter { it.childId == id }
                val noteFallback = mine.mapNotNull { com.lullabyai.app.data.repository.intakeNameFromNotes(it.notes) }.firstOrNull()
                val child = com.lullabyai.app.data.repository.resolveChildForDoctorView(id, mine.firstOrNull()?.notes)
                    ?: noteFallback?.let { name ->
                        Child(id = id, name = name, birthdate = "", gender = "unspecified")
                    }
                    ?: return@mapNotNull null
                val latestStatus = mine.maxByOrNull { it.createdAt ?: "" }?.status ?: "pending"
                val hasCompleted = mine.any { it.status == "completed" }
                val upcomingAppt = mine
                    .filter { it.status in listOf("pending", "confirmed") }
                    .filter { appt ->
                        val start = appt.scheduledAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        start == null || start.isAfter(now)
                    }
                    .minByOrNull { it.scheduledAt ?: it.createdAt ?: "" }
                val visitKind = when {
                    upcomingAppt != null -> PatientVisitKind.UPCOMING
                    !hasCompleted -> PatientVisitKind.NEW
                    else -> PatientVisitKind.RETURNING
                }
                PatientDirectoryRow(
                    child = child,
                    latestStatus = latestStatus,
                    visitKind = visitKind,
                    nextAppointmentAt = upcomingAppt?.scheduledAt,
                    hn = hnMap[id]
                )
            }.sortedWith(
                compareBy<PatientDirectoryRow> { row ->
                    row.nextAppointmentAt?.let { ts ->
                        runCatching { Instant.parse(ts) }.getOrNull()?.toEpochMilli()
                    } ?: Long.MAX_VALUE
                }.thenBy { it.child.name }
            )
            _uiState.value = DoctorPatientDirectoryUiState(rows = rows, isLoading = false)
        }
    }
}
