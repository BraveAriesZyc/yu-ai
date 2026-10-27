package com.tencent.kuiklybase.websocket


/**
 * WebSocket 连接状态
 */
enum class WebSocketState {
    /** 未连接 */
    DISCONNECTED,
    /** 连接中 */
    CONNECTING,
    /** 已连接 */
    CONNECTED,
    /** 重连中 */
    RECONNECTING,
    /** 已关闭（主动关闭，不会重连） */
    CLOSED
}

/**
 * WebSocket 关闭原因
 */
enum class WebSocketCloseReason {
    /** 正常关闭 */
    NORMAL,
    /** 服务端关闭 */
    SERVER_CLOSED,
    /** 网络错误 */
    NETWORK_ERROR,
    /** 心跳超时 */
    HEARTBEAT_TIMEOUT,
    /** App 退到后台 */
    APP_BACKGROUND,
    /** 手动关闭 */
    MANUAL,
    /** 网络切换（Wi-Fi ↔ 蜂窝）导致的主动断开重连 */
    NETWORK_SWITCH
}

/**
 * WebSocket 连接事件
 */
sealed class WebSocketEvent {
    /** 连接成功 */
    data class Connected(val url: String) : WebSocketEvent()
    /** 连接断开 */
    data class Disconnected(val reason: WebSocketCloseReason, val code: Int = 0, val message: String = "") : WebSocketEvent()
    /** 收到文本消息（原始字符串，格式由业务方自行解析） */
    data class MessageReceived(val data: String) : WebSocketEvent()
    /** 收到二进制消息（原始字节数组，适用于 Protobuf/FlatBuffers 等二进制协议） */
    data class BinaryMessageReceived(val data: ByteArray) : WebSocketEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BinaryMessageReceived) return false
            return data.contentEquals(other.data)
        }
        override fun hashCode(): Int = data.contentHashCode()
    }
    /** 发生错误 */
    data class Error(val error: String, val throwable: Throwable? = null) : WebSocketEvent()
    /** 连接状态变化 */
    data class StateChanged(val oldState: WebSocketState, val newState: WebSocketState) : WebSocketEvent()
    /** 心跳事件 */
    data class Heartbeat(val type: HeartbeatType) : WebSocketEvent()
    /** 重连事件 */
    data class Reconnecting(val attempt: Int, val delayMs: Long) : WebSocketEvent()
    /** 网络状态变化事件 */
    data class NetworkChanged(val isAvailable: Boolean, val networkType: String) : WebSocketEvent()
}

/**
 * 心跳类型
 */
enum class HeartbeatType {
    PING_SENT,
    PONG_RECEIVED,
    TIMEOUT
}
