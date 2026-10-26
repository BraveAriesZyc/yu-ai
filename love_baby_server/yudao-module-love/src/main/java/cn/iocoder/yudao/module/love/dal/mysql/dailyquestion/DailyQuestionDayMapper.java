package cn.iocoder.yudao.module.love.dal.mysql.dailyquestion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyQuestionDayDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日一问当日题 Mapper（M32）
 *
 * 功能：当日题 get-or-create 的查与插（uk_question_date 一天一行，并发兜底在 service 层
 * DuplicateKeyException 回退再查，CheckinServiceImpl 同款）+ 月区间查询（历史列表）。
 * 无删除路径（day 行永不清），无按 id 写端点。
 *
 * @author 芋道源码
 */
@Mapper
public interface DailyQuestionDayMapper extends BaseMapperX<LoveDailyQuestionDayDO> {

    /**
     * 查某天的当日题（uk_question_date 一天至多一行，selectOne 安全；
     * @TableLogic 自动附加 deleted = 0，本表无删除路径恒命中实体行）
     */
    default LoveDailyQuestionDayDO selectByDate(LocalDate date) {
        return selectOne(LoveDailyQuestionDayDO::getQuestionDate, date);
    }

    /**
     * 查 [startDate, endDate] 闭区间内的全部当日题（GET /love/daily-question/list 历史列表）
     *
     * 按 question_date asc 排序，端上按日期正序渲染月列表
     */
    default List<LoveDailyQuestionDayDO> selectListByDateRange(LocalDate startDate, LocalDate endDate) {
        return selectList(new LambdaQueryWrapper<LoveDailyQuestionDayDO>()
                .between(LoveDailyQuestionDayDO::getQuestionDate, startDate, endDate)
                .orderByAsc(LoveDailyQuestionDayDO::getQuestionDate)
                .orderByAsc(LoveDailyQuestionDayDO::getId));
    }

}
