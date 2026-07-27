package com.nla.AIscanerPDF.data.billing

import android.util.Log

/**
 * Debug-диагностика RuStore Pay без вывода полных идентификаторов покупки.
 */
class RuStorePayLogger(private val enabled: Boolean) {

    fun event(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun error(message: String, error: Throwable) {
        if (!enabled) return
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
    }
}
