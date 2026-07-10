package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.repository.AuthRepository
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
    val lastMentalHealthResult: com.lullabyai.app.data.model.MentalHealthAssessment? = null
)

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun signUp(email: String, password: String, fullName: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.signUp(email, password, fullName)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess() }
                .onFailure { _uiState.value = AuthUiState(error = friendlyAuthError(it.message)) }
        }
    }

    /** onSuccess reports whether the account must change its (temporary) password before continuing. */
    fun signIn(email: String, password: String, onSuccess: (mustChangePassword: Boolean) -> Unit) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                _uiState.value = AuthUiState(error = "กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน")
                return@launch
            }
            _uiState.value = AuthUiState(isLoading = true)
            repo.signIn(email, password)
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

    fun signInWithGoogle(context: android.content.Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AuthUiState(isLoading = true)
            repo.signInWithGoogle(context)
                .onSuccess { _uiState.value = AuthUiState(isSuccess = true); onSuccess() }
                .onFailure { _uiState.value = AuthUiState(error = it.message ?: "เข้าสู่ระบบด้วย Google ไม่สำเร็จ") }
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
