package com.lullabyai.app.util

import com.lullabyai.app.data.model.VaccineRecord

/**
 * Utility for matching and detecting duplicates or similar pediatric vaccines
 * between user-typed vaccine names and the child's existing vaccine records.
 */
object VaccineSimilarityMatcher {

    data class MatchResult(
        val matchedRecord: VaccineRecord,
        val similarityScore: Float, // 0.0 to 1.0
        val matchReason: String
    )

    /**
     * Known alias / synonym groups covering standard pediatric vaccines (Thai & English).
     */
    private val ALIAS_GROUPS: List<Set<String>> = listOf(
        // BCG / Tuberculosis
        setOf("bcg", "บีซีจี", "วัณโรค", "tuberculosis", "วัคซีนบีซีจี"),
        // Hepatitis B
        setOf("hbv", "hepb", "ตับอักเสบบี", "ไวรัสตับอักเสบบี", "hepatitis b", "ตับบี"),
        // DTP / DTaP / Tdap / Hexavalent
        setOf("dtp", "dtap", "tdap", "คอตีบ", "บาดทะยัก", "ไอกรน", "dtp-hb-hib", "dtphbhib", "คอตีบ-บาดทะยัก-ไอกรน", "คอตีบ-บาดทะยัก-ไอกรน-ตับอักเสบบี-ฮิบ"),
        // Polio (OPV / IPV)
        setOf("opv", "ipv", "polio", "โปลิโอ", "โปลิโอชนิดหยอด", "โปลิโอชนิดฉีด", "โปลิโอหยอด", "โปลิโอฉีด"),
        // Rotavirus
        setOf("rota", "rotavirus", "โรต้า", "ไวรัสโรต้า", "ท้องเสียโรต้า", "วัคซีนโรต้า"),
        // Influenza / Flu
        setOf("influenza", "flu", "ไข้หวัดใหญ่", "หวัดใหญ่", "ฟลู", "วัคซีนไข้หวัดใหญ่"),
        // MMR / Measles Mumps Rubella
        setOf("mmr", "หัด", "คางทูม", "หัดเยอรมัน", "measles", "mumps", "rubella", "หัด-คางทูม-หัดเยอรมัน", "วัคซีนหัด"),
        // Japanese Encephalitis (JE)
        setOf("je", "เจอี", "ไข้สมองอักเสบ", "ไข้สมองอักเสบเจอี", "japanese encephalitis", "สมองอักเสบ"),
        // Varicella / Chickenpox
        setOf("varicella", "chickenpox", "สุกใส", "อีสุกอีใส", "วัคซีนอีสุกอีใส"),
        // Hepatitis A
        setOf("hav", "hepa", "ตับอักเสบเอ", "ไวรัสตับอักเสบเอ", "hepatitis a", "ตับเอ"),
        // Pneumococcal (PCV / IPD)
        setOf("pcv", "ipd", "นิวโมคอคคัส", "ไอพีดี", "pneumococcal", "ปอดบวมไอพีดี", "วัคซีนไอพีดี"),
        // HPV
        setOf("hpv", "เอชพีวี", "มะเร็งปากมดลูก", "human papillomavirus", "วัคซีนมะเร็งปากมดลูก"),
        // Dengue
        setOf("dengue", "ไข้เลือดออก", "เดงกี่", "วัคซีนไข้เลือดออก"),
        // Meningococcal
        setOf("meningococcal", "ไข้กาฬหลังแอ่น", "menacwy", "menb")
    )

    /**
     * Clean and normalize string for comparison.
     */
    fun normalize(text: String): String {
        return text.trim().lowercase()
            .replace("วัคซีน", "")
            .replace("เข็มที่", "")
            .replace("เข็ม", "")
            .replace("dose", "")
            .replace("-", "")
            .replace("_", "")
            .replace(" ", "")
            .trim()
    }

    /**
     * Checks if [inputName] matches or is closely similar to any record in [records].
     * Returns the best [MatchResult] or null if no significant similarity found.
     */
    fun findSimilarVaccine(
        inputName: String,
        records: List<VaccineRecord>
    ): MatchResult? {
        val rawInput = inputName.trim()
        if (rawInput.length < 2) return null
        val normalizedInput = normalize(rawInput)
        val lowerInput = rawInput.lowercase()

        var bestMatch: MatchResult? = null

        for (rec in records) {
            val rawTarget = rec.vaccineName.trim()
            val lowerTarget = rawTarget.lowercase()
            val normalizedTarget = normalize(rawTarget)

            // 1. Exact or normalized exact match
            if (lowerInput == lowerTarget || (normalizedInput.isNotEmpty() && normalizedInput == normalizedTarget)) {
                return MatchResult(
                    matchedRecord = rec,
                    similarityScore = 1.0f,
                    matchReason = "ชื่อตรงกันกับวัคซีนในสมุด (${rec.vaccineName})"
                )
            }

            // 2. Substring containment
            if (normalizedInput.isNotEmpty() && normalizedTarget.isNotEmpty()) {
                if (normalizedTarget.contains(normalizedInput) || normalizedInput.contains(normalizedTarget)) {
                    val result = MatchResult(
                        matchedRecord = rec,
                        similarityScore = 0.9f,
                        matchReason = "ชื่อตรงกันหรือเป็นส่วนหนึ่งของ \"${rec.vaccineName}\""
                    )
                    if (bestMatch == null || result.similarityScore > bestMatch.similarityScore) {
                        bestMatch = result
                    }
                }
            }

            // 3. Alias / Synonym group match
            for (group in ALIAS_GROUPS) {
                val inputInGroup = group.any { alias ->
                    val normAlias = normalize(alias)
                    lowerInput.contains(alias) || (normAlias.isNotEmpty() && normalizedInput.contains(normAlias))
                }
                if (inputInGroup) {
                    val targetInGroup = group.any { alias ->
                        val normAlias = normalize(alias)
                        lowerTarget.contains(alias) || (normAlias.isNotEmpty() && normalizedTarget.contains(normAlias))
                    }
                    if (targetInGroup) {
                        val result = MatchResult(
                            matchedRecord = rec,
                            similarityScore = 0.85f,
                            matchReason = "ตรวจพบว่าเป็นวัคซีนกลุ่มเดียวกับ \"${rec.vaccineName}\""
                        )
                        if (bestMatch == null || result.similarityScore > bestMatch.similarityScore) {
                            bestMatch = result
                        }
                    }
                }
            }

            // 4. Levenshtein / Edit distance similarity for typos
            if (normalizedInput.length >= 3 && normalizedTarget.length >= 3) {
                val score = calculateSimilarity(normalizedInput, normalizedTarget)
                if (score >= 0.7f) {
                    val result = MatchResult(
                        matchedRecord = rec,
                        similarityScore = score,
                        matchReason = "ชื่อมีความคล้ายคลึงกับ \"${rec.vaccineName}\""
                    )
                    if (bestMatch == null || result.similarityScore > bestMatch.similarityScore) {
                        bestMatch = result
                    }
                }
            }
        }

        return bestMatch
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        if (longer.isEmpty()) return 1.0f
        val distance = editDistance(longer, shorter)
        return (longer.length - distance).toFloat() / longer.length.toFloat()
    }

    private fun editDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(minOf(newValue, lastValue), costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
    }
}
