package com.nla.AIscanerPDF.domain.model

sealed interface SubscriptionStatus {
    data object Free : SubscriptionStatus
    data class Premium(val expiresAtMillis: Long?) : SubscriptionStatus
}

data class SubscriptionProduct(
    val productId: String,
    val title: String,
    val price: String,
    val period: SubscriptionPeriod,
)

enum class SubscriptionPeriod { MONTHLY, YEARLY, ONE_TIME }

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Error(val message: String?) : PurchaseResult
}

sealed interface RestoreResult {
    data class Success(val restored: Boolean) : RestoreResult
    data class Error(val message: String?) : RestoreResult
}

/** Ограничения бесплатной версии задаются конфигурацией (п. 11 ТЗ). */
data class FreePlanLimits(
    val maxPagesPerPdf: Int,
    val freeOcrOperations: Int,
    val freeAiOperations: Int,
    val adsEnabled: Boolean,
) {
    companion object {
        val DEFAULT = FreePlanLimits(
            maxPagesPerPdf = 5,
            freeOcrOperations = 3,
            freeAiOperations = 1,
            adsEnabled = false,
        )
    }
}
