package cn.iocoder.yudao.module.love.service.message;

import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageRespVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendReqVO;
import cn.iocoder.yudao.module.love.controller.app.message.vo.AppMessageSendWishesReqVO;

import java.util.List;

/**
 * 悄悄话/心愿消息 Service 接口
 *
 * 端上契约见 INTEGRATION-DESIGN.md §5.2 message 组：增量拉取（lastId 游标只增不减，D-12）、
 * 发送（kind=1 文本 / kind=2 心愿快照）。消息域只有「按自己 couple 收发」，无客户端传 id，天然防 IDOR。
 *
 * @author 芋道源码
 */
public interface MessageService {

    /**
     * 增量拉取消息列表（恒升序返回）
     *
     * 空参 lastId = 首屏取最近一页（id 倒序取 limit 条后反转为正序）；有 lastId = 顺序扫 id &gt; lastId 升序
     *
     * @param lastId 上次拉取的最大消息 id（空 = 首屏）
     * @param limit  条数上限（默认 50、钳制上限 200，§5.2）
     * @return 消息列表（正序）
     */
    List<AppMessageRespVO> getMessageList(Long lastId, Integer limit);

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

}
