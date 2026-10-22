package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AudioModule — 语音播放桥接(M23,跨端接口声明;各端原生壳注册同名模块)
 * 独立于 FileModule 分域:选/传是「动作」一次性回调,播放有跨气泡生命周期(播放中点另一条
 * 要切源、离页要停),状态在原生侧收口。
 * 用法:pager.getModule<AudioModule>(MODULE_NAME) 取用;未桥接端(iOS/ohos/H5)getModule=null,
 * 调用方判空 toast「该端暂不支持」渐进降级(同 FileModule 契约)。
 * 事件:onResult 一次性回调,回参 {"code": 0, "msg": ""}——code=0 已起播;非 0 失败带 msg
 *  (网络不可达/格式不支持等);自然播完/被下一条单飞顶掉不回调(调用方以 playing 态自管,
 *  端上 playingVoiceId 页内 state + 原生单飞双保险)
 * 功能:
 *  - startPlay(url, onResult):起播流媒体 URL,**单飞语义**——已有播放先停后起,同刻至多一条
 *  - stopPlay():停止并释放当前播放(未在播 no-op)
 */
internal class AudioModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 起播(单飞:原生先停上一条再起本条):onResult code=0 已起播 / 非 0 失败带 msg */
    fun startPlay(url: String, onResult: CallbackFn) {
        val methodArgs = JSONObject()
            .put("url", url)
        toNative(false, METHOD_START_PLAY, methodArgs.toString(), onResult)
    }

    /** 停止并释放当前播放(未在播 no-op;无回调,fire-and-forget) */
    fun stopPlay() {
        toNative(false, METHOD_STOP_PLAY, JSONObject().toString(), null)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRAudioModule.MODULE_NAME 同串) */
        const val MODULE_NAME = "HRAudioModule"
        private const val METHOD_START_PLAY = "startPlay"
        private const val METHOD_STOP_PLAY = "stopPlay"
    }
}
