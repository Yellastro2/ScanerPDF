package com.nla.AIscanerPDF.data.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * Загрузка больших изображений с учётом памяти (п. 6, 16 ТЗ):
 * поэтапный inSampleSize, повторные попытки при OutOfMemoryError
 * и применение EXIF-ориентации, чтобы координаты обрезки и рендер
 * всегда работали в отображаемой ориентации кадра.
 */
object BitmapLoader {

    fun decodeSampled(path: String, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Не удалось прочитать изображение" }

        var sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
        var attempts = 0
        while (true) {
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = BitmapFactory.decodeFile(path, options)
                    ?: error("Не удалось декодировать изображение")
                return applyExifOrientation(path, decoded)
            } catch (e: OutOfMemoryError) {
                attempts++
                sampleSize *= 2
                if (attempts > MAX_OOM_RETRIES) throw e
            }
        }
    }

    fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (maxOf(w, h) > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }
        return sampleSize
    }

    /** Поворачивает/отражает Bitmap согласно EXIF; исходник recycle-ится при замене. */
    fun applyExifOrientation(path: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private const val MAX_OOM_RETRIES = 4
}
