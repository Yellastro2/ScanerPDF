package com.nla.AIscanerPDF.presentation.crop

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
import com.nla.AIscanerPDF.domain.geometry.QuadValidator
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.CropPoint
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ImageProcessingRepository
import com.nla.AIscanerPDF.domain.usecase.DeletePageUseCase
import com.nla.AIscanerPDF.domain.usecase.UpdatePageCropUseCase

data class CropUiState(
    val pageId: String = "",
    val documentId: String? = null,
    val imagePath: String? = null,
    val corners: CropCorners = CropCorners.withInset(),
    val isValidShape: Boolean = true,
    val isApplying: Boolean = false,
    val isDetecting: Boolean = false,
    val autoDetected: Boolean = false,
    val error: AppError? = null,
)

sealed interface CropUiEffect {
    data class OpenEditor(val pageId: String) : CropUiEffect
    data class RetakeToCamera(val documentId: String) : CropUiEffect
    data object NavigateBack : CropUiEffect
}

/**
 * Углы хранятся в нормализованных координатах исходного изображения
 * (0..1), а не экрана (п. 5.3 ТЗ). UI переводит их в пиксели области
 * отображения самостоятельно.
 */
class CropViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val imageProcessing: ImageProcessingRepository,
    private val updatePageCrop: UpdatePageCropUseCase,
    private val deletePage: DeletePageUseCase,
    private val analytics: Analytics,
) : ViewModel() {

    private val pageId: String = checkNotNull(savedStateHandle["pageId"])

    private val _state = MutableStateFlow(CropUiState(pageId = pageId))
    val state: StateFlow<CropUiState> = _state.asStateFlow()

    private val _effects = Channel<CropUiEffect>(Channel.BUFFERED)
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
                        imagePath = page.originalPath,
                        corners = page.crop ?: CropCorners.withInset(),
                    )
                }
            }
        }
    }

    fun onCornerMoved(index: Int, newPoint: CropPoint) {
        _state.update { current ->
            val points = current.corners.asList().toMutableList()
            points[index] = CropPoint(newPoint.x.coerceIn(0f, 1f), newPoint.y.coerceIn(0f, 1f))
            val corners = CropCorners.fromList(points)
            current.copy(corners = corners, isValidShape = QuadValidator.isValid(corners))
        }
    }

    fun onDragFinished() = analytics.logEvent(AnalyticsEvent.CORNERS_ADJUSTED)

    fun onReset() {
        _state.update { it.copy(corners = CropCorners.withInset(), isValidShape = true, autoDetected = false) }
    }

    fun onAutoDetect() {
        val path = _state.value.imagePath ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDetecting = true) }
            when (val result = imageProcessing.detectCorners(path)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        isDetecting = false,
                        corners = result.value.corners,
                        isValidShape = QuadValidator.isValid(result.value.corners),
                        autoDetected = result.value.detected,
                        error = if (result.value.detected) null else AppError.CornersNotFound,
                    )
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isDetecting = false, error = result.error)
                }
            }
        }
    }

    fun onApply() {
        val current = _state.value
        if (!current.isValidShape) {
            _state.update { it.copy(error = AppError.InvalidCropShape) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true) }
            when (val result = updatePageCrop(pageId, current.corners)) {
                is AppResult.Success -> _effects.send(CropUiEffect.OpenEditor(pageId))
                is AppResult.Failure -> _state.update {
                    it.copy(isApplying = false, error = result.error)
                }
            }
        }
    }

    /** Переснять: удалить страницу и вернуться в камеру того же документа. */
    fun onRetake() {
        viewModelScope.launch {
            val documentId = _state.value.documentId
            deletePage(pageId)
            if (documentId != null) {
                _effects.send(CropUiEffect.RetakeToCamera(documentId))
            } else {
                _effects.send(CropUiEffect.NavigateBack)
            }
        }
    }

    fun consumeError() = _state.update { it.copy(error = null) }
}
