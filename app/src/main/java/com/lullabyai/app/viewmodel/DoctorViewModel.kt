package com.lullabyai.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.doctor.DoctorDeviceBinding
import com.lullabyai.app.data.model.DoctorAvailability
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.remote.DoctorPatientDriveHelper
import com.lullabyai.app.data.repository.DoctorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: DoctorProfile? = null,
    val availability: List<DoctorAvailability> = emptyList(),
    /** Google Drive app-data reachable for patient detail files. */
    val driveConnected: Boolean? = null,
    val driveChecking: Boolean = false,
    val driveConnectedAccount: String? = null,
    val driveAccountPickerAccounts: List<String>? = null,
    val drivePendingConsentAccount: String? = null,
    /** System OAuth consent — launch from an Activity, then call [onDriveConsentHandled]. */
    val driveConsentIntent: Intent? = null
)

/** Thin wrapper around [DoctorRepository] shared by the doctor-side clinic-profile screens
 *  (Sign Up's practice-details step, Edit Profile, Weekly Hours) — same repo, same state shape,
 *  so one small ViewModel covers all three instead of near-duplicate ones per screen.
 *  Also owns device-binding checks and Drive connection probes for decision #9 B. */
class DoctorViewModel : ViewModel() {
    private val repo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorUiState())
    val uiState: StateFlow<DoctorUiState> = _uiState

    fun loadProfile(doctorId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.getDoctorProfile(doctorId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, profile = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false) } // no row yet — not an error
        }
    }

    fun saveProfile(
        doctorId: String,
        clinicName: String?,
        specialty: String?,
        licenseNumber: String?,
        clinicAddress: String?,
        clinicPhotoUrl: String?,
        bio: String?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.upsertDoctorProfile(doctorId, clinicName, specialty, licenseNumber, clinicAddress, clinicPhotoUrl, bio)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, profile = it); onSuccess() }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /** One day's row in the weekly grid, as the UI holds it before saving. */
    data class WeeklyAvailabilityInput(
        val dayOfWeek: Int,
        val startTime: String?,
        val endTime: String?,
        val isDayOff: Boolean
    )

    fun loadAvailability(doctorId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.getAvailability(doctorId)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false, availability = it) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    fun setAvailability(doctorId: String, dayOfWeek: Int, startTime: String?, endTime: String?, isDayOff: Boolean, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repo.setAvailability(doctorId, dayOfWeek, startTime, endTime, isDayOff)
                .onSuccess { saved ->
                    val updated = _uiState.value.availability.filterNot { it.dayOfWeek == dayOfWeek } + saved
                    _uiState.value = _uiState.value.copy(availability = updated.sortedBy { it.dayOfWeek })
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    /**
     * Save the whole week in ONE coroutine, sequentially.
     *
     * The screen used to loop over the seven days calling [setAvailability], which launched seven
     * independent coroutines. Two consequences, both reported as bugs: `isLoading` was never set, so
     * the Save button could not spin and the doctor got no feedback at all; and seven concurrent
     * read-then-write pairs raced each other on a table with no unique key, which is how duplicate
     * day rows become possible. Sequential writes inside one scope fix both.
     */
    fun saveWeeklyAvailability(
        doctorId: String,
        days: List<WeeklyAvailabilityInput>,
        onSaved: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val results = days.map { d ->
                repo.setAvailability(doctorId, d.dayOfWeek, d.startTime, d.endTime, d.isDayOff)
            }
            val failure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (failure != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = failure.message)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    availability = results.mapNotNull { it.getOrNull() }.sortedBy { it.dayOfWeek }
                )
                repo.purgeUnbookedSlotsOnClosedWeekdays(doctorId)
                onSaved()
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // --- Device binding (one doctor account per device) ---

    /** Blocks signup when this device already has any doctor bound. */
    fun assertDeviceAllowsNewDoctorSignup(context: Context, isThai: Boolean): String? {
        return if (DoctorDeviceBinding.isBoundToAnotherDoctor(context))
            DoctorDeviceBinding.deviceBlockedMessage(isThai)
        else null
    }

    /**
     * After a successful doctor auth: bind if unbound, or reject if another doctor owns the device.
     * Returns an error message when blocked (caller should sign out).
     */
    fun bindOrRejectDoctorSession(context: Context, doctorId: String, isThai: Boolean): String? {
        val result = DoctorDeviceBinding.bindDoctor(context, doctorId)
        return if (result.isFailure) DoctorDeviceBinding.deviceBlockedMessage(isThai) else null
    }

    fun clearDeviceBinding(context: Context) {
        DoctorDeviceBinding.clearBinding(context)
    }

    // --- Google Drive patient-detail space ---

    fun checkDriveConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(driveChecking = true)
            val account = DoctorPatientDriveHelper.getSavedAccount()
            val ok = repo.isPatientDriveAvailable()
            _uiState.value = _uiState.value.copy(
                driveConnected = ok,
                driveChecking = false,
                driveConnectedAccount = account
            )
        }
    }

    /** Starts connect — shows account picker when multiple Google accounts exist. */
    fun connectDrive(
        isThai: Boolean = true,
        forceAccountPicker: Boolean = false,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                driveChecking = true,
                driveConsentIntent = null,
                driveAccountPickerAccounts = null
            )
            applyConnectResult(
                DoctorPatientDriveHelper.requestConnect(forceAccountPicker = forceAccountPicker),
                isThai,
                onResult
            )
        }
    }

    fun connectDriveWithAccount(
        accountEmail: String,
        isThai: Boolean = true,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                driveChecking = true,
                driveConsentIntent = null,
                driveAccountPickerAccounts = null
            )
            applyConnectResult(
                DoctorPatientDriveHelper.requestConnect(accountEmail = accountEmail),
                isThai,
                onResult
            )
        }
    }

    fun dismissDriveAccountPicker() {
        _uiState.value = _uiState.value.copy(driveAccountPickerAccounts = null, driveChecking = false)
    }

    private fun applyConnectResult(
        result: DoctorPatientDriveHelper.DriveConnectResult,
        isThai: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        when (result) {
            is DoctorPatientDriveHelper.DriveConnectResult.Success -> {
                val account = DoctorPatientDriveHelper.getSavedAccount()
                _uiState.value = _uiState.value.copy(
                    driveConnected = true,
                    driveChecking = false,
                    driveConnectedAccount = account,
                    driveAccountPickerAccounts = null,
                    drivePendingConsentAccount = null
                )
                onResult(true, null)
            }
            is DoctorPatientDriveHelper.DriveConnectResult.NeedsAccountChoice -> {
                _uiState.value = _uiState.value.copy(
                    driveChecking = false,
                    driveAccountPickerAccounts = result.accounts
                )
                onResult(false, null)
            }
            is DoctorPatientDriveHelper.DriveConnectResult.NeedsConsent -> {
                _uiState.value = _uiState.value.copy(
                    driveChecking = false,
                    driveConsentIntent = result.intent,
                    drivePendingConsentAccount = result.accountEmail
                )
                onResult(false, null)
            }
            is DoctorPatientDriveHelper.DriveConnectResult.Failed -> {
                val msg = DoctorPatientDriveHelper.userMessage(
                    isThai = isThai,
                    failure = result.reason,
                    loginEmail = result.loginEmail,
                    deviceAccounts = result.deviceAccounts
                )
                _uiState.value = _uiState.value.copy(
                    driveConnected = false,
                    driveChecking = false,
                    driveAccountPickerAccounts = null
                )
                onResult(false, msg)
            }
        }
    }

    fun onDriveConsentHandled(isThai: Boolean = true, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val pending = _uiState.value.drivePendingConsentAccount
        _uiState.value = _uiState.value.copy(driveConsentIntent = null)
        if (pending.isNullOrBlank()) {
            checkDriveConnection()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(driveChecking = true)
            applyConnectResult(
                DoctorPatientDriveHelper.completeConnectAfterConsent(pending),
                isThai,
                onResult
            )
        }
    }

    fun clearDriveConsentIntent() {
        _uiState.value = _uiState.value.copy(driveConsentIntent = null)
    }

    fun driveSetupHint(isThai: Boolean): String = DoctorPatientDriveHelper.userMessage(isThai)
}
