package com.lullabyai.app.data.remote

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveClientTest {

    @Test
    fun `session duration formats correctly to MM SS`() {
        fun formatDuration(seconds: Long): String {
            val m = seconds / 60
            val s = seconds % 60
            return String.format("%02d:%02d", m, s)
        }

        assertEquals("00:00", formatDuration(0L))
        assertEquals("00:45", formatDuration(45L))
        assertEquals("05:30", formatDuration(330L))
        assertEquals("12:00", formatDuration(720L))
        assertEquals("14:59", formatDuration(899L))
        assertEquals("15:00", formatDuration(900L))
    }

    @Test
    fun `session timer thresholds warn at 12 min and prompt dialog at 14 min`() {
        fun checkThresholds(seconds: Long): Pair<Boolean, Boolean> {
            val isWarning = seconds >= 720
            val isDialog = seconds >= 840
            return isWarning to isDialog
        }

        // 5 minutes (normal)
        val (warn5, dialog5) = checkThresholds(300L)
        assertFalse(warn5)
        assertFalse(dialog5)

        // 12 minutes (warn banner on screen)
        val (warn12, dialog12) = checkThresholds(720L)
        assertTrue(warn12)
        assertFalse(dialog12)

        // 13 minutes 30 seconds (still warning)
        val (warn13, dialog13) = checkThresholds(810L)
        assertTrue(warn13)
        assertFalse(dialog13)

        // 14 minutes (refresh dialog prompt appears)
        val (warn14, dialog14) = checkThresholds(840L)
        assertTrue(warn14)
        assertTrue(dialog14)
    }

    @Test
    fun `tool call JSON parsing extracts function name callId and arguments`() {
        val rawJson = """
        {
            "toolCall": {
                "functionCalls": [
                    {
                        "name": "consult_nongrak_brain",
                        "id": "call_abc123",
                        "args": {
                            "symptoms": "น้องมีไข้ 38.5 องศา ตัวร้อนตั้งแต่เที่ยง"
                        }
                    }
                ]
            }
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(rawJson).jsonObject
        val functionCalls = root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray

        val call = functionCalls?.firstOrNull()?.jsonObject
        val name = call?.get("name")?.jsonPrimitive?.contentOrNull
        val id = call?.get("id")?.jsonPrimitive?.contentOrNull
        val symptoms = call?.get("args")?.jsonObject?.get("symptoms")?.jsonPrimitive?.contentOrNull

        assertEquals("consult_nongrak_brain", name)
        assertEquals("call_abc123", id)
        assertEquals("น้องมีไข้ 38.5 องศา ตัวร้อนตั้งแต่เที่ยง", symptoms)
    }

    @Test
    fun `tool response JSON construction matches Gemini Live protocol`() {
        val callId = "call_abc123"
        val adviceText = "เช็ดตัวลดไข้ด้วยน้ำอุณหภูมิห้อง และสังเกตอาการซึม"

        val responseMsg = buildJsonObject {
            putJsonObject("toolResponse") {
                putJsonArray("functionResponses") {
                    addJsonObject {
                        putJsonObject("response") {
                            putJsonObject("output") {
                                put("advice", adviceText)
                            }
                        }
                        put("id", callId)
                    }
                }
            }
        }

        val jsonString = responseMsg.toString()
        assertTrue(jsonString.contains("toolResponse"))
        assertTrue(jsonString.contains("functionResponses"))
        assertTrue(jsonString.contains("call_abc123"))
        assertTrue(jsonString.contains("เช็ดตัวลดไข้"))
    }

    @Test
    fun `little-endian PCM 16-bit byte conversion preserves samples`() {
        val sample: Short = 0x1234
        val byte0 = (sample.toInt() and 0xFF).toByte()
        val byte1 = ((sample.toInt() shr 8) and 0xFF).toByte()

        assertEquals(0x34.toByte(), byte0)
        assertEquals(0x12.toByte(), byte1)

        val reconstructed = ((byte1.toInt() and 0xFF) shl 8) or (byte0.toInt() and 0xFF)
        assertEquals(0x1234, reconstructed)
    }
}
