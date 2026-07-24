package ru.aiscanner.docs.data.billing

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.aiscanner.docs.data.backend.BackendAuthService
import ru.aiscanner.docs.data.backend.BackendSession
import ru.aiscanner.docs.data.backend.BackendSessionStore
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionPeriod
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.repository.SubscriptionRepository

/**
 * Debug-замена RuStore: создает фиктивный purchaseId, но авторизуется
 * и получает access token через настоящий backend.
 */
class MockRuStoreSubscriptionRepository(
    private val backendAuth: BackendAuthService,
    private val sessionStore: BackendSessionStore,
    private val monthlyProductId: String,
    private val yearlyProductId: String,
) : SubscriptionRepository {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)
    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    private val productIds: Set<String> get() = setOf(monthlyProductId, yearlyProductId)

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
    )

    override suspend fun purchase(productId: String): PurchaseResult {
        if (productId !in productIds) return PurchaseResult.Error(null)
        return try {
            val session = backendAuth.exchangePurchase(
                purchaseId = "debug-${UUID.randomUUID()}",
                productId = productId,
            )
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
                backendAuth.exchangePurchase(stored.purchaseId, stored.productId)
            }
            status.value = restored.toSubscriptionStatus()
            RestoreResult.Success(restored = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreResult.Error(null)
        }
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
                backendAuth.exchangePurchase(stored.purchaseId, stored.productId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stored
            }
        }
        status.value = current.toSubscriptionStatus()
    }

    private fun BackendSession.toSubscriptionStatus(): SubscriptionStatus =
        SubscriptionStatus.Premium(subscriptionValidUntilMillis)
}
