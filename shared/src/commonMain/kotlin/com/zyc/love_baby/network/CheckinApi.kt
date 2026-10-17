package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * CheckinApi — 每日打卡域接口(INTEGRATION-DESIGN §5.2 checkin 组,需登录,M8)
 * 用法:协程内 val r = CheckinApi.check(pager);list 端点 data 为数组,经 ApiResult.Ok.dataArray
 *      携带(HttpClient M3 增量),由调用方以 CheckinInfo.listFrom 解析;check 回包单个 VO 用
 *      CheckinInfo.from;summary 回包单对象用 CheckinSummaryInfo.from
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);CheckinModels
 * 事件:无(状态统一落 state/CheckinStore,由调用方驱动)
 * 功能:
 *  - check 打卡今天:不收任何参数(服务端以服务端当天为准,无补打);uk_user_date 幂等由服务端
 *    保证(重复打卡回已有记录不打错),端上按成功处理
 *  - list 按月拉 couple 双方记录(month=yyyy-MM;某日无记录 = 回包无该日条目,端上按空缺渲染);
 *    CheckinPage 进入/切月由调用方换 month 重拉(§7.4 拉取触发点)
 *  - summary 打卡汇总(共同连续/我/TA 全量累计,服务端聚合):首页「今日打卡」条与打卡页页头共用
 *  - ⚠ 查询参数直接拼 URL 而非走 core param 通道(MoodApi 同款:core 各宿主对 GET param 转 query
 *    行为不一,month 为 yyyy-MM 纯 URL 安全字符,显式拼接五端一致)
 *  - 未绑定回 1_003_004_003 走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object CheckinApi {

    private const val PATH_CHECK = "/love/checkin/check"
    private const val PATH_LIST = "/love/checkin/list"
    private const val PATH_SUMMARY = "/love/checkin/summary"

    /** 打卡今天:POST 无 body(不收参数);回包 data 为打卡记录(含服务端 id,Store.check 用回包落态) */
    suspend fun check(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_CHECK, isPost = true)

    /**
     * 按月列表:data 为数组(ApiResult.Ok.dataArray)。
     * @param month yyyy-MM(如 2026-10),由调用方按当前展示月传入
     */
    suspend fun list(pager: Pager, month: String): ApiResult =
        HttpClient.request(pager, "$PATH_LIST?month=$month", isPost = false)

    /** 打卡汇总:data 为单对象(commonStreak/myTotal/partnerTotal) */
    suspend fun summary(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_SUMMARY, isPost = false)
}
