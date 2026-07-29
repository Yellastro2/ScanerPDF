package com.nla.AIscanerPDF.core.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import com.nla.AIscanerPDF.BuildConfig
import com.nla.AIscanerPDF.core.DefaultDispatchersProvider
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.data.ai.AiDocumentService
import com.nla.AIscanerPDF.data.ai.AiResponseParser
import com.nla.AIscanerPDF.data.ai.DisabledAiDocumentService
import com.nla.AIscanerPDF.data.ai.KtorAiDocumentService
import com.nla.AIscanerPDF.data.ai.MockAiDocumentService
import com.nla.AIscanerPDF.data.backend.BackendApiLogger
import com.nla.AIscanerPDF.data.backend.BackendAuthService
import com.nla.AIscanerPDF.data.backend.BackendSessionStore
import com.nla.AIscanerPDF.data.backend.DataStoreBackendSessionStore
import com.nla.AIscanerPDF.data.billing.MockRuStoreSubscriptionRepository
import com.nla.AIscanerPDF.data.billing.RuStorePayLogger
import com.nla.AIscanerPDF.data.billing.RuStoreSubscriptionRepository
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.DebugAnalytics
import com.nla.AIscanerPDF.data.db.AppDatabase
import com.nla.AIscanerPDF.data.export.PdfExporter
import com.nla.AIscanerPDF.data.files.AndroidImageImporter
import com.nla.AIscanerPDF.data.files.DocumentFileStore
import com.nla.AIscanerPDF.data.imageprocessing.AndroidDocumentImageProcessor
import com.nla.AIscanerPDF.data.imageprocessing.DocumentCornerDetector
import com.nla.AIscanerPDF.data.imageprocessing.DocumentImageProcessor
import com.nla.AIscanerPDF.data.imageprocessing.OpenCvDocumentCornerDetector
import com.nla.AIscanerPDF.data.ocr.OcrEngine
import com.nla.AIscanerPDF.data.ocr.TesseractOcrEngine
import com.nla.AIscanerPDF.data.repository.AiRepositoryImpl
import com.nla.AIscanerPDF.data.repository.DocumentRepositoryImpl
import com.nla.AIscanerPDF.data.repository.ExportRepositoryImpl
import com.nla.AIscanerPDF.data.repository.ImageProcessingRepositoryImpl
import com.nla.AIscanerPDF.data.repository.OcrRepositoryImpl
import com.nla.AIscanerPDF.data.repository.SettingsRepositoryImpl
import com.nla.AIscanerPDF.domain.logic.FreePlanLimiter
import com.nla.AIscanerPDF.domain.model.FreePlanLimits
import com.nla.AIscanerPDF.domain.repository.AiRepository
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ExportRepository
import com.nla.AIscanerPDF.domain.repository.ImageImporter
import com.nla.AIscanerPDF.domain.repository.ImageProcessingRepository
import com.nla.AIscanerPDF.domain.repository.OcrRepository
import com.nla.AIscanerPDF.domain.repository.SettingsRepository
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository
import com.nla.AIscanerPDF.domain.usecase.AddPageToDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.AiGate
import com.nla.AIscanerPDF.domain.usecase.AnalyzeContractUseCase
import com.nla.AIscanerPDF.domain.usecase.ApplyPageFilterUseCase
import com.nla.AIscanerPDF.domain.usecase.CreateDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.DeleteDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.DeletePageUseCase
import com.nla.AIscanerPDF.domain.usecase.ExportDocumentToPdfUseCase
import com.nla.AIscanerPDF.domain.usecase.ExportPageToJpgUseCase
import com.nla.AIscanerPDF.domain.usecase.ExtractDocumentDataUseCase
import com.nla.AIscanerPDF.domain.usecase.GetDocumentsUseCase
import com.nla.AIscanerPDF.domain.usecase.ImportImageToDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ObserveDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.RecognizeDocumentTextUseCase
import com.nla.AIscanerPDF.domain.usecase.RenameDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ReorderPagesUseCase
import com.nla.AIscanerPDF.domain.usecase.ShareDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.SummarizeDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.UpdatePageCropUseCase
import com.nla.AIscanerPDF.presentation.ai.AiViewModel
import com.nla.AIscanerPDF.presentation.camera.CameraViewModel
import com.nla.AIscanerPDF.presentation.crop.CropViewModel
import com.nla.AIscanerPDF.presentation.document.DocumentViewModel
import com.nla.AIscanerPDF.presentation.editor.PageEditorViewModel
import com.nla.AIscanerPDF.presentation.home.HomeViewModel
import com.nla.AIscanerPDF.presentation.ocr.OcrViewModel
import com.nla.AIscanerPDF.presentation.premium.PremiumViewModel
import com.nla.AIscanerPDF.presentation.settings.SettingsViewModel
import ru.rustore.sdk.pay.RuStorePayClient

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
    single { RuStorePayLogger(verboseEnabled = BuildConfig.DEBUG) }
    single<BackendSessionStore> { DataStoreBackendSessionStore(androidContext()) }
    single { BackendAuthService(get(), BuildConfig.AI_BASE_URL, get(), get()) }
    // Mock доступен только в debug; release всегда использует RuStore Pay SDK.
    single<SubscriptionRepository> {
        val ruStoreLogger = get<RuStorePayLogger>()
        if (BuildConfig.DEBUG && BuildConfig.USE_MOCK_RUSTORE) {
            ruStoreLogger.event(
                "repository SELECTED mode=mock package=${androidContext().packageName}",
            )
            MockRuStoreSubscriptionRepository(
                backendAuth = get(),
                sessionStore = get(),
                monthlyProductId = BuildConfig.RUSTORE_MONTHLY_ID,
                yearlyProductId = BuildConfig.RUSTORE_YEARLY_ID,
                foreverProductId = BuildConfig.RUSTORE_FOREVER_ID,
            )
        } else {
            ruStoreLogger.event(
                "repository SELECTED mode=real package=${androidContext().packageName} " +
                    "consoleAppId=${BuildConfig.RUSTORE_CONSOLE_APP_ID} " +
                    "products=${BuildConfig.RUSTORE_MONTHLY_ID}," +
                    "${BuildConfig.RUSTORE_YEARLY_ID},${BuildConfig.RUSTORE_FOREVER_ID}",
            )
            RuStoreSubscriptionRepository(
                client = RuStorePayClient.instance,
                backendAuth = get(),
                sessionStore = get(),
                logger = ruStoreLogger,
                monthlyProductId = BuildConfig.RUSTORE_MONTHLY_ID,
                yearlyProductId = BuildConfig.RUSTORE_YEARLY_ID,
                foreverProductId = BuildConfig.RUSTORE_FOREVER_ID,
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
