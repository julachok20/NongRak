package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.DiaryMemory
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Duration.Companion.seconds

class DiaryRepository {

    companion object {
        const val BUCKET = "diary-media"
        private const val SIGNED_TTL_SEC = 3600L
    }

    suspend fun getMemories(childId: String): Result<List<DiaryMemory>> = runCatching {
        supabase.from("diary_memories").select {
            filter { eq("child_id", childId) }
            order("memory_date", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<DiaryMemory>()
    }

    suspend fun getMemoriesForYear(childId: String, year: Int): Result<List<DiaryMemory>> = runCatching {
        val start = "%04d-01-01".format(year)
        val end = "%04d-12-31".format(year)
        supabase.from("diary_memories").select {
            filter {
                eq("child_id", childId)
                gte("memory_date", start)
                lte("memory_date", end)
            }
            order("memory_date", Order.DESCENDING)
        }.decodeList<DiaryMemory>()
    }

    suspend fun saveMemory(
        id: String?,
        childId: String,
        title: String?,
        description: String?,
        memoryDate: String,
        mediaType: String,
        mediaPath: String?,
        mimeType: String?,
        durationSec: Int?,
        milestoneTag: String?,
        isFavorite: Boolean,
        isMilestone: Boolean,
        category: String?
    ): Result<DiaryMemory> = runCatching {
        val payload = buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("title", title?.let { JsonPrimitive(it) } ?: JsonNull)
            put("description", description?.let { JsonPrimitive(it) } ?: JsonNull)
            put("memory_date", JsonPrimitive(memoryDate))
            put("media_type", JsonPrimitive(mediaType))
            put("media_path", mediaPath?.let { JsonPrimitive(it) } ?: JsonNull)
            put("mime_type", mimeType?.let { JsonPrimitive(it) } ?: JsonNull)
            put("duration_sec", durationSec?.let { JsonPrimitive(it) } ?: JsonNull)
            put("milestone_tag", milestoneTag?.let { JsonPrimitive(it) } ?: JsonNull)
            put("is_favorite", JsonPrimitive(isFavorite))
            put("is_milestone", JsonPrimitive(isMilestone))
            put("category", category?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        if (id == null) {
            supabase.from("diary_memories").insert(payload) { select() }.decodeSingle<DiaryMemory>()
        } else {
            supabase.from("diary_memories").update(payload) {
                filter { eq("id", id) }
                select()
            }.decodeSingle<DiaryMemory>()
        }
    }

    suspend fun toggleFavorite(id: String, favorite: Boolean): Result<Unit> = runCatching {
        supabase.from("diary_memories").update(
            buildJsonObject { put("is_favorite", JsonPrimitive(favorite)) }
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteMemory(id: String, mediaPath: String?): Result<Unit> = runCatching {
        if (!mediaPath.isNullOrBlank()) {
            runCatching { supabase.storage.from(BUCKET).delete(mediaPath) }
        }
        supabase.from("diary_memories").delete { filter { eq("id", id) } }
    }

    /** Uploads bytes to private diary-media bucket under uid/childId/… Returns the stored PATH. */
    suspend fun uploadMedia(
        childId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String?
    ): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val path = "$userId/$childId/${System.currentTimeMillis()}_$safeName"
        supabase.storage.from(BUCKET).upload(path, bytes) { upsert = true }
        path
    }

    suspend fun signedMediaUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            supabase.storage.from(BUCKET).createSignedUrl(path, SIGNED_TTL_SEC.seconds)
        }.getOrNull()
    }
}
