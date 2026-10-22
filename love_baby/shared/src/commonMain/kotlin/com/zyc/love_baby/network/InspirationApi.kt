package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager

/**
 * InspirationApi — 恋爱灵感域接口(M17,需登录;全局共享运营内容无 couple/user 归属)
 * 用法:协程内 val r = InspirationApi.today(pager);quote 回包单对象 InspirationQuote.from(r.data),
 *      list 回包数组 InspirationInfo.listFrom(r.dataArray);三端点全 GET 无参零请求体
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);InspirationModels
 * 事件:无(页内自持不进 AppState,D-19g——页销毁即弃,登出/解绑零 reset 接线)
 * 功能:
 *  - today 服务端按日轮换(epochDay 对启用情话池取模,同日恒同句);池空服务端回兜底文案
 *  - quoteRandom 随机换一句(服务端排除今日句,池 >1 必换)
 *  - list 约会灵感全量(id asc 稳定序);端上分类筛选+「换一换」本地打乱,无分页
 *  - 只读端点零错误路径(服务端池空兜底),失败统一普通错误通道
 */
internal object InspirationApi {

    private const val PATH_TODAY = "/love/inspiration/today"
    private const val PATH_QUOTE_RANDOM = "/love/inspiration/quote-random"
    private const val PATH_LIST = "/love/inspiration/list"

    /** 今日情话:data 为单对象(恒对象形态,M11 冻结口径) */
    suspend fun today(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_TODAY, isPost = false)

    /** 随机换一句:data 为单对象 */
    suspend fun quoteRandom(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_QUOTE_RANDOM, isPost = false)

    /** 约会灵感全量列表:data 为数组(ApiResult.Ok.dataArray) */
    suspend fun list(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_LIST, isPost = false)
}
