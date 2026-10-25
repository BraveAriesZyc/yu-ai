package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * getCurrentLocation 回包快照(位置页/上报渲染单源,data class 相等可跳重组)
 *  - ok:定位是否成功;false 时仅 err 有值
 *  - lat/lng:WGS-84 原始坐标(纠偏在 common 侧 GeoKit.wgs84ToGcj02 统一做,原生不转换)
 *  - accuracy:精度(米);err:失败原因码(native 透传,如 "timeout"/"no_provider"/"background")
 */
internal data class LocationFix(
    val ok: Boolean,
    val lat: Double,
    val lng: Double,
    val accuracy: Double,
    val err: String,
)

/**
 * LocationModule — 定位桥接(位置共享/轨迹,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRLocationModule,
 *  LocationManager 双 provider,前后台判定在原生侧[KRApplication isAppForeground——
 *  退后台直接回 err="background",防 lastKnown 兜底路径把陈旧点报上去,前台采集产品定案]);
 *  iOS/ohos/H5 未注册 getModule 返回 null,调用方判空静默降级——位置页显「当前设备暂不支持」
 * 用法:LocationStore.reportIfDue / LocationPage 经 pager.getModule<LocationModule>(MODULE_NAME) 取用;
 *  单次定位(不做常驻 requestLocationUpdates 监听——采集节奏由 common 侧轮询驱动,
 *  每次独立单飞,原生侧 Mutex 防并发);坐标纠偏唯一入口 = GeoKit(原生回原始 WGS-84)
 */
internal class LocationModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 单次定位(一次性回调):原生前台闸+双 provider 单飞+超时兜底,见类注释;ok=false 时 err 有值 */
    fun getCurrentLocation(onResult: (LocationFix) -> Unit) {
        val callback: CallbackFn = { result ->
            onResult(
                LocationFix(
                    ok = result?.optBoolean("ok", false) ?: false,
                    lat = result?.optDouble("lat", 0.0) ?: 0.0,
                    lng = result?.optDouble("lng", 0.0) ?: 0.0,
                    accuracy = result?.optDouble("accuracy", 0.0) ?: 0.0,
                    err = result?.optString("err", "") ?: "",
                )
            )
        }
        toNative(false, METHOD_GET_LOCATION, "{}", callback)
    }

    /** 查询定位权限状态(一次性回调):granted=FINE/COARSE 任一授权;fine/coarse=各自授权位
     *  (权限管理页已开启时行尾展示已获取的精度:精确定位/大致位置) */
    fun checkLocationPermission(onResult: (granted: Boolean, fine: Boolean, coarse: Boolean) -> Unit) {
        val callback: CallbackFn = { result ->
            onResult(
                result?.optBoolean("granted", false) ?: false,
                result?.optBoolean("fine", false) ?: false,
                result?.optBoolean("coarse", false) ?: false,
            )
        }
        toNative(false, METHOD_CHECK, "{}", callback)
    }

    /** 发起运行时授权(一次性回调,语义与 NotificationModule.requestPermission 同款):
     *  granted=请求出结果后是否已授权;asked=true 仅表示「请求已真实发出并出了结果」
     *  (弹框被拒与系统静默拒绝不可区分,denied 统一引导「去系统设置」);asked=false=请求未发出
     *  (已授权短路/宿主不可用) */
    fun requestLocationPermission(onResult: (granted: Boolean, asked: Boolean) -> Unit) {
        val callback: CallbackFn = { result ->
            onResult(
                result?.optBoolean("granted", false) ?: false,
                result?.optBoolean("asked", false) ?: false,
            )
        }
        toNative(false, METHOD_REQUEST, "{}", callback)
    }

    /** 跳系统应用详情页(fire-and-forget):定位无渠道级设置页,统一应用详情(用户手动开权限) */
    fun openSettings() {
        toNative(false, METHOD_OPEN_SETTINGS, "{}", null)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRLocationModule.MODULE_NAME 同串,KR/HR 前缀惯例) */
        const val MODULE_NAME = "HRLocationModule"
        private const val METHOD_GET_LOCATION = "getCurrentLocation"
        private const val METHOD_CHECK = "check"
        private const val METHOD_REQUEST = "request"
        private const val METHOD_OPEN_SETTINGS = "openSettings"
    }
}
