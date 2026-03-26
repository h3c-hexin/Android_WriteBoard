package com.h3c.writeboard.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.h3c.writeboard.ui.theme.BottomSheetBackground

private val TOOLBAR_HEIGHT = 72.dp

/**
 * 工具栏上方弹出面板的统一容器。
 * 底边对齐工具栏顶边，水平居中于锚点按钮。
 */
@Composable
fun ToolbarPopup(
    onDismiss: () -> Unit,
    shadowElevation: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    val toolbarHeightPx = with(LocalDensity.current) { TOOLBAR_HEIGHT.roundToPx() }

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
            shadowElevation = shadowElevation,
            tonalElevation = 4.dp
        ) {
            content()
        }
    }
}
