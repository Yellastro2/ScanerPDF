package com.nla.AIscanerPDF.data.export

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.nla.AIscanerPDF.data.imageprocessing.BitmapLoader
import com.nla.AIscanerPDF.domain.model.DocumentPage
import com.nla.AIscanerPDF.domain.model.ExportQuality
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.model.PdfMargins
import com.nla.AIscanerPDF.domain.model.PdfPageSize
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Многостраничный PDF через android.graphics.pdf.PdfDocument (без GMS).
 * Размер страницы: авто (по изображению) или A4; поля: нет/небольшие;
 * качество управляет размером растра (п. 8 ТЗ).
 */
class PdfExporter {

    fun export(pages: List<DocumentPage>, options: PdfExportOptions, outFile: File) {
        require(pages.isNotEmpty()) { "Документ не содержит страниц" }
        val pdf = PdfDocument()
        try {
            pages.sortedBy { it.position }.forEachIndexed { index, page ->
                val path = page.processedPath ?: page.originalPath
                val bitmap = BitmapLoader.decodeSampled(path, maxDimensionFor(options.quality))
                try {
                    val (pageWidth, pageHeight) = pageSizeFor(options.pageSize, bitmap)
                    val margin = marginFor(options.margins, min(pageWidth, pageHeight))
                    val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val pdfPage = pdf.startPage(info)

                    val availW = pageWidth - margin * 2f
                    val availH = pageHeight - margin * 2f
                    val scale = min(availW / bitmap.width, availH / bitmap.height)
                    val drawW = bitmap.width * scale
                    val drawH = bitmap.height * scale
                    val left = margin + (availW - drawW) / 2f
                    val top = margin + (availH - drawH) / 2f

                    val canvas = pdfPage.canvas
                    canvas.save()
                    canvas.translate(left, top)
                    canvas.scale(scale, scale)
                    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                    canvas.restore()
                    pdf.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(outFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private fun maxDimensionFor(quality: ExportQuality): Int = when (quality) {
        ExportQuality.LOW -> 1000
        ExportQuality.MEDIUM -> 1600
        ExportQuality.HIGH -> 3200
    }

    /** Размер страницы в пунктах (1/72 дюйма). A4 = 595 x 842 pt. */
    private fun pageSizeFor(size: PdfPageSize, bitmap: Bitmap): Pair<Int, Int> = when (size) {
        PdfPageSize.A4 -> if (bitmap.width <= bitmap.height) 595 to 842 else 842 to 595
        PdfPageSize.AUTO -> {
            val longSide = 842f
            val scale = longSide / maxOf(bitmap.width, bitmap.height)
            (bitmap.width * scale).roundToInt().coerceAtLeast(72) to
                (bitmap.height * scale).roundToInt().coerceAtLeast(72)
        }
    }

    private fun marginFor(margins: PdfMargins, shortSide: Int): Int = when (margins) {
        PdfMargins.NONE -> 0
        PdfMargins.SMALL -> (shortSide * 0.035f).roundToInt()
    }
}
