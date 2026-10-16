package com.zyc.love_baby.module

import android.os.Handler
import android.os.Looper
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 长连接触发器原生实现(D-16):okhttp WebSocket 连 yudao /infra/ws?token=,
 * 事件经 connect 的常驻回调多次回放给 common 侧(KRNotifyModule.addNotify 同款多播机制,
 * common 侧 toNative(keepCallbackAlive=true) 声明见 base/WebSocketModule.kt)。
 * 仅信号通道——消息数据仍由 common 侧 HTTP lastId 增量拉,断线由轮询兜底。
 */
class KRWebSocketModule : KuiklyRenderBaseModule() {

    /** okhttp 客户端单例:pingInterval 发 RFC 控制帧保活(Tomcat 自动回 pong;yudao 文本 "ping" 留给 JS 端) */
    private var client: OkHttpClient? = null

    /** 当前连接:null = 未连/已断,connect 判重依据(common 侧 state 之外的双保险) */
    private var socket: WebSocket? = null

    /** common 侧常驻事件回调:disconnect 置空防泄漏;连接存续期多次回放 */
    private var eventCallback: KuiklyRenderCallback? = null

    /** 事件回放统一转主线程(KRNotifyModule 走 BroadcastReceiver.onReceive 主线程的同构):
     *  common 侧回调里发起 lifecycleScope.launch,主线程起步与既有 LaunchedEffect 线程语义一致 */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_CONNECT -> connect(params, callback)
            METHOD_DISCONNECT -> disconnect()
            else -> callback?.invoke(mapOf("code" to -1, "message" to "方法不存在"))
        }
    }

    private fun connect(params: String?, callback: KuiklyRenderCallback?) {
        if (params == null) {
            return
        }
        val paramJSON = JSONObject(params)
        val url = paramJSON.optString("url")
        val token = paramJSON.optString("token")
        if (url.isEmpty() || token.isEmpty()) {
            return
        }
        // 幂等:连接在手直接忽略(common 侧 ChatPushStore state 已判,双保险)
        if (socket != null) {
            return
        }
        eventCallback = callback
        val httpClient = client ?: OkHttpClient.Builder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .build()
            .also { client = it }
        socket = httpClient.newWebSocket(
            // token 走握手 query:yudao TokenAuthenticationFilter Header>Parameter 回退后校验,
            // LoginUserHandshakeInterceptor 无登录态即拒(实测坏/空 token 握手非 101)
            Request.Builder().url("$url?$TOKEN_QUERY=$token").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    fire(EVENT_OPEN, "")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    fire(EVENT_MESSAGE, text)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    fire(EVENT_CLOSED, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    fire(EVENT_ERROR, t.message ?: "")
                }
            })
    }

    private fun disconnect() {
        socket?.close(NORMAL_CLOSE_CODE, null)
        socket = null
        eventCallback = null
    }

    /** 事件回放统一转主线程后回放(okhttp 回调线程不直接进 common,线程语义见 mainHandler 注释) */
    private fun fire(event: String, data: String) {
        mainHandler.post {
            eventCallback?.invoke(mapOf("event" to event, "data" to data))
        }
    }

    override fun onDestroy() {
        // Activity 销毁兜底断连(common 侧登出/解绑已主动 disconnect;此处防 pager 泄漏连接)
        disconnect()
        mainHandler.removeCallbacksAndMessages(null) // 清未决事件回放,防泄漏引用
        client?.dispatcher?.executorService?.shutdown()
        client = null
        super.onDestroy()
    }

    companion object {
        /** common 侧 base/WebSocketModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,照 KRShareModule 惯例) */
        const val MODULE_NAME = "HRWebSocketModule"
        private const val METHOD_CONNECT = "connect"
        private const val METHOD_DISCONNECT = "disconnect"
        private const val TOKEN_QUERY = "token"
        private const val EVENT_OPEN = "open"
        private const val EVENT_MESSAGE = "message"
        private const val EVENT_CLOSED = "closed"
        private const val EVENT_ERROR = "error"
        private const val NORMAL_CLOSE_CODE = 1000
        private const val PING_INTERVAL_SECONDS = 30L
    }
}
