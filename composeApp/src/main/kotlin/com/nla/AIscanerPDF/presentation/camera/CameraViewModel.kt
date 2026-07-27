package com.nla.AIscanerPDF.presentation.camera

import android.graphics.Bitmap
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
import com.nla.AIscanerPDF.data.imageprocessing.DocumentCornerDetector
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.repository.DocumentRepository
import com.nla.AIscanerPDF.domain.repository.ImageProcessingRepository
import com.nla.AIscanerPDF.domain.repository.SettingsRepository
import com.nla.AIscanerPDF.domain.usecase.AddPageToDocumentUseCase
import com.nla.AIscanerPDF.domain.usecase.CreateDocumentUseCase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class CameraUiState(
    val documentId: String? = null,
    val pageCount: Int = 0,
    val torchEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val autoDetectEnabled: Boolean = true,
    /** Контур из живого анализа кадров (нормализованные координаты кадра). */
    val liveCorners: CropCorners? = null,
    val documentFound: Boolean = false,
    /** Размер проанализированного кадра — для проекции контура на PreviewView. */
    val analyzedWidth: Int = 0,
    val analyzedHeight: Int = 0,
    val error: AppError? = null,
)

sealed interface CameraUiEffect {
    data class OpenCrop(val pageId: String) : CameraUiEffect
    data class OpenDocument(val documentId: String) : CameraUiEffect
}

class CameraViewModel(
    savedStateHandle: SavedStateHandle,
    private val documents: DocumentRepository,
    private val createDocument: CreateDocumentUseCase,
    private val addPage: AddPageToDocumentUseCase,
    private val imageProcessing: ImageProcessingRepository,
    private val cornerDetector: DocumentCornerDetector,
    private val settings: SettingsRepository,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CameraUiState(documentId = savedStateHandle.get<String>("documentId")),
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _effects = Channel<CameraUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Защита от параллельных снимков при частом нажатии затвора. */
    private val captureInProgress = AtomicBoolean(false)

    /** Живой анализ: не чаще одного кадра за ANALYSIS_INTERVAL_MS и не параллельно. */
    private val frameAnalysisBusy = AtomicBoolean(false)
    @Volatile
    private var lastAnalysisAt = 0L

    /**
     * Архитектура автоснимка: при стабильной детекции N кадров подряд можно
     * инициировать съёмку. В этой версии автоснимок выключен по умолчанию.
     */
    private val autoCaptureEnabled = false
    private var stableDetections = 0

    init {
        _state.value.documentId?.let { id ->
            viewModelScope.launch {
                val doc = documents.getDocument(id)
                _state.update { it.copy(pageCount = doc?.pages?.size ?: 0) }
            }
        }
        viewModelScope.launch {
            val enabled = settings.settings.first().autoDetectCorners
            _state.update { it.copy(autoDetectEnabled = enabled) }
        }
    }

    /** true, если этот кадр стоит анализировать (троттлинг + занятость). */
    fun shouldAnalyzeFrame(): Boolean {
        if (!_state.value.autoDetectEnabled || _state.value.isSaving) return false
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < ANALYSIS_INTERVAL_MS) return false
        return !frameAnalysisBusy.get()
    }

    /** Принимает кадр анализа (уже повёрнутый по rotationDegrees). Bitmap освобождается здесь. */
    fun onAnalysisFrame(bitmap: Bitmap) {
        if (!frameAnalysisBusy.compareAndSet(false, true)) {
            bitmap.recycle()
            return
        }
        lastAnalysisAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val result = cornerDetector.detectInBitmap(bitmap)
                stableDetections = if (result.detected) stableDetections + 1 else 0
                _state.update {
                    it.copy(
                        liveCorners = if (result.detected) result.corners else null,
                        documentFound = result.detected,
                        analyzedWidth = bitmap.width,
                        analyzedHeight = bitmap.height,
                    )
                }
                if (autoCaptureEnabled && stableDetections >= AUTO_CAPTURE_STABLE_FRAMES) {
                    stableDetections = 0
                    // Точка расширения: инициировать съёмку без нажатия затвора
                }
            } finally {
                bitmap.recycle()
                frameAnalysisBusy.set(false)
            }
        }
    }

    fun toggleAutoDetect() {
        val enabled = !_state.value.autoDetectEnabled
        _state.update {
            it.copy(
                autoDetectEnabled = enabled,
                liveCorners = if (enabled) it.liveCorners else null,
                documentFound = enabled && it.documentFound,
            )
        }
        viewModelScope.launch { settings.setAutoDetectCorners(enabled) }
    }

    fun toggleTorch() = _state.update { it.copy(torchEnabled = !it.torchEnabled) }

    /** Атомарно резервирует съёмку; false — снимок уже выполняется. */
    fun tryBeginCapture(): Boolean {
        if (!captureInProgress.compareAndSet(false, true)) return false
        _state.update { it.copy(isSaving = true) }
        return true
    }

    /** Файл для следующего снимка; документ создаётся при первом кадре. */
    suspend fun prepareCaptureFilePath(): String {
        val documentId = ensureDocument()
        return documents.newOriginalFilePath(documentId)
    }

    fun onCaptureError() {
        captureInProgress.set(false)
        _state.update { it.copy(error = AppError.CaptureFailed, isSaving = false) }
    }

    fun onCameraError() = _state.update { it.copy(error = AppError.CameraUnavailable) }

    fun consumeError() = _state.update { it.copy(error = null) }

    fun onPhotoSaved(originalPath: String) {
        viewModelScope.launch {
            analytics.logEvent(AnalyticsEvent.PHOTO_CAPTURED)
            try {
                val documentId = ensureDocument()
                val detection = imageProcessing.detectCorners(originalPath)
                val corners = (detection as? AppResult.Success)?.value?.corners
                val page = addPage(documentId, originalPath, corners)
                _state.update { it.copy(isSaving = false, pageCount = it.pageCount + 1) }
                _effects.send(CameraUiEffect.OpenCrop(page.id))
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = AppError.CaptureFailed) }
            } finally {
                captureInProgress.set(false)
            }
        }
    }

    fun onOpenDocument() {
        val id = _state.value.documentId ?: return
        viewModelScope.launch { _effects.send(CameraUiEffect.OpenDocument(id)) }
    }

    private suspend fun ensureDocument(): String {
        _state.value.documentId?.let { return it }
        val name = "Скан " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
        val document = createDocument(name)
        analytics.logEvent(AnalyticsEvent.DOCUMENT_CREATED)
        _state.update { it.copy(documentId = document.id) }
        return document.id
    }

    private companion object {
        const val ANALYSIS_INTERVAL_MS = 350L
        const val AUTO_CAPTURE_STABLE_FRAMES = 8
    }
}
