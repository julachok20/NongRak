package com.lullabyai.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unwrapping a JSON envelope the model was never asked for.
 *
 * Four weekly-health-report notifications reached real parents whose body began
 * `{ "report": "คุณพ่อคุณแม่ดูแลน้อง...` because the model answered in JSON although jsonMode was
 * false. These pin the fix, including the cases where it must do nothing.
 */
class ClaudeClientProseTest {

    @Test
    fun `the shape that actually shipped is unwrapped`() {
        val raw = """{"report": "คุณพ่อคุณแม่ดูแลน้องได้อย่างดีมากเลยค่ะ"}"""
        assertEquals("คุณพ่อคุณแม่ดูแลน้องได้อย่างดีมากเลยค่ะ", ClaudeClient.unwrapProse(raw))
    }

    @Test
    fun `a fenced json block is unwrapped too`() {
        val raw = "```json\n{\"text\": \"น้องนอนได้ดีขึ้นค่ะ\"}\n```"
        assertEquals("น้องนอนได้ดีขึ้นค่ะ", ClaudeClient.unwrapProse(raw))
    }

    @Test
    fun `an object of sections keeps every section, not only the first`() {
        val raw = """{"summary": "ภาพรวมดีค่ะ", "advice": "ควรบันทึกการนอนเพิ่ม"}"""
        val out = ClaudeClient.unwrapProse(raw)
        assertTrue(out, out.contains("ภาพรวมดีค่ะ"))
        assertTrue(out, out.contains("ควรบันทึกการนอนเพิ่ม"))
    }

    @Test
    fun `a list of paragraphs is flattened`() {
        val raw = """{"report": [{"paragraph": "ย่อหน้าหนึ่ง"}, {"paragraph": "ย่อหน้าสอง"}]}"""
        val out = ClaudeClient.unwrapProse(raw)
        assertTrue(out, out.contains("ย่อหน้าหนึ่ง"))
        assertTrue(out, out.contains("ย่อหน้าสอง"))
    }

    @Test
    fun `ordinary prose is returned untouched`() {
        val prose = "น้องมีน้ำหนักตามเกณฑ์ค่ะ ควรให้นมตามปกติ"
        assertEquals(prose, ClaudeClient.unwrapProse(prose))
    }

    @Test
    fun `prose that merely mentions a brace is not mangled`() {
        // Starts with text, not an object, so it must not be parsed at all.
        val prose = "ใส่ชื่อในช่อง {name} แล้วกดบันทึก"
        assertEquals(prose, ClaudeClient.unwrapProse(prose))
    }

    @Test
    fun `broken json is left alone rather than emptied`() {
        // Showing the raw text is ugly. Showing nothing is a silent failure, which is worse.
        val broken = """{"report": "ไม่ปิดวงเล็บ"""
        assertEquals(broken, ClaudeClient.unwrapProse(broken))
    }

    @Test
    fun `an object with no readable string falls back to the raw text`() {
        val numbers = """{"weight": 7.2, "height": 68}"""
        assertEquals(numbers, ClaudeClient.unwrapProse(numbers))
    }
}
