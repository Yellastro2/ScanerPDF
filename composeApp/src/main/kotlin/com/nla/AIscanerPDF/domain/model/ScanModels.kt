package com.nla.AIscanerPDF.domain.model

/** Результат съёмки: путь к сохранённому оригиналу. */
data class ScanResult(
    val documentId: String,
    val pageId: String,
    val originalPath: String,
)

data class CornerDetectionResult(
    val corners: CropCorners,
    val detected: Boolean,
    val confidence: Float,
)

data class RecognizedText(
    val documentId: String,
    val pages: List<RecognizedPage>,
) {
    val fullText: String get() = pages.joinToString("\n\n") { it.text }
}

data class RecognizedPage(
    val pageId: String,
    val pageNumber: Int,
    val text: String,
)

sealed interface OcrProgress {
    data class PageInProgress(val current: Int, val total: Int) : OcrProgress
    data class Completed(val result: RecognizedText) : OcrProgress
}
