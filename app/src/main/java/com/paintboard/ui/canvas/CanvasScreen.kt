package com.paintboard.ui.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.paintboard.ui.share.FullscreenQROverlay
import com.paintboard.ui.theme.CanvasBackground
import com.paintboard.ui.toolbar.BottomToolbar

/**
 * 主画布页面
 * 布局：全屏画布 + 底部工具栏（72dp 高）
 * ViewModel 在此创建，向下传递给 DrawingCanvas 和 BottomToolbar
 */
@Composable
fun CanvasScreen(
    viewModel: CanvasViewModel = hiltViewModel()
) {
    val showFullscreenQR by viewModel.showFullscreenQR.collectAsState()
    val shareState by viewModel.shareState.collectAsState()
    val collabState by viewModel.collabState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // 全屏画布
        DrawingCanvas(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel
        )

        // 底部工具栏
        BottomToolbar(
            modifier = Modifier.align(Alignment.BottomCenter),
            viewModel = viewModel
        )

        // 协同状态顶部 Banner
        if (collabState.role != CanvasViewModel.CollabRole.NONE) {
            val deviceCount = collabState.devices.size
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(Color(0x99163a1e)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🟢  协同中 · $deviceCount 台设备在线",
                    color = Color(0xFFA5D6A7),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 全屏二维码展示层
        if (showFullscreenQR && shareState.shareUrl != null) {
            FullscreenQROverlay(
                shareUrl = shareState.shareUrl!!,
                shareRepository = viewModel.shareRepository,
                onDismiss = { viewModel.closeFullscreenQR() }
            )
        }
    }
}
