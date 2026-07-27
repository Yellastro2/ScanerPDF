package com.nla.AIscanerPDF.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size as CvSize
import org.opencv.imgproc.Imgproc
import com.nla.AIscanerPDF.core.DispatchersProvider
import com.nla.AIscanerPDF.domain.model.CornerDetectionResult
import com.nla.AIscanerPDF.domain.model.CropCorners
import com.nla.AIscanerPDF.domain.model.DocumentFilter
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Реализация на Android graphics. Perspective transform выполняется через
 * Matrix.setPolyToPoly (проективное преобразование по 4 точкам).
 * OpenCV-детектор подключается через [DocumentCornerDetector].
 */
class AndroidDocumentImageProcessor(
    private val cornerDetector: DocumentCornerDetector,
    private val dispatchers: DispatchersProvider,
) : DocumentImageProcessor {

    override suspend fun detectDocumentCorners(image: SourceImage): CornerDetectionResult =
        cornerDetector.detect(image)

    override suspend fun cropAndCorrectPerspective(
        image: SourceImage,
        corners: CropCorners,
    ): Bitmap = withContext(dispatchers.default) {
        val source = BitmapLoader.decodeSampled(image.path, MAX_PROCESSING_DIMENSION)
        try {
            val w = source.width.toFloat()
            val h = source.height.toFloat()
            val tl = corners.topLeft
            val tr = corners.topRight
            val br = corners.bottomRight
            val bl = corners.bottomLeft

            val topWidth = hypot((tr.x - tl.x) * w, (tr.y - tl.y) * h)
            val bottomWidth = hypot((br.x - bl.x) * w, (br.y - bl.y) * h)
            val leftHeight = hypot((bl.x - tl.x) * w, (bl.y - tl.y) * h)
            val rightHeight = hypot((br.x - tr.x) * w, (br.y - tr.y) * h)

            val dstWidth = ((topWidth + bottomWidth) / 2f).roundToInt().coerceAtLeast(32)
            val dstHeight = ((leftHeight + rightHeight) / 2f).roundToInt().coerceAtLeast(32)

            val src = floatArrayOf(
                tl.x * w, tl.y * h,
                tr.x * w, tr.y * h,
                br.x * w, br.y * h,
                bl.x * w, bl.y * h,
            )
            val dst = floatArrayOf(
                0f, 0f,
                dstWidth.toFloat(), 0f,
                dstWidth.toFloat(), dstHeight.toFloat(),
                0f, dstHeight.toFloat(),
            )
            val matrix = Matrix()
            check(matrix.setPolyToPoly(src, 0, dst, 0, 4)) { "Некорректное преобразование" }

            val result = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
            Canvas(result).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            result
        } finally {
            source.recycle()
        }
    }

    override suspend fun applyFilter(
        bitmap: Bitmap,
        filter: DocumentFilter,
        brightness: Float,
        contrast: Float,
    ): Bitmap = withContext(dispatchers.default) {
        // Ч/Б и «Улучшение» рендерятся через OpenCV: выравнивание неравномерного
        // освещения (деление на размытый фон) и adaptive threshold дают читаемый
        // мелкий текст и убирают тени. Живое превью в UI остаётся на ColorMatrix.
        val base = when (filter) {
            DocumentFilter.BLACK_WHITE -> openCvBlackWhite(bitmap)
            DocumentFilter.ENHANCE -> openCvIlluminationNormalized(bitmap)
            else -> bitmap
        }
        val matrix = buildColorMatrix(
            if (filter == DocumentFilter.BLACK_WHITE) DocumentFilter.ORIGINAL else filter,
            brightness,
            contrast,
        )
        val result = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        Canvas(result).drawBitmap(base, 0f, 0f, paint)
        if (base != bitmap) base.recycle()
        result
    }

    /** Выравнивание освещения: gray / GaussianBlur(gray) — убирает тени. */
    private fun openCvIlluminationNormalized(bitmap: Bitmap): Bitmap {
        val src = Mat()
        val gray = Mat()
        val background = Mat()
        val normalized = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, background, CvSize(0.0, 0.0), 25.0)
            Core.divide(gray, background, normalized, 255.0)
            Imgproc.cvtColor(normalized, src, Imgproc.COLOR_GRAY2RGBA)
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(src, out)
            return out
        } finally {
            src.release()
            gray.release()
            background.release()
            normalized.release()
        }
    }

    /** Ч/Б документ: выравнивание освещения + adaptive threshold. */
    private fun openCvBlackWhite(bitmap: Bitmap): Bitmap {
        val src = Mat()
        val gray = Mat()
        val background = Mat()
        val normalized = Mat()
        val binary = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, background, CvSize(0.0, 0.0), 25.0)
            Core.divide(gray, background, normalized, 255.0)
            Imgproc.adaptiveThreshold(
                normalized,
                binary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                ADAPTIVE_BLOCK_SIZE,
                ADAPTIVE_C,
            )
            Imgproc.cvtColor(binary, src, Imgproc.COLOR_GRAY2RGBA)
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(src, out)
            return out
        } finally {
            src.release()
            gray.release()
            background.release()
            normalized.release()
            binary.release()
        }
    }

    override suspend fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        withContext(dispatchers.default) {
            val normalized = ((degrees % 360) + 360) % 360
            if (normalized == 0) return@withContext bitmap
            val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

    override suspend fun createPreview(bitmap: Bitmap, maxSize: Int): Bitmap =
        withContext(dispatchers.default) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale >= 1f) return@withContext bitmap
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }

    companion object {
        const val MAX_PROCESSING_DIMENSION = 4000
        const val ADAPTIVE_BLOCK_SIZE = 31
        const val ADAPTIVE_C = 15.0

        /** Матрица цвета для фильтра + яркости (-1..1) + контраста (0.25..3). */
        fun buildColorMatrix(filter: DocumentFilter, brightness: Float, contrast: Float): ColorMatrix {
            val cm = ColorMatrix()
            when (filter) {
                DocumentFilter.ORIGINAL -> Unit
                DocumentFilter.COLOR -> {
                    cm.setSaturation(1.15f)
                    cm.postConcat(contrastMatrix(1.1f))
                }
                DocumentFilter.ENHANCE -> {
                    cm.postConcat(contrastMatrix(1.3f))
                    cm.postConcat(brightnessMatrix(0.06f))
                }
                DocumentFilter.GRAYSCALE -> cm.setSaturation(0f)
                DocumentFilter.BLACK_WHITE -> {
                    cm.setSaturation(0f)
                    cm.postConcat(contrastMatrix(2.4f))
                }
            }
            if (contrast != 1f) cm.postConcat(contrastMatrix(contrast))
            if (brightness != 0f) cm.postConcat(brightnessMatrix(brightness))
            return cm
        }

        private fun contrastMatrix(scale: Float): ColorMatrix {
            val translate = (-0.5f * scale + 0.5f) * 255f
            return ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, translate,
                    0f, scale, 0f, 0f, translate,
                    0f, 0f, scale, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }

        private fun brightnessMatrix(brightness: Float): ColorMatrix {
            val offset = brightness * 255f
            return ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, offset,
                    0f, 1f, 0f, 0f, offset,
                    0f, 0f, 1f, 0f, offset,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
    }
}
