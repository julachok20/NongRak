package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.AppNotification
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.ui.components.InAppAlerts
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NotificationUiState(
    val all: List<AppNotification> = emptyList(),
    val isLoading: Boolean = false
)

class NotificationViewModel : ViewModel() {
    private val repo = NotificationRepository()

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState

    private val seenIds = mutableSetOf<String>()
    private var watchJob: Job? = null

    fun load() {
        viewModelScope.launch {
            val list = repo.getNotifications()
            seenIds += list.map { it.id }
            _uiState.value = NotificationUiState(all = list, isLoading = false)
            repo.markAllRead()
        }
    }

    /**
     * While the owner has the app open, a member's "ส่งคำขอเพิ่มสิทธิ์" lands in `notifications`
     * for them. Poll so the bell and the in-app banner update without a restart.
     * Does not mark rows read — opening the notifications screen does that.
     */
    fun startWatching() {
        if (watchJob != null) return
        watchJob = viewModelScope.launch {
            var primed = false
            while (isActive) {
                val list = runCatching { repo.getNotifications() }.getOrNull()
                if (list != null) {
                    if (primed) {
                        val fresh = list.filter { it.id !in seenIds && it.title == ACCESS_REQUEST_TITLE }
                        if (fresh.isNotEmpty()) {
                            val n = fresh.first()
                            InAppAlerts.post(n.category, n.title, n.body)
                        }
                    }
                    seenIds += list.map { it.id }
                    primed = true
                    _uiState.value = NotificationUiState(all = list, isLoading = false)
                }
                delay(12_000)
            }
        }
    }

    companion object {
        const val ACCESS_REQUEST_TITLE = "คำขอเพิ่มสิทธิ์"

        fun countsOnBell(n: AppNotification): Boolean =
            !n.isRead && (n.category == "warning" || n.title == ACCESS_REQUEST_TITLE)
    }
}
