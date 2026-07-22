package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.ClaudeClient

/** Live-camera "scan surroundings, give advice" feature — one short vision call per captured
 *  frame. Deliberately terse output (single sentence) since this renders as an overlay banner on
 *  a live camera preview, not a chat message. */
object SurroundingsScanService {
    suspend fun analyzeSurroundings(imageBase64: String, isThai: Boolean): String? {
        val system = if (isThai) {
            "คุณคือผู้ช่วยที่ดูภาพจากกล้องแบบเรียลไทม์เพื่อความปลอดภัยของเด็กเล็ก " +
                "ตอบสั้นที่สุดเพียง 1 ประโยค (ไม่เกิน 20 คำ) บอกสิ่งที่สังเกตเห็นในภาพที่เกี่ยวข้องกับความปลอดภัยของเด็ก " +
                "(เช่น ของมีคม ปลั๊กไฟ พื้นลื่น สิ่งของขนาดเล็กที่กลืนได้ ระดับความสูงที่อาจตกได้) " +
                "หากไม่มีอะไรน่ากังวล ให้ตอบสั้นๆ ว่าดูปลอดภัยดี ห้ามใช้ markdown หรือ emoji"
        } else {
            "You are watching a live camera feed for a young child's safety. Reply in exactly ONE short " +
                "sentence (under 20 words) naming anything in view relevant to child safety (sharp objects, " +
                "outlets, slippery floors, small swallowable items, fall hazards). If nothing concerning, " +
                "briefly say it looks safe. No markdown or emoji."
        }
        return runCatching {
            ClaudeClient.generateContent(
                prompt = if (isThai) "ดูภาพนี้และให้คำแนะนำสั้นๆ" else "Look at this image and give brief advice",
                useSearch = false, maxTokens = 80, system = system, jsonMode = false,
                imageBase64 = imageBase64, imageMimeType = "image/jpeg"
            )
        }.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }
}
