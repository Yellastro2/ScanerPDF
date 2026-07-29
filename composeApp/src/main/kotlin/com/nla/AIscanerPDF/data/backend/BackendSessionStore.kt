package com.nla.AIscanerPDF.data.backend

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.backendSessionDataStore by preferencesDataStore(name = "backend_session")

/**
 * Локальная серверная сессия, полученная после проверки покупки.
 * Токен хранится только во внутреннем DataStore приложения.
 */
data class BackendSession(
    val accessToken: String,
    val tokenExpiresAtMillis: Long,
    val purchaseId: String,
    val productId: String,
    val subscriptionValidUntilMillis: Long,
    val autoRenewEnabled: Boolean? = null,
) {
    fun hasValidToken(nowMillis: Long = System.currentTimeMillis()): Boolean =
        accessToken.isNotBlank() && tokenExpiresAtMillis > nowMillis

    fun hasActiveSubscription(nowMillis: Long = System.currentTimeMillis()): Boolean =
        subscriptionValidUntilMillis > nowMillis
}

/**
 * Покупка, которую RuStore уже завершил, но backend ещё не успел активировать.
 *
 * Запись переживает перезапуск процесса, чтобы временная ошибка сети после
 * оплаты не приводила к потере сценария активации.
 */
data class PendingPurchase(
    val purchaseId: String,
    val productId: String,
)

interface BackendSessionStore {
    suspend fun read(): BackendSession?
    suspend fun save(session: BackendSession)
    suspend fun readPendingPurchase(): PendingPurchase?
    suspend fun savePendingPurchase(purchase: PendingPurchase)
    suspend fun clearPendingPurchase()
    suspend fun clearAccessToken()
    /** Удаляет серверную сессию, не затрагивая ожидающую активации покупку. */
    suspend fun clear()
}

class DataStoreBackendSessionStore(private val context: Context) : BackendSessionStore {

    private object Keys {
        val accessToken = stringPreferencesKey("access_token")
        val tokenExpiresAt = longPreferencesKey("token_expires_at")
        val purchaseId = stringPreferencesKey("purchase_id")
        val productId = stringPreferencesKey("product_id")
        val subscriptionValidUntil = longPreferencesKey("subscription_valid_until")
        val autoRenewEnabled = booleanPreferencesKey("subscription_auto_renew_enabled")
        val pendingPurchaseId = stringPreferencesKey("pending_purchase_id")
        val pendingProductId = stringPreferencesKey("pending_product_id")
    }

    override suspend fun read(): BackendSession? {
        val preferences = context.backendSessionDataStore.data.first()
        val accessToken = preferences[Keys.accessToken] ?: return null
        val tokenExpiresAt = preferences[Keys.tokenExpiresAt] ?: return null
        val purchaseId = preferences[Keys.purchaseId] ?: return null
        val productId = preferences[Keys.productId] ?: return null
        val subscriptionValidUntil = preferences[Keys.subscriptionValidUntil] ?: return null
        return BackendSession(
            accessToken = accessToken,
            tokenExpiresAtMillis = tokenExpiresAt,
            purchaseId = purchaseId,
            productId = productId,
            subscriptionValidUntilMillis = subscriptionValidUntil,
            autoRenewEnabled = preferences[Keys.autoRenewEnabled],
        )
    }

    override suspend fun save(session: BackendSession) {
        context.backendSessionDataStore.edit { preferences ->
            preferences[Keys.accessToken] = session.accessToken
            preferences[Keys.tokenExpiresAt] = session.tokenExpiresAtMillis
            preferences[Keys.purchaseId] = session.purchaseId
            preferences[Keys.productId] = session.productId
            preferences[Keys.subscriptionValidUntil] = session.subscriptionValidUntilMillis
            if (session.autoRenewEnabled == null) {
                preferences.remove(Keys.autoRenewEnabled)
            } else {
                preferences[Keys.autoRenewEnabled] = session.autoRenewEnabled
            }
            preferences.remove(Keys.pendingPurchaseId)
            preferences.remove(Keys.pendingProductId)
        }
    }

    override suspend fun readPendingPurchase(): PendingPurchase? {
        val preferences = context.backendSessionDataStore.data.first()
        val purchaseId = preferences[Keys.pendingPurchaseId] ?: return null
        val productId = preferences[Keys.pendingProductId] ?: return null
        return PendingPurchase(purchaseId = purchaseId, productId = productId)
    }

    override suspend fun savePendingPurchase(purchase: PendingPurchase) {
        context.backendSessionDataStore.edit { preferences ->
            preferences[Keys.pendingPurchaseId] = purchase.purchaseId
            preferences[Keys.pendingProductId] = purchase.productId
        }
    }

    override suspend fun clearPendingPurchase() {
        context.backendSessionDataStore.edit { preferences ->
            preferences.remove(Keys.pendingPurchaseId)
            preferences.remove(Keys.pendingProductId)
        }
    }

    override suspend fun clearAccessToken() {
        context.backendSessionDataStore.edit { preferences ->
            preferences[Keys.accessToken] = ""
            preferences[Keys.tokenExpiresAt] = 0L
        }
    }

    override suspend fun clear() {
        context.backendSessionDataStore.edit { preferences ->
            preferences.remove(Keys.accessToken)
            preferences.remove(Keys.tokenExpiresAt)
            preferences.remove(Keys.purchaseId)
            preferences.remove(Keys.productId)
            preferences.remove(Keys.subscriptionValidUntil)
            preferences.remove(Keys.autoRenewEnabled)
        }
    }
}
