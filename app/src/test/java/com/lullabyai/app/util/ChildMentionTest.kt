package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildMentionTest {

    private val giga = Child(id = "1", name = "กีก้า")
    private val fifa = Child(id = "2", name = "ฟีฟ่า")
    private val family = listOf(giga, fifa)

    @Test
    fun `naming one child picks that child not the tab selection`() {
        val picked = ChildMention.resolve("กีก้ากินยังไง", family, fallback = fifa)
        assertTrue(picked?.id == giga.id)
    }

    @Test
    fun `family preamble names everyone and rejects the Child Info tab as the subject`() {
        val text = ChildMention.familyPreamble(family, selected = fifa, isThai = true)
        assertTrue(text.contains("กีก้า"))
        assertTrue(text.contains("ฟีฟ่า"))
        assertTrue(text.contains("แท็บข้อมูลลูก"))
        assertTrue(text.contains("พิจารณาลูกทุกคน"))
        assertFalse(text.isBlank())
    }

    @Test
    fun `one child needs no family preamble`() {
        assertTrue(ChildMention.familyPreamble(listOf(giga), giga, isThai = true).isEmpty())
    }
}
