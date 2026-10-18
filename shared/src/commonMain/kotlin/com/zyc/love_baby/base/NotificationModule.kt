package com.zyc.love_baby.base

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

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
 * 功能:notify 发通知 + M22 三方法(权限管理页驱动)——checkPermission 状态查询(app 级
 *  总开关+渠道级开关双值)/requestPermission 发起运行时授权(33+,结果回调)/openSettings
 *  跳系统通知设置;渠道创建/点击跳转/权限弹窗全在原生侧单源(targetSdk 33 起系统不再自动
 *  弹窗,打开时请求在 Android 壳 KuiklyRenderActivity 主页 onCreate)
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

    /** 查询通知权限状态(一次性回调):appGranted=App 级总开关(areNotificationsEnabled,13+
     *  与运行时权限状态双向同步)/channelOn=「悄悄话」渠道未被单独关;双值分离使页面可区分
     *  「总开关关」与「渠道被单独关」两种已关闭形态 */
    fun checkPermission(onResult: (appGranted: Boolean, channelOn: Boolean) -> Unit) {
        // 回调位置传参(FileModule 同款):toNative 尾随 lambda 会绑到末位 syncCall: Boolean 上
        val callback: CallbackFn = { result ->
            onResult(
                result?.optBoolean("appGranted", false) ?: false,
                result?.optBoolean("channelOn", false) ?: false,
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

    /** 跳系统通知设置(fire-and-forget,null 回调 notify 同款):API<26 由原生回退应用详情页 */
    fun openSettings() {
        toNative(false, METHOD_OPEN_SETTINGS, "{}", null)
    }

    companion object {
        /** 与各端原生注册名一致(Android KRNotificationModule.MODULE_NAME 同串,KR/HR 前缀惯例) */
        const val MODULE_NAME = "HRNotificationModule"
        private const val METHOD_NOTIFY = "notify"
        private const val METHOD_CHECK = "check"
        private const val METHOD_REQUEST = "request"
        private const val METHOD_OPEN_SETTINGS = "openSettings"
    }
}
