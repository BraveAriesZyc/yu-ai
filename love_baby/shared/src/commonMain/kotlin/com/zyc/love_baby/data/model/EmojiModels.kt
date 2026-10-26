package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * EmojiModels — 聊天表情包域响应模型(表情面板分类字典,M25)
 * 用法:list 响应 data 数组经 EmojiInfo.listFrom 解析(启用行全量,端上按 category 聚合 tab)
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - emoji 组仅 list 单端点(GET 无参零请求体,本文件零 Req 类)
 *  - id String(§5.1 NumberSerializer 口径同 WishModels);category 运营可空,空串兜底
 *    (端上空归「常用」桶);emoji 为点选即插输入框的字符本体
 *  - 错误码:M25 app 端点零错误路径(池空端上走 ChatStickers 内置兜底字典),端上无差异化
 *    分支,失败统一走普通错误通道(刷新失败静默保留旧值,不打扰)
 */

/**
 * 表情项(emoji/list 数组元素)。id String 口径同 WishModels(§5.1);
 * category 运营可空,空串端上归「常用」桶
 */
internal data class EmojiInfo(
    val id: String,
    val category: String,
    val emoji: String,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): EmojiInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return EmojiInfo(
                id = id,
                category = data.optString("category"),
                emoji = data.optString("emoji"),
            )
        }

        /** 从 CommonResult.data 数组解析(Ok.dataArray 携带);空缺省返回空列表;服务端序原样保留 */
        fun listFrom(dataArray: JSONArray?): List<EmojiInfo> =
            jsonArrayToList(dataArray, ::from)
    }
}
