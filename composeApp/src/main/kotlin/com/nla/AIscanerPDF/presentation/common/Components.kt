package com.nla.AIscanerPDF.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.core.AppError

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (label != null) Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/** Единое сопоставление доменных ошибок строкам ресурсов (п. 14 ТЗ). */
@Composable
fun AppError.toMessage(): String = when (this) {
    AppError.CameraUnavailable -> stringResource(R.string.error_camera_open)
    AppError.CaptureFailed -> stringResource(R.string.error_capture)
    AppError.CornersNotFound -> stringResource(R.string.crop_not_found)
    AppError.InvalidCropShape -> stringResource(R.string.crop_invalid_shape)
    AppError.OutOfMemory -> stringResource(R.string.error_out_of_memory)
    AppError.PdfExportFailed -> stringResource(R.string.error_pdf_create)
    AppError.OcrEmpty -> stringResource(R.string.ocr_empty)
    AppError.OcrEngineUnavailable -> stringResource(R.string.ocr_engine_unavailable)
    AppError.NoNetwork -> stringResource(R.string.error_no_network)
    AppError.AiUnavailable -> stringResource(R.string.error_ai_unavailable)
    AppError.AiNotConfigured -> stringResource(R.string.error_ai_not_configured)
    AppError.AiAccessDenied -> stringResource(R.string.error_ai_access_denied)
    AppError.AiConsentRequired -> stringResource(R.string.ai_consent_title)
    AppError.DocumentTooLarge -> stringResource(R.string.error_ai_too_large)
    AppError.DocumentNotFound -> stringResource(R.string.error_document_not_found)
    is AppError.FreeLimitReached -> stringResource(R.string.error_free_pdf_limit, limit)
    AppError.PurchaseFailed -> stringResource(R.string.error_purchase)
    AppError.Unknown -> stringResource(R.string.error_generic)
}
