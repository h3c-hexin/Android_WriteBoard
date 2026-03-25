package com.paintboard.ui.canvas

import android.graphics.Paint
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import com.paintboard.domain.model.StrokeTool
import kotlin.math.sqrt

/**
 * 画布 Composable
 *
 * 渲染策略：直接每帧绘制全部笔画，无 Bitmap 缓存，无闪烁。
 *
 * 触摸路由优先级：
 *   1. 显式橡皮模式（工具栏橡皮按钮激活）→ 全部触点擦除
 *   2. 触控笔橡皮端（PointerType.Eraser）→ 擦除
 *   3. PAD 模式下手指触摸（PointerType.Touch）→ 擦除
 *   4. 其余 → 书写
 */
@Composable
fun DrawingCanvas(
    modifier: Modifier = Modifier,
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val page            by viewModel.page.collectAsState()
    val toolState       by viewModel.toolState.collectAsState()
    val activeStroke    by viewModel.activeStroke.collectAsState()
    val eraserCursor    by viewModel.eraserCursor.collectAsState()
    val activeShapeDrag by viewModel.activeShapeDrag.collectAsState()

    // PAD 模式检测：屏幕对角线 < 20 英寸视为 PAD
    val context = LocalContext.current
    val isPadMode = remember {
        val dm = context.resources.displayMetrics
        val wIn = dm.widthPixels / dm.xdpi
        val hIn = dm.heightPixels / dm.ydpi
        sqrt((wIn * wIn + hIn * hIn).toDouble()) < 20.0
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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

                    // 读取手势开始时的工具状态（快照，避免手势中途切换工具导致不一致）
                    val tState = toolState
                    val nx = down.position.x / w
                    val ny = down.position.y / h

                    // 判断本次手势类型
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
                            // ===== 橡皮擦手势 =====
                            viewModel.onEraserBegin(nx, ny, radiusFraction, aspectRatio)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    viewModel.onEraserMove(change.position.x / w, change.position.y / h, radiusFraction, aspectRatio)
                                    change.consume()
                                } else { viewModel.onEraserEnd(); break }
                            } while (true)
                        }
                        isShape -> {
                            // ===== 图形拖拽手势 =====
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
                            // ===== 书写手势 =====
                            viewModel.onStrokeBegin(nx, ny, down.pressure)
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.pressed) {
                                    viewModel.onStrokeMove(change.position.x / w, change.position.y / h, change.pressure)
                                    change.consume()
                                } else { viewModel.onStrokeEnd(); break }
                            } while (true)
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // ─── 0. 页面背景（网格 / 横线）───
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            when (page.background) {
                PageBackground.GRID -> {
                    val step = w / 25f   // 约 25 列网格
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
                    val step = h / 18f   // 约 18 行横线
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
        }

        // ─── 1. 所有已完成笔画 + 图形 ───
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            page.strokes.forEach { stroke ->
                if (stroke.tool == StrokeTool.SHAPE && stroke.shapeType != null && stroke.points.size >= 2) {
                    // 图形笔画：用 ShapeRenderer 绘制
                    ShapeRenderer.drawShape(
                        canvas, stroke.shapeType,
                        stroke.points[0].x, stroke.points[0].y,
                        stroke.points[1].x, stroke.points[1].y,
                        w, h,
                        buildPaint(stroke.color, stroke.width * w)
                    )
                } else {
                    // 普通笔画：Catmull-Rom 贝塞尔
                    canvas.drawPath(
                        StrokeRenderer.buildPath(stroke.points, w, h),
                        buildPaint(stroke.color, stroke.width * w)
                    )
                }
            }
            // ─── 2. 当前正在书写的笔画（实时）───
            if (activeStroke.isNotEmpty()) {
                canvas.drawPath(
                    StrokeRenderer.buildPath(activeStroke, w, h),
                    buildPaint(toolState.currentColor, toolState.strokeWidth.fraction * w)
                )
            }
            // ─── 3. 正在拖拽的图形预览（虚线 + 50% 透明）───
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

        // ─── 3. 橡皮擦光标（白色圆圈，显示擦除范围）───
        eraserCursor?.let { (ex, ey) ->
            val r = toolState.eraserSize.fraction * w
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = r,
                center = Offset(ex * w, ey * h),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/** 构建画笔 Paint */
private fun buildPaint(color: Int, widthPx: Float): Paint {
    return Paint().apply {
        this.color = color
        this.strokeWidth = widthPx.coerceAtLeast(3f)
        this.style = Paint.Style.STROKE
        this.strokeCap = Paint.Cap.ROUND
        this.strokeJoin = Paint.Join.ROUND
        this.isAntiAlias = true
    }
}
