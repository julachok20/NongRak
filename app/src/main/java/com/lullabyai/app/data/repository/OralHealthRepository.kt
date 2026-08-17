package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.DailyOralRoutine
import com.lullabyai.app.data.model.OralHealthEvent
import com.lullabyai.app.data.model.ToothRecord
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * Repository managing teeth map status, oral health events/milestones, and daily oral hygiene routines.
 */
class OralHealthRepository {

    private fun today(): String = LocalDate.now().toString()

    // ---------------- Teeth Map ----------------

    suspend fun getTeethRecords(childId: String): Result<List<ToothRecord>> = runCatching {
        supabase.from("child_teeth_records").select {
            filter { eq("child_id", childId) }
        }.decodeList<ToothRecord>()
    }

    suspend fun saveToothRecord(
        childId: String,
        toothId: String,
        status: String,
        notes: String? = null,
        eruptedAt: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("child_teeth_records").upsert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("tooth_id", JsonPrimitive(toothId))
            put("status", JsonPrimitive(status))
            put("notes", notes?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("erupted_at", eruptedAt?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("updated_at", JsonPrimitive(java.time.Instant.now().toString()))
        }) {
            onConflict = "child_id, tooth_id"
        }
        Unit
    }

    // ---------------- Oral Health Events & Milestones ----------------

    suspend fun getOralHealthEvents(childId: String): Result<List<OralHealthEvent>> = runCatching {
        supabase.from("oral_health_events").select {
            filter { eq("child_id", childId) }
            order("event_date", Order.DESCENDING)
        }.decodeList<OralHealthEvent>()
    }

    suspend fun addOralHealthEvent(
        childId: String,
        category: String,
        eventName: String,
        eventDate: String = today(),
        notes: String? = null,
        photoUrls: List<String> = emptyList()
    ): Result<OralHealthEvent> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not authenticated")
        supabase.from("oral_health_events").insert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("user_id", JsonPrimitive(userId))
            put("category", JsonPrimitive(category))
            put("event_name", JsonPrimitive(eventName))
            put("event_date", JsonPrimitive(eventDate))
            put("notes", notes?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("photo_urls", JsonArray(photoUrls.map { JsonPrimitive(it) }))
        }) {
            select()
        }.decodeSingle<OralHealthEvent>()
    }

    suspend fun deleteOralHealthEvent(eventId: String): Result<Unit> = runCatching {
        supabase.from("oral_health_events").delete {
            filter { eq("id", eventId) }
        }
        Unit
    }

    // ---------------- Daily Oral Routine Tracker ----------------

    suspend fun getDailyRoutine(childId: String, date: String = today()): Result<DailyOralRoutine?> = runCatching {
        supabase.from("daily_oral_routines").select {
            filter { eq("child_id", childId); eq("routine_date", date) }
        }.decodeSingleOrNull<DailyOralRoutine>()
    }

    suspend fun saveDailyRoutine(
        childId: String,
        date: String = today(),
        morning: Boolean,
        lunch: Boolean,
        bedtime: Boolean,
        flossing: Boolean
    ): Result<Unit> = runCatching {
        supabase.from("daily_oral_routines").upsert(buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("routine_date", JsonPrimitive(date))
            put("morning", JsonPrimitive(morning))
            put("lunch", JsonPrimitive(lunch))
            put("bedtime", JsonPrimitive(bedtime))
            put("flossing", JsonPrimitive(flossing))
            put("updated_at", JsonPrimitive(java.time.Instant.now().toString()))
        }) {
            onConflict = "child_id, routine_date"
        }
        Unit
    }

    /**
     * Computes the 7-day consistency metric:
     * Returns Pair(completedDays, totalDays) e.g., Pair(5, 7)
     * A day is counted as complete if brushed at least 2 times (morning + bedtime/lunch).
     */
    suspend fun get7DayConsistency(childId: String): Result<Pair<Int, Int>> = runCatching {
        val startDate = LocalDate.now().minusDays(6).toString()
        val routines = supabase.from("daily_oral_routines").select {
            filter {
                eq("child_id", childId)
                gte("routine_date", startDate)
            }
        }.decodeList<DailyOralRoutine>()

        val routineMap = routines.associateBy { it.routineDate }
        var completedDays = 0
        val totalDays = 7

        for (i in 0 until totalDays) {
            val dateStr = LocalDate.now().minusDays(i.toLong()).toString()
            val r = routineMap[dateStr]
            if (r != null) {
                var count = 0
                if (r.morning) count++
                if (r.lunch) count++
                if (r.bedtime) count++
                if (count >= 2) completedDays++
            }
        }
        Pair(completedDays, totalDays)
    }
}
