package com.nla.AIscanerPDF.data.imageprocessing

import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc
import com.nla.AIscanerPDF.domain.geometry.QuadGeometry
import com.nla.AIscanerPDF.domain.geometry.QuadValidator
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.CropPoint

internal data class OpenCvQuadCandidate(
    val corners: CropCorners,
    val score: Float,
)

/** Восстанавливает выпуклый четырёхугольник из неровного OpenCV-контура. */
internal class OpenCvQuadCandidateExtractor {

    fun extract(contour: MatOfPoint, width: Int, height: Int): OpenCvQuadCandidate? {
        val hull = convexHull(contour)
        val imageArea = width.toDouble() * height
        val areaFraction = (Imgproc.contourArea(hull) / imageArea).toFloat()
        if (areaFraction < QuadGeometry.MIN_AREA_FRACTION) {
            hull.release()
            return null
        }

        return try {
            approximateCandidates(hull, width, height, areaFraction)
                .filterNot { looksLikeImageFrame(it.corners) }
                .maxByOrNull(OpenCvQuadCandidate::score)
        } finally {
            hull.release()
        }
    }

    private fun approximateCandidates(
        hull: MatOfPoint,
        width: Int,
        height: Int,
        areaFraction: Float,
    ): List<OpenCvQuadCandidate> {
        val hull2f = MatOfPoint2f(*hull.toArray())
        return try {
            val perimeter = Imgproc.arcLength(hull2f, true)
            buildList {
                APPROX_EPSILONS.forEach { epsilon ->
                    val candidate = approximateAt(
                        hull2f,
                        perimeter,
                        epsilon,
                        width,
                        height,
                        areaFraction,
                    )
                    if (candidate != null) add(candidate)
                }
            }
        } finally {
            hull2f.release()
        }
    }

    private fun approximateAt(
        hull: MatOfPoint2f,
        perimeter: Double,
        epsilon: Double,
        width: Int,
        height: Int,
        areaFraction: Float,
    ): OpenCvQuadCandidate? {
        val approximation = MatOfPoint2f()
        try {
            Imgproc.approxPolyDP(hull, approximation, epsilon * perimeter, true)
            val points = approximation.toArray()
            if (points.size != CORNER_COUNT) return null

            val polygon = MatOfPoint(*points)
            val convex = try {
                Imgproc.isContourConvex(polygon)
            } finally {
                polygon.release()
            }
            if (!convex) return null

            val normalized = points.map { point ->
                CropPoint(
                    x = (point.x / width).toFloat().coerceIn(0f, 1f),
                    y = (point.y / height).toFloat().coerceIn(0f, 1f),
                )
            }
            val corners = QuadGeometry.orderCorners(normalized)
            if (!QuadGeometry.isPlausibleDocument(corners, areaFraction)) return null

            return OpenCvQuadCandidate(
                corners = corners,
                score = QuadGeometry.scoreCandidate(corners, areaFraction),
            )
        } finally {
            approximation.release()
        }
    }

    private fun convexHull(contour: MatOfPoint): MatOfPoint {
        val sourcePoints = contour.toArray()
        val indices = MatOfInt()
        return try {
            Imgproc.convexHull(contour, indices)
            val hullPoints = indices.toArray()
                .map { index -> sourcePoints[index] }
                .toTypedArray()
            MatOfPoint(*hullPoints)
        } finally {
            indices.release()
        }
    }

    private fun looksLikeImageFrame(corners: CropCorners): Boolean {
        val points = corners.asList()
        val nearBorderCount = points.count { point ->
            point.x < BORDER_MARGIN ||
                point.x > 1f - BORDER_MARGIN ||
                point.y < BORDER_MARGIN ||
                point.y > 1f - BORDER_MARGIN
        }
        return QuadValidator.area(points) > MAX_FRAME_AREA &&
            nearBorderCount >= MIN_BORDER_CORNERS
    }

    private companion object {
        val APPROX_EPSILONS = doubleArrayOf(0.015, 0.02, 0.03, 0.04)
        const val CORNER_COUNT = 4
        const val BORDER_MARGIN = 0.015f
        const val MAX_FRAME_AREA = 0.92f
        const val MIN_BORDER_CORNERS = 3
    }
}
