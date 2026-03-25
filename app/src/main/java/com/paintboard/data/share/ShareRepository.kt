package com.paintboard.data.share

import android.content.Context
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.paintboard.domain.model.DrawingPage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var server: EmbeddedServer<*, *>? = null
    var serverPort: Int = 8080
        private set

    private val pageBitmaps = mutableListOf<Bitmap>()
    private var currentToken: String = ""

    /** 获取设备局域网 IPv4 地址，未连接 WiFi 时返回 null */
    fun getLocalIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.flatMap { it.inetAddresses?.toList() ?: emptyList() }
            ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress
    } catch (_: Exception) { null }

    /**
     * 渲染所有页面为 PNG → 生成新 Token → 启动（或复用）HTTP 服务
     * 返回包含 Token 的分享 URL，未连接 WiFi 时返回 null
     */
    suspend fun prepareShare(pages: List<DrawingPage>): String? {
        val ip = getLocalIp() ?: return null
        withContext(Dispatchers.Default) {
            pageBitmaps.forEach { it.recycle() }
            pageBitmaps.clear()
            pages.forEach { pageBitmaps.add(PageBitmapRenderer.render(it, 1280, 720)) }
        }
        currentToken = newToken()
        ensureServerRunning()
        return "http://$ip:$serverPort/board?token=$currentToken"
    }

    /**
     * 用 ZXing 生成二维码 Bitmap（白底深码，Level Q 容错）
     * @param content 二维码内容（URL）
     * @param sizePx  输出 Bitmap 边长（px）
     */
    fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bmp
    }

    /** 停止 HTTP 服务并释放页面 Bitmap */
    fun stopServer() {
        server?.stop(1000L, 2000L)
        server = null
        pageBitmaps.forEach { it.recycle() }
        pageBitmaps.clear()
        currentToken = ""
    }

    // ===== 内部实现 =====

    private fun newToken(): String {
        val bytes = ByteArray(8).also { SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun ensureServerRunning() {
        if (server != null) return
        server = embeddedServer(CIO, port = serverPort) { configureShareRouting() }.start(wait = false)
    }

    private fun Application.configureShareRouting() {
        routing {
            // 浏览器访问入口：返回 HTML 页面
            get("/board") {
                val token = call.parameters["token"]
                if (token != currentToken) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                call.respondText(buildHtml(currentToken), ContentType.Text.Html)
            }
            // 各页面 PNG 图片
            get("/page/{index}.png") {
                val token = call.parameters["token"]
                if (token != currentToken) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@get
                }
                val i = call.parameters["index"]?.toIntOrNull()
                    ?: run { call.respond(HttpStatusCode.BadRequest); return@get }
                val bmp = pageBitmaps.getOrNull(i)
                    ?: run { call.respond(HttpStatusCode.NotFound); return@get }
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
                call.respondBytes(out.toByteArray(), ContentType.Image.PNG)
            }
        }
    }

    private fun buildHtml(token: String) = buildString {
        appendLine("<!DOCTYPE html><html lang='zh-CN'><head>")
        appendLine("<meta charset='UTF-8'>")
        appendLine("<meta name='viewport' content='width=device-width,initial-scale=1'>")
        appendLine("<title>PaintBoard 白板分享</title>")
        appendLine("<style>")
        appendLine("body{margin:0;background:#1a1a1a;color:#fff;font-family:sans-serif}")
        appendLine(".header{padding:16px;text-align:center;background:#2c2c2c}")
        appendLine(".header h1{margin:0;font-size:20px}")
        appendLine(".header p{margin:4px 0 0;color:#9e9e9e;font-size:14px}")
        appendLine(".tip{background:#1a3a2a;color:#a5d6a7;padding:12px 16px;font-size:14px;text-align:center}")
        appendLine(".pages{padding:16px;display:flex;flex-direction:column;gap:16px;align-items:center}")
        appendLine(".page{width:100%;max-width:800px}")
        appendLine(".page img{width:100%;border-radius:8px;display:block}")
        appendLine(".label{text-align:center;color:#9e9e9e;font-size:13px;margin:8px 0 0}")
        appendLine("</style></head><body>")
        appendLine("<div class='header'><h1>PaintBoard</h1>")
        appendLine("<p>共 ${pageBitmaps.size} 页白板内容</p></div>")
        appendLine("<div class='tip'>💡 长按图片可保存到相册</div>")
        appendLine("<div class='pages'>")
        pageBitmaps.indices.forEach { i ->
            appendLine("<div class='page'>")
            appendLine("<img src='/page/$i.png?token=$token' loading='lazy' alt='第 ${i + 1} 页'>")
            appendLine("<p class='label'>第 ${i + 1} 页 / 共 ${pageBitmaps.size} 页</p>")
            appendLine("</div>")
        }
        appendLine("</div></body></html>")
    }
}
