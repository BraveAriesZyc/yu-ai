package cn.iocoder.yudao.module.love.dal.mysql.mood;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.mood.LoveMoodRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日心情记录 Mapper
 *
 * 功能：当日记录查改（uk_user_date 覆盖写）+ 月历区间查询（INTEGRATION-DESIGN.md §5.2 mood 组）。
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

}
