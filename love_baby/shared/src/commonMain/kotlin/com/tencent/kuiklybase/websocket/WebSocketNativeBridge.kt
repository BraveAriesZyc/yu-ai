package com.tencent.kuiklybase.websocket

/**
 * WebSocket 原生端桥接接口
 *
 * 由各端原生 Module（Android/iOS/鸿蒙）实现，提供 WebSocket 底层通信能力。
 * KMP 层通过此接口与原生端交互，不直接依赖任何平台 API。
 *
 * 实现要求：
 * - connect() 连接成功后调用 WebSocketManager.onNativeConnected()
 * - connect() 连接失败后调用 WebSocketManager.onNativeError()
 * - 收到消息后调用 WebSocketManager.onNativeMessage()
 * - 连接断开后调用 WebSocketManager.onNativeDisconnected()
 * - 发生错误时调用 WebSocketManager.onNativeError()
 */
interface WebSocketNativeBridge {

    /**
     * 建立 WebSocket 连接
     *
     * @param url WebSocket 地址（必须 wss://）
     * @param headers 自定义请求头
     * @param timeoutMs 连接超时时间（毫秒）
     */
    fun connect(url: String, headers: Map<String, String>, timeoutMs: Long)

    /**
     * 断开 WebSocket 连接
     *
     * @param code 关闭状态码（如 1000 正常关闭）
     * @param reason 关闭原因描述
     */
    fun disconnect(code: Int, reason: String)

    /**
     * 发送文本数据
     *
     * @param data 要发送的字符串数据
     */
    fun send(data: String)

    /**
     * 发送二进制数据
     *
     * 原生端应以 WebSocket Binary Frame 发送。
     * KMP 层通过 Base64 编码传递给原生端，原生端解码后以 Binary Frame 发送。
     *
     * @param data 要发送的二进制数据（Base64 编码字符串）
     */
    fun sendBinary(base64Data: String)

    /**
     * 启动定时器
     *
     * @param timerId 定时器唯一标识
     * @param intervalMs 间隔时间（毫秒）
     * @param repeating 是否重复执行
     * @param callback 回调函数
     */
    fun startTimer(timerId: String, intervalMs: Long, repeating: Boolean, callback: () -> Unit)

    /**
     * 停止定时器
     *
     * @param timerId 定时器唯一标识
     */
    fun stopTimer(timerId: String)

    /**
     * 注册网络状态监听
     *
     * 原生端应监听系统网络状态变化（如 Android ConnectivityManager、iOS NWPathMonitor），
     * 当网络状态发生变化时，通过 callback 通知 KMP 层。
     *
     * @param callback 网络状态变化回调，参数含义：
     *   - isAvailable: 当前网络是否可用
     *   - networkType: 网络类型标识（"wifi" / "cellular" / "ethernet" / "unknown" / "none"）
     */
    fun registerNetworkListener(callback: (isAvailable: Boolean, networkType: String) -> Unit)

    /**
     * 注销网络状态监听，释放资源
     */
    fun unregisterNetworkListener()
}
