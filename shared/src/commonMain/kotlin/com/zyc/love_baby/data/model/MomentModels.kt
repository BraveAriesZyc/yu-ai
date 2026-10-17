package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * MomentModels — 相册瞬间域请求/响应模型(INTEGRATION-DESIGN §5.2 moment 组、§12.4 预留落位、M9)
 * 用法:请求体 XxxReq(...).toJson() 传 MomentApi;list 响应 data 数组经 MomentInfo.listFrom 解析,
 *      create 回包的单个 VO 经 MomentInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - moment 组:list(游标分页 id 倒序新→旧,beforeId 游标 + limit 默认 30 上限 100)/create(图+可选
 *    一句话)/delete(仅上传者本人可删,1_003_008_001;无 update,删了重发 D-17 口径)
 *  - ⚠ 写端点一律 POST(§5.2 user 组注记:core 2.29 NetworkModule 仅 GET/POST),delete 传 {"id"}
 *  - imageUrl 为 infra 文件服务上传返回的完整 URL(M4 头像上传链路复用),服务端只存字符串不建存储
 *  - id/userId 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,optString 经
 *    JSON.toString 对数字值同样回字符串,双向兼容);createTime 为 epoch 毫秒(yudao LocalDateTime
 *    序列化),0 = 服务端未回传(与 AnniversaryInfo 同款)
 *  - text 空串 = 无配文(服务端 NOT NULL DEFAULT '',null/空白统一落空串,端上按空串判不判 null)
 *  - 错误码(1_003_008_xxx):000 瞬间不存在/001 仅上传者可删——端上无差异化分支,统一走 BizError
 *    toast 服务端 msg(AnniversaryModels 同款惯例)
 */

/** 发布瞬间(moment/create);imageUrl 为 pickAndUpload 回包 data(端上选图即上传后已持有) */
internal data class MomentCreateReq(
    val imageUrl: String,
    val text: String, // 可空配文,空串 = 无配文(服务端 trim 后落库)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("imageUrl", imageUrl)
        .put("text", text)
}

/** 删除瞬间(moment/delete;仅上传者本人,POST body 同 todo 组口径) */
internal data class MomentDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

// ---------- 响应模型 ----------

/**
 * 瞬间项(moment/list 数组元素与 create 回包 VO;§5.2 moment 组 RespVO 契约)。
 * id/userId String;imageUrl 服务端原样 URL;createTime epoch 毫秒,0 = 未回传
 */
internal data class MomentInfo(
    val id: String,
    val userId: String,
    val imageUrl: String,
    val text: String,
    val createTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): MomentInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return MomentInfo(
                id = id,
                userId = data.optString("userId"),
                imageUrl = data.optString("imageUrl"),
                text = data.optString("text"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 moment/list)。
         * 数组缺省/为空返回空列表;服务端排序(id desc 新→旧)原样保留,调用方不重排
         */
        fun listFrom(dataArray: JSONArray?): List<MomentInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<MomentInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
