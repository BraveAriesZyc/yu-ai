package com.tencent.kuiklybase.websocket

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Android 端 WebSocket 原生 Module
 *
 * 基于 OkHttp 实现，继承 KuiklyRenderBaseModule。
 * KMP 层通过 toNative 调用 call()，原生端通过 keepCallbackAlive 的 callback 回传事件。
 *
 * 注册方式：
 * ```kotlin
 * moduleExport(KRWebSocketModule.MODULE_NAME) { KRWebSocketModule() }
 * ```
 */
class KRWebSocketModule : KuiklyRenderBaseModule() {

    companion object {
        const val MODULE_NAME = "KRWebSocketModule"
        private const val TAG = "KRWebSocketModule"
    }

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timers = ConcurrentHashMap<String, Runnable>()

    /** connect 时传入的常驻回调，用于回传所有 WebSocket 事件 */
    private var wsEventCallback: KuiklyRenderCallback? = null

    /** 各定时器对应的常驻回调 */
    private val timerCallbacks = ConcurrentHashMap<String, KuiklyRenderCallback>()

    /** 网络监听的常驻回调 */
    private var networkEventCallback: KuiklyRenderCallback? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        val json = if (!params.isNullOrEmpty()) JSONObject(params) else JSONObject()
        return when (method) {
            "connect" -> {
                wsEventCallback = callback
                val url = json.optString("url", "")
                val timeoutMs = json.optLong("timeoutMs", 10000L)
                val headersJson = json.optJSONObject("headers")
                val headers = mutableMapOf<String, String>()
                headersJson?.keys()?.forEach { key ->
                    headers[key] = headersJson.optString(key, "")
                }
                connect(url, headers, timeoutMs)
            }
            "disconnect" -> {
                disconnect(json.optInt("code", 1000), json.optString("reason", ""))
            }
            "send" -> {
                send(json.optString("data", ""))
            }
            "sendBinary" -> {
                sendBinary(json.optString("data", ""))
            }
            "startTimer" -> {
                val timerId = json.optString("timerId", "")
                timerCallbacks[timerId] = callback ?: return null
                startTimer(timerId, json.optLong("intervalMs", 0), json.optBoolean("repeating", false))
            }
            "stopTimer" -> {
                val timerId = json.optString("timerId", "")
                timerCallbacks.remove(timerId)
                stopTimer(timerId)
            }
            "registerNetwork" -> {
                networkEventCallback = callback
                registerNetworkListener()
            }
            "unregisterNetwork" -> {
                unregisterNetworkListener()
                networkEventCallback = null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
            }
        }
    }

    /**
     * 通过常驻 callback 回传事件到 KMP 层
     * 传 JSON 字符串而非 Map，确保 KMP 侧 JSONObject 能正确解析
     */
    private fun notifyEvent(callback: KuiklyRenderCallback?, type: String, data: String = "") {
        val json = JSONObject()
        json.put("type", type)
        json.put("data", data)
        callback?.invoke(json.toString())
    }

    // ===== WebSocket =====

    private fun connect(url: String, headers: Map<String, String>, timeoutMs: Long) {
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        client = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .build()

        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        webSocket = client?.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected: ${response.code}")
                mainHandler.post { notifyEvent(wsEventCallback, "connected") }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                mainHandler.post { notifyEvent(wsEventCallback, "message", text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                @OptIn(ExperimentalEncodingApi::class)
                val base64 = Base64.encode(bytes.toByteArray())
                mainHandler.post { notifyEvent(wsEventCallback, "binaryMessage", base64) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                mainHandler.post {
                    val json = JSONObject().apply {
                        put("code", code)
                        put("reason", reason)
                    }
                    notifyEvent(wsEventCallback, "disconnected", json.toString())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: ${t.message}", t)
                mainHandler.post {
                    notifyEvent(wsEventCallback, "error", t.message ?: "Unknown error")
                }
            }
        })
    }

    private fun disconnect(code: Int, reason: String) {
        webSocket?.close(code, reason)
        webSocket = null
    }

    private fun send(data: String): Boolean {
        return webSocket?.send(data) ?: false
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun sendBinary(base64Data: String) {
        val bytes = Base64.decode(base64Data)
        webSocket?.send(okio.ByteString.of(*bytes))
    }

    // ===== 定时器 =====

    private fun startTimer(timerId: String, intervalMs: Long, repeating: Boolean) {
        stopTimer(timerId)

        if (repeating) {
            val runnable = object : Runnable {
                override fun run() {
                    val timerData = JSONObject().put("timerId", timerId).toString()
                    notifyEvent(timerCallbacks[timerId], "timer", timerData)
                    if (timers.containsKey(timerId)) {
                        mainHandler.postDelayed(this, intervalMs)
                    }
                }
            }
            timers[timerId] = runnable
            mainHandler.postDelayed(runnable, intervalMs)
        } else {
            val runnable = Runnable {
                timers.remove(timerId)
                val timerData = JSONObject().put("timerId", timerId).toString()
                notifyEvent(timerCallbacks.remove(timerId), "timer", timerData)
            }
            timers[timerId] = runnable
            mainHandler.postDelayed(runnable, intervalMs)
        }
    }

    private fun stopTimer(timerId: String) {
        timers.remove(timerId)?.let { mainHandler.removeCallbacks(it) }
    }

    // ===== 网络监听 =====

    private fun registerNetworkListener() {
        unregisterNetworkListener()
        try {
            val ctx = context ?: return
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            connectivityManager = cm

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val type = getNetworkType(cm)
                    mainHandler.post { notifyNetworkEvent(true, type) }
                }

                override fun onLost(network: Network) {
                    mainHandler.post { notifyNetworkEvent(false, "none") }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val type = when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                        else -> "unknown"
                    }
                    mainHandler.post { notifyNetworkEvent(true, type) }
                }
            }
            networkCallback = cb
            val request = NetworkRequest.Builder().build()
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.e(TAG, "registerNetworkListener failed", e)
        }
    }

    private fun unregisterNetworkListener() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        networkCallback = null
        connectivityManager = null
    }

    private fun getNetworkType(cm: ConnectivityManager): String {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return "unknown"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "unknown"
        }
    }

    private fun notifyNetworkEvent(isAvailable: Boolean, networkType: String) {
        val data = JSONObject().apply {
            put("isAvailable", isAvailable)
            put("networkType", networkType)
        }.toString()
        notifyEvent(networkEventCallback, "network", data)
    }

    override fun onDestroy() {
        super.onDestroy()
        timers.keys.toList().forEach { stopTimer(it) }
        timerCallbacks.clear()
        unregisterNetworkListener()
        webSocket?.close(1000, "Module destroyed")
        webSocket = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        wsEventCallback = null
        networkEventCallback = null
    }
}
