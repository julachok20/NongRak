package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Talks to the `ai-chat` edge function, which runs the Spark pipeline
 * (ShieldGemma -> Gemma 4 key points -> Typhoon 2 -> ShieldGemma) on the family's own hardware.
 *
 * The app never holds the gateway key: it sends the signed-in parent's Supabase token, and the
 * function holds `SPARK_LLM_KEY`. Failures come back as named reasons ("daily_limit_reached",
 * "upstream_502: …") so the caller can decide between showing them and falling back.
 */
object SparkChatClient {

    data class Reply(
        val text: String,
        val blocked: Boolean,
        val stages: List<String>,
        /** Command-center proposals; empty unless the parent gave something to record. */
        val commands: List<com.lullabyai.app.data.model.AiCommand> = emptyList()
    )

    /** [reasons]: why it was refused (e.g. "monarchy", "fake_news", "check_unavailable"), for the message shown. */
    data class Moderation(val allowed: Boolean, val checked: Boolean, val reasons: List<String> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Several model calls in sequence on one GPU: a medical answer can take a couple of minutes.
    // The socket timeout is the gap between streamed pieces, not the whole answer.
    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 180_000
            socketTimeoutMillis = 180_000
        }
    }

    private val functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/ai-chat"

    private suspend fun accessToken(): String {
        val token = runCatching { supabase.auth.currentAccessTokenOrNull() }.getOrNull()
        if (token.isNullOrBlank()) throw IllegalStateException("spark_not_logged_in")
        return token
    }

    private suspend fun post(body: JsonObject): Pair<Int, String> {
        val token = accessToken()
        val response = client.post(functionUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        return response.status.value to response.bodyAsText()
    }

    /**
     * Asks the pipeline and streams the answer as Typhoon writes it.
     *
     * [onPartial] receives the whole answer so far each time more arrives. The returned [Reply] is
     * the checked final text — which, when ShieldGemma rejects the answer, is a safe replacement
     * for everything [onPartial] showed.
     *
     * The function answers with plain JSON instead of a stream when it stops before generating
     * (question blocked, daily limit, gateway refused); that is read exactly as before.
     *
     * @param question the parent's own words — what the safety check and model routing judge.
     * @param prompt the built prompt (child profile, RAG snippets, recent messages) the models answer.
     * @param system the app's system prompt, prepended to the function's own rules.
     * @param sessionId lets the function add this chat's keyword memory and fold old messages into it.
     * @param imageBase64 an attached photo (already downscaled by the caller). The function reads it
     *   with Typhoon OCR, or Gemma 4 vision when it has no text; it is never stored.
     * @param childIds the children this parent may ask about, so Gemma 4's data look-ups know where
     *   to read. Row-level security still decides what each read can return.
     */
    suspend fun chat(
        question: String,
        prompt: String,
        system: String,
        sessionId: String? = null,
        imageBase64: String? = null,
        imageMime: String? = null,
        childIds: List<String> = emptyList(),
        onPartial: (String) -> Unit = {}
    ): Result<Reply> = runCatching {
        val token = accessToken()
        client.preparePost(functionUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("question", question)
                    put("prompt", prompt)
                    put("system", system)
                    put("stream", true)
                    // Record look-ups and commands read and write times in the parent's own zone.
                    put("tz", java.util.TimeZone.getDefault().id)
                    if (!sessionId.isNullOrBlank()) put("session_id", sessionId)
                    if (!imageBase64.isNullOrBlank() && !imageMime.isNullOrBlank()) {
                        put("image", buildJsonObject {
                            put("data", imageBase64)
                            put("mime", imageMime)
                        })
                    }
                    if (childIds.isNotEmpty()) {
                        put("child_ids", kotlinx.serialization.json.JsonArray(childIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                    }
                }.toString()
            )
        }.execute { response ->
            if (response.contentType()?.match(ContentType.Text.EventStream) == true) {
                readStream(response.bodyAsChannel(), onPartial)
            } else {
                readJsonReply(response.status.value, response.bodyAsText())
            }
        }
    }

    /** Events: `delta` (more text), `done` / `blocked` (checked final text), `error` (named failure). */
    private suspend fun readStream(channel: ByteReadChannel, onPartial: (String) -> Unit): Reply {
        val answer = StringBuilder()
        while (true) {
            val line = channel.readUTF8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            val event = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            val type = event["type"]?.jsonPrimitive?.contentOrNull
            val text = event["text"]?.jsonPrimitive?.contentOrNull
            when (type) {
                "delta" -> if (!text.isNullOrEmpty()) {
                    answer.append(text)
                    onPartial(answer.toString())
                }
                "done", "blocked" -> return Reply(
                    text = text?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("spark_empty_reply"),
                    blocked = type == "blocked",
                    stages = stagesOf(event),
                    commands = commandsOf(event)
                )
                "error" -> throw IllegalStateException(
                    event["error"]?.jsonPrimitive?.contentOrNull ?: "spark_stream_error"
                )
            }
        }
        throw IllegalStateException("spark_stream_ended_early after ${answer.length} chars")
    }

    private fun readJsonReply(status: Int, raw: String): Reply {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse {
            throw IllegalStateException("spark_unreadable_$status: ${raw.take(160)}")
        }
        root["error"]?.jsonPrimitive?.contentOrNull?.let { throw IllegalStateException(it) }
        if (status !in 200..299) throw IllegalStateException("spark_http_$status")
        val text = root["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("spark_empty_reply")
        return Reply(
            text = text,
            blocked = root["blocked"]?.jsonPrimitive?.booleanOrNull ?: false,
            stages = stagesOf(root),
            commands = commandsOf(root)
        )
    }

    private fun stagesOf(obj: JsonObject): List<String> =
        runCatching { obj["stages"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()

    /** A malformed proposal is dropped rather than shown: a card that cannot run must not appear. */
    private fun commandsOf(obj: JsonObject): List<com.lullabyai.app.data.model.AiCommand> =
        runCatching {
            obj["commands"]?.jsonArray?.mapNotNull { element ->
                runCatching {
                    json.decodeFromJsonElement(com.lullabyai.app.data.model.AiCommand.serializer(), element)
                }.getOrNull()
            }
        }.getOrNull().orEmpty()

    /**
     * Runs one confirmed command-center ticket. The function checks the ticket's signature, owner and
     * expiry, runs it once, and writes with this person's own login, so their role decides what is
     * allowed. Failures come back named ("ticket_expired", "ticket_already_used",
     * "command_failed: 42501 …") for the card to show.
     */
    suspend fun execute(ticket: String): Result<Unit> = runCatching {
        val (status, raw) = post(
            buildJsonObject {
                put("mode", "execute")
                put("ticket", ticket)
            }
        )
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        root?.get("error")?.jsonPrimitive?.contentOrNull?.let { throw IllegalStateException(it) }
        if (status !in 200..299) throw IllegalStateException("command_http_$status: ${raw.take(160)}")
    }

    /**
     * ShieldGemma's verdict on community text. If the check cannot run, the text is allowed and
     * [Moderation.checked] is false — a safety model that is down must not silence the community.
     */
    suspend fun moderate(text: String): Moderation {
        if (text.isBlank()) return Moderation(allowed = true, checked = false)
        return runCatching {
            val (status, raw) = post(
                buildJsonObject {
                    put("mode", "moderate")
                    put("text", text)
                }
            )
            if (status !in 200..299) {
                Moderation(allowed = true, checked = false)
            } else {
                val root = json.parseToJsonElement(raw).jsonObject
                Moderation(
                    allowed = root["allowed"]?.jsonPrimitive?.booleanOrNull ?: true,
                    checked = root["checked"]?.jsonPrimitive?.booleanOrNull ?: false
                )
            }
        }.getOrDefault(Moderation(allowed = true, checked = false))
    }

    /**
     * Text plus the pictures / video frames that would go with it, screened before anything is
     * uploaded. Each image is read by OCR (its text judged by ShieldGemma) and looked at by gemma-4.
     *
     * Unlike [moderate] this fails CLOSED when media is attached: a gateway that cannot be reached
     * blocks the post, because the alternative is publishing a picture nothing has checked.
     */
    suspend fun moderateMedia(text: String, images: List<ByteArray>): Moderation {
        if (text.isBlank() && images.isEmpty()) return Moderation(allowed = true, checked = false)
        val blocked = Moderation(allowed = images.isEmpty(), checked = false)
        return runCatching {
            val (status, raw) = post(
                buildJsonObject {
                    put("mode", "moderate")
                    put("text", text)
                    put(
                        "images",
                        kotlinx.serialization.json.JsonArray(
                            images.map { bytes ->
                                buildJsonObject {
                                    put("data", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                                    put("mime", "image/jpeg")
                                }
                            }
                        )
                    )
                }
            )
            if (status !in 200..299) {
                blocked
            } else {
                val root = json.parseToJsonElement(raw).jsonObject
                Moderation(
                    allowed = root["allowed"]?.jsonPrimitive?.booleanOrNull ?: images.isEmpty(),
                    checked = root["checked"]?.jsonPrimitive?.booleanOrNull ?: false,
                    reasons = runCatching {
                        root["reasons"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    }.getOrNull().orEmpty()
                )
            }
        }.getOrDefault(blocked)
    }
}
