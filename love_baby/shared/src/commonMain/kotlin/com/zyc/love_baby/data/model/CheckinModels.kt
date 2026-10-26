package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * CheckinModels — 每日打卡域响应模型(INTEGRATION-DESIGN §5.2 checkin 组、§12.4 预留落位、M8)
 * 用法:check 回包单个 VO 经 CheckinInfo.from 解析;list 响应 data 数组经 CheckinInfo.listFrom 解析;
 *      summary 回包经 CheckinSummaryInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - checkin 组:check(打卡今天,无请求模型——服务端以服务端当天为准,无补打)/list(按月,
 *    couple 双方记录)/summary(共同连续 + 双方全量累计,服务端聚合)
 *  - 无请求模型:check 不收任何参数(防端上时钟作弊,补打不在产品范围,M8 冻结)
 *  - id/userId 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,optString 经
 *    JSON.toString 对数字值同样回字符串,双向兼容);createTime 为 epoch 毫秒(yudao LocalDateTime
 *    序列化),0 = 服务端未回传(TodoInfo 同款)
 *  - checkinDate 为服务端原样 "yyyy-MM-dd" 字符串(yudao 全局 LocalDateSerializer 出参,
 *    AnniversaryInfo.annivDate 同机制);DTO 不持有任何时间/UI 类型(铁律⑥)
 *  - 错误码:check 无独立错误码(uk_user_date 幂等回已有记录,不打错);未绑定 1_003_004_003、
 *    admin 删不存在 1_003_007_000——端上无差异化分支,统一走 BizError toast 服务端 msg(TodoModels 惯例)
 */

// ---------- 响应模型 ----------

/**
 * 打卡项(checkin/check 回包 VO 与 checkin/list 数组元素;§5.2 checkin 组 RespVO 契约)。
 * id/userId String;checkinDate 服务端原样 "yyyy-MM-dd";createTime epoch 毫秒,0 = 未回传
 */
internal data class CheckinInfo(
    val id: String,
    val userId: String,
    val checkinDate: String,
    val createTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): CheckinInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return CheckinInfo(
                id = id,
                userId = data.optString("userId"),
                checkinDate = data.optString("checkinDate"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 checkin/list)。
         * 数组缺省/为空返回空列表;服务端排序(checkin_date asc, id asc)原样保留,Store 不重排
         */
        fun listFrom(dataArray: JSONArray?): List<CheckinInfo> =
            jsonArrayToList(dataArray, ::from)
    }
}

/**
 * 打卡汇总(checkin/summary 回包;首页「今日打卡」条与打卡页页头共用)。
 * commonStreak = 共同连续天数(双方同日都打才累计,今天缺一方从昨天起算,服务端聚合);
 * 0 = 还没有共同连续;myTotal/partnerTotal = 双方全量(跨月)累计天数
 */
internal data class CheckinSummaryInfo(
    val commonStreak: Int,
    val myTotal: Int,
    val partnerTotal: Int,
) {
    companion object {

        /** summary 无主键字段,不做 id 判空;data 缺省(网络层兜底已挡)按 null 交调用方保留旧值 */
        fun from(data: JSONObject?): CheckinSummaryInfo? {
            if (data == null) return null
            return CheckinSummaryInfo(
                commonStreak = data.optInt("commonStreak"),
                myTotal = data.optInt("myTotal"),
                partnerTotal = data.optInt("partnerTotal"),
            )
        }
    }
}
