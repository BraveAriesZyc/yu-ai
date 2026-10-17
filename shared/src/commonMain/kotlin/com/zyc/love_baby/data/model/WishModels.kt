package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * WishModels — 恋爱清单域请求/响应模型(INTEGRATION-DESIGN §5.2 wish 组、§4 love_wish,M14)
 * 用法:请求体 XxxReq(...).toJson() 传 WishApi;list 响应 data 数组经 WishInfo.listFrom 解析,
 *      create/done 回包的单个 VO 经 WishInfo.from 解析(done 回包 = 操作后服务端真值,D-19h 原位替换源)
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - wish 组:list(全量,服务端按 create_time asc,id asc 排序;端上「进行中在前/已完成沉底」
 *    分层排序在页面层做,D-19f)/create/done(单口双向:勾选发庆祝消息、取消清空)/delete
 *  - ⚠ 写端点一律 POST(§5.2:core 2.29 NetworkModule 仅 GET/POST,send-wishes 先例)
 *  - id/userId/doneBy 一律 String(§5.1:NumberSerializer 超 2^53-1 输出字符串、范围内是数字,
 *    optString 经 JSON.toString 对数字值同样回字符串,双向兼容);createTime/doneTime 为 epoch 毫秒
 *    (yudao LocalDateTime 序列化),0 = 服务端未回传(未完成时 doneBy=""/doneTime=0)
 *  - 错误码(§6.4,1_003_009_xxx):000 心愿不存在(不存在/跨 couple 统一,防探测)/001 仅创建者可删
 *    ——端上无差异化分支,统一走 BizError toast 服务端 msg,不设常量(AuthModels 同款惯例);
 *    未绑定时收到的 1_003_004_003 亦走普通错误通道(状态信号放行是 couple/get 专用,§5.2)
 */

// ---------- 请求体(§5.2,全部 POST + application/json) ----------

/** 新建心愿(wish/create);服务端固定初始未完成态,done/doneBy/doneTime 不收客户端传入 */
internal data class WishCreateReq(val title: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
}

/** 勾选完成/取消勾选(wish/done 单口双向,D-19b:done=true CAS 恰发一条庆祝消息) */
internal data class WishDoneReq(val id: String, val done: Boolean) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("done", done)
}

/** 删除心愿(wish/delete;仅创建者可删 1_003_009_001,端上只显不拦校验,服务端兜底) */
internal data class WishDeleteReq(val id: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
}

// ---------- 响应模型 ----------

/**
 * 心愿项(wish/list 数组元素与 create/done 回包 VO;§5.2 wish 组 RespVO 契约)。
 * id/userId/doneBy String;doneTime/createTime epoch 毫秒,0 = 未回传(未完成 doneBy=""/doneTime=0)
 */
internal data class WishInfo(
    val id: String,
    val userId: String,
    val title: String,
    val done: Boolean,
    val doneBy: String,
    val doneTime: Long,
    val createTime: Long,
) {
    companion object {

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): WishInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return WishInfo(
                id = id,
                userId = data.optString("userId"),
                title = data.optString("title"),
                done = data.optBoolean("done"),
                doneBy = data.optString("doneBy"),
                doneTime = data.optLong("doneTime"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 wish/list)。
         * 数组缺省/为空返回空列表;服务端序(create_time asc, id asc)原样保留,分层重排在页面层
         */
        fun listFrom(dataArray: JSONArray?): List<WishInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<WishInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
