package com.h3c.writeboard.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.h3c.writeboard.ui.theme.ActiveIconBackground
import com.h3c.writeboard.ui.theme.ActiveIconDark
import com.h3c.writeboard.ui.theme.IconDefault
import com.h3c.writeboard.ui.theme.IconDisabled

/**
 * 工具栏通用按钮
 * 三态：激活（蓝色背景）/ 默认（灰色图标）/ 禁用（深灰图标）
 * 触控热区：64×64dp（大屏规范）
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val iconColor = when {
        !isEnabled -> IconDisabled
        isActive   -> ActiveIconDark
        else       -> IconDefault
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)                            // 触控热区（不变）
            .clickable(enabled = isEnabled, onClick = onClick)
    ) {
        // 激活背景（40dp，对照设计稿 icon-inner）
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ActiveIconBackground)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(24.dp)         // 图标视觉尺寸（对照设计稿 24dp）
        )
    }
}

/** 分组分隔线 */
@Composable
fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 32.dp)
            .background(com.h3c.writeboard.ui.theme.ToolbarDivider)
    )
}
