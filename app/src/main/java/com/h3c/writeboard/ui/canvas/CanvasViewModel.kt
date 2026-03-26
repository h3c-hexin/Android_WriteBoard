package com.h3c.writeboard.ui.canvas

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.h3c.writeboard.data.collab.CollabRepository
import com.h3c.writeboard.data.file.BoardRepository
import com.h3c.writeboard.data.share.ShareRepository
import com.h3c.writeboard.domain.model.CollabDevice
import com.h3c.writeboard.domain.model.CollabMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.h3c.writeboard.domain.model.ActiveTool
import com.h3c.writeboard.domain.model.DrawingPage
import com.h3c.writeboard.domain.model.DrawingToolState
import com.h3c.writeboard.domain.model.EraserSize
import com.h3c.writeboard.domain.model.PageBackground
import com.h3c.writeboard.domain.model.ShapeType
import com.h3c.writeboard.domain.model.Stroke
import com.h3c.writeboard.domain.model.StrokePoint
import com.h3c.writeboard.domain.model.StrokeTool
import com.h3c.writeboard.domain.model.StrokeWidth
import com.h3c.writeboard.domain.model.UndoAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import javax.inject.Inject

/**
 * 画布页面的 ViewModel
 * 管理：多页列表、当前页笔画列表、工具状态、Undo/Redo 栈（按页独立）
 */
