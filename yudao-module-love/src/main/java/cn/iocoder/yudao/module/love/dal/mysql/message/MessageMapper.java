package cn.iocoder.yudao.module.love.dal.mysql.message;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.message.LoveMessageDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

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
                .eq(LoveMessageDO::getKind, LoveMessageDO.KIND_WISH));
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
                .ne(LoveMessageDO::getSenderId, excludeSenderId));
    }

}
