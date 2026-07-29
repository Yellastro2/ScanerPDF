package com.nla.AIscanerPDF.data.backend

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import com.nla.AIscanerPDF.data.ai.AiResponseParser

@Serializable
private data class RuStoreAuthRequestDto(
    val purchaseId: String,
    val productId: String,
    val invoiceId: String? = null,
    val productType: String? = null,
)

@Serializable
private data class RuStoreAuthResponseDto(
    val accessToken: String,
    val expiresAt: String,
    val subscription: SubscriptionDto,
)

@Serializable
private data class SubscriptionDto(
    val productId: String,
    val status: String,
    val validUntil: String,
    val autoRenewEnabled: Boolean? = null,
)

@Serializable
private data class BackendErrorDto(
    val error: String,
    val message: String,
)

@Serializable
private data class CancelSubscriptionResponseDto(val autoRenewEnabled: Boolean)

class BackendNotConfiguredException : Exception()

class BackendAuthException(
    val statusCode: Int,
    message: String? = null,
) : Exception(message)

/**
 * Обменивает покупку RuStore на собственную серверную сессию приложения.
 */
class BackendAuthService(
    private val client: HttpClient,
    baseUrl: String,
    private val sessionStore: BackendSessionStore,
    private val logger: BackendApiLogger,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    suspend fun exchangePurchase(
        purchaseId: String,
        productId: String,
        invoiceId: String? = null,
        productType: String? = null,
    ): BackendSession {
        if (!isConfigured) throw BackendNotConfiguredException()
        val trace = logger.start(
            operation = "auth.rustore",
            path = AUTH_PATH,
            details = "productId=$productId",
        )
        val startedAt = logger.request(trace, attempt = 1)
        val responseBody: String
        val response = try {
            client.post("$baseUrl$AUTH_PATH") {
                contentType(ContentType.Application.Json)
                setBody(
                    RuStoreAuthRequestDto(
                        purchaseId = purchaseId,
                        productId = productId,
                        invoiceId = invoiceId,
                        productType = productType,
                    ),
                )
            }
        } catch (e: CancellationException) {
            logger.requestFailure(trace, attempt = 1, startedAtMillis = startedAt, error = e)
            throw e
        } catch (e: Exception) {
            logger.requestFailure(trace, attempt = 1, startedAtMillis = startedAt, error = e)
            throw e
        }
        try {
            responseBody = response.bodyAsText()
        } catch (e: CancellationException) {
            logger.requestFailure(trace, attempt = 1, startedAtMillis = startedAt, error = e)
            throw e
        } catch (e: Exception) {
            logger.requestFailure(trace, attempt = 1, startedAtMillis = startedAt, error = e)
            throw e
        }
        logger.response(
            trace = trace,
            attempt = 1,
            statusCode = response.status.value,
            startedAtMillis = startedAt,
            responseChars = responseBody.length,
        )
        if (!response.status.isSuccess()) {
            if (response.status.value == 403) sessionStore.clear()
            val backendError = runCatching {
                AiResponseParser.json.decodeFromString<BackendErrorDto>(responseBody)
            }.getOrNull()
            logger.event(
                trace,
                "authRejected code=${backendError?.error ?: "unknown"}",
            )
            throw BackendAuthException(
                statusCode = response.status.value,
                message = backendError?.error,
            )
        }

        val dto = try {
            AiResponseParser.json.decodeFromString<RuStoreAuthResponseDto>(responseBody)
        } catch (e: Exception) {
            logger.parseFailure(trace, e)
            throw e
        }
        if (dto.accessToken.isBlank() || !dto.subscription.status.equals("active", ignoreCase = true)) {
            sessionStore.clear()
            logger.event(
                trace,
                "invalidAuthResponse tokenPresent=${dto.accessToken.isNotBlank()} " +
                    "subscriptionStatus=${dto.subscription.status}",
            )
            throw BackendAuthException(statusCode = response.status.value)
        }
        val session = try {
            BackendSession(
                accessToken = dto.accessToken,
                tokenExpiresAtMillis = dto.expiresAt.toEpochMillis(),
                purchaseId = purchaseId,
                productId = dto.subscription.productId,
                subscriptionValidUntilMillis = dto.subscription.validUntil.toEpochMillis(),
                autoRenewEnabled = dto.subscription.autoRenewEnabled,
                invoiceId = invoiceId,
                productType = productType,
            )
        } catch (e: Exception) {
            logger.parseFailure(trace, e)
            throw e
        }
        sessionStore.save(session)
        logger.event(
            trace,
            "sessionSaved productId=${session.productId} " +
                "tokenExpiresAt=${session.tokenExpiresAtMillis} " +
                "subscriptionValidUntil=${session.subscriptionValidUntilMillis}",
        )
        return session
    }

    /** Отключает автопродление через защищенный backend endpoint. */
    suspend fun cancelAutoRenew(): BackendSession {
        val stored = sessionStore.read() ?: throw BackendAuthException(statusCode = 401)
        val response = client.post("$baseUrl/v1/subscriptions/cancel") {
            bearerAuth(stored.accessToken)
        }
        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) throw BackendAuthException(statusCode = response.status.value)
        val result = AiResponseParser.json.decodeFromString<CancelSubscriptionResponseDto>(responseBody)
        if (result.autoRenewEnabled) throw BackendAuthException(statusCode = response.status.value)
        val updatedSession = stored.copy(autoRenewEnabled = false)
        sessionStore.save(updatedSession)
        return updatedSession
    }

    private fun String.toEpochMillis(): Long =
        runCatching { Instant.parse(this).toEpochMilli() }
            .getOrElse { throw BackendAuthException(statusCode = 200) }

    private companion object {
        const val AUTH_PATH = "/v1/auth/rustore"
    }
}
