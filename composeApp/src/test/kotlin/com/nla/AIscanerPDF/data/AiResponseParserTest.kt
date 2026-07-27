package com.nla.AIscanerPDF.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.data.ai.AiResponseParser

class AiResponseParserTest {

    @Test
    fun `parses full summary json`() {
        val json = """
            {
              "documentType": "Договор",
              "shortSummary": "Договор аренды помещения",
              "keyPoints": ["Срок 11 месяцев", "Оплата ежемесячно"],
              "dates": [{"value": "01.02.2026", "description": "Начало аренды"}],
              "amounts": [{"value": "50000", "currency": "RUB", "description": "Ежемесячный платёж"}],
              "requiredActions": ["Подписать до 25.01.2026"]
            }
        """.trimIndent()

        val result = AiResponseParser.parseSummary(json)

        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals("Договор", summary.documentType)
        assertEquals(2, summary.keyPoints.size)
        assertEquals("RUB", summary.amounts.first().currency)
    }

    @Test
    fun `missing optional fields fall back to defaults`() {
        val json = """{"shortSummary": "Краткое содержание"}"""
        val result = AiResponseParser.parseSummary(json)
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals("Краткое содержание", summary.shortSummary)
        assertTrue(summary.keyPoints.isEmpty())
        assertTrue(summary.dates.isEmpty())
        assertEquals(null, summary.documentType)
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = """{"shortSummary": "Ок", "unknownField": 42}"""
        assertTrue(AiResponseParser.parseSummary(json).isSuccess)
    }

    @Test
    fun `garbage input returns failure not exception`() {
        assertTrue(AiResponseParser.parseSummary("не json").isFailure)
        assertTrue(AiResponseParser.parseExtraction("{broken").isFailure)
        assertTrue(AiResponseParser.parseContract("").isFailure)
    }

    @Test
    fun `parses contract analysis sections`() {
        val json = """
            {
              "importantTerms": [{"title": "Автопродление", "description": "Договор продлевается автоматически"}],
              "risks": [{"title": "Штраф", "description": "0.1% в день", "sourceFragment": "п. 5.2"}],
              "questionsToClarify": ["Уточнить порядок расторжения"]
            }
        """.trimIndent()

        val result = AiResponseParser.parseContract(json)

        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        assertEquals(1, analysis.importantTerms.size)
        assertEquals("п. 5.2", analysis.risks.first().sourceFragment)
        assertTrue(analysis.deadlines.isEmpty())
    }
}
