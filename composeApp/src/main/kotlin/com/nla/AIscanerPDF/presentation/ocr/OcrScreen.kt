package com.nla.AIscanerPDF.presentation.ocr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(navController: NavHostController, viewModel: OcrViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.ocr_copied)

    state.error?.let { error ->
        val message = error.toMessage()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                actions = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(state.fullText))
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.ocr_copy))
                    }
                    IconButton(onClick = viewModel::onExportTxt) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.ocr_export_txt))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.isRecognizing) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.ocr_progress, state.progressCurrent, state.progressTotal),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    OutlinedButton(
                        onClick = viewModel::onCancelRecognition,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.ocr_cancel))
                    }
                }
            } else {
                Button(
                    onClick = viewModel::onStartRecognition,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Text(
                        stringResource(R.string.document_run_ocr),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }

            OutlinedButton(
                onClick = { navController.navigate(Routes.ai(state.documentId)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.ai_title))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp,
                ),
            ) {
                items(state.pages, key = { it.pageId }) { page ->
                    Column {
                        Text(
                            stringResource(R.string.ocr_page_header, page.pageNumber),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = page.text,
                            onValueChange = { viewModel.onTextEdited(page.pageId, it) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            minLines = 3,
                        )
                    }
                }
            }
        }
    }
}
