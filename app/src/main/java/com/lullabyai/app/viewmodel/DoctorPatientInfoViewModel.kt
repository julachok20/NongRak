package com.lullabyai.app.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChronicCondition
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.PediaConnectPatientParentRecord
import com.lullabyai.app.data.model.PediaConnectPatientRecord
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.remote.DoctorPatientDriveHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.repository.resolveChildForDoctorView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorPatientInfoUiState(
    val child: Child? = null,
    val allergies: List<Allergy> = emptyList(),
    val chronicConditions: List<ChronicCondition> = emptyList(),
    val vaccines: List<VaccineRecord> = emptyList(),
    val latestVisit: DoctorVisit? = null,
    val pediaIntake: PediaConnectPatientRecord? = null,
    val pediaGuardian: PediaConnectPatientParentRecord? = null,
    val driveConnected: Boolean = false,
    val driveConnectedAccount: String? = null,
    val driveAccountPickerAccounts: List<String>? = null,
    val drivePendingConsentAccount: String? = null,
    val driveRecords: List<DoctorPatientDriveHelper.DriveFileMeta> = emptyList(),
    val selectedDriveRecord: DoctorPatientDriveHelper.PatientDetailRecord? = null,
    val driveError: String? = null,
    val driveConsentIntent: Intent? = null,
    val isLoading: Boolean = false,
    val driveLoading: Boolean = false,
    val hn: String = "",
    val clinicNote: String = "",
    val savingDoctorFields: Boolean = false,
    val error: String? = null
)

/** Read-only patient chart for the doctor side — parent-shared tables for allergies/vaccines,
 *  plus Google Drive detailed clinical notes (not stored as full detail in Supabase). */
class DoctorPatientInfoViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val visitRepo = DoctorVisitRepository()
    private val doctorRepo = DoctorRepository()
    private val pediaRepo = PediaConnectRepository()
    private val linkRepo = com.lullabyai.app.data.repository.PediaConnectLinkRepository()

    private val _uiState = MutableStateFlow(DoctorPatientInfoUiState())
    val uiState: StateFlow<DoctorPatientInfoUiState> = _uiState

    fun load(childId: String, isThai: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, driveError = null)
            val resolvedChild = resolveChildForDoctorView(childId) ?: childRepo.getChildById(childId).getOrNull()
            val dataChildId = resolvedChild?.id ?: childId
            val allergies = healthRepo.getAllergies(dataChildId).getOrDefault(emptyList())
            val chronic = healthRepo.getChronicConditions(dataChildId).getOrDefault(emptyList())
            val vaccines = healthRepo.getVaccineRecords(dataChildId).getOrDefault(emptyList())
            val latestVisit = visitRepo.getVisits(dataChildId).getOrDefault(emptyList()).firstOrNull()
            val pediaIntake = pediaRepo.patientRecordForChild(dataChildId).getOrNull()
            val pediaGuardian = pediaIntake?.id?.takeIf { it.isNotBlank() }?.let {
                pediaRepo.mainParentForPatient(it).getOrNull()
            }
            _uiState.value = DoctorPatientInfoUiState(
                child = resolvedChild,
                allergies = allergies,
                chronicConditions = chronic,
                vaccines = vaccines,
                latestVisit = latestVisit,
                pediaIntake = pediaIntake,
                pediaGuardian = pediaGuardian,
                isLoading = false,
                driveLoading = true
            )
            supabase.auth.currentUserOrNull()?.id?.let { doctorId ->
                linkRepo.linkFor(doctorId, childId).getOrNull()?.let { link ->
                    _uiState.value = _uiState.value.copy(
                        hn = link.hn.orEmpty(),
                        clinicNote = link.note.orEmpty()
                    )
                }
            }
            refreshDriveFiles(childId, isThai)
        }
    }

    private suspend fun refreshDriveFiles(childId: String, isThai: Boolean) {
        val account = DoctorPatientDriveHelper.getSavedAccount()
        val drive = doctorRepo.listPatientDetailFiles(childId)
        val driveFailure = drive.exceptionOrNull()
        _uiState.value = _uiState.value.copy(
            driveConnected = driveFailure?.message != DoctorPatientDriveHelper.NO_TOKEN,
            driveConnectedAccount = account,
            driveRecords = drive.getOrDefault(emptyList()),
            driveLoading = false,
            driveError = driveFailure?.let {
                if (it.message == DoctorPatientDriveHelper.NO_TOKEN)
                    DoctorPatientDriveHelper.userMessage(isThai)
                else it.message
            }
        )
    }

    fun connectDrive(childId: String, isThai: Boolean = true, forceAccountPicker: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                driveLoading = true,
                driveError = null,
                driveConsentIntent = null,
                driveAccountPickerAccounts = null
            )
            applyConnectResult(childId, isThai, DoctorPatientDriveHelper.requestConnect(forceAccountPicker = forceAccountPicker))
        }
    }

    fun connectDriveWithAccount(childId: String, accountEmail: String, isThai: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                driveLoading = true,
                driveError = null,
                driveConsentIntent = null,
                driveAccountPickerAccounts = null
            )
            applyConnectResult(
                childId,
                isThai,
                DoctorPatientDriveHelper.requestConnect(accountEmail = accountEmail)
            )
        }
    }

    fun dismissDriveAccountPicker() {
        _uiState.value = _uiState.value.copy(driveAccountPickerAccounts = null, driveLoading = false)
    }

    private suspend fun applyConnectResult(
        childId: String,
        isThai: Boolean,
        result: DoctorPatientDriveHelper.DriveConnectResult
    ) {
        when (result) {
            is DoctorPatientDriveHelper.DriveConnectResult.Success -> refreshDriveFiles(childId, isThai)
            is DoctorPatientDriveHelper.DriveConnectResult.NeedsAccountChoice -> {
                _uiState.value = _uiState.value.copy(
                    driveLoading = false,
                    driveAccountPickerAccounts = result.accounts
                )
            }
            is DoctorPatientDriveHelper.DriveConnectResult.NeedsConsent -> {
                _uiState.value = _uiState.value.copy(
                    driveLoading = false,
                    driveConsentIntent = result.intent,
                    drivePendingConsentAccount = result.accountEmail
                )
            }
            is DoctorPatientDriveHelper.DriveConnectResult.Failed -> {
                _uiState.value = _uiState.value.copy(
                    driveLoading = false,
                    driveConnected = false,
                    driveError = DoctorPatientDriveHelper.userMessage(
                        isThai,
                        result.reason,
                        result.loginEmail,
                        result.deviceAccounts
                    )
                )
            }
        }
    }

    fun onDriveConsentHandled(childId: String, isThai: Boolean = true) {
        val pending = _uiState.value.drivePendingConsentAccount
        _uiState.value = _uiState.value.copy(driveConsentIntent = null)
        viewModelScope.launch {
            if (pending.isNullOrBlank()) {
                refreshDriveFiles(childId, isThai)
            } else {
                _uiState.value = _uiState.value.copy(driveLoading = true)
                applyConnectResult(
                    childId,
                    isThai,
                    DoctorPatientDriveHelper.completeConnectAfterConsent(pending)
                )
            }
        }
    }

    fun clearDriveConsentIntent() {
        _uiState.value = _uiState.value.copy(driveConsentIntent = null)
    }

    fun saveDoctorFields(childId: String, hn: String, note: String, isThai: Boolean = true) {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(savingDoctorFields = true)
            val hnResult = linkRepo.setHn(doctorId, childId, hn)
            val noteResult = linkRepo.setClinicNote(doctorId, childId, note)
            val failure = hnResult.exceptionOrNull() ?: noteResult.exceptionOrNull()
            _uiState.value = _uiState.value.copy(
                savingDoctorFields = false,
                hn = hn.trim(),
                clinicNote = note.trim(),
                error = failure?.let {
                    (if (isThai) "บันทึกข้อมูลไม่สำเร็จ: " else "Could not save: ") + it.message
                }
            )
        }
    }

    fun openDriveRecord(fileId: String, isThai: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(driveLoading = true, selectedDriveRecord = null)
            doctorRepo.downloadPatientDetail(fileId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(driveLoading = false, selectedDriveRecord = it)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        driveLoading = false,
                        driveError = if (it.message == DoctorPatientDriveHelper.NO_TOKEN)
                            DoctorPatientDriveHelper.userMessage(isThai)
                        else it.message
                    )
                }
        }
    }

    fun clearSelectedDriveRecord() {
        _uiState.value = _uiState.value.copy(selectedDriveRecord = null)
    }
}
