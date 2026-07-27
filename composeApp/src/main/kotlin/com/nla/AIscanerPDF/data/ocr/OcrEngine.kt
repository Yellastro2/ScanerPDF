package com.nla.AIscanerPDF.data.ocr

/**
 * Интерфейс офлайн OCR-движка (п. 3 ТЗ). Позволяет подключить PaddleOCR
 * или Tesseract без переделки остального приложения.
 */
interface OcrEngine {
    val isAvailable: Boolean
    val supportedLanguages: Set<String>

    /** Распознаёт текст изображения. Бросает исключение при ошибке движка. */
    suspend fun recognize(imagePath: String, language: String): String
}
