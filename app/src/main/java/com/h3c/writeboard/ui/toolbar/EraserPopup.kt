package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.h3c.writeboard.ui.theme.BottomSheetBackground

/**
 * 橡皮擦二级菜单 Popup
 *
 * 以橡皮擦按钮为锚点，底边对齐工具栏顶边，水平居中于锚点。
 */
@Composable
fun EraserPopup(
    onClearPage: () -> Unit,
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
            EraserPanelContent(
                onClearPage = onClearPage,
                onDismiss = onDismiss
            )
        }
    }
}
