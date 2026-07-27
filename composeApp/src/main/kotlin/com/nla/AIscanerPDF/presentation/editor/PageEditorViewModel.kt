package com.nla.AIscanerPDF.presentation.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nla.AIscanerPDF.core.AppError
import com.nla.AIscanerPDF.core.AppResult
import com.nla.AIscanerPDF.data.analytics.Analytics
import com.nla.AIscanerPDF.data.analytics.AnalyticsEvent
import com.nla.AIscanerPDF.domain.model.DocumentFilter
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.usecase.ApplyPageFilterUseCase

data class PageEditorUiState(
    val pageId: String = "",
    val documentId: String? = null,
    /** Превью строится из processed-изображения после коррекции перспективы. */
    val imagePath: String? = null,
    val filter: DocumentFilter = DocumentFilter.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val rotationDegrees: Int = 0,
    val isSaving: Boolean = false,
    val error: AppError? = null,
)

sealed interface PageEditorUiEffect {
    data class OpenDocument(val documentId: String) : PageEditorUiEffect
    data class OpenCrop(val pageId: String) : PageEditorUiEffect
}

/**
 * Живое превью фильтра/яркости/контраста реализуется в UI через
 * ColorFilter.colorMatrix — новый JPEG не создаётся на каждое движение
 * ползунка (п. 5.4 ТЗ). Файл пересобирается один раз при «Сохранить».
 */
class PageEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val applyPageFilter: ApplyPageFilterUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    private val _state = MutableStateFlow(PageEditorUiState(pageId = pageId))
    val state: StateFlow<PageEditorUiState> = _state.asStateFlow()

    private val _effects = Channel<PageEditorUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val page = documents.getPage(pageId)
            if (page == null) {
                _state.update { it.copy(error = AppError.DocumentNotFound) }
            } else {
                _state.update {
                    it.copy(
                        documentId = page.documentId,
                        imagePath = page.processedPath ?: page.originalPath,
                        filter = page.filter,
                        brightness = page.brightness,
                        contrast = page.contrast,
                        rotationDegrees = 0, // поворот поверх уже отрендеренного processed
                    )
                }
            }
        }
    }

    fun onFilterSelected(filter: DocumentFilter) = _state.update { it.copy(filter = filter) }
    fun onBrightnessChanged(value: Float) = _state.update { it.copy(brightness = value.coerceIn(-1f, 1f)) }
    fun onContrastChanged(value: Float) = _state.update { it.copy(contrast = value.coerceIn(0.25f, 3f)) }
    fun onRotate() = _state.update { it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) }

    fun onRecrop() {
        viewModelScope.launch { _effects.send(PageEditorUiEffect.OpenCrop(pageId)) }
    }

    fun onSave() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val page = documents.getPage(pageId)
            val totalRotation = ((page?.rotationDegrees ?: 0) + current.rotationDegrees) % 360
            val result = applyPageFilter(
                pageId = pageId,
                filter = current.filter,
                brightness = current.brightness,
                contrast = current.contrast,
                rotationDegrees = totalRotation,
            )
            when (result) {
                is AppResult.Success -> {
                    analytics.logEvent(AnalyticsEvent.PAGE_SAVED)
                    _effects.send(PageEditorUiEffect.OpenDocument(result.value.documentId))
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSaving = false, error = result.error)
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
