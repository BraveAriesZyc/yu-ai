package com.zyc.love_baby.module

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
 * 权限:targetSdk 30 下 Android 13+ 的权限弹窗由「activity 启动 + 已存在通知渠道」触发
 * (渠道由 KRApplication.onCreate 即建,skeptic major 修复);此处 checkSelfPermission 仅作
 * 防御——未来升 targetSdk 33+ 忘记运行时申请时静默降级不崩,未授权场景红点兜底
 */
class KRNotificationModule : KuiklyRenderBaseModule() {

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_NOTIFY -> notify(params)
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
        // API 33+ 运行时权限防御(targetSdk 30 下正常已授;未授权时静默降级红点)
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

        /** 固定通知 id(新覆盖旧不堆叠)与 PendingIntent requestCode */
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1001
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
