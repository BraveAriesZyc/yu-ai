package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * checkPermission 回包快照(权限页状态渲染单源,data class 相等可跳重组)
 * 字段语义(原生侧 Android SDK 查询面穷尽核实,ROM 专属开关不可查):
 *  - appGranted:App 级通知总开关(areNotificationsEnabled,13+ 与 POST_NOTIFICATIONS 运行时
 *    权限双向同步,官方文档锤实)
 *  - channelExists:通知渠道已建(API 26+ 且渠道存在;API<26 恒 false=渠道概念不存在,
 *    横幅由通知 PRIORITY 决定,App 已设 HIGH)
 *  - channelBlocked:渠道被用户单独关(importance=IMPORTANCE_NONE;getImportance 读回的是
 *    「用户当前值」,用户调低可查——官方文档锤实)
 *  - channelHigh:渠道 importance≥HIGH=横幅(heads-up)必要非充分条件:原生层 SystemUI 可决
 *    定不弹(SUPPRESSED_EFFECT_PEEK 等),国产 ROM 另有独立「悬浮通知/横幅」开关且无标准
 *    查询 API——故权限页横幅项是「引导去确认」而非断言状态
 *  - dndOn:勿扰模式激活(getCurrentInterruptionFilter!=ALL,API 23+;横幅与声音被系统压制)
 *  - powerSaveOn:省电模式激活(isPowerSaveMode;部分 ROM 省电下临时关横幅)
 */
internal data class NotifyPermState(
    val appGranted: Boolean,
    val channelExists: Boolean,
    val channelBlocked: Boolean,
    val channelHigh: Boolean,
    val dndOn: Boolean,
    val powerSaveOn: Boolean,
)

/**
 * NotificationModule — 系统通知桥接(后台消息提醒,跨端接口声明;各端原生壳注册同名模块)
 * 依赖:core Module.toNative;各端原生实现注册同名模块(Android=KRNotificationModule,
 *  NotificationCompat 实现,前后台判定在原生侧[KRApplication Activity started 计数]);
 *  iOS/ohos/H5 未注册前 getModule 返回 null,调用方判空静默降级——未读红点/轮询兜底不受影响,
 *  渐进降级设计(同 WebSocketModule/FileModule)
 * 用法:ChatStore.pull 落态钩子经 pager.getModule<NotificationModule>(MODULE_NAME) 取用;
 *  收口在 pull 而非 ChatPushStore:轮询/切 Tab/WS 信号全部拉取路径统一覆盖(信号只触发拉,
 *  通知跟着「拉到对方新消息」走,天然幂等——两路径进 pull 由 pullMutex 串行,第二次回包
 *  已无新于游标的条目,不会双发)
 * 入参:notify(title, body)——标题=对方昵称(CoupleStore 快照),body=最新对方消息单行摘要
 *  (文本取 content / 心愿「💌 title」/ 图片「[图片]」,由 common 侧组装,原生不解析业务语义)
 * 事件:无回调(fire-and-forget,toNative 传 null,disconnect 同款惯例)——发没发出由原生判定,
 *  common 无状态:App 前台原生 no-op(气泡+红点已可见,不弹通知打扰),后台才发系统通知;
 *  进程被杀场景 WS 断信号收不到,本期不做厂商推送(留待上线后按需求评估)
 * 功能:notify 发通知 + 权限管理页四方法——checkPermission 状态查询(NotifyPermState 六值,
 *  总开关/渠道存在/渠道被关/渠道高重要级/勿扰/省电;真机反馈轮:卡「查询中」根因=原生在
 *  call() 内同步回调不送达,原生侧统一 mainHandler 转主线程投递)/requestPermission 发起
 *  运行时授权(33+,结果回调)/openSettings 跳 App 级通知设置(总开关手动开,API<26 回退应用
 *  详情页)/openChannelSettings 跳「悄悄话」渠道级设置(渠道重要性/横幅开关所在页,原生回退
 *  链 渠道页→App 通知页→应用详情;渠道创建/点击跳转/权限弹窗全在原生侧单源,targetSdk 33 起
 *  系统不再自动弹窗,打开时请求在 Android 壳 KuiklyRenderActivity 主页 onCreate)
 */
