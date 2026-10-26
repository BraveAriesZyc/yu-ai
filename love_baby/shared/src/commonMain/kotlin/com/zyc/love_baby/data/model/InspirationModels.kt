package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * InspirationModels — 恋爱灵感域响应模型(首页「今日情话」「约会灵感」两卡,M17)
 * 用法:today/quote-random 回包单个 VO 经 InspirationQuote.from 解析(仅 content 一字段);
 *      list 响应 data 数组经 InspirationInfo.listFrom 解析(约会灵感全量,端上分类筛选+换一换打乱)
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - inspiration 组全部只读无请求体(三端点全 GET 无参,本文件零 Req 类)
 *  - id String(§5.1 NumberSerializer 口径同 WishModels);kind 恒 2(仅约会灵感走 list,
 *    情话走 quote 两端点只回 content);title/category 可空运营未填,端上空串兜底
 *  - 错误码:M17 app 三端点零错误路径(池空服务端兜底文案),端上无差异化分支,
 *    失败统一走普通错误通道 toast(同 AuthModels 惯例)
 */

/** 今日情话/换一句 回包 VO(today、quote-random 两端点共用,恒对象形态 M11 冻结口径) */
internal data class InspirationQuote(val content: String) {
    companion object {
        fun from(data: JSONObject?): InspirationQuote? {
            if (data == null) return null
            val content = data.optString("content")
            if (content.isEmpty()) return null
            return InspirationQuote(content)
        }
    }
}

/**
 * 约会灵感项(inspiration/list 数组元素;仅服务 kind=2)。
 * id String 口径同 WishModels(§5.1);title/category 运营可空,空串兜底(端上 category 空归「其他」)
 */
internal data class InspirationInfo(
    val id: String,
    val title: String,
    val content: String,
    val category: String,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): InspirationInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return InspirationInfo(
                id = id,
                title = data.optString("title"),
                content = data.optString("content"),
                category = data.optString("category"),
            )
        }

        /** 从 CommonResult.data 数组解析(Ok.dataArray 携带);空缺省返回空列表;服务端序原样保留 */
        fun listFrom(dataArray: JSONArray?): List<InspirationInfo> =
            jsonArrayToList(dataArray, ::from)
    }
}
