package cn.iocoder.yudao.module.love.service.message;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageReadReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRecallReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRespVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendImageReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageUnreadRespVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendWishesReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.couple.LoveCoupleDO;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import cn.iocoder.yudao.module.love.dal.dataobject.user.LoveUserDO;
import cn.iocoder.yudao.module.love.dal.mysql.couple.LoveCoupleMapper;
import cn.iocoder.yudao.module.love.dal.mysql.message.MessageMapper;
import cn.iocoder.yudao.module.love.dal.mysql.user.LoveUserMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.COUPLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MESSAGE_NOT_EXISTS;
import static cn.iocoder.yudao.module.love.enums.ErrorCodeConstants.MESSAGE_RECALL_EXPIRED;

/**
 * 悄悄话/心愿消息 Service 实现类
 *
 * 以「当前登录用户」为出发点（SecurityFrameworkUtils 反查），不接受客户端传入的 coupleId / userId：
 * 发送方恒为当前用户、接收方恒为 couple 对方、拉取恒按自己 couple 过滤，天然防 IDOR。
 * M12 起 recall 为唯一按客户端 id 的 app 写入口：service 层 couple 归属 + sender 本人双校验防 IDOR
 * （不存在/跨 couple/非本人统一按 1_003_003_000「消息不存在」拒绝，不泄露存在性）。
 *
 * @author 芋道源码
 */
@Service
public class MessageServiceImpl implements MessageService {

    /**
     * 列表每页条数默认值（§5.2：limit 默认 50、上限 200）
     */
    private static final int DEFAULT_LIMIT = 50;

    /**
     * 列表每页条数上限（mapper 直接拼 SQL，未钳制的客户端原始值禁止透传）
     */
    private static final int MAX_LIMIT = 200;

    /**
     * WS 触发器消息类型（D-16，§5.2 WS 通道契约）：有新悄悄话/心愿消息的纯信号，
     * 与客户端 base/WebSocketModule 的判定串保持一致
     */
    private static final String MESSAGE_TYPE_LOVE_NEW = "love-message-new";

    /**
     * 撤回时间窗（M12，秒）：与端上 ChatPage RECALL_WINDOW_MS=120_000 双源同步（服务端权威，
     * 端上预判仅省一次必败请求）
     */
    private static final long RECALL_WINDOW_SECONDS = 120L;

    /**
     * WS 撤回信号类型（M12）：撤回不走「纯信号+增量拉」——增量拉按 id &gt; lastId 扫，拉不到
     * 已拉取老消息的字段变更，故信号携 messageId 由端上本地置墓碑（D-16 的显式破例）；
     * 信号丢失的兜底 = 端上切 Tab 进聊天页全量 resync 纠偏
     */
    private static final String MESSAGE_TYPE_LOVE_RECALL = "love-message-recall";

    /**
     * WS 正在输入信号类型（M13）：typing 是纯瞬态信号——无数据不落库、无「停止输入」信号
     * （端上 4s 过期唯一收口，D-18）；content 为单键占位 JSON 对象（JsonWebSocketMessage.content
     * 要求 JSON 字符串），端上不消费
     */
    private static final String MESSAGE_TYPE_LOVE_TYPING = "love-typing";

    @Resource
    private LoveUserMapper loveUserMapper;
    @Resource
    private LoveCoupleMapper loveCoupleMapper;
    @Resource
    private MessageMapper messageMapper;

    /**
     * WS 触发器(D-16)：给对方在线连接推「有新消息」纯信号。required=false——yudao.websocket.enable=false
     * 时 sender bean 不注册，不强注(照 infra DemoWebSocketMessageListener 先例)；sender-type=local 单实例
     * 内存直投，对方无在线连接时 send 静默 no-op(仅 debug 日志)，端上由既有 5s 轮询兜底
     */
    @Autowired(required = false)
    private WebSocketMessageSender webSocketMessageSender;

