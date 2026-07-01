package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class ForgotStep { EMAIL, OTP, TEMP_PASSWORD }

data class ForgotPasswordUiState(
    val step: ForgotStep = ForgotStep.EMAIL,
    val email: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val tempPassword: String? = null
)

class ForgotPasswordViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _state = MutableStateFlow(ForgotPasswordUiState())
    val state: StateFlow<ForgotPasswordUiState> = _state

    /** Step 1 → send the OTP to the email, advance to the OTP step. */
    fun sendOtp(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            _state.value = _state.value.copy(error = "กรุณากรอกอีเมลให้ถูกต้อง")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            // Check the email exists before sending so we don't silently send to a void address.
            val exists = repo.isEmailRegistered(trimmed)
            if (!exists) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "ไม่พบบัญชีที่ใช้อีเมลนี้ กรุณาตรวจสอบอีเมลของคุณ"
                )
                return@launch
            }
            repo.sendPasswordResetOtp(trimmed)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false, step = ForgotStep.OTP, email = trimmed)
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "ส่งรหัสไปยังอีเมลไม่สำเร็จ กรุณาตรวจสอบอีเมลและสัญญาณอินเทอร์เน็ตแล้วลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    /** Resend the OTP to the same email. */
    fun resendOtp() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, infoMessage = null)
            repo.sendPasswordResetOtp(_state.value.email)
                .onSuccess { _state.value = _state.value.copy(isLoading = false, infoMessage = "ส่งรหัสใหม่ไปยังอีเมลของคุณแล้ว") }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message ?: "ส่งรหัสใหม่ไม่สำเร็จ") }
        }
    }

    /** Step 2 → verify the OTP, then set a temporary password and advance to the final step. */
    fun verifyOtp(code: String) {
        if (code.length < 6) {
            _state.value = _state.value.copy(error = "กรุณากรอกรหัสยืนยัน 6 หลัก")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            repo.verifyRecoveryOtp(_state.value.email, code)
                .onSuccess {
                    repo.setTemporaryPassword()
                        .onSuccess { temp ->
                            _state.value = _state.value.copy(
                                isLoading = false, step = ForgotStep.TEMP_PASSWORD, tempPassword = temp
                            )
                        }
                        .onFailure {
                            _state.value = _state.value.copy(isLoading = false, error = it.message ?: "ตั้งรหัสผ่านชั่วคราวไม่สำเร็จ")
                        }
                }
                .onFailure {
                    _state.value = _state.value.copy(isLoading = false, error = "รหัส OTP ไม่ถูกต้องหรือหมดอายุ กรุณาลองใหม่")
                }
        }
    }

    fun back() {
        if (_state.value.step == ForgotStep.OTP) {
            _state.value = _state.value.copy(step = ForgotStep.EMAIL, error = null, infoMessage = null)
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearInfo() { _state.value = _state.value.copy(infoMessage = null) }
}