@HiltViewModel
class CanvasViewModel @Inject constructor(
    private val boardRepository: BoardRepository,
    val shareRepository: ShareRepository,
    val collabRepository: CollabRepository
) : ViewModel() {

    companion object {
        const val MAX_PAGES = 20
    }

    override fun onCleared() {
        super.onCleared()
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.stopHosting()
            CollabRole.PARTICIPANT -> collabRepository.leaveSession()
            else -> Unit
        }
    }

    // ===== 自动存盘 Job =====
    private var autosaveJob: Job? = null

    // ===== 多页状态 =====
    private val _firstPage = DrawingPage(id = UUID.randomUUID().toString())
    private val _pages = MutableStateFlow(listOf(_firstPage))
    val pages: StateFlow<List<DrawingPage>> = _pages.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    /**
     * 当前页（同步镜像）：与 _pages[_currentPageIndex] 保持一致，
     * 使用独立 MutableStateFlow 确保 DrawingCanvas 等订阅者即时收到更新，
     * 避免 combine + stateIn 的异步延迟导致画布渲染滞后。
     */
    private val _page = MutableStateFlow(_firstPage)
    val page: StateFlow<DrawingPage> = _page.asStateFlow()

    // 自增计数器，JOIN_ACK 全量同步后 +1，通知 DrawingCanvas 清空 pathCache
    private val _pathCacheVersion = MutableStateFlow(0)
    val pathCacheVersion: StateFlow<Int> = _pathCacheVersion.asStateFlow()

    // ===== 工具状态 =====
    private val _toolState = MutableStateFlow(DrawingToolState())
    val toolState: StateFlow<DrawingToolState> = _toolState.asStateFlow()

    // ===== Undo/Redo 栈（按页 ID 独立，每页最多 50 步）=====
    private val undoStacks = mutableMapOf<String, ArrayDeque<UndoAction>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<UndoAction>>()
    private val maxUndoSteps = 50

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // ===== 颜色选择器 Bottom Sheet 显示状态 =====
    private val _showColorPicker = MutableStateFlow(false)
    val showColorPicker: StateFlow<Boolean> = _showColorPicker.asStateFlow()

    fun setShowColorPicker(show: Boolean) {
        _showColorPicker.value = show
    }

    // ===== 图形子面板显示状态 =====
    private val _showShapePanel = MutableStateFlow(false)
    val showShapePanel: StateFlow<Boolean> = _showShapePanel.asStateFlow()

    fun setShowShapePanel(show: Boolean) {
        _showShapePanel.value = show
    }

    // ===== 橡皮擦面板 Bottom Sheet 显示状态 =====
    private val _showEraserPanel = MutableStateFlow(false)
    val showEraserPanel: StateFlow<Boolean> = _showEraserPanel.asStateFlow()

    fun setShowEraserPanel(show: Boolean) {
        _showEraserPanel.value = show
    }

    // ===== 页面管理 Bottom Sheet 显示状态 =====
    private val _showPageManager = MutableStateFlow(false)
    val showPageManager: StateFlow<Boolean> = _showPageManager.asStateFlow()

    fun setShowPageManager(show: Boolean) {
        _showPageManager.value = show
    }

    // ===== 橡皮擦光标位置（归一化坐标，null 表示不显示光标）=====
    private val _eraserCursor = MutableStateFlow<Pair<Float, Float>?>(null)
    val eraserCursor: StateFlow<Pair<Float, Float>?> = _eraserCursor.asStateFlow()

    // ===== 启动恢复询问 =====
    private val _showRestoreDialog = MutableStateFlow(false)
    val showRestoreDialog: StateFlow<Boolean> = _showRestoreDialog.asStateFlow()

    init {
        // 有自动存盘时弹询问，不直接恢复
        if (!boardRepository.loadAutosave().isNullOrEmpty()) {
            _showRestoreDialog.value = true
        }
    }

    /** 用户选择恢复上次内容 */
    fun confirmRestore() {
        _showRestoreDialog.value = false
        val saved = boardRepository.loadAutosave() ?: return
        if (saved.isNotEmpty()) {
            _pages.value = saved
            _page.value = saved[0]
            updateUndoRedoState()
        }
    }

    /** 用户选择新建白板 */
    fun discardRestore() {
        _showRestoreDialog.value = false
    }

    // 本次橡皮擦手势：手势开始时的页面快照，用于抬手时对比 pre/post 得到真实 delta
    private var preErasureStrokes: List<Stroke> = emptyList()

    // ===== 正在绘制的笔画（临时状态，不在 page 中）=====
    private val _activeStroke = MutableStateFlow<List<StrokePoint>>(emptyList())
    val activeStroke: StateFlow<List<StrokePoint>> = _activeStroke.asStateFlow()

    // ===== 正在拖拽的图形（临时预览，不在 page 中）=====
    data class ShapeDrag(val startNx: Float, val startNy: Float, val endNx: Float, val endNy: Float)
    private val _activeShapeDrag = MutableStateFlow<ShapeDrag?>(null)
    val activeShapeDrag: StateFlow<ShapeDrag?> = _activeShapeDrag.asStateFlow()

    // 当前笔画 ID
    private var activeStrokeId: String = ""

    // ===== 页面辅助方法 =====

    private fun currentPageId(): String =
        _pages.value.getOrNull(_currentPageIndex.value)?.id ?: ""

    private fun currentPage(): DrawingPage =
        _pages.value.getOrElse(_currentPageIndex.value) { _pages.value.last() }

    private fun currentUndoStack(): ArrayDeque<UndoAction> =
        undoStacks.getOrPut(currentPageId()) { ArrayDeque() }

    private fun currentRedoStack(): ArrayDeque<UndoAction> =
        redoStacks.getOrPut(currentPageId()) { ArrayDeque() }

    /** 更新当前页内容（同时同步 _page 镜像，确保 DrawingCanvas 即时刷新） */
    private fun updateCurrentPage(transform: (DrawingPage) -> DrawingPage) {
        val idx = _currentPageIndex.value
        _pages.update { pages ->
            if (idx < 0 || idx >= pages.size) return@update pages
            val newList = pages.toMutableList()
            newList[idx] = transform(newList[idx])
            _page.value = newList[idx]   // 同步镜像
            newList
        }
        scheduleAutosave()
    }

    /** 2 秒防抖自动存盘：每次触发都会重置计时，最终一次性写入内部存储 */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2000L)
            boardRepository.saveAutosave(_pages.value)
        }
    }

    // ===== 触摸事件处理 =====

    /** 手指按下：开始新笔画 */
    fun onStrokeBegin(x: Float, y: Float, pressure: Float = 1.0f) {
        activeStrokeId = UUID.randomUUID().toString()
        _activeStroke.value = listOf(StrokePoint(x, y, pressure))
    }

    /** 手指移动：追加点（不做距离过滤，保留全部历史点以保证 FrontBuffer 与 Compose 使用相同点集）*/
    fun onStrokeMove(x: Float, y: Float, pressure: Float = 1.0f) {
        _activeStroke.update { it + StrokePoint(x, y, pressure) }
    }

    /**
     * 批量追加触摸点（历史点 + 当前点一次性写入，减少 StateFlow 更新次数和 GC 压力）。
     * DrawingCanvas 在每次 PointerEvent 时调用，替代逐点调用 onStrokeMove。
     */
    fun onStrokeMoveBatch(newPoints: List<StrokePoint>) {
        if (newPoints.isEmpty()) return
        _activeStroke.update { it + newPoints }
    }

    /**
     * 手指抬起：完成笔画，写入页面，压入 Undo 栈。
     * @param finalX 最终触摸 X（归一化），强制追加以绕过距离过滤，确保提交笔画与 FrontBuffer 末端一致
     * @param finalY 最终触摸 Y（归一化）
     * @param finalPressure 最终压感
     */
    fun onStrokeEnd(finalX: Float = Float.NaN, finalY: Float = Float.NaN, finalPressure: Float = 1.0f) {
        var points = _activeStroke.value
        // 强制追加最后一个触摸点（无论距离过滤），避免提交笔画尾部比 FrontBuffer 短
        if (!finalX.isNaN() && !finalY.isNaN()) {
            val last = points.lastOrNull()
            if (last == null || last.x != finalX || last.y != finalY) {
                points = points + StrokePoint(finalX, finalY, finalPressure)
            }
        }
        if (points.size < 2) {
            // 点击（无位移）：添加一个圆点笔画
            if (points.isEmpty()) return
            val dot = points.first()
            val doubleDot = listOf(dot, dot.copy(x = dot.x + 0.0001f))
            commitStroke(doubleDot)
        } else {
            commitStroke(points)
        }
        _activeStroke.value = emptyList()
    }

    private fun commitStroke(points: List<StrokePoint>) {
        val tool = _toolState.value
        val stroke = Stroke(
            id = activeStrokeId,
            points = points,
            color = tool.currentColor,
            width = tool.strokeWidth.fraction
        )
        // 追加到页面笔画列表
        updateCurrentPage { it.copy(strokes = it.strokes + stroke) }
        // 压入 Undo 栈，清空 Redo 栈
        pushUndo(UndoAction.AddStroke(stroke))
        // 协同广播
        broadcastStroke(_page.value.id, stroke)
    }

    // ===== Undo / Redo =====

    fun undo() {
        val action = currentUndoStack().removeLastOrNull() ?: return
        when (action) {
            is UndoAction.AddStroke -> {
                updateCurrentPage { it.copy(strokes = it.strokes - action.stroke) }
            }
            is UndoAction.EraseStrokes -> {
                updateCurrentPage { it.copy(strokes = it.strokes + action.strokes) }
            }
            is UndoAction.SplitEraseStrokes -> {
                // 撤销分割擦除：移除片段，恢复原始笔画
                updateCurrentPage { page ->
                    val restored = (page.strokes - action.addedSplits.toSet()) + action.removedOriginals
                    page.copy(strokes = restored)
                }
            }
            is UndoAction.ClearPage -> {
                updateCurrentPage { it.copy(strokes = action.strokes) }
            }
        }
        currentRedoStack().addLast(action)
        updateUndoRedoState()
        broadcastUndo(action)
    }

    fun redo() {
        val action = currentRedoStack().removeLastOrNull() ?: return
        when (action) {
            is UndoAction.AddStroke -> {
                updateCurrentPage { it.copy(strokes = it.strokes + action.stroke) }
            }
            is UndoAction.EraseStrokes -> {
                updateCurrentPage { it.copy(strokes = it.strokes - action.strokes.toSet()) }
            }
            is UndoAction.SplitEraseStrokes -> {
                // 重做分割擦除：移除原始笔画，加回片段
                updateCurrentPage { page ->
                    val redone = (page.strokes - action.removedOriginals.toSet()) + action.addedSplits
                    page.copy(strokes = redone)
                }
            }
            is UndoAction.ClearPage -> {
                updateCurrentPage { it.copy(strokes = emptyList()) }
            }
        }
        currentUndoStack().addLast(action)
        updateUndoRedoState()
        broadcastRedo(action)
    }

    private fun pushUndo(action: UndoAction) {
        val stack = currentUndoStack()
        if (stack.size >= maxUndoSteps) stack.removeFirst()
        stack.addLast(action)
        currentRedoStack().clear()
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.value = currentUndoStack().isNotEmpty()
        _canRedo.value = currentRedoStack().isNotEmpty()
    }

    // ===== 工具切换 =====

    fun setActiveTool(tool: ActiveTool) {
        _toolState.update { it.copy(activeTool = tool) }
    }

    fun setStrokeWidth(width: StrokeWidth) {
        _toolState.update { it.copy(strokeWidth = width) }
    }

    fun setEraserSize(size: EraserSize) {
        _toolState.update { it.copy(eraserSize = size) }
    }

    fun setSelectedShape(shape: ShapeType) {
        _toolState.update { it.copy(selectedShape = shape) }
    }

    // ===== 图形绘制手势 =====

    /** 手指按下：记录起点 */
    fun onShapeBegin(nx: Float, ny: Float) {
        _activeShapeDrag.value = ShapeDrag(nx, ny, nx, ny)
    }

    /** 手指移动：更新预览终点 */
    fun onShapeMove(nx: Float, ny: Float) {
        _activeShapeDrag.update { it?.copy(endNx = nx, endNy = ny) }
    }

    /** 手指抬起：提交图形到页面 */
    fun onShapeEnd() {
        val drag = _activeShapeDrag.value ?: return
        _activeShapeDrag.value = null
        val dx = drag.endNx - drag.startNx
        val dy = drag.endNy - drag.startNy
        if (dx * dx + dy * dy < 0.0001f) return  // 点击无位移，忽略
        val tool = _toolState.value
        val stroke = Stroke(
            id = UUID.randomUUID().toString(),
            points = listOf(
                StrokePoint(drag.startNx, drag.startNy),
                StrokePoint(drag.endNx, drag.endNy)
            ),
            color = tool.currentColor,
            width = tool.strokeWidth.fraction,
            tool = StrokeTool.SHAPE,
            shapeType = tool.selectedShape
        )
        updateCurrentPage { it.copy(strokes = it.strokes + stroke) }
        pushUndo(UndoAction.AddStroke(stroke))
        broadcastStroke(_page.value.id, stroke)
    }

    // ===== 橡皮擦操作 =====

    /** 手指按下：开始擦除手势，快照当前页状态 */
    fun onEraserBegin(nx: Float, ny: Float, radiusFraction: Float, aspectRatio: Float) {
        preErasureStrokes = currentPage().strokes.toList()
        _eraserCursor.value = Pair(nx, ny)
        doErase(nx, ny, radiusFraction, aspectRatio)
    }

    /** 手指移动：实时擦除 */
    fun onEraserMove(nx: Float, ny: Float, radiusFraction: Float, aspectRatio: Float) {
        _eraserCursor.value = Pair(nx, ny)
        doErase(nx, ny, radiusFraction, aspectRatio)
    }

    /** 手指抬起：结束擦除，用 pre/post 快照对比得到真实 delta，合并为一步 Undo */
    fun onEraserEnd() {
        _eraserCursor.value = null
        val post = currentPage().strokes.toList()
        val preIds = preErasureStrokes.map { it.id }.toSet()
        val postIds = post.map { it.id }.toSet()
        val removed = preErasureStrokes.filter { it.id !in postIds }
        val added   = post.filter { it.id !in preIds }
        if (removed.isNotEmpty() || added.isNotEmpty()) {
            pushUndo(UndoAction.SplitEraseStrokes(removedOriginals = removed, addedSplits = added))
            broadcastEraseOp(removed, added)
            hostBroadcastPageSyncDebounced()
        }
        preErasureStrokes = emptyList()
    }

    /**
     * 核心擦除逻辑：仅擦除橡皮圆覆盖的部分，圆外的笔画片段保留
     *
     * 算法：
     *   1. 对每个点判断是否在橡皮圆内（使用纵横比修正保证圆形判断在物理像素空间正确）
     *   2. 将连续的"圆外点"分组为新笔画段（≥2 点才成段）
     *   3. 原始笔画替换为 0~N 条新笔画段
     *
     * aspectRatio = canvasHeight / canvasWidth（用于 y 轴缩放，修正非正方形画布）
     */
    private fun doErase(nx: Float, ny: Float, radiusFraction: Float, aspectRatio: Float) {
        val rSq = radiusFraction * radiusFraction
        val page = currentPage()

        val newPageStrokes = mutableListOf<Stroke>()
        var hasChanges = false

        page.strokes.forEach { stroke ->
            // 快速排除：没有交集，直接保留
            // 图形笔画使用精确线段距离判断；普通笔画检查离散采样点
            val hasIntersection = if (stroke.tool == StrokeTool.SHAPE) {
                shapeIntersectsEraser(stroke, nx, ny, radiusFraction, aspectRatio)
            } else {
                stroke.points.any { pt ->
                    val dx = pt.x - nx
                    val dy = (pt.y - ny) * aspectRatio
                    dx * dx + dy * dy <= rSq
                }
            }
            if (!hasIntersection) {
                newPageStrokes.add(stroke)
                return@forEach
            }

            // 有交集：进行分割
            hasChanges = true
            val splits = splitStrokeByEraser(stroke, nx, ny, rSq, aspectRatio)
            newPageStrokes.addAll(splits)
        }

        if (hasChanges) {
            updateCurrentPage { it.copy(strokes = newPageStrokes) }
        }
    }

    /**
     * 精确判断橡皮擦圆是否与图形的可见边缘相交
     *
     * 对直线型图形（直线、矩形、三角、菱形、箭头），使用「点到线段的精确距离」。
     * 对椭圆，使用足够密的圆弧采样（60 点）。
     *
     * 所有距离计算均在「纵横比修正归一化空间」中进行，与 doErase 保持一致：
     *   x 轴不变，y 轴乘以 aspectRatio（= canvasH / canvasW）。
     */
    private fun shapeIntersectsEraser(
        stroke: Stroke,
        nx: Float, ny: Float,
        radiusFraction: Float,
        aspectRatio: Float
    ): Boolean {
        if (stroke.tool != StrokeTool.SHAPE || stroke.points.size < 2 || stroke.shapeType == null) return false
        val rSq = radiusFraction * radiusFraction
        val s = stroke.points[0]; val e = stroke.points[1]
        val sx = s.x; val sy = s.y; val ex = e.x; val ey = e.y
        val minX = minOf(sx, ex); val maxX = maxOf(sx, ex)
        val minY = minOf(sy, ey); val maxY = maxOf(sy, ey)
        val midX = (minX + maxX) / 2f; val midY = (minY + maxY) / 2f

        // 计算点 (nx, ny) 到线段 (ax,ay)→(bx,by) 距离的平方（纵横比修正空间）
        fun segDistSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dxAB = bx - ax;        val dyAB = (by - ay) * aspectRatio
            val dxAP = nx - ax;        val dyAP = (ny - ay) * aspectRatio
            val lenSq = dxAB * dxAB + dyAB * dyAB
            val t = if (lenSq < 1e-12f) 0f else ((dxAP * dxAB + dyAP * dyAB) / lenSq).coerceIn(0f, 1f)
            val cx = ax + t * (bx - ax); val cy = (ay + t * (by - ay)) * aspectRatio
            val dx = nx - cx;            val dy = ny * aspectRatio - cy
            return dx * dx + dy * dy
        }

        fun segsHit(vararg segs: Float): Boolean {
            // segs: [ax0,ay0,bx0,by0, ax1,ay1,bx1,by1, ...]
            var i = 0
            while (i < segs.size) {
                if (segDistSq(segs[i], segs[i+1], segs[i+2], segs[i+3]) <= rSq) return true
                i += 4
            }
            return false
        }

        return when (stroke.shapeType) {
            ShapeType.LINE ->
                segsHit(sx, sy, ex, ey)
            ShapeType.ARROW -> {
                // 主干
                if (segsHit(sx, sy, ex, ey)) return true
                // 两条翼段：复现 ShapeRenderer.drawArrow 的几何（在纵横比修正空间计算）
                val dxC = ex - sx; val dyC = (ey - sy) * aspectRatio
                val lenC = Math.sqrt((dxC * dxC + dyC * dyC).toDouble()).toFloat()
                if (lenC < 1e-4f) return false
                val arrowLenC = lenC * 0.18f
                val angle = Math.atan2(dyC.toDouble(), dxC.toDouble())
                val a1 = angle + Math.PI * 5.0 / 6.0
                val a2 = angle - Math.PI * 5.0 / 6.0
                // 将纵横比修正空间的翼端点换算回归一化坐标
                val w1x = ex + (arrowLenC * Math.cos(a1)).toFloat()
                val w1y = ey + (arrowLenC * Math.sin(a1)).toFloat() / aspectRatio
                val w2x = ex + (arrowLenC * Math.cos(a2)).toFloat()
                val w2y = ey + (arrowLenC * Math.sin(a2)).toFloat() / aspectRatio
                segsHit(ex, ey, w1x, w1y, ex, ey, w2x, w2y)
            }
            ShapeType.RECTANGLE ->
                segsHit(
                    minX, minY, maxX, minY,   // 上
                    maxX, minY, maxX, maxY,   // 右
                    maxX, maxY, minX, maxY,   // 下
                    minX, maxY, minX, minY    // 左
                )
            ShapeType.TRIANGLE ->
                segsHit(
                    midX, minY, minX, maxY,   // 顶→左下
                    minX, maxY, maxX, maxY,   // 底
                    maxX, maxY, midX, minY    // 右下→顶
                )
            ShapeType.DIAMOND ->
                segsHit(
                    midX, minY, maxX, midY,   // 顶→右
                    maxX, midY, midX, maxY,   // 右→底
                    midX, maxY, minX, midY,   // 底→左
                    minX, midY, midX, minY    // 左→顶
                )
            ShapeType.CIRCLE -> {
                // 椭圆用 60 点采样，弧长足够短，不会产生遗漏
                val rx = (maxX - minX) / 2f; val ry = (maxY - minY) / 2f
                (0 until 60).any { i ->
                    val angle = 2 * Math.PI * i / 60
                    val px = (midX + rx * Math.cos(angle)).toFloat()
                    val py = (midY + ry * Math.sin(angle)).toFloat()
                    val dx = px - nx; val dy = (py - ny) * aspectRatio
                    dx * dx + dy * dy <= rSq
                }
            }
        }
    }

    /**
     * 将笔画按橡皮圆分割为 0~N 条片段
     *
     * 遍历所有采样点，把落在圆外的连续点分组为独立笔画（≥2 点才成段）。
     * 图形笔画是原子对象，直接返回空列表（整体删除，不分割）。
     */
    private fun splitStrokeByEraser(
        stroke: Stroke,
        nx: Float, ny: Float,
        rSq: Float,
        aspectRatio: Float
    ): List<Stroke> {
        // 图形是原子对象：只要橡皮擦触碰到任意边缘，整个图形删除（不分割）
        if (stroke.tool == StrokeTool.SHAPE) return emptyList()

        val segments = mutableListOf<Stroke>()
        var currentSeg = mutableListOf<StrokePoint>()

        stroke.points.forEach { pt ->
            val dx = pt.x - nx
            val dy = (pt.y - ny) * aspectRatio
            val insideEraser = dx * dx + dy * dy <= rSq

            if (insideEraser) {
                // 橡皮圆内的点：结束当前片段（若够长则保存）
                if (currentSeg.size >= 2) {
                    segments.add(stroke.copy(id = UUID.randomUUID().toString(), points = currentSeg.toList()))
                }
                currentSeg = mutableListOf()
            } else {
                currentSeg.add(pt)
            }
        }
        // 末尾残余片段
        if (currentSeg.size >= 2) {
            segments.add(stroke.copy(id = UUID.randomUUID().toString(), points = currentSeg.toList()))
        }
        return segments
    }

    /** 清除当前页全部笔画，可撤销 */
    fun clearPage() {
        val strokes = currentPage().strokes
        if (strokes.isEmpty()) return
        pushUndo(UndoAction.ClearPage(strokes))
        updateCurrentPage { it.copy(strokes = emptyList()) }
        broadcastPageClear()
        hostBroadcastPageSyncDebounced()
    }

    fun setColor(color: Int) {
        _toolState.update { state ->
            // 将当前颜色加入最近使用列表（去重，最多 5 个）
            val recent = (listOf(state.currentColor) + state.recentColors)
                .distinct()
                .take(5)
            state.copy(currentColor = color, recentColors = recent)
        }
    }

    // ===== 页面管理 =====

    /** 切换到指定页，更新 undo/redo 按钮状态 */
    fun switchPage(index: Int) {
        val pages = _pages.value
        if (index < 0 || index >= pages.size) return
        _currentPageIndex.value = index
        _page.value = pages[index]     // 同步镜像
        updateUndoRedoState()
        broadcastPageSwitch(index)
    }

    /** 在末尾新增一页（最多 20 页）*/
    fun addPage() {
        val pages = _pages.value
        if (pages.size >= MAX_PAGES) return
        val newPage = DrawingPage(
            id = UUID.randomUUID().toString(),
            background = pages.last().background   // 继承最后一页背景类型
        )
        val newPages = pages + newPage
        _pages.value = newPages
        val newIdx = newPages.size - 1
        _currentPageIndex.value = newIdx
        _page.value = newPage           // 同步镜像
        updateUndoRedoState()
        scheduleAutosave()
        broadcastPageAdd(newPage, newIdx)
    }

    /**
     * 删除指定页（最少保留 1 页）
     * 若删除的是当前页，自动切换至相邻页（优先前一页）
     */
    fun deletePage(index: Int) {
        val pages = _pages.value
        if (pages.size <= 1 || index < 0 || index >= pages.size) return
        val removedId = pages[index].id
        val newPages = pages.toMutableList().also { it.removeAt(index) }
        _pages.value = newPages
        undoStacks.remove(removedId)
        redoStacks.remove(removedId)
        val newIdx = when {
            _currentPageIndex.value >= newPages.size -> newPages.size - 1
            _currentPageIndex.value > index -> _currentPageIndex.value - 1
            else -> _currentPageIndex.value
        }
        _currentPageIndex.value = newIdx
        _page.value = newPages[newIdx]  // 同步镜像
        updateUndoRedoState()
        scheduleAutosave()
        broadcastPageDelete(removedId)
    }

    /**
     * 将第 index 页与第 index-1 页互换（上移）
     * 若 index <= 0 则无效
     */
    fun movePageUp(index: Int) {
        val pages = _pages.value
        if (index <= 0 || index >= pages.size) return
        val newPages = pages.toMutableList()
        val tmp = newPages[index]; newPages[index] = newPages[index - 1]; newPages[index - 1] = tmp
        _pages.value = newPages
        val cur = _currentPageIndex.value
        if (cur == index) _currentPageIndex.value = index - 1
        else if (cur == index - 1) _currentPageIndex.value = index
        // _page 内容不变，只是位置移动，无需更新镜像
        scheduleAutosave()
    }

    /**
     * 将第 index 页与第 index+1 页互换（下移）
     * 若 index >= last 则无效
     */
    fun movePageDown(index: Int) {
        val pages = _pages.value
        if (index < 0 || index >= pages.size - 1) return
        val newPages = pages.toMutableList()
        val tmp = newPages[index]; newPages[index] = newPages[index + 1]; newPages[index + 1] = tmp
        _pages.value = newPages
        val cur = _currentPageIndex.value
        if (cur == index) _currentPageIndex.value = index + 1
        else if (cur == index + 1) _currentPageIndex.value = index
        scheduleAutosave()
    }

    /** 设置指定页的背景类型 */
    fun setPageBackground(index: Int, background: PageBackground) {
        _pages.update { pages ->
            if (index < 0 || index >= pages.size) return@update pages
            val newList = pages.toMutableList()
            newList[index] = newList[index].copy(background = background)
            // 若修改的是当前页，同步镜像
            if (index == _currentPageIndex.value) _page.value = newList[index]
            newList
        }
        scheduleAutosave()
    }

    // ===== 文件操作 =====

    /** 当前文件名（null = 未保存/新建）*/
    private val _currentFileName = MutableStateFlow<String?>(null)
    val currentFileName: StateFlow<String?> = _currentFileName.asStateFlow()

    /** 将所有页面保存到用户选择的 URI */
    fun saveBoard(uri: Uri, displayName: String) {
        boardRepository.save(_pages.value, uri)
        _currentFileName.value = displayName
    }

    /** 从用户选择的 URI 加载白板，替换所有页面和 Undo 栈 */
    fun loadBoard(uri: Uri, displayName: String) {
        val loaded = boardRepository.load(uri) ?: return
        val firstPage = loaded.firstOrNull() ?: return
        undoStacks.clear()
        redoStacks.clear()
        _pages.value = loaded
        _currentPageIndex.value = 0
        _page.value = firstPage
        _currentFileName.value = displayName
        updateUndoRedoState()
    }

    // ===== 分享状态 =====

    data class ShareUiState(
        val isLoading: Boolean = false,
        val shareUrl: String? = null,
        val error: String? = null
    )

    private val _shareState = MutableStateFlow(ShareUiState())
    val shareState: StateFlow<ShareUiState> = _shareState.asStateFlow()

    private val _showSharePanel = MutableStateFlow(false)
    val showSharePanel: StateFlow<Boolean> = _showSharePanel.asStateFlow()

    private val _showFullscreenQR = MutableStateFlow(false)
    val showFullscreenQR: StateFlow<Boolean> = _showFullscreenQR.asStateFlow()

    /** 打开分享面板：立即显示面板，后台渲染页面 + 启动 HTTP 服务 */
    fun openSharePanel() {
        _showSharePanel.value = true
        _shareState.value = ShareUiState(isLoading = true)
        viewModelScope.launch {
            val url = shareRepository.prepareShare(_pages.value)
            _shareState.value = if (url != null) {
                ShareUiState(shareUrl = url)
            } else {
                ShareUiState(error = "未连接 WiFi，无法开启分享")
            }
        }
    }

    fun closeSharePanel() {
        _showSharePanel.value = false
    }

    /** 切换到全屏二维码（关闭小面板）*/
    fun openFullscreenQR() {
        _showSharePanel.value = false
        _showFullscreenQR.value = true
    }

    fun closeFullscreenQR() {
        _showFullscreenQR.value = false
    }

    /** 新建空白白板（清空所有页面和历史）*/
    fun newBoard() {
        undoStacks.clear()
        redoStacks.clear()
        val blank = DrawingPage(id = UUID.randomUUID().toString())
        _pages.value = listOf(blank)
        _currentPageIndex.value = 0
        _page.value = blank
        _currentFileName.value = null
        updateUndoRedoState()
        // 取消防抖，立即落盘空白板（确保下次启动不恢复旧内容）
        autosaveJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            boardRepository.saveAutosave(_pages.value)
        }
    }

    // ===== 协同状态 =====

    enum class CollabRole { NONE, CONNECTING, HOST, PARTICIPANT }

    data class CollabUiState(
        val role: CollabRole = CollabRole.NONE,
        val roomCode: String? = null,
        val hostIp: String? = null,
        val devices: List<CollabDevice> = emptyList(),
        val error: String? = null,
        val nsdTimeout: Boolean = false,
        val lastHost: String? = null,
        val lastRoomCode: String? = null
    )

    private val _collabState = MutableStateFlow(CollabUiState())
    val collabState: StateFlow<CollabUiState> = _collabState.asStateFlow()

    private val _showCollabPanel = MutableStateFlow(false)
    val showCollabPanel: StateFlow<Boolean> = _showCollabPanel.asStateFlow()

    // 上次成功加入的连接信息（供"重新加入"使用）
    private var _lastHost: String? = null
    private var _lastRoomCode: String? = null

    fun openCollabPanel() { _showCollabPanel.value = true }
    fun closeCollabPanel() { _showCollabPanel.value = false }

    // ===== Host：开启 / 结束协同 =====

    fun startHosting() {
        collabRepository.onDeviceListChanged = { devices ->
            _collabState.update { it.copy(devices = devices) }
        }
        collabRepository.onMessageReceived = { msg -> applyRemoteMessage(msg) }
        collabRepository.onNetworkLost = {
            viewModelScope.launch {
                collabRepository.stopHosting()
                collabRepository.onDeviceListChanged = null
                collabRepository.onMessageReceived = null
                collabRepository.onNetworkLost = null
                _collabState.value = CollabUiState(
                    role = CollabRole.NONE,
                    error = "网络已断开，协同已停止"
                )
            }
        }

        val roomCode = collabRepository.startHosting(
            hostDeviceName = "大屏",
            getPages = { _pages.value },
            getCurrentPageIndex = { _currentPageIndex.value }
        )
        _collabState.value = CollabUiState(
            role = CollabRole.HOST,
            roomCode = roomCode,
            hostIp = collabRepository.getLocalIp(),
            devices = collabRepository.getConnectedDevices()
        )
    }

    fun stopHosting() {
        collabRepository.stopHosting()
        collabRepository.onDeviceListChanged = null
        collabRepository.onMessageReceived = null
        collabRepository.onNetworkLost = null
        _collabState.value = CollabUiState()
    }

    // ===== PAD：加入 / 离开 =====

    private fun registerJoinCallbacks() {
        collabRepository.onDeviceListChanged = { devices ->
            _collabState.update { it.copy(devices = devices) }
        }
        collabRepository.onMessageReceived = { msg -> applyRemoteMessage(msg) }
        collabRepository.onSessionEnded = {
            _collabState.value = CollabUiState()
            _showCollabPanel.value = false
        }
        collabRepository.onConnectionLost = {
            _collabState.value = CollabUiState(
                role = CollabRole.NONE,
                error = "连接已断开",
                lastHost = _lastHost,
                lastRoomCode = _lastRoomCode
            )
        }
        collabRepository.onJoinFailed = { errMsg ->
            if (errMsg == CollabRepository.ERR_NSD_TIMEOUT) {
                _collabState.update { it.copy(
                    role = CollabRole.NONE,
                    error = "未找到该房间（可能存在网络隔离）\n请让主设备告知 IP 地址后手动连接",
                    nsdTimeout = true
                )}
            } else {
                _collabState.update { it.copy(role = CollabRole.NONE, error = errMsg, nsdTimeout = false) }
            }
        }
    }

    fun joinSession(roomCode: String) {
        _lastRoomCode = roomCode
        registerJoinCallbacks()
        _collabState.value = CollabUiState(role = CollabRole.CONNECTING)
        collabRepository.joinSession(roomCode = roomCode, deviceName = "PAD")
    }

    fun joinSessionDirect(host: String, roomCode: String) {
        _lastHost = host
        _lastRoomCode = roomCode
        registerJoinCallbacks()
        _collabState.value = CollabUiState(role = CollabRole.CONNECTING)
        collabRepository.joinSessionDirect(host = host, roomCode = roomCode, deviceName = "PAD")
    }

    /** 用户主动离开：清除上次连接记录 */
    fun leaveSession() {
        collabRepository.leaveSession()
        collabRepository.onDeviceListChanged = null
        collabRepository.onMessageReceived = null
        collabRepository.onSessionEnded = null
        collabRepository.onConnectionLost = null
        collabRepository.onJoinFailed = null
        _lastHost = null
        _lastRoomCode = null
        _collabState.value = CollabUiState()
    }

    /** 退后台等被动断开：保留上次连接记录 */
    fun leaveSessionKeepHistory() {
        collabRepository.leaveSession()
        collabRepository.onDeviceListChanged = null
        collabRepository.onMessageReceived = null
        collabRepository.onSessionEnded = null
        collabRepository.onConnectionLost = null
        collabRepository.onJoinFailed = null
        _collabState.value = CollabUiState(lastHost = _lastHost, lastRoomCode = _lastRoomCode)
    }

    /** 重新���入上次协同 */
    fun rejoinLastSession() {
        val host = _lastHost ?: return
        val room = _lastRoomCode ?: return
        joinSessionDirect(host, room)
    }

    // ===== 本地操作广播给远端 =====

    /** 笔画完成后广播（Host 广播给所有 PAD，PAD 发给 Host） */
    private fun broadcastStroke(pageId: String, stroke: Stroke) {
        val msg = CollabMessage(
            type = CollabMessage.STROKE_ADD,
            pageId = pageId,
            stroke = stroke
        )
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.broadcastToAll(msg)
            CollabRole.PARTICIPANT -> collabRepository.sendToHost(msg)
            CollabRole.NONE, CollabRole.CONNECTING -> Unit
        }
    }

    private fun broadcastPageAdd(page: DrawingPage, pageIndex: Int) {
        val msg = CollabMessage(type = CollabMessage.PAGE_ADD, page = page, pageIndex = pageIndex)
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.broadcastToAll(msg)
            CollabRole.PARTICIPANT -> collabRepository.sendToHost(msg)
            CollabRole.NONE, CollabRole.CONNECTING -> Unit
        }
    }

    private fun broadcastPageDelete(pageId: String) {
        val msg = CollabMessage(type = CollabMessage.PAGE_DELETE, pageId = pageId)
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.broadcastToAll(msg)
            CollabRole.PARTICIPANT -> collabRepository.sendToHost(msg)
            CollabRole.NONE, CollabRole.CONNECTING -> Unit
        }
    }

    private fun broadcast(msg: CollabMessage) {
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.broadcastToAll(msg)
            CollabRole.PARTICIPANT -> collabRepository.sendToHost(msg)
            CollabRole.NONE, CollabRole.CONNECTING -> Unit
        }
    }

    private fun broadcastEraseOp(removed: List<Stroke>, added: List<Stroke>) {
        broadcast(CollabMessage(
            type = CollabMessage.ERASE_OP,
            pageId = _page.value.id,
            removedIds = removed.map { it.id },
            strokes = added.ifEmpty { null }
        ))
    }

    private fun broadcastPageClear() {
        broadcast(CollabMessage(type = CollabMessage.PAGE_CLEAR, pageId = _page.value.id))
    }

    /**
     * HOST 权威同步（debounce 300ms）：擦除/清除操作后将当前页最终状态广播给所有端。
     * 延迟发送，让子设备的 STROKE_ADD 有时间到达 HOST，避免全量同步覆盖刚提交的笔画。
     */
    private var pageSyncJob: Job? = null
    private fun hostBroadcastPageSyncDebounced() {
        if (_collabState.value.role != CollabRole.HOST) return
        pageSyncJob?.cancel()
        pageSyncJob = viewModelScope.launch {
            delay(300)
            val page = _page.value
            collabRepository.broadcastToAll(CollabMessage(
                type = CollabMessage.PAGE_STROKES_SYNC,
                pageId = page.id,
                strokes = page.strokes
            ))
        }
    }

    private fun broadcastUndo(action: UndoAction) {
        val pageId = _page.value.id
        val msg = when (action) {
            is UndoAction.AddStroke -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                removedIds = listOf(action.stroke.id)
            )
            is UndoAction.EraseStrokes -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                strokes = action.strokes
            )
            is UndoAction.SplitEraseStrokes -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                removedIds = action.addedSplits.map { it.id },
                strokes = action.removedOriginals.ifEmpty { null }
            )
            is UndoAction.ClearPage -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                strokes = action.strokes.ifEmpty { null }
            )
        }
        broadcast(msg)
    }

    private fun broadcastRedo(action: UndoAction) {
        val pageId = _page.value.id
        val msg = when (action) {
            is UndoAction.AddStroke -> CollabMessage(
                type = CollabMessage.STROKE_ADD, pageId = pageId,
                stroke = action.stroke
            )
            is UndoAction.EraseStrokes -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                removedIds = action.strokes.map { it.id }
            )
            is UndoAction.SplitEraseStrokes -> CollabMessage(
                type = CollabMessage.ERASE_OP, pageId = pageId,
                removedIds = action.removedOriginals.map { it.id },
                strokes = action.addedSplits.ifEmpty { null }
            )
            is UndoAction.ClearPage -> CollabMessage(
                type = CollabMessage.PAGE_CLEAR, pageId = pageId
            )
        }
        broadcast(msg)
    }

    private fun broadcastPageSwitch(index: Int) {
        val msg = CollabMessage(type = CollabMessage.PAGE_SWITCH, pageIndex = index)
        when (_collabState.value.role) {
            CollabRole.HOST -> collabRepository.broadcastToAll(msg)
            CollabRole.PARTICIPANT -> collabRepository.sendToHost(msg)
            CollabRole.NONE, CollabRole.CONNECTING -> Unit
        }
    }

    // ===== 应用远端消息 =====

    private fun applyRemoteMessage(msg: CollabMessage) {
        viewModelScope.launch {
            when (msg.type) {
                CollabMessage.FULL_SYNC -> {
                    val pages = msg.pages ?: return@launch
                    if (pages.isEmpty()) return@launch
                    _pages.value = pages
                    _currentPageIndex.value = 0
                    _page.value = pages[0]
                    updateUndoRedoState()
                }
                CollabMessage.STROKE_ADD -> {
                    val stroke = msg.stroke ?: return@launch
                    val pageId = msg.pageId ?: return@launch
                    _pages.update { list ->
                        list.map { p ->
                            if (p.id == pageId) p.copy(strokes = p.strokes + stroke) else p
                        }
                    }
                    if (_page.value.id == pageId) {
                        _page.value = _pages.value[_currentPageIndex.value]
                    }
                }
                CollabMessage.PAGE_ADD -> {
                    val page = msg.page ?: return@launch
                    if (_pages.value.any { it.id == page.id }) return@launch
                    _pages.update { it + page }
                    msg.pageIndex?.let { idx ->
                        val list = _pages.value
                        if (idx in list.indices) {
                            _currentPageIndex.value = idx
                            _page.value = list[idx]
                        }
                    }
                }
                CollabMessage.PAGE_DELETE -> {
                    val pageId = msg.pageId ?: return@launch
                    val list = _pages.value
                    if (list.size <= 1) return@launch
                    val idx = list.indexOfFirst { it.id == pageId }
                    if (idx < 0) return@launch
                    val newList = list.toMutableList().also { it.removeAt(idx) }
                    val newIdx = (_currentPageIndex.value).coerceIn(0, newList.size - 1)
                    _pages.value = newList
                    _currentPageIndex.value = newIdx
                    _page.value = newList[newIdx]
                    updateUndoRedoState()
                }
                CollabMessage.PAGE_SWITCH -> {
                    val idx = msg.pageIndex ?: return@launch
                    val list = _pages.value
                    if (idx !in list.indices) return@launch
                    _currentPageIndex.value = idx
                    _page.value = list[idx]
                }
                CollabMessage.JOIN_ACK -> {
                    // 保存连接信息供"重新加入"使用
                    _lastHost = collabRepository.connectedHostIp
                    _collabState.update { it.copy(role = CollabRole.PARTICIPANT) }
                    val pages = msg.pages ?: return@launch
                    if (pages.isEmpty()) return@launch
                    _pages.value = pages
                    val idx = (msg.pageIndex ?: 0).coerceIn(0, pages.size - 1)
                    _currentPageIndex.value = idx
                    // 强制新引用，确保 StateFlow 发射触发 Canvas 重绘
                    _page.value = pages[idx].copy()
                    _pathCacheVersion.value++
                    updateUndoRedoState()
                }
                CollabMessage.PAGE_STROKES_SYNC -> {
                    val pageId = msg.pageId ?: return@launch
                    val strokes = msg.strokes ?: return@launch
                    _pages.update { list ->
                        list.map { p -> if (p.id == pageId) p.copy(strokes = strokes) else p }
                    }
                    if (_page.value.id == pageId) {
                        _page.value = _pages.value[_currentPageIndex.value].copy()
                        _pathCacheVersion.value++
                    }
                }
                CollabMessage.ERASE_OP -> {
                    val pageId = msg.pageId ?: return@launch
                    val toRemove = msg.removedIds?.toSet() ?: emptySet()
                    val toAdd = msg.strokes ?: emptyList()
                    _pages.update { list ->
                        list.map { p ->
                            if (p.id != pageId) p
                            else {
                                // toRemove 为空时（undo 恢复操作）直接追加；
                                // toRemove 非空时必须至少有一个 ID 存在，防止并发擦除产生幽灵笔画
                                val shouldAdd = toRemove.isEmpty() ||
                                    toRemove.any { id -> p.strokes.any { it.id == id } }
                                val filtered = p.strokes.filter { it.id !in toRemove }
                                p.copy(strokes = if (shouldAdd) filtered + toAdd else filtered)
                            }
                        }
                    }
                    if (_page.value.id == pageId) {
                        _page.value = _pages.value[_currentPageIndex.value]
                    }
                    hostBroadcastPageSyncDebounced()
                }
                CollabMessage.PAGE_CLEAR -> {
                    val pageId = msg.pageId ?: return@launch
                    _pages.update { list ->
                        list.map { p -> if (p.id == pageId) p.copy(strokes = emptyList()) else p }
                    }
                    if (_page.value.id == pageId) {
                        _page.value = _pages.value[_currentPageIndex.value]
                    }
                    hostBroadcastPageSyncDebounced()
                }
            }
        }
    }
}
