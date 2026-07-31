package com.nla.AIscanerPDF.data.imageprocessing

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.CropCorners

/**
 * Строит маски документа и передаёт найденные контуры общему извлекателю
 * четырёхугольников. Светлая маска запускается только при провале Canny.
 */
internal class OpenCvDocumentDetectionPipeline(
    private val candidateExtractor: OpenCvQuadCandidateExtractor = OpenCvQuadCandidateExtractor(),
) {

    fun detect(gray: Mat): CornerDetectionResult {
        val cannyCandidate = detectWithCanny(gray)
        val best = cannyCandidate ?: detectLightRegion(gray)
        return best?.toResult() ?: notFoundResult()
    }

    private fun detectWithCanny(gray: Mat): OpenCvQuadCandidate? {
        val blurred = Mat()
        val edges = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, CANNY_CLOSE_KERNEL)
        try {
            Imgproc.GaussianBlur(gray, blurred, BLUR_KERNEL, 0.0)
            Imgproc.Canny(blurred, edges, CANNY_LOW, CANNY_HIGH)
            Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
            return findBestCandidate(edges, Imgproc.RETR_LIST, gray.width(), gray.height())
        } finally {
            blurred.release()
            edges.release()
            kernel.release()
        }
    }

    private fun detectLightRegion(gray: Mat): OpenCvQuadCandidate? {
        val blurred = Mat()
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, REGION_CLOSE_KERNEL)
        try {
            Imgproc.GaussianBlur(gray, blurred, BLUR_KERNEL, 0.0)
            Imgproc.threshold(
                blurred,
                mask,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
            )
            Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
            return findBestCandidate(mask, Imgproc.RETR_EXTERNAL, gray.width(), gray.height())
        } finally {
            blurred.release()
            mask.release()
            kernel.release()
        }
    }

    private fun findBestCandidate(
        mask: Mat,
        retrievalMode: Int,
        width: Int,
        height: Int,
    ): OpenCvQuadCandidate? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        return try {
            Imgproc.findContours(
                mask,
                contours,
                hierarchy,
                retrievalMode,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )
            contours
                .sortedByDescending { contour -> Imgproc.contourArea(contour) }
                .take(MAX_CONTOURS_TO_CHECK)
                .mapNotNull { candidateExtractor.extract(it, width, height) }
                .maxByOrNull(OpenCvQuadCandidate::score)
        } finally {
            hierarchy.release()
            contours.forEach(MatOfPoint::release)
        }
    }

    private fun OpenCvQuadCandidate.toResult(): CornerDetectionResult =
        CornerDetectionResult(
            corners = corners,
            detected = true,
            confidence = score.coerceIn(0f, 1f),
        )

    private fun notFoundResult(): CornerDetectionResult =
        CornerDetectionResult(
            corners = CropCorners.withInset(),
            detected = false,
            confidence = 0f,
        )

    private companion object {
        val BLUR_KERNEL = Size(5.0, 5.0)
        val CANNY_CLOSE_KERNEL = Size(5.0, 5.0)
        val REGION_CLOSE_KERNEL = Size(7.0, 7.0)
        const val CANNY_LOW = 50.0
        const val CANNY_HIGH = 150.0
        const val MAX_CONTOURS_TO_CHECK = 15
    }
}
