package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.h3c.writeboard.domain.model.DrawingToolState
import com.h3c.writeboard.ui.theme.BottomSheetBackground
import com.h3c.writeboard.ui.theme.IconDefault
import com.h3c.writeboard.ui.theme.PresetColors
import com.h3c.writeboard.ui.theme.QuickColorBlue
import com.h3c.writeboard.ui.theme.QuickColorGreen
import com.h3c.writeboard.ui.theme.QuickColorRed
import kotlin.math.roundToInt

// ===== HSV 工具函数 =====

/** 将 Android Color Int 转换为 HSV 三元组 [hue 0-360, sat 0-1, val 0-1] */
private fun colorToHsv(colorInt: Int): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(colorInt, hsv)
    return hsv
}

/** 将 HSV 转换为 Compose Color */
private fun hsvToComposeColor(hue: Float, sat: Float, value: Float): Color {
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
}

/** Int 颜色转十六进制字符串（不含 alpha），如 "FFFFFF" */
private fun colorToHex(colorInt: Int): String {
    return String.format("%06X", colorInt and 0xFFFFFF)
}

/** 十六进制字符串解析为 Color Int，解析失败返回 null */
private fun hexToColorInt(hex: String): Int? {
    val clean = hex.trimStart('#').take(6)
    if (clean.length != 6) return null
    return try {
        0xFF000000.toInt() or clean.toInt(16)
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * 颜色选择器 Popup
 *
 * 以调色盘按钮为锚点，底边对齐工具栏顶边，水平居中于锚点。
 * 布局：左列（快捷色 + 20色网格 + 最近色）| 右列（SV矩形 + 色相滑条 + Hex输入）
 */
@Composable
fun ColorPickerPanel(
    toolState: DrawingToolState,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val toolbarHeightPx = with(density) { 72.dp.roundToPx() }

    // ===== 本地 HSV 状态（初始化自当前颜色）=====
    val initHsv = remember(toolState.currentColor) { colorToHsv(toolState.currentColor) }
    var hue by remember { mutableStateOf(initHsv[0]) }
    var sat by remember { mutableStateOf(initHsv[1]) }
    var value by remember { mutableStateOf(initHsv[2]) }
    var hexText by remember { mutableStateOf(colorToHex(toolState.currentColor)) }

    fun selectColor(colorInt: Int) {
        val hsv = colorToHsv(colorInt)
        hue = hsv[0]; sat = hsv[1]; value = hsv[2]
        hexText = colorToHex(colorInt)
        onColorSelected(colorInt)
    }

    fun onHsvChanged() {
        val colorInt = hsvToComposeColor(hue, sat, value).toArgb()
        hexText = colorToHex(colorInt)
        onColorSelected(colorInt)
    }

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
            shadowElevation = 16.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // 标题行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择颜色", color = Color.White, fontSize = 18.sp)
                }

        // ===== 主体：两列布局 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ────── 左列：快捷色 + 色板 + 最近色 ──────
            Column(modifier = Modifier.weight(1f)) {

                // 快捷色
                ColorSection(title = "快捷色") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(
                            Color.White,
                            QuickColorRed,
                            QuickColorBlue,
                            QuickColorGreen
                        ).forEach { c ->
                            val argb = c.toArgb()
                            QuickColorChip(
                                color = c,
                                isSelected = toolState.currentColor == argb,
                                onClick = { selectColor(argb) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 20色预设网格
                ColorSection(title = "色板") {
                    // 5列×4行
                    val cols = 5
                    PresetColors.chunked(cols).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            row.forEach { c ->
                                val argb = c.toArgb()
                                SwatchCell(
                                    color = c,
                                    isSelected = toolState.currentColor == argb,
                                    onClick = { selectColor(argb) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 最近使用色
                if (toolState.recentColors.isNotEmpty()) {
                    ColorSection(title = "最近使用") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            toolState.recentColors.take(5).forEach { argb ->
                                RecentColorChip(
                                    color = Color(argb),
                                    isSelected = toolState.currentColor == argb,
                                    onClick = { selectColor(argb) }
                                )
                            }
                        }
                    }
                }
            }

            // ────── 右列：HSV 自定义调色盘 ──────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "自定义",
                    color = Color(0xFF777777),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                // SV 矩形（饱和度 × 明度）
                SaturationValueRect(
                    hue = hue,
                    saturation = sat,
                    value = value,
                    onChanged = { s, v ->
                        sat = s; value = v
                        onHsvChanged()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 色相滑条
                HueSlider(
                    hue = hue,
                    onHueChanged = { h ->
                        hue = h
                        onHsvChanged()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Hex 输入框
                HexInput(
                    hexText = hexText,
                    onCommit = { newHex ->
                        val parsed = hexToColorInt(newHex)
                        if (parsed != null) {
                            selectColor(parsed)
                        }
                    }
                )
            }
        }
            }
        }
    }
}

// ===== 子组件 =====

/** 带标题的颜色分区 */
@Composable
private fun ColorSection(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
        color = Color(0xFF777777),
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    content()
}

/** 快捷色大圆 (44dp) */
@Composable
private fun QuickColorChip(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                else if (color == Color.White) Modifier.border(1.dp, Color(0xFF555555), CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick)
    )
}

/** 色板格子（固定尺寸）*/
@Composable
private fun SwatchCell(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 32.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(color)
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(5.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    )
}

/** 最近色小圆 (32dp) */
@Composable
private fun RecentColorChip(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) Color.White else Color(0xFF4A4A4A),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

/**
 * SV 矩形：水平方向 = 饱和度（左白右纯色），垂直方向 = 明度（上亮下黑）
 * 用两层渐变叠加实现：白→纯色 + 透明→黑
 */
@Composable
private fun SaturationValueRect(
    hue: Float,
    saturation: Float,
    value: Float,
    onChanged: (sat: Float, value: Float) -> Unit
) {
    val hueColor = hsvToComposeColor(hue, 1f, 1f)

    var rectSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { coords ->
                rectSize = Size(
                    coords.size.width.toFloat(),
                    coords.size.height.toFloat()
                )
            }
            .drawBehind {
                // 第一层：水平渐变（白 → 纯色）
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.White, hueColor))
                )
                // 第二层：垂直渐变（透明 → 黑），叠加暗化
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black)
                    )
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (rectSize != Size.Zero) {
                            val s = (offset.x / rectSize.width).coerceIn(0f, 1f)
                            val v = 1f - (offset.y / rectSize.height).coerceIn(0f, 1f)
                            onChanged(s, v)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (rectSize != Size.Zero) {
                        val s = (change.position.x / rectSize.width).coerceIn(0f, 1f)
                        val v = 1f - (change.position.y / rectSize.height).coerceIn(0f, 1f)
                        onChanged(s, v)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (rectSize != Size.Zero) {
                        val s = (offset.x / rectSize.width).coerceIn(0f, 1f)
                        val v = 1f - (offset.y / rectSize.height).coerceIn(0f, 1f)
                        onChanged(s, v)
                    }
                }
            }
    ) {
        // 光标圆点
        if (rectSize != Size.Zero) {
            val cursorX = (saturation * rectSize.width).roundToInt()
            val cursorY = ((1f - value) * rectSize.height).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(cursorX - 8, cursorY - 8) }
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(hsvToComposeColor(hue, saturation, value))
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/**
 * 色相滑条：彩虹渐变横条，带可拖拽圆形游标
 */
@Composable
private fun HueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    val rainbowBrush = remember {
        Brush.horizontalGradient(
            listOf(
                Color(0xFFFF0000), Color(0xFFFF7F00), Color(0xFFFFFF00),
                Color(0xFF00FF00), Color(0xFF00FFFF), Color(0xFF0000FF),
                Color(0xFF7F00FF), Color(0xFFFF00FF), Color(0xFFFF0000)
            )
        )
    }

    var sliderWidth by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .onGloballyPositioned { sliderWidth = it.size.width.toFloat() }
    ) {
        // 彩虹滑条轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(100))
                .drawBehind { drawRect(rainbowBrush) }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (sliderWidth > 0) {
                                onHueChanged((offset.x / sliderWidth * 360f).coerceIn(0f, 360f))
                            }
                        }
                    ) { change, _ ->
                        change.consume()
                        if (sliderWidth > 0) {
                            onHueChanged((change.position.x / sliderWidth * 360f).coerceIn(0f, 360f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (sliderWidth > 0) {
                            onHueChanged((offset.x / sliderWidth * 360f).coerceIn(0f, 360f))
                        }
                    }
                }
        )

        // 游标圆点
        if (sliderWidth > 0) {
            val thumbX = (hue / 360f * sliderWidth).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbX - 10, 0) }
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(hsvToComposeColor(hue, 1f, 1f))
                    .border(2.dp, Color.White, CircleShape)
            )
        }
    }
}

/** Hex 颜色输入框 */
@Composable
private fun HexInput(
    hexText: String,
    onCommit: (String) -> Unit
) {
    var text by remember(hexText) { mutableStateOf(hexText) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF383838))
            .border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#", color = Color(0xFF888888), fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = text,
            onValueChange = { input ->
                // 只允许 0-9、A-F（不区分大小写），最多 6 位
                val filtered = input.filter { it.isLetterOrDigit() }.uppercase().take(6)
                text = filtered
                if (filtered.length == 6) {
                    onCommit(filtered)
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
