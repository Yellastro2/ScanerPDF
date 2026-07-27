package ru.aiscanner.docs.data.backend

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

data class BackendRequestTrace internal constructor(
    val requestId: String,
    val operation: String,
    val path: String,
)

/**
 * Безопасный debug-лог сетевого обмена с LLMProxy.
 * Тела запросов/ответов, purchaseId и access token намеренно не логируются.
 */
class BackendApiLogger(private val enabled: Boolean) {

    private val requestCounter = AtomicLong()

    fun start(operation: String, path: String, details: String): BackendRequestTrace {
        val trace = BackendRequestTrace(
            requestId = "req-${requestCounter.incrementAndGet()}",
            operation = operation,
            path = path,
        )
        debug(trace, "START $details")
        return trace
    }

    fun request(trace: BackendRequestTrace, attempt: Int): Long {
        debug(trace, "REQUEST_STARTED method=POST path=${trace.path} attempt=$attempt")
        return SystemClock.elapsedRealtime()
    }

    fun response(
        trace: BackendRequestTrace,
        attempt: Int,
        statusCode: Int,
        startedAtMillis: Long,
        responseChars: Int,
    ) {
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAtMillis
        debug(
            trace,
            "REQUEST_FINISHED status=$statusCode path=${trace.path} attempt=$attempt " +
                "durationMs=$elapsedMillis responseChars=$responseChars",
        )
    }

    fun event(trace: BackendRequestTrace, details: String) {
        debug(trace, "EVENT $details")
    }

    fun parsed(trace: BackendRequestTrace, details: String) {
        debug(trace, "RESPONSE_PARSED $details")
    }

    fun parseFailure(trace: BackendRequestTrace, error: Throwable) {
        if (enabled) {
            Log.e(
                TAG,
                "[${trace.requestId}] ${trace.operation} " +
                    "PARSE_FAILED type=${error.javaClass.simpleName}",
            )
        }
    }

    fun requestFailure(
        trace: BackendRequestTrace,
        attempt: Int,
        startedAtMillis: Long,
        error: Throwable,
    ) {
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAtMillis
        error(
            trace,
            "REQUEST_FAILED path=${trace.path} attempt=$attempt " +
                "durationMs=$elapsedMillis type=${error.javaClass.simpleName}",
            error,
        )
    }

    private fun debug(trace: BackendRequestTrace, message: String) {
        if (enabled) Log.d(TAG, "[${trace.requestId}] ${trace.operation} $message")
    }

    private fun error(trace: BackendRequestTrace, message: String, error: Throwable) {
        if (enabled) Log.e(TAG, "[${trace.requestId}] ${trace.operation} $message", error)
    }

    companion object {
        const val TAG = "LLMProxy"
    }
}
