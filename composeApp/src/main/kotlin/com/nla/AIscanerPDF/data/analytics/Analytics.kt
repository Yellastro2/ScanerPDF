package com.nla.AIscanerPDF.data.analytics

import android.util.Log

/**
 * Собственный интерфейс аналитики (п. 12 ТЗ). Бизнес-логика не зависит от SDK.
 * В аналитику не передаются: содержимое OCR, названия документов, реквизиты,
 * изображения, персональные данные — события содержат только имя.
 */
fun interface Analytics {
    fun logEvent(event: AnalyticsEvent)
}

enum class AnalyticsEvent(val eventName: String) {
    APP_OPENED("app_opened"),
    SCAN_STARTED("scan_started"),
    PHOTO_CAPTURED("photo_captured"),
    CORNERS_ADJUSTED("corners_adjusted"),
    PAGE_SAVED("page_saved"),
    DOCUMENT_CREATED("document_created"),
    PDF_EXPORT_STARTED("pdf_export_started"),
    PDF_EXPORT_COMPLETED("pdf_export_completed"),
    OCR_STARTED("ocr_started"),
    OCR_COMPLETED("ocr_completed"),
    OCR_FAILED("ocr_failed"),
    AI_SUMMARY_STARTED("ai_summary_started"),
    AI_SUMMARY_COMPLETED("ai_summary_completed"),
    AI_EXTRACTION_STARTED("ai_extraction_started"),
    CONTRACT_ANALYSIS_STARTED("contract_analysis_started"),
    PAYWALL_SHOWN("paywall_shown"),
    PURCHASE_STARTED("purchase_started"),
    PURCHASE_COMPLETED("purchase_completed"),
    PURCHASE_FAILED("purchase_failed"),
}

/** Debug-реализация: пишет только имя события, без каких-либо данных. */
class DebugAnalytics : Analytics {
    override fun logEvent(event: AnalyticsEvent) {
        Log.d("Analytics", event.eventName)
    }
}
