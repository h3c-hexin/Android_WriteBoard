package com.h3c.writeboard.ui.collab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.h3c.writeboard.domain.model.CollabDevice
import com.h3c.writeboard.ui.common.ToolbarPopup
import com.h3c.writeboard.ui.canvas.CanvasViewModel
import com.h3c.writeboard.ui.theme.IconDefault

/**
 * 协同 Popup（§0 规范）
 *
 * 四种视图：
 *   NONE       → PAD 输入 6 位房间码 / Host 按钮
 *   CONNECTING → 加载圈（等待 NSD + WebSocket 建立）
 *   HOST       → 展示房间码 + IP + 设备列表
 *   PARTICIPANT → 展示设备列表 + 离开
 */
@Composable
fun CollabPopup(
    collabState: CanvasViewModel.CollabUiState,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit,
    onJoin: (roomCode: String) -> Unit,
    onJoinDirect: (host: String, roomCode: String) -> Unit,
    onRejoin: () -> Unit,
    onLeave: () -> Unit,
    onCancelJoin: () -> Unit,
    onDismiss: () -> Unit
) {
    ToolbarPopup(onDismiss = onDismiss) {
        when (collabState.role) {
                CanvasViewModel.CollabRole.NONE ->
                    NoneView(
                        error = collabState.error,
                        nsdTimeout = collabState.nsdTimeout,
                        lastRoomCode = collabState.lastRoomCode,
                        onStartHosting = onStartHosting,
                        onJoin = onJoin,
                        onJoinDirect = onJoinDirect,
                        onRejoin = onRejoin
                    )
                CanvasViewModel.CollabRole.CONNECTING ->
                    ConnectingView(onCancelJoin)
                CanvasViewModel.CollabRole.HOST ->
                    HostView(collabState, onStopHosting, onDismiss)
                CanvasViewModel.CollabRole.PARTICIPANT ->
                    ParticipantView(collabState, onLeave, onDismiss)
        }
    }
}

// ===== 未开启视图（入口）=====

@Composable
private fun NoneView(
    error: String?,
    nsdTimeout: Boolean,
    lastRoomCode: String?,
    onStartHosting: () -> Unit,
    onJoin: (String) -> Unit,
    onJoinDirect: (host: String, roomCode: String) -> Unit,
    onRejoin: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var expandManual by remember(nsdTimeout) { mutableStateOf(nsdTimeout) }
    var ipInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(20.dp).width(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("局域网协同", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(16.dp))

        // 重新加入上次协同
        if (lastRoomCode != null) {
            Button(
                onClick = onRejoin,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("重新加入（房间 $lastRoomCode）", color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
        }

        // 开启协同按钮（作为主设备）
        Button(
            onClick = onStartHosting,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("开启协同（作为主设备）", color = Color.White)
        }

        Spacer(Modifier.height(20.dp))
        Text("── 或加入已有房间 ──", color = IconDefault, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))

        // 6 格 PIN 输入
        PinInput(
            value = pinInput,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it }
        )

        // 错误提示
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Color(0xFFEF9A9A), fontSize = 12.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { if (pinInput.length == 6) onJoin(pinInput) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            modifier = Modifier.fillMaxWidth(),
            enabled = pinInput.length == 6
        ) {
            Text("加入房间", color = Color.White)
        }

        // ===== 手动连接折叠区 =====
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandManual = !expandManual }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expandManual) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = IconDefault,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "自动发现失败？手动输入 IP",
                color = IconDefault,
                fontSize = 12.sp
            )
        }

        AnimatedVisibility(visible = expandManual) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                Text("主机 IP 地址", color = IconDefault, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                IpInput(value = ipInput, onValueChange = { ipInput = it.trim() })
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (ipInput.isNotBlank() && pinInput.length == 6) {
                            onJoinDirect(ipInput, pinInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = ipInput.isNotBlank() && pinInput.length == 6
                ) {
                    Text("直接连接", color = Color.White)
                }
            }
        }
    }
}

// ===== 连接中视图 =====

@Composable
private fun ConnectingView(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp).width(240.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("正在连接...", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))
        CircularProgressIndicator(color = Color(0xFF90CAF9), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text("正在局域网内搜索主设备", color = IconDefault, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) {
            Text("取消", color = IconDefault)
        }
    }
}

// ===== Host 已开启视图 =====

@Composable
private fun HostView(
    collabState: CanvasViewModel.CollabUiState,
    onStopHosting: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp).width(300.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("协同书写", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { onStopHosting(); onDismiss() }) {
                Text("结束协同", color = Color(0xFFEF9A9A), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        // 房间码大字分格显示
        Text("房间码", color = IconDefault, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        RoomCodeDisplay(collabState.roomCode ?: "------")

        // 本机 IP（手动连接兜底）
        if (collabState.hostIp != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "主机 IP：${collabState.hostIp}",
                color = IconDefault,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(16.dp))
        DeviceList(collabState.devices)
    }
}

// ===== PAD 参与者视图 =====

@Composable
private fun ParticipantView(
    collabState: CanvasViewModel.CollabUiState,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(20.dp).width(280.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("协同中", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { onLeave(); onDismiss() }) {
                Text("离开房间", color = Color(0xFFEF9A9A), fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        DeviceList(collabState.devices)
    }
}

// ===== 子组件 =====

/** 6 格 PIN 输入框（单个 BasicTextField 驱动，显示分格效果） */
@Composable
private fun PinInput(value: String, onValueChange: (String) -> Unit) {
    Box {
        // 隐藏的真实输入框
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            modifier = Modifier.matchParentSize()
        )
        // 可见的分格
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(6) { index ->
                val digit = value.getOrNull(index)?.toString() ?: ""
                val isFocused = index == value.length
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) Color(0xFF90CAF9) else IconDefault,
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(digit, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** IP 地址输入框 */
@Composable
private fun IpInput(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        cursorBrush = SolidColor(Color(0xFF90CAF9)),
        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IconDefault, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("192.168.x.x", color = IconDefault.copy(alpha = 0.5f), fontSize = 14.sp)
                }
                inner()
            }
        }
    )
}

/** 房间码大字分格展示（Host 端显示用） */
@Composable
private fun RoomCodeDisplay(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        code.forEach { digit ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF1A3A5C), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    digit.toString(),
                    color = Color(0xFF90CAF9),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 在线设备列表 */
@Composable
private fun DeviceList(devices: List<CollabDevice>) {
    if (devices.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "在线设备（${devices.size} 台）",
            color = IconDefault,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        devices.forEach { device ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(parseColor(device.deviceColor), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(device.deviceName, color = Color.White, fontSize = 14.sp)
                if (device.isHost) {
                    Spacer(Modifier.width(6.dp))
                    Text("主持人", color = Color(0xFF90CAF9), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    Color.Gray
}
