package com.nla.AIscanerPDF.domain.model

sealed interface SubscriptionStatus {
    data object Free : SubscriptionStatus

    /**
     * Подтвержденный Premium-доступ: подписка или бессрочная покупка.
     *
     * [autoRenewEnabled] равен `null`, если источник состояния не передал признак
     * автопродления. Для бессрочной покупки [isLifetime] равен `true`.
     */
    data class Premium(
        val expiresAtMillis: Long?,
        val productId: String = "",
        val autoRenewEnabled: Boolean? = null,
        val isLifetime: Boolean = false,
    ) : SubscriptionStatus
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
    /** RuStore завершил оплату, но backend пока не подтвердил активацию. */
    data object ActivationPending : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Error(val message: String?) : PurchaseResult
}

sealed interface RestoreResult {
    data class Success(val restored: Boolean) : RestoreResult
    /** Для чтения покупок требуется обновить короткую VK-сессию Pay SDK. */
    data object AuthorizationRequired : RestoreResult
    /** Восстановление не завершено из-за временной ошибки SDK или сети. */
    data object TemporarilyUnavailable : RestoreResult
    data class Error(val message: String?) : RestoreResult
}

/** Результат подтвержденного сервером отключения автопродления. */
sealed interface AutoRenewCancellationResult {
    data object Success : AutoRenewCancellationResult
    data class Error(val message: String?) : AutoRenewCancellationResult
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
