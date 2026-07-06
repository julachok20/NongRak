package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

/**
 * Calls the Supabase Edge Function "ai-generate", which holds the Gemini/Claude keys server-side.
 * The app no longer ships any AI API keys — it sends the user's Supabase JWT and gets back text.
 */
object AiProxyClient {

    data class Result(val text: String?, val provider: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 90_000
        }
    }

    private val functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/ai-generate"

    suspend fun generate(
        prompt: String,
        useSearch: Boolean,
        maxTokens: Int,
        errors: MutableList<String> = mutableListOf(),
        system: String? = null,
        jsonMode: Boolean? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null
    ): Result {
        val token = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        if (token.isNullOrBlank()) {
            errors += "proxy: not logged in"
            return Result(null, "")
        }
        return try {
            val body = buildJsonObject {
                put("prompt", prompt)
                put("useSearch", useSearch)
                put("maxTokens", maxTokens)
                if (!system.isNullOrBlank()) put("system", system)
                if (jsonMode != null) put("jsonMode", jsonMode)
                if (!imageBase64.isNullOrBlank()) {
                    put("imageBase64", imageBase64)
                    put("imageMimeType", imageMimeType ?: "image/jpeg")
                }
            }
            val raw = client.post(functionUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            val root = json.parseToJsonElement(raw).jsonObject
            if (root.containsKey("error")) {
                errors += "proxy: ${root["error"]?.jsonPrimitive?.contentOrNull?.take(60)}"
                return Result(null, "")
            }
            val text = root["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val provider = root["provider"]?.jsonPrimitive?.contentOrNull ?: ""
            Result(text, provider)
        } catch (e: Exception) {
            errors += "proxy: net(${e.message?.take(40)})"
            Result(null, "")
        }
    }
}
