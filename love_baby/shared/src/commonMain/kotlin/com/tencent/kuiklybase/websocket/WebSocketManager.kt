package com.tencent.kuiklybase.websocket


/**
 * KuiklyWebsocket 核心管理器
 *
 * 封装 WebSocket 连接管理的完整生命周期，包含：
 * 1. 心跳机制（Ping-Pong 保活检测）
 * 2. 断线自动重连（指数退避 + 随机抖动）
 * 3. App 生命周期感知（后台断开省电，前台自动重连）
 * 4. 网络状态主动防御（Wi-Fi ↔ 蜂窝切换秒级重连）
 * 5. 强制 WSS 加密
 *
 * 消息格式、ACK、去重等业务语义由使用方自行处理。
 *
 * 使用方式：
 * ```kotlin
 * val manager = WebSocketManager()
 * manager.configure("wss://example.com/ws") {
 *     heartbeat {
 *         intervalMs = 15000L
 *         maxMissedPongs = 3
 *     }
 *     reconnect {
 *         enabled = true
 *         initialDelayMs = 2000L
 *         maxDelayMs = 60000L
 *     }
 *     onMessage = { data -> println("收到: $data") }
 * }
 * manager.connect()
 * ```
 */
class WebSocketManager {

    // ===== 状态 =====

    /** 当前连接状态 */
    var state: WebSocketState = WebSocketState.DISCONNECTED
        private set

    /** 当前配置 */
    var config: WebSocketConfig = WebSocketConfig()
        private set

    // ===== 内部状态 =====

    /** 当前重连尝试次数 */
    private var reconnectAttempt = 0

    /** 连续丢失的 Pong 次数 */
    private var missedPongs = 0

    /** 是否正在等待 Pong 回复 */
    private var waitingForPong = false

    /** 心跳定时器 ID */
    private var heartbeatTimerId: String? = null

    /** 重连定时器 ID */
    private var reconnectTimerId: String? = null

    /** 是否 App 在前台 */
    private var isAppForeground = true

    /** 是否主动关闭（主动关闭不触发重连） */
    private var isManualClose = false

    /** 事件监听器列表 */
    private val eventListeners = mutableListOf<(WebSocketEvent) -> Unit>()

    /** 原生端桥接接口（由原生 Module 注入） */
    var nativeBridge: WebSocketNativeBridge? = null

    // ===== 网络状态感知 =====

    /** 当前网络是否可用 */
    private var isNetworkAvailable = true

    /** 当前网络类型 */
    private var currentNetworkType: String = "unknown"

    /** 是否已注册网络监听 */
    private var isNetworkListenerRegistered = false

    /** 内部计数器（用于生成定时器 ID） */
    private var timerCounter = 0L

    // ===== 公开 API =====

    /**
     * 配置 WebSocket
     */
    fun configure(url: String, block: WebSocketConfig.() -> Unit = {}) {
        config = WebSocketConfig().apply {
            this.url = url
            block()
        }
        val errors = config.validate()
        if (errors.isNotEmpty()) {
            val errorMsg = "WebSocket 配置错误: ${errors.joinToString("; ")}"
            dispatchEvent(WebSocketEvent.Error(errorMsg))
        }
    }

    /**
     * 建立连接
     */
    fun connect() {
        if (state == WebSocketState.CONNECTED || state == WebSocketState.CONNECTING) {
            return
        }

        isManualClose = false
        reconnectAttempt = 0
        updateState(WebSocketState.CONNECTING)

        startNetworkListener()
        nativeBridge?.connect(config.url, config.headers, config.connectTimeoutMs)
    }

    /**
     * 断开连接（主动关闭，不触发重连）
     */
    fun disconnect(code: Int = 1000, reason: String = "Normal closure") {
        isManualClose = true
        stopHeartbeat()
        cancelReconnect()
        stopNetworkListener()

        nativeBridge?.disconnect(code, reason)
        updateState(WebSocketState.CLOSED)
        dispatchEvent(WebSocketEvent.Disconnected(WebSocketCloseReason.MANUAL, code, reason))
    }

    /**
     * 发送文本数据（原始字符串，格式由业务方决定）
     *
     * @param data 要发送的字符串数据
     */
    fun send(data: String) {
        if (state == WebSocketState.CONNECTED) {
            nativeBridge?.send(data)
        }
    }

