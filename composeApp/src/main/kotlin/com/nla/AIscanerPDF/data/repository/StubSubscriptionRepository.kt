package com.nla.AIscanerPDF.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.nla.AIscanerPDF.domain.model.PurchaseResult
import com.nla.AIscanerPDF.domain.model.RestoreResult
import com.nla.AIscanerPDF.domain.model.SubscriptionPeriod
import com.nla.AIscanerPDF.domain.model.SubscriptionProduct
import com.nla.AIscanerPDF.domain.model.SubscriptionStatus
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository

/**
 * Заглушка до подключения RuStore Billing (Этап 8 ТЗ).
 * Интерфейс совпадает с боевой реализацией — замена не потребует
 * изменений в остальном приложении.
 */
class StubSubscriptionRepository : SubscriptionRepository {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)

    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    override suspend fun loadProducts(): List<SubscriptionProduct> = listOf(
        SubscriptionProduct("premium_monthly", "Месячная подписка", "—", SubscriptionPeriod.MONTHLY),
        SubscriptionProduct("premium_yearly", "Годовая подписка", "—", SubscriptionPeriod.YEARLY),
    )

    override suspend fun purchase(productId: String): PurchaseResult =
        PurchaseResult.Error("Покупки будут доступны после публикации в RuStore")

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.Success(restored = false)

    override suspend fun refreshSubscriptionStatus() = Unit
}
