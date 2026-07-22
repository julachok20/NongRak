package com.lullabyai.app.data.offline

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.settings.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generic read-through JSON cache backed by the app's existing Preferences DataStore — lets a
 * screen show its last-known-good data instead of a blank/error state when a network call fails
 * (offline, or a flaky connection).
 *
 * ponytail: last-successful-snapshot only, not a real sync engine — no conflict resolution, no
 * partial merging, uses the app-level [NongRakApp.instance] context so callers (repositories) don't
 * need Context threaded into their constructors. Upgrade to Room + WorkManager sync if true
 * offline-first (edits made offline reconciling with the server) becomes an actual requirement.
 */
object OfflineCache {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> put(key: String, value: T) {
        val encoded = json.encodeToString(value)
        NongRakApp.instance.dataStore.edit { it[stringPreferencesKey("offline_cache_$key")] = encoded }
    }

    suspend inline fun <reified T> get(key: String): T? {
        val raw = NongRakApp.instance.dataStore.data.first()[stringPreferencesKey("offline_cache_$key")] ?: return null
        return runCatching { json.decodeFromString<T>(raw) }.getOrNull()
    }
}
