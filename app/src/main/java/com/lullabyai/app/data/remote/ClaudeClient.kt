package com.lullabyai.app.data.remote

/**
 * AI entry point for the app.
 *
 * As of 2026-06-13 the Gemini/Claude API keys live SERVER-SIDE in the Supabase Edge Function
 * "ai-generate" — they are no longer shipped in the APK. This object just forwards the request
 * to [AiProxyClient] and exposes which provider answered.
 *
 * (Object name kept as ClaudeClient so existing call-sites are unchanged.)
 *
 * NOTE: MedGemma is intentionally NOT in this chain. It is reserved for dedicated medical
 * features (e.g. medical-document analysis) and called directly via MedGemmaClient there.
 */
object ClaudeClient {

    /** The family box's general model, text and vision. Food logging asks for it by name. */
    const val GEMMA = "gemma-4"

    /** Which provider answered the most recent request (for the in-app indicator). */
    @Volatile
    var lastProvider: String = ""
        private set

    /**
     * For answers that did not come through [generateContent] (the Spark pipeline, a daily-limit
     * notice). Without it the indicator keeps showing the previous cloud model and misreports
     * who answered.
     */
    fun reportProvider(provider: String) {
        lastProvider = provider
    }

    /**
     * [maxTokens] controls response length:
     *  - Chat / task generation : 1024  (default — fast, cheap)
     *  - AI analysis screens    : 4096  (full detailed responses)
     */
    suspend fun generateContent(
        prompt: String,
        useSearch: Boolean = true,
        maxTokens: Int = 1024,
        system: String? = null,
        jsonMode: Boolean? = null,
        imageBase64: String? = null,
        imageMimeType: String? = null,
        /**
         * A model on the family's own box to answer this, by name (e.g. [GEMMA]). Ignored when the
         * box is unreachable — the cloud chain then answers exactly as it does for any other call.
         */
        model: String? = null
    ): String {
        val errors = mutableListOf<String>()
        val result = AiProxyClient.generate(prompt, useSearch, maxTokens, errors, system, jsonMode, imageBase64, imageMimeType, model)
        if (result.text != null) {
            lastProvider = result.provider
            android.util.Log.i("ClaudeClient", "answered by ${result.provider.ifBlank { "fallback" }}")
            // Asking for prose does not guarantee prose. Models wrap the answer in {"report": "..."}
            // often enough that four weekly-report notifications reached real parents reading
            // literally `{ "report": "คุณพ่อคุณแม่ดูแลน้อง...`. Unwrapped here, once, because every
            // prose generator in the app comes through this one function.
            return if (jsonMode == true) result.text else unwrapProse(result.text)
        }
        lastProvider = ""
        android.util.Log.w("ClaudeClient", "AI proxy unreachable: ${errors.joinToString(" | ")}")
        return "ขออภัยค่ะ ระบบผู้ช่วย AI ไม่พร้อมให้บริการในขณะนี้ กรุณาลองใหม่อีกครั้งในอีกสักครู่"
    }

    /**
     * Pulls the prose out of a JSON envelope the model was never asked for.
     *
     * Handles the shapes actually seen: a fenced ```json block, and an object whose values are the
     * answer - {"report": "..."}, {"text": "..."}, or one wrapping a list of paragraphs. Several
     * string values are joined, so an object of sections does not lose all but the first.
     *
     * Anything that is not a JSON object comes back untouched, which is the normal case. A reply
     * that IS an object but holds no readable string is also returned untouched rather than
     * emptied: showing the raw text is ugly, showing nothing is a silent failure.
     */
    internal fun unwrapProse(raw: String): String {
        val text = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (!text.startsWith("{")) return raw

        val parsed = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(text)
        }.getOrNull() as? kotlinx.serialization.json.JsonObject ?: return raw

        val pieces = mutableListOf<String>()
        fun collect(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is kotlinx.serialization.json.JsonPrimitive ->
                    if (element.isString) element.content.trim().takeIf { it.isNotEmpty() }?.let { pieces += it }
                is kotlinx.serialization.json.JsonArray -> element.forEach { collect(it) }
                is kotlinx.serialization.json.JsonObject -> element.values.forEach { collect(it) }
            }
        }
        parsed.values.forEach { collect(it) }

        return pieces.joinToString("\n\n").ifBlank { raw }
    }
}
