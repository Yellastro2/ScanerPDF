package com.nla.AIscanerPDF.presentation.common

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Простой drag-and-drop для LazyColumn (перестановка страниц, п. 5.5 ТЗ).
 * Перетаскивание запускается long-press; целевая позиция определяется
 * пересечением середины перетаскиваемого элемента с соседним.
 */
class DragDropState(
    private val listState: LazyListState,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
) {
    var draggingItemIndex: Int? by mutableStateOf(null)
        private set

    private var draggedDelta by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset by mutableIntStateOf(0)

    /** Смещение перетаскиваемого элемента относительно его текущей позиции в списке. */
    val draggingItemOffset: Float
        get() {
            val index = draggingItemIndex ?: return 0f
            val item = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index } ?: return 0f
            return draggingItemInitialOffset + draggedDelta - item.offset
        }

    fun onDragStart(offset: Offset) {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
            ?.also { item ->
                draggingItemIndex = item.index
                draggingItemInitialOffset = item.offset
            }
    }

    fun onDrag(deltaY: Float) {
        draggedDelta += deltaY
        val index = draggingItemIndex ?: return
        val current = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == index } ?: return
        val middle = current.offset + draggingItemOffset + current.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.index != index &&
                middle.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return
        onMove(index, target.index)
        draggingItemIndex = target.index
        draggingItemInitialOffset = target.offset
        draggedDelta = 0f
    }

    fun onDragEnd() {
        draggingItemIndex = null
        draggedDelta = 0f
        draggingItemInitialOffset = 0
    }
}

fun Modifier.dragContainer(state: DragDropState): Modifier = pointerInput(state) {
    detectDragGesturesAfterLongPress(
        onDragStart = state::onDragStart,
        onDrag = { change, dragAmount ->
            change.consume()
            state.onDrag(dragAmount.y)
        },
        onDragEnd = state::onDragEnd,
        onDragCancel = state::onDragEnd,
    )
}
