package com.nla.AIscanerPDF.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.data.imageprocessing.AndroidDocumentImageProcessor
import com.nla.AIscanerPDF.domain.model.DocumentFilter
import com.nla.AIscanerPDF.presentation.analytics.reportButtonClick
import com.nla.AIscanerPDF.presentation.common.LoadingState
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(navController: NavHostController, viewModel: PageEditorViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PageEditorUiEffect.OpenDocument ->
                    navController.navigate(Routes.document(effect.documentId)) {
                        popUpTo(Routes.HOME)
                    }
                is PageEditorUiEffect.OpenCrop -> navController.navigate(Routes.crop(effect.pageId))
            }
        }
    }

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
                title = { Text(stringResource(R.string.editor_title)) },
                actions = {
                    IconButton(
                        onClick = {
                            reportButtonClick("Изменить обрезку")
                            viewModel.onRecrop()
                        },
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = stringResource(R.string.editor_recrop))
                    }
                    IconButton(
                        onClick = {
                            reportButtonClick("Повернуть страницу")
                            viewModel.onRotate()
                        },
                    ) {
                        Icon(
                            Icons.Default.Rotate90DegreesCw,
                            contentDescription = stringResource(R.string.editor_rotate),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                if (state.imagePath == null || state.isSaving) {
                    LoadingState(label = if (state.isSaving) stringResource(R.string.editor_saving) else null)
                } else {
                    // Живое превью без пересоздания JPEG: ColorMatrix-фильтр на лету
                    val androidMatrix = AndroidDocumentImageProcessor.buildColorMatrix(
                        state.filter, state.brightness, state.contrast,
                    )
                    AsyncImage(
                        model = state.imagePath,
                        contentDescription = null,
                        colorFilter = ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array)),
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(state.rotationDegrees.toFloat()),
                    )
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(DocumentFilter.entries) { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = {
                            reportButtonClick("Фильтр ${filter.analyticsName()}")
                            viewModel.onFilterSelected(filter)
                        },
                        label = { Text(filter.label()) },
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_brightness), Modifier.padding(end = 8.dp))
                    Slider(
                        value = state.brightness,
                        onValueChange = viewModel::onBrightnessChanged,
                        valueRange = -0.5f..0.5f,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.editor_contrast), Modifier.padding(end = 8.dp))
                    Slider(
                        value = state.contrast,
                        onValueChange = viewModel::onContrastChanged,
                        valueRange = 0.5f..2f,
                    )
                }
            }

            Button(
                onClick = {
                    reportButtonClick("Сохранить страницу")
                    viewModel.onSave()
                },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
private fun DocumentFilter.label(): String = when (this) {
    DocumentFilter.ORIGINAL -> stringResource(R.string.filter_original)
    DocumentFilter.COLOR -> stringResource(R.string.filter_color)
    DocumentFilter.ENHANCE -> stringResource(R.string.filter_enhance)
    DocumentFilter.BLACK_WHITE -> stringResource(R.string.filter_bw)
    DocumentFilter.GRAYSCALE -> stringResource(R.string.filter_gray)
}

private fun DocumentFilter.analyticsName(): String = when (this) {
    DocumentFilter.ORIGINAL -> "Оригинал"
    DocumentFilter.COLOR -> "Цвет"
    DocumentFilter.ENHANCE -> "Улучшение"
    DocumentFilter.BLACK_WHITE -> "Черно-белый"
    DocumentFilter.GRAYSCALE -> "Серый"
}
