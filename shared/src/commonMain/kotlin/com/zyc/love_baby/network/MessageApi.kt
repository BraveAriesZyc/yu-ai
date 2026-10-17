package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.MessageSendImageReq
import com.zyc.love_baby.data.model.MessageSendReq
import com.zyc.love_baby.data.model.MessageSendWishesReq

/**
 * MessageApi — 悄悄话/心愿消息域接口(INTEGRATION-DESIGN §5.2 message 组,需登录)
 * 用法:协程内 val r = MessageApi.list(pager, lastId = ChatStore 记录的服务端最新消息 id);列表端点
 *      data 为数组,经 ApiResult.Ok.dataArray 携带(HttpClient M3 增量),MessageInfo.listFrom 解析;
 *      send/send-wishes 回单个消息 VO(MessageInfo.from)供乐观替换与 lastId 推进(§7.4)
 * 依赖:HttpClient(拆包/带 token/401 单飞刷新);MessageModels
 * 事件:无(会话消息统一落 state/ChatStore;partnerReply 本地假回复由 §7.4 增量拉取整体替换)
 * 功能:
 *  - list 增量拉取:lastId 空串 = 首屏(服务端按 id 倒序取 limit 条后正序返回);有 lastId = 顺序扫
 *    id > lastId 升序返回(§5.2,走 idx_couple_time);limit 固定 50(服务端默认 50、上限 200)
 *  - send 文本悄悄话 / send-wishes 心愿消息(快照式四字段,服务端不校验 wish 归属,§5.2):
 *    回包 = 落库后的单个消息 VO,Store 用它替换本地临时消息并推进 lastId
 *  - ⚠ 查询参数直接拼 URL 而非走 core param 通道(同 MoodApi 注记):lastId 为纯数字字符串或空串
 *    (首屏须显式 lastId= 传参)、limit 数字,均 URL 安全,显式拼接五端行为一致
 *  - 未绑定回 1_003_004_003 走普通 BizError 通道由调用方 toast;本层不做 successBizCodes 放行
 *    (1_003_004_003 的状态信号放行是 couple/get 专用,§5.2)
 */
internal object MessageApi {

    /** 增量拉取每页条数(§5.2:limit 默认 50、上限 200,端上固定取默认) */
    const val LIST_LIMIT = 50

    /** 心愿时间线单次取数上限(服务端钳制同 200;无游标,取最近 200 条,v1 足够) */
    const val WISH_LIST_LIMIT = 200

    private const val PATH_LIST = "/love/message/list"
    private const val PATH_WISH_LIST = "/love/message/wish-list"
    private const val PATH_SEND = "/love/message/send"
    private const val PATH_SEND_WISHES = "/love/message/send-wishes"
    private const val PATH_SEND_IMAGE = "/love/message/send-image"

    /**
     * 增量拉取:data 为数组(ApiResult.Ok.dataArray),服务端恒升序返回。
     * @param lastId 空串 = 首屏取最近一页;否则返回 id > lastId 的增量(Store 层以服务端最新消息 id 传入)
     * @param limit 每页条数,默认 [LIST_LIMIT]
     */
    suspend fun list(pager: Pager, lastId: String = "", limit: Int = LIST_LIMIT): ApiResult =
        HttpClient.request(pager, "$PATH_LIST?lastId=$lastId&limit=$limit", isPost = false)

    /**
     * 心愿时间线:kind=2 只读视图,服务端按 id 倒序(最新在前)返回,快照四字段(content/wishKey/color/icon)
     * 全量,MessageInfo.listFrom 直接解析(与聊天流解耦,不碰 ChatStore/lastId 游标)。
     * data 为数组走 ApiResult.Ok.dataArray;无游标,取最近 limit 条(升级路径=服务端补 beforeId 倒序游标)
     */
    suspend fun wishList(pager: Pager, limit: Int = WISH_LIST_LIMIT): ApiResult =
        HttpClient.request(pager, "$PATH_WISH_LIST?limit=$limit", isPost = false)

    /** 发文本悄悄话:回包为落库后的单个消息 VO(供乐观替换与 lastId 推进) */
    suspend fun send(pager: Pager, content: String): ApiResult =
        HttpClient.request(pager, PATH_SEND, isPost = true, body = MessageSendReq(content).toJson())

    /** 发心愿消息(快照式,§5.2):四字段整体入 love_message,两设备还原靠快照而非端上字典版本 */
    suspend fun sendWishes(
        pager: Pager,
        wishKey: String,
        text: String,
        color: String,
        icon: String,
    ): ApiResult =
        HttpClient.request(
            pager,
            PATH_SEND_WISHES,
            isPost = true,
            body = MessageSendWishesReq(wishKey, text, color, icon).toJson(),
        )

    /** 发图片消息(kind=3,content 列存 URL,M10):回包为落库后的单个消息 VO(供乐观替换与 lastId 推进) */
    suspend fun sendImage(pager: Pager, imageUrl: String): ApiResult =
        HttpClient.request(pager, PATH_SEND_IMAGE, isPost = true, body = MessageSendImageReq(imageUrl).toJson())
}
