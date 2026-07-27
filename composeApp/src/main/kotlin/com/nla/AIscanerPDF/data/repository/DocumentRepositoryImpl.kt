package com.nla.AIscanerPDF.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.data.db.DocumentDao
import com.nla.AIscanerPDF.data.db.DocumentEntity
import com.nla.AIscanerPDF.data.db.toDomain
import com.nla.AIscanerPDF.data.db.toEntity
import com.nla.AIscanerPDF.data.files.DocumentFileStore
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.DocumentPage
import com.nla.AIscanerPDF.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import java.util.UUID

class DocumentRepositoryImpl(
    private val dao: DocumentDao,
    private val files: DocumentFileStore,
    private val dispatchers: DispatchersProvider,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> =
        combine(dao.observeDocuments(), dao.observeAllPages()) { docs, pages ->
            val byDoc = pages.groupBy { it.documentId }
            docs.map { it.toDomain(byDoc[it.id].orEmpty()) }
        }

    override fun observeDocument(documentId: String): Flow<DocumentWithPages?> =
        combine(dao.observeDocument(documentId), dao.observePages(documentId)) { doc, pages ->
            doc?.let {
                DocumentWithPages(
                    document = it.toDomain(pages),
                    pages = pages.map { p -> p.toDomain() },
                )
            }
        }

    override suspend fun getDocument(documentId: String): DocumentWithPages? =
        withContext(dispatchers.io) {
            val doc = dao.getDocument(documentId) ?: return@withContext null
            val pages = dao.getPages(documentId)
            DocumentWithPages(doc.toDomain(pages), pages.map { it.toDomain() })
        }

    override suspend fun createDocument(name: String): Document = withContext(dispatchers.io) {
        val now = System.currentTimeMillis()
        val entity = DocumentEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertDocument(entity)
        entity.toDomain(emptyList())
    }

    override suspend fun renameDocument(documentId: String, newName: String) =
        withContext(dispatchers.io) {
            val doc = dao.getDocument(documentId) ?: return@withContext
            dao.upsertDocument(doc.copy(name = newName, updatedAt = System.currentTimeMillis()))
        }

    override suspend fun deleteDocument(documentId: String) = withContext(dispatchers.io) {
        dao.deleteDocument(documentId) // страницы удаляются каскадно
        files.deleteDocumentFiles(documentId)
    }

    override suspend fun deleteAllDocuments() = withContext(dispatchers.io) {
        dao.deleteAllDocuments()
        files.deleteAll()
    }

    override suspend fun addPage(
        documentId: String,
        originalPath: String,
        initialCrop: CropCorners?,
    ): DocumentPage = withContext(dispatchers.io) {
        val position = dao.getPages(documentId).size
        val page = DocumentPage(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            position = position,
            originalPath = originalPath,
            processedPath = null,
            previewPath = null,
            crop = initialCrop,
        )
        dao.upsertPage(page.toEntity())
        dao.touchDocument(documentId, System.currentTimeMillis())
        page
    }

    override suspend fun getPage(pageId: String): DocumentPage? = withContext(dispatchers.io) {
        dao.getPage(pageId)?.toDomain()
    }

    override suspend fun updatePage(page: DocumentPage) = withContext(dispatchers.io) {
        dao.upsertPage(page.toEntity())
        dao.touchDocument(page.documentId, System.currentTimeMillis())
    }

    override suspend fun deletePage(pageId: String) = withContext(dispatchers.io) {
        val page = dao.getPage(pageId) ?: return@withContext
        dao.deletePage(pageId)
        files.deletePageFiles(page.toDomain())
        // Нормализуем позиции оставшихся страниц
        val remaining = dao.getPages(page.documentId)
        dao.updatePages(remaining.mapIndexed { index, p -> p.copy(position = index) })
        dao.touchDocument(page.documentId, System.currentTimeMillis())
    }

    override suspend fun reorderPages(documentId: String, orderedPageIds: List<String>) =
        withContext(dispatchers.io) {
            dao.reorderPages(documentId, orderedPageIds, System.currentTimeMillis())
        }

    override suspend fun newOriginalFilePath(documentId: String): String =
        withContext(dispatchers.io) { files.newOriginalFile(documentId).absolutePath }
}
