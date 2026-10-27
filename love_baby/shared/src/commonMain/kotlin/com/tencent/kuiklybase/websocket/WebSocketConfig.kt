package com.tencent.kuiklybase.websocket

/**
 * KuiklyWebsocket 配置类
 *
 * 支持 DSL 分组 + 平铺兼容两种写法：
 * ```kotlin
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
 * ```
 */
class WebSocketConfig {

    // ===== 基础属性 =====

    /** WebSocket 连接地址（必须使用 wss:// 协议） */
    var url: String = ""

    /** 自定义请求头 */
    var headers: Map<String, String> = emptyMap()

    /** 连接超时时间（毫秒） */
    var connectTimeoutMs: Long = 10_000L

    /** 是否在创建后立即连接 */
    var autoConnect: Boolean = true

    /** 是否启用 App 生命周期感知 */
    var lifecycleAware: Boolean = true

    /**
     * 是否允许 Debug 模式下使用明文 ws:// 连接
     *
     * 默认 false（强制 wss://）。设为 true 时，validate() 不再强制 wss:// 协议，
     * 方便本地调试连接局域网测试服务器（如 ws://192.168.x.x:8080）。
     *
     * **注意：请确保 Release 包中此值为 false，否则存在安全风险。**
     */
    var allowCleartextInDebug: Boolean = false

    // ===== 事件回调 =====
    // 以下所有回调均保证在主线程（UI 线程）触发，业务方可直接在回调中更新 UI。
    // Android: 通过 Handler(Looper.getMainLooper()) 切换到主线程
    // iOS: 通过 dispatch_async(dispatch_get_main_queue()) 切换到主线程
    // 鸿蒙: 事件回调在 JS/UI 线程执行

    /** 连接成功回调（主线程） */
    var onConnected: ((String) -> Unit)? = null

    /** 连接断开回调（主线程） */
    var onDisconnected: ((WebSocketCloseReason, Int, String) -> Unit)? = null

    /** 收到文本消息回调（主线程，原始字符串，格式由业务方自行解析） */
    var onMessage: ((String) -> Unit)? = null

    /** 收到二进制消息回调（主线程，原始字节数组，适用于 Protobuf/FlatBuffers 等二进制协议） */
    var onBinaryMessage: ((ByteArray) -> Unit)? = null

    /** 错误回调（主线程） */
    var onError: ((String) -> Unit)? = null

    /** 状态变化回调（主线程） */
    var onStateChanged: ((WebSocketState, WebSocketState) -> Unit)? = null

    /** 重连事件回调（主线程） */
    var onReconnecting: ((Int, Long) -> Unit)? = null

    /** 心跳事件回调（主线程） */
    var onHeartbeat: ((HeartbeatType) -> Unit)? = null

    // ===== DSL 分组属性 =====

    val heartbeatOptions = HeartbeatOptions()
    val reconnectOptions = ReconnectOptions()

    /** 心跳配置分组 */
    fun heartbeat(block: HeartbeatOptions.() -> Unit) {
        heartbeatOptions.block()
    }

    /** 重连配置分组 */
    fun reconnect(block: ReconnectOptions.() -> Unit) {
        reconnectOptions.block()
    }

    // ===== 向后兼容代理属性 =====

    /** 是否启用心跳 */
    var enableHeartbeat: Boolean
        get() = heartbeatOptions.enabled
        set(value) { heartbeatOptions.enabled = value }

    /** 心跳间隔（毫秒） */
    var heartbeatIntervalMs: Long
        get() = heartbeatOptions.intervalMs
        set(value) { heartbeatOptions.intervalMs = value }

    /** 是否启用自动重连 */
    var enableReconnect: Boolean
        get() = reconnectOptions.enabled
        set(value) { reconnectOptions.enabled = value }

    /**
     * 校验配置合法性
     */
    internal fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (url.isBlank()) {
            errors.add("WebSocket URL 不能为空")
        }
        if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
            errors.add("WebSocket URL 必须以 wss:// 或 ws:// 开头")
        }
        if (url.startsWith("ws://") && !allowCleartextInDebug) {
            errors.add("必须使用 wss:// 协议以确保安全（iOS ATS 和 Android Network Security Config 要求）。如需本地调试，请设置 allowCleartextInDebug = true")
        }
        if (heartbeatOptions.enabled && heartbeatOptions.intervalMs < 1000L) {
            errors.add("心跳间隔不能小于 1 秒")
        }
        if (reconnectOptions.enabled && reconnectOptions.initialDelayMs < 500L) {
            errors.add("重连初始延迟不能小于 500 毫秒")
        }
        return errors
    }
}

/**
 * 心跳配置
 */
class HeartbeatOptions {
    /** 是否启用心跳 */
    var enabled: Boolean = true

    /** 心跳发送间隔（毫秒），默认 15 秒 */
    var intervalMs: Long = 15_000L

    /** 连续丢失 Pong 的最大次数，超过后认为连接已死 */
    var maxMissedPongs: Int = 3

    /** 自定义 Ping 消息内容（默认空字符串） */
    var pingPayload: String = ""
}

/**
 * 断线重连配置（指数退避）
 */
class ReconnectOptions {
    /** 是否启用自动重连 */
    var enabled: Boolean = true

    /** 最大重连次数，默认无限重连 */
    var maxAttempts: Int = Int.MAX_VALUE

    /** 初始重连延迟（毫秒），默认 2 秒 */
    var initialDelayMs: Long = 2_000L

    /** 最大重连延迟（毫秒），默认 60 秒 */
    var maxDelayMs: Long = 60_000L

    /** 退避倍数，默认 2.0（每次延迟翻倍） */
    var backoffMultiplier: Double = 2.0

    /** 是否添加随机抖动（避免多客户端同时重连导致雪崩） */
    var jitterEnabled: Boolean = true

    /** 抖动系数（0.0 ~ 1.0），默认 0.25 */
    var jitterFactor: Double = 0.25

    /**
     * 计算第 N 次重连的延迟时间（指数退避 + 可选抖动）
     */
    fun calculateDelay(attempt: Int): Long {
        var delay = initialDelayMs
        repeat(attempt - 1) {
            delay = (delay * backoffMultiplier).toLong()
            if (delay > maxDelayMs) {
                delay = maxDelayMs
                return@repeat
            }
        }
        delay = delay.coerceAtMost(maxDelayMs)

        if (jitterEnabled && jitterFactor > 0.0) {
            val jitterRange = (delay * jitterFactor).toLong()
            val jitter = (-jitterRange..jitterRange).random()
            delay = (delay + jitter).coerceAtLeast(initialDelayMs)
        }

        return delay
    }
}
