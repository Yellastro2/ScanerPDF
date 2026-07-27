package com.nla.AIscanerPDF.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.aiscanner.docs.core.AppResult
import ru.aiscanner.docs.domain.usecase.CreateDocumentUseCase
import ru.aiscanner.docs.domain.usecase.DeletePageUseCase
import ru.aiscanner.docs.domain.usecase.ReorderPagesUseCase
import ru.aiscanner.docs.fakes.FakeDocumentRepository

class DocumentUseCasesTest {

    @Test
    fun `create document trims name and stores it`() = runTest {
        val repo = FakeDocumentRepository()
        val document = CreateDocumentUseCase(repo).invoke("  Мой документ  ")
        assertEquals("Мой документ", document.name)
        assertEquals(1, repo.getDocument(document.id)?.let { 1 } ?: 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create document rejects blank name`() = runTest {
        CreateDocumentUseCase(FakeDocumentRepository()).invoke("   ")
    }

    @Test
    fun `reorder pages changes positions`() = runTest {
        val repo = FakeDocumentRepository()
        val doc = repo.createDocument("Документ")
        val p1 = repo.addPage(doc.id, "/tmp/1.jpg", null)
        val p2 = repo.addPage(doc.id, "/tmp/2.jpg", null)
        val p3 = repo.addPage(doc.id, "/tmp/3.jpg", null)

        val result = ReorderPagesUseCase(repo).invoke(doc.id, listOf(p3.id, p1.id, p2.id))

        assertTrue(result is AppResult.Success)
        val ordered = repo.getDocument(doc.id)?.pages?.sortedBy { it.position }?.map { it.id }
        assertEquals(listOf(p3.id, p1.id, p2.id), ordered)
    }

    @Test
    fun `reorder rejects mismatched page ids`() = runTest {
        val repo = FakeDocumentRepository()
        val doc = repo.createDocument("Документ")
        val p1 = repo.addPage(doc.id, "/tmp/1.jpg", null)
        repo.addPage(doc.id, "/tmp/2.jpg", null)

        val result = ReorderPagesUseCase(repo).invoke(doc.id, listOf(p1.id, "unknown-id"))

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun `delete page normalizes remaining positions`() = runTest {
        val repo = FakeDocumentRepository()
        val doc = repo.createDocument("Документ")
        val p1 = repo.addPage(doc.id, "/tmp/1.jpg", null)
        val p2 = repo.addPage(doc.id, "/tmp/2.jpg", null)
        val p3 = repo.addPage(doc.id, "/tmp/3.jpg", null)

        DeletePageUseCase(repo).invoke(p2.id)

        val pages = repo.getDocument(doc.id)?.pages?.sortedBy { it.position }.orEmpty()
        assertEquals(listOf(p1.id, p3.id), pages.map { it.id })
        assertEquals(listOf(0, 1), pages.map { it.position })
    }
}
