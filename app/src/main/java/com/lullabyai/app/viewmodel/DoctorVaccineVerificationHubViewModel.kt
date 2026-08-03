package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.model.VaccineVerificationRequest
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.HealthRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorVaccineVerificationHubUiState(
    val pending: List<VaccineVerificationRequest> = emptyList(),
    val childrenById: Map<String, Child> = emptyMap(),
    val vaccineRecordsById: Map<String, VaccineRecord> = emptyMap(),
    val isLoading: Boolean = false,
    // Set right after a successful resolve — read by DoctorVaccineVerificationSentScreen the same
    // way MentalHealthResultScreen reads authState.lastMentalHealthResult (shared ViewModel instance).
    val lastResolved: VaccineVerificationRequest? = null
)

/** Doctor-side vaccine verification queue: approve/reject requests a parent sent asking the doctor
 *  to confirm a vaccine record. */
class DoctorVaccineVerificationHubViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()

    private val _uiState = MutableStateFlow(DoctorVaccineVerificationHubUiState())
    val uiState: StateFlow<DoctorVaccineVerificationHubUiState> = _uiState

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val pending = doctorRepo.getVaccineVerifications(doctorId = doctorId).getOrDefault(emptyList())
                .filter { it.status == "pending" }
            val childMap = pending.map { it.childId }.distinct()
                .mapNotNull { id -> childRepo.getChildById(id).getOrNull()?.let { id to it } }.toMap()
            val recordMap = pending.mapNotNull { req ->
                healthRepo.getVaccineRecords(req.childId).getOrDefault(emptyList())
                    .find { it.id == req.vaccineRecordId }?.let { req.vaccineRecordId to it }
            }.toMap()
            _uiState.value = _uiState.value.copy(
                pending = pending, childrenById = childMap, vaccineRecordsById = recordMap, isLoading = false
            )
        }
    }

    fun resolve(id: String, approve: Boolean, notes: String?) {
        viewModelScope.launch {
            val result = doctorRepo.resolveVaccineVerification(id, if (approve) "verified" else "rejected", notes)
            _uiState.value = _uiState.value.copy(lastResolved = result.getOrNull())
            load()
        }
    }
}
