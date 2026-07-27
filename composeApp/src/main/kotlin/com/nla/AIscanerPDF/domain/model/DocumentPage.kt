package com.nla.AIscanerPDF.domain.model

import kotlinx.serialization.Serializable

/**
 * Страница документа. Обработка неразрушающая (п. 5.4 ТЗ):
 * хранится оригинал + параметры (обрезка, поворот, фильтр, яркость, контраст).
 */
data class DocumentPage(
    val id: String,
    val documentId: String,
    val position: Int,
    val originalPath: String,
    val processedPath: String?,
    val previewPath: String?,
    val crop: CropCorners?,
    val rotationDegrees: Int = 0,
    val filter: DocumentFilter = DocumentFilter.ORIGINAL,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val recognizedText: String? = null,
)

enum class DocumentFilter { ORIGINAL, COLOR, ENHANCE, BLACK_WHITE, GRAYSCALE }

/** Точка в нормализованных координатах исходного изображения (0..1). */
@Serializable
data class CropPoint(val x: Float, val y: Float)

/** Углы обрезки относительно исходного изображения, а не экрана (п. 5.3 ТЗ). */
@Serializable
data class CropCorners(
    val topLeft: CropPoint,
    val topRight: CropPoint,
    val bottomRight: CropPoint,
    val bottomLeft: CropPoint,
) {
    fun asList(): List<CropPoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        fun fromList(points: List<CropPoint>): CropCorners {
            require(points.size == 4) { "Ожидаются 4 точки" }
            return CropCorners(points[0], points[1], points[2], points[3])
        }

        /** Углы с отступом от краёв — fallback, если контур не найден (п. 6 ТЗ). */
        fun withInset(inset: Float = 0.04f): CropCorners = CropCorners(
            CropPoint(inset, inset),
            CropPoint(1f - inset, inset),
            CropPoint(1f - inset, 1f - inset),
            CropPoint(inset, 1f - inset),
        )
    }
}
