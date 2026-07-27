package com.nla.AIscanerPDF.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.aiscanner.docs.data.db.AppDatabase
import ru.aiscanner.docs.data.db.DocumentEntity
import ru.aiscanner.docs.data.db.PageEntity

@RunWith(AndroidJUnit4::class)
class DocumentDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun document(id: String) = DocumentEntity(id, "Документ $id", 1L, 1L)

    private fun page(id: String, docId: String, position: Int) = PageEntity(
        id = id, documentId = docId, position = position,
        originalPath = "/tmp/$id.jpg", processedPath = null, previewPath = null,
        cropJson = null, rotationDegrees = 0, filter = "ORIGINAL",
        brightness = 0f, contrast = 1f, recognizedText = null,
    )

    @Test
    fun createMultiPageDocumentAndRestore() = runTest {
        val dao = db.documentDao()
        dao.upsertDocument(document("d1"))
        dao.upsertPage(page("p1", "d1", 0))
        dao.upsertPage(page("p2", "d1", 1))
        dao.upsertPage(page("p3", "d1", 2))

        // «Восстановление после перезапуска» = чтение из БД заново
        val restored = dao.getPages("d1")
        assertEquals(listOf("p1", "p2", "p3"), restored.map { it.id })
        assertEquals(listOf(0, 1, 2), restored.map { it.position })
        assertEquals("Документ d1", dao.getDocument("d1")?.name)
    }

    @Test
    fun reorderPagesTransactionUpdatesPositions() = runTest {
        val dao = db.documentDao()
        dao.upsertDocument(document("d1"))
        dao.upsertPage(page("p1", "d1", 0))
        dao.upsertPage(page("p2", "d1", 1))
        dao.upsertPage(page("p3", "d1", 2))

        dao.reorderPages("d1", listOf("p3", "p1", "p2"), now = 42L)

        assertEquals(listOf("p3", "p1", "p2"), dao.getPages("d1").map { it.id })
        assertEquals(42L, dao.getDocument("d1")?.updatedAt)
    }

    @Test
    fun deleteDocumentCascadesPages() = runTest {
        val dao = db.documentDao()
        dao.upsertDocument(document("d1"))
        dao.upsertPage(page("p1", "d1", 0))
        dao.upsertPage(page("p2", "d1", 1))

        dao.deleteDocument("d1")

        assertTrue(dao.getPages("d1").isEmpty())
        assertEquals(null, dao.getDocument("d1"))
    }

    @Test
    fun observeDocumentsEmitsSortedByUpdatedAt() = runTest {
        val dao = db.documentDao()
        dao.upsertDocument(DocumentEntity("old", "Старый", 1L, 1L))
        dao.upsertDocument(DocumentEntity("new", "Новый", 2L, 100L))

        val documents = dao.observeDocuments().first()
        assertEquals(listOf("new", "old"), documents.map { it.id })
    }
}