internal class NotificationModule : Module() {

    override fun moduleName(): String {
        return MODULE_NAME
    }

    /** 发一条后台新消息通知(fire-and-forget):title 对方昵称 / body 消息单行摘要;前台 no-op */
    fun notify(title: String, body: String) {
        val methodArgs = JSONObject()
        methodArgs.put("title", title)
        methodArgs.put("body", body)
        toNative(false, METHOD_NOTIFY, methodArgs.toString(), null)
    }

    /** 查询通知权限状态(一次性回调,回 NotifyPermState 六值快照;字段语义见 data class 注释) */
    fun checkPermission(onResult: (NotifyPermState) -> Unit) {
        // 回调位置传参(FileModule 同款):toNative 尾随 lambda 会绑到末位 syncCall: Boolean 上
        val callback: CallbackFn = { result ->
            onResult(
                NotifyPermState(
                    appGranted = result?.optBoolean("appGranted", false) ?: false,
                    channelExists = result?.optBoolean("channelExists", false) ?: false,
                    channelBlocked = result?.optBoolean("channelBlocked", false) ?: false,
                    channelHigh = result?.optBoolean("channelHigh", false) ?: false,
                    dndOn = result?.optBoolean("dndOn", false) ?: false,
                    powerSaveOn = result?.optBoolean("powerSaveOn", false) ?: false,
                )
            )
        }
        toNative(false, METHOD_CHECK, "{}", callback)
    }

    /** 发起运行时授权(一次性回调):granted=请求出结果后是否已授权;asked=true 仅表示
     *  「请求已真实发出并出了结果」,无法区分「弹了系统授权框被拒」与「拒绝两次后系统
     *  静默拒绝」(33+ 配额耗尽时 requestPermissions 不再弹框但仍立即回调 DENIED,
     *  3-skeptic 对官方文档锤实)——调用方勿以 asked 做真假弹框分支,denied 统一引导
     *  「去系统设置」即可(勿依赖 shouldShowRequestPermissionRationale,部分 ROM 语义
     *  不标准);asked=false=请求未发出(已授权短路/API<33 无运行时语义/宿主不可用) */
    fun requestPermission(onResult: (granted: Boolean, asked: Boolean) -> Unit) {
        // 位置传参同 checkPermission
        val callback: CallbackFn = { result ->
            onResult(
                result?.optBoolean("granted", false) ?: false,
                result?.optBoolean("asked", false) ?: false,
            )
        }
        toNative(false, METHOD_REQUEST, "{}", callback)
    }

    /** 跳系统通知设置(fire-and-forget,null 回调 notify 同款):App 级总开关所在页,
     *  API<26 由原生回退应用详情页 */
    fun openSettings() {
        toNative(false, METHOD_OPEN_SETTINGS, "{}", null)
    }

    /** 跳「悄悄话」渠道级通知设置(fire-and-forget):渠道重要性/悬浮横幅开关所在页,
     *  原生回退链 渠道页→App 通知页→应用详情页(API<26/渠道缺失自动降级) */
    fun openChannelSettings() {
        toNative(false, METHOD_OPEN_CHANNEL, "{}", null)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRNotificationModule.MODULE_NAME 同串,KR/HR 前缀惯例) */
        const val MODULE_NAME = "HRNotificationModule"
        private const val METHOD_NOTIFY = "notify"
        private const val METHOD_CHECK = "check"
        private const val METHOD_REQUEST = "request"
        private const val METHOD_OPEN_SETTINGS = "openSettings"
        private const val METHOD_OPEN_CHANNEL = "openChannel"
    }
}
