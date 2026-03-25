package com.h3c.writeboard.ui.canvas

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.h3c.writeboard.ui.share.FullscreenQROverlay
import com.h3c.writeboard.ui.theme.CanvasBackground
import com.h3c.writeboard.ui.toolbar.BottomToolbar

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
    val showRestoreDialog by viewModel.showRestoreDialog.collectAsState()

    val hasFrontBuffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val frontBufferViewRef = remember { mutableStateOf<FrontBufferStrokeView?>(null) }
    // 待执行的 clearStroke Job，新笔画开始时取消，避免清空正在渲染的笔画
    val scope = rememberCoroutineScope()
    val pendingClearJob = remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // 全屏画布
        DrawingCanvas(
            modifier = Modifier.fillMaxSize(),
            viewModel = viewModel,
            onFrontBufferStrokeStart = if (hasFrontBuffer) { color, widthPx ->
                // 新笔画开始：取消上一笔待执行的 clear，避免中途清空新笔画
                pendingClearJob.value?.cancel()
                pendingClearJob.value = null
                frontBufferViewRef.value?.startStroke(color, widthPx)
            } else null,
            onFrontBufferStrokePoints = if (hasFrontBuffer) { points, w, h ->
                frontBufferViewRef.value?.renderPoints(points, w, h)
            } else null,
            onFrontBufferStrokeDone = if (hasFrontBuffer) { ->
                // commit：前端缓冲内容转入多缓冲层，SurfaceView 不出现空帧
                frontBufferViewRef.value?.commitStroke()
                // 50ms 后 Compose 帧已上屏，再清空多缓冲；scope 随 Composable 自动取消
                pendingClearJob.value = scope.launch {
                    delay(50L)
                    frontBufferViewRef.value?.clearStroke()
                    pendingClearJob.value = null
                }
            } else null
        )

        // FrontBuffer 活跃笔画层（API 29+），叠加于 DrawingCanvas 之上
        if (hasFrontBuffer) {
            AndroidView(
                factory = { ctx -> FrontBufferStrokeView(ctx) },
                modifier = Modifier.fillMaxSize(),
                update = { view -> frontBufferViewRef.value = view }
            )
        }

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

        // 启动恢复询问 Dialog
        if (showRestoreDialog) {
            AlertDialog(
                onDismissRequest = { /* 强制选择，不允许点外部关闭 */ },
                title = { Text("恢复白板") },
                text = { Text("检测到上次未保存的内容，是否恢复？") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmRestore() }) {
                        Text("恢复")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.discardRestore() }) {
                        Text("新建白板")
                    }
                }
            )
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
