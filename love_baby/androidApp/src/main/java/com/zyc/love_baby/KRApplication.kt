package com.zyc.love_baby

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import com.zyc.love_baby.module.KRNotificationModule

class KRApplication : Application() {

    /** 前后台计数(通知 no-op 判定):started Activity 数 > 0 = 前台;onStart/onStop 成对推进 */
    private val foregroundCounter = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            foregroundActivities++
        }

        override fun onActivityStopped(activity: Activity) {
            foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(foregroundCounter)
        ensureNotifyChannel()
        ensureAmapPrivacyCompliance()
    }

    /**
     * 高德 SDK 隐私合规(2021 铁律:任何高德 SDK 接口调用前必须先隐私确认;本 App 自身首启
     * Gate 登录即用户明确使用意愿,未同意协议无法进主功能,视作已同意,合规接口照调):
     * updatePrivacyShow/updatePrivacyAgree 必须在 MapView 创建前调一次,Application.onCreate
     * 是全 App 最早时点;10.0.600 未调会拒绝初始化地图(瓦片空白/回调缺),不会崩。
     * 仅 3dmap 一件(定位走系统 LocationManager,未引高德定位 SDK 无需其合规对)
     */
    private fun ensureAmapPrivacyCompliance() {
        try {
            com.amap.api.maps.MapsInitializer.updatePrivacyShow(this, true, true)
            com.amap.api.maps.MapsInitializer.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {
            // 极端场景(SDK 未打包/初始化异常)静默:地图降级,不拖垮 App 启动
        }
    }

    /**
     * 通知渠道 App 启动即建(skeptic major 修复):targetSdk≤32 时代 Android 13+ 的系统自动
     * 弹窗依赖「首渠道已建 + activity 启动」;升 targetSdk 33(M22)后改由应用主动申请
     * (KuiklyRenderActivity 主页 onCreate),渠道尽早就绪仍成立——权限弹窗的「管理」入口
     * 与通知发送都依赖渠道存在。IMPORTANCE_HIGH=声音/震动/状态栏图标+**heads-up 悬浮横幅**
     * (DEFAULT 无横幅,2026-10-18 真机反馈「顶部有提示但没有横幅」后升级;渠道 importance
     * 一经创建不可改,改配置必须连 channel id 一起换——旧 id "chat" 在已安装设备上永远留着
     * DEFAULT 语义)
     */
    private fun ensureNotifyChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = NotificationManagerCompat.from(this)
        if (manager.getNotificationChannel(KRNotificationModule.CHANNEL_ID_CHAT) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                KRNotificationModule.CHANNEL_ID_CHAT,
                "悄悄话",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
    }

    init {
        application = this
    }

    companion object {
        lateinit var application: Application

        /** App 是否前台(KRNotificationModule 通知 no-op 依据):纯派生值,started 计数>0 */
        val isAppForeground: Boolean
            get() = foregroundActivities > 0

        private var foregroundActivities: Int = 0
    }
}
