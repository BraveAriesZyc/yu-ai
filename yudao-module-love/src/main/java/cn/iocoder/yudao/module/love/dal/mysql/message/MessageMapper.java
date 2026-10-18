package cn.iocoder.yudao.module.love.dal.mysql.message;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 悄悄话/心愿消息 Mapper
 *
 * 功能：消息增量拉取两个入口（INTEGRATION-DESIGN.md §5.2 message 组）——
 * 有 lastId 走 {@link #selectListIncrement}（idx_couple_time 顺序扫），
 * 空参 lastId 首屏走 {@link #selectListLatestPage}（id 倒序取最近一页，service 反转正序返回）；
 * 发送走 BaseMapperX.insert，按 id 写的 admin 删除走 BaseMapperX.deleteById。
 *
 * @author 芋道源码
 */
@Mapper
public interface MessageMapper extends BaseMapperX<LoveMessageDO> {

    /**
     * 增量拉取：id &gt; lastId 的消息，按 id 升序返回 limit 条（§5.2 message/list）
     *
     * 走 idx_couple_time(couple_id, id)：couple_id 等值 + id 范围扫，游标只增不减（D-12）；
     *
     * @param coupleId 情侣关系编号
     * @param lastId   上次拉取的最大消息 id（必传，调用方保证非空；空参走 selectListLatestPage）
     * @param limit    条数上限（service 层钳制：默认 50、上限 200，§5.2；本方法直接拼 SQL，
     *                 禁止把未钳制的客户端原始值传入）
     */
    default List<LoveMessageDO> selectListIncrement(Long coupleId, Long lastId, Integer limit) {
        return selectList(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, coupleId)
                .gt(LoveMessageDO::getId, lastId)
                .orderByAsc(LoveMessageDO::getId)
                // 与仓内 QueryWrapperX.limitN 同款惯例：last("LIMIT n")；limit 由 service 钳制后传入
                .last("LIMIT " + limit));
    }

    /**
     * 首屏最近一页：按 id 倒序取 limit 条（§5.2 message/list 空参 lastId 语义）
     *
     * 返回【倒序】结果，service 层负责反转为正序后返回端上；
     *
     * @param coupleId 情侣关系编号
     * @param limit    条数上限（service 层钳制：默认 50、上限 200，§5.2）
     */
    default List<LoveMessageDO> selectListLatestPage(Long coupleId, Integer limit) {
        return selectList(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, coupleId)
                .orderByDesc(LoveMessageDO::getId)
                // 与仓内 QueryWrapperX.limitN 同款惯例：last("LIMIT n")；limit 由 service 钳制后传入
                .last("LIMIT " + limit));
    }

    /**
     * 心愿时间线视图：kind=2（心愿消息）按 id 倒序取 limit 条（§5.2 心愿查看，只读视图）
     *
     * 返回【倒序】结果（最新在前），service 直接透传不反转——时间线浏览语义与首屏正序流不同；
     * 走 idx_couple_time(couple_id, id) 前缀扫（kind 无独立索引，couple 内量级毫秒级），
     * 若未来单 couple 心愿过千再考虑追加 (couple_id, kind) 索引（love.sql 末尾只追加）
     *
     * @param coupleId 情侣关系编号
     * @param limit    条数上限（service 层钳制：默认 50、上限 200，§5.2）
     */
    default List<LoveMessageDO> selectListWishes(Long coupleId, Integer limit) {
        return selectList(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, coupleId)
                .eq(LoveMessageDO::getKind, LoveMessageDO.KIND_WISH)
                // 已撤回心愿不出时间线（M12：墓碑只在聊天流呈现，与聊天内灰条口径一致）
                .eq(LoveMessageDO::getRecalled, false)
                .orderByDesc(LoveMessageDO::getId)
                // 与仓内 QueryWrapperX.limitN 同款惯例：last("LIMIT n")；limit 由 service 钳制后传入
                .last("LIMIT " + limit));
    }

    /**
     * 心愿计数：kind=2 行数（统计端点 wishCount 用，文本悄悄话不计入——
     * messageCount 仍数全部消息，两口径并存）
     *
     * @param coupleId 情侣关系编号
     */
    default Long selectCountWishes(Long coupleId) {
        return selectCount(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, coupleId)
                .eq(LoveMessageDO::getKind, LoveMessageDO.KIND_WISH)
                // M18:排除已撤回墓碑行(recalled 标记不删行),与端上心愿列表口径一致
                .eq(LoveMessageDO::getRecalled, false));
    }

    /**
     * 未读计数（M11 红点口径，GET /love/message/unread）：couple 内 id 大于我方已读游标且
     * 非本人发送的行数。三口径：kind 不过滤（文本/心愿/图片全计）；sender_id &lt;&gt; 自己
     * （本人发送不产生我方未读）；@TableLogic 自动附加 deleted = 0（admin 逻辑删不计）。
     * 走 idx_couple_time(couple_id, id) 前缀扫，couple 内量级毫秒级
     *
     * @param coupleId         情侣关系编号
     * @param afterMessageId   我方已读游标（love_user.chat_read_cursor，0 = 未读过全计）
     * @param excludeSenderId  排除的发送方（当前用户）
     */
    default Long selectCountUnread(Long coupleId, Long afterMessageId, Long excludeSenderId) {
        return selectCount(new LambdaQueryWrapper<LoveMessageDO>()
                .eq(LoveMessageDO::getCoupleId, coupleId)
                .gt(LoveMessageDO::getId, afterMessageId)
                .ne(LoveMessageDO::getSenderId, excludeSenderId)
                // 对方撤回一条我未读的消息，红点应减（M12）
                .eq(LoveMessageDO::getRecalled, false));
    }

    /**
     * 撤回消息（M12 POST /love/message/recall）：recalled 墓碑置位，不删行不改 content
     *
     * 注解 SQL 不经 MP 注入逻辑：显式 WHERE deleted = 0（@TableLogic 不作用于注解 SQL）、
     * 显式 update_time = NOW()（MP 自动填充同不作用；LoveUserMapper.updateChatReadCursorForward 先例）；
     * sender_id 条件 = 归属第二道闸（service 已校验，防并发窗口内的归属漂移）；
     * recalled = b'0' 条件 = CAS：并发双撤回/重复请求仅一方影响行数 1，rows == 0 由 service 按幂等处理
     * （可能为并发已撤回或 admin 逻辑删竞态，两者端上语义一致：消息不可见）
     *
     * @param id        消息编号（service 层已校验存在 + couple 归属 + sender 本人）
     * @param senderId  当前用户（必须与行 sender_id 一致才置位）
     * @return 影响行数（0 = 幂等：已撤回或 admin 逻辑删竞态）
     */
    @Update("UPDATE love_message SET recalled = b'1', update_time = NOW() "
            + "WHERE id = #{id} AND sender_id = #{senderId} AND deleted = 0 AND recalled = b'0'")
    int recallById(@Param("id") Long id, @Param("senderId") Long senderId);

}
