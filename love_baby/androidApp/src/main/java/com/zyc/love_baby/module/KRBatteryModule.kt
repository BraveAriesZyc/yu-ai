package com.zyc.love_baby.module

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.zyc.love_baby.KRApplication
import org.json.JSONObject

/**
 * 手机电量原生实现(M26 情侣电量互看):common 侧 base/BatteryModule 声明(getBatteryStatus
 * 单方法),BatteryStore 轮询 + 回前台触发驱动上报,common 侧无状态、原生侧也无状态——
 * 每次查询同步窥视 sticky broadcast 即回,不常驻监听(电量变化由「60s 轮询 + 回前台」
 * 节奏自然追上,真机无感知,省一条常驻 receiver 生命周期)。
 * 读值:ACTION_BATTERY_CHANGED 是系统常驻 sticky 广播,registerReceiver(null, filter)
 * 窥视当前值零成本、免权限、免 receiver 注册(targetSdk 33 的 RECEIVER_EXPORTED 语义
 * 只约束真注册;null receiver 窥视是官方文档认可的 sticky 读法)。
 *
 * ⚠ 回调投递铁律(照 KRNotificationModule 定稿):回 common 的 callback 一律经 mainHandler
 * post 异步投递(dispatchCallback 统一收口),严禁 call() 内同步 invoke——真机实证不送达。
 * 载荷用 Map:边界层 toKotlinObject 会把 Map 转 JSON 字符串回 common(Android 端已代转,
 * 勿手工序列化);查询全程 try/catch,任何异常必发兜底回包(level=-1=「未知」可展示形态,
 * 空回包=页面卡「查询中」)。
 */
class KRBatteryModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_GET_STATUS -> getBatteryStatus(callback)
            else -> dispatchCallback(callback, UNKNOWN_METHOD_PAYLOAD)
        }
    }

    /** 查询电量状态(一次性回调):level 0-100/-1 未知,charging 0=未充电 1=充电中 2=已充满 */
    private fun getBatteryStatus(callback: KuiklyRenderCallback?) {
        val payload = try {
            queryBattery()
        } catch (_: Exception) {
            FALLBACK_PAYLOAD
        }
        dispatchCallback(callback, payload)
    }

    /** 窥视 sticky broadcast 采集电量快照(自身不 catch,由调用方兜底收口) */
    private fun queryBattery(): Map<String, Any> {
        val intent = KRApplication.application.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return FALLBACK_PAYLOAD
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        // charging 三分(common BatteryNativeState 同构):USB/AC/无线充电统一归「充电中」;
        // FULL(插电已满)单列——TA「已充满可拔线」与「充电中」语义不同
        val charging = when (status) {
            BatteryManager.BATTERY_STATUS_FULL -> 2
            BatteryManager.BATTERY_STATUS_CHARGING -> 1
            else -> 0
        }
        // level/scale 缺省(极个别 ROM 首帧 sticky 未就绪)回 -1=「未知」,不猜 100/0
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        return mapOf("level" to percent, "charging" to charging)
    }

    /** 回调统一收口:mainHandler 异步投递(类头铁律),null 回调零成本直落 */
    private fun dispatchCallback(callback: KuiklyRenderCallback?, payload: Map<String, Any>) {
        if (callback == null) {
            return
        }
        mainHandler.post { callback.invoke(payload) }
    }

    companion object {
        /** common 侧 base/BatteryModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,惯例) */
        const val MODULE_NAME = "HRBatteryModule"
        private const val METHOD_GET_STATUS = "getBatteryStatus"

        /** 采集异常兜底回包:level=-1=「未知」可展示形态(charging 0=未充电,页面按未知态渲染) */
        private val FALLBACK_PAYLOAD: Map<String, Any> = mapOf("level" to -1, "charging" to 0)

        /** 未知方法回包(与 KRNotificationModule else 分支语义一致,统一收口异步投递) */
        private val UNKNOWN_METHOD_PAYLOAD: Map<String, Any> =
            mapOf("code" to -1, "message" to "方法不存在")
    }
}
