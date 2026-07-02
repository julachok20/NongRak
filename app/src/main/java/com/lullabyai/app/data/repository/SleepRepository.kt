package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.model.SleepSchedule
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.util.TimeUtils
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDateTime

class SleepRepository {

    // ---------------- Sleep sessions (logs) ----------------

    suspend fun getLogs(childId: String): Result<List<SleepLog>> = runCatching {
        supabase.from("sleep_logs").select {
            filter { eq("child_id", childId) }
            order("start_time", Order.DESCENDING)
        }.decodeList<SleepLog>()
    }

    /** The currently in-progress session (end_time IS NULL), if any. */
    suspend fun getActiveSession(childId: String): Result<SleepLog?> = runCatching {
        getLogs(childId).getOrDefault(emptyList()).firstOrNull { it.endTime == null }
    }

    suspend fun startSession(childId: String, sleepType: String): Result<SleepLog> = runCatching {
        supabase.from("sleep_logs").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("sleep_type", JsonPrimitive(sleepType))
                put("start_time", JsonPrimitive(TimeUtils.toUtcIso(LocalDateTime.now())))
            }
        ) { select() }.decodeSingle<SleepLog>()
    }

    suspend fun endSession(id: String, mood: String?): Result<Unit> = runCatching {
        supabase.from("sleep_logs").update(
            buildJsonObject {
                put("end_time", JsonPrimitive(TimeUtils.toUtcIso(LocalDateTime.now())))
                put("mood", mood?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    /** Manually log a completed sleep period. [start]/[end] are naive local wall-clock ISO strings
     *  (e.g. "2026-07-02T20:00:00") — converted to proper UTC instants before writing. */
    suspend fun addManualLog(childId: String, sleepType: String, start: String, end: String): Result<Unit> = runCatching {
        supabase.from("sleep_logs").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("sleep_type", JsonPrimitive(sleepType))
                put("start_time", JsonPrimitive(TimeUtils.toUtcIso(LocalDateTime.parse(start))))
                put("end_time", JsonPrimitive(TimeUtils.toUtcIso(LocalDateTime.parse(end))))
            }
        )
    }

    suspend fun deleteLog(id: String): Result<Unit> = runCatching {
        supabase.from("sleep_logs").delete { filter { eq("id", id) } }
    }

    // ---------------- Recurring schedules ----------------

    suspend fun getSchedules(childId: String): Result<List<SleepSchedule>> = runCatching {
        supabase.from("sleep_schedules").select {
            filter { eq("child_id", childId) }
            order("start_time", Order.ASCENDING)
        }.decodeList<SleepSchedule>()
    }

    suspend fun createSchedule(childId: String, label: String, sleepType: String, startTime: String, endTime: String, daysOfWeek: String): Result<Unit> = runCatching {
        supabase.from("sleep_schedules").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("label", JsonPrimitive(label))
                put("sleep_type", JsonPrimitive(sleepType))
                put("start_time", JsonPrimitive(startTime))
                put("end_time", JsonPrimitive(endTime))
                put("days_of_week", JsonPrimitive(daysOfWeek))
            }
        )
    }

    suspend fun deleteSchedule(id: String): Result<Unit> = runCatching {
        supabase.from("sleep_schedules").delete { filter { eq("id", id) } }
    }
}
