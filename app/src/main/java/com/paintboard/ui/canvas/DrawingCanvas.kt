package com.paintboard.ui.canvas

import android.graphics.Paint
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paintboard.domain.model.ActiveTool
import com.paintboard.domain.model.PageBackground
import com.paintboard.domain.model.StrokePoint
import com.paintboard.domain.model.StrokeTool
import kotlin.math.sqrt

/**
 * 画布 Composable
 *
 * 渲染策略（书写流畅度优化后）：
 *   - 已提交笔画：路径缓存（PathCache），避免每帧重建 Path 对象
 *   - 活跃笔画（API 29+）：由 FrontBufferStrokeView 渲染，绕过 Vsync
 *   - 活跃笔画（API < 29）：Compose 画布实时绘制 + StrokePredictor 预测延伸
 *   - 触摸采样：读取 PointerInputChange.historical 补全帧间中间点
 *
 * 触摸路由优先级：
 *   1. 显式橡皮模式 → 擦除
 *   2. 触控笔橡皮端（PointerType.Eraser）→ 擦除
 *   3. PAD 模式手指触摸 → 擦除
 *   4. 其余 → 书写
 *
 * FrontBuffer 接入通过三个回调传入，默认 null 表示使用 Compose 渲染路径（兼容 API < 29）：
 *   onFrontBufferStrokeStart  — 手势开始，传入颜色和线宽
 *   onFrontBufferStrokePoints — 移动中，传入当前全量点列表 + 画布尺寸
 *   onFrontBufferStrokeDone   — 手势结束，通知清空前端缓冲
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = hiltViewModel(),
    onFrontBufferStrokeStart: ((color: Int, widthPx: Float) -> Unit)? = null,
    onFrontBufferStrokePoints: ((points: List<StrokePoint>, wPx: Float, hPx: Float) -> Unit)? = null,
    onFrontBufferStrokeDone: (() -> Unit)? = null
) {
    val hasFrontBuffer = onFrontBufferStrokePoints != null

    val page            by viewModel.page.collectAsState()
    val toolState       by viewModel.toolState.collectAsState()
    val eraserCursor    by viewModel.eraserCursor.collectAsState()
    val activeShapeDrag by viewModel.activeShapeDrag.collectAsState()

    // FrontBuffer 激活时不订阅 activeStroke（避免每次触摸都触发 DrawingCanvas 重组）
    val activeStroke by if (!hasFrontBuffer) {
        viewModel.activeStroke.collectAsState()
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    val context = LocalContext.current
    val isPadMode = remember {
        val dm = context.resources.displayMetrics
        val wIn = dm.widthPixels / dm.xdpi
        val hIn = dm.heightPixels / dm.ydpi
        sqrt((wIn * wIn + hIn * hIn).toDouble()) < 20.0
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val predictor = remember { StrokePredictor() }

    // 路径缓存：key = stroke.id，避免每帧重建 Path
    val pathCache = remember { HashMap<String, android.graphics.Path>() }

    // 笔画删除时清理缓存（擦除、Undo）
    LaunchedEffect(page.strokes) {
        val currentIds = page.strokes.mapTo(HashSet()) { it.id }
        pathCache.keys.retainAll(currentIds)
    }
    // 画布尺寸变化时清空缓存（路径以像素为单位，尺寸变化后需重建）
    LaunchedEffect(canvasSize) { pathCache.clear() }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = canvasSize.width.toFloat()
                    val h = canvasSize.height.toFloat()
                    if (w <= 0f || h <= 0f) return@awaitEachGesture

                    val tState = toolState
                    val nx = down.position.x / w
                    val ny = down.position.y / h

                    val isErase = when {
                        tState.activeTool == ActiveTool.ERASER      -> true
                        down.type == PointerType.Eraser              -> true
                        isPadMode && down.type == PointerType.Touch  -> true
                        else -> false
                    }
                    val isShape = tState.activeTool == ActiveTool.SHAPE && !isErase
                    val radiusFraction = tState.eraserSize.fraction
                    val aspectRatio = if (w > 0f) h / w else 1f

                    when {
                        isErase -> {
                            // ===== 橡皮擦手势（不变）=====
                            viewModel.onEraserBegin(nx, ny, radiusFraction, aspectRatio)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    viewModel.onEraserMove(
                                        change.position.x / w, change.position.y / h,
                                        radiusFraction, aspectRatio
                                    )
                                    change.consume()
                                } else { viewModel.onEraserEnd(); break }
                            } while (true)
                        }
                        isShape -> {
                            // ===== 图形拖拽手势（不变）=====
                            viewModel.onShapeBegin(nx, ny)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    viewModel.onShapeMove(change.position.x / w, change.position.y / h)
                                    change.consume()
                                } else { viewModel.onShapeEnd(); break }
                            } while (true)
                        }
                        else -> {
                            // ===== 书写手势（优化：历史点 + FrontBuffer）=====
                            predictor.reset()
                            predictor.addPoint(nx, ny, down.pressure, down.uptimeMillis)
                            viewModel.onStrokeBegin(nx, ny, down.pressure)
                            // 通知 FrontBuffer 新笔画开始
                            onFrontBufferStrokeStart?.invoke(
                                tState.currentColor,
                                tState.strokeWidth.fraction * w
                            )
                            // 本次手势的全量点（供 FrontBuffer 做增量渲染）
                            val allPoints = mutableListOf(StrokePoint(nx, ny, down.pressure))
                            var lastX = nx; var lastY = ny; var lastPressure = down.pressure

                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    // ① 历史点 + 当前点：批量收集，一次性写入 ViewModel（减少 GC 压力）
                                    val batch = mutableListOf<StrokePoint>()
                                    change.historical.forEach { hist ->
                                        val hx = hist.position.x / w
                                        val hy = hist.position.y / h
                                        predictor.addPoint(hx, hy, change.pressure, hist.uptimeMillis)
                                        batch.add(StrokePoint(hx, hy, change.pressure))
                                    }
                                    val cx = change.position.x / w
                                    val cy = change.position.y / h
                                    predictor.addPoint(cx, cy, change.pressure, change.uptimeMillis)
                                    batch.add(StrokePoint(cx, cy, change.pressure))
                                    lastX = cx; lastY = cy; lastPressure = change.pressure

                                    allPoints.addAll(batch)
                                    viewModel.onStrokeMoveBatch(batch)

                                    // ② FrontBuffer 渲染（低延迟路径）
                                    onFrontBufferStrokePoints?.invoke(allPoints, w, h)

                                    change.consume()
                                } else {
                                    // 手势结束
                                    onFrontBufferStrokeDone?.invoke()
                                    viewModel.onStrokeEnd(lastX, lastY, lastPressure)
                                    predictor.reset()
                                    break
                                }
                            } while (true)
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas

            // ─── 0. 页面背景 ───
            when (page.background) {
                PageBackground.GRID -> {
                    val step = w / 25f
                    val gridPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(60, 66, 66, 66)
                        strokeWidth = 1f
                        style = android.graphics.Paint.Style.STROKE
                    }
                    var x = step
                    while (x < w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += step }
                    var y = step
                    while (y < h) { canvas.drawLine(0f, y, w, y, gridPaint); y += step }
                }
                PageBackground.LINES -> {
                    val step = h / 18f
                    val linePaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(60, 97, 97, 97)
                        strokeWidth = 1f
                        style = android.graphics.Paint.Style.STROKE
                    }
                    var y = step
                    while (y < h) { canvas.drawLine(0f, y, w, y, linePaint); y += step }
                }
                PageBackground.BLANK -> Unit
            }

            // ─── 1. 已提交笔画（路径缓存，避免每帧重建 Path）───
            page.strokes.forEach { stroke ->
                if (stroke.tool == StrokeTool.SHAPE
                    && stroke.shapeType != null
                    && stroke.points.size >= 2
                ) {
                    ShapeRenderer.drawShape(
                        canvas, stroke.shapeType,
                        stroke.points[0].x, stroke.points[0].y,
                        stroke.points[1].x, stroke.points[1].y,
                        w, h,
                        buildPaint(stroke.color, stroke.width * w)
                    )
                } else {
                    val path = pathCache.getOrPut(stroke.id) {
                        StrokeRenderer.buildPath(stroke.points, w, h)
                    }
                    canvas.drawPath(path, buildPaint(stroke.color, stroke.width * w))
                }
            }

            // ─── 2. 活跃笔画（API < 29 / 无 FrontBuffer 时在 Compose 层渲染）───
            //     FrontBuffer 激活时 activeStroke 始终为空，此块自动跳过
            if (activeStroke.isNotEmpty()) {
                val displayed = activeStroke + predictor.predictedPoints()
                canvas.drawPath(
                    StrokeRenderer.buildPath(displayed, w, h),
                    buildPaint(toolState.currentColor, toolState.strokeWidth.fraction * w)
                )
            }

            // ─── 3. 图形拖拽预览（虚线 + 50% 透明）───
            activeShapeDrag?.let { drag ->
                ShapeRenderer.drawShape(
                    canvas, toolState.selectedShape,
                    drag.startNx, drag.startNy,
                    drag.endNx, drag.endNy,
                    w, h,
                    buildPaint(toolState.currentColor, toolState.strokeWidth.fraction * w),
                    isDashed = true
                )
            }
        }

        // ─── 4. 橡皮擦光标 ───
        eraserCursor?.let { (ex, ey) ->
            val r = toolState.eraserSize.fraction * size.width
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = r,
                center = Offset(ex * size.width, ey * size.height),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

private fun buildPaint(color: Int, widthPx: Float): Paint = Paint().apply {
    this.color = color
    strokeWidth = widthPx.coerceAtLeast(3f)
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    isAntiAlias = true
}
