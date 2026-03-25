package com.paintboard.ui.canvas

import android.graphics.Path
import com.paintboard.domain.model.StrokePoint
import kotlin.math.sqrt

/**
 * 笔迹渲染工具：Catmull-Rom 样条插值
 *
 * 原理：给定四个控制点 P0~P3，计算 P1~P2 之间的平滑曲线。
 * 相比贝塞尔曲线，Catmull-Rom 保证曲线经过所有控制点，手感更自然。
 */
object StrokeRenderer {

    private const val SMOOTH_FACTOR = 0.35f  // 平滑强度，越大越平滑

    /**
     * 将笔画点列表转换为 Android Path（用于 Canvas 绘制）
     * @param points 归一化坐标点列表
     * @param canvasWidth 画布实际宽度（px）
     * @param canvasHeight 画布实际高度（px）
     */
    fun buildPath(
        points: List<StrokePoint>,
        canvasWidth: Float,
        canvasHeight: Float
    ): Path {
        val path = Path()
        if (points.isEmpty()) return path

        // 转换为画布像素坐标
        val px = points.map { it.x * canvasWidth }
        val py = points.map { it.y * canvasHeight }

        if (points.size == 1) {
            // 单点：画一个小圆点
            path.addCircle(px[0], py[0], 2f, Path.Direction.CW)
            return path
        }

        if (points.size == 2) {
            // 两点：直线
            path.moveTo(px[0], py[0])
            path.lineTo(px[1], py[1])
            return path
        }

        // 三点及以上：Catmull-Rom 插值
        path.moveTo(px[0], py[0])

        for (i in 0 until points.size - 1) {
            val p0 = if (i == 0) i else i - 1
            val p1 = i
            val p2 = i + 1
            val p3 = if (i + 2 < points.size) i + 2 else i + 1

            // 计算贝塞尔控制点（Catmull-Rom 转 Cubic Bezier）
            val cp1x = px[p1] + (px[p2] - px[p0]) * SMOOTH_FACTOR
            val cp1y = py[p1] + (py[p2] - py[p0]) * SMOOTH_FACTOR
            val cp2x = px[p2] - (px[p3] - px[p1]) * SMOOTH_FACTOR
            val cp2y = py[p2] - (py[p3] - py[p1]) * SMOOTH_FACTOR

            path.cubicTo(cp1x, cp1y, cp2x, cp2y, px[p2], py[p2])
        }

        return path
    }

    /**
     * 计算压感对应的实际线宽（px）
     * @param baseWidth 基础线宽（已转换为 px）
     * @param pressure 压感值 [0.0, 1.0]
     */
    fun pressureWidth(baseWidth: Float, pressure: Float): Float {
        // 非线性映射：0.7 次方让压感响应更自然
        val normalized = pressure.coerceIn(0.1f, 1.0f)
        return baseWidth * (0.6f + 0.7f * Math.pow(normalized.toDouble(), 0.7).toFloat())
    }
}
