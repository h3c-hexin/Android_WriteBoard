package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.h3c.writeboard.domain.model.EraserSize
import com.h3c.writeboard.ui.theme.ActiveIconBackground
import com.h3c.writeboard.ui.theme.ActiveIconDark
import com.h3c.writeboard.ui.theme.IconDefault

/**
 * 笔刷粗细按钮：显示对应粗细的横线
 */
@Composable
fun StrokeWidthButton(
    lineHeight: Dp,       // 横线高度，模拟笔画粗细
    isActive: Boolean,
    onClick: () -> Unit
) {
    val lineColor = if (isActive) ActiveIconDark else IconDefault

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)                // 触控热区
            .clickable(onClick = onClick)
    ) {
        // 激活背景（40dp 内层，对照设计稿 icon-inner）
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveIconBackground)
            )
        }
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = lineHeight)
                .background(lineColor, RoundedCornerShape(50))
        )
    }
}

/**
 * 橡皮擦大小按钮（工具栏内联版）：用圆形模拟橡皮擦头大小
 * 与 StrokeWidthButton 使用相同的双层结构，视觉保持一致
 */
@Composable
fun EraserSizeToolButton(
    size: EraserSize,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val circleDp: Dp = when (size) {
        EraserSize.SMALL  -> 8.dp
        EraserSize.MEDIUM -> 14.dp
        EraserSize.LARGE  -> 20.dp
    }
    val circleColor = if (isActive) ActiveIconDark else IconDefault

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clickable(onClick = onClick)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveIconBackground)
            )
        }
        // 中空圆圈：只有边框，无填充，更像橡皮擦头
        val strokeWidth: Dp = when (size) {
            EraserSize.SMALL  -> 1.5.dp
            EraserSize.MEDIUM -> 2.dp
            EraserSize.LARGE  -> 2.5.dp
        }
        Box(
            modifier = Modifier
                .size(circleDp)
                .border(strokeWidth, circleColor, CircleShape)
        )
    }
}
