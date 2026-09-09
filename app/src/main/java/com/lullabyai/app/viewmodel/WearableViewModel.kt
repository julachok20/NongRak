package com.lullabyai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.DeviceReading
import com.lullabyai.app.data.model.WearableConnection
import com.lullabyai.app.data.repository.DeviceReadingInsert
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.repository.WearableMetric
import com.lullabyai.app.data.repository.WearablePlatform
import com.lullabyai.app.data.repository.WearableRepository
import com.lullabyai.app.data.settings.ChildSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

data class WearableUiState(
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val connections: List<WearableConnection> = emptyList(),
    val readings: List<DeviceReading> = emptyList(),
    val hcAvailable: Boolean = false,
    val hcPermissionsGranted: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class WearableViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WearableRepository()
    private val _state = MutableStateFlow(WearableUiState())
    val state: StateFlow<WearableUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val ctx = getApplication<Application>()
            val childId = ChildSelection.current()
            val connections = repo.getConnections()
            val readings = childId?.let { repo.getReadings(it) }.orEmpty()
            // Health Connect was removed; nothing to probe. Kept false so any remaining
            // UI branch reads as "not connected" rather than guessing.
            val available = false
            val granted = false
            _state.update {
                it.copy(
                    loading = false,
                    connections = connections,
                    readings = readings,
                    hcAvailable = available,
                    hcPermissionsGranted = granted
                )
            }
        }
    }

    fun isConnected(platform: String): Boolean =
        _state.value.connections.any { it.platform == platform && it.status == "connected" }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    /** Apple Health = Coming Soon (no Android SDK). */
    fun markAppleComingSoon(isThai: Boolean) {
        viewModelScope.launch {
            repo.upsertConnection(
                WearablePlatform.APPLE_HEALTH,
                ChildSelection.current(),
                "Apple Health",
                status = "coming_soon"
            )
            _state.update {
                it.copy(
                    message = if (isThai) "Apple Health รองรับบน iOS — เร็วๆ นี้"
                    else "Apple Health is Coming Soon (iOS only)"
                )
            }
            refresh()
        }
    }

    // connectHcPlatform() registered Google Fit / Samsung / Fitbit / Garmin as Health Connect
    // bridges. Health Connect is gone, and with it the only thing those connections did.

    fun disconnectPlatform(platform: String) {
        viewModelScope.launch {
            repo.disconnect(platform, ChildSelection.current())
            refresh()
        }
    }

    // syncNow() pulled the last 48 hours out of Health Connect. Health Connect was removed
    // with the feature it served, and nothing else fed this path - a smart crib or baby monitor
    // writes into device_readings directly, so there is no pull step to trigger.

    /**
     * Pair a child IoT device. Storage + warning path is live; BLE vendor SDKs can replace the
     * seed snapshot later without changing the parent analysis hooks.
     */
    fun connectIotDevice(platform: String, displayName: String, isThai: Boolean) {
        viewModelScope.launch {
            val childId = ChildSelection.current()
            if (childId == null) {
                _state.update {
                    it.copy(error = if (isThai) "ยังไม่ได้เลือกโปรไฟล์เด็ก" else "No child selected")
                }
                return@launch
            }
            repo.upsertConnection(platform, childId, displayName, status = "connected")
            seedIotSnapshot(childId, platform)
            _state.update {
                it.copy(
                    message = if (isThai) "เชื่อมต่อ $displayName แล้ว"
                    else "Linked $displayName"
                )
            }
            refresh()
        }
    }

    fun logIotReading(metric: String, value: Double, unit: String?, source: String, isThai: Boolean) {
        viewModelScope.launch {
            val childId = ChildSelection.current() ?: return@launch
            val (warn, reason) = repo.evaluateWarning(metric, value)
            repo.insertReading(
                childId = childId,
                source = source,
                metric = metric,
                value = value,
                unit = unit,
                recordedAt = Instant.now().toString(),
                isWarning = warn,
                warningReason = reason
            )
            if (warn) {
                NotificationRepository().notify(
                    NotifCategory.WARNING,
                    if (isThai) "แจ้งเตือนจากอุปกรณ์" else "Device warning",
                    reason
                )
            }
            refresh()
        }
    }

    private suspend fun seedIotSnapshot(childId: String, platform: String) {
        val now = Instant.now().toString()
        val samples = when (platform) {
            WearablePlatform.SMART_CRIB -> listOf(
                DeviceReadingInsert(childId, platform, WearableMetric.ROOM_TEMP_C, 24.5, "celsius", now),
                DeviceReadingInsert(childId, platform, WearableMetric.MOVEMENT, 1.0, "level", now)
            )
            WearablePlatform.BABY_MONITOR -> {
                val hr = 118.0
                val (w, r) = repo.evaluateWarning(WearableMetric.HEART_RATE, hr)
                listOf(
                    DeviceReadingInsert(
                        childId, platform, WearableMetric.HEART_RATE, hr, "bpm", now,
                        isWarning = w, warningReason = r
                    ),
                    DeviceReadingInsert(childId, platform, WearableMetric.ROOM_TEMP_C, 23.0, "celsius", now)
                )
            }
            else -> emptyList()
        }
        repo.insertReadingsBatch(samples)
        repo.markSynced(platform, childId)
    }
}
