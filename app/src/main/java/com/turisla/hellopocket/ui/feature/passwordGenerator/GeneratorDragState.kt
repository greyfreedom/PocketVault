package com.turisla.hellopocket.ui.feature.passwordGenerator

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.turisla.hellopocket.model.GeneratorStep
import kotlin.math.abs

/** 使用惰性列表与边缘自动滚动，片段多时也无需一次创建全部卡片。 */
internal class GeneratorDragState(private val list: LazyListState) {
    var draggedId by mutableStateOf<String?>(null)
        private set
    var center by mutableFloatStateOf(0f)
        private set
    private var pendingIndex: Int? = null

    fun start(id: String) {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        draggedId = id
        center = item.offset + item.size / 2f
        pendingIndex = null
    }

    fun drag(delta: Float) { center += delta }

    fun translation(id: String): Float {
        if (draggedId != id) return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: return 0f
        return center - item.offset - item.size / 2f
    }

    fun stop() {
        draggedId = null
        center = 0f
        pendingIndex = null
    }

    fun reorder(steps: List<GeneratorStep>, onMove: (String, Int) -> Unit) {
        val id = draggedId ?: return
        val visible = list.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == id } ?: return
        // 等待上一次重排真正落到目标索引；滚动改变 offset 并不表示新顺序已布局。
        if (pendingIndex != null && dragged.index != pendingIndex) return
        pendingIndex = null
        val ids = steps.mapTo(mutableSetOf()) { it.id }
        val target = visible.filter { it.key in ids }
            .minByOrNull { abs(center - it.offset - it.size / 2f) } ?: return
        if (target.key == id) return
        val targetIndex = steps.indexOfFirst { it.id == target.key }
        if (targetIndex < 0) return
        pendingIndex = target.index
        // 默认按首个可见项的 key 保持视口，会跟随被拖动项连续错移；重排时固定视口索引。
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        onMove(id, targetIndex)
    }

    suspend fun scrollAtEdge(threshold: Float, speed: Float) {
        if (draggedId == null) return
        val layout = list.layoutInfo
        val direction = when {
            center < layout.viewportStartOffset + threshold -> -1
            center > layout.viewportEndOffset - threshold -> 1
            else -> 0
        }
        if (direction != 0) list.scrollBy(speed * direction)
    }
}

@Composable
internal fun rememberGeneratorDragState(
    list: LazyListState,
    steps: List<GeneratorStep>,
    onMove: (String, Int) -> Unit,
): GeneratorDragState {
    val state = remember(list) { GeneratorDragState(list) }
    val currentSteps by rememberUpdatedState(steps)
    val move by rememberUpdatedState(onMove)
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val speed = with(density) { 10.dp.toPx() }
    LaunchedEffect(state.draggedId) {
        while (state.draggedId != null) {
            withFrameNanos { }
            state.scrollAtEdge(edge, speed)
            state.reorder(currentSteps, move)
        }
    }
    return state
}
