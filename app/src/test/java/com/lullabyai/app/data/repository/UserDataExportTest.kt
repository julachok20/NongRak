package com.lullabyai.app.data.repository

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserDataExportTest {

    private val export = UserDataExport(
        listOf(
            UserDataExport.Section(
                "children", "ข้อมูลบุตรหลาน",
                listOf(buildJsonObject { put("id", JsonPrimitive("c1")); put("name", JsonPrimitive("น้องกิก้า")) }),
                null
            ),
            UserDataExport.Section("allergies", "ประวัติการแพ้", emptyList(), null),
            UserDataExport.Section("chat_messages", "ประวัติการสนทนากับ AI", emptyList(), "permission denied")
        )
    )

    @Test
    fun `report labels columns, hides ids, and names a table that failed`() {
        val report = export.toReport("2026-09-16")
        assertTrue(report.contains("• ชื่อ: น้องกิก้า"))
        assertFalse(report.contains("c1"))
        assertTrue(report.contains("(ไม่มีข้อมูล)"))
        assertTrue(report.contains("โหลดข้อมูลส่วนนี้ไม่สำเร็จ: permission denied"))
    }

    @Test
    fun `json keeps raw rows including ids and lists errors`() {
        val json = export.toJson("2026-09-16")
        assertTrue(json.contains("\"id\": \"c1\""))
        assertTrue(json.contains("\"errors\""))
        assertTrue(json.contains("\"chat_messages\": \"permission denied\""))
    }
}
