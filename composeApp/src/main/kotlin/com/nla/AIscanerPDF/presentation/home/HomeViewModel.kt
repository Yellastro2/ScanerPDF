package com.nla.AIscanerPDF.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.AnalyticsEvent
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.usecase.DeleteDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ExportDocumentToPdfUseCase
import com.nla.AIscanerPDF.domain.usecase.GetDocumentsUseCase
import com.nla.AIscanerPDF.domain.usecase.ImportImageToDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.RenameDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ShareDocumentUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val isLoading: Boolean = true,
    val documents: List<Document> = emptyList(),
    val isBusy: Boolean = false,
    val error: AppError? = null,
)

sealed interface HomeUiEffect {
    data class OpenDocument(val documentId: String) : HomeUiEffect
    data object OpenCamera : HomeUiEffect
    data class OpenCrop(val pageId: String) : HomeUiEffect
    data object OpenPremium : HomeUiEffect
}

class HomeViewModel(
    getDocuments: GetDocumentsUseCase,
    private val renameDocument: RenameDocumentUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val exportToPdf: ExportDocumentToPdfUseCase,
    private val shareDocument: ShareDocumentUseCase,
    private val importImage: ImportImageToDocumentUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<AppError?>(null)

    val state: StateFlow<HomeUiState> = combine(
        getDocuments(),
        busy,
        errorFlow,
    ) { documents, isBusy, error ->
        HomeUiState(isLoading = false, documents = documents, isBusy = isBusy, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _effects = Channel<HomeUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onScanClick() {
        analytics.logEvent(AnalyticsEvent.SCAN_STARTED)
        viewModelScope.launch { _effects.send(HomeUiEffect.OpenCamera) }
    }

    fun onDocumentClick(documentId: String) {
        viewModelScope.launch { _effects.send(HomeUiEffect.OpenDocument(documentId)) }
    }

    fun onRename(documentId: String, newName: String) {
        viewModelScope.launch { renameDocument(documentId, newName) }
    }

    fun onDelete(documentId: String) {
        viewModelScope.launch { deleteDocument(documentId) }
    }

    /** «Поделиться»/«Экспортировать» из меню: PDF с настройками по умолчанию + Sharesheet. */
    fun onExportAndShare(documentId: String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            analytics.logEvent(AnalyticsEvent.PDF_EXPORT_STARTED)
            when (val result = exportToPdf(documentId, PdfExportOptions())) {
                is AppResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.PDF_EXPORT_COMPLETED)
                    shareDocument(result.value)
                }
                is AppResult.Failure -> {
                    errorFlow.value = result.error
                    if (result.error is AppError.FreeLimitReached) {
                        analytics.logEvent(AnalyticsEvent.PAYWALL_SHOWN)
                        _effects.send(HomeUiEffect.OpenPremium)
                    }
                }
            }
            busy.value = false
        }
    }

    fun onImportFromGallery(uriString: String, namePrefix: String) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            val name = namePrefix + " " +
                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            when (val result = importImage(null, uriString, name)) {
                is AppResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.DOCUMENT_CREATED)
                    _effects.send(HomeUiEffect.OpenCrop(result.value.id))
                }
                is AppResult.Failure -> errorFlow.value = result.error
            }
            busy.value = false
        }
    }

    fun consumeError() { errorFlow.value = null }
}
