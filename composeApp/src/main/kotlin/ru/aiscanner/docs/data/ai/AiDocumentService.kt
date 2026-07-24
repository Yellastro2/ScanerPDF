package ru.aiscanner.docs.data.ai

import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

/**
 * Единый интерфейс AI-сервиса (п. 9 ТЗ). Провайдер не зашит жёстко:
 * реализация — Ktor-клиент к нашему backend-прокси; API-ключи
 * провайдера в APK не хранятся.
 */
interface AiDocumentService {
    suspend fun summarize(text: String, language: String): AiSummary
    suspend fun extractData(text: String, language: String): ExtractedDocumentData
    suspend fun analyzeContract(text: String, language: String): ContractAnalysis
}

/** Текст, превышающий лимит, отклоняется до отправки (п. 9 ТЗ). */
class DocumentTooLargeException : Exception()

/** Серверный токен отсутствует, истек или подписка больше не активна. */
class AiAccessDeniedException : Exception()

/** Backend принял запрос, но не смог выполнить AI-анализ. */
class AiBackendException : Exception()

const val AI_MAX_TEXT_CHARS: Int = 60_000
