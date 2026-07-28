package com.nla.AIscanerPDF.domain.repository

import com.nla.AIscanerPDF.domain.model.AppSettings
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.OcrProgress
import com.nla.AIscanerPDF.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.AutoRenewCancellationResult
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.DocumentPage
import com.nla.AIscanerPDF.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData
import com.nla.AIscanerPDF.domain.model.ExportedFile
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.model.PurchaseResult
import com.nla.AIscanerPDF.domain.model.RestoreResult
import com.nla.AIscanerPDF.domain.model.SubscriptionProduct
import com.nla.AIscanerPDF.domain.model.SubscriptionStatus

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(documentId: String): Flow<DocumentWithPages?>
    suspend fun getDocument(documentId: String): DocumentWithPages?
    suspend fun createDocument(name: String): Document
    suspend fun renameDocument(documentId: String, newName: String)
    suspend fun deleteDocument(documentId: String)
    suspend fun deleteAllDocuments()

    suspend fun addPage(documentId: String, originalPath: String, initialCrop: CropCorners?): DocumentPage
    suspend fun getPage(pageId: String): DocumentPage?
    suspend fun updatePage(page: DocumentPage)
    suspend fun deletePage(pageId: String)
    suspend fun reorderPages(documentId: String, orderedPageIds: List<String>)

    /** Директория для сохранения нового оригинала страницы. */
    suspend fun newOriginalFilePath(documentId: String): String
}

interface ImageProcessingRepository {
    suspend fun detectCorners(imagePath: String): AppResult<CornerDetectionResult>

    /** Рендер страницы по её неразрушающим параметрам в processed/preview файлы. */
    suspend fun renderPage(page: DocumentPage): AppResult<DocumentPage>
}

interface OcrRepository {
    val isEngineAvailable: Boolean
    fun recognizeDocument(documentId: String, language: String): Flow<AppResult<OcrProgress>>
}

interface AiRepository {
    suspend fun summarize(documentId: String, text: String, language: String): AppResult<AiSummary>
    suspend fun extractData(documentId: String, text: String, language: String): AppResult<ExtractedDocumentData>
    suspend fun analyzeContract(documentId: String, text: String, language: String): AppResult<ContractAnalysis>
}

interface ExportRepository {
    suspend fun exportToPdf(documentId: String, options: PdfExportOptions): AppResult<ExportedFile>
    suspend fun exportPagesToJpg(documentId: String): AppResult<List<ExportedFile>>
    suspend fun exportTextToTxt(documentId: String, text: String): AppResult<ExportedFile>
    suspend fun shareFile(file: ExportedFile): AppResult<Unit>
}

interface SubscriptionRepository {
    val subscriptionStatus: Flow<SubscriptionStatus>
    suspend fun loadProducts(): List<SubscriptionProduct>
    suspend fun purchase(productId: String): PurchaseResult
    suspend fun restorePurchases(): RestoreResult
    suspend fun cancelAutoRenew(): AutoRenewCancellationResult
    suspend fun refreshSubscriptionStatus()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoDetectCorners(enabled: Boolean)
    suspend fun setAiConsentGiven(given: Boolean)
    suspend fun incrementOcrOperations()
    suspend fun incrementAiOperations()
}

/** Импорт изображения из галереи (SAF/Photo Picker) во внутреннее хранилище. */
interface ImageImporter {
    /** Копирует изображение по URI в директорию документа, возвращает путь к файлу. */
    suspend fun importImage(documentId: String, uriString: String): String
}
