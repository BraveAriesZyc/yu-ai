package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * MoodModels — 心情域请求/响应模型(INTEGRATION-DESIGN §5.2 mood 组、§4 love_mood_record、
 * D-14 M3.5 心情选项字典、§7.1 DTO 约定)
 * 用法:请求体 MoodRecordReq(...)/MoodOptionCreateReq(...)/MoodOptionDeleteReq(...).toJson() 传 MoodApi;
 *      月度列表响应 data 数组经 MoodRecordInfo.listFrom 解析,字典列表经 MoodOptionInfo.listFrom 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无(记录与字典增删统一落 state/MoodStore)
 * 功能:
 *  - record 组:record 记当日心情({"optionId":String},M3.5 由 moodType 改传选项 id)/list 按月拉
 *    couple 双方记录(month=yyyy-MM);记录附快照三列(moodLabel/emoji/color),月历渲染只吃快照——
 *    删除选项后历史不变灰(D-14,love_message 心愿快照同哲学)
 *  - option 组(M3.5):list 查 couple 私有字典/create 新增/delete 删除(写恒 POST)
 *  - id/optionId/userId 一律 String(§5.1,同 CoupleModels 惯例:optString 经 JSON.toString 对数字值
 *    回字符串,双向兼容)
 *  - ⚠ moodDate 按 String 原样存(§5.2:预期 yyyy-MM-dd,端上不做时区/格式换算),
 *    月份过滤与日格匹配由 Store 层做字符串比对
 *  - moodType Int 原样存为存量兜底(M3.5 起新记录恒 NULL→optInt 回 0):仅当记录缺 color 快照时
 *    按 1-5 旧映射取色(再灰点),常态渲染路径走快照
 *  - option 组错误码 1_003_002_xxx(M3.5 激活):000 不存在/001 无权/002 上限/003 重名,
 *    经普通 BizError 通道由调用方 toast
 */

// ---------- 请求体(§5.2,POST + application/json) ----------

/** 记当日心情(mood/record):传 couple 私有字典选项编号;同日重复记 = 服务端 uk_user_date 覆盖写(§4),端上按成功处理 */
internal data class MoodRecordReq(val optionId: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("optionId", optionId)
}

/** 新增心情选项(mood/option/create):label/emoji/color 原样上送(emoji/色板由端上预设集选定) */
internal data class MoodOptionCreateReq(val label: String, val emoji: String, val color: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("label", label)
        .put("emoji", emoji)
        .put("color", color)
}

/** 删除心情选项(mood/option/delete):传选项编号(app-api 写端点恒 POST body,core 仅 GET/POST) */
internal data class MoodOptionDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

// ---------- 响应模型 ----------

/**
 * 单日心情(mood/record/list 数组元素;§5.2 组 RespVO 契约 + M3.5 快照列只加字段)。
 * moodDate 为服务端原样字符串(预期 yyyy-MM-dd);moodDate 无记录的日期回包无条目,端上按空缺渲染;
 * 快照三列(moodLabel/emoji/color)记录时点定格——选项可能已被删除,渲染勿依赖字典回查
 */
internal data class MoodRecordInfo(
    val id: String,
    val userId: String,
    val moodDate: String,
    val moodType: Int, // 存量兜底:M3.5 起新记录恒 0(服务端 NULL);仅缺 color 快照时按 1-5 旧映射取色
    val optionId: String,
    val moodLabel: String,
    val emoji: String,
    val color: String,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): MoodRecordInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return MoodRecordInfo(
                id = id,
                userId = data.optString("userId"),
                moodDate = data.optString("moodDate"),
                moodType = data.optInt("moodType"),
                optionId = data.optString("optionId"),
                moodLabel = data.optString("moodLabel"),
                emoji = data.optString("emoji"),
                color = data.optString("color"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带)。
         * 数组缺省/为空返回空列表;条目顺序原样保留(排序语义归服务端)
         */
        fun listFrom(dataArray: JSONArray?): List<MoodRecordInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<MoodRecordInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}

/**
 * 心情选项(mood/option/list 数组元素与 create 回包;couple 私有字典,双方共享一份)。
 * color 为 hex 原样字符串(#RRGGBB),渲染时经 colorFromHex 映射并兜底(铁律⑥);
 * 排序归服务端(sort asc, id asc:播种行在前、双方新增按创建序落尾),端上按回包顺序渲染不重排
 */
internal data class MoodOptionInfo(
    val id: String,
    val label: String,
    val emoji: String,
    val color: String,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): MoodOptionInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return MoodOptionInfo(
                id = id,
                label = data.optString("label"),
                emoji = data.optString("emoji"),
                color = data.optString("color"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(mood/option/list);数组缺省/为空返回空列表(空字典=空态自愈路径);
         * create 回包为单对象,由 from 单独解析(Store.addOption 用回包落态拿服务端 id,防脏 key)
         */
        fun listFrom(dataArray: JSONArray?): List<MoodOptionInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<MoodOptionInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
