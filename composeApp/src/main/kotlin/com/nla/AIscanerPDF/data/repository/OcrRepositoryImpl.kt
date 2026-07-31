package com.nla.AIscanerPDF.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.data.ocr.OcrEngine
import com.nla.AIscanerPDF.domain.model.OcrProgress
import com.nla.AIscanerPDF.domain.model.RecognizedPage
import com.nla.AIscanerPDF.domain.model.RecognizedText
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.OcrRepository

/**
 * Постраничное распознавание с прогрессом и поддержкой отмены
 * (отмена — через отмену коллектора Flow, п. 5.6 ТЗ).
 */
class OcrRepositoryImpl(
    private val engine: OcrEngine,
    private val documents: DocumentRepository,
    private val dispatchers: DispatchersProvider,
) : OcrRepository {

    override val isEngineAvailable: Boolean get() = engine.isAvailable

    override fun recognizeDocument(
        documentId: String,
        language: String,
        onlyUnrecognizedPages: Boolean,
    ): Flow<AppResult<OcrProgress>> =
        flow {
            if (!engine.isAvailable) {
                emit(AppResult.Failure(AppError.OcrEngineUnavailable))
                return@flow
            }
            val doc = documents.getDocument(documentId)
            if (doc == null) {
                emit(AppResult.Failure(AppError.DocumentNotFound))
                return@flow
            }
            val allPages = doc.pages.sortedBy { it.position }
            val pagesToRecognize = if (onlyUnrecognizedPages) {
                allPages.filter { it.recognizedText == null }
            } else {
                allPages
            }
            val textByPageId = allPages.associate { it.id to it.recognizedText }.toMutableMap()

            pagesToRecognize.forEachIndexed { index, page ->
                emit(AppResult.Success(OcrProgress.PageInProgress(index + 1, pagesToRecognize.size)))
                val imagePath = page.processedPath ?: page.originalPath
                val text = try {
                    engine.recognize(imagePath, language)
                } catch (e: Exception) {
                    emit(AppResult.Failure(AppError.OcrEmpty))
                    return@flow
                }
                documents.updatePage(page.copy(recognizedText = text))
                textByPageId[page.id] = text
            }

            val recognized = allPages.mapIndexed { index, page ->
                RecognizedPage(page.id, index + 1, textByPageId[page.id].orEmpty())
            }
            if (recognized.all { it.text.isBlank() }) {
                emit(AppResult.Failure(AppError.OcrEmpty))
            } else {
                emit(AppResult.Success(OcrProgress.Completed(RecognizedText(documentId, recognized))))
            }
        }.flowOn(dispatchers.default)
}
