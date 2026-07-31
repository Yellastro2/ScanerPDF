package com.nla.AIscanerPDF.presentation.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.AnalyticsEvent
import com.nla.AIscanerPDF.domain.model.AiSummary
import com.nla.AIscanerPDF.domain.model.ContractAnalysis
import com.nla.AIscanerPDF.domain.model.ExtractedDocumentData
import com.nla.AIscanerPDF.domain.model.OcrProgress
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.SettingsRepository
import com.nla.AIscanerPDF.domain.usecase.AnalyzeContractUseCase
import com.nla.AIscanerPDF.domain.usecase.ExtractDocumentDataUseCase
import com.nla.AIscanerPDF.domain.usecase.RecognizeDocumentTextUseCase
import com.nla.AIscanerPDF.domain.usecase.SummarizeDocumentUseCase

enum class AiMode { SUMMARY, EXTRACTION, CONTRACT }

data class AiUiState(
    val isLoading: Boolean = false,
    val isRecognizingDocument: Boolean = false,
    val activeMode: AiMode? = null,
    val hasRecognizedText: Boolean = false,
    val showConsentDialog: Boolean = false,
    val summary: AiSummary? = null,
    val extraction: ExtractedDocumentData? = null,
    val contract: ContractAnalysis? = null,
    val error: AppError? = null,
) {
    val isBusy: Boolean get() = isLoading || isRecognizingDocument
}

sealed interface AiUiEffect {
    data object OpenPremium : AiUiEffect
}

/**
 * AI работает только с распознанным текстом (п. 9 ТЗ). Перед первым
 * запросом показывается согласие на отправку текста на сервер (п. 10 ТЗ);
 * изображения не отправляются.
 */
class AiViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val settings: SettingsRepository,
    private val recognizeText: RecognizeDocumentTextUseCase,
    private val summarize: SummarizeDocumentUseCase,
    private val extractData: ExtractDocumentDataUseCase,
    private val analyzeContract: AnalyzeContractUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    private val _effects = Channel<AiUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var pendingMode: AiMode? = null

    init {
        viewModelScope.launch {
            val text = loadText()
            _state.update { it.copy(hasRecognizedText = text.isNotBlank()) }
        }
    }

    fun onRun(mode: AiMode) {
        if (_state.value.isBusy) return
        viewModelScope.launch { run(mode) }
    }

    fun onConsentAccepted() {
        _state.update { it.copy(showConsentDialog = false) }
        val mode = pendingMode ?: return
        pendingMode = null
        viewModelScope.launch {
            settings.setAiConsentGiven(true)
            run(mode)
        }
    }

    fun onConsentDeclined() {
        pendingMode = null
        _state.update { it.copy(showConsentDialog = false) }
    }

    fun consumeError() = _state.update { it.copy(error = null) }

    private suspend fun run(mode: AiMode) {
        val text = prepareRecognizedText(mode) ?: return
        logStart(mode)
        _state.update { it.copy(isLoading = true, activeMode = mode, error = null) }
        val language = "ru"
        when (mode) {
            AiMode.SUMMARY -> when (val result = summarize(documentId, text, language)) {
                is AppResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.AI_SUMMARY_COMPLETED)
                    _state.update { it.copy(isLoading = false, summary = result.value) }
                }
                is AppResult.Failure -> onFailure(mode, result.error)
            }
            AiMode.EXTRACTION -> when (val result = extractData(documentId, text, language)) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, extraction = result.value) }
                is AppResult.Failure -> onFailure(mode, result.error)
            }
            AiMode.CONTRACT -> when (val result = analyzeContract(documentId, text, language)) {
                is AppResult.Success -> _state.update { it.copy(isLoading = false, contract = result.value) }
                is AppResult.Failure -> onFailure(mode, result.error)
            }
        }
    }

    private suspend fun onFailure(mode: AiMode, error: AppError) {
        when (error) {
            AppError.AiConsentRequired -> {
                pendingMode = mode
                _state.update { it.copy(isLoading = false, showConsentDialog = true) }
            }
            is AppError.FreeLimitReached, AppError.AiAccessDenied -> {
                analytics.logEvent(AnalyticsEvent.PAYWALL_SHOWN)
                _state.update { it.copy(isLoading = false) }
                _effects.send(AiUiEffect.OpenPremium)
            }
            else -> _state.update { it.copy(isLoading = false, error = error) }
        }
    }

    /**
     * Дораспознаёт только страницы с recognizedText == null. Страницы с пустой
     * строкой уже проверены OCR и повторно не запускаются.
     */
    private suspend fun prepareRecognizedText(mode: AiMode): String? {
        val document = documents.getDocument(documentId)
        if (document == null) {
            _state.update { it.copy(error = AppError.DocumentNotFound) }
            return null
        }

        if (document.pages.any { it.recognizedText == null }) {
            val language = settings.settings.first().ocrLanguage
            var recognitionError: AppError? = null
            _state.update {
                it.copy(
                    isRecognizingDocument = true,
                    activeMode = mode,
                    error = null,
                )
            }
            analytics.logEvent(AnalyticsEvent.OCR_STARTED)
            recognizeText(
                documentId = documentId,
                language = language,
                onlyUnrecognizedPages = true,
            ).collect { result ->
                when (result) {
                    is AppResult.Success -> if (result.value is OcrProgress.Completed) {
                        analytics.logEvent(AnalyticsEvent.OCR_COMPLETED)
                    }
                    is AppResult.Failure -> recognitionError = result.error
                }
            }
            _state.update { it.copy(isRecognizingDocument = false) }

            recognitionError?.let { error ->
                analytics.logEvent(AnalyticsEvent.OCR_FAILED)
                onRecognitionFailure(error)
                return null
            }
        }

        val text = loadText()
        val hasText = text.isNotBlank()
        _state.update { it.copy(hasRecognizedText = hasText) }
        if (!hasText) {
            _state.update { it.copy(error = AppError.OcrEmpty) }
            return null
        }
        return text
    }

    private suspend fun onRecognitionFailure(error: AppError) {
        when (error) {
            is AppError.FreeLimitReached -> {
                analytics.logEvent(AnalyticsEvent.PAYWALL_SHOWN)
                _effects.send(AiUiEffect.OpenPremium)
            }
            else -> _state.update { it.copy(error = error) }
        }
    }

    private fun logStart(mode: AiMode) = analytics.logEvent(
        when (mode) {
            AiMode.SUMMARY -> AnalyticsEvent.AI_SUMMARY_STARTED
            AiMode.EXTRACTION -> AnalyticsEvent.AI_EXTRACTION_STARTED
            AiMode.CONTRACT -> AnalyticsEvent.CONTRACT_ANALYSIS_STARTED
        },
    )

    private suspend fun loadText(): String {
        val doc = documents.getDocument(documentId) ?: return ""
        return doc.pages.sortedBy { it.position }
            .mapNotNull { it.recognizedText }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}
