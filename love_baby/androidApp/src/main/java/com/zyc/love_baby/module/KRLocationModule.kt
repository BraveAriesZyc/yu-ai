package com.zyc.love_baby.module

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.zyc.love_baby.KRApplication
import org.json.JSONObject

/**
 * 定位原生实现(M26 位置共享/行动轨迹):common 侧 base/LocationModule 声明(单次定位 +
 * 权限三件 + 跳设置),LocationStore 节流上报/位置页驱动。定位为「单次拉取」形态——
 * 采集节奏(5min ticker + 页面轮询)由 common 侧驱动,原生每次独立定位,不做常驻
 * requestLocationUpdates 监听(无前台服务、无后台采集,产品定案)。
 *
 * ⚠ 前台闸(双闸之一,原生侧):getCurrentLocation 入口先判 KRApplication.isAppForeground,
 * 退后台直接回 err="background" 不定位——防轮询协程被 WebView/Doze 推迟后与回前台窗口
 * 竞速、以及任何 lastKnown 类兜底路径把陈旧点报上去;另一闸在 common(协程 keyed
 * foregroundTick,退后台 effect 挂起重启即复位),双闸独立不互信。
 *
 * ⚠ 定位策略:双 provider(GPS + Network)并挂 requestLocationUpdates(勿用已废弃
 * requestSingleUpdate),首 fix 即回 + 10s 超时回错;**不用 getLastKnownLocation 兜底**
 * (缓存点年龄不可控,室内可能数小时前——产品语义是「TA 现在在哪」,陈旧点比没有更糟,
 * 超时回错由调用方显式降级)。两 provider 均未开启(系统定位总关)立即回 err="no_provider"。
 *
 * ⚠ 单飞搭车:定位在飞期间新请求只入列共享同一 fix(60s 轮询与手动刷新并发不重复定位、
 * 不堆超时器);结果统一 deliver 收口——停监听/撤超时/复位,回调全部 mainHandler.post
 * 异步投递(回调铁律照 KRNotificationModule 定稿,call() 内同步 invoke 真机不送达;
 * GPS 回调虽是独立消息仍走 post,与全仓模块同规)。坐标回 WGS-84 原始值——纠偏唯一
 * 入口在 common base/GeoKit.wgs84ToGcj02,原生不转换(高德系存取口径 GCJ-02)。
 *
 * 权限:check/request 照 KRNotificationModule 静态槽范式(requestCode 2004,与通知 2001/
 * 录音 2002/读媒体 2003 错开),FINE+COARSE 双权限一次申请 any-granted 放行(COARSE
 * 亦可定位,精度差但功能可用);拒绝两次后系统静默拒绝仍回调 DENIED(asked=true,
 * 与真实弹框不可区分),调用方 denied 一律引导「去系统设置」。
 */
class KRLocationModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 单飞搭车队列:在飞期间新请求的回调入列,与首发共享同一 fix(空=无人在等) */
    private val inFlightCallbacks = ArrayList<KuiklyRenderCallback>()

    /** 定位在飞标志(与队列同生命周期:非空必 requesting,requesting 可空队列——首发即挂超时) */
    private var requesting = false

    private val locationManager by lazy {
        KRApplication.application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    /** 超时收口:双 provider 并挂下 GPS 室内可能长期无 fix,靠超时回错降级,不悬挂调用方 */
    private val timeoutRunnable = Runnable {
        deliver(errorPayload(ERR_TIMEOUT))
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            deliver(
                mapOf(
                    "ok" to true,
                    "lat" to location.latitude,
                    "lng" to location.longitude,
                    "accuracy" to location.accuracy.toDouble(),
                    "err" to "",
                )
            )
        }

        // minSdk 23 < 30:三个旧回调在老框架上是抽象方法,必须显式 no-op 防 AbstractMethodError
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }

        override fun onProviderEnabled(provider: String) {
        }

        override fun onProviderDisabled(provider: String) {
        }
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_GET_LOCATION -> getCurrentLocation(callback)
            METHOD_CHECK -> checkPermission(callback)
            METHOD_REQUEST -> requestPermission(callback)
            METHOD_OPEN_SETTINGS -> openSettings()
            else -> dispatchCallback(callback, UNKNOWN_METHOD_PAYLOAD)
        }
    }

    /** 单次定位:前台闸 → 权限闸 → provider 闸 → 双 provider 并挂首 fix 即回(10s 超时兜底) */
    private fun getCurrentLocation(callback: KuiklyRenderCallback?) {
        if (!KRApplication.isAppForeground) {
            dispatchCallback(callback, errorPayload(ERR_BACKGROUND))
            return
        }
        if (!permissionGranted()) {
            dispatchCallback(callback, errorPayload(ERR_NO_PERMISSION))
            return
        }
        val providers = enabledProviders()
        if (providers.isEmpty()) {
            dispatchCallback(callback, errorPayload(ERR_NO_PROVIDER))
            return
        }
        // 单飞搭车:在飞期间新请求只入列(不重复挂监听/超时),与首发共享同一 fix
        if (callback != null) {
            inFlightCallbacks.add(callback)
        }
        if (requesting) {
            return
        }
        requesting = true
        try {
            for (provider in providers) {
                locationManager.requestLocationUpdates(
                    provider, 0L, 0f, locationListener, mainHandler.looper,
                )
            }
            mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        } catch (_: Exception) {
            // 权限竞速回收(检查后被系统撤)/provider 异常:必发兜底回包,不悬挂调用方
            deliver(errorPayload(ERR_NO_PROVIDER))
        }
    }

    /** 结果统一收口:停监听/撤超时/复位单飞,全部回调 mainHandler.post 异步投递(类头铁律) */
    private fun deliver(payload: Map<String, Any>) {
        if (requesting) {
            try {
                locationManager.removeUpdates(locationListener)
            } catch (_: Exception) {
            }
            mainHandler.removeCallbacks(timeoutRunnable)
            requesting = false
        }
        val callbacks = ArrayList(inFlightCallbacks)
        inFlightCallbacks.clear()
        for (cb in callbacks) {
            mainHandler.post { cb.invoke(payload) }
        }
    }

    /** 可用 provider 采集(GPS + Network;系统定位总关时两个都 false → 上层回 no_provider) */
    private fun enabledProviders(): List<String> {
        val result = ArrayList<String>(2)
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                result.add(LocationManager.GPS_PROVIDER)
            }
        } catch (_: Exception) {
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                result.add(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {
        }
        return result
    }

    /** 权限状态查询:granted=FINE/COARSE 任一授权(与 common 侧契约一致);fine/coarse=各自
     *  授权位(common 侧权限管理页已开启时行尾展示已获取的精度:精确定位/大致位置) */
    private fun checkPermission(callback: KuiklyRenderCallback?) {
        dispatchCallback(callback, permissionSnapshot(KRApplication.application))
    }

    /** 运行时授权(FINE+COARSE 双权限一次申请):结构照 KRNotificationModule.requestPermission——
     *  已授权短路 asked=false;宿主不可用 asked=false;发结果经静态槽一次性收口 */
    private var myPendingCallback: KuiklyRenderCallback? = null

    private fun requestPermission(callback: KuiklyRenderCallback?) {
        if (permissionGranted()) {
            dispatchCallback(callback, mapOf("granted" to true, "asked" to false))
            return
        }
        val host = activity
        if (host == null || host.isFinishing) {
            dispatchCallback(callback, mapOf("granted" to false, "asked" to false))
            return
        }
        pendingPermissionCallback = callback
        myPendingCallback = callback
        try {
            host.requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
                REQUEST_CODE_PERMISSION,
            )
        } catch (_: Exception) {
            pendingPermissionCallback = null
            dispatchCallback(callback, mapOf("granted" to false, "asked" to false))
        }
    }

    /** 跳系统应用详情页(定位无渠道级设置页,统一应用详情让用户手动开权限)。
     *  不 finish 本页,从设置返回即回 */
    private fun openSettings() {
        val context = KRApplication.application
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    /** 回调统一收口:mainHandler 异步投递(类头铁律),null 回调零成本直落 */
    private fun dispatchCallback(callback: KuiklyRenderCallback?, payload: Map<String, Any>) {
        if (callback == null) {
            return
        }
        mainHandler.post { callback.invoke(payload) }
    }

    /** 页面销毁:在飞定位直接停收(不投递——common 侧已销毁,回调作废,KRNotificationModule
     *  清静态槽同款静默语义);权限槽做归属校验清理,不误伤其他实例在飞的申请 */
    override fun onDestroy() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }
        mainHandler.removeCallbacks(timeoutRunnable)
        requesting = false
        inFlightCallbacks.clear()
        if (myPendingCallback != null && pendingPermissionCallback === myPendingCallback) {
            pendingPermissionCallback = null
        }
        myPendingCallback = null
        super.onDestroy()
    }

    companion object {
        /** common 侧 base/LocationModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,惯例) */
        const val MODULE_NAME = "HRLocationModule"

        private const val METHOD_GET_LOCATION = "getCurrentLocation"
        private const val METHOD_CHECK = "check"
        private const val METHOD_REQUEST = "request"
        private const val METHOD_OPEN_SETTINGS = "openSettings"

        /** 单次定位超时:双 provider 并挂,GPS 室内无 fix 由 NETWORK 兜底,仍无则 10s 收口 */
        private const val TIMEOUT_MS = 10_000L

        /** 运行时权限申请 requestCode(静态槽第四个,与通知 2001/录音 2002/读媒体 2003 错开) */
        const val REQUEST_CODE_PERMISSION = 2004

        /** err 失败原因码(common LocationFix.err 透传,调用方按语义分流:background 静默、
         *  no_provider/no_permission 引导设置、timeout 提示稍后再试) */
        const val ERR_BACKGROUND = "background"
        const val ERR_NO_PERMISSION = "no_permission"
        const val ERR_NO_PROVIDER = "no_provider"
        const val ERR_TIMEOUT = "timeout"

        /** 页面主动申请的一次性回传(仅 METHOD_REQUEST 设;onDestroy 归属校验清槽)——
         *  KuiklyRenderBaseModule 无 onRequestPermissionsResult 钩子,由 KuiklyRenderActivity
         *  override 后转发本静态入口(与通知/录音/读媒体三槽同款桥模式) */
        @Volatile
        private var pendingPermissionCallback: KuiklyRenderCallback? = null

        /** 权限结果转发口(onRequestPermissionsResult 在主线程调用,post 一拍出当前主队列
         *  消息,与实例侧 dispatchCallback 同规——类头铁律) */
        private val mainHandler = Handler(Looper.getMainLooper())

        /** KuiklyRenderActivity.onRequestPermissionsResult 转发入口:无 pending 静默丢弃,
         *  有 pending 一次性收口防重放;FINE+COARSE any-granted 放行 */
        fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_PERMISSION) return
            val callback = pendingPermissionCallback ?: return
            pendingPermissionCallback = null
            var granted = false
            for (result in grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true
                }
            }
            mainHandler.post { callback.invoke(mapOf("granted" to granted, "asked" to true)) }
        }

        /** 定位失败统一回包形状(ok=false,lat/lng/accuracy 归零,仅 err 有义) */
        private fun errorPayload(err: String): Map<String, Any> = mapOf(
            "ok" to false,
            "lat" to 0.0,
            "lng" to 0.0,
            "accuracy" to 0.0,
            "err" to err,
        )

        /** 未知方法回包(与 KRNotificationModule else 分支语义一致,统一收口异步投递) */
        private val UNKNOWN_METHOD_PAYLOAD: Map<String, Any> =
            mapOf("code" to -1, "message" to "方法不存在")

        /** 定位权限三值快照(实例 check 与宿主直注 PermissionSnapshot 共用单源;application
         *  context 查询无实例状态) */
        fun permissionSnapshot(context: android.content.Context): Map<String, Any> {
            fun granted(p: String) =
                ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
            val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            return mapOf("granted" to (fine || coarse), "fine" to fine, "coarse" to coarse)
        }
    }
}

/** FINE/COARSE 权限检查(内联扩展保持调用处扁平):fineGranted/coarseGranted 各自授权位
 *  (check 回包透传,权限页行尾展示精度),permissionGranted=任一授权即可用 */
private fun KRLocationModule.fineGranted(): Boolean =
    isLocPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)

private fun KRLocationModule.coarseGranted(): Boolean =
    isLocPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

private fun KRLocationModule.permissionGranted(): Boolean = fineGranted() || coarseGranted()

private fun KRLocationModule.isLocPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(KRApplication.application, permission) ==
        PackageManager.PERMISSION_GRANTED
