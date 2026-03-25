package com.paintboard.ui.share

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paintboard.data.share.ShareRepository
import com.paintboard.ui.theme.IconDefault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 全屏二维码展示层（点击背景关闭）
 */
@Composable
fun FullscreenQROverlay(
    shareUrl: String,
    shareRepository: ShareRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember(shareUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(shareUrl) {
        qrBitmap = withContext(Dispatchers.Default) {
            shareRepository.generateQrBitmap(shareUrl, 800).asImageBitmap()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = false, onClick = {})  // 阻止点击事件传到背景
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("扫码分享", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(24.dp))

            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Image(
                        bitmap = qrBitmap!!,
                        contentDescription = "二维码",
                        modifier = Modifier.size(296.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = shareUrl,
                    color = IconDefault,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        val clip = ClipData.newPlainText("分享链接", shareUrl)
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text("⚠️ 手机须连接同一 WiFi", color = Color(0xFFFFCC80), fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Color(0xFF90CAF9), fontSize = 16.sp)
            }
        }
    }
}
