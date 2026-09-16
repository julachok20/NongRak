package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Typhoon's note on how to talk to this parent (parent_ai_style). Written by ai-chat, never by the app. */
@Serializable
data class ParentAiStyle(
    @SerialName("user_id") val userId: String = "",
    val style: String = "",
    val enabled: Boolean = true,
    /** Settings › ตั้งค่า AI เอง: warm / cheerful / professional, null = automatic. */
    val tone: String? = null,
    /** short / balanced / detailed, null = automatic. */
    @SerialName("answer_length") val answerLength: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/**
 * What the parent can do with the note: read it, switch learning off, wipe it.
 *
 * Every write is read back, so a change that RLS or the network silently dropped is reported
 * instead of shown as done.
 */
class ParentStyleRepository {

    private fun userId(): String = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")

    /** Null when nothing has been learnt or switched yet. */
    suspend fun get(): Result<ParentAiStyle?> = runCatching {
        supabase.from("parent_ai_style").select {
            filter { eq("user_id", userId()) }
        }.decodeSingleOrNull<ParentAiStyle>()
    }

    suspend fun setEnabled(enabled: Boolean): Result<ParentAiStyle> = runCatching {
        val uid = userId()
        supabase.from("parent_ai_style").upsert(
            buildJsonObject {
                put("user_id", uid)
                put("enabled", enabled)
            }
        ) { onConflict = "user_id" }
        val saved = get().getOrThrow() ?: error("parent_style_not_saved")
        check(saved.enabled == enabled) { "parent_style_enabled_not_saved: wanted $enabled, got ${saved.enabled}" }
        saved
    }

    /** The parent's own choices; null means automatic. Only keys allowed by the table's check constraints. */
    suspend fun setPreferences(tone: String?, answerLength: String?): Result<ParentAiStyle> = runCatching {
        val uid = userId()
        supabase.from("parent_ai_style").upsert(
            buildJsonObject {
                put("user_id", uid)
                put("tone", tone)
                put("answer_length", answerLength)
            }
        ) { onConflict = "user_id" }
        val saved = get().getOrThrow() ?: error("parent_style_not_saved")
        check(saved.tone == tone && saved.answerLength == answerLength) {
            "ai_preferences_not_saved: wanted $tone/$answerLength, got ${saved.tone}/${saved.answerLength}"
        }
        saved
    }

    /** Clears what was learnt and restarts the count; the on/off choice is kept. */
    suspend fun reset(): Result<ParentAiStyle> = runCatching {
        val uid = userId()
        supabase.from("parent_ai_style").upsert(
            buildJsonObject {
                put("user_id", uid)
                put("style", "")
                put("answers_since_update", 0)
            }
        ) { onConflict = "user_id" }
        val saved = get().getOrThrow() ?: error("parent_style_not_saved")
        check(saved.style.isEmpty()) { "parent_style_not_cleared: ${saved.style.length} chars remain" }
        saved
    }
}
