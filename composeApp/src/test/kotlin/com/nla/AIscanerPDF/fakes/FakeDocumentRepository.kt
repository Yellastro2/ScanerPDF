package com.nla.AIscanerPDF.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.aiscanner.docs.domain.model.CropCorners
import ru.aiscanner.docs.domain.model.Document
import ru.aiscanner.docs.domain.model.DocumentPage
import ru.aiscanner.docs.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import java.util.UUID

/** In-memory реализация для unit-тестов. */
class FakeDocumentRepository : DocumentRepository {

    private data class State(
        val documents: Map<String, Document> = emptyMap(),
        val pages: Map<String, DocumentPage> = emptyMap(),
    )

    private val state = MutableStateFlow(State())

    override fun observeDocuments(): Flow<List<Document>> =
        state.map { s ->
            s.documents.values.map { doc ->
                doc.copy(pageCount = s.pages.values.count { it.documentId == doc.id })
            }.sortedByDescending { it.updatedAt }
        }

    override fun observeDocument(documentId: String): Flow<DocumentWithPages?> =
        state.map { buildDocumentWithPages(it, documentId) }

    override suspend fun getDocument(documentId: String): DocumentWithPages? =
        buildDocumentWithPages(state.value, documentId)

    private fun buildDocumentWithPages(s: State, documentId: String): DocumentWithPages? {
        val doc = s.documents[documentId] ?: return null
        val pages = s.pages.values.filter { it.documentId == documentId }.sortedBy { it.position }
        return DocumentWithPages(doc.copy(pageCount = pages.size), pages)
    }

    override suspend fun createDocument(name: String): Document {
        val now = System.currentTimeMillis()
        val doc = Document(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = now,
            updatedAt = now,
            pageCount = 0,
            previewPath = null,
            hasRecognizedText = false,
        )
        state.value = state.value.copy(documents = state.value.documents + (doc.id to doc))
        return doc
    }

    override suspend fun renameDocument(documentId: String, newName: String) {
        val doc = state.value.documents[documentId] ?: return
        state.value = state.value.copy(
            documents = state.value.documents + (documentId to doc.copy(name = newName)),
        )
    }

    override suspend fun deleteDocument(documentId: String) {
        state.value = State(
            documents = state.value.documents - documentId,
            pages = state.value.pages.filterValues { it.documentId != documentId },
        )
    }

    override suspend fun deleteAllDocuments() {
        state.value = State()
    }

    override suspend fun addPage(
        documentId: String,
        originalPath: String,
        initialCrop: CropCorners?,
    ): DocumentPage {
        val position = state.value.pages.values.count { it.documentId == documentId }
        val page = DocumentPage(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            position = position,
            originalPath = originalPath,
            processedPath = null,
            previewPath = null,
            crop = initialCrop,
        )
        state.value = state.value.copy(pages = state.value.pages + (page.id to page))
        return page
    }

    override suspend fun getPage(pageId: String): DocumentPage? = state.value.pages[pageId]

    override suspend fun updatePage(page: DocumentPage) {
        state.value = state.value.copy(pages = state.value.pages + (page.id to page))
    }

    override suspend fun deletePage(pageId: String) {
        val page = state.value.pages[pageId] ?: return
        val remaining = state.value.pages - pageId
        val normalized = remaining.values
            .filter { it.documentId == page.documentId }
            .sortedBy { it.position }
            .mapIndexed { index, p -> p.copy(position = index) }
            .associateBy { it.id }
        state.value = state.value.copy(
            pages = remaining.filterValues { it.documentId != page.documentId } + normalized,
        )
    }

    override suspend fun reorderPages(documentId: String, orderedPageIds: List<String>) {
        val updated = orderedPageIds.mapIndexedNotNull { index, id ->
            state.value.pages[id]?.copy(position = index)
        }.associateBy { it.id }
        state.value = state.value.copy(pages = state.value.pages + updated)
    }

    override suspend fun newOriginalFilePath(documentId: String): String =
        "/tmp/$documentId/${UUID.randomUUID()}.jpg"
}
