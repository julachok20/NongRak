package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.AppNotification
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Notification categories. */
object NotifCategory {
    const val SCHEDULE = "schedule"
    const val WARNING = "warning"
    const val NORMAL = "normal"
    const val SYNC_REQUEST = "sync_request"
}

/**
 * Categories worth spending a LINE push on.
 *
 * The OA is on the free plan: 300 push messages PER MONTH across every user combined, not per
 * user. Mirroring all four categories would exhaust that in days on a handful of families and
 * then fail silently for everyone, including the alerts that actually matter.
 *
 * NORMAL is excluded on purpose — AI tips and daily reports are worth reading in the app, not
 * worth a scarce push. Widen this only after checking the quota, or after upgrading the plan.
 * Replies from the webhook (the pairing confirmation) are free and do not count against it.
 */
private val LINE_PUSH_CATEGORIES = setOf(
    NotifCategory.WARNING,
    NotifCategory.SCHEDULE,
    NotifCategory.SYNC_REQUEST
)

class NotificationRepository {

    suspend fun getNotifications(): List<AppNotification> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        supabase.from("notifications").select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList<AppNotification>()
    }.getOrDefault(emptyList())

    /**
     * Add a notification: posts a real Android system notification, records the in-app history
     * row, and mirrors it to LINE when the parent has linked their account.
     *
     * The LINE copy matters because SystemNotifier only fires while this process is running the
     * code — there is no FCM, so nothing here reaches a phone with the app closed. LINE is
     * currently the only channel that does.
     */
    suspend fun notify(category: String, title: String, body: String? = null) {
        // Device pop-up notification (fires even if the DB insert below fails / user offline).
        runCatching { com.lullabyai.app.util.SystemNotifier.show(title, body) }
        // Foreground copy. A system notification posted while the parent is already inside the app
        // is a heads-up they are looking straight past; this is the one they actually see.
        runCatching { com.lullabyai.app.ui.components.InAppAlerts.post(category, title, body) }
        // Best effort and never fatal: an unlinked parent, notifications switched off, or a
        // blocked OA all return false. The in-app row below stays the source of truth.
        if (category in LINE_PUSH_CATEGORIES) runCatching {
            LineRepository.push(listOfNotNull(title, body?.takeIf { it.isNotBlank() }).joinToString("\n"))
        }
        runCatching {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            supabase.from("notifications").insert(
                buildJsonObject {
                    put("user_id", JsonPrimitive(userId))
                    put("category", JsonPrimitive(category))
                    put("title", JsonPrimitive(title))
                    if (body != null) put("body", JsonPrimitive(body))
                }
            )
        }
    }

    suspend fun markAllRead() {
        runCatching {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return
            supabase.from("notifications").update(
                buildJsonObject { put("is_read", JsonPrimitive(true)) }
            ) { filter { eq("user_id", userId); eq("is_read", false) } }
        }
    }

    suspend fun unreadCount(): Int = getNotifications().count { !it.isRead }
}