    @Override
    public List<AppMessageRespVO> getMessageList(Long lastId, Integer limit) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        LoveCoupleDO couple = getValidatedCouple(currentUserId);
        int size = resolveLimit(limit);
        if (lastId == null) {
            // 空参 lastId = 首屏：id 倒序取最近一页（mapper 返回倒序），service 反转为正序后返回端上（§5.2）
            List<LoveMessageDO> latestPage = messageMapper.selectListLatestPage(couple.getId(), size);
            Collections.reverse(latestPage); // MyBatis 返回可变 ArrayList，可原地反转
            return BeanUtils.toBean(latestPage, AppMessageRespVO.class);
        }
        // 有 lastId：走 idx_couple_time(couple_id, id) 顺序扫 id > lastId 升序返回（游标只增不减，D-12）
        return BeanUtils.toBean(messageMapper.selectListIncrement(couple.getId(), lastId, size), AppMessageRespVO.class);
    }

    @Override
    public List<AppMessageRespVO> getWishList(Integer limit) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        LoveCoupleDO couple = getValidatedCouple(currentUserId);
        int size = resolveLimit(limit);
        // 时间线语义 = 最新在前浏览，mapper 倒序直接透传不反转（与 getMessageList 首屏正序流不同）
        return BeanUtils.toBean(messageMapper.selectListWishes(couple.getId(), size), AppMessageRespVO.class);
    }

    @Override
    public AppMessageRespVO sendMessage(AppMessageSendReqVO reqVO) {
        // trim 后非空、≤500：长度由 VO @Length 按原文早拦（列 varchar(500)），空白串在此打回全局参数错误
        String content = reqVO.getContent().trim();
        if (content.isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return insertAndBuildRespVO(LoveMessageDO.KIND_TEXT, content, null, null, null);
    }

    @Override
    public AppMessageRespVO sendWishes(AppMessageSendWishesReqVO reqVO) {
        // 快照式（§5.2）：wishKey/text/color/icon 四字段整体入 love_message，服务端不校验 wish 归属
        // （无 wish 实体，文案字典留端上），两设备还原靠快照字段。text 允许缺省（端上心愿消息本地模型
        // 即空文案，渲染走 wishKey/color/icon 快照），NULL 归一为空串满足 content 列 NOT NULL；
        // wishKey/color/icon 为空串 = 缺省（端上惯例），NULL 入可空列等价
        String content = reqVO.getText() != null ? reqVO.getText() : "";
        return insertAndBuildRespVO(LoveMessageDO.KIND_WISH, content,
                reqVO.getWishKey(), reqVO.getColor(), reqVO.getIcon());
    }

    @Override
    public AppMessageRespVO sendImage(AppMessageSendImageReqVO reqVO) {
        // trim 后非空、≤512：长度由 VO @Length 按原文早拦（列 varchar(512)），空白串在此打回全局参数错误
        String imageUrl = reqVO.getImageUrl().trim();
        if (imageUrl.isEmpty()) {
            throw exception(BAD_REQUEST);
        }
        return insertAndBuildRespVO(LoveMessageDO.KIND_IMAGE, imageUrl, null, null, null);
    }

    @Override
    public AppMessageUnreadRespVO getUnreadCount() {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        AppMessageUnreadRespVO resp = new AppMessageUnreadRespVO();
        // 未绑定返回全 0 而非报错（UserProfileServiceImpl.getStats L84-87 先例）——
        // 红点查询是只读旁路，未绑定不构成错误态；禁改回 CommonResult<Long> 标量回包
        // （端上 HttpClient toOk 只分派对象/数组，标量落空对象，M11 冻结决策）
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            resp.setUnread(0L);
            return resp;
        }
        LoveUserDO user = loveUserMapper.selectById(currentUserId);
        long cursor = user != null && user.getChatReadCursor() != null ? user.getChatReadCursor() : 0L;
        resp.setUnread(messageMapper.selectCountUnread(couple.getId(), cursor, currentUserId));
        return resp;
    }

    @Override
    public void markRead(AppMessageReadReqVO reqVO) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        // 未绑定报 1_003_004_003（与 list/send 组同口径；unread 静默回 0、read 报错的非对称，
        // 与 stats 静默 0 / unbind 报错两先例各自对齐）
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        // 不查库校验消息归属：游标只推进自己行的已读位，谎报大 id 仅压制自己的红点（自伤无动机），
        // GREATEST 已防回退；从简（M11 冻结决策，风险清单 R3 留档）
        loveUserMapper.updateChatReadCursorForward(currentUserId, reqVO.getLastMessageId());
    }

    /**
     * 撤回消息（M12）：校验链 = 存在 + couple 归属 + sender 本人（统一 1_003_003_000 防探测）→
     * 已撤回幂等成功 → 时间窗（服务端时钟权威，1_003_003_001）→ CAS 置位墓碑 → WS 推对方
     *
     * recall 是首个按客户端 id 的 app 写入口（见类注释 IDOR 口径）；撤回不删行，content 留档审计
     */
    @Override
    public void recallMessage(AppMessageRecallReqVO reqVO) {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        LoveCoupleDO couple = getValidatedCouple(currentUserId);
        // 三重校验统一按「消息不存在」拒绝：不泄露他 couple / 他人消息的存在性（防探测，§11 IDOR 口径）
        LoveMessageDO message = messageMapper.selectById(reqVO.getMessageId());
        if (message == null || !Objects.equals(message.getCoupleId(), couple.getId())
                || !Objects.equals(message.getSenderId(), currentUserId)) {
            throw exception(MESSAGE_NOT_EXISTS);
        }
        // 已撤回幂等成功：重复点按 / 双端同发均不报错（checkin 覆盖写先例口径）
        if (Boolean.TRUE.equals(message.getRecalled())) {
            return;
        }
        // 时间窗权威校验（服务端时钟，不受端上本地时钟影响）；createTime 由 insertFill 落库时填充，
        // selectById 读回的即 DB 真值
        if (message.getCreateTime() == null
                || message.getCreateTime().isBefore(LocalDateTime.now().minusSeconds(RECALL_WINDOW_SECONDS))) {
            throw exception(MESSAGE_RECALL_EXPIRED);
        }
        // CAS 置位墓碑：rows==0 = 并发已撤回（或 admin 逻辑删竞态），幂等成功不打错
        int rows = messageMapper.recallById(message.getId(), currentUserId);
        // 撤回信号推对方在线连接（insertAndBuildRespVO 同链路）：单键 Map.of 合法；
        // rows==0 时对方大概率已收到首个信号，不重推
        if (rows > 0 && webSocketMessageSender != null) {
            webSocketMessageSender.send(UserTypeEnum.MEMBER.getValue(), message.getReceiverId(),
                    MESSAGE_TYPE_LOVE_RECALL, JsonUtils.toJsonString(
                            Map.of("messageId", String.valueOf(message.getId()))));
        }
    }

    @Override
    public void sendTyping() {
        Long currentUserId = getCurrentUserId();
        // §6.5 停用账号收口
        getValidatedCurrentUser(currentUserId);
        LoveCoupleDO couple = getValidatedCouple(currentUserId);
        // 纯中继（D-18）：无落库无状态，对方离线/未桥接时 send 静默 no-op；节流在端上（3s），
        // 服务端不做限流（情侣双方仅两人，不落库无存储放大）
        if (webSocketMessageSender != null) {
            webSocketMessageSender.send(UserTypeEnum.MEMBER.getValue(), getPartnerId(couple, currentUserId),
                    MESSAGE_TYPE_LOVE_TYPING, JsonUtils.toJsonString(Map.of("typing", "1")));
        }
    }

    /**
     * 落库并构建完整消息 VO（send / send-wishes 共用）
     *
     * 发送方 = 当前用户、接收方 = couple 对方快照（D-11：couple 物理解绑后审计仍可归对）；
     * audit_status 首期恒写 1 兜底，事前审核二期接入（§12.3）；id 由 MP 自增回填——
     * 端上依赖回包 id 做乐观替换本地临时消息与 lastId 推进（§7.4），故回包必含完整字段
     */
    private AppMessageRespVO insertAndBuildRespVO(Integer kind, String content,
                                                  String wishKey, String color, String icon) {
        Long senderId = getCurrentUserId();
        // 停用收口 + 未绑定校验（与 todo 组同口径）：两个发送端点统一在此收口，list 已在入口收口
        getValidatedCurrentUser(senderId);
        LoveCoupleDO couple = getValidatedCouple(senderId);
        LoveMessageDO message = new LoveMessageDO()
                .setCoupleId(couple.getId())
                .setSenderId(senderId)
                .setReceiverId(getPartnerId(couple, senderId))
                .setKind(kind)
                .setContent(content)
                .setWishKey(wishKey)
                .setColor(color)
                .setIcon(icon)
                .setAuditStatus(LoveMessageDO.AUDIT_STATUS_APPROVED);
        messageMapper.insert(message);
        // WS 触发器(D-16)：落库后给对方在线连接推一条纯信号，数据仍由端上 lastId 增量拉保证——
        // 断线/漏推/未桥接端全部由既有轮询兜底，信号丢了最多退回 5s 延迟不丢消息；
        // 推送失败仅记日志(AbstractWebSocketMessageSender 内部 catch IOException)，不影响回包；
        // messageId 按 String 契约(§5.1)，端上只当信号不消费内容
        if (webSocketMessageSender != null) {
            webSocketMessageSender.send(UserTypeEnum.MEMBER.getValue(), message.getReceiverId(),
                    MESSAGE_TYPE_LOVE_NEW, JsonUtils.toJsonString(
                            Map.of("messageId", String.valueOf(message.getId()))));
        }
        return BeanUtils.toBean(message, AppMessageRespVO.class);
    }

    /**
     * limit 钳制：缺省 50、夹取 [1, 200]（§5.2；mapper 直接拼 SQL，未钳制值禁止透传）
     */
    private int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(Math.max(limit, 1), MAX_LIMIT);
    }

    /**
     * 取 couple 对方 userId（userA/userB 二选一）
     */
    private Long getPartnerId(LoveCoupleDO couple, Long currentUserId) {
        return Objects.equals(couple.getUserAId(), currentUserId)
                ? couple.getUserBId() : couple.getUserAId();
    }

    /**
     * 校验当前用户已绑定情侣关系：未绑定抛 1_003_004_003（与 todo 组同口径），
     * couple 由当前登录人反查（无客户端传参），防 IDOR
     */
    private LoveCoupleDO getValidatedCouple(Long currentUserId) {
        LoveCoupleDO couple = loveCoupleMapper.selectByUserId(currentUserId);
        if (couple == null) {
            throw exception(COUPLE_NOT_EXISTS);
        }
        return couple;
    }

    /**
     * 获得当前登录用户编号
     *
     * 理论不可达为 null：controller 未标 @PermitAll，未登录请求已被 anyRequest().authenticated() 拦截；
     * 万一穿透（token 被并发移除等），按 401 让端上走刷新/回 Gate 通道（CoupleServiceImpl 同款）
     */
    private Long getCurrentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw exception(UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 校验当前用户可用（§6.5 停用账号收口）：行不存在（被 admin 删除）与停用统一按
     * 「账号已停用」（1_003_005_002）处理，端上清会话回 Gate，行为一致
     * （私有方法结构照抄 CoupleServiceImpl 范式，todo/mood/message 各 service 统一口径）
     */
    private void getValidatedCurrentUser(Long userId) {
        LoveUserDO user = loveUserMapper.selectById(userId);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

}
