package com.nla.AIscanerPDF.domain.usecase

import kotlinx.coroutines.flow.Flow
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.DocumentFilter
import com.nla.AIscanerPDF.domain.model.DocumentPage
import com.nla.AIscanerPDF.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.geometry.QuadValidator
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ImageProcessingRepository

class GetDocumentsUseCase(private val repository: DocumentRepository) {
    operator fun invoke(): Flow<List<Document>> = repository.observeDocuments()
}

class ObserveDocumentUseCase(private val repository: DocumentRepository) {
    operator fun invoke(documentId: String): Flow<DocumentWithPages?> =
        repository.observeDocument(documentId)
}

class CreateDocumentUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(name: String): Document {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Имя документа не может быть пустым" }
        return repository.createDocument(trimmed)
    }
}

class RenameDocumentUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(documentId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) repository.renameDocument(documentId, trimmed)
    }
}

class DeleteDocumentUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(documentId: String) = repository.deleteDocument(documentId)
}

class AddPageToDocumentUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(
        documentId: String,
        originalPath: String,
        initialCrop: CropCorners?,
    ): DocumentPage = repository.addPage(documentId, originalPath, initialCrop)
}

class DeletePageUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(pageId: String) = repository.deletePage(pageId)
}

class ReorderPagesUseCase(private val repository: DocumentRepository) {
    suspend operator fun invoke(documentId: String, orderedPageIds: List<String>): AppResult<Unit> {
        val pages = repository.getDocument(documentId)?.pages
            ?: return AppResult.Failure(AppError.DocumentNotFound)
        val currentIds = pages.map { it.id }.toSet()
        if (orderedPageIds.toSet() != currentIds || orderedPageIds.size != pages.size) {
            return AppResult.Failure(AppError.Unknown)
        }
        repository.reorderPages(documentId, orderedPageIds)
        return AppResult.Success(Unit)
    }
}

class UpdatePageCropUseCase(
    private val repository: DocumentRepository,
    private val imageProcessing: ImageProcessingRepository,
) {
    suspend operator fun invoke(pageId: String, corners: CropCorners): AppResult<DocumentPage> {
        val clamped = QuadValidator.clamp(corners)
        if (!QuadValidator.isValid(clamped)) return AppResult.Failure(AppError.InvalidCropShape)
        val page = repository.getPage(pageId) ?: return AppResult.Failure(AppError.DocumentNotFound)
        val updated = page.copy(crop = clamped)
        repository.updatePage(updated)
        return imageProcessing.renderPage(updated)
    }
}

class ApplyPageFilterUseCase(
    private val repository: DocumentRepository,
    private val imageProcessing: ImageProcessingRepository,
) {
    suspend operator fun invoke(
        pageId: String,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
        rotationDegrees: Int,
    ): AppResult<DocumentPage> {
        val page = repository.getPage(pageId) ?: return AppResult.Failure(AppError.DocumentNotFound)
        val updated = page.copy(
            filter = filter,
            brightness = brightness.coerceIn(-1f, 1f),
            contrast = contrast.coerceIn(0.25f, 3f),
            rotationDegrees = ((rotationDegrees % 360) + 360) % 360,
        )
        repository.updatePage(updated)
        return imageProcessing.renderPage(updated)
    }
}
