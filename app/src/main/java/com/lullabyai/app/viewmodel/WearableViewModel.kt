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
import com.lullabyai.app.data.wearables.HealthConnectSync
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
            val available = HealthConnectSync.isAvailable(ctx)
            val granted = if (available) HealthConnectSync.hasAllPermissions(ctx) else false
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

    /**
     * Connect a Health-Connect-backed platform (Google Fit / Samsung / Fitbit / Garmin / HC).
     * Caller should request HC permissions first when the SDK is available.
     */
    fun connectHcPlatform(platform: String, displayName: String, isThai: Boolean) {
        viewModelScope.launch {
            val childId = ChildSelection.current()
            repo.upsertConnection(platform, childId, displayName, status = "connected")
            if (platform != WearablePlatform.HEALTH_CONNECT) {
                repo.upsertConnection(
                    WearablePlatform.HEALTH_CONNECT, childId, "Health Connect", status = "connected"
                )
            }
            syncNow(isThai)
        }
    }

    fun disconnectPlatform(platform: String) {
        viewModelScope.launch {
            repo.disconnect(platform, ChildSelection.current())
            refresh()
        }
    }

    fun syncNow(isThai: Boolean) {
        viewModelScope.launch {
            val childId = ChildSelection.current()
            if (childId == null) {
                _state.update {
                    it.copy(error = if (isThai) "ยังไม่ได้เลือกโปรไฟล์เด็ก" else "No child selected")
                }
                return@launch
            }
            _state.update { it.copy(syncing = true, error = null, message = null) }
            val ctx = getApplication<Application>()
            if (!HealthConnectSync.isAvailable(ctx)) {
                _state.update {
                    it.copy(
                        syncing = false,
                        error = if (isThai) "อุปกรณ์นี้ยังไม่มี Health Connect — กรุณาติดตั้งจาก Play Store"
                        else "Health Connect is not available — install it from the Play Store"
                    )
                }
                refresh()
                return@launch
            }
            if (!HealthConnectSync.hasAllPermissions(ctx)) {
                _state.update {
                    it.copy(
                        syncing = false,
                        error = if (isThai) "ยังไม่ได้ให้สิทธิ์อ่านข้อมูลสุขภาพ" else "Health permissions not granted yet"
                    )
                }
                refresh()
                return@launch
            }
            val rows = HealthConnectSync.readRecent(ctx, childId, hours = 48)
            repo.insertReadingsBatch(rows)
            repo.markSynced(WearablePlatform.HEALTH_CONNECT, childId)

            // Immediate parent warnings for freshly synced abnormal samples.
            val notif = NotificationRepository()
            rows.filter { it.isWarning }.take(3).forEach { r ->
                notif.notify(NotifCategory.WARNING, if (isThai) "แจ้งเตือนจากอุปกรณ์" else "Device warning", r.warningReason)
            }

            _state.update {
                it.copy(
                    syncing = false,
                    message = if (isThai) "ซิงค์แล้ว ${rows.size} รายการ" else "Synced ${rows.size} readings"
                )
            }
            refresh()
        }
    }

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
