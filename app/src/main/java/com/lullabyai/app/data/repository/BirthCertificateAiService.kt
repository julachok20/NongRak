package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.ClaudeClient
import kotlinx.serialization.json.*

/**
 * Vision-AI extraction of a child's identity fields from a photo of a birth certificate, used by
 * the "Upload Birth Certificate" add-child path. Same JSON-mode + imageBase64 pattern as
 * [NutritionAiService.estimateFoodFromPhoto] — reuses the shared ai-generate vision endpoint.
 *
 * This is OCR/vision guesswork on a legal document, so callers MUST treat the result as a
 * pre-fill only: land the parent on the normal editable onboarding form to review/correct before
 * anything is saved. Never create a child record directly from this output.
 */
object BirthCertificateAiService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class ExtractedInfo(val name: String, val birthdateIso: String?, val gender: String)

    private fun extractJson(raw: String): String {
        val s = raw.trim()
        val start = s.indexOfFirst { it == '{' || it == '[' }
        val end = s.indexOfLast { it == '}' || it == ']' }
        return if (start in 0 until end) s.substring(start, end + 1) else s
    }

    /** Returns null if no legible birth certificate / child name could be found in the photo.
     *  No [lang] param — extracted values are literal document text, a fixed date format, and a
     *  fixed gender enum, none of which need localization. */
    suspend fun extractChildInfo(imageBase64: String, imageMimeType: String): ExtractedInfo? {
        val sys = "You read Thai and English official birth certificates. " +
            "Reply with ONLY valid minified JSON for the requested schema — no markdown, no code fences, no commentary."
        val prompt = "This photo shows a child's birth certificate (Thai สูติบัตร or an English-language equivalent). " +
            "Extract the CHILD's (not the parents') full name, date of birth, and sex. " +
            "Thai certificates print the year in the Buddhist Era (พ.ศ.) — if the year looks like Buddhist Era " +
            "(e.g. > 2400), convert it to the Gregorian year by subtracting 543 before returning it. " +
            "Return birthdate as ISO 8601 \"YYYY-MM-DD\" in the Gregorian calendar, or null if unreadable. " +
            "gender must be exactly one of: \"male\", \"female\", \"unspecified\". " +
            "If this does not look like a birth certificate or no child name is legible, return {\"name\":null}. " +
            "Return JSON exactly: {\"name\":\"...\",\"birthdate\":\"YYYY-MM-DD\",\"gender\":\"...\"}"
        val raw = ClaudeClient.generateContent(
            prompt, useSearch = false, maxTokens = 300, system = sys,
            imageBase64 = imageBase64, imageMimeType = imageMimeType
        )
        val obj = runCatching { json.parseToJsonElement(extractJson(raw)) as? JsonObject }.getOrNull() ?: return null
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val birthdate = (obj["birthdate"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf { Regex("""\d{4}-\d{2}-\d{2}""").matches(it) }
        val gender = (obj["gender"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            ?.takeIf { it in setOf("male", "female", "unspecified") } ?: "unspecified"
        return ExtractedInfo(name, birthdate, gender)
    }
}
