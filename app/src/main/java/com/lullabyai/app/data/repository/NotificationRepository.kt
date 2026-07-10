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

class NotificationRepository {

    suspend fun getNotifications(): List<AppNotification> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        supabase.from("notifications").select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
            limit(100)
        }.decodeList<AppNotification>()
    }.getOrDefault(emptyList())

    /** Add a notification: posts a real Android system notification AND records the in-app history row. */
    suspend fun notify(category: String, title: String, body: String? = null) {
        // Device pop-up notification (fires even if the DB insert below fails / user offline).
        runCatching { com.lullabyai.app.util.SystemNotifier.show(title, body) }
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
