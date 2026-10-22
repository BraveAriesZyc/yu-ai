package cn.iocoder.yudao.module.love.service.message;

import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageReadReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRecallReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRespVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendCardReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendImageReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendStickerReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendVideoReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendVoiceReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendWishesReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageUnreadRespVO;

import java.util.List;

/**
 * 悄悄话/心愿消息 Service 接口
 *
 * 端上契约见 INTEGRATION-DESIGN.md §5.2 message 组：增量拉取（lastId 游标只增不减，D-12）、
 * 发送（kind=1 文本 / kind=2 心愿快照 / kind=3 图片 URL）。消息域只有「按自己 couple 收发」，
 * 无客户端传 id，天然防 IDOR——M12 起 recall 为唯一按客户端 id 的 app 写入口，service 层
 * couple 归属 + sender 本人双校验防 IDOR（不存在/跨 couple/非本人统一按 1_003_003_000，不泄露存在性）。
 *
 * @author 芋道源码
 */
public interface MessageService {

    /**
     * 增量拉取消息列表（恒升序返回）
     *
     * beforeId &gt; 0 = 历史前翻（id &lt; beforeId 倒序取一页反转正序，M19）；空参 lastId = 首屏取最近一页
     * （id 倒序取 limit 条后反转为正序）；有 lastId = 顺序扫 id &gt; lastId 升序；beforeId 优先于 lastId
     *
     * @param lastId   上次拉取的最大消息 id（空 = 首屏）
     * @param beforeId 历史前翻游标（M19：取 id 小于该值的更早一页；0/空 = 不启用）
     * @param limit    条数上限（默认 50、钳制上限 200，§5.2）
     * @return 消息列表（正序）
     */
    List<AppMessageRespVO> getMessageList(Long lastId, Long beforeId, Integer limit);

    /**
     * 心愿时间线（kind=2 只读视图，按 id 倒序最新在前）：与聊天流解耦的查看入口——
     * 聊天 lastId 游标只进不退 + 首屏截断，历史心愿无法回溯，故单开全量(限 limit)时间线端点。
     * 无游标：v1 取最近 limit 条，将来按「只加不改」补 beforeId 倒序游标即可平滑升级
     *
     * @param limit 条数上限（默认 50、钳制上限 200，§5.2）
     * @return 心愿消息列表（倒序，快照四字段全量回传）
     */
    List<AppMessageRespVO> getWishList(Integer limit);

    /**
     * 发送文本悄悄话（kind=1）：sender = 当前用户，receiver = couple 对方，audit_status 恒写 1
     *
     * @param reqVO 文本内容（trim 后非空、≤500）
     * @return 落库后的完整消息 VO（含 id，供端上乐观替换本地临时消息并推进 lastId，§7.4）
     */
    AppMessageRespVO sendMessage(AppMessageSendReqVO reqVO);

    /**
     * 发送心愿消息（kind=2，快照式）：wishKey/text/color/icon 四字段整体入 love_message，
     * 服务端不校验 wish 归属（无 wish 实体，文案字典留端上），两设备还原靠快照字段（§5.2）
     *
     * @param reqVO 心愿快照四字段
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendWishes(AppMessageSendWishesReqVO reqVO);

    /**
     * 发送图片消息（kind=3）：content 列存图片完整 URL（infra 文件服务上传返回，≤512）；
     * 发送方/接收方/audit_status/回包语义同 {@link #sendMessage}，错误码零新增（M10）
     *
     * @param reqVO 图片 URL
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendImage(AppMessageSendImageReqVO reqVO);

    /**
     * 发送语音消息（kind=4，M23）：content 列存 JSON 快照 {"url":<语音URL>,"dur":<秒>}（服务端组装，
     * 组装后 >512 抛 BAD_REQUEST 兜底）；发送方/接收方/audit_status/回包语义同 {@link #sendMessage}，
     * 错误码零新增
     *
     * @param reqVO 语音 URL + 时长秒
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendVoice(AppMessageSendVoiceReqVO reqVO);

    /**
     * 发送视频消息（kind=5，M23）：content 列存 JSON 快照 {"url":<视频URL>,"cover":<封面URL>,"dur":<秒>}
     * （cover 可空 = 缺省，端上灰底兜底）；发送方/接收方/audit_status/回包语义同 {@link #sendMessage}，
     * 错误码零新增
     *
     * @param reqVO 视频 URL + 封面 URL（可空）+ 时长秒
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendVideo(AppMessageSendVideoReqVO reqVO);

    /**
     * 发送贴纸消息（kind=6，M23）：content 列存贴纸 key 明文（快照式同 kind=2 wishKey——服务端
     * 不校验字典归属，端上按 key 对齐同版本内置字典渲染，未知 key 端上占位降级）；
     * 发送方/接收方/audit_status/回包语义同 {@link #sendMessage}，错误码零新增
     *
     * @param reqVO 贴纸 key（≤64）
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendSticker(AppMessageSendStickerReqVO reqVO);

    /**
     * 发送自定义卡片消息（kind=7，M23）：content 列存 JSON 快照 {"type":<卡片类型>,"title":<标题>,
     * "sub":<副文案>,"icon":<icon name>}；cardType 服务端不做白名单校验（快照哲学，端上按 type 分派
     * 渲染，首期仅 wishWall）；发送方/接收方/audit_status/回包语义同 {@link #sendMessage}，错误码零新增
     *
     * @param reqVO 卡片类型 + 标题/副文案/图标快照
     * @return 落库后的完整消息 VO（含 id，语义同 {@link #sendMessage}）
     */
    AppMessageRespVO sendCard(AppMessageSendCardReqVO reqVO);

    /**
     * 聊天未读数（M11 红点口径）：couple 内 id &gt; 我方 chat_read_cursor 且非本人发送，
     * kind 1/2/3 全计，admin 逻辑删自动不计；未绑定返回 unread=0（stats 先例，只读旁路不报错）
     *
     * @return 未读数 VO（对象形态——端上 HttpClient toOk 标量落空对象，禁改回标量回包）
     */
    AppMessageUnreadRespVO getUnreadCount();

    /**
     * 标记聊天已读（M11）：GREATEST 只进不退推进我方游标，并发/迟到标记不回退；
     * 未绑定 1_003_004_003（发送端点同口径）；不校验消息归属（自伤无动机，从简）
     *
     * @param reqVO 已读到的最大消息 id
     */
    void markRead(AppMessageReadReqVO reqVO);

    /**
     * 撤回消息（M12）：发送方 2 分钟内可撤回自己发的消息（kind 1/2/3 均可）。
     * 校验链：存在 + couple 归属 + sender 本人（统一 1_003_003_000 防探测）→ 已撤回幂等成功 →
     * 时间窗（服务端时钟权威，1_003_003_001）→ CAS 置位墓碑。撤回不删行，content 留档审计；
     * 成功后向对方在线连接推 love-message-recall 信号（携 messageId）
     *
     * @param reqVO 待撤回消息 id
     */
    void recallMessage(AppMessageRecallReqVO reqVO);

    /**
     * 上报「正在输入」信号（M13，D-18）：纯中继，无请求体、不落库、无持久化；
     * 校验链 getValidatedCurrentUser → getValidatedCouple 后经 WS 向对方在线连接推
     * love-typing 纯信号，对方离线/未桥接静默 no-op；节流在端上（3s），服务端不做限流
     */
    void sendTyping();

}
