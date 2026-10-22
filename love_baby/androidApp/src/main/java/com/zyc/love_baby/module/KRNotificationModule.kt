package com.zyc.love_baby.module

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
 *
 * ⚠ 回调投递铁律(2026-10-19 真机反馈轮定稿):所有回 common 的 callback 一律经 mainHandler
 * post 异步投递(dispatchCallback 统一收口),严禁在 call() 内同步 invoke——call() 由引擎
 * runMainQueueTasks 主队列任务驱动,真机实证 call() 内同步回包不送达(权限页卡「查询中」,
 * checked 永假);仓内 WS/File 两模块全部经 mainHandler 异步投递且真机验证可用,引擎对
 * 「主队列任务执行期间的同步回调」在同 setViewProp 事件路径也有降级异步的官方同款处理
 * (KuiklyRenderCore:408-411「正在主线程执行任务产生的同步事件->异步」),2.29 源码锤实。
 * 载荷用 Map 即可:边界层 KuiklyRenderCommonContextHandler.toKotlinObject 会把 Map 转
 * JSON 字符串再回 common(WS 模块 mapOf 真机先例;core Module.toNative 只认 String/框架
 * JSONObject,直传裸 Map 在 iOS/鸿蒙桥会落 null,故官方 demo 建议回 JSON 串——Android 端
 * 边界层已代转,勿再手工序列化)。
 * 查询兜底:checkPermission 采集全程 try/catch,任何异常也必发兜底回包(全 false=「已关闭」
 * 形态)——空回包=页面卡「查询中」,宁可给可操作的已关闭态
 */
