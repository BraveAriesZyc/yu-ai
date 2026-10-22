package com.zyc.love_baby.network

import com.tencent.kuikly.core.pager.Pager
import com.zyc.love_baby.data.model.MessageReadReq
import com.zyc.love_baby.data.model.MessageRecallReq
import com.zyc.love_baby.data.model.MessageSendCardReq
import com.zyc.love_baby.data.model.MessageSendImageReq
import com.zyc.love_baby.data.model.MessageSendReq
import com.zyc.love_baby.data.model.MessageSendStickerReq
import com.zyc.love_baby.data.model.MessageSendVideoReq
import com.zyc.love_baby.data.model.MessageSendVoiceReq
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
 *    id > lastId 升序返回(§5.2,走 idx_couple_time);beforeId>0 = 历史前翻(M19,优先于 lastId);
 *    limit 固定 50(服务端默认 50、上限 200)
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
    private const val PATH_SEND_VOICE = "/love/message/send-voice" // M23
    private const val PATH_SEND_VIDEO = "/love/message/send-video" // M23
    private const val PATH_SEND_STICKER = "/love/message/send-sticker" // M23
    private const val PATH_SEND_CARD = "/love/message/send-card" // M23
    private const val PATH_UNREAD = "/love/message/unread"
    private const val PATH_READ = "/love/message/read"
    private const val PATH_RECALL = "/love/message/recall"
    private const val PATH_TYPING = "/love/message/typing"

    /**
     * 增量拉取:data 为数组(ApiResult.Ok.dataArray),服务端恒升序返回。
     * @param lastId 空串 = 首屏取最近一页;否则返回 id > lastId 的增量(Store 层以服务端最新消息 id 传入)
     * @param beforeId 历史前翻游标(M19 下拉加载更早):取 id 小于该值的更早一页,服务端正序返回;
     *        >0 时优先于 lastId(两游标语义不同向);空串 = 服务端按 0 处理不启用
     * @param limit 每页条数,默认 [LIST_LIMIT]
     */
    suspend fun list(pager: Pager, lastId: String = "", limit: Int = LIST_LIMIT, beforeId: String = ""): ApiResult =
        HttpClient.request(pager, "$PATH_LIST?lastId=$lastId&beforeId=$beforeId&limit=$limit", isPost = false)

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

    /** 发语音消息(kind=4,M23):content 快照 {"url","dur"} 由服务端组装;回包语义同 sendImage */
    suspend fun sendVoice(pager: Pager, voiceUrl: String, duration: Int): ApiResult =
        HttpClient.request(pager, PATH_SEND_VOICE, isPost = true, body = MessageSendVoiceReq(voiceUrl, duration).toJson())

    /** 发视频消息(kind=5,M23):content 快照 {"url","cover","dur"},cover 可空串;回包语义同 sendImage */
    suspend fun sendVideo(pager: Pager, videoUrl: String, coverUrl: String, duration: Int): ApiResult =
        HttpClient.request(pager, PATH_SEND_VIDEO, isPost = true, body = MessageSendVideoReq(videoUrl, coverUrl, duration).toJson())

    /** 发贴纸消息(kind=6,M23):content = key 明文(快照式,服务端不校验字典);回包语义同 sendImage */
    suspend fun sendSticker(pager: Pager, stickerKey: String): ApiResult =
        HttpClient.request(pager, PATH_SEND_STICKER, isPost = true, body = MessageSendStickerReq(stickerKey).toJson())

    /** 发自定义卡片消息(kind=7,M23):content 快照 {"type","title","sub","icon"};回包语义同 sendImage */
    suspend fun sendCard(
        pager: Pager,
        cardType: String,
        title: String,
        sub: String,
        icon: String,
    ): ApiResult =
        HttpClient.request(
            pager,
            PATH_SEND_CARD,
            isPost = true,
            body = MessageSendCardReq(cardType, title, sub, icon).toJson(),
        )

    /**
     * 聊天未读数(红点口径,M11):回包 data 为对象 {"unread":n}。
     * ⚠ 对象形态契约——服务端禁改回 CommonResult<Long> 标量(toOk 只分派对象/数组,标量落空对象)
     */
    suspend fun unread(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_UNREAD, isPost = false)

    /** 标记已读(M11):服务端 GREATEST 只进不退;回包标量 true 经 toOk 归空对象,调用方只判 Ok 不读 data */
    suspend fun markRead(pager: Pager, lastMessageId: String): ApiResult =
        HttpClient.request(pager, PATH_READ, isPost = true, body = MessageReadReq(lastMessageId).toJson())

    /**
     * 撤回消息(M12):服务端权威校验归属(couple+sender,统一 1_003_003_000 防探测)与 2 分钟窗
     * (超窗 1_003_003_001);已撤回幂等成功。回包标量 true 经 toOk 归空对象,调用方只判 Ok 不读 data
     */
    suspend fun recall(pager: Pager, messageId: String): ApiResult =
        HttpClient.request(pager, PATH_RECALL, isPost = true, body = MessageRecallReq(messageId).toJson())

    /**
     * 上报「正在输入」信号(M13,D-18):**无请求体**(HttpClient 缺省发空对象,create-invite/unbind
     * 先例),服务端不落库、经 WS 中继给对方在线连接,对方离线静默 no-op。回包标量 true 经 toOk 归
     * 空对象,调用方只判 Ok 不读 data;节流在 ChatStore.noteTyping(3s),本层原样透传
     */
    suspend fun typing(pager: Pager): ApiResult =
        HttpClient.request(pager, PATH_TYPING, isPost = true)
}
