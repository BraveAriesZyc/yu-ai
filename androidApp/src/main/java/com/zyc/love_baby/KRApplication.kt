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
    }

    /**
     * 通知渠道 App 启动即建(skeptic major 修复):targetSdk≤32 应用的 Android 13+ 通知权限弹窗
     * 由「activity 启动 + 已存在通知渠道」触发——渠道建晚了(如闸在 notify 的权限检查之后)
     * 弹窗永不出现,新装设备通知永久失效。IMPORTANCE_HIGH=声音/震动/状态栏图标+**heads-up
     * 悬浮横幅**(DEFAULT 无横幅,2026-10-18 真机反馈「顶部有提示但没有横幅」后升级;渠道
     * importance 一经创建不可改,改配置必须连 channel id 一起换——旧 id "chat" 在已安装设备
     * 上永远留着 DEFAULT 语义)
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
