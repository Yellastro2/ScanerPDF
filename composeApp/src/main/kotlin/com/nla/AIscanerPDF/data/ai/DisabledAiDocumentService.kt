package com.nla.AIscanerPDF.data.ai

import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData

/** Бросается, когда backend-прокси не сконфигурирован (AI_BASE_URL пуст). */
class AiNotConfiguredException : Exception()

/**
 * Реализация для сборок без настроенного backend-прокси: AI-функции
 * отключены с понятным сообщением. Фиктивные даты, суммы и реквизиты
 * пользователю не показываются.
 */
class DisabledAiDocumentService : AiDocumentService {
    override suspend fun summarize(text: String, language: String): AiSummary =
        throw AiNotConfiguredException()

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData =
        throw AiNotConfiguredException()

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis =
        throw AiNotConfiguredException()
}
