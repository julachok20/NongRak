package com.lullabyai.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "nongrak_settings")

enum class AppLanguage { TH, EN }

data class AppSettings(
    val darkMode: Boolean = false,
    val language: AppLanguage = AppLanguage.TH,
    val dailyReport: Boolean = true,
    val healthAlert: Boolean = true,
    val aiTips: Boolean = false
)

class SettingsManager(private val context: Context) {

    private val DARK_MODE = booleanPreferencesKey("dark_mode")
    private val LANGUAGE = stringPreferencesKey("language")
    private val DAILY_REPORT = booleanPreferencesKey("daily_report")
    private val HEALTH_ALERT = booleanPreferencesKey("health_alert")
    private val AI_TIPS = booleanPreferencesKey("ai_tips")
    private val SELECTED_CHILD = stringPreferencesKey("selected_child_id")

    private val RECENT_SERVICES = stringPreferencesKey("recent_services")

    /** The child the user is currently viewing (multi-child support). Null = not set yet. */
    val selectedChildId: Flow<String?> = context.dataStore.data.map { it[SELECTED_CHILD] }

    suspend fun setSelectedChildId(value: String?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(SELECTED_CHILD) else prefs[SELECTED_CHILD] = value
        }
    }

    private val SERVICE_USAGE = stringPreferencesKey("service_usage")
    private val REMINDED_KEYS = stringPreferencesKey("vaccine_reminded_keys")

    /** Dedupe markers (e.g. "vaccineId:3") so a vaccine reminder fires once per offset, not repeatedly. */
    suspend fun getRemindedKeys(): Set<String> =
        context.dataStore.data.first()[REMINDED_KEYS]?.split("|||")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    suspend fun setRemindedKeys(keys: Set<String>) {
        context.dataStore.edit { it[REMINDED_KEYS] = keys.joinToString("|||") }
    }

    /** Recently-opened service keys, newest first (comma-separated). For the dashboard "บริการล่าสุด". */
    val recentServices: Flow<List<String>> = context.dataStore.data.map {
        it[RECENT_SERVICES]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

    /** How many times each service was opened (key:count,...). For ordering "บริการหลัก" by activity. */
    val serviceUsage: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        (prefs[SERVICE_USAGE] ?: "").split(",").mapNotNull { entry ->
            val p = entry.split(":"); if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
        }.toMap()
    }

    /** Records a service open: bumps recency AND the usage count (both feed the dashboard). */
    suspend fun pushRecentService(key: String) {
        context.dataStore.edit { prefs ->
            val cur = prefs[RECENT_SERVICES]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[RECENT_SERVICES] = (listOf(key) + cur.filter { it != key }).take(4).joinToString(",")

            val counts = (prefs[SERVICE_USAGE] ?: "").split(",").mapNotNull { e ->
                val p = e.split(":"); if (p.size == 2) p[0] to (p[1].toIntOrNull() ?: 0) else null
            }.toMap().toMutableMap()
            counts[key] = (counts[key] ?: 0) + 1
            prefs[SERVICE_USAGE] = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            darkMode = prefs[DARK_MODE] ?: false,
            language = if (prefs[LANGUAGE] == "EN") AppLanguage.EN else AppLanguage.TH,
            dailyReport = prefs[DAILY_REPORT] ?: true,
            healthAlert = prefs[HEALTH_ALERT] ?: true,
            aiTips = prefs[AI_TIPS] ?: false
        )
    }

    suspend fun setDarkMode(value: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = value }
    }

    suspend fun setLanguage(value: AppLanguage) {
        context.dataStore.edit { it[LANGUAGE] = value.name }
    }

    suspend fun setDailyReport(value: Boolean) {
        context.dataStore.edit { it[DAILY_REPORT] = value }
    }

    suspend fun setHealthAlert(value: Boolean) {
        context.dataStore.edit { it[HEALTH_ALERT] = value }
    }

    suspend fun setAiTips(value: Boolean) {
        context.dataStore.edit { it[AI_TIPS] = value }
    }
}
