package com.paintboard.domain.model

import kotlinx.serialization.Serializable

/**
 * 笔画数据模型
 * 坐标使用归一化值 [0.0, 1.0]，便于跨设备协同和文件存储
 * 渲染时乘以画布实际尺寸转换为 px
 */
@Serializable
data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val color: Int,              // ARGB 整型
    val width: Float,            // 归一化宽度（相对画布宽度），渲染时转为 px
    val tool: StrokeTool = StrokeTool.PEN,
    val shapeType: ShapeType? = null  // 仅当 tool == SHAPE 时非 null；points[0]=起点, points[1]=终点
)

@Serializable
data class StrokePoint(
    val x: Float,            // 归一化 x，范围 [0.0, 1.0]
    val y: Float,            // 归一化 y，范围 [0.0, 1.0]
    val pressure: Float = 1.0f
)

@Serializable
enum class StrokeTool { PEN, ERASER, SHAPE }

/** 图形类型 */
@Serializable
enum class ShapeType { LINE, ARROW, RECTANGLE, CIRCLE, TRIANGLE, DIAMOND }

/** 笔刷粗细三档，对应画布宽度的比例 */
enum class StrokeWidth(val fraction: Float) {
    THIN(0.002f),    // 细：画布宽度的 0.2%
    MEDIUM(0.004f),  // 中：画布宽度的 0.4%
    THICK(0.008f)    // 粗：画布宽度的 0.8%
}
