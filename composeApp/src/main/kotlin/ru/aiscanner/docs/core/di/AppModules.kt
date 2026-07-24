package ru.aiscanner.docs.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.aiscanner.docs.BuildConfig
import ru.aiscanner.docs.core.DefaultDispatchersProvider
import ru.aiscanner.docs.core.DispatchersProvider
import ru.aiscanner.docs.data.ai.AiDocumentService
import ru.aiscanner.docs.data.ai.AiResponseParser
import ru.aiscanner.docs.data.ai.DisabledAiDocumentService
import ru.aiscanner.docs.data.ai.KtorAiDocumentService
import ru.aiscanner.docs.data.ai.MockAiDocumentService
import ru.aiscanner.docs.data.backend.BackendApiLogger
import ru.aiscanner.docs.data.backend.BackendAuthService
import ru.aiscanner.docs.data.backend.BackendSessionStore
import ru.aiscanner.docs.data.backend.DataStoreBackendSessionStore
import ru.aiscanner.docs.data.billing.MockRuStoreSubscriptionRepository
import ru.aiscanner.docs.data.billing.RuStoreSubscriptionRepository
import ru.aiscanner.docs.data.analytics.Analytics
import ru.aiscanner.docs.data.analytics.DebugAnalytics
import ru.aiscanner.docs.data.db.AppDatabase
import ru.aiscanner.docs.data.export.PdfExporter
import ru.aiscanner.docs.data.files.AndroidImageImporter
import ru.aiscanner.docs.data.files.DocumentFileStore
import ru.aiscanner.docs.data.imageprocessing.AndroidDocumentImageProcessor
import ru.aiscanner.docs.data.imageprocessing.DocumentCornerDetector
import ru.aiscanner.docs.data.imageprocessing.DocumentImageProcessor
import ru.aiscanner.docs.data.imageprocessing.OpenCvDocumentCornerDetector
import ru.aiscanner.docs.data.ocr.OcrEngine
import ru.aiscanner.docs.data.ocr.TesseractOcrEngine
import ru.aiscanner.docs.data.repository.AiRepositoryImpl
import ru.aiscanner.docs.data.repository.DocumentRepositoryImpl
import ru.aiscanner.docs.data.repository.ExportRepositoryImpl
import ru.aiscanner.docs.data.repository.ImageProcessingRepositoryImpl
import ru.aiscanner.docs.data.repository.OcrRepositoryImpl
import ru.aiscanner.docs.data.repository.SettingsRepositoryImpl
import ru.aiscanner.docs.domain.logic.FreePlanLimiter
import ru.aiscanner.docs.domain.model.FreePlanLimits
import ru.aiscanner.docs.domain.repository.AiRepository
import ru.aiscanner.docs.domain.repository.DocumentRepository
import ru.aiscanner.docs.domain.repository.ExportRepository
import ru.aiscanner.docs.domain.repository.ImageImporter
import ru.aiscanner.docs.domain.repository.ImageProcessingRepository
import ru.aiscanner.docs.domain.repository.OcrRepository
import ru.aiscanner.docs.domain.repository.SettingsRepository
import ru.aiscanner.docs.domain.repository.SubscriptionRepository
import ru.aiscanner.docs.domain.usecase.AddPageToDocumentUseCase
import ru.aiscanner.docs.domain.usecase.AiGate
import ru.aiscanner.docs.domain.usecase.AnalyzeContractUseCase
import ru.aiscanner.docs.domain.usecase.ApplyPageFilterUseCase
import ru.aiscanner.docs.domain.usecase.CreateDocumentUseCase
import ru.aiscanner.docs.domain.usecase.DeleteDocumentUseCase
import ru.aiscanner.docs.domain.usecase.DeletePageUseCase
import ru.aiscanner.docs.domain.usecase.ExportDocumentToPdfUseCase
import ru.aiscanner.docs.domain.usecase.ExportPageToJpgUseCase
import ru.aiscanner.docs.domain.usecase.ExtractDocumentDataUseCase
import ru.aiscanner.docs.domain.usecase.GetDocumentsUseCase
import ru.aiscanner.docs.domain.usecase.ImportImageToDocumentUseCase
import ru.aiscanner.docs.domain.usecase.ObserveDocumentUseCase
import ru.aiscanner.docs.domain.usecase.RecognizeDocumentTextUseCase
import ru.aiscanner.docs.domain.usecase.RenameDocumentUseCase
import ru.aiscanner.docs.domain.usecase.ReorderPagesUseCase
import ru.aiscanner.docs.domain.usecase.ShareDocumentUseCase
import ru.aiscanner.docs.domain.usecase.SummarizeDocumentUseCase
import ru.aiscanner.docs.domain.usecase.UpdatePageCropUseCase
import ru.aiscanner.docs.presentation.ai.AiViewModel
import ru.aiscanner.docs.presentation.camera.CameraViewModel
import ru.aiscanner.docs.presentation.crop.CropViewModel
import ru.aiscanner.docs.presentation.document.DocumentViewModel
import ru.aiscanner.docs.presentation.editor.PageEditorViewModel
import ru.aiscanner.docs.presentation.home.HomeViewModel
import ru.aiscanner.docs.presentation.ocr.OcrViewModel
import ru.aiscanner.docs.presentation.premium.PremiumViewModel
import ru.aiscanner.docs.presentation.settings.SettingsViewModel
import ru.rustore.sdk.billingclient.RuStoreBillingClientFactory

