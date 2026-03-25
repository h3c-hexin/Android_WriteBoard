package com.paintboard.ui.toolbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 橡皮擦面板内容（不含弹出容器，由 EraserPopup 负责包裹）
 *
 * 内容：标题 + 关闭按钮 + 三档圆形大小 + 滑动解锁清除当前页
 */
@Composable
fun EraserPanelContent(
    onClearPage: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // 标题行 + 关闭按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "橡皮擦",
                color = Color.White,
                fontSize = 18.sp
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(20.dp)
                )
            }
        }


        Spacer(Modifier.height(12.dp))

        // 滑动解锁清除当前页
        SlideToUnlockClear(onUnlocked = onClearPage)

        // 提示文字
        Text(
            text = "清除后可通过撤销（↩）恢复",
            color = Color(0xFF888888),
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        )
    }
}

/**
 * 滑动解锁清除当前页
 *
 * 使用 BoxWithConstraints 获取真实轨道宽度，Y 偏移固定为 (52dp - 40dp) / 2 = 6dp
 */
@Composable
private fun SlideToUnlockClear(onUnlocked: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbProgress = remember { Animatable(0f) }
    val triggered = remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFFEF9A9A).copy(alpha = 0.10f))
            .border(1.dp, Color(0xFFEF9A9A).copy(alpha = 0.25f), RoundedCornerShape(100.dp))
    ) {
        val trackW = constraints.maxWidth.toFloat()
        val thumbSizePx = with(density) { 40.dp.toPx() }
        val leftPadPx = with(density) { 6.dp.toPx() }
        val maxOffset = (trackW - thumbSizePx - leftPadPx).coerceAtLeast(0f)
        val yOffsetPx = ((with(density) { 52.dp.toPx() } - thumbSizePx) / 2).toInt()

        // 进度背景
        val prog = thumbProgress.value
        if (prog > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(prog.coerceIn(0f, 1f))
                    .background(Color(0xFFEF9A9A).copy(alpha = (prog * 0.25f).coerceIn(0f, 0.3f)))
            )
        }

        // 提示文字（居中）
        Text(
            text = "→ 滑动清除当前页",
            color = Color(0xFFEF9A9A).copy(alpha = 0.7f),
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        // 滑块
        Box(
            modifier = Modifier
                .offset { IntOffset(
                    (thumbProgress.value * maxOffset + leftPadPx).toInt(),
                    yOffsetPx
                ) }
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF9A9A))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (!triggered.value && thumbProgress.value >= 0.9f) {
                                    triggered.value = true
                                    onUnlocked()
                                }
                                thumbProgress.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                                triggered.value = false
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { thumbProgress.animateTo(0f, spring()) }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        if (maxOffset > 0f) {
                            val newVal = (thumbProgress.value + dragAmount.x / maxOffset).coerceIn(0f, 1f)
                            coroutineScope.launch { thumbProgress.snapTo(newVal) }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (thumbProgress.value >= 0.9f) Icons.Default.Delete else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF7F0000),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
