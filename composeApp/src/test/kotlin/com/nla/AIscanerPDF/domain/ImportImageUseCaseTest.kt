package com.nla.AIscanerPDF.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.usecase.ImportImageToDocumentUseCase
import ru.aiscanner.docs.fakes.FakeDocumentRepository
import ru.aiscanner.docs.fakes.FakeImageImporter
import ru.aiscanner.docs.fakes.FakeImageProcessingRepository

class ImportImageUseCaseTest {

    @Test
    fun `import without document creates new document with page`() = runTest {
        val repo = FakeDocumentRepository()
        val useCase = ImportImageToDocumentUseCase(repo, FakeImageImporter(), FakeImageProcessingRepository())

        val result = useCase(null, "content://media/1", "Импорт 01.01")

        assertTrue(result is AppResult.Success)
        val page = (result as AppResult.Success).value
        val documents = repo.observeDocuments().first()
        assertEquals(1, documents.size)
        assertEquals("Импорт 01.01", documents.first().name)
        assertNotNull(page.crop) // автодетект вернул углы с отступами
    }

    @Test
    fun `import into existing document appends page`() = runTest {
        val repo = FakeDocumentRepository()
        val doc = repo.createDocument("Документ")
        repo.addPage(doc.id, "/tmp/1.jpg", null)
        val useCase = ImportImageToDocumentUseCase(repo, FakeImageImporter(), FakeImageProcessingRepository())

        val result = useCase(doc.id, "content://media/2", "не используется")

        assertTrue(result is AppResult.Success)
        assertEquals(2, repo.getDocument(doc.id)?.pages?.size)
        assertEquals(1, (result as AppResult.Success).value.position)
    }
}
