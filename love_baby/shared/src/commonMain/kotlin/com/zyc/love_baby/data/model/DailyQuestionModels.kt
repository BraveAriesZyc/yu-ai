package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * DailyQuestionModels — 每日一问域模型(M32,D-21 答完自动互见)
 * 用法:today/answer 回包单对象经 DailyQuestionToday.from(今日无题返回 null,调用方按占位渲染);
 *      list 回包数组经 DailyQuestionItem.listFrom;请求模型 DailyQuestionAnswerReq(content)
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - 揭晓隐私口径:partnerAnswer 仅 revealed(双方都已提交)时非空——揭晓前对方答案不出接口
 *    (只给 partnerAnswered 布尔供状态文案),myAnswer 空串 = 未答(服务端恒定字符串契约,不发 null)
 *  - questionDate 为服务端原样 "yyyy-MM-dd" 字符串(yudao 全局 LocalDateSerializer 出参,
 *    CheckinInfo.checkinDate 同机制);DTO 不持有任何时间/UI 类型(铁律⑥)
 *  - 错误码:answer 已揭晓 1_003_016_001、题库空 1_003_016_000、未绑定 1_003_004_003——
 *    端上无差异化分支,统一走 BizError toast 服务端 msg(TodoModels 惯例)
 */

// ---------- 响应模型 ----------

/**
 * 今日题(today/answer 回包;answer 回包为提交后的 latest 态——本次提交可能正是揭晓的那一答)。
 * 今日无题(题库空/当日题未生成)from 返回 null,调用方显「今日无题」占位
 */
internal data class DailyQuestionToday(
    val questionDate: String,
    val content: String,
    val category: String,
    /** 空串 = 未答 */
    val myAnswer: String,
    val partnerAnswered: Boolean,
    /** 仅 revealed 非空 */
    val partnerAnswer: String,
    val revealed: Boolean,
) {
    companion object {

        /** content 空(今日无题)判无效返回 null,调用方按占位渲染 */
        fun from(data: JSONObject?): DailyQuestionToday? {
            if (data == null) return null
            val content = data.optString("content")
            if (content.isEmpty()) return null
            return DailyQuestionToday(
                questionDate = data.optString("questionDate"),
                content = content,
                category = data.optString("category"),
                myAnswer = data.optString("myAnswer"),
                partnerAnswered = data.optBoolean("partnerAnswered"),
                partnerAnswer = data.optString("partnerAnswer"),
                revealed = data.optBoolean("revealed"),
            )
        }
    }
}

/**
 * 历史条目(list 数组元素;字段形态与今日题一致,questionDate 为过去某天)。
 * 同揭晓隐私口径:错过没答的天对方答案永不揭晓(partnerAnswer 恒空)
 */
internal data class DailyQuestionItem(
    val questionDate: String,
    val content: String,
    val category: String,
    /** 空串 = 当天没答 */
    val myAnswer: String,
    val partnerAnswered: Boolean,
    /** 仅 revealed 非空 */
    val partnerAnswer: String,
    val revealed: Boolean,
) {
    companion object {

        /** content 空(理论不可达,day 行恒有题)判无效条目跳过,不整体失败 */
        fun from(data: JSONObject?): DailyQuestionItem? {
            if (data == null) return null
            val content = data.optString("content")
            if (content.isEmpty()) return null
            return DailyQuestionItem(
                questionDate = data.optString("questionDate"),
                content = content,
                category = data.optString("category"),
                myAnswer = data.optString("myAnswer"),
                partnerAnswered = data.optBoolean("partnerAnswered"),
                partnerAnswer = data.optString("partnerAnswer"),
                revealed = data.optBoolean("revealed"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带)。
         * 数组缺省/为空返回空列表;服务端排序(question_date asc)原样保留
         */
        fun listFrom(dataArray: JSONArray?): List<DailyQuestionItem> =
            jsonArrayToList(dataArray, ::from)
    }
}

// ---------- 请求模型 ----------

/**
 * 提交答案(answer 请求体)。不收日期/题目 id:只能答当天,服务端以服务端当天为准,
 * 无补答(D-21,防端上时钟作弊,AppCheckinController 口径)
 */
internal data class DailyQuestionAnswerReq(val content: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("content", content)
}
