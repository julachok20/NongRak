package com.lullabyai.app.data.remote

import android.util.Log
import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Gets a short-lived credential for a Gemini Live voice call.
 *
 * The Google API key stays in the `gemini-live-token` edge function as a Supabase secret. The
 * device receives a single-use token that expires in minutes, so nothing worth stealing is ever
 * compiled into the app.
 *
 * Every failure returns null rather than throwing: the caller falls back to the turn-based call,
 * which works without Gemini Live at all. A voice call that quietly degrades beats one that dies.
 */
object GeminiLiveToken {

    private const val TAG = "GeminiLiveToken"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // A call is being placed while the parent waits, so this cannot hang on a dead network.
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TokenResponse(
        val token: String? = null,
        @SerialName("expiresAt") val expiresAt: String? = null,
        val error: String? = null
    )

    suspend fun fetch(): String? {
        val accessToken = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        if (accessToken.isNullOrBlank()) {
            Log.w(TAG, "Not signed in; cannot mint a Live token.")
            return null
        }

        val url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/gemini-live-token"
        val request = Request.Builder()
            .url(url)
            .post(ByteArray(0).toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Says what came back, not only that something went wrong - the three cases
                    // (not deployed, secret missing, key rejected) need different fixes.
                    Log.w(TAG, "Token request failed: HTTP ${response.code} ${body.take(200)}")
                    return@use null
                }
                val parsed = runCatching { json.decodeFromString<TokenResponse>(body) }.getOrNull()
                if (parsed?.error != null) {
                    Log.w(TAG, "Token request refused: ${parsed.error}")
                    return@use null
                }
                parsed?.token?.takeIf { it.isNotBlank() }
            }
        }.getOrElse {
            Log.w(TAG, "Token request threw: ${it.message}")
            null
        }
    }
}
