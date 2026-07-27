package com.nla.AIscanerPDF.data.imageprocessing

import android.graphics.Bitmap
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.DocumentFilter

/** Ссылка на исходное изображение — путь к файлу, а не Bitmap (п. 16 ТЗ). */
data class SourceImage(val path: String)

data class ProcessedImage(val path: String, val width: Int, val height: Int)

/**
 * Модуль обработки изображений (п. 6 ТЗ). Все методы выполняются вне Main Thread.
 */
interface DocumentImageProcessor {
    suspend fun detectDocumentCorners(image: SourceImage): CornerDetectionResult

    suspend fun cropAndCorrectPerspective(image: SourceImage, corners: CropCorners): Bitmap

    suspend fun applyFilter(
        bitmap: Bitmap,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
    ): Bitmap

    suspend fun rotate(bitmap: Bitmap, degrees: Int): Bitmap

    suspend fun createPreview(bitmap: Bitmap, maxSize: Int): Bitmap
}

/**
 * Детектор границ документа. Реализация на OpenCV (Canny → контуры →
 * крупнейший четырёхугольник) подключается на Этапе 3 без изменения клиентов.
 */
interface DocumentCornerDetector {
    suspend fun detect(image: SourceImage): CornerDetectionResult

    /** Детекция по уже загруженному кадру (живой контур на камере). */
    suspend fun detectInBitmap(bitmap: Bitmap): CornerDetectionResult
}
