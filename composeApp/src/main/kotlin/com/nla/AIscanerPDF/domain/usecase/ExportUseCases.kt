package com.nla.AIscanerPDF.domain.usecase

import kotlinx.coroutines.flow.first
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.domain.logic.FreePlanLimiter
import com.nla.AIscanerPDF.domain.model.ExportedFile
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ExportRepository
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository

class ExportDocumentToPdfUseCase(
    private val documents: DocumentRepository,
    private val export: ExportRepository,
    private val subscriptions: SubscriptionRepository,
    private val limiter: FreePlanLimiter,
) {
    suspend operator fun invoke(documentId: String, options: PdfExportOptions): AppResult<ExportedFile> {
        val doc = documents.getDocument(documentId)
            ?: return AppResult.Failure(AppError.DocumentNotFound)
        val status = subscriptions.subscriptionStatus.first()
        if (!limiter.canExportPdf(status, doc.pages.size)) {
            return AppResult.Failure(AppError.FreeLimitReached(limiter.maxPagesPerPdf()))
        }
        return export.exportToPdf(documentId, options)
    }
}

class ExportPageToJpgUseCase(private val export: ExportRepository) {
    suspend operator fun invoke(documentId: String): AppResult<List<ExportedFile>> =
        export.exportPagesToJpg(documentId)
}

class ShareDocumentUseCase(private val export: ExportRepository) {
    suspend operator fun invoke(file: ExportedFile): AppResult<Unit> = export.shareFile(file)
}
