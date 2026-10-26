package cn.iocoder.yudao.module.love.dal.mysql.mood;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.love.controller.admin.mood.vo.LoveMoodRecordPageReqVO;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日心情记录 Mapper
 *
 * 功能：当日记录查改（uk_user_date 覆盖写）+ 月历区间查询（INTEGRATION-DESIGN.md §5.2 mood 组）
 * + admin 分页（M31，D-13 裁决变更补后台页）。
 *
 * @author 芋道源码
 */
@Mapper
public interface MoodRecordMapper extends BaseMapperX<LoveMoodRecordDO> {

    /**
     * 查某用户某日的心情记录（§5.2 POST /love/mood/record 覆盖写语义）
     *
     * uk_user_date(user_id, mood_date) 保证一人一天至多一条，selectOne 安全
     * （@TableLogic 自动附加 deleted = 0，逻辑删除行不会命中）
     */
    default LoveMoodRecordDO selectByUserIdAndDate(Long userId, LocalDate date) {
        return selectOne(LoveMoodRecordDO::getUserId, userId, LoveMoodRecordDO::getMoodDate, date);
    }

    /**
     * 按 couple 查 [startDate, endDate] 闭区间内双方的心情记录（§5.2 GET /love/mood/record/list?month=）
     *
     * 走 idx_couple_month(couple_id, mood_date)；按 mood_date 升序，
     * 某日无记录 = 回包无该日条目，端上按空缺渲染
     */
    default List<LoveMoodRecordDO> selectListByCoupleIdBetweenDate(Long coupleId, LocalDate startDate, LocalDate endDate) {
        return selectList(new LambdaQueryWrapper<LoveMoodRecordDO>()
                .eq(LoveMoodRecordDO::getCoupleId, coupleId)
                .between(LoveMoodRecordDO::getMoodDate, startDate, endDate)
                .orderByAsc(LoveMoodRecordDO::getMoodDate));
    }

    /**
     * admin 分页：eqIfPresent coupleId / userId + likeIfPresent moodLabel + moodDate 区间
     * + orderByDesc(id)（MomentMapper.selectPage 同款惯例；M31 D-13 裁决变更补后台页）
     *
     * mood_date 是纯 DATE 列（无时分秒），between 不会像 moment 的 DATETIME 列那样把结束日
     * 隐式截断到当日零点，直接 betweenIfPresent 即可（MomentMapper.java:37-44 注释同源）
     */
    default PageResult<LoveMoodRecordDO> selectPage(LoveMoodRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<LoveMoodRecordDO>()
                .eqIfPresent(LoveMoodRecordDO::getCoupleId, reqVO.getCoupleId())
                .eqIfPresent(LoveMoodRecordDO::getUserId, reqVO.getUserId())
                .likeIfPresent(LoveMoodRecordDO::getMoodLabel, reqVO.getMoodLabel())
                .betweenIfPresent(LoveMoodRecordDO::getMoodDate, reqVO.getMoodDate())
                .orderByDesc(LoveMoodRecordDO::getId));
    }

}
