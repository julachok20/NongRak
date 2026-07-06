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

    /** Which provider answered the most recent request (for the in-app indicator). */
    @Volatile
    var lastProvider: String = ""
        private set

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
        imageMimeType: String? = null
    ): String {
        val errors = mutableListOf<String>()
        val result = AiProxyClient.generate(prompt, useSearch, maxTokens, errors, system, jsonMode, imageBase64, imageMimeType)
        if (result.text != null) {
            lastProvider = result.provider
            android.util.Log.i("ClaudeClient", "answered by ${result.provider.ifBlank { "fallback" }}")
            return result.text
        }
        lastProvider = ""
        android.util.Log.w("ClaudeClient", "AI proxy unreachable: ${errors.joinToString(" | ")}")
        return "ขออภัยค่ะ ระบบผู้ช่วย AI ไม่พร้อมให้บริการในขณะนี้ กรุณาลองใหม่อีกครั้งในอีกสักครู่"
    }
}
