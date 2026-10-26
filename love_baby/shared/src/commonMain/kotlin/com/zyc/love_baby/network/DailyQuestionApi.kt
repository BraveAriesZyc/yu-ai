package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.DailyQuestionAnswerReq

/**
 * DailyQuestionApi — 每日一问域接口(M32,需登录;需 couple 绑定,未绑定回 1_003_004_003)
 * 用法:协程内 val r = DailyQuestionApi.today(pager);today/answer 回包单对象
 *      DailyQuestionToday.from(r.data),list 回包数组 DailyQuestionItem.listFrom(r.dataArray)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);DailyQuestionModels
 * 事件:today/answer 状态落 state/DailyQuestionStore(首页「每日一问」条与题目页共用);
 *      list 页内自持(D-19g——页销毁即弃,登出/解绑零 reset 接线)
 * 功能:
 *  - today 今日题+双方作答状态:当日题服务端懒生成(同日恒同题),今日无题 content 空(from 回 null)
 *  - answer 提交/覆盖写今天的答案(POST body content;只能答当天无补答,服务端以服务端当天为准;
 *    已揭晓回 1_003_016_001 由调用方 toast)
 *  - list 按月历史(question_date asc,含没答的天;month=yyyy-MM 经 queryPath 显式拼 URL,
 *    CheckinApi 同款决策,month 为纯 URL 安全字符)
 */
internal object DailyQuestionApi {

    private const val PATH_TODAY = "/love/daily-question/today"
    private const val PATH_ANSWER = "/love/daily-question/answer"
    private const val PATH_LIST = "/love/daily-question/list"

    /** 今日题:data 为单对象(恒对象形态,M11 冻结口径);今日无题 from 回 null */
    suspend fun today(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_TODAY, isPost = false)

    /** 提交/修改今天的答案:data 为单对象(答完即时反映最新互见/揭晓态) */
    suspend fun answer(pager: Pager, req: DailyQuestionAnswerReq): ApiResult =
        HttpClient.request(pager, PATH_ANSWER, isPost = true, body = req.toJson())

    /**
     * 按月历史:data 为数组(ApiResult.Ok.dataArray)。
     * @param month yyyy-MM(如 2026-10),由调用方按当前展示月传入
     */
    suspend fun list(pager: Pager, month: String): ApiResult =
        HttpClient.request(pager, queryPath(PATH_LIST, "month" to month), isPost = false)
}
