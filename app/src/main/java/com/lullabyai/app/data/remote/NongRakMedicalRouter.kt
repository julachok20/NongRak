package com.lullabyai.app.data.remote

/**
 * Routes medical questions to MedGemma, and stamps every MedGemma answer with a warning.
 *
 * Why a separate router rather than another branch inside [ClaudeClient]: the general chat chain
 * answers parenting questions, MedGemma answers clinical ones, and those two need *different*
 * disclaimers. An answer about nap routines does not need "this is not a diagnosis" on top; an
 * answer about a rash with a fever absolutely does, and it must be impossible to lose.
 *
 * The standard warning is a banner in the chat, not answer text (product decision 2026-09-13). It
 * must still never vanish when a session is reopened - the case where a stale medical answer is
 * most dangerous - so the flag is persisted with the message (chat_messages.medical) rather than
 * held in a composable. The emergency warning stays in the text: it is an instruction, not a note.
 *
 * Deliberately conservative: when unsure it does NOT claim a question is medical, and when
 * MedGemma is unreachable it returns null so the caller falls back to the normal chain rather
 * than failing the parent's question outright.
 */
object NongRakMedicalRouter {

    /** True when MedGemma is reachable; without it every question takes the normal chain. */
    val isAvailable: Boolean get() = MedGemmaClient.isConfigured

    // -----------------------------------------------------------------------------------------
    // Intent detection
    // -----------------------------------------------------------------------------------------

    /**
     * Symptoms, conditions, medicines and body parts. Matched as substrings because Thai is
     * written without spaces, so word-boundary matching does not apply the way it does in English.
     */
    private val MEDICAL_TERMS_TH = listOf(
        // symptoms
        "ไข้", "ตัวร้อน", "ไอ", "จาม", "น้ำมูก", "คัดจมูก", "หายใจ", "หอบ",
        "อาเจียน", "อ้วก", "ท้องเสีย", "ถ่ายเหลว", "ท้องผูก", "ปวดท้อง", "ผื่น", "ผด",
        "คัน", "ลมพิษ", "บวม", "ชัก", "เกร็ง", "ซึม", "ตาแดง", "หูอื้อ",
        "เจ็บคอ", "ปวดหู", "แผล", "เลือดออก", "ฟกช้ำ", "หกล้ม", "สำลัก", "ตัวเหลือง",
        "ดีซ่าน", "ปัสสาวะ", "อุจจาระ", "น้ำหนักลด",
        // conditions
        "แพ้", "ภูมิแพ้", "หอบหืด", "ปอดบวม", "ไข้หวัด", "มือเท้าปาก", "อีสุกอีใส",
        "ไข้เลือดออก", "ท้องร่วง", "ลำไส้", "ติดเชื้อ", "อักเสบ", "โรค",
        // care and treatment
        "ยา", "ยาลดไข้", "พาราเซตามอล", "ไอบูโพรเฟน", "ยาปฏิชีวนะ", "ขนาดยา",
        "วัคซีน", "ฉีดยา", "หมอ", "แพทย์", "โรงพยาบาล", "คลินิก", "ฉุกเฉิน", "อาการ",
        "รักษา", "วินิจฉัย", "ตรวจเลือด"
    )

    private val MEDICAL_TERMS_EN = listOf(
        "fever", "temperature", "cough", "sneeze", "runny nose", "congestion", "breathing",
        "wheez", "vomit", "throw up", "diarrhea", "diarrhoea", "constipat", "stomach ache",
        "tummy ache", "rash", "hives", "itch", "swell", "seizure", "convulsion", "lethargic",
        "pink eye", "sore throat", "earache", "wound", "bleeding", "bruise",
        "choking", "jaundice", "dehydrat",
        "allergy", "allergic", "asthma", "pneumonia", "influenza", "hand foot and mouth",
        "chickenpox", "dengue", "infection", "inflamed", "disease", "illness", "sick",
        "medicine", "medication", "dose", "dosage", "paracetamol", "acetaminophen",
        "ibuprofen", "antibiotic", "vaccine", "doctor", "hospital", "clinic", "emergency",
        "symptom", "treatment", "diagnos"
    )

