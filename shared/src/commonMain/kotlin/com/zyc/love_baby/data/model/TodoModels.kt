package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * TodoModels — 待办域请求/响应模型(INTEGRATION-DESIGN §5.2 todo 组、§4 love_todo、§7.1 DTO 约定)
 * 用法:请求体 XxxReq(...).toJson() 传 TodoApi;列表响应 data 数组经 TodoInfo.listFrom 解析,
 *      create/update 回包的单个 VO 经 TodoInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - todo 组:list(全量,服务端按 create_time asc,id asc 排序,端上整表覆盖)/create/update/delete
 *  - ⚠ 写端点一律 POST(§5.2 user 组注记同款修正:core 2.29 NetworkModule 仅 GET/POST,
 *    原 PUT/DELETE 已于 M3 改 POST body,§5.2 todo 组表)
 *  - id/ownerId 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,optString 经
 *    JSON.toString 对数字值同样回字符串,双向兼容);createTime 为 epoch 毫秒(yudao LocalDateTime
 *    序列化),0 = 服务端未回传(与 CoupleModels.bindTime 同款)
 *  - ⚠ color 仅存 hex 字符串(§4 love_todo.color):Kuikly Color 不可 JSON 序列化(§7.6 铁律⑥),
 *    Color ↔ hex 双向映射留 Store 层,DTO 不持有任何 UI 类型
 *  - 错误码(§6.4,1_003_001_xxx):000 待办不存在/001 无权操作该待办(服务端按 id 写操作强制 couple
 *    归属校验防 IDOR)——端上无差异化分支,统一走 BizError toast 服务端 msg,不设常量(AuthModels 同款
 *    惯例);未绑定时收到的 1_003_004_003 亦走普通错误通道(状态信号放行是 couple/get 专用,§5.2)
 */

// ---------- 请求体(§5.2,全部 POST + application/json) ----------

/** 新建待办(todo/create);color 为标签胶囊的 hex 色值,默认值由调用方(Store 层)给 */
internal data class TodoCreateReq(val title: String, val tag: String, val color: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("tag", tag)
        .put("color", color)
}

/**
 * 整条更新(todo/update,含 toggle done;M3 实施修正 PUT → POST,§5.2 todo 组表)。
 * 五字段全量上送:编辑语义 = 用端上当前整项覆盖服务端(last-write,§7.4 D-6),
 * 不做字段级合并
 */
internal data class TodoUpdateReq(
    val id: String,
    val title: String,
    val done: Boolean,
    val tag: String,
    val color: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("done", done)
        .put("tag", tag)
        .put("color", color)
}

/** 删除待办(todo/delete;M3 实施修正 DELETE?id= → POST body,§5.2 todo 组表) */
internal data class TodoDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

// ---------- 响应模型 ----------

/**
 * 待办项(todo/list 数组元素与 create/update 回包 VO;§5.2 todo 组 RespVO 契约)。
 * id/ownerId String;color 为 hex 字符串(Color 映射留 Store 层,§7.6 铁律⑥);createTime epoch 毫秒,0 = 未回传
 */
internal data class TodoInfo(
    val id: String,
    val title: String,
    val done: Boolean,
    val tag: String,
    val color: String,
    val ownerId: String,
    val createTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): TodoInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return TodoInfo(
                id = id,
                title = data.optString("title"),
                done = data.optBoolean("done"),
                tag = data.optString("tag"),
                color = data.optString("color"),
                ownerId = data.optString("ownerId"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 todo/list)。
         * 数组缺省/为空返回空列表;服务端排序(create_time asc, id asc)原样保留,端上整表覆盖不重排
         */
        fun listFrom(dataArray: JSONArray?): List<TodoInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<TodoInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
