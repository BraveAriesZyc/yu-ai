package cn.iocoder.yudao.module.love.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveLocationSnapshotDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 位置点 Mapper
 *
 * 功能：轨迹区间查询（DESC 截断 + service 翻转）、最新点查询（30s 限频 + partner 组装）、
 * 30 天保留策略物理清除（M26 share 域，INTEGRATION-DESIGN.md §12.4「上报频率受限」）。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveLocationSnapshotMapper extends BaseMapperX<LoveLocationSnapshotDO> {

    /**
     * 查某用户最新一个位置点（30s 限频比较 + partner latest 组装）
     *
     * idx_user_time(user_id, report_time) 最左前缀可推（report_time 为第二列时 ORDER BY
     * 需 filesort，但 LIMIT 1 单行代价可忽略）；report_time desc, id desc 兜底同秒多条的稳定性
     */
    default LoveLocationSnapshotDO selectLatestByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<LoveLocationSnapshotDO>()
                .eq(LoveLocationSnapshotDO::getUserId, userId)
                .orderByDesc(LoveLocationSnapshotDO::getReportTime)
                .orderByDesc(LoveLocationSnapshotDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查某用户在某 couple 的时间区间内的轨迹点（§5.2 GET /love/share/location/trail）
     *
     * ⚠ 必须 ORDER BY report_time DESC LIMIT 500 后由 service 翻转回升序：
     * 若按升序 LIMIT，会把窗口截成「最老的 500 个点」（近 7 天早段），方向反了；
     * 走 idx_couple_user_time(couple_id, user_id, report_time)
     */
    default List<LoveLocationSnapshotDO> selectRecentByCoupleUser(Long coupleId, Long userId,
                                                                  LocalDateTime startTime, LocalDateTime endTime) {
        return selectList(new LambdaQueryWrapper<LoveLocationSnapshotDO>()
                .eq(LoveLocationSnapshotDO::getCoupleId, coupleId)
                .eq(LoveLocationSnapshotDO::getUserId, userId)
                .ge(LoveLocationSnapshotDO::getReportTime, startTime)
                .le(LoveLocationSnapshotDO::getReportTime, endTime)
                .orderByDesc(LoveLocationSnapshotDO::getReportTime)
                .orderByDesc(LoveLocationSnapshotDO::getId)
                .last("LIMIT 500"));
    }

    /**
     * 物理清除某用户 report_time 早于指定时刻的位置点（30 天保留策略）
     *
     * ⚠ 手写 @Delete 物理 SQL：BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删（deleted=1 行
     * 仍占索引位且永不消亡），保留策略必须是物理清除——CheckinMapper.reviveDeletedByUserIdAndDate
     * 注解 SQL 绕过 MP 注入逻辑的同款先例（该处是 UPDATE，此处是 DELETE，均不带 deleted 条件）。
     * 走 idx_user_time(user_id, report_time)，report 时顺带调用（单用户 DELETE 轻量）
     */
    @Delete("DELETE FROM love_location_snapshot WHERE user_id = #{userId} AND report_time < #{time}")
    int purgeBefore(@Param("userId") Long userId, @Param("time") LocalDateTime time);

    /**
     * 物理清除某用户全部位置点（M33 账号注销：注销即物理删，无时间窗）
     *
     * ⚠ 手写 @Delete 物理 SQL 同 purgeBefore（BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删，
     * 注销的隐私删除必须物理）——走 idx_user_time(user_id) 前缀
     */
    @Delete("DELETE FROM love_location_snapshot WHERE user_id = #{userId}")
    int deletePhysicalByUserId(@Param("userId") Long userId);

}
