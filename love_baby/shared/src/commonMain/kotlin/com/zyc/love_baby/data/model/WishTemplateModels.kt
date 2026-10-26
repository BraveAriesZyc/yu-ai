package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * WishTemplateModels — 心愿墙挑选模板域响应模型(心情页心愿墙挑选列表数据源,M25)
 * 用法:list 响应 data 数组经 WishTemplateInfo.listFrom 解析(启用行全量,端上按 category 聚合 chips)
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - wishTemplate 组仅 list 单端点(GET 无参零请求体,本文件零 Req 类)
 *  - id String(§5.1 NumberSerializer 口径同 WishModels)即送出 wishKey(kind=2 快照式
 *    上送四字段,服务端不校验、渲染不查本表——删/停模板不影响历史消息);subtitle 运营可空
 *  - 错误码:M25 app 端点零错误路径(池空端上走 allWishes 内置兜底),端上无差异化分支,
 *    失败统一走普通错误通道(刷新失败静默保留旧值,不打扰)
 */

/**
 * 心愿模板项(wish-template/list 数组元素)。id String 口径同 WishModels(§5.1),即 wishKey;
 * category/subtitle 运营可空(category 空串端上归「其他」桶,subtitle 空串卡上不渲染第二行);
 * colorStart/colorEnd 为 hex 字符串,icon 为端上 AppIconType 枚举 name(未知名端上兜底 Heart)
 */
internal data class WishTemplateInfo(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val colorStart: String,
    val colorEnd: String,
    val icon: String,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): WishTemplateInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return WishTemplateInfo(
                id = id,
                category = data.optString("category"),
                title = data.optString("title"),
                subtitle = data.optString("subtitle"),
                colorStart = data.optString("colorStart"),
                colorEnd = data.optString("colorEnd"),
                icon = data.optString("icon"),
            )
        }

        /** 从 CommonResult.data 数组解析(Ok.dataArray 携带);空缺省返回空列表;服务端序原样保留 */
        fun listFrom(dataArray: JSONArray?): List<WishTemplateInfo> =
            jsonArrayToList(dataArray, ::from)
    }
}
