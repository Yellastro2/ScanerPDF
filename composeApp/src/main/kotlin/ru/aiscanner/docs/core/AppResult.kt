package ru.aiscanner.docs.core

/**
 * Результат операции доменного уровня. Ошибки типизированы,
 * чтобы UI показывал понятные сообщения без stack trace (п. 14 ТЗ).
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    data object CameraUnavailable : AppError
    data object CaptureFailed : AppError
    data object CornersNotFound : AppError
    data object InvalidCropShape : AppError
    data object OutOfMemory : AppError
    data object PdfExportFailed : AppError
    data object OcrEmpty : AppError
    data object OcrEngineUnavailable : AppError
    data object NoNetwork : AppError
    data object AiUnavailable : AppError
    data object AiNotConfigured : AppError
    data object AiAccessDenied : AppError
    data object AiConsentRequired : AppError
    data object DocumentTooLarge : AppError
    data object DocumentNotFound : AppError
    data class FreeLimitReached(val limit: Int) : AppError
    data object PurchaseFailed : AppError
    data object Unknown : AppError
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(block: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) block(error)
    return this
}
