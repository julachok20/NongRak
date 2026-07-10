package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

/**
 * Transparency + deletion for every table the AI reads for analysis (see
 * ChatRepository.buildChildContext / buildStatsSummary) — a parent-facing "what do you keep, let
 * me remove it" view, distinct from the existing full-account delete in Settings.
 */
class MedicalDataVaultRepository {

    data class Category(val table: String, val labelTh: String, val labelEn: String, val childScoped: Boolean = true)

    val categories = listOf(
        Category("health_records", "บันทึกสุขภาพ", "Health records"),
        Category("growth_records", "บันทึกการเจริญเติบโต", "Growth records"),
        Category("allergies", "ประวัติการแพ้", "Allergies"),
        Category("medical_history", "ประวัติทางการแพทย์", "Medical history"),
        Category("vaccine_records", "บันทึกวัคซีน", "Vaccine records"),
        Category("development_milestones", "พัฒนาการ", "Development milestones"),
        Category("sleep_logs", "บันทึกการนอน", "Sleep logs"),
        Category("food_logs", "บันทึกอาหาร", "Food logs"),
        Category("exercise_logs", "บันทึกการออกกำลังกาย", "Exercise logs"),
        Category("waste_records", "บันทึกการขับถ่าย", "Waste records"),
        Category("chat_messages", "ประวัติการสนทนากับ AI", "AI chat history", childScoped = false)
    )

    /** Row count for one category. childScoped categories need [childId]; account-scoped ones use
     *  the current user. Decodes into a minimal id-only shape — cheap enough for a settings screen. */
    suspend fun getCount(category: Category, childId: String?): Int = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return 0
        if (category.childScoped) {
            if (childId == null) return 0
            supabase.from(category.table).select {
                filter { eq("child_id", childId) }
            }.decodeList<kotlinx.serialization.json.JsonObject>().size
        } else {
            supabase.from(category.table).select {
                filter { eq("user_id", userId) }
            }.decodeList<kotlinx.serialization.json.JsonObject>().size
        }
    }.getOrDefault(0)

    suspend fun deleteCategory(category: Category, childId: String?): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        if (category.childScoped) {
            val cid = requireNotNull(childId) { "childId required for ${category.table}" }
            supabase.from(category.table).delete { filter { eq("child_id", cid) } }
        } else {
            supabase.from(category.table).delete { filter { eq("user_id", userId) } }
            supabase.from("chat_sessions").delete { filter { eq("user_id", userId) } }
        }
    }
}
