package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.GoogleDriveClient
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

@Serializable
data class SearchCacheEntry(
    val id: String = "",
    @SerialName("query_hash")  val queryHash:  String  = "",
    @SerialName("query_text")  val queryText:  String  = "",
    val result:                                String  = "",
    val source:                                String  = "gemini_search",
    @SerialName("expires_at")  val expiresAt:  String? = null,
    @SerialName("created_at")  val createdAt:  String? = null
)

/**
 * General-purpose AI search-result cache.
 *
 * Priority:
 *   1. Supabase `search_cache` table  (cloud, shared across devices)
 *   2. Google Drive JSON file          (fallback when Supabase quota is exceeded)
 *
 * Cache TTL: [CACHE_TTL_HOURS] hours (configurable below).
 * Error responses are never saved — they expire naturally so the next call retries.
 */
class SearchCacheRepository {

    companion object {
        private const val CACHE_TTL_HOURS = 48L   // how long results stay fresh

        /** Stable hash of [text] used as the cache key (MD5, lowercase-normalised). */
        fun hashQuery(text: String): String {
            val normalised = text.lowercase().trim()
            val md = MessageDigest.getInstance("MD5")
            return md.digest(normalised.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    /**
     * Returns a cached AI response for [query], or null if not found / expired.
     * Tries Supabase first, then Google Drive.
     */
    suspend fun get(query: String): String? {
        val hash = hashQuery(query)
        return getCachedFromSupabase(hash) ?: GoogleDriveClient.getCached(hash)
    }

    // ------------------------------------------------------------------
    // Write
    // ------------------------------------------------------------------

    /**
     * Saves [result] for [query].
     * Tries Supabase; on failure (quota, network) falls back to Google Drive.
     * Error / blank responses are silently ignored.
     */
    suspend fun save(query: String, result: String, source: String = "gemini_search") {
        if (isErrorResponse(result)) return
        val hash    = hashQuery(query)
        val expires = Instant.now().plus(CACHE_TTL_HOURS, ChronoUnit.HOURS).toString()

        val savedToSupabase = trySaveToSupabase(hash, query, result, source, expires)
        if (!savedToSupabase) {
            GoogleDriveClient.saveCache(hash, query, result, expires)
        }
    }

    // ------------------------------------------------------------------
    // Supabase helpers
    // ------------------------------------------------------------------

    private suspend fun getCachedFromSupabase(hash: String): String? = runCatching {
        val now = Instant.now().toString()
        supabase.from("search_cache").select {
            filter {
                eq("query_hash", hash)
                gt("expires_at", now)
            }
            limit(1)
        }.decodeList<SearchCacheEntry>().firstOrNull()?.result
    }.getOrNull()

    private suspend fun trySaveToSupabase(
        hash: String, query: String, result: String,
        source: String, expires: String
    ): Boolean = runCatching {
        // Delete any previous entry for this hash, then insert fresh
        supabase.from("search_cache").delete { filter { eq("query_hash", hash) } }
        supabase.from("search_cache").insert(
            SearchCacheEntry(
                queryHash = hash,
                queryText = query,
                result    = result,
                source    = source,
                expiresAt = expires
            )
        )
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------
    // Guard
    // ------------------------------------------------------------------

    private fun isErrorResponse(text: String): Boolean {
        if (text.isBlank() || text.length < 30) return true
        val lower = text.lowercase()
        return lower.contains("quota")
            || lower.contains("ขออภัย")
            || lower.contains("กรุณาลองใหม่")
            || lower.contains("cannot")
            || lower.contains("error")
            || lower.contains("invalid")
            || lower.contains("api key")
    }
}
