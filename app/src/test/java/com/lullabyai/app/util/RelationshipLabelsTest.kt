package com.lullabyai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelationshipLabelsTest {

    @Test
    fun `chat welcome is a short hello with the parent name`() {
        assertEquals("สวัสดีค่ะ คุณจุฬจ๊อก", RelationshipLabels.chatWelcome("mother", true, "จุฬจ๊อก"))
        assertEquals("Hello, Julachok", RelationshipLabels.chatWelcome("mother", false, "Julachok"))
    }

    @Test
    fun `chat welcome does not double คุณ when the name already has it`() {
        assertEquals("สวัสดีค่ะ คุณจุฬจ๊อก", RelationshipLabels.chatWelcome("mother", true, "คุณจุฬจ๊อก"))
    }

    @Test
    fun `chat welcome falls back to the relationship term`() {
        assertEquals("สวัสดีค่ะ คุณแม่", RelationshipLabels.chatWelcome("mother", true, null))
        assertEquals("Hello, Mom", RelationshipLabels.chatWelcome("mother", false, null))
    }

    @Test
    fun `chat system prompt switches identity and tone based on mascotKey`() {
        val nongrakTH = RelationshipLabels.chatSystemPrompt("mother", isThai = true, mascotKey = "nongrak")
        org.junit.Assert.assertTrue(nongrakTH.contains("คุณคือ \"น้องรัก\" (NongRak)"))
        org.junit.Assert.assertTrue(nongrakTH.contains("อบอุ่น"))

        val cocoTH = RelationshipLabels.chatSystemPrompt("father", isThai = true, mascotKey = "coco")
        org.junit.Assert.assertTrue(cocoTH.contains("คุณคือ \"โคโค่\" (Coco)"))
        org.junit.Assert.assertTrue(cocoTH.contains("ร่าเริง สดใส มีพลังบวกสูง"))

        val bunnyTH = RelationshipLabels.chatSystemPrompt("mother", isThai = true, mascotKey = "bunny")
        org.junit.Assert.assertTrue(bunnyTH.contains("คุณคือ \"บันนี่\" (Bunny)"))
        org.junit.Assert.assertTrue(bunnyTH.contains("สุขอนามัย ความสะอาด ปลอดภัย ยา วัคซีน"))

        val pinguTH = RelationshipLabels.chatSystemPrompt("guardian", isThai = true, mascotKey = "pingu")
        org.junit.Assert.assertTrue(pinguTH.contains("คุณคือ \"พิงกุ\" (Pingu)"))
        org.junit.Assert.assertTrue(pinguTH.contains("ฉลาด ช่างสังเกต"))

        val cocoEN = RelationshipLabels.chatSystemPrompt("father", isThai = false, mascotKey = "coco")
        org.junit.Assert.assertTrue(cocoEN.contains("You are \"Coco\""))
        org.junit.Assert.assertTrue(cocoEN.contains("golden retriever puppy"))

        val bunnyEN = RelationshipLabels.chatSystemPrompt("mother", isThai = false, mascotKey = "bunny")
        org.junit.Assert.assertTrue(bunnyEN.contains("You are \"Bunny\""))
        org.junit.Assert.assertTrue(bunnyEN.contains("nurse bunny"))

        val pinguEN = RelationshipLabels.chatSystemPrompt("mother", isThai = false, mascotKey = "pingu")
        org.junit.Assert.assertTrue(pinguEN.contains("You are \"Pingu\""))
        org.junit.Assert.assertTrue(pinguEN.contains("scientist penguin"))
    }

    @Test
    fun `chat system prompt includes multi-child need info guardrails`() {
        val promptTH = RelationshipLabels.chatSystemPrompt("mother", isThai = true, mascotKey = "nongrak")
        org.junit.Assert.assertTrue(promptTH.contains("กฎเหล็กกรณีครอบครัวมีลูกหลายคน"))
        org.junit.Assert.assertTrue(promptTH.contains("ห้ามข้ามไปถามข้อมูลของเด็กคนอื่นในครอบครัวโดยเด็ดขาด"))

        val promptEN = RelationshipLabels.chatSystemPrompt("mother", isThai = false, mascotKey = "nongrak")
        org.junit.Assert.assertTrue(promptEN.contains("Strict rule for multi-child households"))
        org.junit.Assert.assertTrue(promptEN.contains("NEVER jump to asking about another child in the family"))
    }
}
