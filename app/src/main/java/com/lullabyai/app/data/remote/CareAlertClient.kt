package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * "แจ้งเตือนด่วนไปยังคุณแม่ทันที" — asks line-notify-parents to push one LINE message to the child's
 * parents. Every check (membership, who counts as a parent, the daily cap) is in the function; this
 * only carries the request and reports the answer truthfully.
 */
object CareAlertClient {

    /** What happened, in words the caregiver screen can show without guessing. */
    data class Outcome(val sent: Boolean, val delivered: Int, val reason: String?)

    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }
    private val url = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/line-notify-parents"

    suspend fun notifyParents(childId: String, text: String): Result<Outcome> = runCatching {
        val token = supabase.auth.currentAccessTokenOrNull() ?: error("not signed in")
        val raw = client.post(url) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("child_id", childId)
                put("text", text.take(400))
            }.toString())
        }.bodyAsText()
        // A non-JSON body (gateway error page) is reported with what actually came back.
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            error("line-notify-parents returned non-JSON: ${raw.take(120)}")
        }
        Outcome(
            sent = root["sent"]?.jsonPrimitive?.booleanOrNull == true,
            delivered = root["delivered"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
            reason = root["reason"]?.jsonPrimitive?.contentOrNull
        )
    }
}
