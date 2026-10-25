package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.base.BatteryModule
import com.zyc.love_baby.base.BatteryNativeState
import com.zyc.love_baby.data.model.BatteryRespInfo
import com.zyc.love_baby.data.model.ShareSettingInfo
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.BatteryApi
import com.zyc.love_baby.network.ShareApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * BatteryStore — 情侣电量互看快照单例(M26,StatsStore 单例范式)
 * 用法:读 myLevel/myCharging/myReportTime(我方)与 partnerXxx(TA 方)渲染电量页;
 *      refresh 兼上报(电量页打开/60s 轮询/回前台上报统一入口);batteryShareEnabled 开关
 *      由电量页 loadSetting/updateShare 读写;登出/解绑 MainPager 调 reset
 * 依赖:androidx.compose.runtime 快照状态;BatteryApi/ShareApi(HttpClient 401 单飞刷新);
 *      base/BatteryModule(原生电量,Android 注册/iOS·ohos·H5 未注册 getModule=null 降级)
 * 事件:轮询/上报失败静默保留旧值(只读降级 §11);开关更新回 ApiResult 由页面 toast
 * 功能:
 *  - refresh = 原生读电量(可得且开关开)→ battery/report(一次请求带回双方状态,「上报即
 *    刷新」免二次拉取);桥缺失/读取失败( level=-1 )/开关关 → 退 battery/get 纯拉
 *    (共享关仍可见 TA——上传与查看是两件事)
 *  - 哨兵口径(与 BatteryModels 约定同源):level -1=未知/从未上报,charging -1=从未上报,
 *    reportTime 0=从未上报;partnerShareEnabled=false 时 TA 字段为哨兵(UI 显「未开启共享」)
 *  - 无后台采集(产品定案):TA 电量新鲜度靠 reportTime 过期语义,超 24h 灰显「很久未在线」
 */
internal object BatteryStore {

    // ---- 我方快照(上报即更新;未上报/清场后为哨兵值) ----
    var myLevel by mutableIntStateOf(-1)
        private set
    var myCharging by mutableIntStateOf(-1)
        private set
    var myReportTime by mutableLongStateOf(0L)
        private set

    // ---- TA 快照 ----
    var partnerShareEnabled by mutableStateOf(true)
        private set
    var partnerLevel by mutableIntStateOf(-1)
        private set
    var partnerCharging by mutableIntStateOf(-1)
        private set
    var partnerReportTime by mutableLongStateOf(0L)
        private set

    /** 我方电量共享开关(懒创建默认开,低敏;电量页 loadSetting 后为准) */
    var batteryShareEnabled by mutableStateOf(true)
        private set

    /**
     * 拉取(兼上报)双方电量:原生电量可得且开关开 → report(带回双方);否则 get 纯拉。
     * 失败静默保留旧值(60s 轮询语义,不打扰)
     */
    suspend fun refresh(pager: Pager) {
        val resp = if (batteryShareEnabled) reportViaNative(pager) else null
        val result = resp ?: BatteryApi.get(pager)
        if (result is ApiResult.Ok) {
            BatteryRespInfo.from(result.data)?.let { apply(it) }
        }
    }

    /** 拉我的共享开关(电量页打开时;失败静默保留旧值) */
    suspend fun loadSetting(pager: Pager) {
        val result = ShareApi.getSetting(pager)
        if (result is ApiResult.Ok) {
            ShareSettingInfo.from(result.data)?.let { batteryShareEnabled = it.batteryShare }
        }
    }

    /** 更新电量共享开关:成功落值回 Ok(页面 toast);失败不落值(UI 回滚) */
    suspend fun updateShare(pager: Pager, enabled: Boolean): ApiResult {
        val result = ShareApi.updateSetting(pager, batteryShare = enabled, locationShare = null)
        if (result is ApiResult.Ok) {
            ShareSettingInfo.from(result.data)?.let { batteryShareEnabled = it.batteryShare }
        }
        return result
    }

    /** 原生读电量并上报:桥缺失/读取失败(level<0)/异常回 null(调用方退 get) */
    private suspend fun reportViaNative(pager: Pager): ApiResult? {
        val module = pager.getModule<BatteryModule>(BatteryModule.MODULE_NAME) ?: return null
        val state = try {
            suspendCoroutine<BatteryNativeState> { cont ->
                module.getBatteryStatus { s -> cont.resume(s) }
            }
        } catch (_: Exception) {
            return null
        }
        if (state.level < 0) return null // 电量未知(极端机型 sticky 未就绪)不上报,免造脏数据
        return try {
            BatteryApi.report(pager, state.level, state.charging)
        } catch (_: Exception) {
            null
        }
    }

    private fun apply(resp: BatteryRespInfo) {
        resp.mine?.let { mine ->
            myLevel = mine.level
            myCharging = mine.charging
            myReportTime = mine.reportTime
        }
        partnerShareEnabled = resp.partner.shareEnabled
        partnerLevel = resp.partner.level
        partnerCharging = resp.partner.charging
        partnerReportTime = resp.partner.reportTime
    }

    /** 登出/解绑清场(与 StatsStore.reset 同范式,重复清幂等) */
    fun reset() {
        myLevel = -1
        myCharging = -1
        myReportTime = 0L
        partnerShareEnabled = true
        partnerLevel = -1
        partnerCharging = -1
        partnerReportTime = 0L
        batteryShareEnabled = true
    }
}
