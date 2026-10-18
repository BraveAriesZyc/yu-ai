package com.zyc.love_baby.module

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import com.zyc.love_baby.KRApplication
import com.zyc.love_baby.KuiklyRenderActivity
import com.zyc.love_baby.R
import org.json.JSONObject

/**
 * 后台消息通知原生实现(M21):common 侧 base/NotificationModule 声明(pull 落态钩子驱动,
 * fire-and-forget 无回调)。前台一律 no-op(KRApplication started 计数判定,
 * 气泡+红点已可见不弹通知打扰);App 后台才发系统通知。通知盲区=进程被杀(无厂商推送,
 * 留待上线后评估)与 WS 后台静默断开(空闲超时/Doze/切网)——后者由 M19 全局心跳每轮
 * 顺手重连 + 心跳轮询拉取双兜底,通知随 pull 落态仍可达,仅延迟。
 * 权限:targetSdk 33(M22)起 POST_NOTIFICATIONS 为运行时权限,新装默认关——打开时主动请求
 * (KuiklyRenderActivity 主页 onCreate)与权限管理页(M22 PermissionPage)双通道申请;此处
 * checkSelfPermission 仅作发送前防御,未授权场景静默降级不崩,红点兜底
 */
class KRNotificationModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_NOTIFY -> notify(params)
            METHOD_CHECK -> checkPermission(callback)
            METHOD_REQUEST -> requestPermission(callback)
            METHOD_OPEN_SETTINGS -> openSettings()
            else -> callback?.invoke(mapOf("code" to -1, "message" to "方法不存在"))
        }
    }

    private fun notify(params: String?) {
        if (params == null) {
            return
        }
        // App 前台不通知(气泡+红点已可见):标题/正文解析前判定,无谓桥调用零成本拦截
        if (KRApplication.isAppForeground) {
            return
        }
        val paramJSON = JSONObject(params)
        val title = paramJSON.optString("title")
        val body = paramJSON.optString("body")
        if (title.isEmpty() && body.isEmpty()) {
            return
        }
        val manager = NotificationManagerCompat.from(KRApplication.application)
        // API 33+ 运行时权限防御(targetSdk 33 起新装默认关,未授权时静默降级红点兜底)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionGranted()) {
            return
        }
        val notification = NotificationCompat.Builder(KRApplication.application, CHANNEL_ID_CHAT)
            .setSmallIcon(R.drawable.ic_notify_heart) // 专供通知的单色图形(自适应启动图会渲染成剪影)
            .setContentTitle(title)
            .setContentText(body)
            // 文本超行折叠成 BigTextStyle 单行摘要语义(情侣消息常长句,预览尽量多露)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            // API<26 旧设备 heads-up 语义(26+ 由渠道 IMPORTANCE_HIGH 决定,priority 被忽略)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // 同 id 重投不重放声音/震动(并发覆盖拉双发时数秒内只响一声)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        // NOTIF_ID 固定:同一会话新通知覆盖旧条目(情侣单会话语义,通知栏不堆叠)
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 双保险:极端场景(用户后台手动关通知权限)不崩,红点兜底
        }
    }

    /** 权限管理页(M22)状态查询:app 级总开关(areNotificationsEnabled,含 13+ 运行时权限与
     *  低版本设置开关,双向同步官方文档锤实)+ 渠道级开关(「悄悄话」渠道可被单独关)双值回,
     *  状态页才能区分「总开关关」与「渠道被单独关」两种已关闭形态 */
    private fun checkPermission(callback: KuiklyRenderCallback?) {
        val manager = NotificationManagerCompat.from(KRApplication.application)
        val appGranted = manager.areNotificationsEnabled()
        var channelOn = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelOn = manager.getNotificationChannel(CHANNEL_ID_CHAT)
                ?.let { it.importance != NotificationManager.IMPORTANCE_NONE } ?: true
        }
        callback?.invoke(mapOf("appGranted" to appGranted, "channelOn" to channelOn))
    }

    /** 权限管理页「去开启」:33+ 且未授权时对宿主 Activity 发运行时申请(结果经
     *  onRequestPermissionsResult→companion 静态转发一次性回调);已授权/API<33(无运行时
     *  权限语义)/宿主不可用,均 asked=false 同步回。⚠️ asked=true 仅表示「请求已发出并出
     *  结果」——拒绝两次后系统静默拒绝时 requestPermissions 不再弹框**但仍立即回调
     *  DENIED**(3-skeptic 官方文档锤实),即静默拒绝也走 asked=true,无法与真实弹框区分;
     *  调用方(PermissionPage)已改为 denied 一律引导跳系统设置,勿依赖 asked 做分支 */
    private fun requestPermission(callback: KuiklyRenderCallback?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val on = NotificationManagerCompat.from(KRApplication.application).areNotificationsEnabled()
            callback?.invoke(mapOf("granted" to on, "asked" to false))
            return
        }
        if (permissionGranted()) {
            callback?.invoke(mapOf("granted" to true, "asked" to false))
            return
        }
        val host = activity
        if (host == null || host.isFinishing) {
            callback?.invoke(mapOf("granted" to false, "asked" to false))
            return
        }
        pendingPermissionCallback = callback
        try {
            host.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_PERMISSION)
        } catch (_: Exception) {
            pendingPermissionCallback = null
            callback?.invoke(mapOf("granted" to false, "asked" to false))
        }
    }

    /** 跳系统通知设置(ACTION_APP_NOTIFICATION_SETTINGS 需 API 26+,minSdk 23 回退应用详情页;
     *  try/catch 防护官方文档要求的无匹配 Activity 场景)。不 finish 本页,从设置返回即回 */
    private fun openSettings() {
        val context = KRApplication.application
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** 点击通知回 App:复用既有任务栈(SINGLE_TOP 不重建),进页后红点/消息由常规拉取链路就绪 */
    private fun contentIntent(): PendingIntent {
        val intent = Intent(KRApplication.application, KuiklyRenderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            KRApplication.application,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** common 侧 base/NotificationModule.MODULE_NAME 同串(KR 前缀原生类名、HR 前缀注册名,惯例) */
        const val MODULE_NAME = "HRNotificationModule"

        /**
         * 通知渠道 id(KRApplication.ensureNotifyChannel 同串,系统设置可见名「悄悄话」)。
         * v2:由 "chat" 换 "chat_high"——渠道 importance 一经创建不可改,升 IMPORTANCE_HIGH
         * (heads-up 横幅)必须换 id,旧 id 渠道在已装设备上留 DEFAULT 语义不碍事
         */
        const val CHANNEL_ID_CHAT = "chat_high"
        private const val METHOD_NOTIFY = "notify"
        private const val METHOD_CHECK = "check"
        private const val METHOD_REQUEST = "request"
        private const val METHOD_OPEN_SETTINGS = "openSettings"

        /** 固定通知 id(新覆盖旧不堆叠)与 PendingIntent requestCode */
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1001

        /** 运行时权限申请 requestCode(自动请求[主页 onCreate]与页面主动申请共用一个:pending
         *  槽位单份,系统授权框模态期间不会并发第二次) */
        const val REQUEST_CODE_PERMISSION = 2001

        /** 页面主动申请的一次性回传(仅 METHOD_REQUEST 设;自动请求不设,结果静默):
         *  KuiklyRenderBaseModule 无 onRequestPermissionsResult 钩子,由 KuiklyRenderActivity
         *  override 后转发本静态入口(KRPickImageActivity→KRFileModule.onPickResult 同款桥模式,
         *  权限结果发回发起 Activity 本身,无需透明桥 Activity) */
        @Volatile
        private var pendingPermissionCallback: KuiklyRenderCallback? = null

        /** KuiklyRenderActivity.onRequestPermissionsResult 转发入口:无 pending 静默丢弃
         *  (自动请求路径),有 pending 一次性收口防重放 */
        fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_PERMISSION) return
            val callback = pendingPermissionCallback ?: return
            pendingPermissionCallback = null
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            callback.invoke(mapOf("granted" to granted, "asked" to true))
        }
    }
}

/** notify 前置权限检查(内联扩展保持调用处扁平;API 33+ 才有运行时语义) */
private fun KRNotificationModule.permissionGranted(): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        KRApplication.application,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )
    return granted == PackageManager.PERMISSION_GRANTED
}
