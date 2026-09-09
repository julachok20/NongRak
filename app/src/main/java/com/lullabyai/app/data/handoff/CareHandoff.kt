package com.lullabyai.app.data.handoff

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class HandoffTask(val text: String, val done: Boolean = false)

@Serializable
data class HandoffUpcoming(val id:String, val title:String, @SerialName("starts_at") val startsAt:String)
@Serializable
data class HandoffContext(
    @SerialName("health_allowed") val healthAllowed:Boolean=false,
    val recent:List<com.lullabyai.app.data.model.ChildActivityEntry> = emptyList(),
    val upcoming:List<HandoffUpcoming> = emptyList(),
    @SerialName("as_of") val asOf:String=""
)

@Serializable
data class CareHandoff(
    val id: String,
    @SerialName("child_id") val childId: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val status: String = "pending",
    val instructions: String = "",
    val tasks: List<HandoffTask> = emptyList(),
    @SerialName("return_note") val returnNote: String = "",
    @SerialName("expected_end") val expectedEnd: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("closed_at") val closedAt: String? = null
)

/** UI affordances only; the RPC repeats every transition/actor check under a row lock. */
fun handoffActions(h: CareHandoff, userId: String): List<String> = when {
    userId == h.recipientId && h.status == "pending" -> listOf("accept", "decline")
    userId == h.senderId && h.status == "pending" -> listOf("cancel")
    userId == h.recipientId && h.status == "active" -> listOf("complete")
    userId == h.senderId && h.status == "completed" -> listOf("acknowledge")
    else -> emptyList()
}

@Serializable
data class HandoffDraft(
    val id: String = java.util.UUID.randomUUID().toString(),
    val recipient: String = "",
    val notes: String = "",
    val checklist: String = "",
    val expectedEnd: String = ""
)
