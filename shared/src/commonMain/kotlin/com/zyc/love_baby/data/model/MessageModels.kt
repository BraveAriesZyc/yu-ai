package com.zyc.love_baby.data.model

import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * MessageModels — 悄悄话/心愿消息域请求响应模型(INTEGRATION-DESIGN §5.2 message 组、§4 love_message、§7.1 DTO 约定)
 * 用法:请求体 XxxReq(...).toJson() 传 MessageApi;列表响应 data 数组经 MessageInfo.listFrom 解析,
 *      send/send-wishes 回包的单个消息 VO 经 MessageInfo.from 解析
 * 依赖:仅 core JSONObject/JSONArray 手写映射(§7.1:不动 shared/build.gradle.kts,无序列化插件)
 * 事件:无
 * 功能:
 *  - message 组:list 增量拉取(lastId 空串 = 首屏,服务端按 id 倒序取 limit 条后正序返回;有 lastId 则
 *    顺序扫 id > lastId 升序返回,§5.2)/send 文本悄悄话/send-wishes 心愿消息
 *  - send/send-wishes 回包 = 落库后的单个消息 VO:供 Store 层乐观替换本地临时消息并推进 lastId(§7.4)
 *  - ⚠ send-wishes 快照式(§5.2):wishKey/text/color/icon 四字段整体入 love_message,服务端不校验
 *    wish 归属(无 wish 实体,文案字典留端上 §7.4),两设备还原靠快照字段而非字典版本
 *  - id/senderId 一律 String(§5.1,同 CoupleModels 惯例:optString 经 JSON.toString 对数字值回字符串);
 *    kind Int(1 文本悄悄话/2 心愿消息/3 图片消息,比对常量 KIND_TEXT/KIND_WISH/KIND_IMAGE,
 *    渲染分支留 Store 层);
 *    wishKey/color/icon 可空,皆以空串表示缺省(CoupleModels 同款惯例),color 为 hex、icon 为 icon name
 *    字符串——Color/AppIconType 不可 JSON 序列化(§7.6 铁律⑥),映射留 Store 层;
 *    createTime 为 epoch 毫秒(yudao LocalDateTime 序列化),0 = 服务端未回传
 *  - 错误码(§6.4,1_003_003_xxx):000 消息不存在——端上无差异化分支,统一走 BizError toast,
 *    不设常量(AuthModels 同款惯例);未绑定时收到的 1_003_004_003 亦走普通错误通道
 *    (状态信号放行是 couple/get 专用,§5.2)
 */

// ---------- 请求体(§5.2,全部 POST + application/json) ----------

/** 发文本悄悄话(message/send),kind=1 */
internal data class MessageSendReq(val content: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("content", content)
}

/** 发心愿消息(message/send-wishes,快照式四字段整体入 love_message,kind=2,§5.2) */
internal data class MessageSendWishesReq(
    val wishKey: String,
    val text: String,
    val color: String,
    val icon: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("wishKey", wishKey)
        .put("text", text)
        .put("color", color)
        .put("icon", icon)
}

/** 发图片消息(message/send-image,kind=3,content 列存 URL,≤512,M10) */
internal data class MessageSendImageReq(val imageUrl: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("imageUrl", imageUrl)
}

/** 标记已读(message/read,M11):lastMessageId = 已读到的最大消息 id(服务端 GREATEST 只进不退;String 传 §5.1) */
internal data class MessageReadReq(val lastMessageId: String) {
    fun toJson(): JSONObject = JSONObject()
        .put("lastMessageId", lastMessageId)
}

// ---------- 响应模型 ----------

/**
 * 悄悄话/心愿消息(message/list 数组元素与 send、send-wishes 回包 VO;§5.2 message 组契约)。
 * id/senderId String;wishKey/color/icon 空串 = 缺省;createTime epoch 毫秒,0 = 未回传
 */
internal data class MessageInfo(
    val id: String,
    val senderId: String,
    val kind: Int,
    val content: String,
    val wishKey: String,
    val color: String,
    val icon: String,
    val createTime: Long,
) {
    companion object {

        /** kind 值域(§4 love_message.kind:1 文本悄悄话 / 2 心愿消息 / 3 图片消息);
         *  比对常量,渲染枚举映射留 Store 层 */
        const val KIND_TEXT = 1
        const val KIND_WISH = 2
        const val KIND_IMAGE = 3

        /** id 为空判无效条目返回 null(listFrom 逐条跳过,不整体失败) */
        fun from(data: JSONObject?): MessageInfo? {
            if (data == null) return null
            val id = data.optString("id")
            if (id.isEmpty()) return null
            return MessageInfo(
                id = id,
                senderId = data.optString("senderId"),
                kind = data.optInt("kind"),
                content = data.optString("content"),
                wishKey = data.optString("wishKey"),
                color = data.optString("color"),
                icon = data.optString("icon"),
                createTime = data.optLong("createTime"),
            )
        }

        /**
         * 从 CommonResult.data 数组解析(数组形态经 HttpClient 的 Ok.dataArray 携带,§5.2 message/list)。
         * 数组缺省/为空返回空列表;条目顺序原样保留(服务端恒升序:首屏倒序取后已正序,增量按 id > lastId 升序)
         */
        fun listFrom(dataArray: JSONArray?): List<MessageInfo> {
            if (dataArray == null) return emptyList()
            val result = ArrayList<MessageInfo>(dataArray.length())
            for (i in 0 until dataArray.length()) {
                from(dataArray.optJSONObject(i))?.let { result.add(it) }
            }
            return result
        }
    }
}
