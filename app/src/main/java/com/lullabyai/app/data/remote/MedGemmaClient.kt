package com.lullabyai.app.data.remote

import com.lullabyai.app.BuildConfig
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
 * Calls a self-hosted MedGemma endpoint (Hugging Face Inference Endpoint or Replicate) that
 * exposes the OpenAI-compatible Chat Completions API at  {endpoint}/v1/chat/completions.
 *
 * Configured via local.properties → MEDGEMMA_ENDPOINT + MEDGEMMA_TOKEN.
 * Returns the response text on success, or null on any failure / when not configured —
 * so ClaudeClient can fall back to Claude/Gemini.
 */
object MedGemmaClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000   // hosted models can be slower on cold start
            socketTimeoutMillis = 90_000
        }
    }

    /** True when an endpoint is configured. Token is optional (Colab/vLLM often has no auth). */
    val isConfigured: Boolean
        get() = com.lullabyai.app.data.settings.AiEndpointConfig.effectiveEndpoint.isNotBlank()

    /** {base}/v1/chat/completions, accepting a base that already names the path. */
    private fun chatUrl(base: String): String {
        val trimmed = base.trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/v1/chat/completions"
    }

    /**
     * One real call, with the failure reported instead of swallowed.
     *
     * [tryGenerate] returns null on any problem so chat can fall back to Claude, which is right in
     * production and useless while setting a server up: "no answer" hides a wrong port, a missing
     * token and a VPN that is not connected. This is what the settings screen's test button uses.
     */
    suspend fun testConnection(): Result<String> {
        val cfg = com.lullabyai.app.data.settings.AiEndpointConfig
        val endpoint = cfg.effectiveEndpoint
        if (endpoint.isBlank()) return Result.failure(IllegalStateException("ai_endpoint_missing"))
        return try {
            val body = buildJsonObject {
                put("model", cfg.effectiveModel.ifBlank { "tgi" })
                put("max_tokens", 16)
                putJsonArray("messages") {
                    addJsonObject { put("role", "user"); put("content", "ping") }
                }
            }
            val response = client.post(chatUrl(endpoint)) {
                if (cfg.effectiveToken.isNotBlank()) {
                    header("Authorization", "Bearer ${cfg.effectiveToken}")
                }
                header("ngrok-skip-browser-warning", "true")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val text = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return Result.failure(IllegalStateException("http_${response.status.value}: ${text.take(200)}"))
            }
            val errors = mutableListOf<String>()
            val answer = parseText(text, errors)
                ?: return Result.failure(IllegalStateException(errors.joinToString().ifBlank { "unreadable_response" }))
            Result.success(answer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Same persona/style/safety rules used across the app
    private val systemPrompt = """
คุณคือ "น้องรัก" (NongRak) ผู้ช่วย AI สำหรับพ่อแม่ในการเลี้ยงดูเด็กแรกเกิดถึง 3 ปี
หน้าที่ของคุณคือให้คำแนะนำและตอบคำถามของพ่อแม่เกี่ยวกับลูกวัย 0-3 ปี โดยอ้างอิงแนวทางจาก WHO, AAP และกระทรวงสาธารณสุขไทย

แนวคิดหลักของผู้ช่วย (สำคัญที่สุด):
คุณเป็นผู้ช่วยที่ "แนะนำ ให้กำลังใจ และเสริมพลัง" ไม่ใช่ทำสิ่งต่างๆ แทนพ่อแม่
- คุณจะไม่ทำงานแทนผู้ปกครอง แต่จะให้คำแนะนำเป็นขั้นตอนที่ชัดเจนและละเอียด เพื่อให้พ่อแม่ลงมือทำด้วยตัวเอง
- จุดมุ่งหมายคือสร้างความมั่นใจ ปลุกพลังใจ และทำให้พ่อแม่รู้สึกว่า "ฉันทำได้"
- วางบทบาทเป็นโค้ชหรือพี่เลี้ยงที่อบอุ่น ชวนและให้กำลังใจให้ลงมือทำ ไม่ใช่ทำให้แทน

ข้อกำหนดในการตอบ:
- ตอบเป็นภาษาไทยเสมอ ยกเว้นผู้ใช้พิมพ์ภาษาอังกฤษจึงตอบเป็นภาษาอังกฤษ
- ห้ามใช้สัญลักษณ์พิเศษหรือ markdown และห้ามใช้ emoji
- ใช้ภาษาที่เป็นธรรมชาติ ฟังดูเหมือนมนุษย์ที่อบอุ่นและเข้าใจง่าย
- หากมีอาการเร่งด่วน ให้แนะนำพบแพทย์ทันที
""".trim()

    /**
     * Tries MedGemma. Returns text on success, null on failure (caller falls back).
     * [errors] collects a short reason for debugging.
     */
    suspend fun tryGenerate(
        prompt: String,
        maxTokens: Int = 1024,
        errors: MutableList<String> = mutableListOf()
    ): String? {
        if (!isConfigured) { errors += "MedGemma: not configured"; return null }

        // Normalize endpoint → {base}/v1/chat/completions. A value typed on the device (a WireGuard
        // address, say) wins over whatever was compiled into the build.
        val url = chatUrl(com.lullabyai.app.data.settings.AiEndpointConfig.effectiveEndpoint)

        return try {
            // Model id: HF Inference Providers needs the full id (e.g. google/medgemma-4b-it:featherless-ai);
            // a self-hosted TGI/vLLM server accepts "tgi". Configurable via MEDGEMMA_MODEL.
            val model = com.lullabyai.app.data.settings.AiEndpointConfig.effectiveModel.ifBlank { "tgi" }
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", maxTokens)
                put("temperature", 0.7)
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", systemPrompt) }
                    addJsonObject { put("role", "user"); put("content", prompt) }
                }
            }

            val raw = client.post(url) {
                // Auth only if a token is set (paid HF endpoints need it; Colab/vLLM usually don't)
                val token = com.lullabyai.app.data.settings.AiEndpointConfig.effectiveToken
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
                header("ngrok-skip-browser-warning", "true")  // harmless; bypasses ngrok free interstitial
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            parseText(raw, errors)
        } catch (e: Exception) {
            errors += "MedGemma: net(${e.message?.take(40)})"
            null
        }
    }

    private fun parseText(raw: String, errors: MutableList<String>): String? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            if (root.containsKey("error")) {
                val msg = root["error"].let {
                    it?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                        ?: it?.jsonPrimitive?.contentOrNull
                } ?: "unknown"
                errors += "MedGemma: ${msg.take(60)}"
                return null
            }
            root["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            errors += "MedGemma: parse(${e.message?.take(30)})"
            null
        }
    }
}
