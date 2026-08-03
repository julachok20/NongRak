package com.lullabyai.app.data.model

/**
 * Chat persona modes the parent can switch between (request: "รองรับโหมดเล่านิทาน สนทนากับเด็กได้").
 *
 * The system prompt itself is fixed inside ChatRepository.sendMessage (RelationshipLabels
 * .chatSystemPrompt) and is not per-message, so a mode instead prepends [promptPrefix] to the text
 * sent to the model. The bubble the parent sees keeps the raw text — see ChatViewModel.sendMessage.
 * ponytail: prefix over a new repository param, because the repo file is owned elsewhere; if the
 * prefix ever needs to stay out of stored history, add a `modeInstruction` param to the repo call.
 */
enum class ChatMode(val speechRate: Float, val speechPitch: Float) {
    /** Normal parenting assistant — unchanged behaviour. */
    NORMAL(1.0f, 1.0f),

    /** โหมดเล่านิทาน — bedtime storyteller, read slower. */
    STORY(0.82f, 1.0f),

    /** สนทนากับเด็ก — talks to the child directly, brighter and slightly faster. */
    CHILD(0.94f, 1.18f);

    fun label(isThai: Boolean): String = when (this) {
        NORMAL -> if (isThai) "ปกติ" else "Normal"
        STORY -> if (isThai) "เล่านิทาน" else "Story"
        CHILD -> if (isThai) "คุยกับเด็ก" else "For child"
    }

    /** Empty for [NORMAL] so normal chat sends exactly what it sends today. */
    fun promptPrefix(): String = when (this) {
        NORMAL -> ""
        STORY ->
            "[โหมดเล่านิทาน] ตอบในบทบาทผู้เล่านิทานสำหรับเด็กเล็ก เล่าเป็นเรื่องราวสั้น ๆ ต่อเนื่อง " +
                "ใช้ประโยคสั้น คำง่าย น้ำเสียงอบอุ่นนุ่มนวลเหมาะกับก่อนนอน ปิดท้ายด้วยข้อคิดสั้น ๆ " +
                "ข้อความจะถูกอ่านออกเสียง จึงห้ามใช้ markdown สัญลักษณ์พิเศษ หรือ emoji\n\n"
        CHILD ->
            "[โหมดสนทนากับเด็ก] ตอบในบทบาทเพื่อนคุยของเด็กเล็ก พูดกับเด็กโดยตรง ใช้ประโยคสั้นมาก คำง่าย " +
                "สนุก ถามกลับทีละคำถาม ไม่อธิบายเรื่องการแพทย์ที่ซับซ้อนกับเด็ก " +
                "หากเด็กเล่าอาการที่น่าเป็นห่วง ให้บอกเด็กอย่างอ่อนโยนให้ไปเรียกผู้ปกครองมาคุยทันที " +
                "ข้อความจะถูกอ่านออกเสียง จึงห้ามใช้ markdown สัญลักษณ์พิเศษ หรือ emoji\n\n"
    }
}
