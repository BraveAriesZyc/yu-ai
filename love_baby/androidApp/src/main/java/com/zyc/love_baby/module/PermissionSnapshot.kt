package com.zyc.love_baby.module

import android.content.Context
import org.json.JSONObject

/**
 * 权限页首帧状态快照(M29):模块回调在权限管理页静默丢失真机实证(页永卡「查询中」,
 * 两轮 mainHandler 投递修复未根治),首帧真实状态改走 pageData 直注通道(theme/session
 * 同款)——KuiklyRenderActivity.createPageData 对 permissionPage 调 [build],common 侧
 * PermissionPager.willInit 播种,首帧即真实状态,彻底绕开模块桥。
 * 回前台刷新(M36.1):pageData 只在创建时读一次,从系统设置/应用详情改完权限返回或
 * 应用内弹框出结果时快照已陈旧。M36.0 首版走 delegator.sendEvent 事件推送,静态链路
 * 逐环可证却真机无效——与本页模块回调同死(core 初始化后宿主→core 的桥调用在真机上
 * 不可达),故改「宿主落 prefs+页面轮询」:宿主把新鲜快照+时间戳写进 KRSharedPreferencesModule
 * 文件(纯 Android API,与 core 状态无关;onResume 与 onRequestPermissionsResult 两处),
 * common 侧 PermissionPager 每秒经 SharedPreferencesModule.getString 同步桥轮询
 * (core→host 方向,主题持久化同走此桥真机已证),时间戳前进即落态。
 * 四组状态全部 companion 单源直采(KRNotificationModule.queryPermStateSnapshot /
 * KRLocationModule.permissionSnapshot / KRFileModule.micGrantedSnapshot+mediaPermSnapshot,
 * 与模块 check 回包同一份查询逻辑,无第二实现);模块 refresh 仍在(回调若通,到包同值
 * 幂等刷;非 Android 宿主无落地,维持逐组查询+超时兜底链)
 */
object PermissionSnapshot {

    /** pageData 键(common 侧 PermissionPage.kt PERM_SNAPSHOT_KEY 同串,双源勿单改) */
    const val KEY_PERM_SNAPSHOT = "permSnapshot"

    /** 落地快照 prefs 键(M36.1;common 侧 PermissionPage.kt PERM_SNAPSHOT_LIVE_KEY
     *  同串双源,勿单改一边;写在 KRSharedPreferencesModule 文件=shared 各 Store 与
     *  KuiklyRenderActivity.bootPrefs 同文件,common 同步桥可读) */
    const val KEY_PERM_SNAPSHOT_LIVE = "permSnapshotLive"

    /** 落地快照时间戳 prefs 键(M36.1;common 侧 PermissionPage.kt
     *  PERM_SNAPSHOT_LIVE_TS_KEY 同串双源,勿单改一边;时间戳是轮询的推进信号——
     *  快照内容不变(弹框点了拒绝)也要落态+复位在飞申请标记) */
    const val KEY_PERM_SNAPSHOT_LIVE_TS = "permSnapshotLiveTs"

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
