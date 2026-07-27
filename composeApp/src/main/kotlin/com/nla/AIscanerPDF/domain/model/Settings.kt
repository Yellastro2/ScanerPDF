package com.nla.AIscanerPDF.domain.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultQuality: ExportQuality = ExportQuality.MEDIUM,
    val defaultPageSize: PdfPageSize = PdfPageSize.AUTO,
    val ocrLanguage: String = "ru+en",
    val autoDetectCorners: Boolean = true,
    val aiConsentGiven: Boolean = false,
    val usedOcrOperations: Int = 0,
    val usedAiOperations: Int = 0,
)
