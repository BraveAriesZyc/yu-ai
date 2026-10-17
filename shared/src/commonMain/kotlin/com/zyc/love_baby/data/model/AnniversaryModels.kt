package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * AnniversaryModels — 纪念日域请求/响应模型(INTEGRATION-DESIGN §5.2 anniversary 组、§12.4 预留落位、M5/D-17)
 * 用法:请求体 XxxReq(...).toJson() 传 AnniversaryApi;列表响应 data 数组经 AnniversaryInfo.listFrom 解析,
 *      create 回包的单个 VO 经 AnniversaryInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - anniversary 组:list(全量,服务端按 anniv_date asc,id asc 排序,端上整表覆盖)/create/delete
 *    (无 update,D-17:删了重加)
 *  - ⚠ 写端点一律 POST(§5.2 user 组注记同款修正:core 2.29 NetworkModule 仅 GET/POST),delete 传 {"id"}
 *  - id/ownerId 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,optString 经
 *    JSON.toString 对数字值同样回字符串,双向兼容);createTime 为 epoch 毫秒(yudao LocalDateTime
 *    序列化),0 = 服务端未回传(与 TodoInfo 同款)
 *  - annivDate 为服务端原样 "yyyy-MM-dd" 字符串(yudao 全局 LocalDateSerializer 出参,AppMoodRespVO
 *    .moodDate 同机制);倒数算术全在端上(commonMain 禁 java.time,AppState.anniversaryDayIndex 纯函数),
 *    DTO 不持有任何时间/UI 类型(铁律⑥)
 *  - 错误码(1_003_006_xxx):000 纪念日不存在/001 无权操作(服务端 couple 归属校验防 IDOR)/002 日期
 *    格式非法——端上无差异化分支,统一走 BizError toast 服务端 msg(TodoModels 同款惯例)
 */

/** 新建纪念日(anniversary/create);annivDate "yyyy-MM-dd" 由服务端 @Pattern + LocalDate.parse 双重校验 */
internal data class AnniversaryCreateReq(
    val title: String,
    val annivDate: String,
    val repeatType: Int, // 1=每年重复 2=一次性(值域收口在服务端,非法打全局 400)
    val emoji: String,   // 端上预设字典选一,空串 = 未选(服务端 DEFAULT '')
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("annivDate", annivDate)
        .put("repeatType", repeatType)
        .put("emoji", emoji)
}

/** 删除纪念日(anniversary/delete;M3 实施修正 DELETE?id= → POST body,§5.2 todo 组同款) */
internal data class AnniversaryDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

// ---------- 响应模型 ----------

/**
 * 纪念日项(anniversary/list 数组元素与 create 回包 VO;§5.2 anniversary 组 RespVO 契约)。
 * id/ownerId String;annivDate 服务端原样 "yyyy-MM-dd";createTime epoch 毫秒,0 = 未回传
 */
internal data class AnniversaryInfo(
    val id: String,
    val title: String,
    val annivDate: String,
    val repeatType: Int,
    val emoji: String,
    val ownerId: String,
    val createTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): AnniversaryInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return AnniversaryInfo(
                id = id,
                title = data.optString("title"),
                annivDate = data.optString("annivDate"),
                repeatType = data.optInt("repeatType"),
                emoji = data.optString("emoji"),
                ownerId = data.optString("ownerId"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 anniversary/list)。
         * 数组缺省/为空返回空列表;服务端排序(anniv_date asc, id asc)原样保留,展示序由端上纯函数
         * sortedAnniversaries 重排(D-17),Store 不重排
         */
        fun listFrom(dataArray: JSONArray?): List<AnniversaryInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<AnniversaryInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
