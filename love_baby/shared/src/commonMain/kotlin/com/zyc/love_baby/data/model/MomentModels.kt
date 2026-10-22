package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * MomentModels — 相册瞬间域请求/响应模型(INTEGRATION-DESIGN §5.2 moment 组、§12.4 预留落位,M9;M15 多图 + 点赞扩展)
 * 用法:请求体 XxxReq(...).toJson() 传 MomentApi;list 响应 data 数组经 MomentInfo.listFrom 解析,
 *      create 回包的单个 VO 经 MomentInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - moment 组:list(游标分页 id 倒序新→旧,beforeId 游标 + limit 默认 30 上限 100)/create(多图 +
 *    可选一句话)/delete(仅上传者本人可删,1_003_008_001;无 update,删了重发 D-17 口径)/like(点赞
 *    取消单口幂等 toggle,M15)
 *  - ⚠ 写端点一律 POST(§5.2 user 组注记:core 2.29 NetworkModule 仅 GET/POST),delete/like 传 body
 *  - imageUrl 为 infra 文件服务上传返回的完整 URL(M4 头像上传链路复用),服务端只存字符串不建存储;
 *    M15 起 imageUrls 为多图列表(≤9,服务端归一恒非空,存量单图 = [imageUrl]),imageUrl 语义收敛
 *    为首图冗余(端上渲染一律走 imageUrls)
 *  - 点赞不发悄悄话消息、无 WS 信号(D-20c 用户裁决)——对方点赞/增删的感知靠相册页可见期 5s 静默
 *    轮询对账(MomentPage fetchSilentMerge);like 回包 = 操作后服务端真值 {likeCount, liked},
 *    端上按 id 原位替换(D-20e,禁端上乐观推导)
 *  - id/userId 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,optString 经
 *    JSON.toString 对数字值同样回字符串,双向兼容);createTime 为 epoch 毫秒(yudao LocalDateTime
 *    序列化),0 = 服务端未回传(与 AnniversaryInfo 同款)
 *  - text 空串 = 无配文(服务端 NOT NULL DEFAULT '',null/空白统一落空串,端上按空串判不判 null)
 *  - 错误码(1_003_008_xxx):000 瞬间不存在/001 仅上传者可删——端上无差异化分支,统一走 BizError
 *    toast 服务端 msg(AnniversaryModels 同款惯例);like 复用 000(不存在/跨 couple 统一,防探测
 *    D-20d,零新增错误码)
 */

/** 发布瞬间(moment/create;M15 多图):imageUrl 仍传首张(满足服务端 @NotBlank 兼容),imageUrls 为全列表 */
internal data class MomentCreateReq(
    val imageUrl: String,
    val imageUrls: List<String>,
    val text: String, // 可空配文,空串 = 无配文(服务端 trim 后落库)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("imageUrl", imageUrl)
        .put("imageUrls", JSONArray().apply { imageUrls.forEach { put(it) } })
        .put("text", text)
}

/** 删除瞬间(moment/delete;仅上传者本人,POST body 同 todo 组口径) */
internal data class MomentDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

/** 点赞/取消瞬间(moment/like;M15,单口幂等,liked=true 点赞/false 取消) */
internal data class MomentLikeReq(val id: String, val liked: Boolean) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("liked", liked)
}

// ---------- 响应模型 ----------

/**
 * 瞬间项(moment/list 数组元素与 create 回包 VO;§5.2 moment 组 RespVO 契约)。
 * id/userId String;imageUrl 首图冗余;imageUrls 恒非空(服务端归一保证,端上兜底 [imageUrl]
 * 兼容老服务端);likeCount/liked M15 组装字段,均带默认值——默认值参与 data class equals,
 * 轮询对账(fetchSilentMerge)靠结构相等跳过无谓替换;createTime epoch 毫秒,0 = 未回传
 */
internal data class MomentInfo(
    val id: String,
    val userId: String,
    val imageUrl: String,
    val imageUrls: List<String> = emptyList(),
    val text: String,
    val createTime: Long,
    val likeCount: Int = 0,
    val liked: Boolean = false,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): MomentInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            val imageUrl = data.optString("imageUrl")
            // 多图:逐项收集非空 URL;回包缺该字段/为空(老服务端)兜底 [imageUrl]
            val urls = ArrayList<String>()
            data.optJSONArray("imageUrls")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val u = arr.optString(i)
                    if (!u.isNullOrEmpty()) urls.add(u)
                }
            }
            if (urls.isEmpty() && imageUrl.isNotEmpty()) urls.add(imageUrl)
            return MomentInfo(
                id = id,
                userId = data.optString("userId"),
                imageUrl = imageUrl,
                imageUrls = urls,
                text = data.optString("text"),
                createTime = data.optLong("createTime"),
                likeCount = data.optInt("likeCount"),
                liked = data.optBoolean("liked"),
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
