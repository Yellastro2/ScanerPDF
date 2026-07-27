package com.nla.AIscanerPDF.data.imageprocessing

import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.domain.geometry.QuadGeometry
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.CropPoint

/**
 * Реальный детектор границ документа на OpenCV (без GMS):
 * downscale → grayscale → Gaussian blur → Canny → morphology close →
 * контуры → выпуклые четырёхугольники → оценка (площадь, углы) →
 * лучший кандидат → нормализованные координаты исходника.
 * Fallback с отступами — только если достоверный контур не найден.
 */
class OpenCvDocumentCornerDetector(
    private val dispatchers: DispatchersProvider,
) : DocumentCornerDetector {

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
        val edges = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, CANNY_LOW, CANNY_HIGH)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()

            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )
            hierarchy.release()

            val best = selectBestCandidate(contours, rgba.width(), rgba.height())
            return if (best != null) {
                CornerDetectionResult(
                    corners = best.corners,
                    detected = true,
                    confidence = best.score.coerceIn(0f, 1f),
                )
            } else {
                CornerDetectionResult(
                    corners = CropCorners.withInset(),
                    detected = false,
                    confidence = 0f,
                )
            }
        } finally {
            rgba.release()
            gray.release()
            edges.release()
        }
    }

    private data class Candidate(val corners: CropCorners, val score: Float)

    /** Отбор лучшего достоверного четырёхугольника среди контуров. */
    private fun selectBestCandidate(contours: List<MatOfPoint>, width: Int, height: Int): Candidate? {
        val imageArea = (width * height).toFloat()
        var best: Candidate? = null
        contours.forEach { contour ->
            val candidate = toQuadCandidate(contour, width, height)
            val fraction = Imgproc.contourArea(contour).toFloat() / imageArea
            contour.release()
            if (candidate != null && QuadGeometry.isPlausibleDocument(candidate, fraction)) {
                val score = QuadGeometry.scoreCandidate(candidate, fraction)
                if (score > (best?.score ?: 0f)) best = Candidate(candidate, score)
            }
        }
        return best
    }

    /** Аппроксимирует контур; возвращает выпуклый четырёхугольник или null. */
    private fun toQuadCandidate(contour: MatOfPoint, width: Int, height: Int): CropCorners? {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approx2f = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(contour2f, true)
            Imgproc.approxPolyDP(contour2f, approx2f, APPROX_EPSILON * perimeter, true)
            val points = approx2f.toArray()
            if (points.size != 4) return null
            val approx = MatOfPoint(*points)
            val convex = Imgproc.isContourConvex(approx)
            approx.release()
            if (!convex) return null
            val normalized = points.map {
                CropPoint(
                    (it.x / width).toFloat().coerceIn(0f, 1f),
                    (it.y / height).toFloat().coerceIn(0f, 1f),
                )
            }
            return QuadGeometry.orderCorners(normalized)
        } finally {
            contour2f.release()
            approx2f.release()
        }
    }

    private companion object {
        const val ANALYSIS_MAX_DIMENSION = 1200
        const val CANNY_LOW = 50.0
        const val CANNY_HIGH = 150.0
        const val APPROX_EPSILON = 0.02
    }
}
