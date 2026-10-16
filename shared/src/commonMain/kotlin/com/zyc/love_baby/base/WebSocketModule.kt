package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * WebSocketModule — 长连接触发器桥接(D-16,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRWebSocketModule,okhttp 实现;
 *  iOS/ohos/H5 未注册前 getModule 返回 null,调用方判空降级纯轮询——渐进降级设计,§5.2 WS 通道)
 * 用法:ChatPushStore 经 pager.getModule<WebSocketModule>(MODULE_NAME) 取用;token 取 SessionStore.accessToken
 * 入参:connect(url, token, onEvent)——url 为完整 ws 地址(ApiConfig.wsUrl),token 由原生拼进握手
 *  query 参数(?token=xxx:TokenAuthenticationFilter Header>Parameter 优先级,
 *  LoginUserHandshakeInterceptor 强制要求登录态,无/坏 token 握手被拒)
 * 事件:onEvent 常驻多次回放(keepCallbackAlive=true,NotifyModule.addNotify 同款多播机制),
 *  回参 {"event": "open|message|closed|error", "data": "文本帧原文"};
 *  message 的 data = 服务端 JsonWebSocketMessage 原文 {"type","content"}(content 为字符串化 JSON)
 * 功能:connect 建连/断线后重连由 common 侧幂等触发;disconnect 主动断开(登出/解绑清场)。
 *  仅信号通道——消息数据仍走 HTTP lastId 增量拉(D-15 游标语义),信号丢了由轮询兜底不丢消息
 */
internal class WebSocketModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 连接(常驻回调):事件回参见头注释「事件」段;重复 connect 由原生侧幂等(socket 活着忽略) */
    fun connect(url: String, token: String, onEvent: CallbackFn) {
        val methodArgs = JSONObject()
        methodArgs.put("url", url)
        methodArgs.put("token", token)
        // keepCallbackAlive=true:回调常驻,原生侧事件驱动多次回放(KRNotifyModule.addNotify 同款机制)
        toNative(true, METHOD_CONNECT, methodArgs.toString(), onEvent)
    }

    /** 主动断开(登出/解绑清场;不调则连接活到进程死/服务端断/原生 onDestroy 兜底) */
    fun disconnect() {
        toNative(false, METHOD_DISCONNECT, null, null)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRWebSocketModule.MODULE_NAME 同串;KR 前缀原生类名、HR 前缀注册名,照 KRShareModule 惯例) */
        const val MODULE_NAME = "HRWebSocketModule"
        private const val METHOD_CONNECT = "connect"
        private const val METHOD_DISCONNECT = "disconnect"
    }
}
