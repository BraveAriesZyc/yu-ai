package cn.iocoder.yudao.module.love.dal.mysql.share;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.share.LoveLocationPlaceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程地点 Mapper
 *
 * 功能：地点节流查最新（recordPlaceIfDue）、列表区间查询（DESC 截断 + service 翻转）、
 * 30 天保留策略物理清除（M28 行动轨迹地点记录）。
 *
 * @author 芋道源码
 */
@Mapper
public interface LoveLocationPlaceMapper extends BaseMapperX<LoveLocationPlaceDO> {

    /**
     * 查某用户最新一条地点记录（30min/300m 节流比较）
     *
     * idx_user_time(user_id, report_time)；report_time desc, id desc 兜底同秒多条的稳定性
     */
    default LoveLocationPlaceDO selectLatestByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<LoveLocationPlaceDO>()
                .eq(LoveLocationPlaceDO::getUserId, userId)
                .orderByDesc(LoveLocationPlaceDO::getReportTime)
                .orderByDesc(LoveLocationPlaceDO::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查某用户在某 couple 的时间区间内的地点记录（GET /love/share/location/place/list）
     *
     * ⚠ 必须 ORDER BY report_time DESC LIMIT 100 后由 service 翻转回升序：
     * 若按升序 LIMIT，会把窗口截成「最老的 100 条」（近 7 天早段），方向反了；
     * 走 idx_couple_user_time(couple_id, user_id, report_time)
     */
    default List<LoveLocationPlaceDO> selectRecentByCoupleUser(Long coupleId, Long userId,
                                                               LocalDateTime startTime, LocalDateTime endTime) {
        return selectList(new LambdaQueryWrapper<LoveLocationPlaceDO>()
                .eq(LoveLocationPlaceDO::getCoupleId, coupleId)
                .eq(LoveLocationPlaceDO::getUserId, userId)
                .ge(LoveLocationPlaceDO::getReportTime, startTime)
                .le(LoveLocationPlaceDO::getReportTime, endTime)
                .orderByDesc(LoveLocationPlaceDO::getReportTime)
                .orderByDesc(LoveLocationPlaceDO::getId)
                .last("LIMIT 100"));
    }

    /**
     * 物理清除某用户 report_time 早于指定时刻的地点记录（30 天保留策略）
     *
     * ⚠ 手写 @Delete 物理 SQL：BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删（deleted=1 行
     * 仍占索引位且永不消亡），保留策略必须是物理清除——LoveLocationSnapshotMapper.purgeBefore
     * 同款先例（均不带 deleted 条件）。走 idx_user_time(user_id, report_time)，report 时顺带调用
     */
    @Delete("DELETE FROM love_location_place WHERE user_id = #{userId} AND report_time < #{time}")
    int purgeBefore(@Param("userId") Long userId, @Param("time") LocalDateTime time);

    /**
     * 物理清除某用户全部地点记录（M33 账号注销：注销即物理删，无时间窗）
     *
     * ⚠ 手写 @Delete 物理 SQL 同 purgeBefore（BaseDO @TableLogic 会把 mapper.delete 拦成逻辑删，
     * 注销的隐私删除必须物理）
     */
    @Delete("DELETE FROM love_location_place WHERE user_id = #{userId}")
    int deletePhysicalByUserId(@Param("userId") Long userId);

}
