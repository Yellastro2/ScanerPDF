package ru.aiscanner.docs.data.billing

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.aiscanner.docs.data.backend.BackendAuthException
import ru.aiscanner.docs.data.backend.BackendAuthService
import ru.aiscanner.docs.data.backend.BackendSession
import ru.aiscanner.docs.data.backend.BackendSessionStore
import ru.aiscanner.docs.domain.model.PurchaseResult
import ru.aiscanner.docs.domain.model.RestoreResult
import ru.aiscanner.docs.domain.model.SubscriptionPeriod
import ru.aiscanner.docs.domain.model.SubscriptionProduct
import ru.aiscanner.docs.domain.model.SubscriptionStatus
import ru.aiscanner.docs.domain.repository.SubscriptionRepository
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.model.PreferredPurchaseType
import ru.rustore.sdk.pay.model.ProductId
import ru.rustore.sdk.pay.model.ProductPurchaseParams
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.RuStorePaymentException
import ru.rustore.sdk.pay.model.SdkTheme
import ru.rustore.sdk.pay.model.SubscriptionPurchase
import ru.rustore.sdk.pay.model.SubscriptionPurchaseStatus
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Передача deeplink-интентов оплаты в RuStore Pay SDK. */
interface PayDeeplinkHandler {
    fun onNewIntent(intent: Intent)
}

/**
 * Реальная интеграция с RuStore Pay SDK.
 *
 * SDK отвечает за каталог и покупку, а backend проверяет purchaseId через
 * RuStore Public API и остаётся источником истины для доступа к AI.
 */
class RuStoreSubscriptionRepository(
    private val client: RuStorePayClient,
    private val backendAuth: BackendAuthService,
    private val sessionStore: BackendSessionStore,
    private val monthlyProductId: String,
    private val yearlyProductId: String,
) : SubscriptionRepository, PayDeeplinkHandler {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)

    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    private val productIds: Set<String> get() = setOf(monthlyProductId, yearlyProductId)

    override fun onNewIntent(intent: Intent) {
        client.intentInteractor.proceedIntent(intent, SdkTheme.LIGHT)
    }

    override suspend fun loadProducts(): List<SubscriptionProduct> =
        client.productInteractor
            .getProducts(productIds.map(::ProductId))
            .awaitResult()
            .filter { it.type == ProductType.SUBSCRIPTION }
            .map { product ->
                val productId = product.productId.value
                SubscriptionProduct(
                    productId = productId,
                    title = product.title.value,
                    price = product.amountLabel.value,
                    period = if (productId == yearlyProductId) {
                        SubscriptionPeriod.YEARLY
                    } else {
                        SubscriptionPeriod.MONTHLY
                    },
                )
            }

    override suspend fun purchase(productId: String): PurchaseResult {
        if (productId !in productIds) return PurchaseResult.Error(null)
        return try {
            val result = client.purchaseInteractor.purchase(
                ProductPurchaseParams(ProductId(productId)),
                PreferredPurchaseType.ONE_STEP,
                SdkTheme.LIGHT,
                null,
            ).awaitResult()
            if (result.productType != ProductType.SUBSCRIPTION || result.productId.value != productId) {
                return PurchaseResult.Error(null)
            }
            val session = backendAuth.exchangePurchase(
                purchaseId = result.purchaseId.value,
                productId = result.productId.value,
            )
            status.value = session.toSubscriptionStatus()
            PurchaseResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuStorePaymentException.ProductPurchaseCancelled) {
            PurchaseResult.Cancelled
        } catch (e: Exception) {
            PurchaseResult.Error(null)
        }
    }

    override suspend fun restorePurchases(): RestoreResult =
        try {
            refreshSubscriptionStatus()
            RestoreResult.Success(restored = status.value is SubscriptionStatus.Premium)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RestoreResult.Error(null)
        }

    /** Проверка ранее купленной подписки при запуске и ручном восстановлении. */
    override suspend fun refreshSubscriptionStatus() {
        val purchases = try {
            client.purchaseInteractor.getPurchases().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (useActiveCachedSession()) return
            throw e
        }
        val candidates = purchases
            .filterIsInstance<SubscriptionPurchase>()
            .filter { purchase ->
                purchase.productId.value in productIds &&
                    purchase.status in RESTORABLE_STATUSES
            }
            .sortedByDescending { it.status == SubscriptionPurchaseStatus.ACTIVE }

        if (candidates.isEmpty()) {
            sessionStore.clear()
            status.value = SubscriptionStatus.Free
            return
        }

        var lastVerificationError: Exception? = null
        for (purchase in candidates) {
            try {
                val session = backendAuth.exchangePurchase(
                    purchaseId = purchase.purchaseId.value,
                    productId = purchase.productId.value,
                )
                status.value = session.toSubscriptionStatus()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendAuthException) {
                if (e.statusCode != HTTP_FORBIDDEN) lastVerificationError = e
            } catch (e: Exception) {
                lastVerificationError = e
            }
        }

        if (useActiveCachedSession(candidates.map { it.productId.value }.toSet())) return
        if (lastVerificationError != null) throw lastVerificationError

        sessionStore.clear()
        status.value = SubscriptionStatus.Free
    }

    private suspend fun useActiveCachedSession(
        allowedProductIds: Set<String> = productIds,
    ): Boolean {
        val cachedSession = sessionStore.read()
        if (
            cachedSession == null ||
            cachedSession.productId !in allowedProductIds ||
            !cachedSession.hasActiveSubscription()
        ) {
            return false
        }
        status.value = cachedSession.toSubscriptionStatus()
        return true
    }

    private fun BackendSession.toSubscriptionStatus(): SubscriptionStatus =
        SubscriptionStatus.Premium(subscriptionValidUntilMillis)

    private companion object {
        const val HTTP_FORBIDDEN = 403

        val RESTORABLE_STATUSES = setOf(
            SubscriptionPurchaseStatus.ACTIVE,
            SubscriptionPurchaseStatus.PAUSED,
        )
    }
}

private suspend fun <T : Any> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
