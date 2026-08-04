package com.nla.AIscanerPDF.presentation.crop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.magnifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import com.nla.AIscanerPDF.R
import com.nla.AIscanerPDF.data.imageprocessing.BitmapLoader
import com.nla.AIscanerPDF.domain.model.CropPoint
import com.nla.AIscanerPDF.presentation.analytics.reportButtonClick
import com.nla.AIscanerPDF.presentation.common.LoadingState
import com.nla.AIscanerPDF.presentation.common.toMessage
import com.nla.AIscanerPDF.presentation.navigation.Routes
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(navController: NavHostController, viewModel: CropViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CropUiEffect.OpenEditor -> navController.navigate(Routes.editor(effect.pageId)) {
                    popUpTo(Routes.CROP) { inclusive = true }
                }
                is CropUiEffect.RetakeToCamera ->
                    navController.navigate(Routes.camera(effect.documentId)) {
                        popUpTo(Routes.CROP) { inclusive = true }
                    }
                CropUiEffect.NavigateBack -> navController.popBackStack()
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.crop_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.crop_corner_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val imagePath = state.imagePath
                if (imagePath == null || state.isApplying) {
                    LoadingState(label = if (state.isApplying) stringResource(R.string.editor_saving) else null)
                } else {
                    CropEditor(
                        imagePath = imagePath,
                        corners = state.corners.asList(),
                        isValid = state.isValidShape,
                        onCornerMoved = viewModel::onCornerMoved,
                        onDragFinished = viewModel::onDragFinished,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        reportButtonClick("Переснять страницу")
                        viewModel.onRetake()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(stringResource(R.string.crop_retake), Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = {
                        reportButtonClick("Сбросить обрезку")
                        viewModel.onReset()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(stringResource(R.string.crop_reset), Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = {
                        reportButtonClick("Автоопределить углы")
                        viewModel.onAutoDetect()
                    },
                    enabled = !state.isDetecting,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null)
                }
            }
            Button(
                onClick = {
                    reportButtonClick("Применить обрезку")
                    viewModel.onApply()
                },
                enabled = state.isValidShape && !state.isApplying,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text(stringResource(R.string.crop_apply), Modifier.padding(start = 4.dp))
            }
        }
    }
}

/**
 * Редактор границ: изображение + 4 перетаскиваемых угла с лупой.
 * Углы приходят и уходят в нормализованных координатах изображения.
 */
@Composable
private fun CropEditor(
    imagePath: String,
    corners: List<CropPoint>,
    isValid: Boolean,
    onCornerMoved: (Int, CropPoint) -> Unit,
    onDragFinished: () -> Unit,
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapLoader.decodeSampled(imagePath, 1600) }.getOrNull()
        }
    }
    val loaded = bitmap ?: run { LoadingState(); return }

    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val scale = minOf(boxW / loaded.width, boxH / loaded.height)
        val drawW = loaded.width * scale
        val drawH = loaded.height * scale
        val offsetX = (boxW - drawW) / 2f
        val offsetY = (boxH - drawH) / 2f

        fun toScreen(p: CropPoint) = Offset(offsetX + p.x * drawW, offsetY + p.y * drawH)
        fun toNormalized(o: Offset) = CropPoint(
            ((o.x - offsetX) / drawW).coerceIn(0f, 1f),
            ((o.y - offsetY) / drawH).coerceIn(0f, 1f),
        )

        var draggedIndex by remember { mutableStateOf(-1) }
        var magnifierCenter by remember { mutableStateOf(Offset.Unspecified) }

        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .magnifier(sourceCenter = { magnifierCenter }, zoom = 2f)
                .pointerInput(loaded) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val touchRadius = 64.dp.toPx()
                            draggedIndex = corners.indices.minByOrNull { i ->
                                val s = toScreen(corners[i])
                                hypot(s.x - start.x, s.y - start.y)
                            }?.takeIf { i ->
                                val s = toScreen(corners[i])
                                hypot(s.x - start.x, s.y - start.y) <= touchRadius
                            } ?: -1
                            if (draggedIndex >= 0) magnifierCenter = start
                        },
                        onDrag = { change, _ ->
                            if (draggedIndex >= 0) {
                                magnifierCenter = change.position
                                onCornerMoved(draggedIndex, toNormalized(change.position))
                            }
                        },
                        onDragEnd = {
                            if (draggedIndex >= 0) onDragFinished()
                            draggedIndex = -1
                            magnifierCenter = Offset.Unspecified
                        },
                        onDragCancel = {
                            draggedIndex = -1
                            magnifierCenter = Offset.Unspecified
                        },
                    )
                },
        ) {
            val screenPoints = corners.map { toScreen(it) }
            val lineColor = if (isValid) Color(0xFF2E7BFF) else Color(0xFFE53935)
            val path = Path().apply {
                moveTo(screenPoints[0].x, screenPoints[0].y)
                for (i in 1 until screenPoints.size) lineTo(screenPoints[i].x, screenPoints[i].y)
                close()
            }
            drawPath(path, color = lineColor.copy(alpha = 0.15f))
            drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
            screenPoints.forEach { point ->
                drawCircle(Color.White, radius = 12.dp.toPx(), center = point)
                drawCircle(lineColor, radius = 12.dp.toPx(), center = point, style = Stroke(3.dp.toPx()))
            }
        }
    }
}
