package com.zyc.love_baby.base

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
 * 功能:仅发通知动作;渠道创建/点击跳转/权限判定全在原生侧单源(targetSdk 30 通知权限
 *  自动授予,manifest 声明 POST_NOTIFICATIONS 防未来升 targetSdk 时遗漏)
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

    companion object {
        /** 与各端原生注册名一致(Android KRNotificationModule.MODULE_NAME 同串,KR/HR 前缀惯例) */
        const val MODULE_NAME = "HRNotificationModule"
        private const val METHOD_NOTIFY = "notify"
    }
}
