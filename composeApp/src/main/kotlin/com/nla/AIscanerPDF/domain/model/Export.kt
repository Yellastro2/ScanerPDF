package com.nla.AIscanerPDF.domain.model

enum class PdfPageSize { AUTO, A4 }
enum class PdfMargins { NONE, SMALL }
enum class ExportQuality { LOW, MEDIUM, HIGH }

data class PdfExportOptions(
    val pageSize: PdfPageSize = PdfPageSize.AUTO,
    val margins: PdfMargins = PdfMargins.NONE,
    val quality: ExportQuality = ExportQuality.MEDIUM,
    val fileName: String? = null,
)

data class ExportedFile(val path: String, val mimeType: String)
