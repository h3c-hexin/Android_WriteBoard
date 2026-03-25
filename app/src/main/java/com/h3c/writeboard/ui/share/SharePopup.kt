package com.h3c.writeboard.ui.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.h3c.writeboard.data.share.ShareRepository
import com.h3c.writeboard.ui.canvas.CanvasViewModel
import com.h3c.writeboard.ui.theme.BottomSheetBackground
import com.h3c.writeboard.ui.theme.IconDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 扫码分享 Popup（遵循 §0 统一规范）
 *
 * 三种状态：
 *   加载中 → 显示进度圈 + "正在准备..."
 *   就绪   → 显示二维码 + URL + 放大按钮
 *   错误   → 显示错误文字
 */
@Composable
fun SharePopup(
    shareState: CanvasViewModel.ShareUiState,
    shareRepository: ShareRepository,
    onEnlargeQR: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val toolbarHeightPx = with(density) { 72.dp.roundToPx() }
    val context = LocalContext.current

    // 生成二维码 Bitmap（在 shareUrl 变化时重新计算）
    var qrBitmap by remember(shareState.shareUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shareState.shareUrl) {
        val url = shareState.shareUrl ?: return@LaunchedEffect
        qrBitmap = withContext(Dispatchers.Default) {
            shareRepository.generateQrBitmap(url, 400).asImageBitmap()
        }
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
            shadowElevation = 12.dp,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("扫码分享", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))

                when {
                    shareState.isLoading -> {
                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color(0xFF90CAF9),
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text("正在准备...", color = IconDefault, fontSize = 14.sp)
                            }
                        }
                    }

                    shareState.error != null -> {
                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                shareState.error,
                                color = Color(0xFFEF9A9A),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    qrBitmap != null -> {
                        // 二维码（白色背景保证识别率）
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap!!,
                                contentDescription = "扫码二维码",
                                modifier = Modifier.size(184.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // URL（点击复制）
                        Text(
                            text = shareState.shareUrl!!,
                            color = IconDefault,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable {
                                val clip = ClipData.newPlainText("分享链接", shareState.shareUrl)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("⚠️ 手机须连接同一 WiFi", color = Color(0xFFFFCC80), fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onEnlargeQR) {
                            Text("放大二维码（全屏展示）", color = Color(0xFF90CAF9), fontSize = 14.sp)
                        }
                    }

                    else -> {
                        // qrBitmap 还在生成中，显示占位
                        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF90CAF9), modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}
