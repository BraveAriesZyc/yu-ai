package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * ReportModels — 恋爱月报域响应模型(M30)
 * 用法:report/monthly 回包单个 VO 经 ReportMonthlyInfo.from 解析(topMoods 数组内联解析,
 *      LocationModels.points 嵌套数组同型)
 * 依赖:仅 core JSONObject 手写映射(§7.1:无序列化插件);ModelKit(jsonArrayToList)
 * 事件:无
 * 功能:
 *  - 单月五域只读聚合(打卡我/TA/共同天数 + 心情条数与 Top3 分布 + 悄悄话/相册/心愿完成计数)
 *    + 在一起天数;未绑定全 0(服务端静默口径)——全 0 也是合法月报,端上不设空态分支
 *  - 计数字段缺省按 0 解析(optLong 兜底,不做字段级失败);month 服务端解析后回填,
 *    端上不自行推导(month 为空判无效回包,调用方按失败态处理)
 *  - topMoods 服务端按 mood_label 快照聚合 Top3;emoji/color 为记录时快照,存量旧行可能为空,
 *    展示侧兜底(空 emoji 显 label 首字,空色走 colorFromHex fallback)
 *  - 全字段只读展示,无请求模型/无 toJson(listFrom 不适用:顶层是单对象非数组)
 */

/**
 * 心情分布单项(服务端 TopMood;label/emoji/color 为记录时快照,不受选项删除影响)
 */
internal data class TopMoodInfo(
    val label: String,
    val emoji: String,
    val color: String,
    val count: Long,
) {
    companion object {

        /** label 为空判无效条目返回 null(jsonArrayToList 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): TopMoodInfo? {
            if (data == null) return null
            val label = data.optString("label")
            if (label.isEmpty()) return null
            return TopMoodInfo(
                label = label,
                emoji = data.optString("emoji"),
                color = data.optString("color"),
                count = data.optLong("count"),
            )
        }
    }
}

/**
 * 恋爱月报(report/monthly 回包 VO,M30 契约)。
 * bindDays 含绑定当天;my/partner/togetherCheckinDays 与四计数均为「指定月」口径
 * (服务端 ge 月首零点 + lt 次月零点展开);count 均为本月事实计数,无跨月累计
 */
internal data class ReportMonthlyInfo(
    val month: String, // yyyy-MM,服务端解析后回填
    val bindDays: Int,
    val myCheckinDays: Long,
    val partnerCheckinDays: Long,
    val togetherCheckinDays: Long,
    val moodCount: Long,
    val topMoods: List<TopMoodInfo>,
    val messageCount: Long,
    val momentCount: Long,
    val wishDoneCount: Long,
) {
    companion object {

        /** month 为空判无效回包返回 null(data 缺省网络层兜底已挡,此处防御解析残包) */
        fun from(data: JSONObject?): ReportMonthlyInfo? {
            if (data == null) return null
            val month = data.optString("month")
            if (month.isEmpty()) return null
            return ReportMonthlyInfo(
                month = month,
                bindDays = data.optInt("bindDays"),
                myCheckinDays = data.optLong("myCheckinDays"),
                partnerCheckinDays = data.optLong("partnerCheckinDays"),
                togetherCheckinDays = data.optLong("togetherCheckinDays"),
                moodCount = data.optLong("moodCount"),
                topMoods = jsonArrayToList(data.optJSONArray("topMoods"), TopMoodInfo::from),
                messageCount = data.optLong("messageCount"),
                momentCount = data.optLong("momentCount"),
                wishDoneCount = data.optLong("wishDoneCount"),
            )
        }
    }
}
