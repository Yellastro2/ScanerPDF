package com.nla.AIscanerPDF.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.domain.logic.FreePlanLimiter
import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData
import com.nla.AIscanerPDF.domain.model.OcrProgress
import com.nla.AIscanerPDF.domain.repository.AiRepository
import com.nla.AIscanerPDF.domain.repository.OcrRepository
import com.nla.AIscanerPDF.domain.repository.SettingsRepository
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository

class RecognizeDocumentTextUseCase(
    private val ocr: OcrRepository,
    private val settings: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val limiter: FreePlanLimiter,
) {
    operator fun invoke(documentId: String, language: String): Flow<AppResult<OcrProgress>> = flow {
        val status = subscriptions.subscriptionStatus.first()
        val used = settings.settings.first().usedOcrOperations
        if (!limiter.canRunOcr(status, used)) {
            emit(AppResult.Failure(AppError.FreeLimitReached(used)))
            return@flow
        }
        ocr.recognizeDocument(documentId, language).collect { result ->
            if (result is AppResult.Success && result.value is OcrProgress.Completed) {
                settings.incrementOcrOperations()
            }
            emit(result)
        }
    }
}

/**
 * Базовая проверка перед AI-запросом: согласие пользователя (п. 10 ТЗ)
 * и лимит бесплатных операций (п. 11 ТЗ).
 */
class AiGate(
    private val settings: SettingsRepository,
    private val subscriptions: SubscriptionRepository,
    private val limiter: FreePlanLimiter,
) {
    suspend fun check(): AppError? {
        val current = settings.settings.first()
        if (!current.aiConsentGiven) return AppError.AiConsentRequired
        val status = subscriptions.subscriptionStatus.first()
        if (!limiter.canRunAi(status, current.usedAiOperations)) {
            return AppError.FreeLimitReached(current.usedAiOperations)
        }
        return null
    }

    suspend fun onCompleted() = settings.incrementAiOperations()
}

class SummarizeDocumentUseCase(private val ai: AiRepository, private val gate: AiGate) {
    suspend operator fun invoke(documentId: String, text: String, language: String): AppResult<AiSummary> {
        gate.check()?.let { return AppResult.Failure(it) }
        return ai.summarize(documentId, text, language).also {
            if (it is AppResult.Success) gate.onCompleted()
        }
    }
}

class ExtractDocumentDataUseCase(private val ai: AiRepository, private val gate: AiGate) {
    suspend operator fun invoke(documentId: String, text: String, language: String): AppResult<ExtractedDocumentData> {
        gate.check()?.let { return AppResult.Failure(it) }
        return ai.extractData(documentId, text, language).also {
            if (it is AppResult.Success) gate.onCompleted()
        }
    }
}

class AnalyzeContractUseCase(private val ai: AiRepository, private val gate: AiGate) {
    suspend operator fun invoke(documentId: String, text: String, language: String): AppResult<ContractAnalysis> {
        gate.check()?.let { return AppResult.Failure(it) }
        return ai.analyzeContract(documentId, text, language).also {
            if (it is AppResult.Success) gate.onCompleted()
        }
    }
}
