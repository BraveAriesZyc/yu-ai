package cn.iocoder.yudao.module.love.dal.mysql.wish;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.wish.LoveWishDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 恋爱清单 Mapper
 *
 * 功能：app 侧全量列表查询 + done 状态迁移（M14，INTEGRATION-DESIGN.md §5.2 wish 组）；
 * 按 id 单条读与删除走 BaseMapperX 通用方法（selectById / deleteById），
 * couple 归属校验在 service 层收口（防 IDOR）。
 *
 * @author 芋道源码
 */
@Mapper
public interface WishMapper extends BaseMapperX<LoveWishDO> {

    /**
     * 按 couple 查全量心愿列表（§5.2 GET /love/wish/list，端上整体覆盖）
     *
     * 排序 create_time asc, id asc（照 TodoMapper）：端上会按「进行中在前 / 已完成沉底」重排（D-19f），
     * 服务端序仅作兜底稳定序，id 兜底同一秒内多条记录的稳定性
     */
    default List<LoveWishDO> selectListByCoupleId(Long coupleId) {
        return selectList(new LambdaQueryWrapper<LoveWishDO>()
                .eq(LoveWishDO::getCoupleId, coupleId)
                .orderByAsc(LoveWishDO::getCreateTime)
                .orderByAsc(LoveWishDO::getId));
    }

    /**
     * 勾选完成（M14 POST /love/wish/done，done=true 分支）：done 置位 + 记录完成人/时间
     *
     * 注解 SQL 不经 MP 注入逻辑：显式 WHERE deleted = 0（@TableLogic 不作用于注解 SQL）、
     * 显式 update_time = NOW()（MP 自动填充同不作用；MessageMapper.recallById 先例）；
     * couple_id 条件 = 归属第二道闸（service 已校验，防并发窗口内的归属漂移）；
     * done = b'0' 条件 = CAS（D-19b）：并发双勾 / 重复请求仅一方影响行数 1——恰该方发完成消息，
     * rows == 0 由 service 按幂等处理（重读回包，已 done 真值 done_by/done_time 不变）
     *
     * @param id      心愿编号（service 层已校验存在 + couple 归属）
     * @param coupleId 当前 couple（必须与行 couple_id 一致才置位）
     * @param userId  完成人（当前登录人 = 勾选者，可与创建人不同——双方均可勾选）
     * @return 影响行数（0 = 幂等：已 done 或 admin 逻辑删竞态）
     */
    @Update("UPDATE love_wish SET done = b'1', done_by = #{userId}, done_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND couple_id = #{coupleId} AND deleted = 0 AND done = b'0'")
    int markDone(@Param("id") Long id, @Param("coupleId") Long coupleId, @Param("userId") Long userId);

    /**
     * 取消勾选（done=false 分支）：清完成态三字段
     *
     * 注解 SQL 原因同 {@link #markDone}，另加：undo 须将 done_by / done_time 置回 NULL，
     * updateById 默认忽略 null 字段置不上（必须显式 SET 列 = NULL）；
     * done = b'1' 条件 = 对称 CAS：重复 undo 仅一方生效，rows == 0 幂等；
     * 不清任何「已发完成消息」类标记（D-19b：无 celebrated 列，undo→re-done 重发为预期——
     * 重新完成是新的完成事件）
     *
     * @param id       心愿编号（service 层已校验存在 + couple 归属）
     * @param coupleId 当前 couple
     * @return 影响行数（0 = 幂等：已未完成）
     */
    @Update("UPDATE love_wish SET done = b'0', done_by = NULL, done_time = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND couple_id = #{coupleId} AND deleted = 0 AND done = b'1'")
    int markUndone(@Param("id") Long id, @Param("coupleId") Long coupleId);

}
