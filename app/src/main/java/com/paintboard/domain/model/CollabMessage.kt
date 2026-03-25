package com.paintboard.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 协同消息协议（JSON 序列化）
 *
 * Host ←→ PAD 双向传输，通过 `type` 字段区分类型。
 */
@Serializable
data class CollabMessage(
    val type: String,
    // JOIN / JOIN_ACK
    val token: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val deviceColor: String? = null,
    val deviceList: List<CollabDevice>? = null,
    // FULL_SYNC
    val pages: List<DrawingPage>? = null,
    // STROKE_ADD
    val pageId: String? = null,
    val stroke: Stroke? = null,
    // PAGE_ADD
    val page: DrawingPage? = null,
    // PAGE_DELETE
    // pageId 复用
    // PAGE_SWITCH
    val pageIndex: Int? = null,
    // PAGE_STROKES_SYNC / ERASE_OP
    val strokes: List<Stroke>? = null,
    val removedIds: List<String>? = null,
    // CURSOR_MOVE
    val x: Float? = null,
    val y: Float? = null,
) {
    companion object {
        const val JOIN = "JOIN"
        const val JOIN_ACK = "JOIN_ACK"
        const val JOIN_DENIED = "JOIN_DENIED"
        const val FULL_SYNC = "FULL_SYNC"
        const val DEVICE_JOIN = "DEVICE_JOIN"
        const val DEVICE_LEAVE = "DEVICE_LEAVE"
        const val STROKE_ADD = "STROKE_ADD"
        const val PAGE_ADD = "PAGE_ADD"
        const val PAGE_DELETE = "PAGE_DELETE"
        const val PAGE_SWITCH = "PAGE_SWITCH"
        const val PING = "PING"
        const val PONG = "PONG"
        const val SESSION_END = "SESSION_END"
        const val PAGE_STROKES_SYNC = "PAGE_STROKES_SYNC"
        const val ERASE_OP = "ERASE_OP"
        const val PAGE_CLEAR = "PAGE_CLEAR"
    }
}

@Serializable
data class CollabDevice(
    val deviceId: String,
    val deviceName: String,
    val deviceColor: String,
    val isHost: Boolean = false
)
