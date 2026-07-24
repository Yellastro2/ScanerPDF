package ru.aiscanner.docs.data.repository

import kotlinx.coroutines.CancellationException
import ru.aiscanner.docs.core.AppError
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.data.ai.AiDocumentService
import ru.aiscanner.docs.data.ai.AiAccessDeniedException
import ru.aiscanner.docs.data.ai.AiNotConfiguredException
import ru.aiscanner.docs.data.ai.DocumentTooLargeException
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData
import ru.aiscanner.docs.domain.repository.AiRepository
import java.io.IOException

/**
 * Ошибки AI приводятся к типизированным AppError. Текст документа
 * не логируется и не попадает в сообщения об ошибках (п. 10 ТЗ).
 */
class AiRepositoryImpl(private val service: AiDocumentService) : AiRepository {

    override suspend fun summarize(documentId: String, text: String, language: String): AppResult<AiSummary> =
        safeCall { service.summarize(text, language) }

    override suspend fun extractData(documentId: String, text: String, language: String): AppResult<ExtractedDocumentData> =
        safeCall { service.extractData(text, language) }

    override suspend fun analyzeContract(documentId: String, text: String, language: String): AppResult<ContractAnalysis> =
        safeCall { service.analyzeContract(text, language) }

    private inline fun <T> safeCall(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: AiNotConfiguredException) {
        AppResult.Failure(AppError.AiNotConfigured)
    } catch (e: DocumentTooLargeException) {
        AppResult.Failure(AppError.DocumentTooLarge)
    } catch (e: AiAccessDeniedException) {
        AppResult.Failure(AppError.AiAccessDenied)
    } catch (e: IOException) {
        AppResult.Failure(AppError.NoNetwork)
    } catch (e: Exception) {
        AppResult.Failure(AppError.AiUnavailable)
    }
}
