package com.nla.AIscanerPDF.data.billing

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import com.nla.AIscanerPDF.data.backend.BackendAuthException
import com.nla.AIscanerPDF.data.backend.BackendAuthService
import com.nla.AIscanerPDF.data.backend.BackendSession
import com.nla.AIscanerPDF.data.backend.BackendSessionStore
import com.nla.AIscanerPDF.domain.model.PurchaseResult
import com.nla.AIscanerPDF.domain.model.RestoreResult
import com.nla.AIscanerPDF.domain.model.SubscriptionPeriod
import com.nla.AIscanerPDF.domain.model.SubscriptionProduct
import com.nla.AIscanerPDF.domain.model.SubscriptionStatus
import com.nla.AIscanerPDF.domain.repository.SubscriptionRepository
import ru.rustore.sdk.core.tasks.Task
import ru.rustore.sdk.pay.RuStorePayClient
import ru.rustore.sdk.pay.callback.PurchaseEventListener
import ru.rustore.sdk.pay.model.InvoiceId
import ru.rustore.sdk.pay.model.PreferredPurchaseType
import ru.rustore.sdk.pay.model.ProductId
import ru.rustore.sdk.pay.model.PurchaseAvailabilityResult
import ru.rustore.sdk.pay.model.PurchaseId
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
    private val logger: RuStorePayLogger,
    private val monthlyProductId: String,
    private val yearlyProductId: String,
) : SubscriptionRepository, PayDeeplinkHandler {

    private val status = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.Free)

    override val subscriptionStatus: Flow<SubscriptionStatus> = status

    private val productIds: Set<String> get() = setOf(monthlyProductId, yearlyProductId)

    override fun onNewIntent(intent: Intent) {
        logger.event(
            "deeplink.proceed action=${intent.action} scheme=${intent.data?.scheme ?: "none"}",
        )
        client.getIntentInteractor().proceedIntent(intent, SdkTheme.LIGHT)
    }

    override suspend fun loadProducts(): List<SubscriptionProduct> {
        logger.event("products.load START requested=${productIds.joinToString()}")
        return try {
            client.getProductInteractor()
                .getProducts(productIds.map(::ProductId))
                .awaitResult()
                .filter { it.type == ProductType.SUBSCRIPTION }
                .map { product ->
                    val productId = product.productId.value
                    logger.event(
                        "products.load ITEM productId=$productId type=${product.type} " +
                            "price=${product.amountLabel.value}",
                    )
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
                .also { products ->
                    logger.event("products.load SUCCESS count=${products.size}")
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("products.load FAILED", e)
            throw e
        }
    }

    private val purchaseEventListener = object : PurchaseEventListener {
        override fun onPurchaseCreated(purchaseId: PurchaseId, invoiceId: InvoiceId) {
            logger.event(
                "purchase.event CREATED purchaseId=${logger.maskedId(purchaseId.value)} " +
                    "invoiceId=${logger.maskedId(invoiceId.value)}",
            )
        }

        override fun onPaymentStarted(purchaseId: PurchaseId, invoiceId: InvoiceId) {
            logger.event(
                "purchase.event PAYMENT_STARTED purchaseId=${logger.maskedId(purchaseId.value)} " +
                    "invoiceId=${logger.maskedId(invoiceId.value)}",
            )
        }

        override fun onPaymentCompleted(purchaseId: PurchaseId, invoiceId: InvoiceId) {
            logger.event(
                "purchase.event PAYMENT_COMPLETED purchaseId=${logger.maskedId(purchaseId.value)} " +
                    "invoiceId=${logger.maskedId(invoiceId.value)}",
            )
        }

        override fun onPaymentFailed(purchaseId: PurchaseId?, invoiceId: InvoiceId?) {
            logger.event(
                "purchase.event PAYMENT_FAILED purchaseId=${logger.maskedId(purchaseId?.value)} " +
                    "invoiceId=${logger.maskedId(invoiceId?.value)}",
            )
        }

        override fun onPurchaseCancelled(purchaseId: PurchaseId?, invoiceId: InvoiceId?) {
            logger.event(
                "purchase.event CANCELLED purchaseId=${logger.maskedId(purchaseId?.value)} " +
                    "invoiceId=${logger.maskedId(invoiceId?.value)}",
            )
        }
    }

    private suspend fun checkPurchaseAvailability(productId: String): Boolean {
        logger.event("purchase.availability START productId=$productId")
        return when (
            val availability = client.getPurchaseInteractor()
                .getPurchaseAvailability()
                .awaitResult()
        ) {
            PurchaseAvailabilityResult.Available -> {
                logger.event("purchase.availability AVAILABLE productId=$productId")
                true
            }
            is PurchaseAvailabilityResult.Unavailable -> {
                logger.error(
                    "purchase.availability UNAVAILABLE productId=$productId",
                    availability.cause,
                )
                false
            }
            else -> {
                logger.event(
                    "purchase.availability UNKNOWN productId=$productId " +
                        "result=${availability.javaClass.name}",
                )
                false
            }
        }
    }

    override suspend fun purchase(productId: String): PurchaseResult {
        if (productId !in productIds) {
            logger.event("purchase REJECTED unknownProductId=$productId")
            return PurchaseResult.Error(null)
        }
        logger.event("purchase START productId=$productId")
        return try {
            if (!checkPurchaseAvailability(productId)) return PurchaseResult.Error(null)
            val result = client.getPurchaseInteractor().purchase(
                ProductPurchaseParams(ProductId(productId)),
                PreferredPurchaseType.ONE_STEP,
                SdkTheme.LIGHT,
                purchaseEventListener,
            ).awaitResult()
            logger.event(
                "purchase.sdk SUCCESS productId=${result.productId.value} " +
                    "purchaseId=${logger.maskedId(result.purchaseId.value)} " +
                    "type=${result.productType} sandbox=${result.sandbox}",
            )
            if (result.productType != ProductType.SUBSCRIPTION || result.productId.value != productId) {
                logger.event(
                    "purchase.sdk INVALID_RESULT expectedProductId=$productId " +
                        "actualProductId=${result.productId.value} type=${result.productType}",
                )
                return PurchaseResult.Error(null)
            }
            logger.event("purchase.backend START productId=${result.productId.value}")
            val session = backendAuth.exchangePurchase(
                purchaseId = result.purchaseId.value,
                productId = result.productId.value,
            )
            logger.event(
                "purchase.backend SUCCESS productId=${session.productId} " +
                    "subscriptionValidUntil=${session.subscriptionValidUntilMillis}",
            )
            status.value = session.toSubscriptionStatus()
            PurchaseResult.Success
        } catch (e: CancellationException) {
            logger.event("purchase COROUTINE_CANCELLED productId=$productId")
            throw e
        } catch (e: RuStorePaymentException.ProductPurchaseCancelled) {
            logger.event(
                "purchase.sdk CANCELLED productId=$productId " +
                    "purchaseId=${logger.maskedId(e.purchaseId?.value)} type=${e.productType}",
            )
            PurchaseResult.Cancelled
        } catch (e: RuStorePaymentException.ProductPurchaseException) {
            logger.error(
                "purchase.sdk FAILED productId=$productId " +
                    "purchaseId=${logger.maskedId(e.purchaseId?.value)} " +
                    "invoiceId=${logger.maskedId(e.invoiceId?.value)} " +
                    "sandbox=${e.sandbox} type=${e.productType}",
                e,
            )
            PurchaseResult.Error(null)
        } catch (e: Exception) {
            logger.error("purchase FAILED productId=$productId", e)
            PurchaseResult.Error(null)
        }
    }

    override suspend fun restorePurchases(): RestoreResult =
        try {
            logger.event("restore START")
            refreshSubscriptionStatus()
            val restored = status.value is SubscriptionStatus.Premium
            logger.event("restore SUCCESS restored=$restored")
            RestoreResult.Success(restored = restored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("restore FAILED", e)
            RestoreResult.Error(null)
        }

    /** Проверка ранее купленной подписки при запуске и ручном восстановлении. */
    override suspend fun refreshSubscriptionStatus() {
        logger.event("subscription.refresh START")
        val purchases = try {
            client.getPurchaseInteractor().getPurchases().awaitResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("subscription.refresh SDK_FAILED", e)
            if (useActiveCachedSession()) {
                logger.event("subscription.refresh CACHE_FALLBACK")
                return
            }
            throw e
        }
        logger.event("subscription.refresh SDK_SUCCESS purchases=${purchases.size}")
        val candidates = purchases
            .filterIsInstance<SubscriptionPurchase>()
            .filter { purchase ->
                purchase.productId.value in productIds &&
                    purchase.status in RESTORABLE_STATUSES
            }
            .sortedByDescending { it.status == SubscriptionPurchaseStatus.ACTIVE }

        if (candidates.isEmpty()) {
            logger.event("subscription.refresh NO_ACTIVE_CANDIDATES")
            sessionStore.clear()
            status.value = SubscriptionStatus.Free
            return
        }
        candidates.forEach { purchase ->
            logger.event(
                "subscription.refresh CANDIDATE productId=${purchase.productId.value} " +
                    "purchaseId=${logger.maskedId(purchase.purchaseId.value)} " +
                    "status=${purchase.status} sandbox=${purchase.sandbox}",
            )
        }

        var lastVerificationError: Exception? = null
        for (purchase in candidates) {
            try {
                logger.event(
                    "subscription.verify START productId=${purchase.productId.value} " +
                        "purchaseId=${logger.maskedId(purchase.purchaseId.value)}",
                )
                val session = backendAuth.exchangePurchase(
                    purchaseId = purchase.purchaseId.value,
                    productId = purchase.productId.value,
                )
                logger.event(
                    "subscription.verify SUCCESS productId=${session.productId} " +
                        "validUntil=${session.subscriptionValidUntilMillis}",
                )
                status.value = session.toSubscriptionStatus()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendAuthException) {
                logger.error(
                    "subscription.verify BACKEND_REJECTED productId=${purchase.productId.value} " +
                        "statusCode=${e.statusCode}",
                    e,
                )
                if (e.statusCode != HTTP_FORBIDDEN) lastVerificationError = e
            } catch (e: Exception) {
                logger.error(
                    "subscription.verify FAILED productId=${purchase.productId.value}",
                    e,
                )
                lastVerificationError = e
            }
        }

        if (useActiveCachedSession(candidates.map { it.productId.value }.toSet())) {
            logger.event("subscription.refresh VERIFIED_CACHE_FALLBACK")
            return
        }
        if (lastVerificationError != null) throw lastVerificationError

        logger.event("subscription.refresh INACTIVE")
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
        logger.event(
            "subscription.cache ACTIVE productId=${cachedSession.productId} " +
                "validUntil=${cachedSession.subscriptionValidUntilMillis}",
        )
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
