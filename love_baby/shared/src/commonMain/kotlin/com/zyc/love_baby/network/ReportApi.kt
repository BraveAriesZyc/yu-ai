package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * ReportApi — 恋爱月报域接口(app-api /love/report,需登录,M30)
 * 用法:协程内 val r = ReportApi.monthly(pager, month);回包 data 为单对象,经
 *      ReportMonthlyInfo.from 解析(CheckinApi.summary 同型)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);ReportModels
 * 事件:无(月报页内自持状态,不落全局 Store——MoodTimelinePage 页域先例)
 * 功能:
 *  - monthly 单月五域聚合(打卡/心情/悄悄话/相册/清单 + 在一起天数):month=yyyy-MM,
 *    端上恒显式传当前展示月(服务端缺省当月兜底);非法格式服务端 400 走 BizError toast
 *  - ⚠ 查询参数经 network.queryPath 显式拼 URL(CheckinApi/MoodApi 同款决策;month 为
 *    yyyy-MM 纯 URL 安全字符)
 *  - 未绑定回全 0(服务端静默口径,无 1_003_004_003 分支,不参与 successBizCodes)
 */
internal object ReportApi {

    private const val PATH_MONTHLY = "/love/report/monthly"

    /** 恋爱月报:data 为单对象(month/bindDays/五域计数/topMoods) */
    suspend fun monthly(pager: Pager, month: String): ApiResult =
        HttpClient.request(pager, queryPath(PATH_MONTHLY, "month" to month), isPost = false)
}
