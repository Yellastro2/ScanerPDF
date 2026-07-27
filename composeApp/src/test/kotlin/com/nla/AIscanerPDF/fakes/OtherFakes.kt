package com.nla.AIscanerPDF.fakes

import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.model.CornerDetectionResult
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.DocumentPage
import ru.aiscanner.docs.domain.model.ExportedFile
import ru.aiscanner.docs.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.repository.ExportRepository
import com.nla.AIscanerPDF.domain.repository.ImageImporter
import com.nla.AIscanerPDF.domain.repository.ImageProcessingRepository

class FakeExportRepository : ExportRepository {
    val exportedDocuments = mutableListOf<String>()
    val sharedFiles = mutableListOf<ExportedFile>()

    override suspend fun exportToPdf(documentId: String, options: PdfExportOptions): AppResult<ExportedFile> {
        exportedDocuments += documentId
        return AppResult.Success(ExportedFile("/tmp/$documentId.pdf", "application/pdf"))
    }

    override suspend fun exportPagesToJpg(documentId: String): AppResult<List<ExportedFile>> =
        AppResult.Success(emptyList())

    override suspend fun exportTextToTxt(documentId: String, text: String): AppResult<ExportedFile> =
        AppResult.Success(ExportedFile("/tmp/$documentId.txt", "text/plain"))

    override suspend fun shareFile(file: ExportedFile): AppResult<Unit> {
        sharedFiles += file
        return AppResult.Success(Unit)
    }
}

class FakeImageProcessingRepository : ImageProcessingRepository {
    override suspend fun detectCorners(imagePath: String): AppResult<CornerDetectionResult> =
        AppResult.Success(CornerDetectionResult(CropCorners.withInset(), detected = false, confidence = 0f))

    override suspend fun renderPage(page: DocumentPage): AppResult<DocumentPage> =
        AppResult.Success(page.copy(processedPath = "/tmp/processed.jpg", previewPath = "/tmp/preview.webp"))
}

class FakeImageImporter : ImageImporter {
    val imported = mutableListOf<Pair<String, String>>()

    override suspend fun importImage(documentId: String, uriString: String): String {
        imported += documentId to uriString
        return "/tmp/$documentId/imported.jpg"
    }
}
