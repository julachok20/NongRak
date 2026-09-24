package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.ktor.client.*
import io.ktor.client.engine.okhttp.OkHttp
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

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            socketTimeoutMillis = 90_000
        }
    }

    private val functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/ai-generate"

    /** Raw 24kHz mono PCM from Gemini TTS, ready to hand to AudioTrack without decoding. */
    data class Speech(val pcm: ByteArray, val sampleRate: Int, val provider: String)

    /**
     * Speaks [text] through Gemini's TTS models, via the same edge function that holds the keys.
     *
     * Returns null on any failure — no retry, no second provider. The caller falls back to the
     * device's own TextToSpeech, which is free and offline; spending another round trip to sound
     * slightly better is not worth making the companion mute.
     */
    suspend fun synthesize(text: String, voice: String? = null, style: String? = null): Speech? {
        val token = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        if (token.isNullOrBlank()) return null
        return runCatching {
            val body = buildJsonObject {
                put("prompt", text)
                put("mode", "tts")
                if (!voice.isNullOrBlank()) put("voice", voice)
                if (!style.isNullOrBlank()) put("style", style)
            }
            val raw = client.post(functionUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
                header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            val root = json.parseToJsonElement(raw).jsonObject
            val b64 = (root["audioBase64"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: return null
            val mime = (root["mimeType"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            // e.g. "audio/L16;codec=pcm;rate=24000"
            val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24_000
            Speech(
                pcm = android.util.Base64.decode(b64, android.util.Base64.DEFAULT),
                sampleRate = rate,
                provider = (root["provider"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
            )
        }.getOrNull()
    }

    suspend fun generate(
        prompt: String,
        useSearch: Boolean,
        maxTokens: Int,
        errors: MutableList<String> = mutableListOf(),
        system: String? = null,
        jsonMode: Boolean? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        model: String? = null
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
                // Names a model the self-hosted box serves; the cloud providers ignore it.
                if (!model.isNullOrBlank()) put("model", model)
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
