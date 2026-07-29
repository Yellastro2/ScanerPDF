package com.nla.AIscanerPDF.data.billing

import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import com.nla.AIscanerPDF.data.backend.BackendAuthException
import com.nla.AIscanerPDF.data.backend.BackendAuthService
import com.nla.AIscanerPDF.data.backend.BackendSession
import com.nla.AIscanerPDF.data.backend.BackendSessionStore
import com.nla.AIscanerPDF.data.backend.PendingPurchase
import com.nla.AIscanerPDF.domain.model.PurchaseResult
import com.nla.AIscanerPDF.domain.model.AutoRenewCancellationResult
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
import ru.rustore.sdk.pay.model.Purchase
import ru.rustore.sdk.pay.model.PurchaseAvailabilityResult
import ru.rustore.sdk.pay.model.PurchaseId
import ru.rustore.sdk.pay.model.ProductPurchaseParams
import ru.rustore.sdk.pay.model.ProductType
import ru.rustore.sdk.pay.model.RuStorePaymentException
import ru.rustore.sdk.pay.model.SdkTheme
import ru.rustore.sdk.pay.model.SubscriptionPurchase
import ru.rustore.sdk.pay.model.SubscriptionPurchaseStatus
import ru.rustore.sdk.pay.model.UserAuthorizationStatus
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
    private val refreshMutex = Mutex()

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
            val pendingPurchase = PendingPurchase(
                purchaseId = result.purchaseId.value,
                productId = result.productId.value,
            )
            refreshMutex.withLock {
                persistPendingPurchase(pendingPurchase)
                logger.event("purchase.backend START productId=${result.productId.value}")
                activateCompletedPurchase(pendingPurchase)
            }
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
            val purchaseId = e.purchaseId?.value
            if (purchaseId == null || e.productType != ProductType.SUBSCRIPTION) {
                PurchaseResult.Error(null)
            } else {
                val pendingPurchase = PendingPurchase(
                    purchaseId = purchaseId,
                    productId = e.productId?.value ?: productId,
                )
                refreshMutex.withLock {
                    persistPendingPurchase(pendingPurchase)
                    activateCompletedPurchase(pendingPurchase)
                }
            }
        } catch (e: Exception) {
            logger.error("purchase FAILED productId=$productId", e)
            PurchaseResult.Error(null)
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        return try {
            logger.event("restore START")
            if (!isUserAuthorized()) {
                logger.event("restore AUTHORIZATION_REQUIRED")
                return RestoreResult.AuthorizationRequired
            }
            refreshMutex.withLock {
                refreshSubscriptionStatusLocked(authorizationConfirmed = true)
            }
            val restored = status.value is SubscriptionStatus.Premium
            logger.event("restore SUCCESS restored=$restored")
            RestoreResult.Success(restored = restored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("restore FAILED", e)
            RestoreResult.TemporarilyUnavailable
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

    /** Проверка ранее купленной подписки при запуске и ручном восстановлении. */
    override suspend fun refreshSubscriptionStatus() = refreshMutex.withLock {
        refreshSubscriptionStatusLocked(authorizationConfirmed = false)
    }

    private suspend fun refreshSubscriptionStatusLocked(authorizationConfirmed: Boolean) {
        logger.event("subscription.refresh START")
        val pendingAttempt = activatePendingPurchaseIfPresent()
        if (pendingAttempt.activated) return
        var lastVerificationError: Exception? = pendingAttempt.error
        if (!authorizationConfirmed && !isUserAuthorized()) {
            logger.event("subscription.refresh UNAUTHORIZED")
            if (useActiveCachedSession()) {
                logger.event("subscription.refresh UNAUTHORIZED_CACHE_FALLBACK")
            } else {
                status.value = SubscriptionStatus.Free
            }
            return
        }
        val purchases = try {
            getPurchasesWithRetry()
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
            if (lastVerificationError != null) {
                if (useActiveCachedSession()) {
                    logger.event("subscription.refresh PENDING_CACHE_FALLBACK")
                    return
                }
                throw lastVerificationError
            }
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

        for (purchase in candidates) {
            try {
                logger.event(
                    "subscription.verify START productId=${purchase.productId.value} " +
                        "purchaseId=${logger.maskedId(purchase.purchaseId.value)}",
                )
                val session = exchangePurchaseWithRetry(
                    PendingPurchase(
                        purchaseId = purchase.purchaseId.value,
                        productId = purchase.productId.value,
                    ),
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
                if (e.isPermanentClientError()) {
                    clearMatchingPendingPurchase(purchase.purchaseId.value)
                } else {
                    lastVerificationError = e
                }
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

    private suspend fun activateCompletedPurchase(purchase: PendingPurchase): PurchaseResult =
        try {
            val session = exchangePurchaseWithRetry(purchase)
            logger.event(
                "purchase.backend SUCCESS productId=${session.productId} " +
                    "subscriptionValidUntil=${session.subscriptionValidUntilMillis}",
            )
            status.value = session.toSubscriptionStatus()
            PurchaseResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendAuthException) {
            if (e.isPermanentClientError()) {
                sessionStore.clearPendingPurchase()
                logger.error("purchase.backend REJECTED productId=${purchase.productId}", e)
                PurchaseResult.Error(e.message)
            } else {
                logger.error("purchase.backend PENDING productId=${purchase.productId}", e)
                PurchaseResult.ActivationPending
            }
        } catch (e: Exception) {
            logger.error("purchase.backend PENDING productId=${purchase.productId}", e)
            PurchaseResult.ActivationPending
        }

    private suspend fun persistPendingPurchase(purchase: PendingPurchase) {
        try {
            sessionStore.savePendingPurchase(purchase)
            logger.event("purchase.pending SAVED productId=${purchase.productId}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("purchase.pending SAVE_FAILED productId=${purchase.productId}", e)
        }
    }

    /**
     * Сначала повторяет активацию уже оплаченной покупки. Ошибка сохраняется,
     * но не мешает дополнительно запросить актуальные покупки у SDK.
     */
    private suspend fun activatePendingPurchaseIfPresent(): PendingActivationAttempt {
        val pending = sessionStore.readPendingPurchase()
            ?: return PendingActivationAttempt(activated = false)
        if (pending.productId !in productIds) {
            logger.event("subscription.pending INVALID_PRODUCT productId=${pending.productId}")
            sessionStore.clearPendingPurchase()
            return PendingActivationAttempt(activated = false)
        }
        logger.event("subscription.pending RETRY productId=${pending.productId}")
        return try {
            val session = exchangePurchaseWithRetry(pending)
            status.value = session.toSubscriptionStatus()
            logger.event("subscription.pending SUCCESS productId=${session.productId}")
            PendingActivationAttempt(activated = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackendAuthException) {
            if (e.isPermanentClientError()) {
                logger.error("subscription.pending REJECTED productId=${pending.productId}", e)
                sessionStore.clearPendingPurchase()
                PendingActivationAttempt(activated = false)
            } else {
                logger.error("subscription.pending TEMPORARY_FAILURE productId=${pending.productId}", e)
                PendingActivationAttempt(activated = false, error = e)
            }
        } catch (e: Exception) {
            logger.error("subscription.pending TEMPORARY_FAILURE productId=${pending.productId}", e)
            PendingActivationAttempt(activated = false, error = e)
        }
    }

    private suspend fun exchangePurchaseWithRetry(purchase: PendingPurchase): BackendSession =
        retryTemporaryOperation("backend.exchange") {
            backendAuth.exchangePurchase(
                purchaseId = purchase.purchaseId,
                productId = purchase.productId,
            )
        }

    private suspend fun getPurchasesWithRetry(): List<Purchase> =
        retryTemporaryOperation("sdk.getPurchases") {
            client.getPurchaseInteractor().getPurchases().awaitResult()
        }

    private suspend fun isUserAuthorized(): Boolean {
        val authorizationStatus = retryTemporaryOperation("sdk.authorization") {
            client.getUserInteractor().getUserAuthorizationStatus().awaitResult()
        }
        logger.event("sdk.authorization STATUS value=$authorizationStatus")
        return authorizationStatus == UserAuthorizationStatus.AUTHORIZED
    }

    private suspend fun <T> retryTemporaryOperation(
        operation: String,
        block: suspend () -> T,
    ): T {
        var lastError: Exception? = null
        repeat(RETRY_ATTEMPTS) { index ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackendAuthException) {
                if (e.isPermanentClientError()) throw e
                lastError = e
            } catch (e: Exception) {
                lastError = e
            }
            val attempt = index + 1
            logger.event("$operation RETRY attempt=$attempt")
            if (attempt < RETRY_ATTEMPTS) delay(RETRY_DELAY_MILLIS * attempt)
        }
        throw checkNotNull(lastError)
    }

    private suspend fun clearMatchingPendingPurchase(purchaseId: String) {
        if (sessionStore.readPendingPurchase()?.purchaseId == purchaseId) {
            sessionStore.clearPendingPurchase()
        }
    }

    private fun BackendAuthException.isPermanentClientError(): Boolean =
        statusCode in CLIENT_ERROR_RANGE &&
            statusCode != HTTP_REQUEST_TIMEOUT &&
            statusCode != HTTP_TOO_MANY_REQUESTS

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
        SubscriptionStatus.Premium(
            expiresAtMillis = subscriptionValidUntilMillis,
            productId = productId,
            autoRenewEnabled = autoRenewEnabled,
        )

    private companion object {
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val RETRY_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 500L
        val CLIENT_ERROR_RANGE = 400..499

        val RESTORABLE_STATUSES = setOf(
            SubscriptionPurchaseStatus.ACTIVE,
            SubscriptionPurchaseStatus.PAUSED,
        )
    }

    private data class PendingActivationAttempt(
        val activated: Boolean,
        val error: Exception? = null,
    )
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
