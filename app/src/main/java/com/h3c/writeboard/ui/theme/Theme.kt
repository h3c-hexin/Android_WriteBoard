package com.h3c.writeboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 深色主题配色方案（仅支持深色主题）
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueDark,
    onPrimary = Color(0xFF003C8F),
    primaryContainer = ActiveBackground,
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF003C8F),
    background = CanvasBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = ToolbarBackground,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = BottomSheetBackground,
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = ErrorRed,
    onError = Color(0xFF000000),
    outline = ToolbarDivider
)

@Composable
fun PaintBoardTheme(
    content: @Composable () -> Unit
) {
    // PaintBoard 仅使用深色主题，不跟随系统
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = PaintBoardTypography,
        content = content
    )
}