    /**
     * 发送二进制数据（适用于 Protobuf/FlatBuffers 等二进制协议）
     *
     * 内部通过 Base64 编码传递给原生端，原生端解码后以 WebSocket Binary Frame 发送。
     *
     * @param data 要发送的二进制数据
     */
    fun sendBinary(data: ByteArray) {
        if (state == WebSocketState.CONNECTED) {
            val base64 = encodeBase64(data)
            nativeBridge?.sendBinary(base64)
        }
    }

    /**
     * 添加事件监听器
     */
    fun addEventListener(listener: (WebSocketEvent) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * 移除事件监听器
     */
    fun removeEventListener(listener: (WebSocketEvent) -> Unit) {
        eventListeners.remove(listener)
    }

    // ===== 原生端回调（由 NativeBridge 调用） =====

    /**
     * 原生端通知：连接成功
     */
    fun onNativeConnected() {
        updateState(WebSocketState.CONNECTED)
        reconnectAttempt = 0
        missedPongs = 0
        waitingForPong = false

        dispatchEvent(WebSocketEvent.Connected(config.url))
        config.onConnected?.invoke(config.url)

        if (config.heartbeatOptions.enabled) {
            startHeartbeat()
        }
    }

    /**
     * 原生端通知：连接断开
     */
    fun onNativeDisconnected(code: Int, reason: String) {
        stopHeartbeat()

        val closeReason = when {
            isManualClose -> WebSocketCloseReason.MANUAL
            code == 1000 -> WebSocketCloseReason.NORMAL
            code == 1001 -> WebSocketCloseReason.SERVER_CLOSED
            else -> WebSocketCloseReason.NETWORK_ERROR
        }

        dispatchEvent(WebSocketEvent.Disconnected(closeReason, code, reason))
        config.onDisconnected?.invoke(closeReason, code, reason)

        if (!isManualClose && state != WebSocketState.CLOSED) {
            updateState(WebSocketState.DISCONNECTED)
            scheduleReconnect()
        }
    }

    /**
     * 原生端通知：收到消息
     *
     * 心跳 Pong 由组件内部处理，其他消息直接透传给业务方。
     * 业务方需自行约定心跳 Pong 的识别方式（通过 pingPayload 配置）。
     */
    fun onNativeMessage(data: String) {
        // 检查是否为心跳 Pong 回复
        if (isHeartbeatPong(data)) {
            missedPongs = 0
            waitingForPong = false
            dispatchEvent(WebSocketEvent.Heartbeat(HeartbeatType.PONG_RECEIVED))
            config.onHeartbeat?.invoke(HeartbeatType.PONG_RECEIVED)
            return
        }

        // 业务消息，直接透传
        dispatchEvent(WebSocketEvent.MessageReceived(data))
        config.onMessage?.invoke(data)
    }

    /**
     * 原生端通知：发生错误
     */
    fun onNativeError(error: String) {
        dispatchEvent(WebSocketEvent.Error(error))
        config.onError?.invoke(error)

        if (state == WebSocketState.CONNECTING || state == WebSocketState.CONNECTED) {
            updateState(WebSocketState.DISCONNECTED)
            if (!isManualClose) {
                scheduleReconnect()
            }
        }
    }

    /**
     * 原生端通知：收到二进制消息
     *
     * 原生端将收到的 Binary Frame 以 Base64 编码传递过来，
     * 此处解码后透传给业务方。
     */
    fun onNativeBinaryMessage(base64Data: String) {
        val bytes = decodeBase64(base64Data)
        dispatchEvent(WebSocketEvent.BinaryMessageReceived(bytes))
        config.onBinaryMessage?.invoke(bytes)
    }

    // ===== App 生命周期感知 =====

    /**
     * App 进入后台 — 断开连接，停止心跳（省电）
     */
    fun onAppBackground() {
        if (!config.lifecycleAware) return

        isAppForeground = false
        stopHeartbeat()
        cancelReconnect()

        if (state == WebSocketState.CONNECTED) {
            nativeBridge?.disconnect(1000, "App entered background")
            updateState(WebSocketState.DISCONNECTED)
            dispatchEvent(
                WebSocketEvent.Disconnected(
                    WebSocketCloseReason.APP_BACKGROUND,
                    1000,
                    "App entered background"
                )
            )
        }
    }

    /**
     * App 回到前台 — 立即触发重连
     */
    fun onAppForeground() {
        if (!config.lifecycleAware) return

        isAppForeground = true

        if (state == WebSocketState.DISCONNECTED && !isManualClose) {
            reconnectAttempt = 0
            updateState(WebSocketState.RECONNECTING)
            nativeBridge?.connect(config.url, config.headers, config.connectTimeoutMs)
        }
    }

    /**
     * 销毁管理器，释放资源
     */
    fun destroy() {
        disconnect()
        stopNetworkListener()
        eventListeners.clear()
        nativeBridge = null
    }

    // ===== 心跳机制 =====

    private fun startHeartbeat() {
        stopHeartbeat()
        missedPongs = 0
        waitingForPong = false

        val intervalMs = config.heartbeatOptions.intervalMs
        heartbeatTimerId = generateTimerId("hb")

        nativeBridge?.startTimer(heartbeatTimerId!!, intervalMs, true) {
            onHeartbeatTick()
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimerId?.let {
            nativeBridge?.stopTimer(it)
            heartbeatTimerId = null
        }
    }

    private fun onHeartbeatTick() {
        if (state != WebSocketState.CONNECTED) return

        if (waitingForPong) {
            missedPongs++
        }

        if (missedPongs > config.heartbeatOptions.maxMissedPongs) {
            dispatchEvent(WebSocketEvent.Heartbeat(HeartbeatType.TIMEOUT))
            config.onHeartbeat?.invoke(HeartbeatType.TIMEOUT)
            stopHeartbeat()

            nativeBridge?.disconnect(1001, "Heartbeat timeout")
            updateState(WebSocketState.DISCONNECTED)
            dispatchEvent(
                WebSocketEvent.Disconnected(
                    WebSocketCloseReason.HEARTBEAT_TIMEOUT,
                    1001,
                    "Heartbeat timeout: missed ${missedPongs} pongs"
                )
            )
            scheduleReconnect()
            return
        }

        // 发送 Ping（使用配置的 pingPayload）
        waitingForPong = true
        nativeBridge?.send(config.heartbeatOptions.pingPayload)
        dispatchEvent(WebSocketEvent.Heartbeat(HeartbeatType.PING_SENT))
        config.onHeartbeat?.invoke(HeartbeatType.PING_SENT)
    }

    /**
     * 判断收到的消息是否为心跳 Pong 回复
     *
     * 默认策略：如果 pingPayload 非空，则检查收到的数据是否等于 pingPayload（echo 模式）。
     * 如果 pingPayload 为空，则不自动识别 Pong（业务方需自行处理）。
     *
     * 业务方可通过设置 pingPayload 来约定心跳协议，例如：
     * - pingPayload = "ping" → 服务端收到后回复 "ping"（echo）或 "pong"
     */
    private fun isHeartbeatPong(data: String): Boolean {
        val payload = config.heartbeatOptions.pingPayload
        if (payload.isEmpty()) return false
        return data == payload
    }

    // ===== 断线重连（指数退避） =====

    private fun scheduleReconnect() {
        if (!config.reconnectOptions.enabled) return
        if (isManualClose) return
        if (!isAppForeground && config.lifecycleAware) return
        if (reconnectAttempt >= config.reconnectOptions.maxAttempts) {
            dispatchEvent(WebSocketEvent.Error("重连次数已达上限（${config.reconnectOptions.maxAttempts}）"))
            return
        }

        reconnectAttempt++
        val delayMs = config.reconnectOptions.calculateDelay(reconnectAttempt)

        updateState(WebSocketState.RECONNECTING)
        dispatchEvent(WebSocketEvent.Reconnecting(reconnectAttempt, delayMs))
        config.onReconnecting?.invoke(reconnectAttempt, delayMs)

        reconnectTimerId = generateTimerId("rc")
        nativeBridge?.startTimer(reconnectTimerId!!, delayMs, false) {
            doReconnect()
        }
    }

    private fun cancelReconnect() {
        reconnectTimerId?.let {
            nativeBridge?.stopTimer(it)
            reconnectTimerId = null
        }
    }

    private fun doReconnect() {
        if (state == WebSocketState.CONNECTED) return
        if (isManualClose) return

        updateState(WebSocketState.CONNECTING)
        nativeBridge?.connect(config.url, config.headers, config.connectTimeoutMs)
    }

    // ===== 内部工具 =====

    private fun updateState(newState: WebSocketState) {
        val oldState = state
        if (oldState != newState) {
            state = newState
            dispatchEvent(WebSocketEvent.StateChanged(oldState, newState))
            config.onStateChanged?.invoke(oldState, newState)
        }
    }

    private fun dispatchEvent(event: WebSocketEvent) {
        for (listener in eventListeners) {
            try {
                listener(event)
            } catch (_: Exception) {
            }
        }
    }

    private fun generateTimerId(prefix: String): String {
        return "${prefix}_${++timerCounter}"
    }

    // ===== 网络状态主动防御 =====

    private fun startNetworkListener() {
        if (isNetworkListenerRegistered) return

        nativeBridge?.registerNetworkListener { isAvailable, networkType ->
            onNetworkChanged(isAvailable, networkType)
        }
        isNetworkListenerRegistered = true
    }

    private fun stopNetworkListener() {
        if (!isNetworkListenerRegistered) return

        nativeBridge?.unregisterNetworkListener()
        isNetworkListenerRegistered = false
    }

    /**
     * 网络状态变化回调
     *
     * 核心策略：
     * 1. 网络恢复 → 立即重连（0 延迟）
     * 2. 网络类型切换（Wi-Fi ↔ 蜂窝） → 主动断开旧连接，立即重连
     * 3. 网络断开 → 停止定时器省电，等待网络恢复
     */
    fun onNetworkChanged(isAvailable: Boolean, networkType: String) {
        val previousAvailable = isNetworkAvailable
        val previousType = currentNetworkType

        isNetworkAvailable = isAvailable
        currentNetworkType = networkType

        dispatchEvent(WebSocketEvent.NetworkChanged(isAvailable, networkType))

        if (isManualClose) return
        if (!isAppForeground && config.lifecycleAware) return

        when {
            // 网络恢复
            !previousAvailable && isAvailable -> {
                if (state == WebSocketState.DISCONNECTED || state == WebSocketState.RECONNECTING) {
                    cancelReconnect()
                    reconnectAttempt = 0
                    updateState(WebSocketState.CONNECTING)
                    nativeBridge?.connect(config.url, config.headers, config.connectTimeoutMs)
                }
            }

            // 网络类型切换
            isAvailable && previousAvailable
                && previousType != networkType
                && previousType != "unknown"
                && state == WebSocketState.CONNECTED -> {
                stopHeartbeat()
                nativeBridge?.disconnect(1001, "Network switched: $previousType -> $networkType")

                dispatchEvent(
                    WebSocketEvent.Disconnected(
                        WebSocketCloseReason.NETWORK_SWITCH,
                        1001,
                        "Network switched: $previousType -> $networkType"
                    )
                )

                reconnectAttempt = 0
                updateState(WebSocketState.CONNECTING)
                nativeBridge?.connect(config.url, config.headers, config.connectTimeoutMs)
            }

            // 网络断开
            !isAvailable -> {
                stopHeartbeat()
                cancelReconnect()

                if (state == WebSocketState.CONNECTED || state == WebSocketState.CONNECTING) {
                    updateState(WebSocketState.DISCONNECTED)
                    dispatchEvent(
                        WebSocketEvent.Disconnected(
                            WebSocketCloseReason.NETWORK_ERROR,
                            0,
                            "Network unavailable"
                        )
                    )
                }
            }
        }
    }

    // ===== Base64 编解码（跨平台兼容） =====

    private val BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private fun encodeBase64(data: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(BASE64_CHARS[(b0 shr 2) and 0x3F])
            sb.append(BASE64_CHARS[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            if (i + 1 < data.size) {
                sb.append(BASE64_CHARS[((b1 shl 2) or (b2 shr 6)) and 0x3F])
            } else {
                sb.append('=')
            }
            if (i + 2 < data.size) {
                sb.append(BASE64_CHARS[b2 and 0x3F])
            } else {
                sb.append('=')
            }
            i += 3
        }
        return sb.toString()
    }

    private fun decodeBase64(str: String): ByteArray {
        val clean = str.replace("\n", "").replace("\r", "")
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < clean.length) {
            val c0 = BASE64_CHARS.indexOf(clean[i])
            val c1 = if (i + 1 < clean.length) BASE64_CHARS.indexOf(clean[i + 1]) else 0
            val c2 = if (i + 2 < clean.length && clean[i + 2] != '=') BASE64_CHARS.indexOf(clean[i + 2]) else -1
            val c3 = if (i + 3 < clean.length && clean[i + 3] != '=') BASE64_CHARS.indexOf(clean[i + 3]) else -1
            bytes.add(((c0 shl 2) or (c1 shr 4)).toByte())
            if (c2 >= 0) bytes.add((((c1 and 0xF) shl 4) or (c2 shr 2)).toByte())
            if (c3 >= 0) bytes.add((((c2 and 0x3) shl 6) or c3).toByte())
            i += 4
        }
        return bytes.toByteArray()
    }
}
