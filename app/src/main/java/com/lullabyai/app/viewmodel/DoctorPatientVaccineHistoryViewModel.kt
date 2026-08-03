package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.resolveChildForDoctorView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DoctorPatientVaccineHistoryUiState(
    val child: Child? = null,
    val completedVaccines: List<VaccineRecord> = emptyList(),
    val upcomingVaccines: List<VaccineRecord> = emptyList(),
    val isLoading: Boolean = false,
    val sendingId: String? = null,
    val sentMessage: String? = null,
    val error: String? = null
)

/** Read-only vaccine history for the doctor side — reuses HealthRepository.getVaccineRecords,
 *  the same table VaccineBookScreen (parent side) reads from. */
class DoctorPatientVaccineHistoryViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val doctorRepo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorPatientVaccineHistoryUiState())
    val uiState: StateFlow<DoctorPatientVaccineHistoryUiState> = _uiState

    fun load(childId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, sentMessage = null)
            val child = resolveChildForDoctorView(childId) ?: childRepo.getChildById(childId).getOrNull()
            val dataChildId = child?.id ?: childId
            val all = healthRepo.getVaccineRecords(dataChildId).getOrDefault(emptyList())
            val completed = all.filter { it.status == "completed" }.sortedByDescending { it.dateGiven ?: "" }
            val upcoming = all.filter { it.status != "completed" && it.status != "skipped" }
                .sortedBy { it.scheduledDate ?: "9999" }
            _uiState.value = DoctorPatientVaccineHistoryUiState(child, completed, upcoming, false)
        }
    }

    fun sendVerificationToParent(record: VaccineRecord, isThai: Boolean) {
        if (!record.certificateUrl.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                error = if (isThai) "มีใบรับรองแล้ว — ใช้บันทึกการตรวจเพื่อส่งใหม่" else "Certificate already on file."
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            error = if (isThai) {
                "ส่งใบรับรองจาก บันทึกการตรวจ (นัดวัคซีน) เท่านั้น — จะได้ไฟล์ PDF ให้ผู้ปกครอง"
            } else {
                "Send certificates from Clinical Record Entry (vaccine appointment) only."
            }
        )
    }

    fun clearSentMessage() {
        _uiState.value = _uiState.value.copy(sentMessage = null, error = null)
    }
}
