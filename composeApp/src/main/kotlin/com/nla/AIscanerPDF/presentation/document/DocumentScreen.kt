package com.nla.AIscanerPDF.presentation.document

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.domain.model.Document
import com.nla.AIscanerPDF.domain.model.DocumentPage
import com.nla.AIscanerPDF.domain.model.DocumentWithPages
import com.nla.AIscanerPDF.domain.model.ExportQuality
import com.nla.AIscanerPDF.domain.model.PdfExportOptions
import com.nla.AIscanerPDF.domain.model.PdfMargins
import com.nla.AIscanerPDF.domain.model.PdfPageSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nla.AIscanerPDF.presentation.analytics.reportButtonClick
import com.nla.AIscanerPDF.presentation.common.ConfirmDialog
import com.nla.AIscanerPDF.presentation.common.DragDropState
import com.nla.AIscanerPDF.presentation.common.dragContainer
import com.nla.AIscanerPDF.presentation.common.LoadingState
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes
import com.nla.AIscanerPDF.presentation.theme.ScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(navController: NavHostController, viewModel: DocumentViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DocumentUiEffect.OpenCamera -> navController.navigate(Routes.camera(effect.documentId))
                is DocumentUiEffect.OpenEditor -> navController.navigate(Routes.editor(effect.pageId))
                is DocumentUiEffect.OpenCrop -> navController.navigate(Routes.crop(effect.pageId))
                is DocumentUiEffect.OpenOcr -> navController.navigate(Routes.ocr(effect.documentId))
                is DocumentUiEffect.OpenAi -> navController.navigate(Routes.ai(effect.documentId))
                DocumentUiEffect.OpenPremium -> navController.navigate(Routes.PREMIUM)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onImportFromGallery(it.toString()) }
    }

    DocumentContent(
        state = state,
        onOpenGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onAddPage = viewModel::onAddPage,
        onRunOcr = viewModel::onRunOcr,
        onRunAi = viewModel::onRunAi,
        onExportPdf = viewModel::onExportPdf,
        onMovePage = viewModel::onMovePage,
        onMovePageByIndex = viewModel::onMovePageByIndex,
        onEditPage = viewModel::onEditPage,
        onDeletePage = viewModel::onDeletePage,
        onConsumeError = viewModel::consumeError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentContent(
    state: DocumentUiState,
    onOpenGallery: () -> Unit,
    onAddPage: () -> Unit,
    onRunOcr: () -> Unit,
    onRunAi: () -> Unit,
    onExportPdf: (PdfExportOptions) -> Unit,
    onMovePage: (pageId: String, up: Boolean) -> Unit,
    onMovePageByIndex: (fromIndex: Int, toIndex: Int) -> Unit,
    onEditPage: (pageId: String) -> Unit,
    onDeletePage: (pageId: String) -> Unit,
    onConsumeError: () -> Unit,
    pageImageModel: (DocumentPage) -> Any? = { page -> page.previewPath ?: page.originalPath },
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var deletePageId by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    state.error?.let { error ->
        val message = error.toMessage()
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(message)
            onConsumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.document?.document?.name ?: stringResource(R.string.document_title),
//                        modifier = Modifier.padding(start = 8.dp),
                        fontSize = 18.sp)
                },
                actions = {
                    IconButton(
                        onClick = {
                            reportButtonClick("Импортировать страницу из галереи")
                            onOpenGallery()
                        },
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.home_import_gallery),
                        )
                    }
                    IconButton(
                        onClick = {
                            reportButtonClick("Распознать текст документа")
                            onRunOcr()
                        },
                    ) {
                        Icon(
                            Icons.Default.TextSnippet,
                            contentDescription = stringResource(R.string.document_run_ocr),
                        )
                    }
                    IconButton(
                        onClick = {
                            reportButtonClick("AI-анализ документа")
                            onRunAi()
                        },
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.document_ai_analysis),
                        )
                    }
                    IconButton(
                        onClick = {
                            reportButtonClick("Открыть экспорт PDF")
                            showExportDialog = true
                        },
                        enabled = !state.isExporting,
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = stringResource(R.string.document_export_pdf),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    reportButtonClick("Добавить страницу")
                    onAddPage()
                },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.document_add_page), Modifier.padding(start = 8.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val document = state.document
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.isExporting -> LoadingState(
                Modifier.padding(padding),
                label = stringResource(R.string.export_in_progress),
            )
            document == null -> Text(
                stringResource(R.string.error_document_not_found),
                Modifier.padding(padding).padding(16.dp),
            )
            else -> {
                val listState = rememberLazyListState()
                val dragDropState = remember(listState) {
                    DragDropState(listState, onMovePageByIndex)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .dragContainer(dragDropState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    itemsIndexed(
                        document.pages.sortedBy { it.position },
                        key = { _, page -> page.id },
                    ) { index, page ->
                        val isDragging = dragDropState.draggingItemIndex == index
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragDropState.draggingItemOffset else 0f
                                },
                        ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = pageImageModel(page),
                                contentDescription = stringResource(
                                    R.string.document_page_n, page.position + 1,
                                ),
                                modifier = Modifier.size(72.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    stringResource(R.string.document_page_n, page.position + 1),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row {
                                    IconButton(
                                        onClick = {
                                            reportButtonClick("Переместить страницу вверх")
                                            onMovePage(page.id, true)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = stringResource(R.string.document_move_up),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            reportButtonClick("Переместить страницу вниз")
                                            onMovePage(page.id, false)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.document_move_down),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            reportButtonClick("Редактировать страницу")
                                            onEditPage(page.id)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.document_edit_page),
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            reportButtonClick("Удалить страницу")
                                            deletePageId = page.id
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.action_delete),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    deletePageId?.let { pageId ->
        ConfirmDialog(
            title = stringResource(R.string.delete_page_title),
            message = stringResource(R.string.delete_page_message),
            onConfirm = {
                onDeletePage(pageId)
                deletePageId = null
            },
            onDismiss = { deletePageId = null },
        )
    }

    if (showExportDialog) {
        PdfExportDialog(
            onConfirm = { options ->
                showExportDialog = false
                onExportPdf(options)
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

@Composable
private fun PdfExportDialog(onConfirm: (PdfExportOptions) -> Unit, onDismiss: () -> Unit) {
    var pageSize by remember { mutableStateOf(PdfPageSize.AUTO) }
    var margins by remember { mutableStateOf(PdfMargins.NONE) }
    var quality by remember { mutableStateOf(ExportQuality.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.export_page_size), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(pageSize == PdfPageSize.AUTO, {
                        reportButtonClick("Размер PDF Авто")
                        pageSize = PdfPageSize.AUTO
                    },
                        { Text(stringResource(R.string.export_size_auto)) })
                    FilterChip(pageSize == PdfPageSize.A4, {
                        reportButtonClick("Размер PDF A4")
                        pageSize = PdfPageSize.A4
                    },
                        { Text(stringResource(R.string.export_size_a4)) })
                }
                Text(stringResource(R.string.export_margins), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(margins == PdfMargins.NONE, {
                        reportButtonClick("Поля PDF без полей")
                        margins = PdfMargins.NONE
                    },
                        { Text(stringResource(R.string.export_margins_none)) })
                    FilterChip(margins == PdfMargins.SMALL, {
                        reportButtonClick("Поля PDF маленькие")
                        margins = PdfMargins.SMALL
                    },
                        { Text(stringResource(R.string.export_margins_small)) })
                }
                Text(stringResource(R.string.export_quality), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(quality == ExportQuality.LOW, {
                        reportButtonClick("Качество PDF низкое")
                        quality = ExportQuality.LOW
                    },
                        { Text(stringResource(R.string.export_quality_low)) })
                    FilterChip(quality == ExportQuality.MEDIUM, {
                        reportButtonClick("Качество PDF среднее")
                        quality = ExportQuality.MEDIUM
                    },
                        { Text(stringResource(R.string.export_quality_medium)) })
                    FilterChip(quality == ExportQuality.HIGH, {
                        reportButtonClick("Качество PDF высокое")
                        quality = ExportQuality.HIGH
                    },
                        { Text(stringResource(R.string.export_quality_high)) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    reportButtonClick("Экспортировать PDF")
                    onConfirm(PdfExportOptions(pageSize, margins, quality))
                },
            ) {
                Text(stringResource(R.string.document_export_pdf))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    reportButtonClick("Отмена экспорта PDF")
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
fun DocumentScreenPreview() {
    ScannerTheme {
        DocumentContent(
            state = DocumentUiState(
                isLoading = false,
                document = DocumentWithPages(
                    document = Document(
                        id = "preview-document",
                        name = "Скан 31.07.2026 15:43",
                        createdAt = 0,
                        updatedAt = 0,
                        pageCount = 0,
                        previewPath = null,
                        hasRecognizedText = false,
                    ),
                    pages = listOf(
                        DocumentPage(
                            id = "preview-page-1",
                            documentId = "preview-document",
                            position = 0,
                            originalPath = "",
                            processedPath = null,
                            previewPath = null,
                            crop = null,
                        ),
                        DocumentPage(
                            id = "preview-page-2",
                            documentId = "preview-document",
                            position = 1,
                            originalPath = "",
                            processedPath = null,
                            previewPath = null,
                            crop = null,
                        ),
                    ),
                ),
            ),
            onOpenGallery = {},
            onAddPage = {},
            onRunOcr = {},
            onRunAi = {},
            onExportPdf = {},
            onMovePage = { _, _ -> },
            onMovePageByIndex = { _, _ -> },
            onEditPage = {},
            onDeletePage = {},
            onConsumeError = {},
            pageImageModel = { R.drawable.ic_launcher_foreground },
        )
    }
}
