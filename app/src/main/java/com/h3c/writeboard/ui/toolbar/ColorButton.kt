package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 颜色快捷按钮：圆形色块
 * 选中时显示白色外圈
 */
@Composable
fun ColorButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)                // 触控热区
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(if (isSelected) 32.dp else 28.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected)
                        Modifier.border(2.dp, Color.White, CircleShape)
                    else
                        Modifier
                )
        )
    }
}
