package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.routine.CareRoutine
import com.lullabyai.app.data.routine.CareRoutineLog
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * The child's repeating day and today's ticks.
 *
 * Who may do what is enforced in Postgres: any member may read and tick, but only parents (or
 * whoever created an item) may change the routine itself — a nanny follows the plan rather than
 * rewriting it. The writes here select their row back, because an RLS refusal is otherwise a
 * successful-looking call that changed nothing.
 */
class RoutineRepository {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    fun today(): String = LocalDate.now().toString()

    suspend fun listRoutines(childId: String): Result<List<CareRoutine>> = runCatching {
        supabase.from("care_routines").select {
            filter { eq("child_id", childId); eq("active", true) }
            order("time_of_day", Order.ASCENDING)
        }.decodeList<CareRoutine>()
    }

    suspend fun listLogs(childId: String, date: String): Result<List<CareRoutineLog>> = runCatching {
        supabase.from("care_routine_logs").select {
            filter { eq("child_id", childId); eq("on_date", date) }
        }.decodeList<CareRoutineLog>()
    }

    suspend fun addRoutine(
        childId: String,
        timeOfDay: String,
        category: String,
        title: String,
        detail: String,
        prepNote: String = ""
    ): Result<CareRoutine> = runCatching {
        val rows = supabase.from("care_routines").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("time_of_day", JsonPrimitive(timeOfDay))
                put("category", JsonPrimitive(category))
                put("title", JsonPrimitive(title.trim()))
                put("detail", JsonPrimitive(detail.trim()))
                put("prep_note", JsonPrimitive(prepNote.trim().take(300)))
            }
        ) { select() }.decodeList<CareRoutine>()
        rows.firstOrNull() ?: error("routine_insert_denied: 0 rows returned for child $childId")
    }

    suspend fun updateRoutine(
        id: String,
        timeOfDay: String,
        category: String,
        title: String,
        detail: String,
        prepNote: String = ""
    ): Result<Unit> = runCatching {
        val rows = supabase.from("care_routines").update(
            buildJsonObject {
                put("time_of_day", JsonPrimitive(timeOfDay))
                put("category", JsonPrimitive(category))
                put("title", JsonPrimitive(title.trim()))
                put("detail", JsonPrimitive(detail.trim()))
                put("prep_note", JsonPrimitive(prepNote.trim().take(300)))
            }
        ) {
            filter { eq("id", id) }
            select()
        }.decodeList<CareRoutine>()
        check(rows.isNotEmpty()) { "routine_update_denied: 0 rows updated for $id" }
    }

    /**
     * Removing an item hides it instead of deleting it, so yesterday's ticks still refer to
     * something and a mis-tap does not erase a week of history.
     */
    suspend fun deactivateRoutine(id: String): Result<Unit> = runCatching {
        val rows = supabase.from("care_routines").update(
            buildJsonObject { put("active", JsonPrimitive(false)) }
        ) {
            filter { eq("id", id) }
            select()
        }.decodeList<CareRoutine>()
        check(rows.isNotEmpty()) { "routine_remove_denied: 0 rows updated for $id" }
    }

    /** Tick or un-tick one item for one day. [done] false records a deliberate skip with a reason. */
    suspend fun setLog(
        childId: String,
        routineId: String,
        date: String,
        done: Boolean,
        note: String
    ): Result<Unit> = runCatching {
        val me = userId ?: error("routine_log_signed_out")
        supabase.from("care_routine_logs").upsert(
            buildJsonObject {
                put("routine_id", JsonPrimitive(routineId))
                put("child_id", JsonPrimitive(childId))
                put("on_date", JsonPrimitive(date))
                put("status", JsonPrimitive(if (done) "done" else "skipped"))
                put("note", JsonPrimitive(note.trim().take(500)))
                // RLS requires the row to belong to the person writing it.
                put("actor_id", JsonPrimitive(me))
                put("recorded_at", JsonPrimitive(java.time.Instant.now().toString()))
            }
        ) { onConflict = "routine_id, on_date" }
        Unit
    }

    /** Undo today's record for one item, putting it back to "not done yet". */
    suspend fun clearLog(routineId: String, date: String): Result<Unit> = runCatching {
        supabase.from("care_routine_logs").delete {
            filter { eq("routine_id", routineId); eq("on_date", date) }
        }
        Unit
    }
}
