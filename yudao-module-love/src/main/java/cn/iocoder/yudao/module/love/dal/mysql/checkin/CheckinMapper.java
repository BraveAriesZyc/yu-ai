package cn.iocoder.yudao.module.love.dal.mysql.checkin;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.checkin.LoveCheckinDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日打卡 Mapper
 *
 * 功能：当日记录查写（uk_user_date 幂等打卡）+ 月历区间查询 + 全量拉取（summary 内存聚合）
 * （INTEGRATION-DESIGN.md §5.2 checkin 组）。按 id 单条读写走 BaseMapperX 通用方法
 * （selectById / deleteById），admin 判空校验在 service 层收口。
 *
 * @author 芋道源码
 */
@Mapper
public interface CheckinMapper extends BaseMapperX<LoveCheckinDO> {

    /**
     * 查某用户某日的打卡记录（§5.2 POST /love/checkin/check 幂等语义）
     *
     * uk_user_date(user_id, checkin_date) 保证一人一天至多一条，selectOne 安全
     * （@TableLogic 自动附加 deleted = 0，逻辑删除行不会命中；该行仍占 uk 位，
     * 重打插入会撞 uk 走冲突回退，与 MoodServiceImpl 注释同款 DDL 级遗留）
     */
    default LoveCheckinDO selectByUserIdAndDate(Long userId, LocalDate date) {
        return selectOne(LoveCheckinDO::getUserId, userId, LoveCheckinDO::getCheckinDate, date);
    }

    /**
     * 复活被逻辑删除的当日打卡行（admin 删了当天记录后用户重打：逻辑删除行仍占 uk_user_date 位，
     * 标准插入必撞 uk 且 @TableLogic 读写都过滤 deleted = 1，mood 组记录在案的 DDL 级遗留——
     * checkin 的 admin delete 是主操作，此路径必须可恢复，故注入原生 UPDATE 绕过 @TableLogic）
     *
     * 注解 SQL 不经 MP 注入逻辑，WHERE deleted = 1 显式限定只碰已删行；uk 保证同 (user_id, date)
     * 至多一行（含已删），影响行数恒 0/1。create_time 保留原值（语义 = 首次打卡时间）。
     */
    @Update("UPDATE love_checkin SET deleted = 0, couple_id = #{coupleId}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND checkin_date = #{date} AND deleted = 1")
    int reviveDeletedByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date,
                                     @Param("coupleId") Long coupleId);

    /**
     * 按 couple 查 [startDate, endDate] 闭区间内双方的打卡记录（§5.2 GET /love/checkin/list?month=）
     *
     * 走 idx_couple_date(couple_id, checkin_date)；按 checkin_date asc, id asc 排序，
     * 某日无记录 = 回包无该日条目，端上按空缺渲染
     */
    default List<LoveCheckinDO> selectListByCoupleIdBetweenDate(Long coupleId, LocalDate startDate, LocalDate endDate) {
        return selectList(new LambdaQueryWrapper<LoveCheckinDO>()
                .eq(LoveCheckinDO::getCoupleId, coupleId)
                .between(LoveCheckinDO::getCheckinDate, startDate, endDate)
                .orderByAsc(LoveCheckinDO::getCheckinDate)
                .orderByAsc(LoveCheckinDO::getId));
    }

    /**
     * 按 couple 查全量打卡记录（§5.2 GET /love/checkin/summary 内存聚合）
     *
     * 打卡是纯日期点且一人一天一条，表增长有上界（天数 × 2），全量拉取单条 SQL 即可，
     * streak 跨月边界由 service 层内存回溯计算（月历单月视图算不了跨月连续）
     */
    default List<LoveCheckinDO> selectListByCoupleId(Long coupleId) {
        return selectList(new LambdaQueryWrapper<LoveCheckinDO>()
                .eq(LoveCheckinDO::getCoupleId, coupleId)
                .orderByAsc(LoveCheckinDO::getCheckinDate)
                .orderByAsc(LoveCheckinDO::getId));
    }

}
