package cn.iocoder.yudao.module.love.dal.mysql.dailyquestion;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.love.dal.dataobject.dailyquestion.LoveDailyAnswerDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日一问作答 Mapper（M32）
 *
 * 功能：单人单题作答查写（uk_user_question 一人一题一条，覆盖写/并发兜底在 service 层）
 * + couple 月区间查询（历史列表走 idx_couple_date）。
 * 无删除路径（答案不提供删除/撤销），无按 id 写端点。
 *
 * @author 芋道源码
 */
@Mapper
public interface DailyAnswerMapper extends BaseMapperX<LoveDailyAnswerDO> {

    /**
     * 查某用户对某道题的作答（揭晓锁判断 + today 组装）
     *
     * uk_user_question(user_id, question_id) 保证一人一题至多一条，selectOne 安全
     * （@TableLogic 自动附加 deleted = 0；本表无删除路径）
     */
    default LoveDailyAnswerDO selectByUserIdAndQuestionId(Long userId, Long questionId) {
        return selectOne(LoveDailyAnswerDO::getUserId, userId,
                LoveDailyAnswerDO::getQuestionId, questionId);
    }

    /**
     * 按 couple 查 [startDate, endDate] 闭区间内双方的作答（GET /love/daily-question/list）
     *
     * 走 idx_couple_date(couple_id, answer_date)；answer_date 冗余恒 = question_date，
     * 与 day 行月区间两查后在 service 层内存合并（ReportServiceImpl 内存聚合同款口径）
     */
    default List<LoveDailyAnswerDO> selectListByCoupleBetweenDate(Long coupleId, LocalDate startDate, LocalDate endDate) {
        return selectList(new LambdaQueryWrapper<LoveDailyAnswerDO>()
                .eq(LoveDailyAnswerDO::getCoupleId, coupleId)
                .between(LoveDailyAnswerDO::getAnswerDate, startDate, endDate)
                .orderByAsc(LoveDailyAnswerDO::getAnswerDate)
                .orderByAsc(LoveDailyAnswerDO::getId));
    }

}