val coreModule = module {
    single<DispatchersProvider> { DefaultDispatchersProvider() }
    single<Analytics> { DebugAnalytics() }
    single { FreePlanLimits.DEFAULT }
    single { FreePlanLimiter(get()) }
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(AiResponseParser.json) }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
}

val dataModule = module {
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().documentDao() }
    single { DocumentFileStore(androidContext()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get(), get(), get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }
    single { BackendApiLogger(enabled = BuildConfig.DEBUG) }
    single<BackendSessionStore> { DataStoreBackendSessionStore(androidContext()) }
    single { BackendAuthService(get(), BuildConfig.AI_BASE_URL, get(), get()) }
    // debug: mock RuStore с настоящей backend-авторизацией;
    // release: настоящая покупка RuStore с тем же обменом на access token.
    single<SubscriptionRepository> {
        if (BuildConfig.DEBUG) {
            MockRuStoreSubscriptionRepository(
                backendAuth = get(),
                sessionStore = get(),
                monthlyProductId = BuildConfig.RUSTORE_MONTHLY_ID,
                yearlyProductId = BuildConfig.RUSTORE_YEARLY_ID,
            )
        } else {
            val client = RuStoreBillingClientFactory.create(
                context = androidContext(),
                consoleApplicationId = BuildConfig.RUSTORE_CONSOLE_APP_ID,
                deeplinkScheme = "scannerai",
            )
            RuStoreSubscriptionRepository(
                client = client,
                backendAuth = get(),
                sessionStore = get(),
                monthlyProductId = BuildConfig.RUSTORE_MONTHLY_ID,
                yearlyProductId = BuildConfig.RUSTORE_YEARLY_ID,
            )
        }
    }
    single<ImageImporter> { AndroidImageImporter(androidContext(), get(), get()) }

    single<DocumentCornerDetector> { OpenCvDocumentCornerDetector(get()) }
    single<DocumentImageProcessor> { AndroidDocumentImageProcessor(get(), get()) }
    single<ImageProcessingRepository> { ImageProcessingRepositoryImpl(get(), get(), get(), get()) }

    single<OcrEngine> { TesseractOcrEngine(androidContext(), get()) }
    single<OcrRepository> { OcrRepositoryImpl(get(), get(), get()) }

    // debug: mock разрешён только явным флагом USE_MOCK_AI;
    // release: только реальный Ktor-клиент к backend-прокси;
    // без AI_BASE_URL AI-функции отключены с понятным сообщением.
    single<AiDocumentService> {
        val baseUrl = BuildConfig.AI_BASE_URL
        when {
            BuildConfig.DEBUG && BuildConfig.USE_MOCK_AI -> MockAiDocumentService()
            baseUrl.isNotBlank() -> KtorAiDocumentService(get(), baseUrl, get(), get(), get())
            else -> DisabledAiDocumentService()
        }
    }
    single<AiRepository> { AiRepositoryImpl(get()) }

    single { PdfExporter() }
    single<ExportRepository> { ExportRepositoryImpl(androidContext(), get(), get(), get(), get()) }
}

val domainModule = module {
    factory { GetDocumentsUseCase(get()) }
    factory { ObserveDocumentUseCase(get()) }
    factory { CreateDocumentUseCase(get()) }
    factory { RenameDocumentUseCase(get()) }
    factory { DeleteDocumentUseCase(get()) }
    factory { AddPageToDocumentUseCase(get()) }
    factory { DeletePageUseCase(get()) }
    factory { ReorderPagesUseCase(get()) }
    factory { UpdatePageCropUseCase(get(), get()) }
    factory { ApplyPageFilterUseCase(get(), get()) }
    factory { ExportDocumentToPdfUseCase(get(), get(), get(), get()) }
    factory { ExportPageToJpgUseCase(get()) }
    factory { ShareDocumentUseCase(get()) }
    factory { ImportImageToDocumentUseCase(get(), get(), get()) }
    factory { RecognizeDocumentTextUseCase(get(), get(), get(), get()) }
    factory { AiGate(get(), get(), get()) }
    factory { SummarizeDocumentUseCase(get(), get()) }
    factory { ExtractDocumentDataUseCase(get(), get()) }
    factory { AnalyzeContractUseCase(get(), get()) }
}

val viewModelModule = module {
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { CameraViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { CropViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { PageEditorViewModel(get(), get(), get(), get()) }
    viewModel { DocumentViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { OcrViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { AiViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { PremiumViewModel(get(), get()) }
}

val appModules = listOf(coreModule, dataModule, domainModule, viewModelModule)