    /**
     * Signs that need care now, not advice. These get a stronger warning, and the reply leads with
     * "go now" instead of leading with information.
     */
    private val EMERGENCY_TERMS_TH = listOf(
        "ชัก", "เกร็ง", "หายใจไม่ออก", "หยุดหายใจ", "ตัวเขียว", "ปากเขียว", "ไม่รู้สึกตัว",
        "หมดสติ", "เรียกไม่ตื่น", "ซึมมาก", "สำลัก", "เลือดออกไม่หยุด", "กินยาผิด",
        "กลืนสิ่งแปลกปลอม", "ไข้สูงมาก", "คอแข็ง",
        // Added when the community opened symptom threads to an AI first pass: signs a parent is
        // likely to describe in full rather than name, and which must not wait on an answer.
        "หายใจเร็ว", "หอบ", "ซี่โครงบุ๋ม", "อกบุ๋ม", "กระหม่อมบุ๋ม", "กระหม่อมโป่ง",
        "ไม่ปัสสาวะ", "ไม่ฉี่", "ปัสสาวะน้อยมาก", "อาเจียนพุ่ง", "อาเจียนสีเขียว",
        "อาเจียนเป็นเลือด", "ถ่ายเป็นเลือด", "อุจจาระมีเลือด", "ผื่นจ้ำเลือด", "จุดเลือดออก",
        "ตัวลาย", "ไม่ยอมกินนม", "ดูดนมไม่ได้", "ตัวอ่อนปวกเปียก"
    )

    private val EMERGENCY_TERMS_EN = listOf(
        "seizure", "convulsion", "not breathing", "stopped breathing", "blue lips", "turning blue",
        "unconscious", "unresponsive", "won't wake", "wont wake", "choking",
        "bleeding heavily", "swallowed", "poison", "overdose", "stiff neck",
        "struggling to breathe", "chest indrawing", "grunting", "sunken fontanelle",
        "bulging fontanelle", "not urinating", "no wet nappy", "no wet diaper",
        "projectile vomiting", "green vomit", "vomiting blood", "blood in stool",
        "bloody stool", "petechiae", "non-blanching rash", "mottled", "floppy",
        "refusing to feed", "won't feed", "wont feed"
    )

    /**
     * Below this age a fever is a reason to be seen NOW, whatever the number on the thermometer.
     * A newborn with sepsis can present with nothing but a temperature, and the reassuring answer
     * is the dangerous one - it buys the delay. This is the single case the keyword lists above
     * cannot catch on their own, because it depends on the child, not on the words.
     */
    const val FEVER_ALWAYS_URGENT_UNDER_MONTHS = 3

    private val FEVER_TERMS = listOf("ไข้", "ตัวร้อน", "fever", "febrile", "temperature")

    /**
     * Whether [text] reads as a medical question rather than general parenting.
     *
     * Emergency terms count as medical BY CONSTRUCTION, not by being duplicated into the lists
     * above. Keeping two hand-maintained lists in step failed immediately in practice: "หมดสติ"
     * and "unconscious" were emergency-only, so an unconscious child read as non-medical, skipped
     * MedGemma, and lost the emergency warning - the single worst outcome this router can produce.
     * Deriving it here makes that gap unrepresentable instead of merely fixed once.
     */
    fun isMedicalQuestion(text: String): Boolean {
        val t = text.lowercase()
        return MEDICAL_TERMS_TH.any { it in text } ||
            MEDICAL_TERMS_EN.any { it in t } ||
            isEmergency(text)
    }

    /** Whether [text] describes something that should not wait on an AI answer at all. */
    fun isEmergency(text: String): Boolean {
        val t = text.lowercase()
        return EMERGENCY_TERMS_TH.any { it in text } || EMERGENCY_TERMS_EN.any { it in t }
    }

