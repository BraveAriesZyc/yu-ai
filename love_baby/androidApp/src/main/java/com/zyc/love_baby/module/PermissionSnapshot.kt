package com.zyc.love_baby.module

import android.content.Context
import org.json.JSONObject

/**
 * 权限页首帧状态快照(M29):模块回调在权限管理页静默丢失真机实证(页永卡「查询中」,
 * 两轮 mainHandler 投递修复未根治),首帧真实状态改走 pageData 直注通道(theme/session
 * 同款)——KuiklyRenderActivity.createPageData 对 permissionPage 调 [build],common 侧
 * PermissionPager.willInit 播种,首帧即真实状态,彻底绕开模块桥。
 * 回前台重推(M36):pageData 只在创建时读一次,从系统设置/应用详情改完权限返回时快照
 * 已陈旧,而模块回调通道在本页已废——onResume 重采快照经 delegator.sendEvent 直推
 * ([EVENT_PERM_SNAPSHOT] 事件,common 侧观察者收包走同一解析),回页即真值。
 * 四组状态全部 companion 单源直采(KRNotificationModule.queryPermStateSnapshot /
 * KRLocationModule.permissionSnapshot / KRFileModule.micGrantedSnapshot+mediaPermSnapshot,
 * 与模块 check 回包同一份查询逻辑,无第二实现);模块 refresh 仍在(回调若通,到包同值
 * 幂等刷;非 Android 宿主无推送,维持逐组查询+超时兜底链)
 */
object PermissionSnapshot {

    /** pageData 键(common 侧 PermissionPage.kt PERM_SNAPSHOT_KEY 同串,双源勿单改) */
    const val KEY_PERM_SNAPSHOT = "permSnapshot"

    /** 回前台重推事件名(M36;common 侧 PermissionPage.kt PERM_PUSH_EVENT 同串,双源勿单改) */
    const val EVENT_PERM_SNAPSHOT = "permSnapshotPush"

    /** 采集四组权限状态并落 JSON 串(全系统 API 级安全查询,无版本闸;采集异常不外抛——
     *  单组失败落 false,与模块 check 的兜底语义一致,页面永远有可渲染的真实形态) */
    fun build(context: Context): String {
        val json = JSONObject()
        try {
            KRNotificationModule.queryPermStateSnapshot().forEach { (k, v) -> json.put(k, v) }
        } catch (_: Exception) {
        }
        try {
            KRLocationModule.permissionSnapshot(context).forEach { (k, v) -> json.put(k, v) }
        } catch (_: Exception) {
        }
        try {
            json.put("micGranted", KRFileModule.micGrantedSnapshot(context))
            KRFileModule.mediaPermSnapshot(context).forEach { (k, v) -> json.put(k, v) }
        } catch (_: Exception) {
        }
        return json.toString()
    }
}
