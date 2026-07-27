package com.nla.AIscanerPDF.data.imageprocessing

import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.CropCorners

/**
 * Fallback-детектор: если надёжный контур не найден (или OpenCV ещё
 * не подключён), возвращаем углы с отступом от краёв и detected=false —
 * пользователь корректирует вручную (п. 6 ТЗ).
 */
class FallbackCornerDetector : DocumentCornerDetector {
    override suspend fun detect(image: SourceImage): CornerDetectionResult = result()

    override suspend fun detectInBitmap(bitmap: android.graphics.Bitmap): CornerDetectionResult = result()

    private fun result() = CornerDetectionResult(
        corners = CropCorners.withInset(),
        detected = false,
        confidence = 0f,
    )
}
