package ru.aiscanner.docs.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import ru.aiscanner.docs.data.backend.BackendApiLogger
import ru.aiscanner.docs.data.backend.BackendAuthService
import ru.aiscanner.docs.data.backend.BackendRequestTrace
import ru.aiscanner.docs.data.backend.BackendSession
import ru.aiscanner.docs.data.backend.BackendSessionStore
import ru.aiscanner.docs.domain.model.AiSummary
import ru.aiscanner.docs.domain.model.ContractAnalysis
import ru.aiscanner.docs.domain.model.ExtractedDocumentData

private data class AiBackendPayload(
    val body: String,
    val trace: BackendRequestTrace,
)

private data class AiBackendHttpResult(
    val status: HttpStatusCode,
    val body: String,
)

@Serializable
private data class AiBackendErrorDto(val error: String)

/**
 * Клиент backend-прокси (п. 9 ТЗ). API-ключ AI-провайдера в APK не хранится —
 * авторизация выполняется на стороне прокси. AI-запрос повторяется только
 * один раз после 401 и успешного обновления токена; остальные ответы
 * автоматически не ретраятся. Таймауты настроены в HttpClient,
 * отмена — стандартная отмена корутины Ktor.
 */
class KtorAiDocumentService(
    private val client: HttpClient,
    private val baseUrl: String,
    private val sessionStore: BackendSessionStore,
    private val backendAuth: BackendAuthService,
    private val logger: BackendApiLogger,
) : AiDocumentService {

    override suspend fun summarize(text: String, language: String): AiSummary {
        val payload = post("ai.summarize", SUMMARY_PATH, text, language)
        return parseResponse(
            payload = payload,
            result = AiResponseParser.parseSummary(payload.body),
        ) { summary ->
            "documentTypePresent=${summary.documentType != null} " +
                "keyPoints=${summary.keyPoints.size} dates=${summary.dates.size} " +
                "amounts=${summary.amounts.size} actions=${summary.requiredActions.size}"
        }
    }

    override suspend fun extractData(text: String, language: String): ExtractedDocumentData {
        val payload = post("ai.extract", EXTRACT_PATH, text, language)
        return parseResponse(
            payload = payload,
            result = AiResponseParser.parseExtraction(payload.body),
        ) { extraction ->
            "fields=${extraction.fields.size}"
        }
    }

    override suspend fun analyzeContract(text: String, language: String): ContractAnalysis {
        val payload = post("ai.contract", CONTRACT_PATH, text, language)
        return parseResponse(
            payload = payload,
            result = AiResponseParser.parseContract(payload.body),
        ) { contract ->
            "importantTerms=${contract.importantTerms.size} risks=${contract.risks.size} " +
                "deadlines=${contract.deadlines.size} moneyTerms=${contract.moneyTerms.size} " +
                "questions=${contract.questionsToClarify.size}"
        }
    }

    private suspend fun post(
        operation: String,
        path: String,
        text: String,
        language: String,
    ): AiBackendPayload {
        if (text.length > AI_MAX_TEXT_CHARS) throw DocumentTooLargeException()
        val trace = logger.start(
            operation = operation,
            path = path,
            details = "textChars=${text.length} language=$language",
        )
        var session = requireSession(trace)
        var response = executePost(trace, 1, text, language, session.accessToken)
        if (response.status.value == 401) {
            logger.event(trace, "tokenRejected refreshingSession=true")
            sessionStore.clearAccessToken()
            session = refreshSession(trace, session)
            response = executePost(trace, 2, text, language, session.accessToken)
        }
        if (!response.status.isSuccess()) {
            if (response.status.value == 401) {
                sessionStore.clearAccessToken()
                logger.event(trace, "requestRejected status=401 code=tokenRejected")
                throw AiAccessDeniedException()
            }
            if (response.status.value == 403) {
                sessionStore.clear()
                logger.event(trace, "requestRejected status=403 code=accessDenied")
                throw AiAccessDeniedException()
            }
            val errorCode = runCatching {
                AiResponseParser.json.decodeFromString<AiBackendErrorDto>(response.body).error
            }.getOrNull()
            logger.event(
                trace,
                "requestRejected status=${response.status.value} code=${errorCode ?: "unknown"}",
            )
            throw AiBackendException()
        }
        return AiBackendPayload(body = response.body, trace = trace)
    }

    private suspend fun requireSession(trace: BackendRequestTrace): BackendSession {
        val stored = sessionStore.read()
        if (stored == null) {
            logger.event(trace, "sessionMissing")
            throw AiAccessDeniedException()
        }
        if (!stored.hasActiveSubscription()) {
            logger.event(trace, "subscriptionExpired")
            throw AiAccessDeniedException()
        }
        return if (stored.hasValidToken()) {
            logger.event(trace, "sessionReady productId=${stored.productId}")
            stored
        } else {
            logger.event(trace, "tokenExpired refreshingSession=true")
            refreshSession(trace, stored)
        }
    }

    private suspend fun refreshSession(
        trace: BackendRequestTrace,
        stored: BackendSession,
    ): BackendSession =
        try {
            backendAuth.exchangePurchase(stored.purchaseId, stored.productId).also {
                logger.event(trace, "sessionRefreshed productId=${it.productId}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.event(trace, "sessionRefreshFailed type=${e.javaClass.simpleName}")
            throw AiAccessDeniedException()
        }

    private suspend fun executePost(
        trace: BackendRequestTrace,
        attempt: Int,
        text: String,
        language: String,
        accessToken: String,
    ): AiBackendHttpResult {
        val startedAt = logger.request(trace, attempt)
        return try {
            val response = client.post("${baseUrl.trimEnd('/')}${trace.path}") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(AiTextRequestDto(text = text, language = language))
            }
            val responseBody = response.bodyAsText()
            logger.response(
                trace = trace,
                attempt = attempt,
                statusCode = response.status.value,
                startedAtMillis = startedAt,
                responseChars = responseBody.length,
            )
            AiBackendHttpResult(status = response.status, body = responseBody)
        } catch (e: CancellationException) {
            logger.requestFailure(trace, attempt, startedAt, e)
            throw e
        } catch (e: Exception) {
            logger.requestFailure(trace, attempt, startedAt, e)
            throw e
        }
    }

    private fun <T> parseResponse(
        payload: AiBackendPayload,
        result: Result<T>,
        details: (T) -> String,
    ): T = result.fold(
        onSuccess = {
            logger.parsed(payload.trace, details(it))
            it
        },
        onFailure = {
            logger.parseFailure(payload.trace, it)
            throw it
        },
    )

    private companion object {
        const val SUMMARY_PATH = "/v1/ai/summarize"
        const val EXTRACT_PATH = "/v1/ai/extract"
        const val CONTRACT_PATH = "/v1/ai/contract"
    }
}
