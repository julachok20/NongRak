package com.lullabyai.app.data.repository

import com.lullabyai.app.data.handoff.*
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.*

class HandoffRepository {
    val userId: String get() = supabase.auth.currentUserOrNull()?.id.orEmpty()
    suspend fun context(childId:String, handoffId:String?=null):HandoffContext = supabase.postgrest.rpc("handoff_context",buildJsonObject {
        put("p_child_id",childId); put("p_handoff_id",handoffId?.let(::JsonPrimitive) ?: JsonNull)
    }).decodeAs<HandoffContext>()
    suspend fun list(childId: String): List<CareHandoff> = supabase.from("care_handoffs").select {
        filter { eq("child_id", childId) }
        order("created_at", Order.DESCENDING)
        limit(100)
    }.decodeList<CareHandoff>()

    suspend fun send(childId: String, draft: HandoffDraft) {
        val tasks = draft.checklist.lines().map(String::trim).filter(String::isNotEmpty)
        require(tasks.size <= 20 && tasks.all { it.length <= 300 }) { "Maximum 20 tasks, 300 characters each" }
        val expected = draft.expectedEnd.trim().takeIf { it.isNotEmpty() }?.let {
            java.time.OffsetDateTime.parse(it).toInstant().toString()
        }
        supabase.postgrest.rpc("handoff_create", buildJsonObject {
            put("p_id", draft.id); put("p_child_id", childId); put("p_recipient_id", draft.recipient)
            put("p_instructions", draft.notes)
            put("p_tasks", JsonArray(tasks.map(::JsonPrimitive)))
            put("p_expected_end", expected?.let(::JsonPrimitive) ?: JsonNull)
        })
    }

    /**
     * Sends one briefing to every caregiver ticked in Permission settings.
     *
     * The recipient list is the server's to decide, not the client's: a tick removed a moment ago
     * must not still receive the child's medical instructions. An expected end is required here
     * (unlike the 1:1 send) because a shift with no end never clears from the caregiver's banner.
     */
    suspend fun broadcast(childId: String, draft: HandoffDraft): HandoffBroadcastResult {
        val tasks = draft.checklist.lines().map(String::trim).filter(String::isNotEmpty)
        require(tasks.size <= 20 && tasks.all { it.length <= 300 }) { "Maximum 20 tasks, 300 characters each" }
        val expected = draft.expectedEnd.trim().takeIf { it.isNotEmpty() }
            ?.let { java.time.OffsetDateTime.parse(it).toInstant().toString() }
            ?: throw IllegalArgumentException("handoff_expected_end_required")
        return supabase.postgrest.rpc("handoff_broadcast", buildJsonObject {
            put("p_child_id", childId)
            put("p_instructions", draft.notes)
            put("p_tasks", JsonArray(tasks.map(::JsonPrimitive)))
            put("p_expected_end", expected)
        }).decodeAs<HandoffBroadcastResult>()
    }

    suspend fun act(id: String, action: String, note: String = "", index: Int? = null, done: Boolean? = null) {
        supabase.postgrest.rpc("handoff_act", buildJsonObject {
            put("p_id", id); put("p_action", action); put("p_note", note)
            put("p_task_index", index?.let(::JsonPrimitive) ?: JsonNull)
            put("p_done", done?.let(::JsonPrimitive) ?: JsonNull)
        })
    }
}
