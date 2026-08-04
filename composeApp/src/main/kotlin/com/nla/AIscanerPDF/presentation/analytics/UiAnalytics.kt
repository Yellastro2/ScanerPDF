package com.nla.AIscanerPDF.presentation.analytics

import io.appmetrica.analytics.AppMetrica

fun reportButtonClick(buttonName: String) {
    AppMetrica.reportEvent("Нажата $buttonName")
}
