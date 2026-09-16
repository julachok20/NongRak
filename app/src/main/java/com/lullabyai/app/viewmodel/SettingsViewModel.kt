package com.lullabyai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.data.settings.AppSettings
import com.lullabyai.app.data.settings.SettingsManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val manager = SettingsManager(app)

    val settings: StateFlow<AppSettings> = manager.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, AppSettings()
    )

    fun setDarkMode(value: Boolean) = viewModelScope.launch { manager.setDarkMode(value) }
    fun setLanguage(value: AppLanguage) = viewModelScope.launch { manager.setLanguage(value) }
    fun toggleLanguage() = viewModelScope.launch {
        val current = settings.value.language
        manager.setLanguage(if (current == AppLanguage.TH) AppLanguage.EN else AppLanguage.TH)
    }
    fun setPushNotifications(value: Boolean) = viewModelScope.launch { manager.setPushNotifications(value) }
    fun setDailyReport(value: Boolean) = viewModelScope.launch { manager.setDailyReport(value) }
    fun setHealthAlert(value: Boolean) = viewModelScope.launch { manager.setHealthAlert(value) }
    fun setAiTips(value: Boolean) = viewModelScope.launch { manager.setAiTips(value) }
    fun setAutoRecord(value: Boolean) = viewModelScope.launch { manager.setAutoRecord(value) }
    fun setAppLock(value: Boolean) = viewModelScope.launch { manager.setAppLock(value) }
    fun setTwoFactorEnabled(value: Boolean) = viewModelScope.launch { manager.setTwoFactorEnabled(value) }
    fun setHasSeenTour(value: Boolean) = viewModelScope.launch { manager.setHasSeenTour(value) }
    fun setHasSeenDoctorTour(value: Boolean) = viewModelScope.launch { manager.setHasSeenDoctorTour(value) }
}
