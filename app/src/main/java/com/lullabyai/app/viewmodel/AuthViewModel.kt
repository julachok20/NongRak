package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.AuthRepository.AuthPortal
import com.lullabyai.app.data.model.hasDoctorRole
import com.lullabyai.app.data.model.hasParentRole
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.data.doctor.isRegistrationComplete
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.util.GoogleAuthHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val profile: Profile? = null,
    /** Short-lived signed URL for the profile photo (private bucket). Use this to display the avatar. */
    val avatarSignedUrl: String? = null,
    // --- Mental health self-assessment (parent-level, monthly) ---
    val mentalHealthHistory: List<com.lullabyai.app.data.model.MentalHealthAssessment> = emptyList(),
    val mentalHealthSubmitting: Boolean = false,
    val lastMentalHealthResult: com.lullabyai.app.data.model.MentalHealthAssessment? = null,
    // --- Postpartum physical-recovery self-assessment (parent-level, monthly) ---
    val postpartumHistory: List<com.lullabyai.app.data.model.PostpartumCheckIn> = emptyList(),
    val postpartumSubmitting: Boolean = false,
    val lastPostpartumResult: com.lullabyai.app.data.model.PostpartumCheckIn? = null
)

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    /** [phone] must already be normalized by the caller (see `ThaiPhone.normalize`); the repository
     *  re-validates it anyway. New params go BEFORE `onSuccess` and AFTER `accountType` so existing
     *  positional call sites keep binding to the same slots. */
    fun signUp(email: String, password: String, fullName: String, captchaToken: String? = null, accountType: String = "parent", phone: String? = null, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.signUp(
                email = email,
                password = password,
                fullName = fullName,
                captchaToken = captchaToken,
                accountType = accountType,
                phone = phone
            )
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess() }
                .onFailure { _uiState.value = AuthUiState(error = friendlyAuthError(it.message)) }
        }
    }

    /** onSuccess reports whether the account must change its (temporary) password before continuing. */
    fun signIn(email: String, password: String, captchaToken: String? = null, onSuccess: (mustChangePassword: Boolean) -> Unit) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _uiState.value = AuthUiState(error = "กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน")
                return@launch
            }
            _uiState.value = AuthUiState(isLoading = true)
            repo.signIn(email, password, captchaToken)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess(repo.mustChangePassword()) }
                .onFailure { _uiState.value = AuthUiState(error = friendlyAuthError(it.message)) }
        }
    }

    /** Complete forced change with just a new password (no current-password re-check), clears the flag. */
    fun completeForcedChange(newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.completeForcedChange(newPassword)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false); onSuccess() }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyAuthError(it.message)) }
        }
    }

    /** Forced password change after logging in with a temporary password. */
    fun changePasswordForced(currentPassword: String, newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.changePasswordForced(currentPassword, newPassword)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false); onSuccess() }
                .onFailure {
                    val raw = it.message?.lowercase() ?: ""
                    val msg = if (raw.contains("invalid") || raw.contains("credential") || raw.contains("password"))
                        "รหัสผ่านปัจจุบันไม่ถูกต้อง กรุณาตรวจสอบแล้วลองอีกครั้ง"
                    else friendlyAuthError(it.message)
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                }
        }
    }

    /** Classic Google Sign-In SDK → Supabase ID token exchange (via [rememberGoogleSignInAction]). */
    fun completeGoogleSignIn(
        idToken: String?,
        isThai: Boolean,
        onSuccess: () -> Unit,
        accountType: String = "parent",
        errorMessage: String? = null
    ) {
        viewModelScope.launch {
            if (idToken.isNullOrBlank()) {
                _uiState.value = AuthUiState(
                    error = GoogleAuthHelper.friendlyError(
                        errorMessage ?: "ยกเลิกการเข้าสู่ระบบ",
                        isThai
                    )
                )
                return@launch
            }
            _uiState.value = AuthUiState(isLoading = true)
            repo.signInWithGoogleIdToken(idToken, accountType)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess() }
                .onFailure {
                    _uiState.value = AuthUiState(
                        error = GoogleAuthHelper.friendlyError(it.message, isThai)
                    )
                }
        }
    }

    fun signOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repo.signOut().onSuccess { onSuccess() }
        }
    }

    fun cancelSignup(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.cancelSignup()
            onDone()
        }
    }

    /** Permanently delete the account + all data (PDPA). onResult(true) on success. */
    fun deleteAccount(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.deleteAccount()
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false); onResult(true) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyAuthError(it.message)); onResult(false) }
        }
    }

    fun changePassword(newPassword: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.changePassword(newPassword)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false); onSuccess() }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = friendlyAuthError(it.message)) }
        }
    }

    /** Upload a new profile photo (raw image bytes), then reload the profile. */
    fun uploadAvatar(bytes: ByteArray, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.uploadAvatar(bytes)
                .onSuccess { signedUrl ->
                    repo.getProfile().onSuccess { p -> _uiState.value = _uiState.value.copy(profile = p) }
                    // repo returns a fresh signed URL — display it immediately
                    _uiState.value = _uiState.value.copy(isLoading = false, avatarSignedUrl = signedUrl)
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = "อัปโหลดรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง") }
        }
    }

    /** PDPA data export — returns the user's full data as a JSON string (or null on failure). */
    fun exportData(onReady: (String?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val json = repo.exportUserData().getOrNull()
            _uiState.value = _uiState.value.copy(isLoading = false)
            onReady(json)
        }
    }

    fun loadProfile() {
        viewModelScope.launch {
            repo.getProfile()
                .onSuccess { p ->
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(p.avatarUrl)
                    _uiState.value = _uiState.value.copy(profile = p, avatarSignedUrl = signed)
                }
        }
    }

    /**
     * Route after auth using the **portal** the user chose (parent login vs doctor login).
     * Same email may have both roles; portals stay separate.
     */
    fun resolveHomeForPortal(
        portal: AuthPortal,
        isThai: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            repo.getProfile()
                .onSuccess { p ->
                    val portalKey = when (portal) {
                        AuthPortal.DOCTOR -> SettingsManager.PORTAL_DOCTOR
                        AuthPortal.PARENT -> SettingsManager.PORTAL_PARENT
                    }
                    SettingsManager(NongRakApp.instance).setLastAuthPortal(portalKey)
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(p.avatarUrl)
                    _uiState.value = _uiState.value.copy(profile = p, avatarSignedUrl = signed)
                    val allowed = when (portal) {
                        AuthPortal.PARENT -> p.hasParentRole()
                        AuthPortal.DOCTOR -> p.hasDoctorRole()
                    }
                    if (allowed) onSuccess()
                    else {
                        repo.signOut()
                        _uiState.value = AuthUiState(error = repo.portalRejectionMessage(portal, isThai))
                    }
                }
                .onFailure {
                    if (portal == AuthPortal.PARENT) onSuccess() else {
                        _uiState.value = AuthUiState(error = repo.portalRejectionMessage(portal, isThai))
                    }
                }
        }
    }

    /**
     * After Google or email auth on the doctor portal: verify doctor role, then route to profile
     * completion (Stitch 44.Regis-Google) or dashboard based on [doctor_profiles] row.
     */
    fun resolveDoctorPortalAfterAuth(
        isThai: Boolean,
        onNeedsRegistration: () -> Unit,
        onDashboard: () -> Unit,
        onRejected: (String) -> Unit = { _ -> }
    ) {
        viewModelScope.launch {
            repo.getProfile()
                .onSuccess { p ->
                    SettingsManager(NongRakApp.instance).setLastAuthPortal(SettingsManager.PORTAL_DOCTOR)
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(p.avatarUrl)
                    _uiState.value = _uiState.value.copy(profile = p, avatarSignedUrl = signed)
                    if (!p.hasDoctorRole()) {
                        repo.signOut()
                        onRejected(repo.portalRejectionMessage(AuthRepository.AuthPortal.DOCTOR, isThai))
                        return@launch
                    }
                    val doctorId = getCurrentUserId()
                    if (doctorId == null) {
                        onRejected(if (isThai) "ไม่พบเซสชันแพทย์" else "Doctor session missing")
                        return@launch
                    }
                    val doctorProfile = com.lullabyai.app.data.repository.DoctorRepository()
                        .getDoctorProfile(doctorId).getOrNull()
                    if (doctorProfile.isRegistrationComplete()) onDashboard()
                    else onNeedsRegistration()
                }
                .onFailure {
                    onRejected(repo.portalRejectionMessage(AuthRepository.AuthPortal.DOCTOR, isThai))
                }
        }
    }

    fun signUpDoctor(
        email: String,
        password: String,
        fullName: String,
        captchaToken: String? = null,
        /** Normalized by the caller via ThaiPhone.normalize, same contract as [signUp]. */
        phone: String? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.signUpDoctor(email, password, fullName, captchaToken, phone)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess() }
                .onFailure { _uiState.value = AuthUiState(error = friendlyAuthError(it.message)) }
        }
    }

    /** After Google identity, attach doctor role when registering from the doctor portal. */
    fun attachDoctorRoleAfterGoogle(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            repo.grantDoctorRole()
                .onSuccess { onSuccess() }
                .onFailure { onFailure(it.message ?: "Could not enable doctor access") }
        }
    }

    /**
     * @deprecated Prefer [resolveHomeForPortal] so parent and doctor logins do not cross-route.
     */
    fun resolveHomeAfterAuth(onDoctor: () -> Unit, onParent: () -> Unit) {
        viewModelScope.launch {
            repo.getProfile()
                .onSuccess { p ->
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(p.avatarUrl)
                    _uiState.value = _uiState.value.copy(profile = p, avatarSignedUrl = signed)
                    if (p.hasDoctorRole() && !p.hasParentRole()) onDoctor()
                    else if (p.hasParentRole()) onParent()
                    else onParent()
                }
                .onFailure { onParent() }
        }
    }

    fun updateProfile(username: String?, fullName: String?, avatarUrl: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.updateProfile(username, fullName, avatarUrl)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false); onSuccess() }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /** Toggle the separate AI-training-data opt-in, then refresh the profile so the Settings
     *  toggle reflects the confirmed server state (not an optimistic local flip). */
    fun setAiTrainingConsent(value: Boolean) {
        viewModelScope.launch {
            repo.setAiTrainingConsent(value).onSuccess { loadProfile() }
        }
    }

    fun loadMentalHealthHistory() {
        viewModelScope.launch {
            repo.getMentalHealthHistory().onSuccess { _uiState.value = _uiState.value.copy(mentalHealthHistory = it) }
        }
    }

    fun submitMentalHealthAssessment(answers: List<Int>, onDone: () -> Unit) {
        _uiState.value = _uiState.value.copy(mentalHealthSubmitting = true)
        viewModelScope.launch {
            repo.submitMentalHealthAssessment(answers)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        mentalHealthSubmitting = false, lastMentalHealthResult = result,
                        mentalHealthHistory = listOf(result) + _uiState.value.mentalHealthHistory
                    )
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(mentalHealthSubmitting = false) }
        }
    }

    fun loadPostpartumHistory() {
        viewModelScope.launch {
            repo.getPostpartumHistory().onSuccess { _uiState.value = _uiState.value.copy(postpartumHistory = it) }
        }
    }

    fun submitPostpartumCheckIn(answers: List<Int>, onDone: () -> Unit) {
        _uiState.value = _uiState.value.copy(postpartumSubmitting = true)
        viewModelScope.launch {
            repo.submitPostpartumCheckIn(answers)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        postpartumSubmitting = false, lastPostpartumResult = result,
                        postpartumHistory = listOf(result) + _uiState.value.postpartumHistory
                    )
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(postpartumSubmitting = false) }
        }
    }

    /** Maps raw (often English) auth errors to clear, friendly Thai messages. */
    private fun friendlyAuthError(raw: String?): String {
        val m = raw?.lowercase() ?: ""
        return when {
            m.contains("invalid") || m.contains("credential") ->
                "อีเมลหรือรหัสผ่านไม่ถูกต้อง กรุณาตรวจสอบแล้วลองอีกครั้ง"
            m.contains("already") || m.contains("registered") || m.contains("exists") ->
                "อีเมลนี้มีบัญชีอยู่แล้ว กรุณาเข้าสู่ระบบ"
            m.contains("not confirmed") ->
                "อีเมลยังไม่ได้รับการยืนยัน กรุณาตรวจสอบกล่องอีเมลของคุณก่อนเข้าสู่ระบบ"
            m.contains("least") && m.contains("password") ->
                "รหัสผ่านสั้นเกินไป กรุณาตั้งอย่างน้อย 6 ตัวอักษร"
            m.contains("weak") ->
                "รหัสผ่านคาดเดาง่ายเกินไป กรุณาตั้งรหัสผ่านที่ปลอดภัยกว่านี้"
            m.contains("network") || m.contains("timeout") || m.contains("host") ||
                m.contains("resolve") || m.contains("connect") || m.contains("unreachable") ->
                "เชื่อมต่ออินเทอร์เน็ตไม่ได้ กรุณาตรวจสอบสัญญาณแล้วลองใหม่"
            m.contains("rate") || m.contains("too many") ->
                "มีการพยายามหลายครั้งเกินไป กรุณารอสักครู่แล้วลองใหม่"
            else ->
                "เกิดข้อผิดพลาด กรุณาลองใหม่อีกครั้ง"
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    suspend fun hasCompletedOnboarding(): Boolean = repo.hasCompletedOnboarding()
    fun getCurrentUserId() = repo.currentUser?.id
}
