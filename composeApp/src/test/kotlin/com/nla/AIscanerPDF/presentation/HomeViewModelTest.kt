package com.nla.AIscanerPDF.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.AnalyticsEvent
import ru.aiscanner.docs.domain.logic.FreePlanLimiter
import ru.aiscanner.docs.domain.model.FreePlanLimits
import ru.aiscanner.docs.domain.usecase.DeleteDocumentUseCase
import ru.aiscanner.docs.domain.usecase.ExportDocumentToPdfUseCase
import ru.aiscanner.docs.domain.usecase.GetDocumentsUseCase
import ru.aiscanner.docs.domain.usecase.ImportImageToDocumentUseCase
import ru.aiscanner.docs.domain.usecase.RenameDocumentUseCase
import ru.aiscanner.docs.domain.usecase.ShareDocumentUseCase
import ru.aiscanner.docs.data.repository.StubSubscriptionRepository
import ru.aiscanner.docs.fakes.FakeDocumentRepository
import ru.aiscanner.docs.fakes.FakeExportRepository
import ru.aiscanner.docs.fakes.FakeImageImporter
import ru.aiscanner.docs.fakes.FakeImageProcessingRepository
import ru.aiscanner.docs.presentation.home.HomeUiEffect
import ru.aiscanner.docs.presentation.home.HomeViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeDocumentRepository()
    private val analyticsEvents = mutableListOf<AnalyticsEvent>()
    private val analytics = Analytics { event -> analyticsEvents.add(event) }

    private val exportRepo = FakeExportRepository()

    private fun createViewModel() = HomeViewModel(
        getDocuments = GetDocumentsUseCase(repo),
        renameDocument = RenameDocumentUseCase(repo),
        deleteDocument = DeleteDocumentUseCase(repo),
        exportToPdf = ExportDocumentToPdfUseCase(
            documents = repo,
            export = exportRepo,
            subscriptions = StubSubscriptionRepository(),
            limiter = FreePlanLimiter(FreePlanLimits.DEFAULT),
        ),
        shareDocument = ShareDocumentUseCase(exportRepo),
        importImage = ImportImageToDocumentUseCase(repo, FakeImageImporter(), FakeImageProcessingRepository()),
        analytics = analytics,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state exposes documents from repository`() = runTest(dispatcher) {
        repo.createDocument("Первый")
        repo.createDocument("Второй")
        val viewModel = createViewModel()

        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(2, state.documents.size)
        assertEquals(false, state.isLoading)
        collectJob.cancel()
    }

    @Test
    fun `scan click emits camera effect and logs analytics`() = runTest(dispatcher) {
        val viewModel = createViewModel()

        viewModel.onScanClick()
        dispatcher.scheduler.advanceUntilIdle()

        val effect = viewModel.effects.first()

        assertTrue(effect is HomeUiEffect.OpenCamera)
        assertTrue(analyticsEvents.contains(AnalyticsEvent.SCAN_STARTED))
    }

    @Test
    fun `delete removes document from state`() = runTest(dispatcher) {
        val doc = repo.createDocument("Удаляемый")
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onDelete(doc.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.documents.isEmpty())
        collectJob.cancel()
    }

    @Test
    fun `rename updates document name`() = runTest(dispatcher) {
        val doc = repo.createDocument("Старое имя")
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        viewModel.onRename(doc.id, "Новое имя")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Новое имя", viewModel.state.value.documents.first().name)
        collectJob.cancel()
    }

    @Test
    fun `export and share exports pdf and opens sharesheet`() = runTest(dispatcher) {
        val doc = repo.createDocument("Для экспорта")
        repo.addPage(doc.id, "/tmp/1.jpg", null)
        val viewModel = createViewModel()

        viewModel.onExportAndShare(doc.id)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(doc.id), exportRepo.exportedDocuments)
        assertEquals(1, exportRepo.sharedFiles.size)
        assertTrue(analyticsEvents.contains(AnalyticsEvent.PDF_EXPORT_COMPLETED))
    }

    @Test
    fun `gallery import creates document and requests crop screen`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        val collected = mutableListOf<HomeUiEffect>()
        val job = launch { viewModel.effects.collect { collected.add(it) } }

        viewModel.onImportFromGallery("content://media/42", "Импорт")
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        val collectJob = launch { viewModel.state.collect {} }
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.state.value.documents.size)
        assertTrue(collected.any { it is HomeUiEffect.OpenCrop })
        collectJob.cancel()
    }
}
