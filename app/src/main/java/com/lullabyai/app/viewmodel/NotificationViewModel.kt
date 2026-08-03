package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.AppNotification
import com.lullabyai.app.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NotificationUiState(
    val all: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false
)

class NotificationViewModel : ViewModel() {
    private val repo = NotificationRepository()

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState

    fun load() {
        viewModelScope.launch {
            val list = repo.getNotifications()
            _uiState.value = NotificationUiState(all = list, isLoading = false)
            repo.markAllRead()
        }
    }
}
