package com.nla.AIscanerPDF.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.presentation.common.ConfirmDialog
import com.nla.AIscanerPDF.presentation.common.EmptyState
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeUiEffect.OpenCamera -> navController.navigate(Routes.camera())
                is HomeUiEffect.OpenDocument -> navController.navigate(Routes.document(effect.documentId))
                is HomeUiEffect.OpenCrop -> navController.navigate(Routes.crop(effect.pageId))
                HomeUiEffect.OpenPremium -> navController.navigate(Routes.PREMIUM)
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    state.error?.let { error ->
        val message = error.toMessage()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeError()
        }
    }

    val importedName = stringResource(R.string.home_imported_name)
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onImportFromGallery(it.toString(), importedName) }
    }

    var deleteCandidate by remember { mutableStateOf<Document?>(null) }
    var renameCandidate by remember { mutableStateOf<Document?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Button(
                onClick = viewModel::onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    .height(64.dp)
                    .semantics { contentDescription = "Сканировать документ" },
            ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null)
                Text(
                    stringResource(R.string.home_scan_button),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !state.isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = "Импортировать изображение из галереи" },
            ) {
                Icon(Icons.Default.Collections, contentDescription = null)
                Text(
                    stringResource(R.string.home_import_gallery),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (!state.isLoading && state.documents.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.home_empty_title),
                    subtitle = stringResource(R.string.home_empty_subtitle),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 24.dp,
                    ),
                ) {
                    items(state.documents, key = { it.id }) { document ->
                        DocumentRow(
                            document = document,
                            onClick = { viewModel.onDocumentClick(document.id) },
                            onRename = { renameCandidate = document },
                            onShare = { viewModel.onExportAndShare(document.id) },
                            onDelete = { deleteCandidate = document },
                        )
                    }
                }
            }
        }
    }

    deleteCandidate?.let { doc ->
        ConfirmDialog(
            title = stringResource(R.string.delete_document_title),
            message = stringResource(R.string.delete_document_message, doc.name),
            onConfirm = {
                viewModel.onDelete(doc.id)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null },
        )
    }

    renameCandidate?.let { doc ->
        var name by remember(doc.id) { mutableStateOf(doc.name) }
        AlertDialog(
            onDismissRequest = { renameCandidate = null },
            title = { Text(stringResource(R.string.rename_document_title)) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onRename(doc.id, name)
                    renameCandidate = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameCandidate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DocumentRow(
    document: Document,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = document.previewPath,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(document.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        .format(Date(document.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    Text(
                        stringResource(R.string.home_pages_count, document.pageCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (document.hasRecognizedText) {
                        Text(
                            " · " + stringResource(R.string.home_has_ocr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_open)) },
                    onClick = { menuOpen = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_share)) },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_export)) },
                    onClick = { menuOpen = false; onShare() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}
