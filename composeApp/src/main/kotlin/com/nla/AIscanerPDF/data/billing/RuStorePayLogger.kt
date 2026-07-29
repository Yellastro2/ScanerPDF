package com.nla.AIscanerPDF.data.billing

import android.util.Log

/**
 * Диагностика RuStore Pay без вывода полных идентификаторов покупки.
 *
 * Подробные события пишутся только в debug, а обезличенные ошибки остаются
 * в release, чтобы различать сбой SDK и недоступность backend.
 */
class RuStorePayLogger(private val verboseEnabled: Boolean) {

    fun event(message: String) {
        if (verboseEnabled) Log.d(TAG, message)
    }

    fun error(message: String, error: Throwable) {
        val causes = generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .joinToString(" <- ") { cause ->
                val details = cause.message?.sanitizedMessage()
                if (details == null) cause.javaClass.name else "${cause.javaClass.name}: $details"
            }
        val stack = generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .flatMap { cause ->
                cause.stackTrace.take(MAX_STACK_FRAMES_PER_CAUSE).asSequence()
            }
            .joinToString("\n") { frame ->
                "at ${frame.className}.${frame.methodName}(${frame.fileName}:${frame.lineNumber})"
            }
        Log.e(TAG, "$message causes=[$causes]\n$stack")
    }

    fun maskedId(value: String?): String =
        when {
            value == null -> "null"
            value.length <= MASK_VISIBLE_CHARS * 2 -> "<short:${value.length}>"
            else -> "${value.take(MASK_VISIBLE_CHARS)}...${value.takeLast(MASK_VISIBLE_CHARS)}"
        }

    private fun String.sanitizedMessage(): String =
        replace(UUID_PATTERN, "<uuid>")
            .replace(IP_ADDRESS_PATTERN, "<ip>")
            .replace(Regex("\\s+"), " ")
            .take(MAX_MESSAGE_LENGTH)

    private companion object {
        const val TAG = "RuStorePay"
        const val MASK_VISIBLE_CHARS = 4
        const val MAX_CAUSE_DEPTH = 8
        const val MAX_MESSAGE_LENGTH = 500
        const val MAX_STACK_FRAMES_PER_CAUSE = 12
        val UUID_PATTERN = Regex(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-" +
                "[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}\\b",
        )
        val IP_ADDRESS_PATTERN = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    }
}
