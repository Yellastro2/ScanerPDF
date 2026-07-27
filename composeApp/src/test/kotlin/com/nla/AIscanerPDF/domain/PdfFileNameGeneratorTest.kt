package com.nla.AIscanerPDF.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.domain.logic.PdfFileNameGenerator

class PdfFileNameGeneratorTest {

    private val timestamp = 1735689600000L // 2025-01-01 UTC

    @Test
    fun `generates name with date and extension`() {
        val name = PdfFileNameGenerator.generate("Договор аренды", timestamp)
        assertTrue(name.startsWith("Договор аренды_"))
        assertTrue(name.endsWith(".pdf"))
    }

    @Test
    fun `replaces forbidden filesystem characters`() {
        val sanitized = PdfFileNameGenerator.sanitize("a/b\\c:d*e?f\"g<h>i|j")
        assertEquals("a_b_c_d_e_f_g_h_i_j", sanitized)
    }

    @Test
    fun `blank name falls back to default`() {
        assertEquals("Документ", PdfFileNameGenerator.sanitize("   "))
        assertEquals("Документ", PdfFileNameGenerator.sanitize(null))
        assertEquals("Документ", PdfFileNameGenerator.sanitize("..."))
    }

    @Test
    fun `long names are truncated`() {
        val longName = "а".repeat(200)
        assertTrue(PdfFileNameGenerator.sanitize(longName).length <= 60)
    }
}