    /**
     * [isEmergency] plus the one judgement that needs to know whose child this is: a fever under
     * three months old.
     *
     * Used by community symptom threads, which know the child's age; the chat router does not and
     * keeps calling [isEmergency]. A null [ageMonths] means the age is unknown, and an unknown age
     * with a fever is treated as urgent - erring toward "go and be seen" is the only error here
     * that is cheap to be wrong about.
     *
     * ponytail: keyword matching, with the ceiling that implies. It catches the phrases listed and
     * misses everything phrased another way, so it may only ever ADD a warning, never remove one
     * and never decide that something is fine.
     */
    fun isEmergencyForAge(text: String, ageMonths: Int?): Boolean {
        if (isEmergency(text)) return true
        val mentionsFever = FEVER_TERMS.any { it in text } || FEVER_TERMS.any { it in text.lowercase() }
        return mentionsFever && (ageMonths == null || ageMonths < FEVER_ALWAYS_URGENT_UNDER_MONTHS)
    }

    /** Rough language guess: any Thai codepoint means answer in Thai. */
    private fun looksThai(text: String): Boolean = text.any { it.code in 0x0E00..0x0E7F }

    // -----------------------------------------------------------------------------------------
    // Warnings
    // -----------------------------------------------------------------------------------------

    fun standardWarning(isThai: Boolean): String = if (isThai) {
        """
        ⚠️ คำเตือน: ข้อมูลนี้เป็นคำแนะนำทั่วไปจาก AI ทางการแพทย์ (MedGemma)
        ไม่ใช่การวินิจฉัยโรค และไม่สามารถใช้แทนการตรวจโดยแพทย์ได้
        หากอาการไม่ดีขึ้น แย่ลง หรือคุณไม่แน่ใจ กรุณาพาลูกไปพบแพทย์
        """.trimIndent()
    } else {
        """
        ⚠️ Warning: This is general guidance from a medical AI (MedGemma).
        It is not a diagnosis and cannot replace examination by a doctor.
        If symptoms persist, worsen, or you are unsure, please see a doctor.
        """.trimIndent()
    }

    fun emergencyWarning(isThai: Boolean): String = if (isThai) {
        """
        🚨 อาการที่คุณอธิบายอาจเป็นภาวะฉุกเฉิน
        กรุณาพาลูกไปโรงพยาบาลทันที หรือโทร 1669 (การแพทย์ฉุกเฉิน)
        อย่ารอผลจาก AI ข้อความด้านล่างเป็นข้อมูลประกอบเท่านั้น ไม่ใช่การวินิจฉัย
        """.trimIndent()
    } else {
        """
        🚨 What you describe may be a medical emergency.
        Take your child to a hospital now, or call 1669 (Thai emergency medical services).
        Do not wait for an AI answer. The text below is background only, not a diagnosis.
        """.trimIndent()
    }

    // -----------------------------------------------------------------------------------------
    // Routing
    // -----------------------------------------------------------------------------------------

    data class MedicalAnswer(
        val text: String,
        val wasEmergency: Boolean,
        /** For the provider indicator, matching ClaudeClient.lastProvider. */
        val provider: String = "medgemma"
    )

    /**
     * Answers [userMessage] with MedGemma, warning first.
     *
     * Returns null when the question is not medical, MedGemma is unconfigured, or the call failed.
     * In every one of those cases the caller should use the normal chain: null never means "refuse
     * to answer" - a parent asking about a fever must still get a reply.
     */
    suspend fun tryAnswer(
        userMessage: String,
        contextPrompt: String = "",
        errors: MutableList<String> = mutableListOf()
    ): MedicalAnswer? {
        if (!isMedicalQuestion(userMessage)) return null
        if (!isAvailable) { errors += "MedicalRouter: MedGemma not configured"; return null }

        val isThai = looksThai(userMessage)
        val emergency = isEmergency(userMessage)

        val prompt = buildString {
            if (contextPrompt.isNotBlank()) { append(contextPrompt); append("\n\n") }
            append(userMessage)
        }

        val body = MedGemmaClient.tryGenerate(prompt, maxTokens = 1024, errors = errors)
            ?: return null

        // The "not a diagnosis" note is the chat's banner now (ChatRepository flags the message and
        // the flag is stored with it). "Go to hospital now" is not a disclaimer, so it stays on top
        // of the answer itself.
        return MedicalAnswer(
            text = if (emergency) "${emergencyWarning(isThai)}\n\n$body" else body,
            wasEmergency = emergency
        )
    }
}
