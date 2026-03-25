package com.paintboard.domain.model

import com.paintboard.ui.theme.QuickColorBlack
import com.paintboard.ui.theme.QuickColorBlue
import com.paintboard.ui.theme.QuickColorGreen
import com.paintboard.ui.theme.QuickColorRed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 当前工具状态，由 CanvasViewModel 持有
 */
data class DrawingToolState(
    val activeTool: ActiveTool = ActiveTool.PEN,
    val strokeWidth: StrokeWidth = StrokeWidth.MEDIUM,
    // 深色画布上默认用白色（黑板+粉笔效果，减少眩光的同时保持可读性）
    val currentColor: Int = android.graphics.Color.WHITE,
    val quickColors: List<Int> = listOf(
        android.graphics.Color.WHITE,
        QuickColorRed.toArgb(),
        QuickColorBlue.toArgb(),
        QuickColorGreen.toArgb()
    ),
    val recentColors: List<Int> = emptyList(),
    val eraserSize: EraserSize = EraserSize.MEDIUM,
    val selectedShape: ShapeType = ShapeType.RECTANGLE
)

enum class ActiveTool { PEN, ERASER, SHAPE }

enum class EraserSize(val fraction: Float) {
    SMALL(0.01f),   // 画布宽度的 1%
    MEDIUM(0.02f),  // 画布宽度的 2%
    LARGE(0.04f)    // 画布宽度的 4%
}
