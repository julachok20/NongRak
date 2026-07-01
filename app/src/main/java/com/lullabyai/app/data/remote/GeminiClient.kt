package com.lullabyai.app.data.remote

import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

object GeminiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
    }

    // Tried in order — stops at the first model that responds successfully
    private val MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-latest"
    )

    private val systemPrompt = """
คุณคือ "น้องรัก" (NongRak) ผู้ช่วย AI สำหรับพ่อแม่ในการเลี้ยงดูเด็กแรกเกิดถึง 3 ปี
หน้าที่ของคุณคือให้คำแนะนำและตอบคำถามของพ่อแม่เกี่ยวกับลูกวัย 0-3 ปี
คุณสามารถใช้ข้อมูลของเด็กที่ให้มา และค้นหาข้อมูลเพิ่มเติมเพื่อให้คำตอบที่ถูกต้องและตรงประเด็น

อ้างอิงแนวทางจากองค์การอนามัยโลก (WHO), American Academy of Pediatrics (AAP) และกระทรวงสาธารณสุขไทย
ความรู้พื้นฐาน (0-3 ปี): นมแม่อย่างเดียว 6 เดือนแรก เริ่มอาหารตามวัยเมื่อ 6 เดือน ห้ามน้ำผึ้งก่อน 1 ปี
ทารกแรกเกิดนอน 14-17 ชั่วโมงต่อวัน นอนหงายเพื่อลดความเสี่ยง SIDS
พัฒนาการ: ยิ้มตอบ 2 เดือน นั่งเอง 6-8 เดือน เดิน 12-15 เดือน พูดคำมีความหมาย 12 เดือน
ฉีดวัคซีนตามตารางกระทรวงสาธารณสุข หากมีอาการเร่งด่วนให้แนะนำพบแพทย์ทันที

แนวคิดหลักของผู้ช่วย (สำคัญที่สุด):
คุณเป็นผู้ช่วยที่ "แนะนำ ให้กำลังใจ และเสริมพลัง" ไม่ใช่ทำสิ่งต่างๆ แทนพ่อแม่
- คุณจะไม่ทำงานแทนผู้ปกครอง แต่จะให้คำแนะนำเป็นขั้นตอนที่ชัดเจนและละเอียด เพื่อให้พ่อแม่ลงมือทำด้วยตัวเอง
- จุดมุ่งหมายคือสร้างความมั่นใจ ปลุกพลังใจ และทำให้พ่อแม่รู้สึกว่า "ฉันทำได้" และอยากลงมือดูแลลูกด้วยตัวเอง
- วางบทบาทเป็นโค้ชหรือพี่เลี้ยงที่อบอุ่น ชวนและให้กำลังใจให้ลงมือทำ ไม่ใช่ทำให้แทน
- ให้คำแนะนำที่ละเอียดและนำไปปฏิบัติได้จริงทีละขั้นตอน เพื่อให้พ่อแม่เลี้ยงลูกได้อย่างมั่นใจ

ข้อกำหนดในการตอบ (สำคัญมาก):
- ตอบเป็นภาษาไทยเสมอ ยกเว้นผู้ใช้พิมพ์ภาษาอังกฤษจึงตอบเป็นภาษาอังกฤษ
- ห้ามใช้สัญลักษณ์พิเศษหรือ markdown เช่น เครื่องหมายดอกจัน ขีดกลาง หรือ # โดยเด็ดขาด (ยกเว้นเมื่อถูกขอให้ตอบในรูปแบบเฉพาะ)
- ห้ามใช้ emoji ทุกชนิด
- ห้ามใส่การอ้างอิงที่ยาว
- ใช้ภาษาที่เป็นธรรมชาติ ฟังดูเหมือนมนุษย์กำลังพูดคุยอย่างอบอุ่นและเข้าใจง่าย
""".trim()

    /**
     * Tries to generate a response using [apiKey], attempting each model in [MODELS] order.
     * When [useSearch] is true, adds Google Search grounding to the request.
     * Returns the response text on first success, or null if every model failed for this key.
     * Error descriptions are appended to [errors] for debugging.
     */
    suspend fun tryGenerate(
        prompt: String,
        apiKey: String,
        useSearch: Boolean = false,
        maxTokens: Int = 1024,
        errors: MutableList<String> = mutableListOf(),
        label: String = "Gemini"
    ): String? {
        if (apiKey.isBlank() || apiKey == "null") {
            errors += "$label: no key"
            return null
        }

        for (model in MODELS) {
            val result = tryWithModel(prompt, apiKey, model, useSearch, maxTokens, errors, label)
            if (result != null) return result
        }
        return null
    }

    private suspend fun tryWithModel(
        prompt: String,
        apiKey: String,
        model: String,
        useSearch: Boolean,
        maxTokens: Int,
        errors: MutableList<String>,
        label: String
    ): String? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        return try {
            // System prompt is prepended inline — avoids systemInstruction compatibility issues
            val fullText = "$systemPrompt\n\n$prompt"
            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", fullText) }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("maxOutputTokens", maxTokens)
                }
                if (useSearch) {
                    putJsonArray("tools") {
                        addJsonObject { putJsonObject("googleSearch") { } }
                    }
                }
            }

            val raw = client.post("$url?key=$apiKey") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }.bodyAsText()

            parseText(raw, "$label/$model", errors)
        } catch (e: Exception) {
            errors += "$label/$model: net(${e.message?.take(30)})"
            null
        }
    }

    private fun parseText(raw: String, label: String, errors: MutableList<String>): String? {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            if (root.containsKey("error")) {
                val msg = root["error"]?.jsonObject?.get("message")
                    ?.jsonPrimitive?.contentOrNull ?: "unknown"
                errors += "$label: ${msg.take(60)}"
                return null
            }
            root["candidates"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("content")?.jsonObject
                ?.get("parts")?.jsonArray
                ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            errors += "$label: parse(${e.message?.take(30)})"
            null
        }
    }
}