class KRNotificationModule : KuiklyRenderBaseModule() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            METHOD_NOTIFY -> notify(params)
            METHOD_CHECK -> checkPermission(callback)
            METHOD_REQUEST -> requestPermission(callback)
            METHOD_OPEN_SETTINGS -> openSettings()
            METHOD_OPEN_CHANNEL -> openChannelSettings()
            else -> dispatchCallback(callback, UNKNOWN_METHOD_PAYLOAD)
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

    /** 权限管理页(M22)状态查询:六值富载荷(common 侧 NotifyPermState 同构)。字段语义:
     *  - appGranted:App 级总开关(areNotificationsEnabled,13+ 与 POST_NOTIFICATIONS 运行时
     *    权限双向同步,官方文档锤实)
     *  - channelExists:渠道已建(API 26+ 且存在;API<26 恒 false=渠道概念不存在,横幅由
     *    通知 PRIORITY_HIGH 决定,notify 已设)
     *  - channelBlocked:渠道被用户单独关(importance=IMPORTANCE_NONE;getImportance 读回
     *    的是用户当前值,用户调低可查)
     *  - channelHigh:渠道 importance≥HIGH=横幅(heads-up)必要非充分条件——原生 SystemUI 可
     *    决定不弹,国产 ROM 另有独立「悬浮通知/横幅」开关且无标准查询 API(权限页该项只能
     *    引导去确认,不能断言)
     *  - dndOn:勿扰模式(getCurrentInterruptionFilter!=ALL,横幅与声音被系统压制)
     *  - powerSaveOn:省电模式(部分 ROM 省电下临时关横幅)
     *  采集异常也回兜底形态,必达(见类头铁律) */
    private fun checkPermission(callback: KuiklyRenderCallback?) {
        val payload = try {
            queryPermState()
        } catch (_: Exception) {
            FALLBACK_STATE_PAYLOAD
        }
        dispatchCallback(callback, payload)
    }

    /** 采集通知权限六值快照(仅 checkPermission 调用;自身不 catch,由调用方兜底收口) */
    private fun queryPermState(): Map<String, Any> {
        val context = KRApplication.application
        val manager = NotificationManagerCompat.from(context)
        val appGranted = manager.areNotificationsEnabled()
        var channelExists = false
        var channelBlocked = false
        var channelHigh = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(CHANNEL_ID_CHAT)
            if (channel != null) {
                channelExists = true
                channelBlocked = channel.importance == NotificationManager.IMPORTANCE_NONE
                channelHigh = channel.importance >= NotificationManager.IMPORTANCE_HIGH
            }
        }
        // 勿扰/省电:minSdk 23=M/21 起可用,无版本闸;系统服务异常让上层兜底
        val notifyManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndOn = notifyManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val powerSaveOn = powerManager.isPowerSaveMode
        return mapOf(
            "appGranted" to appGranted,
            "channelExists" to channelExists,
            "channelBlocked" to channelBlocked,
            "channelHigh" to channelHigh,
            "dndOn" to dndOn,
            "powerSaveOn" to powerSaveOn,
        )
    }

    /** 权限管理页「去开启」:33+ 且未授权时对宿主 Activity 发运行时申请(结果经
     *  onRequestPermissionsResult→companion 静态转发一次性回调);已授权/API<33(无运行时
     *  权限语义)/宿主不可用,均 asked=false 同步回。⚠️ asked=true 仅表示「请求已发出并出
     *  结果」——拒绝两次后系统静默拒绝时 requestPermissions 不再弹框**但仍立即回调
     *  DENIED**(3-skeptic 官方文档锤实),即静默拒绝也走 asked=true,无法与真实弹框区分;
     *  调用方(PermissionPage)已改为 denied 一律引导跳系统设置,勿依赖 asked 做分支 */
    /** 本实例在飞的申请回调(与 companion 静态槽存同一对象引用;onDestroy 归属校验用) */
    private var myPendingCallback: KuiklyRenderCallback? = null

    private fun requestPermission(callback: KuiklyRenderCallback?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val on = NotificationManagerCompat.from(KRApplication.application).areNotificationsEnabled()
            dispatchCallback(callback, mapOf("granted" to on, "asked" to false))
            return
        }
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
            host.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_PERMISSION)
        } catch (_: Exception) {
            pendingPermissionCallback = null
            dispatchCallback(callback, mapOf("granted" to false, "asked" to false))
        }
    }

    /** 跳 App 级系统通知设置(总开关所在页;API<26 回退应用详情页)。不 finish 本页,从设置返回即回 */
    private fun openSettings() {
        val context = KRApplication.application
        val intent = appNotifySettingsIntent(context)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(appDetailsIntent(context))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    /** 跳「悄悄话」渠道级通知设置(渠道重要性/悬浮横幅开关所在页;权限页「悬浮横幅提醒」
     *  引导行的落点)。回退链:渠道页深链(官方 ACTION_CHANNEL_NOTIFICATION_SETTINGS,
     *  EXTRA_APP_PACKAGE+EXTRA_CHANNEL_ID,需 API 26+ 且渠道已建——渠道由 KRApplication
     *  启动即建,常态成立)→ App 通知设置页(渠道缺失/低版本)→ 应用详情页(极端 ROM 深链
     *  无匹配 Activity,官方文档要求 try/catch)。⚠ Application context 起页必须
     *  FLAG_ACTIVITY_NEW_TASK;不 finish 本页,返回即回 */
    private fun openChannelSettings() {
        val context = KRApplication.application
        val channelExists = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            NotificationManagerCompat.from(context).getNotificationChannel(CHANNEL_ID_CHAT) != null
        val intent = if (channelExists) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            appNotifySettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    /** App 级通知设置 Intent(ACTION_APP_NOTIFICATION_SETTINGS 需 API 26+;调用方自加 NEW_TASK) */
    private fun appNotifySettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetailsIntent(context)
        }

    /** 应用详情页 Intent(最低回退点;调用方自加 NEW_TASK) */
    private fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** 回调统一收口:mainHandler 异步投递(类头铁律),null 回调(notify 等 fire-and-forget)零成本直落 */
    private fun dispatchCallback(callback: KuiklyRenderCallback?, payload: Map<String, Any>) {
        if (callback == null) {
            return
        }
        mainHandler.post { callback.invoke(payload) }
    }

    /** 页面销毁即清自己在飞的 pending 槽(3-skeptic:静态槽无销毁清理则已销毁页面的
     *  Activity/视图树被静态引用到进程结束=泄漏,且 stale 回调可能被另一 Activity 实例
     *  的 2001 结果错误消费;归属校验照 KRFileModule.onDestroy 清 current 先例——仅当
     *  槽内仍是本实例发出的回调才清,不误伤其他实例在飞的申请;正常流程结果先于销毁
     *  送达,此处清空是 no-op。销毁后结果到达走 onPermissionResult 的 pending 空静默
     *  丢弃,死页面无感,新实例 requesting=false 自愈) */
    override fun onDestroy() {
        if (myPendingCallback != null && pendingPermissionCallback === myPendingCallback) {
            pendingPermissionCallback = null
        }
        myPendingCallback = null
        super.onDestroy()
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
        private const val METHOD_OPEN_CHANNEL = "openChannel"

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

        /** 权限结果转发口(onRequestPermissionsResult 在主线程调用,post 一拍出当前主队列
         *  消息,与实例侧 dispatchCallback 同规——类头铁律) */
        private val mainHandler = Handler(Looper.getMainLooper())

        /** KuiklyRenderActivity.onRequestPermissionsResult 转发入口:无 pending 静默丢弃
         *  (自动请求路径),有 pending 一次性收口防重放 */
        fun onPermissionResult(requestCode: Int, grantResults: IntArray) {
            if (requestCode != REQUEST_CODE_PERMISSION) return
            val callback = pendingPermissionCallback ?: return
            pendingPermissionCallback = null
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            mainHandler.post { callback.invoke(mapOf("granted" to granted, "asked" to true)) }
        }

        /** checkPermission 采集异常的兜底回包:全 false=「已关闭」可操作形态(类头铁律:
         *  空回包=页面卡「查询中」,宁可给可操作的已关闭态) */
        private val FALLBACK_STATE_PAYLOAD: Map<String, Any> = mapOf(
            "appGranted" to false,
            "channelExists" to false,
            "channelBlocked" to false,
            "channelHigh" to false,
            "dndOn" to false,
            "powerSaveOn" to false,
        )

        /** 未知方法回包(与旧 else 分支语义一致,走统一收口异步投递) */
        private val UNKNOWN_METHOD_PAYLOAD: Map<String, Any> =
            mapOf("code" to -1, "message" to "方法不存在")
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
