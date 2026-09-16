package com.lullabyai.app.util

/**
 * The name shown under an AI answer, from ClaudeClient.lastProvider.
 *
 * Our own hardware (the Spark pipeline, or ai-generate's LOCAL_AI_URL box) is "NongRak nano - Local";
 * everything else names the cloud model that actually wrote the answer.
 */
fun answerLabel(provider: String): String {
    val p = provider.trim()
    if (p.startsWith("Spark", ignoreCase = true) || p.startsWith("Local", ignoreCase = true)) return "NongRak nano 1"
    val model = when {
        p.startsWith("Gemini", ignoreCase = true) -> "Gemini"
        p.startsWith("Claude", ignoreCase = true) -> "Claude"
        p.startsWith("medgemma", ignoreCase = true) -> "MedGemma"
        // "OpenRouter 2 (llama-3.3-70b-instruct)" -> the model in brackets
        p.startsWith("OpenRouter", ignoreCase = true) ->
            p.substringAfter('(', "").substringBefore(')').ifBlank { "OpenRouter" }
        else -> p.ifBlank { "AI" }
    }
    return "NongRak nano - $model"
}
