package com.lullabyai.app.data.offline

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.settings.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Queues a growth-record write that failed (offline / flaky connection) and retries it later —
 * scoped to growth records only, as the one representative write worth covering for this pass
 * (per the "lightweight, not a full sync engine" scope). [flush] is called at app start
 * (NongRakApp.onCreate) so a record logged while offline goes through the next time the app opens
 * with a working connection, instead of silently vanishing.
 */
object PendingWriteQueue {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("pending_growth_records")

    private suspend fun readAll(): List<GrowthRecord> {
        val raw = NongRakApp.instance.dataStore.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<GrowthRecord>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun writeAll(records: List<GrowthRecord>) {
        NongRakApp.instance.dataStore.edit { it[key] = json.encodeToString(records) }
    }

    suspend fun enqueueGrowthRecord(record: GrowthRecord) {
        writeAll(readAll() + record)
    }

    /** Attempts every queued record; keeps only the ones that still fail. Safe to call repeatedly
     *  (e.g. every app start) — an empty queue is a no-op. */
    suspend fun flush() {
        val pending = readAll()
        if (pending.isEmpty()) return
        val repo = HealthRepository()
        val stillPending = pending.filter { repo.addGrowthRecord(it).isFailure }
        writeAll(stillPending)
    }
}
