package com.tencent.kuiklybase.websocket

import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * KuiklyWebsocket Module —— Kuikly Pager 层集成入口
 *
 * 使用方式：
 * ```kotlin
 * class MyPage : BasePager() {
 *     override fun createExternalModules(): Map<String, Module>? {
 *         return mapOf(WebSocketModule.MODULE_NAME to WebSocketModule())
 *     }
 *
 *     override fun created() {
 *         super.created()
 *         val wsModule = acquireModule<WebSocketModule>(WebSocketModule.MODULE_NAME)
 *         wsModule.manager.configure("wss://example.com/ws") {
 *             onMessage = { data -> handleMessage(data) }
 *         }
 *         wsModule.manager.connect()
 *     }
 * }
 * ```
 */
class WebSocketModule : Module() {

    /** WebSocket 核心管理器 */
    val manager = WebSocketManager()

    private var bridgeInitialized = false

    /** 定时器回调缓存 */
    private val timerCallbacks = mutableMapOf<String, () -> Unit>()

    /** 网络状态回调 */
    private var networkCallback: ((isAvailable: Boolean, networkType: String) -> Unit)? = null

    override fun moduleName(): String = MODULE_NAME

    /**
     * 初始化 bridge，将基于 toNative 的桥接实现注入 manager。
     * 在首次使用 manager 前调用。
     */
    fun ensureBridge() {
        if (bridgeInitialized) return
        bridgeInitialized = true
        manager.nativeBridge = createBridge()
    }

    private fun createBridge(): WebSocketNativeBridge {
        val module = this
        return object : WebSocketNativeBridge {

            override fun connect(url: String, headers: Map<String, String>, timeoutMs: Long) {
                val params = JSONObject()
                params.put("url", url)
                params.put("timeoutMs", timeoutMs)
                val headersJson = JSONObject()
                for ((key, value) in headers) {
                    headersJson.put(key, value)
                }
                params.put("headers", headersJson)
                // keepCallbackAlive=true，让原生端能持续回调事件
                module.toNative(true, METHOD_CONNECT, params.toString(), { result ->
                    module.handleNativeEvent(result)
                }, false)
            }

            override fun disconnect(code: Int, reason: String) {
                val params = JSONObject()
                params.put("code", code)
                params.put("reason", reason)
                module.toNative(false, METHOD_DISCONNECT, params.toString(), null, false)
            }

            override fun send(data: String) {
                val params = JSONObject()
                params.put("data", data)
                module.toNative(false, METHOD_SEND, params.toString(), null, false)
            }

            override fun sendBinary(base64Data: String) {
                val params = JSONObject()
                params.put("data", base64Data)
                module.toNative(false, METHOD_SEND_BINARY, params.toString(), null, false)
            }

            override fun startTimer(timerId: String, intervalMs: Long, repeating: Boolean, callback: () -> Unit) {
                timerCallbacks[timerId] = callback
                val params = JSONObject()
                params.put("timerId", timerId)
                params.put("intervalMs", intervalMs)
                params.put("repeating", repeating)
                module.toNative(true, METHOD_START_TIMER, params.toString(), { result ->
                    module.handleNativeEvent(result)
                }, false)
            }

            override fun stopTimer(timerId: String) {
                timerCallbacks.remove(timerId)
                val params = JSONObject()
                params.put("timerId", timerId)
                module.toNative(false, METHOD_STOP_TIMER, params.toString(), null, false)
            }

            override fun registerNetworkListener(callback: (isAvailable: Boolean, networkType: String) -> Unit) {
                networkCallback = callback
                module.toNative(true, METHOD_REGISTER_NETWORK, null, { result ->
                    module.handleNativeEvent(result)
                }, false)
            }

            override fun unregisterNetworkListener() {
                networkCallback = null
                module.toNative(false, METHOD_UNREGISTER_NETWORK, null, null, false)
            }
        }
    }

    /**
     * 处理原生端通过 callback 回传的事件
     */
    private fun handleNativeEvent(result: Any?) {
        val resultStr = result?.toString() ?: return
        val json = try { JSONObject(resultStr) } catch (e: Exception) { return }
        val type = json.optString("type", "")
        val data = json.optString("data", "")

        when (type) {
            CALLBACK_CONNECTED -> manager.onNativeConnected()
            CALLBACK_DISCONNECTED -> {
                val dataJson = try { JSONObject(data) } catch (e: Exception) { JSONObject() }
                manager.onNativeDisconnected(
                    dataJson.optInt("code", 0),
                    dataJson.optString("reason", "")
                )
            }
            CALLBACK_MESSAGE -> manager.onNativeMessage(data)
            CALLBACK_BINARY_MESSAGE -> manager.onNativeBinaryMessage(data)
            CALLBACK_ERROR -> manager.onNativeError(data)
            CALLBACK_TIMER -> {
                val dataJson = try { JSONObject(data) } catch (e: Exception) { JSONObject() }
                val timerId = dataJson.optString("timerId", "")
                timerCallbacks[timerId]?.invoke()
            }
            CALLBACK_NETWORK -> {
                val dataJson = try { JSONObject(data) } catch (e: Exception) { JSONObject() }
                val isAvailable = dataJson.optBoolean("isAvailable", true)
                val networkType = dataJson.optString("networkType", "unknown")
                networkCallback?.invoke(isAvailable, networkType)
            }
        }
    }

    companion object {
        const val MODULE_NAME = "KRWebSocketModule"

        // 原生回调类型
        private const val CALLBACK_CONNECTED = "connected"
        private const val CALLBACK_DISCONNECTED = "disconnected"
        private const val CALLBACK_MESSAGE = "message"
        private const val CALLBACK_BINARY_MESSAGE = "binaryMessage"
        private const val CALLBACK_ERROR = "error"
        private const val CALLBACK_TIMER = "timer"
        private const val CALLBACK_NETWORK = "network"

        // 调用原生方法名
        private const val METHOD_CONNECT = "connect"
        private const val METHOD_DISCONNECT = "disconnect"
        private const val METHOD_SEND = "send"
        private const val METHOD_SEND_BINARY = "sendBinary"
        private const val METHOD_START_TIMER = "startTimer"
        private const val METHOD_STOP_TIMER = "stopTimer"
        private const val METHOD_REGISTER_NETWORK = "registerNetwork"
        private const val METHOD_UNREGISTER_NETWORK = "unregisterNetwork"
    }
}
