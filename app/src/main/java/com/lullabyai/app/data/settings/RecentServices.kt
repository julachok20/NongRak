package com.lullabyai.app.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Process-wide list of recently-opened service keys (newest first), persisted via SettingsManager.
 * The dashboard reads [list] for its "บริการล่าสุด" row; any screen records a visit with [push].
 * Initialize once at app start with [init].
 */
object RecentServices {
    private val _list = MutableStateFlow<List<String>>(emptyList())
    val list: StateFlow<List<String>> = _list

    // Per-service open counts — used to order the "บริการหลัก" section by the user's activity.
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts

    private var manager: SettingsManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(settingsManager: SettingsManager) {
        if (manager != null) return
        manager = settingsManager
        scope.launch { settingsManager.recentServices.collect { _list.value = it } }
        scope.launch { settingsManager.serviceUsage.collect { _counts.value = it } }
    }

    fun push(key: String) {
        manager?.let { m -> scope.launch { m.pushRecentService(key) } }
    }
}
