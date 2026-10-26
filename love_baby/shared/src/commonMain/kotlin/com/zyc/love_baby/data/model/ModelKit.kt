package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * jsonArrayToList — model 层数组解析单源:逐项经 parse 试解析,null 项跳过不占位。
 * 用法:各域 model companion 的 listFrom 单行委托 jsonArrayToList(dataArray, ::from),
 *      companion 对外签名不变;parse 即各域自己的 from(JSONObject?)->T? 手写映射
 * 依赖:仅 core JSONObject/JSONArray(与各 model 同款,§7.1:无序列化插件)
 * 事件:无(纯函数)
 * 功能:语义与原 13 份同体手写逐字一致——dataArray 为 null/空返回 emptyList;parse 回 null
 *      的条目(id 缺失等无效条目)跳过不整体失败
 */
internal fun <T> jsonArrayToList(dataArray: JSONArray?, parse: (JSONObject?) -> T?): List<T> {
    if (dataArray == null) return emptyList()
    val result = ArrayList<T>(dataArray.length())
    for (i in 0 until dataArray.length()) {
        parse(dataArray.optJSONObject(i))?.let { result.add(it) }
    }
    return result
}
