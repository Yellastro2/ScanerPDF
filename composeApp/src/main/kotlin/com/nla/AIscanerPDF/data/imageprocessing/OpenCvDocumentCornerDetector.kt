package com.nla.AIscanerPDF.data.imageprocessing

import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult

/**
 * Загружает изображение и передаёт одноканальную матрицу в OpenCV-пайплайн.
 * Детали построения масок и отбора четырёхугольников вынесены отдельно.
 */
class OpenCvDocumentCornerDetector(
    private val dispatchers: DispatchersProvider,
) : DocumentCornerDetector {

    private val pipeline = OpenCvDocumentDetectionPipeline()

    override suspend fun detect(image: SourceImage): CornerDetectionResult =
        withContext(dispatchers.default) {
            val bitmap = BitmapLoader.decodeSampled(image.path, ANALYSIS_MAX_DIMENSION)
            try {
                detectInBitmap(bitmap)
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun detectInBitmap(bitmap: Bitmap): CornerDetectionResult =
        withContext(dispatchers.default) { runDetection(bitmap) }

    private fun runDetection(bitmap: Bitmap): CornerDetectionResult {
        val rgba = Mat()
        val gray = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            return pipeline.detect(gray)
        } finally {
            rgba.release()
            gray.release()
        }
    }

    private companion object {
        const val ANALYSIS_MAX_DIMENSION = 1200
    }
}
