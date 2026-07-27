package com.nla.AIscanerPDF.domain.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Генерация безопасного имени PDF-файла из названия документа. */
object PdfFileNameGenerator {

    private val forbidden = Regex("[\\\\/:*?\"<>|\\n\\r\\t]")
    private const val MAX_BASE_LENGTH = 60

    fun generate(documentName: String?, timestampMillis: Long, fallbackName: String = "Документ"): String {
        val base = sanitize(documentName, fallbackName)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMillis))
        return "${base}_$date.pdf"
    }

    fun sanitize(name: String?, fallbackName: String = "Документ"): String {
        val cleaned = (name ?: "")
            .replace(forbidden, "_")
            .trim()
            .trim('.')
            .take(MAX_BASE_LENGTH)
        return cleaned.ifBlank { fallbackName }
    }
}
