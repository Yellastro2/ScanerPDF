package com.nla.AIscanerPDF.presentation.document

import androidx.lifecycle.SavedStateHandle
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
import com.nla.AIscanerPDF.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.usecase.DeletePageUseCase
import com.nla.AIscanerPDF.domain.usecase.ExportDocumentToPdfUseCase
import com.nla.AIscanerPDF.domain.usecase.ImportImageToDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ObserveDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.ReorderPagesUseCase
import com.nla.AIscanerPDF.domain.usecase.ShareDocumentUseCase
import kotlin.text.get

data class DocumentUiState(
    val isLoading: Boolean = true,
    val document: DocumentWithPages? = null,
    val isExporting: Boolean = false,
    val error: AppError? = null,
)

sealed interface DocumentUiEffect {
    data class OpenCamera(val documentId: String) : DocumentUiEffect
    data class OpenEditor(val pageId: String) : DocumentUiEffect
    data class OpenCrop(val pageId: String) : DocumentUiEffect
    data class OpenOcr(val documentId: String) : DocumentUiEffect
    data class OpenAi(val documentId: String) : DocumentUiEffect
    data object OpenPremium : DocumentUiEffect
}

class DocumentViewModel(
    savedStateHandle: SavedStateHandle,
    observeDocument: ObserveDocumentUseCase,
    private val reorderPages: ReorderPagesUseCase,
    private val deletePage: DeletePageUseCase,
    private val exportToPdf: ExportDocumentToPdfUseCase,
    private val shareDocument: ShareDocumentUseCase,
    private val importImage: ImportImageToDocumentUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val exporting = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<AppError?>(null)

    val state: StateFlow<DocumentUiState> = combine(
        observeDocument(documentId),
        exporting,
        errorFlow,
    ) { doc, isExporting, error ->
        DocumentUiState(
            isLoading = false,
            document = doc,
            isExporting = isExporting,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentUiState())

    private val _effects = Channel<DocumentUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onAddPage() {
        viewModelScope.launch { _effects.send(DocumentUiEffect.OpenCamera(documentId)) }
    }

    fun onEditPage(pageId: String) {
        viewModelScope.launch { _effects.send(DocumentUiEffect.OpenEditor(pageId)) }
    }

    fun onDeletePage(pageId: String) {
        viewModelScope.launch { deletePage(pageId) }
    }

    fun onMovePage(pageId: String, up: Boolean) {
        val pages = state.value.document?.pages?.sortedBy { it.position } ?: return
        val index = pages.indexOfFirst { it.id == pageId }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in pages.indices) return
        val ids = pages.map { it.id }.toMutableList()
        ids[index] = ids[target].also { ids[target] = ids[index] }
        viewModelScope.launch { reorderPages(documentId, ids) }
    }

    fun onExportPdf(options: PdfExportOptions) {
        viewModelScope.launch {
            exporting.value = true
            analytics.logEvent(AnalyticsEvent.PDF_EXPORT_STARTED)
            when (val result = exportToPdf(documentId, options)) {
                is AppResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.PDF_EXPORT_COMPLETED)
                    shareDocument(result.value)
                }
                is AppResult.Failure -> {
                    errorFlow.value = result.error
                    if (result.error is AppError.FreeLimitReached) {
                        analytics.logEvent(AnalyticsEvent.PAYWALL_SHOWN)
                        _effects.send(DocumentUiEffect.OpenPremium)
                    }
                }
            }
            exporting.value = false
        }
    }

    fun onRunOcr() {
        viewModelScope.launch { _effects.send(DocumentUiEffect.OpenOcr(documentId)) }
    }

    /** Перестановка перетаскиванием: перемещает страницу с позиции from на to. */
    fun onMovePageByIndex(fromIndex: Int, toIndex: Int) {
        val pages = state.value.document?.pages?.sortedBy { it.position } ?: return
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) return
        val ids = pages.map { it.id }.toMutableList()
        val moved = ids.removeAt(fromIndex)
        ids.add(toIndex, moved)
        viewModelScope.launch { reorderPages(documentId, ids) }
    }

    fun onImportFromGallery(uriString: String) {
        viewModelScope.launch {
            when (val result = importImage(documentId, uriString, "")) {
                is AppResult.Success -> _effects.send(DocumentUiEffect.OpenCrop(result.value.id))
                is AppResult.Failure -> errorFlow.value = result.error
            }
        }
    }

    fun onRunAi() {
        viewModelScope.launch { _effects.send(DocumentUiEffect.OpenAi(documentId)) }
    }

    fun consumeError() { errorFlow.value = null }
}
