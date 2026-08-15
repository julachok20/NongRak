package com.lullabyai.app.data.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-wide holder for the currently-selected child id (multi-child support).
 *
 * Repositories need the selection synchronously and have no Context, so this singleton keeps the id
 * in memory while persisting it to DataStore via [SettingsManager]. Initialize once at app start
 * with [init]; read with [current] / observe via [id]; change with [set].
 */
object ChildSelection {
    private val _id = MutableStateFlow<String?>(null)
    val id: StateFlow<String?> = _id

    // Becomes true once the persisted value has been read at least once. Lets readers avoid
    // clobbering a saved selection with a fallback before DataStore has loaded (cold-start race).
    private val _loaded = MutableStateFlow(false)

    private var manager: SettingsManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(settingsManager: SettingsManager) {
        if (manager != null) return
        manager = settingsManager
        // Load the persisted selection and keep tracking external changes.
        scope.launch {
            settingsManager.selectedChildId.collect { _id.value = it; _loaded.value = true }
        }
    }

    /** Suspends until the persisted selection has been read (so fallbacks don't clobber it). */
    suspend fun awaitLoaded() { _loaded.first { it } }

    fun set(childId: String?) {
        _id.value = childId
        manager?.let { m -> scope.launch { m.setSelectedChildId(childId) } }
    }

    fun current(): String? = _id.value

    /**
     * Drops the selected child on sign-out.
     *
     * The back stack is already cleared when logging out, but this selection is a device-level
     * singleton backed by DataStore — without clearing it the next account starts out pointed at
     * the previous user's child, which they may not even have access to. `_loaded` resets too, so
     * the next sign-in waits for that account's own persisted value rather than reusing the stale
     * one.
     */
    fun clearForSignOut() {
        _id.value = null
        _loaded.value = false
        manager?.let { m -> scope.launch { m.setSelectedChildId(null) } }
    }
}
