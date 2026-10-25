package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.BatteryReportReq

/**
 * BatteryApi — 电量互看域接口(M26,需登录+绑定)
 * 用法:协程内 val r = BatteryApi.report(pager, level, charging);report 与 get 回包结构相同
 *      (BatteryRespInfo:mine 可 null + partner 恒有),经 ApiResult.Ok.data 解析
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);BatteryModels
 * 事件:无(电量状态统一落 state/BatteryStore,由调用方驱动)
 * 功能:
 *  - report 上报自己快照(每用户一行 upsert,update_time 即 reportTime)——上报即带回双方状态,
 *    电量页「上报即刷新」单请求完成,免二次拉取
 *  - get 电量页 60s 轮询用(回包结构与 report 相同,只读不写)
 *  - 我方电量共享关闭时服务端拒收 report(1_003_015_002)——端上开关状态驱动上报,正常不触发
 */
internal object BatteryApi {

    private const val PATH_REPORT = "/love/share/battery/report"
    private const val PATH_GET = "/love/share/battery/get"

    /** 上报自己电量并带回双方状态:charging 0=未充电 1=充电中 2=已充满 */
    suspend fun report(pager: Pager, level: Int, charging: Int): ApiResult =
        HttpClient.request(pager, PATH_REPORT, isPost = true, body = BatteryReportReq(level, charging).toJson())

    /** 查电量双方状态(只读轮询) */
    suspend fun get(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_GET, isPost = false)
}
