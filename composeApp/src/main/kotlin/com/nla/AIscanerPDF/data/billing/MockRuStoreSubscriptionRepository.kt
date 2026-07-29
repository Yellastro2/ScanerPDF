package com.nla.AIscanerPDF.data.billing

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import com.nla.AIscanerPDF.data.backend.BackendAuthService
import com.nla.AIscanerPDF.data.backend.BackendSession
import com.nla.AIscanerPDF.data.backend.BackendSessionStore
import com.nla.AIscanerPDF.domain.model.PurchaseResult
import com.nla.AIscanerPDF.domain.model.AutoRenewCancellationResult
import com.nla.AIscanerPDF.domain.model.RestoreResult
import com.nla.AIscanerPDF.domain.model.SubscriptionPeriod
import com.nla.AIscanerPDF.domain.model.SubscriptionProduct
import com.nla.AIscanerPDF.domain.model.SubscriptionStatus
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository

/**
 * Debug-замена RuStore: создает фиктивный purchaseId, но авторизуется
 * и получает access token через настоящий backend.
 */
class MockRuStoreSubscriptionRepository(
    private val backendAuth: BackendAuthService,
    private val sessionStore: BackendSessionStore,
    private val monthlyProductId: String,
    private val yearlyProductId: String,
    private val foreverProductId: String,
) : SubscriptionRepository {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)
    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    private val productIds: Set<String>
        get() = setOf(monthlyProductId, yearlyProductId, foreverProductId)

    override suspend fun loadProducts(): List<SubscriptionProduct> = listOf(
        SubscriptionProduct(
            productId = monthlyProductId,
            title = "Месячная подписка",
            price = "Тестовая покупка",
            period = SubscriptionPeriod.MONTHLY,
        ),
        SubscriptionProduct(
            productId = yearlyProductId,
            title = "Годовая подписка",
            price = "Тестовая покупка",
            period = SubscriptionPeriod.YEARLY,
        ),
        SubscriptionProduct(
            productId = foreverProductId,
            title = "Premium навсегда",
            price = "Тестовая покупка",
            period = SubscriptionPeriod.ONE_TIME,
        ),
    )

    override suspend fun purchase(productId: String): PurchaseResult {
        if (productId !in productIds) return PurchaseResult.Error(null)
        return try {
            val productType = if (productId == foreverProductId) {
                "NON_CONSUMABLE_PRODUCT"
            } else {
                "SUBSCRIPTION"
            }
            val backendSession = backendAuth.exchangePurchase(
                purchaseId = "debug-${UUID.randomUUID()}",
                productId = productId,
                invoiceId = "debug-invoice-${UUID.randomUUID()}",
                productType = productType,
            )
            val session = if (productId == foreverProductId) {
                backendSession.copy(
                    subscriptionValidUntilMillis = LIFETIME_VALID_UNTIL_MILLIS,
                    autoRenewEnabled = null,
                ).also { sessionStore.save(it) }
            } else {
                backendSession
            }
            status.value = session.toSubscriptionStatus()
            PurchaseResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PurchaseResult.Error(null)
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        val stored = sessionStore.read()
            ?: return RestoreResult.Success(restored = false)
        if (!stored.hasActiveSubscription()) {
            sessionStore.clear()
            status.value = SubscriptionStatus.Free
            return RestoreResult.Success(restored = false)
        }
        return try {
            val restored = if (stored.hasValidToken()) {
                stored
            } else {
                backendAuth.exchangePurchase(
                    purchaseId = stored.purchaseId,
                    productId = stored.productId,
                    invoiceId = stored.invoiceId,
                    productType = stored.productType,
                )
            }
            status.value = restored.toSubscriptionStatus()
            RestoreResult.Success(restored = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreResult.Error(null)
        }
    }

    override suspend fun cancelAutoRenew(): AutoRenewCancellationResult = try {
        status.value = backendAuth.cancelAutoRenew().toSubscriptionStatus()
        AutoRenewCancellationResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AutoRenewCancellationResult.Error(e.message)
    }

    override suspend fun refreshSubscriptionStatus() {
        val stored = sessionStore.read()
        if (stored == null || !stored.hasActiveSubscription()) {
            sessionStore.clear()
            status.value = SubscriptionStatus.Free
            return
        }
        val current = if (stored.hasValidToken()) {
            stored
        } else {
            try {
                backendAuth.exchangePurchase(
                    purchaseId = stored.purchaseId,
                    productId = stored.productId,
                    invoiceId = stored.invoiceId,
                    productType = stored.productType,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stored
            }
        }
        status.value = current.toSubscriptionStatus()
    }

    private fun BackendSession.toSubscriptionStatus(): SubscriptionStatus =
        SubscriptionStatus.Premium(
            expiresAtMillis = subscriptionValidUntilMillis,
            productId = productId,
            autoRenewEnabled = autoRenewEnabled,
            isLifetime = productType == "NON_CONSUMABLE_PRODUCT",
        )

    private companion object {
        const val LIFETIME_VALID_UNTIL_MILLIS = 253_402_300_799_000L
    }
}
