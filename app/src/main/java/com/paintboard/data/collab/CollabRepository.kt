package com.paintboard.data.collab

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.paintboard.domain.model.CollabDevice
import com.paintboard.domain.model.CollabMessage
import com.paintboard.domain.model.DrawingPage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val NSD_SERVICE_TYPE = "_paintboard._tcp"
private const val NSD_SERVICE_PREFIX = "paintboard-"

@Singleton
class CollabRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val collabPort = 8765

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val nsdManager = context.getSystemService(NsdManager::class.java)!!

    // ===== Host 端状态 =====
    private var server: EmbeddedServer<*, *>? = null
    private var currentRoomCode: String = ""
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val sessions = ConcurrentHashMap<String, DefaultWebSocketServerSession>()
    private val connectedDevices = ConcurrentHashMap<String, CollabDevice>()
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ===== PAD 客户端状态 =====
    private var clientJob: Job? = null
    private var activeOutgoingChannel: Channel<String>? = null
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ===== 事件回调（由 ViewModel 注册）=====
    var onMessageReceived: ((CollabMessage) -> Unit)? = null
    var onDeviceListChanged: ((List<CollabDevice>) -> Unit)? = null
    var onSessionEnded: (() -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null
    var onJoinFailed: ((String) -> Unit)? = null
    var onNetworkLost: (() -> Unit)? = null

    companion object {
        const val ERR_NSD_TIMEOUT = "NSD_TIMEOUT"
    }

    // ===== Host：开启协同 =====

    /**
     * 生成 6 位房间码，启动 WebSocket 服务，注册 NSD。
     * @return 房间码（6 位数字字符串）
     */
    fun startHosting(
        hostDeviceName: String,
        getPages: () -> List<DrawingPage>
    ): String {
        if (server != null) return currentRoomCode
        currentRoomCode = (100000..999999).random().toString()

        connectedDevices["host"] = CollabDevice(
            deviceId = "host",
            deviceName = hostDeviceName,
            deviceColor = "#64B5F6",
            isHost = true
        )
        notifyDeviceListChanged()

        server = embeddedServer(ServerCIO, port = collabPort) {
            configureWebSocket(getPages)
        }.start(wait = false)

        registerNsd(currentRoomCode)
        registerNetworkCallback()
        return currentRoomCode
    }

    fun stopHosting() {
        broadcastToAll(CollabMessage(type = CollabMessage.SESSION_END))
        unregisterNsd()
        unregisterNetworkCallback()
        server?.stop(500L, 1000L)
        server = null
        sessions.clear()
        connectedDevices.clear()
        currentRoomCode = ""
        notifyDeviceListChanged()
    }

    fun broadcastToAll(message: CollabMessage) {
        val text = json.encodeToString(message)
        hostScope.launch {
            sessions.values.forEach { s -> runCatching { s.send(Frame.Text(text)) } }
        }
    }

    // ===== PAD：加入协同 =====

    /**
     * 通过 NSD 发现局域网内匹配 roomCode 的主机，然后建立 WebSocket 连接。
     * 结果通过 onMessageReceived / onJoinFailed 回调返回。
     */
    fun joinSession(roomCode: String, deviceName: String) {
        val channel = setupClientChannel()
        clientJob = clientScope.launch {
            val hostInfo = withTimeoutOrNull(5000L) { discoverHost(roomCode) }
            if (hostInfo == null) {
                onJoinFailed?.invoke(ERR_NSD_TIMEOUT)
                return@launch
            }
            doConnect(hostInfo.first, hostInfo.second, roomCode, deviceName, channel)
        }
    }

    /**
     * 跳过 NSD，直接用 IP 连接主机（AP 隔离兜底方案）。
     */
    fun joinSessionDirect(host: String, roomCode: String, deviceName: String) {
        val channel = setupClientChannel()
        clientJob = clientScope.launch {
            doConnect(host, collabPort, roomCode, deviceName, channel)
        }
    }

    fun leaveSession() {
        activeOutgoingChannel?.close()
        activeOutgoingChannel = null
        clientJob?.cancel()
        clientJob = null
        connectedDevices.clear()
        notifyDeviceListChanged()
    }

    fun sendToHost(message: CollabMessage) {
        clientScope.launch {
            runCatching { activeOutgoingChannel?.send(json.encodeToString(message)) }
        }
    }

    fun getConnectedDevices(): List<CollabDevice> = connectedDevices.values.toList()

    /** 获取本机局域网 IPv4 地址（用于 Host 展示给子设备手动连接） */
    fun getLocalIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { !it.isLoopback && it.isUp }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    // ===== 内部：WebSocket 客户端连接 =====

    private fun setupClientChannel(): Channel<String> {
        activeOutgoingChannel?.close()
        clientJob?.cancel()
        val channel = Channel<String>(Channel.BUFFERED)
        activeOutgoingChannel = channel
        return channel
    }

    private suspend fun doConnect(
        hostIp: String,
        hostPort: Int,
        roomCode: String,
        deviceName: String,
        channel: Channel<String>
    ) {
        var sessionEndedNormally = false
        var joinSucceeded = false

        try {
            HttpClient(CIO) { install(WebSockets) }
                .webSocket("ws://$hostIp:$hostPort/collab") {
                    // 发送协程：消费 channel
                    val sendJob = launch {
                        for (text in channel) {
                            runCatching { send(Frame.Text(text)) }
                        }
                    }

                    // 发送 JOIN
                    channel.send(json.encodeToString(
                        CollabMessage(
                            type = CollabMessage.JOIN,
                            token = roomCode,
                            deviceName = deviceName
                        )
                    ))

                    // 接收循环
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val msg = runCatching {
                                json.decodeFromString<CollabMessage>(frame.readText())
                            }.getOrNull() ?: continue

                            when (msg.type) {
                                CollabMessage.JOIN_DENIED -> {
                                    onJoinFailed?.invoke("房间码无效，请重新确认")
                                    break
                                }
                                CollabMessage.JOIN_ACK -> {
                                    joinSucceeded = true
                                    msg.deviceList?.forEach { connectedDevices[it.deviceId] = it }
                                    notifyDeviceListChanged()
                                    onMessageReceived?.invoke(msg)
                                }
                                CollabMessage.DEVICE_JOIN -> {
                                    msg.deviceList?.firstOrNull()?.let {
                                        connectedDevices[it.deviceId] = it
                                        notifyDeviceListChanged()
                                    }
                                }
                                CollabMessage.DEVICE_LEAVE -> {
                                    msg.deviceId?.let {
                                        connectedDevices.remove(it)
                                        notifyDeviceListChanged()
                                    }
                                }
                                CollabMessage.SESSION_END -> {
                                    sessionEndedNormally = true
                                    connectedDevices.clear()
                                    notifyDeviceListChanged()
                                    onSessionEnded?.invoke()
                                }
                                CollabMessage.PING -> {
                                    channel.send(json.encodeToString(
                                        CollabMessage(type = CollabMessage.PONG)
                                    ))
                                }
                                else -> onMessageReceived?.invoke(msg)
                            }
                        }
                    } catch (_: ClosedReceiveChannelException) { }

                    sendJob.cancel()
                }
        } catch (e: CancellationException) {
            throw e   // 协程被主动取消（leaveSession），不触发断线回调
        } catch (e: Exception) {
            onJoinFailed?.invoke("连接失败：${e.message ?: "未知错误"}")
            return
        }

        // 连接已正常建立，但非正常结束（非 SESSION_END）→ 断线
        if (joinSucceeded && !sessionEndedNormally) {
            onConnectionLost?.invoke()
        }
    }

    // ===== NSD =====

    private fun registerNsd(roomCode: String) {
        val info = NsdServiceInfo().apply {
            serviceName = "$NSD_SERVICE_PREFIX$roomCode"
            serviceType = NSD_SERVICE_TYPE
            port = collabPort
        }
        nsdRegistrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, err: Int) {}
            override fun onUnregistrationFailed(si: NsdServiceInfo, err: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo) {}
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
        }
        runCatching { nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdRegistrationListener) }
    }

    private fun unregisterNsd() {
        nsdRegistrationListener?.let {
            runCatching { nsdManager.unregisterService(it) }
            nsdRegistrationListener = null
        }
    }

    /**
     * 在局域网内发现 serviceName 含有 roomCode 的 paintboard 服务。
     * 需在协程中调用，调用方负责 withTimeoutOrNull 超时处理。
     */
    private suspend fun discoverHost(roomCode: String): Pair<String, Int>? {
        val result = CompletableDeferred<Pair<String, Int>?>()
        var discoveryListener: NsdManager.DiscoveryListener? = null

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                result.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.contains("$NSD_SERVICE_PREFIX$roomCode")) return
                if (result.isCompleted) return

                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        // 不完成 deferred，等待其他发现
                    }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val ip = si.host?.hostAddress
                        if (ip != null && !result.isCompleted) {
                            result.complete(Pair(ip, si.port))
                        }
                    }
                })
            }
        }

        runCatching {
            nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            return null
        }

        val found = result.await()
        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
        return found
    }

    // ===== 网络状态监听（Host 断网检测）=====

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (server != null) onNetworkLost?.invoke()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(networkCallback!!) }
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { cb ->
            runCatching {
                context.getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(cb)
            }
            networkCallback = null
        }
    }

    // ===== WebSocket 服务端 =====

    private fun Application.configureWebSocket(getPages: () -> List<DrawingPage>) {
        install(ServerWebSockets) {
            pingPeriodMillis = 2000
            timeoutMillis = 10000
        }
        routing {
            webSocket("/collab") {
                val deviceId = newDeviceId()
                try {
                    handleNewClient(deviceId, this, getPages)
                } finally {
                    sessions.remove(deviceId)
                    val leaving = connectedDevices.remove(deviceId)
                    notifyDeviceListChanged()
                    if (leaving != null) {
                        broadcastToAll(CollabMessage(
                            type = CollabMessage.DEVICE_LEAVE,
                            deviceId = deviceId
                        ))
                    }
                }
            }
        }
    }

    private suspend fun handleNewClient(
        deviceId: String,
        session: DefaultWebSocketServerSession,
        getPages: () -> List<DrawingPage>
    ) {
        var joined = false
        var deviceName = "PAD"

        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val msg = runCatching {
                json.decodeFromString<CollabMessage>(frame.readText())
            }.getOrNull() ?: break

            if (msg.type == CollabMessage.JOIN) {
                if (msg.token != currentRoomCode) {
                    session.send(Frame.Text(json.encodeToString(
                        CollabMessage(type = CollabMessage.JOIN_DENIED)
                    )))
                    session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "invalid token"))
                    return
                }
                deviceName = msg.deviceName ?: "PAD"
                joined = true
                break
            }
        }
        if (!joined) return

        val colors = listOf("#81C784", "#FFB74D", "#CE93D8", "#FFF176", "#F48FB1")
        val usedColors = connectedDevices.values.map { it.deviceColor }.toSet()
        val color = colors.firstOrNull { it !in usedColors } ?: colors[connectedDevices.size % colors.size]

        val device = CollabDevice(deviceId = deviceId, deviceName = deviceName, deviceColor = color)
        connectedDevices[deviceId] = device
        sessions[deviceId] = session

        session.send(Frame.Text(json.encodeToString(
            CollabMessage(
                type = CollabMessage.JOIN_ACK,
                deviceId = deviceId,
                deviceColor = color,
                deviceList = connectedDevices.values.toList(),
                pages = getPages()
            )
        )))

        val joinNotify = json.encodeToString(CollabMessage(
            type = CollabMessage.DEVICE_JOIN,
            deviceList = listOf(device)
        ))
        sessions.entries.filter { it.key != deviceId }.forEach { (_, s) ->
            runCatching { s.send(Frame.Text(joinNotify)) }
        }
        notifyDeviceListChanged()

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val text = frame.readText()
                val msg = runCatching {
                    json.decodeFromString<CollabMessage>(text)
                }.getOrNull() ?: continue

                sessions.entries.filter { it.key != deviceId }.forEach { (_, s) ->
                    runCatching { s.send(Frame.Text(text)) }
                }
                onMessageReceived?.invoke(msg)
            }
        } catch (_: ClosedReceiveChannelException) { }
    }

    // ===== 工具方法 =====

    private fun newDeviceId(): String {
        val bytes = ByteArray(4).also { java.security.SecureRandom().nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun notifyDeviceListChanged() {
        onDeviceListChanged?.invoke(connectedDevices.values.toList())
    }
}
