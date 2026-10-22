package com.zyc.love_baby.module

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

/**
 * 语音播放原生实现(M23):MediaPlayer 流媒体起播/停播,**单飞语义**——起播前先释放上一条,
 * 同刻至多一个实例(播放中点另一条语音即切源,与端上 playingVoiceId 页内态双保险)。
 * common base/AudioModule 声明,聊天语音气泡消费;自然播完/被顶掉不回调(端上以 playing 态自管,
 * common 侧页内 state 收口,无跨气泡回调时序负担)。
 * 回调统一主线程收口(KRFileModule 同规:MediaPlayer 回调线程与 KR 桥线程不一,common 侧
 * Compose 状态只认主线程);onDestroy 释放防泄漏(模块随页销毁)。
 */
class KRAudioModule : KuiklyRenderBaseModule() {

    /** 当前播放实例(null=空闲);单飞判等依据(prepared 回来时已被顶掉则静默释放) */
    private var player: MediaPlayer? = null

    /** 在飞回调(单飞:新起播即顶掉旧回调,旧实例回调不再收口) */
    private var startCallback: KuiklyRenderCallback? = null

    /** 回调统一转主线程(KRFileModule 同规) */
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_START_PLAY -> startPlay(params, callback)
            METHOD_STOP_PLAY -> {
                releasePlayer()
                callback?.invoke(mapOf("code" to 0, "msg" to ""))
            }
            else -> callback?.invoke(mapOf("code" to -1, "msg" to "方法不存在"))
        }
    }

    /** 起播(单飞):prepareAsync 异步预载,onPrepared 起播回 code=0;onError 回非 0 带 msg */
    private fun startPlay(params: String?, callback: KuiklyRenderCallback?) {
        val url = if (params.isNullOrEmpty()) "" else JSONObject(params).optString("url")
        if (url.isEmpty()) {
            callback?.invoke(mapOf("code" to -1, "msg" to "参数缺失"))
            return
        }
        releasePlayer() // 单飞:先停上一条(含顶掉其在飞回调)
        startCallback = callback
        val p = MediaPlayer()
        player = p
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            p.setDataSource(url)
            p.setOnPreparedListener { mp ->
                mainHandler.post {
                    if (mp !== player) {
                        // prepared 回来时已被新起播/停播顶掉:静默释放,不收口不抢播
                        try {
                            mp.release()
                        } catch (_: Exception) {
                        }
                        return@post
                    }
                    mp.start()
                    dispatchStart(0, "")
                }
            }
            p.setOnErrorListener { mp, _, _ ->
                mainHandler.post {
                    if (mp === player) {
                        releasePlayer()
                        dispatchStart(-1, "语音播放失败")
                    } else {
                        try {
                            mp.release()
                        } catch (_: Exception) {
                        }
                    }
                }
                true // 已处理,不再走 CompleteListener
            }
            p.prepareAsync()
        } catch (_: Exception) {
            // setDataSource 失败(URL 不可达/格式不支持)同步抛:释放后回错
            releasePlayer()
            dispatchStart(-1, "语音播放失败")
        }
    }

    /** 起播结果一次性收口(主线程投递;releasePlayer 顶掉即静默丢弃) */
    private fun dispatchStart(code: Int, msg: String) {
        val cb = startCallback ?: return
        startCallback = null
        mainHandler.post { cb.invoke(mapOf("code" to code, "msg" to msg)) }
    }

    /** 释放当前播放(未在播 no-op):stop 对未起播实例抛 IllegalStateException,逐个吞掉;
     *  在飞回调一并作废(顶掉语义) */
    private fun releasePlayer() {
        startCallback = null
        player?.let { p ->
            try {
                p.stop()
            } catch (_: Exception) {
            }
            try {
                p.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        /** common 侧 base/AudioModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,照惯例) */
        const val MODULE_NAME = "HRAudioModule"
        private const val METHOD_START_PLAY = "startPlay"
        private const val METHOD_STOP_PLAY = "stopPlay"
    }
}
