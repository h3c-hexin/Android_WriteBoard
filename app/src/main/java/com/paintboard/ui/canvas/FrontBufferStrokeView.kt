package com.paintboard.ui.canvas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.view.SurfaceView
import androidx.annotation.RequiresApi
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer
import com.paintboard.domain.model.StrokePoint

/**
 * 前端缓冲活跃笔画层（API 29+）
 *
 * 线程模型：
 *   触摸线程（主线程）：调用 startStroke / renderPoints / commitStroke / clearStroke
 *   渲染线程（Renderer 内部线程）：调用 onDrawFrontBufferedLayer / onDrawMultiBufferedLayer
 *
 * 跨线程安全措施：
 *   - snapshotPoints / snapshotWPx / snapshotHPx 三变量合并为单个 @Volatile SnapshotState，
 *     单次引用赋值保证原子性，渲染线程读到的是一致的快照
 *   - renderPoints() 用 allPoints.toList() 做防御性拷贝，避免触摸线程继续追加点时
 *     渲染线程读到不完整数据（ConcurrentModificationException / 脏读）
 *
 * 性能优化：
 *   - reusablePath / frontPaint / multiPaint 在类初始化时分配，渲染回调内复用，
 *     避免 onDrawFrontBufferedLayer 每次调用都 new Path() / new Paint()
 *   - reusablePath 和两个 Paint 均在同一渲染线程使用，无需额外同步
 */
@RequiresApi(Build.VERSION_CODES.Q)
class FrontBufferStrokeView(context: Context) : SurfaceView(context) {

    data class CRSegment(
        val p0x: Float, val p0y: Float,
        val p1x: Float, val p1y: Float,
        val p2x: Float, val p2y: Float,
        val p3x: Float, val p3y: Float,
        val widthPx: Float,
        val color: Int
    )

    /** 渲染线程所需的不可变快照，单一引用保证读写原子性 */
    private data class SnapshotState(
        val points: List<StrokePoint>,
        val wPx: Float,
        val hPx: Float
    )

    private var renderer: CanvasFrontBufferedRenderer<CRSegment>? = null
    private var lastRenderedIndex = 0

    private var strokeColor: Int = android.graphics.Color.WHITE
    private var strokeWidthPx: Float = 8f

    @Volatile private var snapshot = SnapshotState(emptyList(), 1f, 1f)

    // 渲染线程复用对象（两个回调均在同一渲染线程，无需额外同步）
    private val reusablePath = android.graphics.Path()
    private val frontPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val multiPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    companion object {
        private const val SMOOTH_FACTOR = 0.35f
    }

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer = CanvasFrontBufferedRenderer(this,
            object : CanvasFrontBufferedRenderer.Callback<CRSegment> {

                /** 前端缓冲：增量绘制最新段，低延迟。复用 reusablePath / frontPaint。 */
                override fun onDrawFrontBufferedLayer(
                    canvas: Canvas, bufferWidth: Int, bufferHeight: Int, param: CRSegment
                ) {
                    val f = SMOOTH_FACTOR
                    val cp1x = param.p1x + (param.p2x - param.p0x) * f
                    val cp1y = param.p1y + (param.p2y - param.p0y) * f
                    val cp2x = param.p2x - (param.p3x - param.p1x) * f
                    val cp2y = param.p2y - (param.p3y - param.p1y) * f

                    reusablePath.reset()
                    reusablePath.moveTo(param.p1x, param.p1y)
                    reusablePath.cubicTo(cp1x, cp1y, cp2x, cp2y, param.p2x, param.p2y)

                    frontPaint.color = param.color
                    frontPaint.strokeWidth = param.widthPx.coerceAtLeast(3f)
                    canvas.drawPath(reusablePath, frontPaint)
                }

                /**
                 * 多缓冲层：commitStroke() 触发，用不可变点快照重建连续 Catmull-Rom 路径。
                 * 与 Compose StrokeRenderer.buildPath 完全一致 → 抬手字形零变化。
                 */
                override fun onDrawMultiBufferedLayer(
                    canvas: Canvas, bufferWidth: Int, bufferHeight: Int,
                    params: Collection<CRSegment>
                ) {
                    val s = snapshot
                    if (s.points.size < 2) return
                    val path = StrokeRenderer.buildPath(s.points, s.wPx, s.hPx)
                    multiPaint.color = strokeColor
                    multiPaint.strokeWidth = strokeWidthPx.coerceAtLeast(3f)
                    canvas.drawPath(path, multiPaint)
                }
            })
    }

    override fun onDetachedFromWindow() {
        renderer?.release(true)
        renderer = null
        super.onDetachedFromWindow()
    }

    /** 手势开始：重置状态。不调用 clear()，前一笔多缓冲内容保留以防交接闪烁。 */
    fun startStroke(color: Int, widthPx: Float) {
        strokeColor = color
        strokeWidthPx = widthPx
        lastRenderedIndex = 0
        snapshot = SnapshotState(emptyList(), 1f, 1f)
    }

    /**
     * 增量提交新触摸点：将所有新增的 Catmull-Rom 段写入前端缓冲。
     *
     * 线程安全：allPoints.toList() 生成不可变拷贝再赋给 snapshot，
     * 触摸线程后续对 allPoints 的 addAll 不会影响渲染线程正在读取的快照。
     */
    fun renderPoints(allPoints: List<StrokePoint>, wPx: Float, hPx: Float) {
        val r = renderer ?: return
        val n = allPoints.size
        if (n < 2) return

        // 防御性拷贝：确保渲染线程读到的是当前时刻的完整不可变快照
        snapshot = SnapshotState(allPoints.toList(), wPx, hPx)

        for (i in lastRenderedIndex until n - 1) {
            r.renderFrontBufferedLayer(buildSegment(allPoints, i, n, wPx, hPx))
        }
        lastRenderedIndex = n - 1
    }

    /** 手势结束第一步：commit 到多缓冲层，前端缓冲清空，SurfaceView 不出现空帧 */
    fun commitStroke() {
        renderer?.commit()
        lastRenderedIndex = 0
    }

    /** 手势结束第二步：清空多缓冲层（CanvasScreen 在 50ms 后调用） */
    fun clearStroke() {
        renderer?.clear()
    }

    // ──────────────────────────────────────────────────────────────

    private fun buildSegment(
        pts: List<StrokePoint>, i: Int, n: Int, wPx: Float, hPx: Float
    ): CRSegment {
        val p0 = pts[if (i > 0) i - 1 else 0]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[if (i + 2 < n) i + 2 else i + 1]
        return CRSegment(
            p0x = p0.x * wPx, p0y = p0.y * hPx,
            p1x = p1.x * wPx, p1y = p1.y * hPx,
            p2x = p2.x * wPx, p2y = p2.y * hPx,
            p3x = p3.x * wPx, p3y = p3.y * hPx,
            widthPx = strokeWidthPx, color = strokeColor
        )
    }
}
