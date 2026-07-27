package com.nla.AIscanerPDF.data.ai

import kotlinx.coroutines.delay
import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.ContractClause
import com.nla.AIscanerPDF.domain.model.DetectedAmount
import com.nla.AIscanerPDF.domain.model.DetectedDate
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData
import com.nla.AIscanerPDF.domain.model.ExtractedField

/** Mock-реализация для разработки (п. 9 ТЗ): без сети, детерминированные ответы. */
class MockAiDocumentService : AiDocumentService {

    override suspend fun summarize(text: String, language: String): AiSummary {
        requireSize(text)
        delay(700)
        return AiSummary(
            documentType = "Документ (демо)",
            shortSummary = "Это демонстрационный ответ AI. Реальный анализ будет доступен после подключения backend-прокси.",
            keyPoints = listOf(
                "Текст успешно передан на анализ",
                "Длина текста: ${text.length} символов",
            ),
            dates = listOf(DetectedDate("01.01.2026", "Пример найденной даты")),
            amounts = listOf(DetectedAmount("10 000", "RUB", "Пример найденной суммы")),
            requiredActions = listOf("Проверьте документ вручную"),
        )
    }

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData {
        requireSize(text)
        delay(700)
        return ExtractedDocumentData(
            fields = listOf(
                ExtractedField("Тип анализа", "Демонстрационный", page = 1, sourceFragment = null, confidence = 1f),
            ),
        )
    }

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis {
        requireSize(text)
        delay(700)
        return ContractAnalysis(
            importantTerms = listOf(
                ContractClause("Демо-режим", "Реальный анализ договора появится после подключения backend.", null),
            ),
            risks = emptyList(),
            deadlines = emptyList(),
            moneyTerms = emptyList(),
            questionsToClarify = listOf("Подключить боевой AI-сервис"),
        )
    }

    private fun requireSize(text: String) {
        if (text.length > AI_MAX_TEXT_CHARS) throw DocumentTooLargeException()
    }
}
