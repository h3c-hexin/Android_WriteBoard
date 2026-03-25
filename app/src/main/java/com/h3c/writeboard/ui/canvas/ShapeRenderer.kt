package com.h3c.writeboard.ui.canvas

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.h3c.writeboard.domain.model.ShapeType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 图形渲染工具
 *
 * 所有图形坐标使用归一化值 [0.0, 1.0]，渲染前乘以画布宽高转为 px。
 * 支持实线（已提交图形）和虚线（拖拽预览）两种样式。
 */
object ShapeRenderer {

    /**
     * 在 Android Canvas 上绘制图形
     *
     * @param isDashed true = 虚线预览（50% alpha + DashPathEffect）
     */
    fun drawShape(
        canvas: android.graphics.Canvas,
        type: ShapeType,
        startNx: Float, startNy: Float,
        endNx: Float, endNy: Float,
        canvasW: Float, canvasH: Float,
        paint: Paint,
        isDashed: Boolean = false
    ) {
        val sx = startNx * canvasW
        val sy = startNy * canvasH
        val ex = endNx * canvasW
        val ey = endNy * canvasH

        val p = if (isDashed) {
            Paint(paint).apply {
                alpha = 128
                val dashLen = 20f
                pathEffect = DashPathEffect(floatArrayOf(dashLen, dashLen * 0.75f), 0f)
            }
        } else paint

        when (type) {
            ShapeType.LINE      -> canvas.drawLine(sx, sy, ex, ey, p)
            ShapeType.ARROW     -> drawArrow(canvas, sx, sy, ex, ey, p)
            ShapeType.RECTANGLE -> drawRect(canvas, sx, sy, ex, ey, p)
            ShapeType.CIRCLE    -> drawCircle(canvas, sx, sy, ex, ey, p)
            ShapeType.TRIANGLE  -> drawTriangle(canvas, sx, sy, ex, ey, p)
            ShapeType.DIAMOND   -> drawDiamond(canvas, sx, sy, ex, ey, p)
        }
    }

    // ───── 各图形绘制 ─────

    private fun drawRect(canvas: android.graphics.Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        canvas.drawRect(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey), p)
    }

    private fun drawCircle(canvas: android.graphics.Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        canvas.drawOval(RectF(min(sx, ex), min(sy, ey), max(sx, ex), max(sy, ey)), p)
    }

    private fun drawTriangle(canvas: android.graphics.Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        val left  = min(sx, ex);  val right = max(sx, ex)
        val top   = min(sy, ey);  val bottom = max(sy, ey)
        val path = Path().apply {
            moveTo((left + right) / 2f, top)  // 顶点（居中）
            lineTo(left, bottom)               // 左下角
            lineTo(right, bottom)              // 右下角
            close()
        }
        canvas.drawPath(path, p)
    }

    private fun drawDiamond(canvas: android.graphics.Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        val left  = min(sx, ex);  val right = max(sx, ex)
        val top   = min(sy, ey);  val bottom = max(sy, ey)
        val midX  = (left + right) / 2f
        val midY  = (top + bottom) / 2f
        val path = Path().apply {
            moveTo(midX, top);   lineTo(right, midY)
            lineTo(midX, bottom); lineTo(left, midY)
            close()
        }
        canvas.drawPath(path, p)
    }

    private fun drawArrow(canvas: android.graphics.Canvas, sx: Float, sy: Float, ex: Float, ey: Float, p: Paint) {
        canvas.drawLine(sx, sy, ex, ey, p)
        val dx = ex - sx;  val dy = ey - sy
        val length = sqrt(dx * dx + dy * dy)
        if (length < 1f) return
        val arrowLen = (length * 0.18f).coerceAtLeast(20f)
        val angle = atan2(dy.toDouble(), dx.toDouble())
        // 箭头两翼（与主线夹角约 150°）
        val a1 = angle + Math.PI * 5.0 / 6.0
        val a2 = angle - Math.PI * 5.0 / 6.0
        canvas.drawLine(ex, ey, (ex + arrowLen * cos(a1)).toFloat(), (ey + arrowLen * sin(a1)).toFloat(), p)
        canvas.drawLine(ex, ey, (ex + arrowLen * cos(a2)).toFloat(), (ey + arrowLen * sin(a2)).toFloat(), p)
    }
}
