package com.zyc.love_baby.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.DailyQuestionAnswerReq
import com.zyc.love_baby.data.model.DailyQuestionToday
import com.zyc.love_baby.network.ApiResult
import com.zyc.love_baby.network.DailyQuestionApi

/**
 * DailyQuestionStore — 每日一问今日态(M32,D-21)
 * 用法:首页「每日一问」条(bindState 首拉)与题目页共用 today;答题经 submit 同步刷新;
 *      历史列表不进 Store(题目页消费,页内自持 D-19g)
 * 依赖:DailyQuestionApi/DailyQuestionToday;首页按 CoupleStore.bindState 驱动首拉
 * 事件:reset 由 MainPager 登出/解绑两处清场 effect 接线(CheckinStore 同款)
 * 功能:
 *  - today 今日题态(题目/我的答案/对方是否已答/揭晓态):null = 未拉到或今日无题,
 *    条上显引导文案不挡入口;题目页进入时 loadToday 重拉校准(答题后回首页即时翻转)
 *  - submit 提交答案(覆盖写;已揭晓服务端回 1_003_016_001 走普通 BizError 由调用方 toast):
 *    成功用回包整对象覆盖 today——本次提交可能正是揭晓的那一答,条上状态随回包即时翻转
 */
internal object DailyQuestionStore {

    /** 今日题态;null = 尚未拉到或今日无题(调用方显引导/占位文案) */
    var today by mutableStateOf<DailyQuestionToday?>(null)
        private set

    /**
     * 拉取今日题(§7.4 触发点:首页 bindState 首拉 / 题目页进入):
     * 成功整对象覆盖(today 恒当天事实,无月缓存概念);失败静默保留旧值——只读降级,
     * toast 交给写失败场景(CheckinStore.loadSummary 同款)
     */
    suspend fun loadToday(pager: Pager): ApiResult {
        val result = DailyQuestionApi.today(pager)
        if (result is ApiResult.Ok) {
            today = DailyQuestionToday.from(result.data)
        }
        return result
    }

    /**
     * 提交今天的答案(揭晓前可覆盖写):成功用回包整对象覆盖 today
     */
    suspend fun submit(pager: Pager, content: String): ApiResult {
        val result = DailyQuestionApi.answer(pager, DailyQuestionAnswerReq(content))
        if (result is ApiResult.Ok) {
            today = DailyQuestionToday.from(result.data)
        }
        return result
    }

    /** 清场(登出/解绑;幂等):今日题态归 null */
    fun reset() {
        today = null
    }
}
