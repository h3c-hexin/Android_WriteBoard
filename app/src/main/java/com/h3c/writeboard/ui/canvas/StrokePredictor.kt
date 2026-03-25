package com.h3c.writeboard.ui.canvas

import com.h3c.writeboard.domain.model.StrokePoint

/**
 * 基于速度的笔画预测器
 *
 * 原理：取最近几个触摸点估算速度向量，在手指实际位置前方外推 1 帧（≈16ms），
 * 让渲染超前于手指，消除体感延迟。
 *
 * 预测点仅用于当帧视觉渲染，不写入存盘、不参与协同广播、不压 Undo 栈。
 */
class StrokePredictor {

    private data class TimedPoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val timeMs: Long
    )

    private val history = ArrayDeque<TimedPoint>(HISTORY_SIZE)

    /** 喂入一个触摸点（含历史点循环调用） */
    fun addPoint(x: Float, y: Float, pressure: Float, timeMs: Long) {
        history.addLast(TimedPoint(x, y, pressure, timeMs))
        if (history.size > HISTORY_SIZE) history.removeFirst()
    }

    /**
     * 返回在实际触摸点之后外推的预测点列表（通常 1-2 个）。
     * 若历史点不足，返回空列表。
     */
    fun predictedPoints(): List<StrokePoint> {
        if (history.size < 2) return emptyList()

        // 用最近两点估算瞬时速度（归一化坐标/ms）
        val p1 = history[history.size - 2]
        val p2 = history[history.size - 1]
        val dt = (p2.timeMs - p1.timeMs).toFloat().coerceAtLeast(1f)
        val vx = (p2.x - p1.x) / dt
        val vy = (p2.y - p1.y) / dt

        // 速度过小时不预测（静止或极慢书写，预测无意义）
        if (vx * vx + vy * vy < MIN_VELOCITY_SQ) return emptyList()

        // 外推两个点：半帧 + 一帧，避免预测过于激进
        val halfFrame = PREDICT_FRAME_MS * 0.5f
        val fullFrame = PREDICT_FRAME_MS
        return listOf(
            StrokePoint(p2.x + vx * halfFrame, p2.y + vy * halfFrame, p2.pressure),
            StrokePoint(p2.x + vx * fullFrame, p2.y + vy * fullFrame, p2.pressure)
        )
    }

    /** 手势开始时重置 */
    fun reset() {
        history.clear()
    }

    companion object {
        private const val HISTORY_SIZE = 5
        private const val PREDICT_FRAME_MS = 16f       // 60Hz 设备一帧时间
        private const val MIN_VELOCITY_SQ = 1e-8f      // 最小有效速度平方（归一化单位/ms）²
    }
}
