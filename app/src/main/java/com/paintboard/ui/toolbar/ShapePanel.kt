package com.paintboard.ui.toolbar

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.paintboard.domain.model.ShapeType
import com.paintboard.ui.canvas.ShapeRenderer
import com.paintboard.ui.theme.ActiveIconBackground
import com.paintboard.ui.theme.ActiveIconDark
import com.paintboard.ui.theme.BottomSheetBackground
import com.paintboard.ui.theme.IconDefault

/** 图形类型列表（顺序对应 2×3 宫格）*/
private val SHAPE_LIST = listOf(
    ShapeType.LINE      to "直线",
    ShapeType.ARROW     to "箭头",
    ShapeType.RECTANGLE to "矩形",
    ShapeType.CIRCLE    to "圆形",
    ShapeType.TRIANGLE  to "三角",
    ShapeType.DIAMOND   to "菱形"
)

/**
 * 图形选择子面板 Popup
 * 锚定在图形按钮正上方，宽度固定 280dp，2×3 宫格布局
 */
@Composable
fun ShapePanel(
    selectedShape: ShapeType,
    onShapeSelected: (ShapeType) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val toolbarHeightPx = with(density) { 72.dp.roundToPx() }

    Popup(
        popupPositionProvider = object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2)
                    .coerceIn(8, (windowSize.width - popupContentSize.width - 8).coerceAtLeast(8))
                val y = windowSize.height - toolbarHeightPx - popupContentSize.height
                return IntOffset(x, y)
            }
        },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = BottomSheetBackground,
            shadowElevation = 12.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("图形", color = Color.White, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                // 第 1 行：直线 / 箭头 / 矩形
                Row(
                    modifier = Modifier.width(240.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SHAPE_LIST.take(3).forEach { (type, label) ->
                        ShapeButton(type, label, type == selectedShape) {
                            onShapeSelected(type)
                            onDismiss()
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                // 第 2 行：圆形 / 三角 / 菱形
                Row(
                    modifier = Modifier.width(240.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SHAPE_LIST.drop(3).forEach { (type, label) ->
                        ShapeButton(type, label, type == selectedShape) {
                            onShapeSelected(type)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

/** 单个图形按钮：图标（自定义 Canvas）+ 文字标签 */
@Composable
private fun ShapeButton(
    type: ShapeType,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val iconColor  = if (isSelected) ActiveIconDark  else IconDefault
    val labelColor = if (isSelected) ActiveIconDark  else IconDefault

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (isSelected) Modifier.background(ActiveIconBackground) else Modifier)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        // 用 ShapeRenderer 直接画图形轮廓作为图标
        Canvas(modifier = Modifier.size(32.dp)) {
            val w = size.width;  val h = size.height
            val paint = Paint().apply {
                color = iconColor.toArgb()
                strokeWidth = (2.5f * density).coerceAtLeast(3f)
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = true
            }
            drawIntoCanvas { c ->
                // 留 15% 边距，让图形不贴边
                val pad = 0.15f
                ShapeRenderer.drawShape(c.nativeCanvas, type, pad, pad, 1f - pad, 1f - pad, w, h, paint)
            }
        }
        Text(text = label, color = labelColor, fontSize = 12.sp)
    }
}

