package com.lullabyai.app.data.remote

import android.accounts.AccountManager
import com.lullabyai.app.NongRakApp
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Stores the search-result cache as a single JSON file in the user's Google Drive
 * app-data folder (hidden from the user, only visible to this app).
 *
 * Used as a fallback when Supabase write/read fails (quota exceeded, network, etc.).
 * Reads the file once per app session (lazy) and keeps an in-memory copy;
 * writes back the full file every time an entry is added.
 */
object GoogleDriveClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000; socketTimeoutMillis = 30_000 }
    }

    private const val DRIVE_FILES   = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD_URL    = "https://www.googleapis.com/upload/drive/v3/files"
    private const val DRIVE_SCOPE   = "oauth2:https://www.googleapis.com/auth/drive.appdata"
    private const val CACHE_FILE    = "lullaby_ai_search_cache.json"

    // In-memory mirror of the Drive JSON file
    private val memCache = mutableMapOf<String, CacheEntry>()
    private var loaded   = false
    private var fileId: String? = null

    data class CacheEntry(val query: String, val result: String, val expires: String)

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Returns a non-expired cached result for [hash], or null. */
    suspend fun getCached(hash: String): String? {
        ensureLoaded()
        val entry = memCache[hash] ?: return null
        val expires = runCatching { Instant.parse(entry.expires) }.getOrNull() ?: return null
        return if (Instant.now().isBefore(expires)) entry.result else null
    }

    /** Saves a new cache entry to Drive (after Supabase failed). */
    suspend fun saveCache(hash: String, query: String, result: String, expires: String) {
        ensureLoaded()
        memCache[hash] = CacheEntry(query, result, expires)
        pruneExpired()
        writeToDrive()
    }

    // ------------------------------------------------------------------
    // Token
    // ------------------------------------------------------------------

    /**
     * Gets a Google OAuth2 access token with Drive appdata scope via AccountManager.
     * Matches the signed-in Supabase user's email when possible; otherwise uses
     * the first Google account on the device.
     */
    private suspend fun getToken(): String? = withContext(Dispatchers.IO) {
        try {
            val ctx = NongRakApp.instance
            val userEmail = runCatching<String?> {
                supabase.auth.currentUserOrNull()?.email
            }.getOrNull()

            val am = AccountManager.get(ctx)
            val accounts = am.getAccountsByType("com.google")
            if (accounts.isEmpty()) return@withContext null

            val account = if (userEmail != null)
                accounts.firstOrNull { it.name.equals(userEmail, ignoreCase = true) }
                    ?: accounts.first()
            else accounts.first()

            // blockingGetAuthToken must run on IO thread; notifyAuthFailure=false → silent fail
            am.blockingGetAuthToken(account, DRIVE_SCOPE, false)
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Drive operations
    // ------------------------------------------------------------------

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true          // set first to prevent re-entry on concurrent calls
        val token = getToken() ?: return
        try {
            fileId = findFileId(token)
            fileId?.let { id ->
                val content = downloadFile(token, id)
                if (content != null) parseContent(content)
            }
        } catch (e: Exception) { /* Drive not reachable — proceed with empty cache */ }
    }

    private suspend fun writeToDrive() {
        val token = getToken() ?: return
        try {
            val content = buildJson()
            val existingId = fileId ?: findFileId(token)
            if (existingId != null) {
                updateFile(token, existingId, content)
                fileId = existingId
            } else {
                fileId = createFile(token, content)
            }
        } catch (e: Exception) { /* silent — Drive is best-effort */ }
    }

    private suspend fun findFileId(token: String): String? = runCatching {
        val raw = client.get(DRIVE_FILES) {
            header("Authorization", "Bearer $token")
            parameter("q", "name='$CACHE_FILE' and trashed=false")
            parameter("spaces", "appDataFolder")
            parameter("fields", "files(id)")
        }.bodyAsText()
        json.parseToJsonElement(raw).jsonObject["files"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private suspend fun downloadFile(token: String, id: String): String? = runCatching {
        client.get("$DRIVE_FILES/$id") {
            header("Authorization", "Bearer $token")
            parameter("alt", "media")
        }.bodyAsText()
    }.getOrNull()

    private suspend fun createFile(token: String, content: String): String? = runCatching {
        val boundary = "lullaby_${System.currentTimeMillis()}"
        val meta  = """{"name":"$CACHE_FILE","parents":["appDataFolder"]}"""
        val body  = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                    "--$boundary\r\nContent-Type: application/json\r\n\r\n$content\r\n--$boundary--"
        val raw = client.post("$UPLOAD_URL?uploadType=multipart") {
            header("Authorization", "Bearer $token")
            contentType(ContentType("multipart", "related",
                listOf(HeaderValueParam("boundary", boundary))))
            setBody(body)
        }.bodyAsText()
        json.parseToJsonElement(raw).jsonObject["id"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private suspend fun updateFile(token: String, id: String, content: String) {
        runCatching {
            client.patch("$UPLOAD_URL/$id?uploadType=media") {
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(content)
            }
        }
    }

    // ------------------------------------------------------------------
    // JSON helpers
    // ------------------------------------------------------------------

    private fun buildJson(): String = buildJsonObject {
        memCache.forEach { (hash, e) ->
            putJsonObject(hash) {
                put("query",   e.query)
                put("result",  e.result)
                put("expires", e.expires)
            }
        }
    }.toString()

    private fun parseContent(content: String) {
        runCatching {
            json.parseToJsonElement(content).jsonObject.forEach { (hash, v) ->
                val obj = v.jsonObject
                memCache[hash] = CacheEntry(
                    query   = obj["query"]?.jsonPrimitive?.contentOrNull  ?: "",
                    result  = obj["result"]?.jsonPrimitive?.contentOrNull  ?: "",
                    expires = obj["expires"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            }
        }
    }

    private fun pruneExpired() {
        val now = Instant.now()
        val expired = memCache.entries
            .filter { (_, v) -> runCatching { Instant.parse(v.expires).isBefore(now) }.getOrDefault(true) }
            .map { it.key }
        expired.forEach { memCache.remove(it) }
    }
}
