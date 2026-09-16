package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerLabelTest {

    @Test
    fun `spark and local box are NongRak nano 1`() {
        assertEquals("NongRak nano 1", answerLabel("Spark (safety_in → key_points → answer)"))
        assertEquals("NongRak nano 1", answerLabel("Spark (daily limit)"))
        assertEquals("NongRak nano 1", answerLabel("Local (typhoon-2)"))
    }

    @Test
    fun `cloud providers name their model`() {
        assertEquals("NongRak nano - Gemini", answerLabel("Gemini 2"))
        assertEquals("NongRak nano - Claude", answerLabel("Claude"))
        assertEquals("NongRak nano - MedGemma", answerLabel("medgemma"))
        assertEquals("NongRak nano - llama-3.3-70b-instruct", answerLabel("OpenRouter 1 (llama-3.3-70b-instruct)"))
        assertEquals("NongRak nano - OpenRouter", answerLabel("OpenRouter 3"))
    }
}
