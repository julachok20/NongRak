package com.lullabyai.app.data

import com.lullabyai.app.data.repository.BirthCertificateAiService
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A birth certificate prints a legal name with a title on the front of it. The nickname suggested
 * from it must be the child's given name, not the title — offering "เด็กชาย" as a nickname would be
 * worse than offering nothing.
 */
class GivenNameTest {

    @Test
    fun `strips Thai titles and keeps the given name`() {
        assertEquals("สมชาย", BirthCertificateAiService.givenName("เด็กชาย สมชาย ใจดี"))
        assertEquals("มาลี", BirthCertificateAiService.givenName("เด็กหญิง มาลี รักดี"))
        assertEquals("ก้อง", BirthCertificateAiService.givenName("ด.ช. ก้อง สุขใจ"))
    }

    @Test
    fun `strips English titles`() {
        assertEquals("Somchai", BirthCertificateAiService.givenName("Master Somchai Jaidee"))
        assertEquals("Malee", BirthCertificateAiService.givenName("Miss Malee Rakdee"))
    }

    @Test
    fun `handles a name with no title`() {
        assertEquals("สมชาย", BirthCertificateAiService.givenName("สมชาย ใจดี"))
        assertEquals("Somchai", BirthCertificateAiService.givenName("Somchai"))
    }

    @Test
    fun `never returns blank, because an empty nickname field helps nobody`() {
        assertEquals("เด็กชาย", BirthCertificateAiService.givenName("เด็กชาย"))
    }
}
