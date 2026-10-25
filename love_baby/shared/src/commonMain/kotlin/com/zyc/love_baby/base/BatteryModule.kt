package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * getBatteryStatus 回包快照(电量页渲染单源,data class 相等可跳重组)
 * 字段语义(Android BatteryManager sticky broadcast 查询面):
 *  - level:电量百分比 0-100;-1=未知(读取失败/极端机型)
 *  - charging:0=未充电 1=充电中 2=已充满(EXTRA_STATUS 映射;USB/AC/无线充电统一归「充电中」)
 */
internal data class BatteryNativeState(
    val level: Int,
    val charging: Int,
)

/**
 * BatteryModule — 手机电量桥接(情侣电量互看,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRBatteryModule,
 *  BatteryManager ACTION_BATTERY_CHANGED sticky broadcast 读值,无任何权限);
 *  iOS/ohos/H5 未注册 getModule 返回 null,调用方判空静默降级——电量页显「当前设备暂不支持」
 *  (NotificationModule 渐进降级同款)
 * 用法:BatteryStore.refresh 经 pager.getModule<BatteryModule>(MODULE_NAME) 取用;
 *  电量无事件推送(不做 broadcast 常驻监听),上报节奏=页面轮询+回前台触发,common 侧无状态
 */
internal class BatteryModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 查询电量状态(一次性回调,回 BatteryNativeState 快照;原生侧 mainHandler 异步投递) */
    fun getBatteryStatus(onResult: (BatteryNativeState) -> Unit) {
        val callback: CallbackFn = { result ->
            onResult(
                BatteryNativeState(
                    level = result?.optInt("level", -1) ?: -1,
                    charging = result?.optInt("charging", 0) ?: 0,
                )
            )
        }
        toNative(false, METHOD_GET_STATUS, "{}", callback)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRBatteryModule.MODULE_NAME 同串,KR/HR 前缀惯例) */
        const val MODULE_NAME = "HRBatteryModule"
        private const val METHOD_GET_STATUS = "getBatteryStatus"
    }
}
